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
        read(jobId).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].interviewTime").value("2026-10-21T14:30:00.123"));
        assertThat(jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId)).isEqualTo(jobBefore);
    }

    @Test
    void emptyOwnedJobIs200AndOptionalFieldsBecomeNull() throws Exception {
        read(jobId).andExpect(jsonPath("$.items").isEmpty());
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
        java.util.List<Integer> numbers = new java.util.ArrayList<>();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            String body = mvc.perform(get(path(jobId)).with(user(owner)).param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.totalPages").value(4))
                    .andReturn().getResponse().getContentAsString();
            numbers.add(JsonPath.read(body, "$.items[0].roundNo"));
            ids.add(((Number) JsonPath.read(body, "$.items[0].id")).longValue());
        }
        assertThat(numbers).containsExactly(1, 1, 2, 3);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.get(0)).isLessThan(ids.get(1));
        read(secondJob).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void paginationDefaultsEmptyMetadataAndOutOfRangePageAreConsistent() throws Exception {
        read(jobId).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        create(jobId, "{\"roundNo\":3}").andExpect(status().isCreated());
        create(jobId, "{\"roundNo\":9}").andExpect(status().isCreated());
        read(jobId).andExpect(jsonPath("$.items[0].roundNo").value(3));
        mvc.perform(get(path(jobId)).with(user(owner)).param("page", "2147483647").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].roundNo").value(9));
    }

    @Test
    void focusedRecordUsesItsSortedPositionAndCannotCrossJobsOrOwners() throws Exception {
        create(jobId, "{\"roundNo\":9}").andExpect(status().isCreated());
        long target = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=?", Long.class, jobId);
        create(jobId, "{\"roundNo\":3}").andExpect(status().isCreated());
        mvc.perform(get(path(jobId)).with(user(owner)).param("roundId", String.valueOf(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].id").value(target));
        mvc.perform(get(path(secondJob)).with(user(owner)).param("roundId", String.valueOf(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(path(otherJob)).with(user(other)).param("roundId", String.valueOf(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(path(jobId)).with(user(owner)).param("roundId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"page,0", "page,-1", "page,1.5", "page,2147483648", "page,DO_NOT_ECHO",
            "roundId,0", "roundId,-1", "roundId,1.5", "roundId,9223372036854775808", "roundId,DO_NOT_ECHO"})
    void invalidPageParametersAreSanitized400(String field, String value) throws Exception {
        String body = mvc.perform(get(path(jobId)).with(user(owner)).param(field, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields." + field).isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_ECHO", "NumberFormatException", "Failed to convert");
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
        read(jobId).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].review").value("原虚构复盘"));
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
        mvc.perform(get(path(otherJob)).with(user(other))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(path(jobId)).with(user(other)).param("userId", owner.getId().toString())).andExpect(status().isNotFound());
        read(secondJob).andExpect(jsonPath("$.items").isEmpty());
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
        read(jobId).andExpect(jsonPath("$.items[0].review").value("虚构复盘"));
    }

    @Test
    void databaseCompositeForeignKeyRejectsMismatchedOwner() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", other.getId(), jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isZero();
    }

    @Test
    void updatesFiveFieldsWithoutChangingIdentityOwnerOrOtherRows() throws Exception {
        create(jobId, "{\"roundNo\":1,\"review\":\"原复盘\"}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        create(secondJob, "{\"roundNo\":1}").andExpect(status().isCreated());
        Map<String, Object> otherBefore = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE job_application_id=?", secondJob);
        Map<String, Object> jobBefore = jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId);
        String values = "{\"roundNo\":3,\"interviewTime\":\"2026-10-21T18:30:45.123456\",\"roundType\":\" 技术二面 \",\"result\":\" 通过 \",\"review\":\" 新复盘 \"}";
        for (int i = 0; i < 2; i++) update(id, values).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.roundNo").value(3))
                .andExpect(jsonPath("$.interviewTime").value("2026-10-21T18:30:45.123"))
                .andExpect(jsonPath("$.roundType").value("技术二面")).andExpect(jsonPath("$.result").value("通过"))
                .andExpect(jsonPath("$.review").value("新复盘")).andExpect(jsonPath("$.userId").doesNotExist());
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE job_application_id=?", secondJob)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId)).isEqualTo(jobBefore);
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_rounds WHERE id=?", Long.class, id)).isEqualTo(owner.getId());
        read(jobId).andExpect(jsonPath("$.items[0].review").value("新复盘"));
    }

    @Test
    void omittedNullAndBlankUpdateFieldsReallyClearStoredValues() throws Exception {
        create(jobId, "{\"roundNo\":1,\"interviewTime\":\"2026-10-21T09:00:00\",\"roundType\":\"面试\",\"result\":\"结果\",\"review\":\"复盘\"}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        update(id, "{\"roundNo\":1,\"interviewTime\":null,\"roundType\":\"  \",\"result\":null}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE id=? AND interview_time IS NULL AND round_type IS NULL AND result IS NULL AND review IS NULL", Long.class, id)).isEqualTo(1);
    }

    @Test
    void conflictingRenumberReturns409AndLeavesAllContentUnchanged() throws Exception {
        create(jobId, "{\"roundNo\":1,\"review\":\"第一轮原复盘\"}").andExpect(status().isCreated());
        create(jobId, "{\"roundNo\":2,\"review\":\"第二轮原复盘\"}").andExpect(status().isCreated());
        var before = jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id");
        update(roundId(jobId, 1), "{\"roundNo\":2,\"review\":\"不能保存的草稿\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROUND"));
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(before);
    }

    @Test
    void historicalDuplicatesCanBeEditedIndividuallyAndRenumbered() throws Exception {
        create(jobId, "{\"roundNo\":1,\"review\":\"原第一条\"}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no,review) VALUES (?,?,1,'历史第二条')", owner.getId(), jobId);
        update(id, "{\"roundNo\":1,\"review\":\"只改当前一条\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT review FROM interview_rounds WHERE job_application_id=? AND id<>?", String.class, jobId, id)).isEqualTo("历史第二条");
        update(id, "{\"roundNo\":3,\"review\":\"改为第三轮\"}").andExpect(status().isOk());
        mvc.perform(get(path(jobId)).with(user(owner)).param("roundId", String.valueOf(id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.items[0].id").value(id));
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void nonexistentAndForeignUpdatesHaveIdentical404AndCannotChangeOwnerOrParent() throws Exception {
        create(jobId, "{\"roundNo\":1}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        String missing = update(Long.MAX_VALUE, "{\"roundNo\":1}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String foreign = mvc.perform(put("/api/interview-rounds/" + id).with(user(other)).with(csrf())
                        .param("userId", owner.getId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"review\":\"不能写入\"}"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreign).isEqualTo(missing);
        update(id, "{\"roundNo\":1,\"id\":9223372036854775807,\"userId\":%d,\"jobApplicationId\":%d,\"review\":\"允许的复盘\"}".formatted(other.getId(), otherJob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_rounds WHERE id=?", Long.class, id)).isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT job_application_id FROM interview_rounds WHERE id=?", Long.class, id)).isEqualTo(jobId);
        assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidUpdateFieldsDoNotChangeExistingRecord(Map<String, Object> values, String field) throws Exception {
        create(jobId, "{\"roundNo\":1,\"review\":\"原虚构内容\"}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        var before = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id);
        String body = update(id, json.writeValueAsString(values)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + field).isString()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_ECHO");
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"roundNo\":1.5}", "{\"roundNo\":\"1\"}", "{\"roundNo\":1,\"interviewTime\":\"2026-02-30T10:00:00\"}",
            "{\"roundNo\":1,\"interviewTime\":\"2026-10-21T10:00:00Z\"}"})
    void malformedUpdateDoesNotWrite(String body) throws Exception {
        create(jobId, "{\"roundNo\":1}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        var before = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id);
        update(id, body).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id)).isEqualTo(before);
    }

    @Test
    void updateRequiresLoginAndCsrf() throws Exception {
        create(jobId, "{\"roundNo\":1}").andExpect(status().isCreated());
        long id = roundId(jobId, 1);
        var before = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id);
        mvc.perform(put("/api/interview-rounds/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/interview-rounds/" + id).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/interview-rounds/" + id).with(user(owner)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-id", "9223372036854775808"})
    void malformedUpdateIdIs400(String id) throws Exception {
        mvc.perform(put("/api/interview-rounds/" + id).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1}"))
                .andExpect(status().isBadRequest());
        assertThat(count()).isZero();
    }

    private long roundId(Long job, int number) {
        return jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=? AND round_no=?", Long.class, job, number);
    }

    private ResultActions update(Long id, String body) throws Exception {
        return mvc.perform(put("/api/interview-rounds/" + id).with(user(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
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
