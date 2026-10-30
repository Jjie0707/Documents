package jjie.job_spring.vision.service;

import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Stream;
import jjie.job_spring.vision.common.JobRecognitionDraft;
import jjie.job_spring.vision.exception.RecognitionResultException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecognitionResultService {
    // Java String 的 UTF-16 单元上限，足够容纳十字段上限及 Unicode 转义形式。
    public static final int MAX_JSON_CHARS = 300000;
    private static final Set<String> FIELDS = Set.of("companyName", "jobTitle", "city", "salary",
            "responsibilities", "requirements", "educationRequirement", "sourcePlatform",
            "originalUrl", "applicationDeadline");

    // 只用于模型结果，不修改 Spring 的全局 ObjectMapper 或现有岗位 API 行为。
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(4).maxStringLength(MAX_JSON_CHARS)
                            .maxNameLength(64).maxNumberLength(128).build())
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final Validator validator;

    public RecognitionResultService(Validator validator) {
        this.validator = validator;
    }

    public JobRecognitionDraft parseAndValidate(String content) {
        if (content == null) throw invalidResult();
        if (content.length() > MAX_JSON_CHARS) {
            throw new RecognitionResultException("RECOGNITION_RESULT_TOO_LARGE", "识别结果过长，请换一张内容较少的截图或手动填写");
        }
        if (content.isBlank()) throw invalidResult();
        JobRecognitionDraft draft;
        try {
            JsonNode root = json.readTree(content);
            if (root == null || !root.isObject()) throw invalidResult();
            for (var field : root.properties()) {
                // 先检查类型，避免把数字、布尔或数组自动转换为字符串。
                if (!FIELDS.contains(field.getKey()) || (!field.getValue().isNull() && !field.getValue().isString())) {
                    throw invalidResult();
                }
            }
            draft = json.treeToValue(root, JobRecognitionDraft.class);
        } catch (JacksonException exception) {
            // 不附带原异常、源 JSON、字段值或底层解析信息。
            throw invalidResult();
        }
        // 普通 Service 方法不会自动触发 @Valid，必须主动调用 Bean Validation。
        if (!validator.validate(draft).isEmpty()) throw invalidResult();
        if (Stream.of(draft.companyName(), draft.jobTitle(), draft.city(), draft.salary(),
                draft.responsibilities(), draft.requirements(), draft.educationRequirement(),
                draft.sourcePlatform(), draft.originalUrl(), draft.applicationDeadline()).allMatch(value -> value == null)) {
            throw new RecognitionResultException("EMPTY_RECOGNITION_RESULT", "未识别到有效岗位内容，请换一张截图或手动填写");
        }
        return draft;
    }

    private RecognitionResultException invalidResult() {
        return new RecognitionResultException("INVALID_RECOGNITION_RESULT", "识别结果格式不正确，请换一张截图或手动填写");
    }
}
