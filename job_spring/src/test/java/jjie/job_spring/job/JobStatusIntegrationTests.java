package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.common.JobStatus;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-status;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobStatusIntegrationTests {
    private static String testPassword;
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private Long aliceJob;
    private Long bobJob;

    @BeforeAll
    static void generateTestOnlyCredential() {
        testPassword = UUID.randomUUID().toString();
        testHash = new BCryptPasswordEncoder(12).encode(testPassword);
    }

    @BeforeEach
    void seedIsolatedMemoryDatabase() {
        alice = seedUser("status_demo_a");
        bob = seedUser("status_demo_b");
        aliceJob = seedJob(alice.getId(), "虚构公司A");
        bobJob = seedJob(bob.getId(), "虚构公司B");
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void acceptsEveryStatusAndRepeatedUpdatesWithoutChangingOtherFields(JobStatus status) throws Exception {
        Map<String, Object> expected = new LinkedHashMap<>(row(aliceJob));
        expected.replaceAll((key, value) -> key.equalsIgnoreCase("status") ? status.name() : value);
        for (int attempt = 0; attempt < 2; attempt++) {
            update(aliceJob, "{\"status\":\"" + status.name() + "\"}")
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.id").value(aliceJob))
                    .andExpect(jsonPath("$.status").value(status.name()))
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }
        assertThat(row(aliceJob)).isEqualTo(expected);
        assertThat(statusOf(bobJob)).isEqualTo("TO_APPLY");
        mvc.perform(get("/api/jobs/" + aliceJob).with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status.name()));
        mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value(status.name()));
    }

    @Test
    void otherUsersRecordAndMissingRecordHaveSame404AndDoNotChangeData() throws Exception {
        Map<String, Object> before = row(bobJob);
        String other = update(bobJob, "{\"status\":\"OFFER\"}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = update(Long.MAX_VALUE, "{\"status\":\"OFFER\"}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(other).isEqualTo(missing).doesNotContain("虚构公司B");
        assertThat(row(bobJob)).isEqualTo(before);
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @Test
    void injectedOwnerQueryAndExtraBodyFieldsCannotChangeOwnerOrOtherColumns() throws Exception {
        Map<String, Object> expected = new LinkedHashMap<>(row(aliceJob));
        expected.replaceAll((key, value) -> key.equalsIgnoreCase("status") ? "APPLIED" : value);
        String body = """
                {"status":"APPLIED","userId":%d,"id":%d,"companyName":"DO_NOT_WRITE",
                 "salary":"DO_NOT_WRITE","jobTitle":"DO_NOT_WRITE"}
                """.formatted(bob.getId(), bobJob);
        mvc.perform(patch(path(aliceJob)).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(row(aliceJob)).isEqualTo(expected);
        assertThat(statusOf(bobJob)).isEqualTo("TO_APPLY");
        mvc.perform(patch(path(bobJob)).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFER\"}"))
                .andExpect(status().isNotFound());
        assertThat(statusOf(bobJob)).isEqualTo("TO_APPLY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}"})
    void missingStatusHasFieldErrorAndDoesNotUpdate(String body) throws Exception {
        update(aliceJob, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields.status").value("投递状态不能为空"));
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"DO_NOT_ECHO\"}", "{\"status\":\"applied\"}", "{\"status\":\" APPLIED \"}",
            "{\"status\":\"\"}", "{\"status\":0}", "{\"status\":1}", "{\"status\":true}",
            "{\"status\":{}}", "{\"status\":[]}", "{", "null", "[]"
    })
    void invalidStatusAndJsonAreRejectedWithoutEchoOrUpdate(String body) throws Exception {
        MvcResult result = update(aliceJob, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("DO_NOT_ECHO", testPassword, testHash);
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void invalidIdReturnsLimited400(String id) throws Exception {
        mvc.perform(patch("/api/jobs/" + id + "/status").with(user(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("岗位ID格式不正确"));
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @Test
    void anonymousRequestWithValidCsrfStillRequiresLogin() throws Exception {
        mvc.perform(patch(path(aliceJob)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @Test
    void missingOrInvalidCsrfCannotUpdate() throws Exception {
        mvc.perform(patch(path(aliceJob)).with(user(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path(aliceJob)).with(user(alice)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isForbidden());
        assertThat(statusOf(aliceJob)).isEqualTo("TO_APPLY");
    }

    @Test
    void realLoginAndCsrfTokenCanUpdateTwiceWithoutFetchingAnotherToken() throws Exception {
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
        for (String status : new String[] {"APPLIED", "INTERVIEWING"}) {
            mvc.perform(patch(path(aliceJob)).session(session).header(header, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        }
        assertThat(statusOf(aliceJob)).isEqualTo("INTERVIEWING");
    }

    private org.springframework.test.web.servlet.ResultActions update(Long id, String body) throws Exception {
        return mvc.perform(patch(path(id)).with(user(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String path(Long id) { return "/api/jobs/" + id + "/status"; }

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
                INSERT INTO job_applications(user_id, company_name, job_title, city, source_platform, original_url,
                    salary, responsibilities, requirements, education_requirement, application_deadline)
                VALUES (?, ?, '虚构测试岗位', '测试城市', 'TEST_ONLY', 'https://example.com/job',
                    '面议', '虚构职责', '虚构要求', '不限', '2030-01-01')
                """, userId, company);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, userId);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", id);
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject("SELECT status FROM job_applications WHERE id=?", String.class, id);
    }
}
