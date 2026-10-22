package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-delete;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/job-delete-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobDeleteIntegrationTests {
    private static String testPassword;
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private Long aliceJob;
    private Long aliceOtherJob;
    private Long bobJob;

    @BeforeAll
    static void generateTestOnlyCredential() {
        testPassword = UUID.randomUUID().toString();
        testHash = new BCryptPasswordEncoder(12).encode(testPassword);
    }

    @BeforeEach
    void seedIsolatedMemoryDatabase() {
        alice = seedUser("delete_demo_a");
        bob = seedUser("delete_demo_b");
        aliceJob = seedJob(alice.getId(), "虚构公司A");
        aliceOtherJob = seedJob(alice.getId(), "虚构公司A的另一个岗位");
        bobJob = seedJob(bob.getId(), "虚构公司B");
    }

    @Test
    void deletesOnlyOwnedTargetAndReturnsEmpty204() throws Exception {
        Map<String, Object> otherBefore = row(aliceOtherJob);
        Map<String, Object> bobBefore = row(bobJob);
        deleteJob(aliceJob).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        assertThat(count(aliceJob)).isZero();
        assertThat(row(aliceOtherJob)).isEqualTo(otherBefore);
        assertThat(row(bobJob)).isEqualTo(bobBefore);
        mvc.perform(get("/api/jobs/" + aliceJob).with(user(alice))).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(aliceOtherJob));
        mvc.perform(get("/api/jobs").with(user(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bobJob));
    }

    @Test
    void repeatedMissingAndForeignDeletesHaveIdentical404AndDoNotDeleteMore() throws Exception {
        deleteJob(aliceJob).andExpect(status().isNoContent());
        List<Map<String, Object>> before = jobs();
        String repeated = deleteJob(aliceJob).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = deleteJob(Long.MAX_VALUE).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String foreign = deleteJob(bobJob).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(repeated).isEqualTo(missing).isEqualTo(foreign).doesNotContain("虚构公司B");
        assertThat(jobs()).isEqualTo(before);
    }

    @Test
    void injectedOwnerQueryAndBodyCannotSwitchUserOrTarget() throws Exception {
        List<Map<String, Object>> before = jobs();
        mvc.perform(delete(path(bobJob)).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"id\":%d}".formatted(bob.getId(), aliceJob)))
                .andExpect(status().isNotFound());
        assertThat(jobs()).isEqualTo(before);
        Map<String, Object> bobBefore = row(bobJob);
        Map<String, Object> ownOtherBefore = row(aliceOtherJob);
        mvc.perform(delete(path(aliceJob)).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"id\":%d}".formatted(bob.getId(), bobJob)))
                .andExpect(status().isNoContent());
        assertThat(count(aliceJob)).isZero();
        assertThat(row(bobJob)).isEqualTo(bobBefore);
        assertThat(row(aliceOtherJob)).isEqualTo(ownOtherBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void invalidIdReturns400WithoutDeletingAnything(String id) throws Exception {
        List<Map<String, Object>> before = jobs();
        mvc.perform(delete("/api/jobs/" + id).with(user(alice)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("岗位ID格式不正确"));
        assertThat(jobs()).isEqualTo(before);
    }

    @Test
    void anonymousRequestWithValidCsrfStillRequiresLogin() throws Exception {
        List<Map<String, Object>> before = jobs();
        mvc.perform(delete(path(aliceJob)).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(jobs()).isEqualTo(before);
    }

    @Test
    void missingOrInvalidCsrfCannotDelete() throws Exception {
        List<Map<String, Object>> before = jobs();
        mvc.perform(delete(path(aliceJob)).with(user(alice))).andExpect(status().isForbidden());
        mvc.perform(delete(path(aliceJob)).with(user(alice)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(jobs()).isEqualTo(before);
    }

    @Test
    void deletesRelatedInterviewDataInTheSameTransaction() throws Exception {
        seedInterview(aliceJob, alice.getId());
        seedInterview(aliceOtherJob, alice.getId());
        seedInterview(bobJob, bob.getId());
        List<Map<String, Object>> otherRounds = jdbc.queryForList("SELECT * FROM interview_rounds WHERE job_application_id<>? ORDER BY id", aliceJob);
        List<Map<String, Object>> otherQuestions = jdbc.queryForList("SELECT * FROM interview_questions WHERE interview_round_id NOT IN (SELECT id FROM interview_rounds WHERE job_application_id=?) ORDER BY id", aliceJob);
        deleteJob(aliceJob).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(count(aliceJob)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM interview_rounds ORDER BY id")).isEqualTo(otherRounds);
        assertThat(jdbc.queryForList("SELECT * FROM interview_questions ORDER BY id")).isEqualTo(otherQuestions);
    }

    @Test
    void relatedDataOnForeignJobStillReturns404InsteadOfDisclosingConflict() throws Exception {
        seedInterview(bobJob, bob.getId());
        List<Map<String, Object>> before = jobs();
        String foreign = deleteJob(bobJob).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = deleteJob(Long.MAX_VALUE).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(foreign).isEqualTo(missing);
        assertThat(jobs()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions", Long.class)).isEqualTo(1L);
    }

    @Test
    void realSessionTokenCanDeleteTwoJobsWithoutRefetching() throws Exception {
        MvcResult before = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) before.getRequest().getSession(false);
        String loginHeader = JsonPath.read(before.getResponse().getContentAsString(), "$.headerName");
        String loginToken = JsonPath.read(before.getResponse().getContentAsString(), "$.token");
        MvcResult login = mvc.perform(post("/api/auth/login").session(session).header(loginHeader, loginToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", alice.getUsername()).param("password", testPassword))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult after = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String header = JsonPath.read(after.getResponse().getContentAsString(), "$.headerName");
        String token = JsonPath.read(after.getResponse().getContentAsString(), "$.token");
        Map<String, Object> bobBefore = row(bobJob);
        for (Long id : new Long[] {aliceJob, aliceOtherJob}) {
            mvc.perform(delete(path(id)).session(session).header(header, token))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
            assertThat(count(id)).isZero();
        }
        assertThat(row(bobJob)).isEqualTo(bobBefore);
    }

    private ResultActions deleteJob(Long id) throws Exception {
        return mvc.perform(delete(path(id)).with(user(alice)).with(csrf()));
    }

    private static String path(Long id) { return "/api/jobs/" + id; }

    private AppUserDetails seedUser(String username) {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, testHash);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username);
        account.setPasswordHash(testHash);
        return new AppUserDetails(account);
    }

    private Long seedJob(Long userId, String company) {
        jdbc.update("""
                INSERT INTO job_applications(user_id, company_name, job_title, city, salary, status)
                VALUES (?, ?, '虚构删除测试岗位', '测试城市', '面议', 'INTERVIEWING')
                """, userId, company);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=?",
                Long.class, userId, company);
    }

    private void seedInterview(Long jobId, Long userId) {
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", userId, jobId);
        Long roundId = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=?", Long.class, jobId);
        jdbc.update("INSERT INTO interview_questions(user_id,interview_round_id,question) VALUES (?,?,'虚构测试问题')",
                userId, roundId);
    }

    private Long count(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, id);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", id);
    }

    private List<Map<String, Object>> jobs() {
        return jdbc.queryForList("SELECT * FROM job_applications ORDER BY id");
    }
}
