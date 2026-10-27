package jjie.job_spring.interview;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:question-write;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/interview-rounds-test.sql", "/sql/question-delete-guard-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
// 不套测试外层事务，观察 Service 实际提交/回滚后的数据库状态。
class InterviewQuestionWriteIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails owner;
    private AppUserDetails other;
    private Long jobId;
    private Long roundId;
    private Long siblingRound;
    private Long foreignRound;
    private Long target;
    private Long sibling;
    private Long otherRoundQuestion;
    private Long foreignQuestion;

    @BeforeEach
    void seedIsolatedH2() {
        for (String table : new String[]{"question_delete_guard", "interview_questions", "interview_rounds", "job_applications", "users"}) {
            jdbc.update("DELETE FROM " + table); // 仅清理本测试专用内存库，表名是固定常量。
        }
        owner = seedUser("question_write_a"); other = seedUser("question_write_b");
        jobId = seedJob(owner); roundId = seedRound(owner, jobId, 1); siblingRound = seedRound(owner, jobId, 2);
        foreignRound = seedRound(other, seedJob(other), 1);
        target = seedQuestion(owner, roundId, "目标虚构问题"); sibling = seedQuestion(owner, roundId, "保留同轮问题");
        otherRoundQuestion = seedQuestion(owner, siblingRound, "保留另一轮问题"); foreignQuestion = seedQuestion(other, foreignRound, "保留他人问题");
    }

    @Test
    void updatesOnlyTargetNormalizesFieldsAndPreservesParentsAndOtherQuestions() throws Exception {
        var roundBefore = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", roundId);
        var jobBefore = jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId);
        var siblingBefore = row(sibling); var otherBefore = row(otherRoundQuestion); var foreignBefore = row(foreignQuestion);
        update(target, Map.of("question", "  新虚构问题\n下一行  ", "answerNotes", " 回答\n要点 ", "knowledgeTopic", " 事务 ", "reviewNotes", " 复盘 "))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(target)).andExpect(jsonPath("$.question").value("新虚构问题\n下一行"))
                .andExpect(jsonPath("$.answerNotes").value("回答\n要点")).andExpect(jsonPath("$.knowledgeTopic").value("事务"))
                .andExpect(jsonPath("$.reviewNotes").value("复盘")).andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.interviewRoundId").doesNotExist());
        read(roundId).andExpect(status().isOk()).andExpect(jsonPath("$.items[1].question").value("新虚构问题\n下一行"));
        assertThat(row(sibling)).isEqualTo(siblingBefore); assertThat(row(otherRoundQuestion)).isEqualTo(otherBefore);
        assertThat(row(foreignQuestion)).isEqualTo(foreignBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", roundId)).isEqualTo(roundBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId)).isEqualTo(jobBefore);
    }

    @Test
    void omittedNullAndWhitespaceOptionalsAreExplicitlyClearedAndSameUpdateSucceeds() throws Exception {
        rawUpdate(target, "{\"question\":\"保留问题\",\"answerNotes\":null,\"knowledgeTopic\":\" \u2003\"}")
                .andExpect(status().isOk());
        assertThat(row(target)).containsEntry("answer_notes", null).containsEntry("knowledge_topic", null).containsEntry("review_notes", null);
        update(target, Map.of("question", "保留问题")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target));
        assertThat(count()).isEqualTo(4);
    }

    @Test
    void spoofedIdsDoNotMoveOrOverwriteOtherRecords() throws Exception {
        var original = row(foreignQuestion);
        update(target, Map.of("question", "允许的更新", "id", foreignQuestion, "userId", other.getId(), "interviewRoundId", foreignRound))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target));
        assertThat(row(target)).containsEntry("user_id", owner.getId()).containsEntry("interview_round_id", roundId);
        assertThat(row(foreignQuestion)).isEqualTo(original);
        mvc.perform(delete(path(target)).with(user(owner)).with(csrf()).param("userId", other.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("id", foreignQuestion, "userId", other.getId()))))
                .andExpect(status().isNoContent());
        assertThat(row(foreignQuestion)).isEqualTo(original); assertThat(count()).isEqualTo(3);
    }

    @Test
    void deletesOnlyTargetWith204AndKeepsEverythingElse() throws Exception {
        var siblingBefore = row(sibling); var foreignBefore = row(foreignQuestion);
        remove(target).andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(count()).isEqualTo(3); assertThat(row(sibling)).isEqualTo(siblingBefore); assertThat(row(foreignQuestion)).isEqualTo(foreignBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE id=?", Long.class, roundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, jobId)).isEqualTo(1);
    }

    @Test
    void missingRepeatedAndForeignTargetsHaveIdentical404WithoutChanges() throws Exception {
        String missingUpdate = update(Long.MAX_VALUE, Map.of("question", "不得保存")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String foreignUpdate = update(foreignQuestion, Map.of("question", "不得保存")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreignUpdate).isEqualTo(missingUpdate);
        String missingDelete = remove(Long.MAX_VALUE).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String foreignDelete = remove(foreignQuestion).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreignDelete).isEqualTo(missingDelete); assertThat(count()).isEqualTo(4);
        remove(target).andExpect(status().isNoContent());
        assertThat(remove(target).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString()).isEqualTo(missingDelete);
        assertThat(count()).isEqualTo(3);
    }

    @Test
    void deleteConstraintFailureRollsBackAndKeepsOriginalQuestion() throws Exception {
        jdbc.update("INSERT INTO question_delete_guard(question_id) VALUES (?)", target);
        var before = row(target);
        remove(target).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(row(target)).isEqualTo(before); assertThat(count()).isEqualTo(4);
    }

    @Test
    void focusLocatesActualPageWithinRoundAndDoesNotExposeOtherScopes() throws Exception {
        for (int i = 0; i < 10; i++) seedQuestion(owner, roundId, "虚构分页问题" + i);
        mvc.perform(get(listPath(roundId)).with(user(owner)).param("questionId", target.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.items[1].id").value(target));
        for (Long id : new Long[]{foreignQuestion, otherRoundQuestion, Long.MAX_VALUE}) {
            mvc.perform(get(listPath(roundId)).with(user(owner)).param("questionId", id.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items.length()").value(10));
        }
        remove(target).andExpect(status().isNoContent()); remove(sibling).andExpect(status().isNoContent());
        mvc.perform(get(listPath(roundId)).with(user(owner)).param("page", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.total").value(10));
        remove(otherRoundQuestion).andExpect(status().isNoContent());
        read(siblingRound).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\" \\n\"}", "{\"question\":\"\u2003\"}"})
    void updateRequiresQuestionAndDoesNotWrite(String values) throws Exception {
        var before = row(target);
        rawUpdate(target, values).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.question").isString());
        assertThat(row(target)).isEqualTo(before);
    }

    static Stream<Arguments> limits() {
        return Stream.of(Arguments.of("question", 2000), Arguments.of("answerNotes", 4000), Arguments.of("knowledgeTopic", 100), Arguments.of("reviewNotes", 2000));
    }
    @ParameterizedTest
    @MethodSource("limits")
    void updateChecksBoundaryAndDoesNotEchoInvalidContent(String field, int limit) throws Exception {
        var values = new java.util.HashMap<String,Object>(); values.put("question", "虚构问题"); values.put(field, "测".repeat(limit));
        update(target, values).andExpect(status().isOk()); var before = row(target);
        values.put(field, "DO_NOT_ECHO" + "测".repeat(limit));
        String response = update(target, values).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields." + field).isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO"); assertThat(row(target)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"question\":[]}", "{\"question\":{\"secret\":\"DO_NOT_ECHO\"}}"})
    void malformedUpdateIsSanitizedWithoutWriting(String values) throws Exception {
        var before = row(target);
        String response = rawUpdate(target, values).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", "Exception"); assertThat(row(target)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DO_NOT_ECHO", "9223372036854775808"})
    void invalidIdsAre400ForBothWrites(String id) throws Exception {
        String update = mvc.perform(put("/api/interview-questions/" + id).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"虚构问题\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String delete = mvc.perform(delete("/api/interview-questions/" + id).with(user(owner)).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(update + delete).doesNotContain("DO_NOT_ECHO"); assertThat(count()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "DO_NOT_ECHO"})
    void invalidFocusIsSanitized400(String id) throws Exception {
        String response = mvc.perform(get(listPath(roundId)).with(user(owner)).param("questionId", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.questionId").isString()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", "Failed to convert");
    }

    @Test
    void bothWritesRequireLoginAndCsrf() throws Exception {
        for (boolean deleting : new boolean[]{false,true}) {
            var anonymous = deleting ? delete(path(target)) : put(path(target));
            mvc.perform(anonymous.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                    .andExpect(status().isUnauthorized());
            var noCsrf = deleting ? delete(path(target)) : put(path(target));
            mvc.perform(noCsrf.with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                    .andExpect(status().isForbidden());
            var invalidCsrf = deleting ? delete(path(target)) : put(path(target));
            mvc.perform(invalidCsrf.with(user(owner)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(count()).isEqualTo(4);
    }

    private ResultActions update(Long id, Map<String,?> values) throws Exception { return rawUpdate(id, json.writeValueAsString(values)); }
    private ResultActions rawUpdate(Long id, String values) throws Exception { return mvc.perform(put(path(id)).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(values)); }
    private ResultActions remove(Long id) throws Exception { return mvc.perform(delete(path(id)).with(user(owner)).with(csrf())); }
    private ResultActions read(Long id) throws Exception { return mvc.perform(get(listPath(id)).with(user(owner))); }
    private String path(Long id) { return "/api/interview-questions/" + id; }
    private String listPath(Long id) { return "/api/interview-rounds/" + id + "/questions"; }
    private Map<String,Object> row(Long id) { return jdbc.queryForMap("SELECT * FROM interview_questions WHERE id=?", id); }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions", Long.class); }
    private AppUserDetails seedUser(String username) {
        String credential = UUID.randomUUID().toString(); jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, credential);
        UserAccount account = new UserAccount(); account.setUsername(username); account.setPasswordHash(credential);
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username)); return new AppUserDetails(account);
    }
    private Long seedJob(AppUserDetails user) {
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构问题操作公司','虚构岗位')", user.getId());
        return jdbc.queryForObject("SELECT MAX(id) FROM job_applications WHERE user_id=?", Long.class, user.getId());
    }
    private Long seedRound(AppUserDetails user, Long job, int number) {
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,?)", user.getId(), job, number);
        return jdbc.queryForObject("SELECT MAX(id) FROM interview_rounds WHERE job_application_id=?", Long.class, job);
    }
    private Long seedQuestion(AppUserDetails user, Long round, String text) {
        jdbc.update("INSERT INTO interview_questions(user_id,interview_round_id,question,answer_notes,knowledge_topic,review_notes) VALUES (?,?,?,'原回答','原主题','原复盘')", user.getId(), round, text);
        return jdbc.queryForObject("SELECT MAX(id) FROM interview_questions WHERE interview_round_id=?", Long.class, round);
    }
}
