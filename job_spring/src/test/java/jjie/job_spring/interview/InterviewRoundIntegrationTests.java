package jjie.job_spring.interview;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:interview-rounds;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/interview-rounds-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class InterviewRoundIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails owner;
    private AppUserDetails other;
    private Long jobId;
    private Long otherJob;
    private Long secondJob;

    @BeforeEach
    void seedIsolatedDatabase() {
        owner = seedUser("round_demo_a");
        other = seedUser("round_demo_b");
        jobId = seedJob(owner, "虚构公司A");
        secondJob = seedJob(owner, "虚构公司A另一岗位");
        otherJob = seedJob(other, "虚构公司B");
    }

    @Test
    void createsNormalizesAndReadsRoundWithoutChangingJob() throws Exception {
        Map<String, Object> jobBefore = jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId);
        String body = create(jobId, json.writeValueAsString(Map.of(
                "roundNo", 1, "interviewTime", "2026-10-21T14:30:00.123456",
                "roundType", " 技术面 ", "result", " 待反馈 ", "review", "  第一行\n第二行  ")))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.roundNo").value(1))
                .andExpect(jsonPath("$.roundType").value("技术面"))
                .andExpect(jsonPath("$.result").value("待反馈"))
                .andExpect(jsonPath("$.review").value("第一行\n第二行"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.jobApplicationId").doesNotExist())
                .andExpect(jsonPath("$.interviewTime").value("2026-10-21T14:30:00.123"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_rounds WHERE id=?", Long.class, id)).isEqualTo(owner.getId());
        read(jobId).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].interviewTime").value("2026-10-21T14:30:00.123"));
        assertThat(jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId)).isEqualTo(jobBefore);
    }

    @Test
    void emptyOwnedJobIs200AndOptionalFieldsBecomeNull() throws Exception {
        read(jobId).andExpect(jsonPath("$").isEmpty());
        create(jobId, "{\"roundNo\":1}").andExpect(status().isCreated());
        create(jobId, "{\"roundNo\":2,\"interviewTime\":null,\"roundType\":\"  \",\"result\":\"\\t\",\"review\":\"\\n\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE interview_time IS NULL AND round_type IS NULL AND result IS NULL AND review IS NULL", Long.class)).isEqualTo(2);
    }

    @Test
    void sortsByRoundNumberThenIdAndPreservesHistoricalDuplicateRecords() throws Exception {
        for (int roundNo : new int[]{3, 1, 2}) create(jobId, "{\"roundNo\":" + roundNo + "}").andExpect(status().isCreated());
        // 模拟本次修正前已有的重复数据；读取不能静默隐藏、合并或删除历史记录。
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no,review) VALUES (?,?,1,'历史虚构复盘')", owner.getId(), jobId);
        String body = read(jobId)
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<Integer>>read(body, "$[*].roundNo")).containsExactly(1, 1, 2, 3);
        java.util.List<Number> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids.get(0).longValue()).isLessThan(ids.get(1).longValue());
        read(secondJob).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void repeatedRoundIs409WithoutChangingExistingContentOrInserting() throws Exception {
        create(jobId, "{\"roundNo\":1,\"result\":\"原测试结果\",\"review\":\"原虚构复盘\"}").andExpect(status().isCreated());
        java.util.List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id");
        create(jobId, "{\"roundNo\":1,\"result\":\"DO_NOT_ECHO\",\"review\":\"替换内容\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROUND"))
                .andExpect(jsonPath("$.fields.roundNo").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DO_NOT_ECHO"))));
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(before);
        read(jobId).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].review").value("原虚构复盘"));
    }

    @Test
    void sameNumberCanBeUsedOnDifferentOwnedJobsAndByAnotherOwner() throws Exception {
        create(jobId, "{\"roundNo\":1}").andExpect(status().isCreated());
        create(secondJob, "{\"roundNo\":1}").andExpect(status().isCreated());
        mvc.perform(post(path(otherJob)).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1}"))
                .andExpect(status().isCreated());
        // 别人的岗位即使已有同号轮次，仍然返回 404，不能泄露重复信息。
        create(otherJob, "{\"roundNo\":1}").andExpect(status().isNotFound());
        assertThat(count()).isEqualTo(3);
    }

    @Test
    void acceptsUnicodeLengthAndDateBoundaries() throws Exception {
        create(jobId, json.writeValueAsString(Map.of("roundNo", Integer.MAX_VALUE,
                "roundType", "类".repeat(50), "result", "果".repeat(100), "review", "😀".repeat(2000),
                "interviewTime", "1000-01-01T00:00:00"))).andExpect(status().isCreated());
        create(jobId, "{\"roundNo\":1,\"interviewTime\":\"9999-12-31T23:59:59.999\"}").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidFieldsReturn400WithoutInsertingOrEchoing(Map<String, Object> values, String field) throws Exception {
        String response = create(jobId, json.writeValueAsString(values)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + field).isString()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO");
        assertThat(count()).isZero();
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of(Map.of("review", "DO_NOT_ECHO"), "roundNo"),
                Arguments.of(Map.of("roundNo", 0, "review", "DO_NOT_ECHO"), "roundNo"),
                Arguments.of(Map.of("roundNo", -1, "review", "DO_NOT_ECHO"), "roundNo"),
                Arguments.of(Map.of("roundNo", 1, "roundType", "😀".repeat(51), "review", "DO_NOT_ECHO"), "roundType"),
                Arguments.of(Map.of("roundNo", 1, "result", "果".repeat(101), "review", "DO_NOT_ECHO"), "result"),
                Arguments.of(Map.of("roundNo", 1, "review", "DO_NOT_ECHO".repeat(200)), "review"),
                Arguments.of(Map.of("roundNo", 1, "interviewTime", "0999-12-31T23:59:59", "review", "DO_NOT_ECHO"), "interviewTime"),
                Arguments.of(Map.of("roundNo", 1, "interviewTime", "+10000-01-01T00:00:00", "review", "DO_NOT_ECHO"), "interviewTime")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"roundNo\":null}", "{\"roundNo\":2147483648}", "{\"roundNo\":1.5}", "{\"roundNo\":\"1\"}", "{\"roundNo\":true}",
            "{\"roundNo\":\"DO_NOT_ECHO\"}", "{\"roundNo\":1,\"interviewTime\":\"2026-02-30T10:00:00\"}",
            "{\"roundNo\":1,\"interviewTime\":\"DO_NOT_ECHO\"}",
            "{\"roundNo\":1,\"interviewTime\":\"2026-10-21T10:00:00Z\"}",
            "{\"roundNo\":1,\"interviewTime\":\"2026-10-21T10:00:00+08:00\"}",
            "{\"roundNo\":1,\"interviewTime\":[2026,10,21,10,0]}",
            "{\"roundNo\":1,\"interviewTime\":\"\"}"})
    void malformedJsonNumberAndDateAreRejected(String body) throws Exception {
        String response = create(jobId, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", "Exception", "jdbc:");
        assertThat(count()).isZero();
    }

    @Test
    void missingAndForeignJobsHaveSame404ForBothEndpoints() throws Exception {
        String missingRead = mvc.perform(get(path(Long.MAX_VALUE)).with(user(owner))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String foreignRead = mvc.perform(get(path(otherJob)).with(user(owner))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(foreignRead).isEqualTo(missingRead);
        String missingCreate = create(Long.MAX_VALUE, "{\"roundNo\":1}").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String foreignCreate = create(otherJob, "{\"roundNo\":1}").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreignCreate).isEqualTo(missingCreate);
        assertThat(count()).isZero();
    }

    @Test
    void ownerAndJobCannotBeForgedAndListsStayIsolated() throws Exception {
        mvc.perform(post(path(jobId)).with(user(owner)).with(csrf()).param("userId", other.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"userId\":%d,\"jobApplicationId\":%d,\"id\":9223372036854775807}".formatted(other.getId(), otherJob)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_rounds", Long.class)).isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT job_application_id FROM interview_rounds", Long.class)).isEqualTo(jobId);
        assertThat(jdbc.queryForObject("SELECT id FROM interview_rounds", Long.class)).isNotEqualTo(Long.MAX_VALUE);
        mvc.perform(get(path(otherJob)).with(user(other))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path(jobId)).with(user(other)).param("userId", owner.getId().toString())).andExpect(status().isNotFound());
        read(secondJob).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void loginAndCsrfAreRequired() throws Exception {
        mvc.perform(get(path(jobId))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(jobId)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(jobId)).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path(jobId)).with(user(owner)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isForbidden());
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void badPathIdIs400ForBothEndpoints(String id) throws Exception {
        mvc.perform(get("/api/jobs/" + id + "/interview-rounds").with(user(owner))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/jobs/" + id + "/interview-rounds").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isBadRequest());
        assertThat(count()).isZero();
    }

    @Test
    void createdRoundBlocksJobDeletionAndRemainsReadable() throws Exception {
        create(jobId, "{\"roundNo\":1,\"review\":\"虚构复盘\"}").andExpect(status().isCreated());
        mvc.perform(delete("/api/jobs/" + jobId).with(user(owner)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get("/api/jobs/" + jobId).with(user(owner))).andExpect(status().isOk());
        read(jobId).andExpect(jsonPath("$[0].review").value("虚构复盘"));
    }

    @Test
    void databaseCompositeForeignKeyRejectsMismatchedOwner() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", other.getId(), jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isZero();
    }

    private ResultActions create(Long id, String body) throws Exception {
        return mvc.perform(post(path(id)).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions read(Long id) throws Exception {
        return mvc.perform(get(path(id)).with(user(owner))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private static String path(Long id) { return "/api/jobs/" + id + "/interview-rounds"; }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds", Long.class); }

    private AppUserDetails seedUser(String username) {
        String credential = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, credential);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username); account.setPasswordHash(credential);
        return new AppUserDetails(account);
    }

    private Long seedJob(AppUserDetails user, String company) {
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title,status) VALUES (?,?,?,?)", user.getId(), company, "虚构轮次岗位", "APPLIED");
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=?", Long.class, user.getId(), company);
    }
}
