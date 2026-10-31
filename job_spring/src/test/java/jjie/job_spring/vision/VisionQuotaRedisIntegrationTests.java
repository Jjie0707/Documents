package jjie.job_spring.vision;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import jjie.job_spring.config.VisionQuotaProperties;
import jjie.job_spring.vision.exception.ImageValidationException;
import jjie.job_spring.vision.exception.VisionQuotaException;
import jjie.job_spring.vision.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

/** 启动本测试自己拥有的临时 Redis；绝不读取应用配置或连接默认 6379。 */
class VisionQuotaRedisIntegrationTests {
    private static Process process;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private VisionQuotaProperties config;
    private VisionQuotaKeyService keys;
    private VisionQuotaService quotas;
    private Clock clock;
    private LocalDate day;
    private Instant resetAt;

    @BeforeAll static void startOwnedRedis() throws Exception {
        String binary = System.getenv("REDIS_SERVER_BIN");
        if (binary == null) binary = Files.isExecutable(Path.of("/opt/homebrew/bin/redis-server"))
                ? "/opt/homebrew/bin/redis-server" : "redis-server";
        int port;
        try (var socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        var directory = Files.createTempDirectory("job-quota-test-");
        process = new ProcessBuilder(binary, "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no", "--dir", directory.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            boolean ready = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && process.isAlive()) {
                try (var socket = new Socket("127.0.0.1", port)) { ready = true; break; }
                catch (java.io.IOException ignored) { Thread.sleep(30); }
            }
            if (!ready) throw new IllegalStateException("Isolated Redis did not start; install Redis or set REDIS_SERVER_BIN");
            factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).shutdownTimeout(Duration.ZERO).build());
            factory.afterPropertiesSet();
            factory.start();
            redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
        } catch (Exception exception) { process.destroyForcibly(); throw exception; }
    }

    @AfterAll static void stopOwnedRedis() throws Exception {
        try { if (factory != null) factory.destroy(); }
        finally {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
            }
        }
    }

    @BeforeEach void setUp() {
        // 仅删除本测试独占进程内、固定业务前缀下的键；不使用 FLUSHDB。
        var old = redis.keys("vision:quota:v1:*");
        if (old != null && !old.isEmpty()) redis.delete(old);
        config = new VisionQuotaProperties();
        config.setHmacSecret(UUID.randomUUID().toString());
        keys = new VisionQuotaKeyService(config);
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        day = clock.instant().atZone(VisionQuotaService.QUOTA_ZONE).toLocalDate();
        resetAt = day.plusDays(1).atStartOfDay(VisionQuotaService.QUOTA_ZONE).toInstant();
        quotas = new VisionQuotaService(redis, config, keys, clock);
    }

    private String ipKey(String ip) { return keys.dailyIpKey(day, ip); }
    private String globalKey() { return keys.dailyGlobalKey(day); }
    private void fixture(String key, String count) {
        redis.opsForValue().set(key, count);
        redis.expireAt(key, resetAt);
    }
    private long expiry(String key) {
        return redis.execute(new DefaultRedisScript<>("return redis.call('EXPIRETIME', KEYS[1])", Long.class), List.of(key));
    }

    private long race(boolean distinctIps, int attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 1; i <= attempts; i++) {
                String ip = distinctIps ? "198.18." + (i / 250) + "." + (i % 250 + 1) : "192.0.2.1";
                work.add(() -> {
                    try { quotas.reserve(ip); return true; }
                    catch (VisionQuotaException exception) {
                        assertThat(exception.getCode()).isIn("IP_QUOTA_EXHAUSTED", "GLOBAL_QUOTA_EXHAUSTED");
                        return false;
                    }
                });
            }
            long succeeded = 0;
            for (Future<Boolean> result : pool.invokeAll(work)) if (result.get(5, TimeUnit.SECONDS)) succeeded++;
            return succeeded;
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSameIpAdmitsExactlyThirtyAndNeverOverdrawsGlobal() throws Exception {
        assertThat(race(false, 60)).isEqualTo(30);
        assertThat(redis.opsForValue().get(ipKey("192.0.2.1"))).isEqualTo("30");
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo("30");
        assertThat(expiry(globalKey())).isEqualTo(resetAt.getEpochSecond());
        assertThat(expiry(ipKey("192.0.2.1"))).isEqualTo(resetAt.getEpochSecond());
    }

    @Test void concurrentDifferentIpsAdmitExactlyGlobalLimitWithoutDebitingRejectedIps() throws Exception {
        assertThat(race(true, 360)).isEqualTo(300);
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo("300");
        var ipKeys = redis.keys("vision:quota:v1:*:ip:*");
        assertThat(ipKeys).hasSize(300);
        for (String key : ipKeys) assertThat(redis.opsForValue().get(key)).isEqualTo("1");
    }

    @Test void readOnlyQueriesNeverWriteOrExtendExpiry() {
        assertThat(quotas.query("192.0.2.1").remaining()).isEqualTo(30L);
        assertThat(redis.keys("vision:quota:v1:*")).isEmpty();
        quotas.reserve("192.0.2.1");
        long before = expiry(ipKey("192.0.2.1"));
        assertThat(quotas.query("192.0.2.1").remaining()).isEqualTo(29L);
        assertThat(expiry(ipKey("192.0.2.1"))).isEqualTo(before);
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo("1");
    }

    @Test void mappedIpv4AndIpv4UseSameQuotaBucket() {
        quotas.reserve("::ffff:192.0.2.1");
        quotas.reserve("192.0.2.1");
        assertThat(quotas.query("::ffff:192.0.2.1").used()).isEqualTo(2L);
        assertThat(redis.keys("vision:quota:v1:*:ip:*")).hasSize(1);
    }

    @Test void realExpiryDeletesBothCounters() throws Exception {
        // 直接执行同一脚本，以短期限验证 Redis 实际清理；Java 正常入口始终传上海次日零点。
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new org.springframework.core.io.ClassPathResource("redis/reserve-vision-quota.lua"));
        script.setResultType(Long.class);
        long now = Instant.now().getEpochSecond();
        assertThat(redis.execute(script, List.of(ipKey("192.0.2.1"), globalKey()),
                "5", "100", Long.toString(now - 1), Long.toString(now + 2))).isEqualTo(1L);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Boolean.TRUE.equals(redis.hasKey(globalKey())) && System.nanoTime() < deadline) Thread.sleep(50);
        assertThat(redis.hasKey(globalKey())).isFalse();
        assertThat(redis.hasKey(ipKey("192.0.2.1"))).isFalse();
    }

    @Test void oldDateCannotWriteAfterMidnightAndOldCountersDoNotAffectToday() {
        var yesterday = day.minusDays(1);
        String old = keys.dailyGlobalKey(yesterday);
        redis.opsForValue().set(old, "100");
        quotas.reserve("192.0.2.1");
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo("1");
        var stale = new VisionQuotaService(redis, config, keys,
                Clock.fixed(yesterday.atStartOfDay(VisionQuotaService.QUOTA_ZONE).toInstant(), ZoneOffset.UTC));
        assertThatThrownBy(() -> stale.reserve("192.0.2.2")).isInstanceOfSatisfying(VisionQuotaException.class,
                error -> assertThat(error.getCode()).isEqualTo("QUOTA_DAY_CHANGED"));
        assertThat(redis.opsForValue().get(old)).isEqualTo("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "1.5", "01", "2147483648", "999999999999999999999"})
    void badGlobalCounterDoesNotPartiallyCreateIpCounter(String count) {
        fixture(globalKey(), count);
        assertThatThrownBy(() -> quotas.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        assertThat(redis.hasKey(ipKey("192.0.2.1"))).isFalse();
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo(count);
    }

    @Test void wrongTypeMissingExpiryOrEarlyExpiryFailsBeforeAnyWrites() {
        redis.opsForList().rightPush(globalKey(), "fixture");
        assertThatThrownBy(() -> quotas.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        assertThat(redis.hasKey(ipKey("192.0.2.1"))).isFalse();
        redis.delete(globalKey());
        redis.opsForValue().set(globalKey(), "0");
        assertThatThrownBy(() -> quotas.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        redis.expire(globalKey(), Duration.ofSeconds(2));
        assertThatThrownBy(() -> quotas.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        assertThat(redis.hasKey(ipKey("192.0.2.1"))).isFalse();
    }

    @Test void missingGlobalWithExistingIpCannotSilentlyResetWholeSiteCount() {
        fixture(ipKey("192.0.2.1"), "2");
        assertThatThrownBy(() -> quotas.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        assertThat(redis.hasKey(globalKey())).isFalse();
    }

    private MockMultipartFile validImage() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new java.awt.image.BufferedImage(2, 3, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("image", "fictional.png", "image/png", bytes.toByteArray());
    }

    @Test void invalidImageNeverConsumesAndExhaustionNeverInvokesControlledClient() throws Exception {
        var admission = new VisionAdmissionService(new ImageValidationService(), quotas);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> admission.execute(new MockMultipartFile("image", new byte[]{1}),
                "192.0.2.1", calls::incrementAndGet)).isInstanceOf(ImageValidationException.class);
        assertThat(redis.keys("vision:quota:v1:*")).isEmpty();
        for (int i = 0; i < 30; i++) admission.execute(validImage(), "192.0.2.1", calls::incrementAndGet);
        assertThatThrownBy(() -> admission.execute(validImage(), "192.0.2.1", calls::incrementAndGet))
                .isInstanceOf(VisionQuotaException.class);
        assertThat(calls).hasValue(30);
    }

    @Test void callbackFailureIsNeitherRetriedNorRefundedAndGlobalDenialStopsClient() throws Exception {
        config.setGlobalDailyLimit(1);
        var admission = new VisionAdmissionService(new ImageValidationService(), quotas);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> admission.execute(validImage(), "192.0.2.1", () -> {
            calls.incrementAndGet(); throw new IllegalStateException("fictional client failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(redis.opsForValue().get(globalKey())).isEqualTo("1");
        assertThatThrownBy(() -> admission.execute(validImage(), "192.0.2.2", calls::incrementAndGet))
                .isInstanceOfSatisfying(VisionQuotaException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GLOBAL_QUOTA_EXHAUSTED"));
        assertThat(calls).hasValue(1);
        assertThat(redis.hasKey(ipKey("192.0.2.2"))).isFalse();
    }
}
