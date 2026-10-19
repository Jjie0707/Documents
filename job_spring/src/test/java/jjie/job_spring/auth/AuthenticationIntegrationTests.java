package jjie.job_spring.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authentication;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthenticationIntegrationTests {
    // 仅用于内存数据库的测试夹具。
    private static final String TEST_PASSWORD = "auth-test-only-password";
    private static String hash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private Long aliceId;
    private Long bobId;
    private Long aliceJob;
    private Long bobJob;

    @BeforeAll
    static void prepareHash() {
        hash = new BCryptPasswordEncoder(12).encode(TEST_PASSWORD);
    }

    @BeforeEach
    void seedIsolatedMemoryDatabase() {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", "alice", hash);
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", "bob", hash);
        aliceId = jdbc.queryForObject("SELECT id FROM users WHERE username='alice'", Long.class);
        bobId = jdbc.queryForObject("SELECT id FROM users WHERE username='bob'", Long.class);
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,?,?)", aliceId, "Alice Company", "Alice Job");
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,?,?)", bobId, "Bob Company", "Bob Job");
        aliceJob = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, aliceId);
        bobJob = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, bobId);
    }

    @Test
    void anonymousUserCannotReadIdentityOrJobs() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/" + aliceJob)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPersistsIdentityRotatesSessionAndErasesPassword() throws Exception {
        Token before = token(null);
        String oldId = before.session().getId();
        MvcResult result = loginRequest(before, "  ALICE  ", TEST_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceId))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(oldId);
        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(((AppUserDetails) context.getAuthentication().getPrincipal()).getPassword()).isNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(aliceId)).andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void wrongPasswordAndUnknownUserHaveSameGenericFailure() throws Exception {
        String first = loginRequest(token(null), "alice", "wrong-test-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second = loginRequest(token(null), "missing-user", TEST_PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second).contains("INVALID_CREDENTIALS").doesNotContain(TEST_PASSWORD, hash);
    }

    @Test
    void oversizedLoginPasswordIsRejectedWithoutServerError() throws Exception {
        loginRequest(token(null), "alice", "x".repeat(73)).andExpect(status().isUnauthorized());
    }

    @Test
    void appendingBeyondMaximumPasswordLengthCannotAuthenticate() throws Exception {
        String boundaryPassword = "p".repeat(72);
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?",
                new BCryptPasswordEncoder(12).encode(boundaryPassword), aliceId);
        loginRequest(token(null), "alice", boundaryPassword + "x").andExpect(status().isUnauthorized());
        loginRequest(token(null), "alice", boundaryPassword).andExpect(status().isOk());
    }

    @Test
    void loginRequiresCsrfAndDoesNotAuthenticateOnFailure() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice").param("password", TEST_PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void eachUserOnlySeesOwnJobsAndQueryParameterCannotChangeOwner() throws Exception {
        MockHttpSession alice = loginAs("alice");
        MockHttpSession bob = loginAs("bob");
        mvc.perform(get("/api/jobs").session(alice).param("userId", bobId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceJob))
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(content().string(not(containsString("Bob Company"))));
        mvc.perform(get("/api/jobs").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bobJob));
    }

    @Test
    void guessingOtherUsersJobIdIsSameAsMissingRecord() throws Exception {
        MockHttpSession alice = loginAs("alice");
        mvc.perform(get("/api/jobs/" + aliceJob).session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobTitle").value("Alice Job"));
        String other = mvc.perform(get("/api/jobs/" + bobJob).session(alice))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/jobs/9223372036854775807").session(alice))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(other).isEqualTo(missing).doesNotContain("Bob Company");
    }

    @Test
    void emptyAccountGetsEmptyList() throws Exception {
        jdbc.update("DELETE FROM job_applications WHERE user_id=?", aliceId);
        mvc.perform(get("/api/jobs").session(loginAs("alice")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void oldCsrfTokenIsRejectedAfterLoginAndNewOneAllowsLogout() throws Exception {
        Token before = token(null);
        MvcResult result = loginRequest(before, "alice", TEST_PASSWORD).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session).header(before.header(), before.value()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        Token after = token(session);
        mvc.perform(post("/api/auth/logout").session(session).header(after.header(), after.value()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void getOrMissingTokenCannotLogUserOut() throws Exception {
        MockHttpSession session = loginAs("alice");
        mvc.perform(get("/api/auth/logout").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void createEndpointRequiresValidJobFields() throws Exception {
        MockHttpSession session = loginAs("alice");
        Token token = token(session);
        mvc.perform(post("/api/jobs").session(session).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyName").exists())
                .andExpect(jsonPath("$.fields.jobTitle").exists());
    }

    private MockHttpSession loginAs(String username) throws Exception {
        return (MockHttpSession) loginRequest(token(null), username, TEST_PASSWORD)
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(Token token, String username,
                                                                           String password) throws Exception {
        return mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), token.value())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", username).param("password", password));
    }

    private Token token(MockHttpSession session) throws Exception {
        var builder = get("/api/auth/csrf");
        if (session != null) builder.session(session);
        MvcResult result = mvc.perform(builder).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new Token((MockHttpSession) result.getRequest().getSession(false),
                JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    private record Token(MockHttpSession session, String header, String value) { }
}
