package jjie.job_spring.vision.service;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jjie.job_spring.config.VisionModelProperties;
import jjie.job_spring.vision.common.*;
import jjie.job_spring.vision.exception.VisionModelException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class GlmVisionClientTests {
    private HttpServer server;
    private ExecutorService executor;
    private VisionModelProperties config;
    private GlmVisionClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"jobTitle\\\":\\\"Java工程师\\\"}\",\"tool_calls\":null}}]}";
    private volatile String seenBody, authorization;
    private volatile boolean slow;
    @BeforeEach void setUp() throws Exception {
        config = new VisionModelProperties();
        config.setApiKey(UUID.randomUUID().toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/chat", exchange -> {
            calls.incrementAndGet();
            seenBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (status == -1) { exchange.close(); return; }
            exchange.getResponseHeaders().set("Location", "/redirect-target");
            try {
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, slow ? 0 : data.length);
                if (slow) { exchange.getResponseBody().write('{'); exchange.getResponseBody().flush(); Thread.sleep(1600); }
                exchange.getResponseBody().write(data);
            } catch (Exception ignored) { /* 超时取消连接是预期场景，不打印请求或响应。 */ }
            finally { exchange.close(); }
        });
        server.createContext("/redirect-target", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        client = new GlmVisionClient(config, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
    private ValidatedImage image() { return new ValidatedImage(new byte[]{1,2,3}, new ImageValidationResponse("PNG", "image/png", 3, 1, 1)); }

    @Test void sendsDefaultFreeModelBase64NoFilenameOrToolsAndReturnsContentOnly() {
        assertThat(client.extract(image())).isEqualTo("{\"jobTitle\":\"Java工程师\"}");
        var request = JsonMapper.builder().build().readTree(seenBody);
        assertThat(request.path("model").asString()).isEqualTo("glm-4.1v-thinking-flash");
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.has("thinking")).isFalse();
        assertThat(request.path("messages").get(1).path("content").get(0).path("image_url").path("url").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(image().bytes()));
        assertThat(request.has("tools")).isFalse();
        assertThat(authorization).isEqualTo("Bearer " + config.getApiKey());
        assertThat(calls).hasValue(1);
        assertThat(config.toString()).doesNotContain(config.getApiKey());
    }
    @Test void explicit46vConfigurationDisablesThinkingWithoutAutomaticFallback() {
        config.setName("glm-4.6v-flash");
        client.extract(image());
        var request = JsonMapper.builder().build().readTree(seenBody);
        assertThat(request.path("model").asString()).isEqualTo("glm-4.6v-flash");
        assertThat(request.path("thinking").path("type").asString()).isEqualTo("disabled");
        assertThat(calls).hasValue(1);
    }
    @ParameterizedTest @ValueSource(strings={"glm-4.6v", "glm-5", "glm-4v-flash", "", "unknown"})
    void unsupportedOrPaidModelsNeverSendHttpRequest(String name) {
        config.setName(name);
        assertThatThrownBy(() -> client.extract(image())).isInstanceOfSatisfying(VisionModelException.class,
                error -> assertThat(error.getCode()).isEqualTo("MODEL_NOT_CONFIGURED"));
        assertThat(calls).hasValue(0);
    }
    @ParameterizedTest @ValueSource(ints={301,302,307,308,400,401,403,429,500,503})
    void providerErrorsAreFixedRedactedAndNeverRedirectedOrRetried(int value) {
        status = value; body = "PRIVATE_PROVIDER_BODY " + config.getApiKey();
        assertThatThrownBy(() -> client.extract(image())).isInstanceOf(VisionModelException.class)
                .hasMessageNotContaining("PRIVATE_PROVIDER_BODY").hasMessageNotContaining(config.getApiKey()).hasNoCause();
        assertThat(calls).hasValue(1);
    }
    @ParameterizedTest @ValueSource(strings={"", "null", "[]", "{}", "{\"error\":{}}", "{bad", "{\"choices\":[]}",
        "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
        "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":[],\"tool_calls\":[]}}]}",
        "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\",\"tool_calls\":[{}]}}]}",
        "{\"choices\":[],\"choices\":[]}"})
    void invalidEnvelopesCannotBecomeDraft(String value) {
        body = value;
        assertThatThrownBy(() -> client.extract(image())).isInstanceOf(VisionModelException.class).hasNoCause();
    }
    @Test void oversizedResponseIsBounded() {
        body = "x".repeat(1024 * 1024 + 1);
        assertThatThrownBy(() -> client.extract(image())).isInstanceOf(VisionModelException.class).hasNoCause();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1113,MODEL_ACCOUNT_UNAVAILABLE", "1315,MODEL_KEY_TYPE_UNSUPPORTED",
            "1309,MODEL_KEY_TYPE_UNSUPPORTED", "1314,MODEL_KEY_TYPE_UNSUPPORTED", "1311,MODEL_PERMISSION_UNAVAILABLE",
            "1308,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1310,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1316,MODEL_PROVIDER_QUOTA_EXHAUSTED",
            "1317,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1318,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1319,MODEL_PROVIDER_QUOTA_EXHAUSTED",
            "1320,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1321,MODEL_PROVIDER_QUOTA_EXHAUSTED", "1302,MODEL_RATE_LIMITED", "1313,MODEL_RATE_LIMITED",
            "1305,MODEL_PROVIDER_BUSY"})
    void known429CodesGiveActionableMessagesWithoutProviderText(String code, String expected) {
        status = 429;
        body = "{\"error\":{\"code\":\""+code+"\",\"message\":\"PRIVATE_PROVIDER_MESSAGE\"}}";
        assertThatThrownBy(() -> client.extract(image())).isInstanceOfSatisfying(VisionModelException.class,
                error -> assertThat(error.getCode()).isEqualTo(expected))
                .hasMessageNotContaining("PRIVATE_PROVIDER_MESSAGE").hasNoCause();
        assertThat(calls).hasValue(1);
    }
    @Test void numeric429CodeIsRecognizedButUnknownCodeIsNeverEchoed() {
        status = 429; body = "{\"error\":{\"code\":1113}}";
        assertThatThrownBy(() -> client.extract(image())).isInstanceOfSatisfying(VisionModelException.class,
                error -> assertThat(error.getCode()).isEqualTo("MODEL_ACCOUNT_UNAVAILABLE"));
        body = "{\"error\":{\"code\":\"PRIVATE_UNKNOWN_CODE\"}}";
        assertThatThrownBy(() -> client.extract(image())).isInstanceOfSatisfying(VisionModelException.class,
                error -> assertThat(error.getCode()).isEqualTo("MODEL_RATE_LIMITED"))
                .hasMessageNotContaining("PRIVATE_UNKNOWN_CODE");
    }
    @Test void connectionClosedAfterReceivingPostIsNotRetried() {
        status = -1;
        assertThatThrownBy(() -> client.extract(image())).isInstanceOf(VisionModelException.class);
        assertThat(calls).hasValue(1);
    }
    @Test void deadlineCoversSlowBodyAfterHeadersAndDoesNotRetry() {
        config.setTimeoutSeconds(1); slow = true;
        long start = System.nanoTime();
        assertThatThrownBy(() -> client.extract(image())).isInstanceOfSatisfying(VisionModelException.class,
                error -> assertThat(error.getCode()).isEqualTo("MODEL_TIMEOUT"));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1500);
        assertThat(calls).hasValue(1);
    }
}
