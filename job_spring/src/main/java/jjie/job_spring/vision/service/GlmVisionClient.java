package jjie.job_spring.vision.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import jjie.job_spring.config.VisionModelProperties;
import jjie.job_spring.vision.common.ValidatedImage;
import jjie.job_spring.vision.exception.VisionModelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GlmVisionClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final VisionModelProperties properties;
    private final HttpClient http;
    private final URI endpoint;
    private final String prompt;
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(300000).maxNameLength(128).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Autowired
    public GlmVisionClient(VisionModelProperties properties) {
        this(properties, URI.create("https://open.bigmodel.cn/api/paas/v4/chat/completions"));
    }

    // 包内构造器仅供本地受控HTTP测试；生产端点固定，型号仅允许Properties中的免费白名单。
    GlmVisionClient(VisionModelProperties properties, URI endpoint) {
        this.properties = properties;
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try { prompt = new ClassPathResource("vision-job-prompt.txt").getContentAsString(StandardCharsets.UTF_8); }
        catch (IOException exception) { throw new IllegalStateException("Vision prompt unavailable"); }
    }

    public String extract(ValidatedImage image) {
        if (!properties.isConfigured()) throw failure("MODEL_NOT_CONFIGURED", "识别服务配置不完整或型号不受支持，可继续手动填写");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getName());
        body.put("stream", false);
        body.put("max_tokens", 8192);
        // 4.1V自带思考，不能套用4.6V的关闭参数；不向页面返回思考内容。
        if ("glm-4.6v-flash".equals(properties.getName())) body.put("thinking", Map.of("type", "disabled"));
        body.put("messages", List.of(
                        Map.of("role", "system", "content", prompt),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", Base64.getEncoder().encodeToString(image.bytes()))),
                                Map.of("type", "text", "text", "提取这张招聘截图的十个岗位字段，只返回JSON。")))));
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, info -> new LimitedBody());
        try {
            // get 的期限覆盖响应体接收，不能只依赖“收到响应头”的超时。
            var response = future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw failure("MODEL_AUTH_FAILED", "识别服务配置或权限异常，请联系维护者或手动填写");
            if (response.statusCode() == 429)
                throw rateFailure(response.body());
            if (response.statusCode() != 200) throw unavailable();
            var root = json.readTree(response.body());
            if (root == null || !root.isObject() || root.has("error")) throw invalid();
            var choices = root.path("choices");
            if (!choices.isArray() || choices.size() != 1) throw invalid();
            var choice = choices.get(0);
            var message = choice.path("message");
            var tools = message.path("tool_calls");
            if (!choice.path("finish_reason").isString() || !"stop".equals(choice.path("finish_reason").asString())
                    || (!tools.isMissingNode() && !tools.isNull() && !(tools.isArray() && tools.isEmpty()))
                    || !message.path("content").isString()) throw invalid();
            return message.path("content").asString();
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw timeout();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof HttpTimeoutException) throw timeout();
            throw unavailable();
        } catch (tools.jackson.core.JacksonException exception) {
            throw invalid();
        }
    }

    private VisionModelException rateFailure(byte[] body) {
        // HTTP 429 也可能是账户/产品Key问题。只匹配已知官方码，不回传原文或任意字段。
        String code = "";
        try {
            var root = json.readTree(body);
            if (root != null) {
                var value = root.path("error").path("code");
                if (value.isString()) code = value.asString();
                else if (value.isIntegralNumber()) code = Long.toString(value.asLong());
            }
        } catch (tools.jackson.core.JacksonException ignored) { /* 未知错误保持通用提示。 */ }
        return switch (code) {
            case "1113" -> failure("MODEL_ACCOUNT_UNAVAILABLE", "模型标准API账户状态异常（供应商1113），请在智谱控制台核对账户状态；可继续手动填写");
            case "1315", "1309", "1314" -> failure("MODEL_KEY_TYPE_UNSUPPORTED", "当前Key对应的产品类型或套餐不适用，请核对模型标准API Key；可继续手动填写");
            case "1311" -> failure("MODEL_PERMISSION_UNAVAILABLE", "当前Key没有该模型的使用权限，请在智谱控制台核对；可继续手动填写");
            case "1308", "1310", "1316", "1317", "1318", "1319", "1320", "1321" ->
                    failure("MODEL_PROVIDER_QUOTA_EXHAUSTED", "供应商使用上限已到，请在智谱控制台查看重置时间；可继续手动填写");
            case "1302", "1313" -> failure("MODEL_RATE_LIMITED", "模型账号请求频率受限，请稍后重试或手动填写");
            case "1305" -> failure("MODEL_PROVIDER_BUSY", "该模型当前访问量较大，请稍后重试或手动填写");
            default -> failure("MODEL_RATE_LIMITED", "模型服务暂时繁忙，请稍后重试或手动填写");
        };
    }

    private static VisionModelException failure(String code, String message) { return new VisionModelException(code, message); }
    private static VisionModelException invalid() { return failure("INVALID_MODEL_RESPONSE", "模型返回结果不完整或格式异常，请换图或手动填写"); }
    private static VisionModelException timeout() { return failure("MODEL_TIMEOUT", "模型识别超时，次数可能已消耗；请勿连续重复提交，可继续手动填写"); }
    private static VisionModelException unavailable() { return failure("MODEL_UNAVAILABLE", "模型服务暂不可用，次数可能已消耗；可稍后重试或手动填写"); }

    /** 有界订阅者：边收边检查，超过1MiB立即取消，响应原文永不进入异常。 */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (var chunk : chunks) {
                if ((long) bytes.size() + chunk.remaining() > MAX_RESPONSE_BYTES) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Vision response too large")); return;
                }
                byte[] part = new byte[chunk.remaining()]; chunk.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
