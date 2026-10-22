package jjie.job_spring.interview;

import java.util.Map;
import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:interview-delete;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/interview-rounds-test.sql", "/sql/interview-delete-guards-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
// 不使用测试外层 @Transactional：让 Service 真实提交/回滚后，再由另一查询验证结果。
class InterviewDeletionIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails owner;
    private AppUserDetails other;
    private Long jobId;
    private Long secondJob;
    private Long foreignJob;
    private Long target;
    private Long sibling;
    private Long otherRound;
    private Long foreignRound;

    @BeforeEach
    void seedOnlyIsolatedH2() {
        for (String table : new String[]{"round_delete_guard", "job_delete_guard", "interview_questions", "interview_rounds", "job_applications", "users"}) {
            jdbc.update("DELETE FROM " + table); // 表名是固定测试常量，只清理本测试专用 H2。
        }
        owner = seedUser("delete_round_a"); other = seedUser("delete_round_b");
        jobId = seedJob(owner, "虚构删除公司A"); secondJob = seedJob(owner, "虚构对照公司A"); foreignJob = seedJob(other, "虚构公司B");
        target = seedRound(jobId, owner, 1); sibling = seedRound(jobId, owner, 2);
        otherRound = seedRound(secondJob, owner, 1); foreignRound = seedRound(foreignJob, other, 1);
    }

    @Test
    void deletesOnlyTargetAndItsQuestionsWithEmpty204() throws Exception {
        var siblingBefore = round(sibling); var otherBefore = round(otherRound); var foreignBefore = round(foreignRound);
        var otherQuestions = jdbc.queryForList("SELECT * FROM interview_questions WHERE interview_round_id<>? ORDER BY id", target);
        removeRound(target).andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(roundCount(target)).isZero(); assertThat(questionCount(target)).isZero();
        assertThat(round(sibling)).isEqualTo(siblingBefore); assertThat(round(otherRound)).isEqualTo(otherBefore);
        assertThat(round(foreignRound)).isEqualTo(foreignBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(otherQuestions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(3);
        mvc.perform(get("/api/jobs/" + jobId + "/interview-rounds").with(user(owner)).param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(sibling));
    }

    @Test
    void deletesRoundWithoutQuestionsAndLastRoundLeavesEmptyPage() throws Exception {
        jdbc.update("DELETE FROM interview_questions WHERE interview_round_id=?", otherRound);
        removeRound(otherRound).andExpect(status().isNoContent());
        mvc.perform(get("/api/jobs/" + secondJob + "/interview-rounds").with(user(owner)).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void sameNumberHistoryDoesNotChangeDeleteTarget() throws Exception {
        Long duplicate = seedRound(jobId, owner, 1);
        var before = round(duplicate);
        removeRound(target).andExpect(status().isNoContent());
        assertThat(round(duplicate)).isEqualTo(before); assertThat(questionCount(duplicate)).isEqualTo(1);
    }

    @Test
    void repeatedMissingAndForeignRoundDeletesReturnIdentical404() throws Exception {
        removeRound(target).andExpect(status().isNoContent());
        var before = jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id");
        String repeated = removeRound(target).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = removeRound(Long.MAX_VALUE).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String foreign = removeRound(foreignRound).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(repeated).isEqualTo(missing).isEqualTo(foreign).contains("ROUND_NOT_FOUND");
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(before);
        assertThat(roundCount(foreignRound)).isEqualTo(1);
    }

    @Test
    void forgedQueryAndBodyCannotSwitchOwnerOrTarget() throws Exception {
        mvc.perform(delete("/api/interview-rounds/" + foreignRound).with(user(owner)).with(csrf())
                        .param("userId", other.getId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"id\":%d}".formatted(other.getId(), target)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/interview-rounds/" + target).with(user(owner)).with(csrf())
                        .param("userId", other.getId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"id\":%d}".formatted(other.getId(), foreignRound)))
                .andExpect(status().isNoContent());
        assertThat(roundCount(target)).isZero(); assertThat(roundCount(foreignRound)).isEqualTo(1);
        assertThat(questionCount(foreignRound)).isEqualTo(1);
    }

    @Test
    void missingLoginOrCsrfCannotDeleteAnything() throws Exception {
        mvc.perform(delete("/api/interview-rounds/" + target).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/interview-rounds/" + target).with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/interview-rounds/" + target).with(user(owner)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(roundCount(target)).isEqualTo(1); assertThat(questionCount(target)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "9223372036854775808"})
    void malformedIdReturnsSanitized400(String id) throws Exception {
        String response = mvc.perform(delete("/api/interview-rounds/" + id).with(user(owner)).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("NumberFormatException", "Failed to convert");
        assertThat(roundCount(target)).isEqualTo(1);
    }

    @Test
    void failedRoundDeleteRollsBackAlreadyDeletedQuestions() throws Exception {
        jdbc.update("INSERT INTO round_delete_guard(round_id) VALUES (?)", target);
        var roundBefore = round(target);
        var questionsBefore = jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id");
        String response = removeRound(target).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("round_delete_guard", "FOREIGN KEY", "jdbc:");
        assertThat(round(target)).isEqualTo(roundBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(questionsBefore);
    }

    @Test
    void directSqlStillHonorsRestrictInsteadOfDatabaseCascade() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM job_applications WHERE id=?", jobId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM interview_rounds WHERE id=?", target))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(roundCount(target)).isEqualTo(1); assertThat(questionCount(target)).isEqualTo(1);
    }

    @Test
    void deletingJobClearsAllItsRoundsAndQuestionsButKeepsOtherData() throws Exception {
        var othersBefore = jdbc.queryForList("SELECT * FROM interview_rounds WHERE job_application_id<>? ORDER BY id", jobId);
        var otherQuestions = jdbc.queryForList("SELECT * FROM interview_questions WHERE interview_round_id NOT IN (SELECT id FROM interview_rounds WHERE job_application_id=?) ORDER BY id", jobId);
        mvc.perform(delete("/api/jobs/" + jobId).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, jobId)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(othersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(otherQuestions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(2);
    }

    @Test
    void failedJobDeleteRollsBackAllRoundsAndQuestions() throws Exception {
        jdbc.update("INSERT INTO job_delete_guard(job_id) VALUES (?)", jobId);
        var jobsBefore = jdbc.queryForList("SELECT * FROM job_applications ORDER BY id");
        var roundsBefore = jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id");
        var questionsBefore = jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id");
        String response = mvc.perform(delete("/api/jobs/" + jobId).with(user(owner)).with(csrf()))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("job_delete_guard", "FOREIGN KEY", "jdbc:");
        assertThat(jdbc.queryForList("SELECT * FROM job_applications ORDER BY id")).isEqualTo(jobsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(roundsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(questionsBefore);
    }

    @Test
    void foreignJobDeleteCannotRemoveAnyOfItsChildren() throws Exception {
        var roundsBefore = jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id");
        var questionsBefore = jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id");
        String foreign = mvc.perform(delete("/api/jobs/" + foreignJob).with(user(owner)).with(csrf())
                        .param("userId", other.getId().toString()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(delete("/api/jobs/" + Long.MAX_VALUE).with(user(owner)).with(csrf()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreign).isEqualTo(missing);
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(roundsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(questionsBefore);
    }

    private ResultActions removeRound(Long id) throws Exception {
        return mvc.perform(delete("/api/interview-rounds/" + id).with(user(owner)).with(csrf()));
    }
    private Map<String,Object> round(Long id) { return jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", id); }
    private long roundCount(Long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE id=?", Long.class, id); }
    private long questionCount(Long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions WHERE interview_round_id=?", Long.class, id); }
    private AppUserDetails seedUser(String username) {
        String credential = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, credential);
        UserAccount account = new UserAccount(); account.setUsername(username); account.setPasswordHash(credential);
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        return new AppUserDetails(account);
    }
    private Long seedJob(AppUserDetails user, String company) {
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,?, '虚构删除岗位')", user.getId(), company);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=?", Long.class, user.getId(), company);
    }
    private Long seedRound(Long job, AppUserDetails user, int number) {
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no,review) VALUES (?,?,?,'虚构复盘')", user.getId(), job, number);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM interview_rounds WHERE job_application_id=? AND user_id=?", Long.class, job, user.getId());
        jdbc.update("INSERT INTO interview_questions(user_id,interview_round_id,question,answer_notes,knowledge_topic,review_notes) VALUES (?,?,'虚构问题','虚构回答','事务','虚构备注')", user.getId(), id);
        return id;
    }
}
