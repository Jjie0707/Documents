package jjie.job_spring.auth;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "/sql/users-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RegistrationIntegrationTests {

    // 此值仅是测试夹具，不是任何实际账号的密码。
    private static final String TEST_PASSWORD = "test-only-password-123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private ObjectMapper json;

    @Test
    void createsUserWithGeneratedIdAndVerifiableHash() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  learner  ", TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("learner"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, "learner");
        assertThat(hash).isNotEqualTo(TEST_PASSWORD).startsWith("$2");
        assertThat(encoder.matches(TEST_PASSWORD, hash)).isTrue();
        assertThat(encoder.matches("different-test-password", hash)).isFalse();
    }

    @Test
    void duplicateUsernameIsCaseInsensitiveAndDoesNotCreateSecondUser() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("learner", TEST_PASSWORD)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("LEARNER", TEST_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void anonymousClientCanFetchCsrfTokenAndRegisterWithoutBeingLoggedIn() throws Exception {
        MvcResult tokenResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.token");
        String headerName = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.headerName");

        mvc.perform(post("/api/auth/register").session(session).header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("learner", TEST_PASSWORD)))
                .andExpect(status().isCreated());
        // 注册完成不创建已登录身份。
        mvc.perform(get("/api/jobs").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfTokenIsForbiddenAndDoesNotInsertUser() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body("learner", TEST_PASSWORD)))
                .andExpect(status().isForbidden());
        assertThat(userCount()).isZero();
    }

    @Test
    void wrongCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body("learner", TEST_PASSWORD)))
                .andExpect(status().isForbidden());
        assertThat(userCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void invalidParametersAreRejectedWithoutEchoingPassword(String username, String password,
                                                            String invalidField) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(username, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields." + invalidField).isNotEmpty())
                .andReturn();
        if (!password.isBlank()) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(password);
        }
        assertThat(userCount()).isZero();
    }

    static Arguments[] invalidArguments() {
        return new Arguments[] {
                Arguments.of("   ", TEST_PASSWORD, "username"),
                Arguments.of("u".repeat(51), TEST_PASSWORD, "username"),
                Arguments.of("learner", "", "password"),
                Arguments.of("learner", "short", "password"),
                Arguments.of("learner", "p".repeat(73), "password"),
                Arguments.of("learner", "密".repeat(25), "password"),
                Arguments.of("learner", "🙂".repeat(4), "password")
        };
    }

    @Test
    void acceptsExactlySeventyTwoUtf8Bytes() throws Exception {
        String password = "密".repeat(24);
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("learner", password)))
                .andExpect(status().isCreated());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?",
                String.class, "learner");
        assertThat(encoder.matches(password, hash)).isTrue();
    }

    @Test
    void malformedJsonGetsGenericError() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"请求体格式不正确","fields":{}}
                        """));
        assertThat(userCount()).isZero();
    }

    @Test
    void missingPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"learner\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("密码不能为空"));
        assertThat(userCount()).isZero();
    }

    private String body(String username, String password) {
        return json.writeValueAsString(Map.of("username", username, "password", password));
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }
}
