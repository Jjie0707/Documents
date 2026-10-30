package jjie.job_spring.vision;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jjie.job_spring.job.common.CreateJobRequest;
import jjie.job_spring.vision.common.JobRecognitionDraft;
import jjie.job_spring.vision.exception.RecognitionResultException;
import jjie.job_spring.vision.service.RecognitionResultService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecognitionResultServiceTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Integer> TEXT_LIMITS = Map.of(
            "companyName", 200, "jobTitle", 200, "city", 100, "salary", 100,
            "responsibilities", 10000, "requirements", 10000,
            "educationRequirement", 100, "sourcePlatform", 100, "originalUrl", 2048);
    private final RecognitionResultService service = new RecognitionResultService(FACTORY.getValidator());

    @AfterAll
    static void closeValidationFactory() { FACTORY.close(); }

    @Test
    void mapsAllTenFieldsAndPreservesInternalMultilineContent() {
        var draft = service.parseAndValidate("""
                {"companyName":"　 虚构公司 ", "jobTitle":" Java工程师 ",
                 "city":" 示例城市 ", "salary":" 面议 ",
                 "responsibilities":" 开发接口\\n  编写测试 ", "requirements":" Java\\nSQL ",
                 "educationRequirement":" 本科 ", "sourcePlatform":" 示例平台 ",
                 "originalUrl":" HTTPS://example.com/招聘?id=demo#岗位 ",
                 "applicationDeadline":" 2024-02-29 "}
                """);
        assertThat(draft).isEqualTo(new JobRecognitionDraft("虚构公司", "Java工程师", "示例城市", "面议",
                "开发接口\n  编写测试", "Java\nSQL", "本科", "示例平台", "HTTPS://example.com/招聘?id=demo#岗位", LocalDate.of(2024, 2, 29)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyName", "jobTitle", "city", "salary", "responsibilities", "requirements", "educationRequirement", "sourcePlatform", "originalUrl"})
    void acceptsPartialDraftWithoutInventingMissingFields(String field) {
        String value = field.equals("originalUrl") ? "https://example.com/job" : "虚构内容";
        var result = JSON.valueToTree(service.parseAndValidate(JSON.writeValueAsString(Map.of(field, value))));
        assertThat(result.get(field).stringValue()).isEqualTo(value);
        assertThat(result.properties().stream().filter(entry -> !entry.getValue().isNull())).hasSize(1);
    }

    @Test
    void nullAndWhitespaceAreNormalizedWithoutRequiringCompanyOrJobTitle() {
        var draft = service.parseAndValidate("""
                {"companyName":null,"jobTitle":"　 ","city":"","salary":"\\t",
                 "responsibilities":"  虚构职责  ","requirements":" ","educationRequirement":null,
                 "sourcePlatform":"","originalUrl":"　 ","applicationDeadline":" "}
                """);
        assertThat(draft.responsibilities()).isEqualTo("虚构职责");
        assertThat(JSON.valueToTree(draft).properties().stream().filter(entry -> !entry.getValue().isNull())).hasSize(1);
        var saveRequest = new CreateJobRequest(null, null, null, null, draft.responsibilities(), null, null, null, null, null);
        assertThat(FACTORY.getValidator().validate(saveRequest)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("companyName", "jobTitle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"companyName\":null}", "{\"companyName\":\"　 \",\"applicationDeadline\":\" \"}"})
    void distinguishesNoContentFromMalformedResult(String content) {
        assertCode(content, "EMPTY_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"　 ", "null", "[]", "42", "true", "\"text\"", "{", "{\"companyName\":}",
            "```json\n{\"companyName\":\"demo\"}\n```", "前言{\"companyName\":\"demo\"}",
            "{\"companyName\":\"demo\"} 后记", "{\"companyName\":\"demo\"} {}",
            "{\"companyName\":\"demo\"} null", "{'companyName':'demo'}", "{companyName:\"demo\"}",
            "{\"companyName\":\"demo\",}", "{/*comment*/\"companyName\":\"demo\"}"})
    void rejectsMalformedJsonNonObjectsAndAdditionalOutput(String content) {
        assertCode(content, "INVALID_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyName", "jobTitle", "city", "salary", "responsibilities", "requirements", "educationRequirement", "sourcePlatform", "originalUrl", "applicationDeadline"})
    void rejectsNumericCoercionForEveryField(String field) {
        assertCode("{\"" + field + "\":123}", "INVALID_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "[]", "[\"Java\"]", "{}", "{\"value\":\"Java\"}", "1.25"})
    void rejectsNonStringValuesInsteadOfStringifyingThem(String value) {
        assertCode("{\"requirements\":" + value + "}", "INVALID_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "userId", "status", "confidence", "draft", "CompanyName", "PRIVATE_UNKNOWN_FIELD"})
    void rejectsUnknownAndOwnershipFields(String field) {
        assertCode(JSON.writeValueAsString(Map.of("companyName", "demo", field, "PRIVATE_VALUE")), "INVALID_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"companyName\":\"first\",\"companyName\":\"second\"}",
            "{\"companyName\":\"same\",\"companyName\":\"same\"}",
            "{\"companyName\":null,\"companyName\":\"demo\"}",
            "{\"companyName\":\"demo\",\"company\\u004eame\":\"other\"}"})
    void rejectsDuplicateKeysIncludingEscapedNames(String content) {
        assertCode(content, "INVALID_RECOGNITION_RESULT");
    }

    @Test
    void enforcesEveryTextLimitInUnicodeCodePointsWithoutTruncation() {
        for (var field : TEXT_LIMITS.entrySet()) {
            String prefix = field.getKey().equals("originalUrl") ? "https://example.com/" : "";
            String boundary = prefix + "😀".repeat(field.getValue() - prefix.length());
            var result = JSON.valueToTree(service.parseAndValidate(JSON.writeValueAsString(Map.of(field.getKey(), "  " + boundary + "　"))));
            assertThat(result.get(field.getKey()).stringValue()).isEqualTo(boundary);
            assertCode(JSON.writeValueAsString(Map.of(field.getKey(), boundary + "a")), "INVALID_RECOGNITION_RESULT");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2025-02-29", "0999-12-31", "10000-01-01", "2026-1-01", "2026/01/01",
            "2026-01-01T00:00:00", "长期招聘", "10月30日", "null"})
    void rejectsInvalidOrIncompleteDates(String value) {
        assertCode(JSON.writeValueAsString(Map.of("applicationDeadline", value)), "INVALID_RECOGNITION_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01-01", "2024-02-29", "9999-12-31"})
    void acceptsValidDatesIncludingPastDates(String value) {
        assertThat(service.parseAndValidate(JSON.writeValueAsString(Map.of("applicationDeadline", value))).applicationDeadline())
                .isEqualTo(LocalDate.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//example.com/job", "www.example.com", "https://user:PRIVATE_VALUE@example.com/job",
            "https://example.com:", "https://example.com:65536/job", "https://example.com/a b"})
    void rejectsInvalidUrlsWithoutAccessingThem(String url) {
        assertCode(JSON.writeValueAsString(Map.of("originalUrl", url)), "INVALID_RECOGNITION_RESULT");
    }

    @Test
    void responseContainsExactlyTenFieldsAndNoRawTextOrEntityMetadata() {
        var result = JSON.valueToTree(service.parseAndValidate("{\"companyName\":\"demo\"}"));
        assertThat(result.propertyNames()).containsExactlyInAnyOrderElementsOf(Set.of("companyName", "jobTitle", "city", "salary",
                "responsibilities", "requirements", "educationRequirement", "sourcePlatform", "originalUrl", "applicationDeadline"));
        assertThat(result.get("sourcePlatform").isNull()).isTrue();
        assertThat(result.get("applicationDeadline").isNull()).isTrue();
    }

    @Test
    void maximumValidFieldsFitEvenWhenEachSupplementaryCharacterIsEscaped() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var field : TEXT_LIMITS.entrySet()) {
            String prefix = field.getKey().equals("originalUrl") ? "https://example.com/" : "";
            fields.put(field.getKey(), prefix + "😀".repeat(field.getValue() - prefix.length()));
        }
        fields.put("applicationDeadline", "9999-12-31");
        String content = JSON.writeValueAsString(fields).replace("😀", "\\ud83d\\ude00");
        assertThat(content.length()).isLessThan(RecognitionResultService.MAX_JSON_CHARS);
        var draft = service.parseAndValidate(content);
        assertThat(draft.responsibilities().codePointCount(0, draft.responsibilities().length())).isEqualTo(10000);
    }

    @Test
    void boundsRawResultSizeAndRejectsDeeplyNestedInput() {
        String content = "{\"companyName\":\"demo\"}";
        String exact = content + " ".repeat(RecognitionResultService.MAX_JSON_CHARS - content.length());
        assertThat(service.parseAndValidate(exact).companyName()).isEqualTo("demo");
        assertCode(exact + " ", "RECOGNITION_RESULT_TOO_LARGE");
        assertCode("{\"companyName\":" + "[".repeat(100) + "null" + "]".repeat(100) + "}", "INVALID_RECOGNITION_RESULT");
    }

    @Test
    void errorsAndDraftToStringNeverExposeModelContentOrParserCause() {
        String malformed = "{\"companyName\":\"PRIVATE_MODEL_CONTENT\", invalid}";
        var error = catchThrowableOfType(RecognitionResultException.class, () -> service.parseAndValidate(malformed));
        assertThat(error.getCause()).isNull();
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        assertThat(trace.toString()).doesNotContain("PRIVATE_MODEL_CONTENT", "JsonParseException", "Source:");
        assertThat(service.parseAndValidate("{\"companyName\":\"PRIVATE_MODEL_CONTENT\"}").toString())
                .isEqualTo("JobRecognitionDraft[content=REDACTED]");
    }

    private void assertCode(String content, String code) {
        assertThatThrownBy(() -> service.parseAndValidate(content)).isInstanceOf(RecognitionResultException.class)
                .hasMessageNotContaining("PRIVATE").hasCause(null).extracting("code").isEqualTo(code);
    }
}
