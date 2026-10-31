package jjie.job_spring.vision;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import jjie.job_spring.config.VisionQuotaProperties;
import jjie.job_spring.vision.service.VisionQuotaKeyService;
import jjie.job_spring.vision.service.VisionQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VisionQuotaServiceTests {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private VisionQuotaProperties properties;
    private VisionQuotaKeyService keys;
    private VisionQuotaService service;
    private static final String IP = "192.0.2.10";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new VisionQuotaProperties();
        properties.setHmacSecret(UUID.randomUUID().toString());
        keys = new VisionQuotaKeyService(properties);
        service = at("2026-10-30T15:59:59Z");
    }

    private VisionQuotaService at(String instant) {
        return new VisionQuotaService(redis, properties, keys, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void missingCounterAndRepeatedQueriesNeverCreateIncrementOrExpireKeys() {
        for (int i = 0; i < 2; i++) {
            var response = service.query(IP);
            assertThat(response.available()).isTrue();
            assertThat(response.used()).isZero();
            assertThat(response.remaining()).isEqualTo(30L);
        }
        verify(redis, times(2)).opsForValue();
        verify(values, times(2)).get(keys.dailyIpKey(LocalDate.of(2026, 10, 30), IP));
        verifyNoMoreInteractions(redis, values);
    }

    @Test
    void reportsUsedCountAndConfiguredLimit() {
        properties.setDailyLimit(7);
        when(values.get(anyString())).thenReturn("2");
        var response = service.query(IP);
        assertThat(response.dailyLimit()).isEqualTo(7);
        assertThat(response.used()).isEqualTo(2L);
        assertThat(response.remaining()).isEqualTo(5L);
        assertThat(response.reason()).isEqualTo("AVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"30", "31", "9223372036854775807"})
    void exhaustedCountNeverReturnsNegativeRemaining(String count) {
        when(values.get(anyString())).thenReturn(count);
        var response = service.query(IP);
        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo("EXHAUSTED");
        assertThat(response.remaining()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "abc", "", " 1", "1 ", "01", "+1", "9223372036854775808", "99999999999999999999"})
    void malformedCountersFailClosedWithoutPretendingUnused(String count) {
        when(values.get(anyString())).thenReturn(count);
        var response = service.query(IP);
        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo("INVALID_COUNTER");
        assertThat(response.used()).isNull();
        assertThat(response.remaining()).isNull();
    }

    @Test
    void redisFailureDoesNotExposeConnectionDetails() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("PRIVATE_CONNECTION_DETAIL"));
        var response = service.query(IP);
        assertThat(response.reason()).isEqualTo("STORE_UNAVAILABLE");
        assertThat(response.available()).isFalse();
        assertThat(response.used()).isNull();
        assertThat(response.remaining()).isNull();
        assertThat(response.toString()).doesNotContain("PRIVATE_CONNECTION_DETAIL", IP, properties.getHmacSecret());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "                                "})
    void missingOrWeakSecretDoesNotContactRedis(String secret) {
        properties.setHmacSecret(secret);
        assertThat(service.query(IP).reason()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(redis);
    }

    @Test
    void nullSecretAndInvalidLimitFailClosed() {
        properties.setHmacSecret(null);
        assertThat(service.query(IP).available()).isFalse();
        properties.setHmacSecret(UUID.randomUUID().toString());
        properties.setDailyLimit(0);
        assertThat(service.query(IP).reason()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(redis);
    }

    @Test
    void missingClientDoesNotShareAnAnonymousBucket() {
        assertThat(service.query(null).reason()).isEqualTo("CLIENT_UNAVAILABLE");
        assertThat(service.query(" ").reason()).isEqualTo("CLIENT_UNAVAILABLE");
        verifyNoInteractions(redis);
    }

    @Test
    void shanghaiMidnightChangesBothKeyAndResetTime() {
        assertThat(service.query(IP).resetAt().toString()).isEqualTo("2026-10-31T00:00+08:00");
        var next = at("2026-10-30T16:00:00Z").query(IP);
        assertThat(next.resetAt().toString()).isEqualTo("2026-11-01T00:00+08:00");
        verify(values).get(keys.dailyIpKey(LocalDate.of(2026, 10, 30), IP));
        verify(values).get(keys.dailyIpKey(LocalDate.of(2026, 10, 31), IP));
    }

    @Test
    void hmacIsStableWithinDayButSeparatesClientsDatesAndSecrets() {
        var day = LocalDate.of(2026, 10, 30);
        String key = keys.dailyIpKey(day, IP);
        assertThat(key).matches("vision:quota:v1:\\{2026-10-30}:ip:[0-9a-f]{64}");
        assertThat(key).doesNotContain(IP, properties.getHmacSecret());
        assertThat(keys.dailyIpKey(day, IP)).isEqualTo(key);
        assertThat(keys.dailyIpKey(day, "192.0.2.11")).isNotEqualTo(key);
        assertThat(keys.dailyIpKey(day.plusDays(1), IP).substring(32)).isNotEqualTo(key.substring(32));
        properties.setHmacSecret(UUID.randomUUID().toString());
        assertThat(keys.dailyIpKey(day, IP)).isNotEqualTo(key);
        assertThat(properties.toString()).doesNotContain(properties.getHmacSecret());
    }
}
