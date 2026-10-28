package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.common.CreateJobRequest;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-creation;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobCreationIntegrationTests {
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails alice;
    private AppUserDetails bob;

    @BeforeAll
    static void generateTestOnlyCredential() {
        // 凭据运行时生成，仅存于本次 H2 测试库，不使用任何实际账号。
        testHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    }

    @BeforeEach
    void seedUsers() {
        alice = seedUser("create_demo_a");
        bob = seedUser("create_demo_b");
    }

    @Test
    void createsOwnedJobAndCanReadItBack() throws Exception {
        MvcResult result = createJson("""
                {"companyName":"  示例公司  ","jobTitle":" Java 后端实习 ",
                 "city":" 测试城市 ","salary":" 面议 ",
                 "responsibilities":"  负责 Java 开发\\n  编写测试  ",
                 "requirements":"  熟悉 Java\\r\\n了解 SQL 😀  "}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.companyName").value("示例公司"))
                .andExpect(jsonPath("$.jobTitle").value("Java 后端实习"))
                .andExpect(jsonPath("$.city").value("测试城市"))
                .andExpect(jsonPath("$.salary").value("面议"))
                .andExpect(jsonPath("$.responsibilities").value("负责 Java 开发\n  编写测试"))
                .andExpect(jsonPath("$.requirements").value("熟悉 Java\r\n了解 SQL 😀"))
                .andExpect(jsonPath("$.status").value("TO_APPLY"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/jobs/" + id);
        assertThat(jdbc.queryForObject("SELECT user_id FROM job_applications WHERE id=?", Long.class, id))
                .isEqualTo(alice.getId());
        assertThat(jdbc.queryForObject("SELECT responsibilities FROM job_applications WHERE id=?", String.class, id))
                .isEqualTo("负责 Java 开发\n  编写测试");
        assertThat(jdbc.queryForObject("SELECT requirements FROM job_applications WHERE id=?", String.class, id))
                .isEqualTo("熟悉 Java\r\n了解 SQL 😀");
        mvc.perform(get("/api/jobs/" + id).with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobTitle").value("Java 后端实习"))
                .andExpect(jsonPath("$.responsibilities").value("负责 Java 开发\n  编写测试"))
                .andExpect(jsonPath("$.requirements").value("熟悉 Java\r\n了解 SQL 😀"));
        mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].requirements").value("熟悉 Java\r\n了解 SQL 😀"));
        mvc.perform(get("/api/jobs").with(user(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/jobs/" + id).with(user(bob))).andExpect(status().isNotFound());
    }

    @Test
    void omittedOrBlankOptionalFieldsAreStoredAsNull() throws Exception {
        mvc.perform(post("/api/jobs").with(user(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"示例公司\",\"jobTitle\":\"示例岗位\"}"))
                .andExpect(status().isCreated());
        createJson("""
                {"companyName":"示例公司","jobTitle":"示例岗位","city":"  ","salary":"\\t\\n",
                 "responsibilities":"\\n\\t　 ","requirements":"　\\r\\n "}
                """)
                .andExpect(status().isCreated());
        createJson("""
                {"companyName":"示例公司","jobTitle":"示例岗位","city":null,"salary":null,
                 "responsibilities":null,"requirements":null}
                """)
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM job_applications WHERE city IS NULL AND salary IS NULL
                    AND responsibilities IS NULL AND requirements IS NULL
                """, Long.class)).isEqualTo(3L);
    }

    @Test
    void acceptsFieldLengthBoundaries() throws Exception {
        createJson(json.writeValueAsString(Map.of(
                "companyName", "  " + "公".repeat(200) + "  ", "jobTitle", "岗".repeat(200),
                "city", "城".repeat(100), "salary", "薪".repeat(100),
                "responsibilities", "  " + "😀".repeat(10000) + "  ", "requirements", "要".repeat(10000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibilities").value("😀".repeat(10000)))
                .andExpect(jsonPath("$.requirements").value("要".repeat(10000)));
        assertThat(jobCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidJobs")
    void invalidFieldsDoNotInsertOrEchoInput(CreateJobRequest request, String field) throws Exception {
        MvcResult result = create(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields." + field).isNotEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("DO_NOT_ECHO");
        assertThat(jobCount()).isZero();
    }

    static Stream<Arguments> invalidJobs() {
        return Stream.of(
                Arguments.of(new CreateJobRequest(null, "DO_NOT_ECHO", null, null, null, null), "companyName"),
                Arguments.of(new CreateJobRequest("  ", "DO_NOT_ECHO", null, null, null, null), "companyName"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", null, null, null, null, null), "jobTitle"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "\t", null, null, null, null), "jobTitle"),
                Arguments.of(new CreateJobRequest("x".repeat(201), "DO_NOT_ECHO", null, null, null, null), "companyName"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "x".repeat(201), null, null, null, null), "jobTitle"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", "x".repeat(101), null, null, null), "city"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", null, "x".repeat(101), null, null), "salary"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", null, null, "职".repeat(10001), null), "responsibilities"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", null, null, "😀".repeat(10001), null), "responsibilities"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", null, null, null, "要".repeat(10001)), "requirements"),
                Arguments.of(new CreateJobRequest("DO_NOT_ECHO", "岗位", null, null, null, "😀".repeat(10001)), "requirements")
        );
    }

    @Test
    void malformedOrNullJsonDoesNotInsert() throws Exception {
        for (String body : new String[] {"{", "null",
                "{\"companyName\":\"示例公司\",\"jobTitle\":\"示例岗位\",\"responsibilities\":{}}",
                "{\"companyName\":\"示例公司\",\"jobTitle\":\"示例岗位\",\"responsibilities\":[]}",
                "{\"companyName\":\"示例公司\",\"jobTitle\":\"示例岗位\",\"requirements\":{}}",
                "{\"companyName\":\"示例公司\",\"jobTitle\":\"示例岗位\",\"requirements\":[]}"}) {
            mvc.perform(post("/api/jobs").with(user(alice)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(jobCount()).isZero();
    }

    @Test
    void anonymousRequestWithValidCsrfStillRequiresLogin() throws Exception {
        mvc.perform(post("/api/jobs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateJobRequest("示例公司", "示例岗位", null, null, "虚构职责", "虚构要求"))))
                .andExpect(status().isUnauthorized());
        assertThat(jobCount()).isZero();
    }

    @Test
    void missingOrInvalidCsrfDoesNotInsert() throws Exception {
        String body = json.writeValueAsString(new CreateJobRequest("示例公司", "示例岗位", null, null, "虚构职责", "虚构要求"));
        mvc.perform(post("/api/jobs").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/jobs").with(user(alice)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(jobCount()).isZero();
    }

    @Test
    void extraFieldsCannotSetOwnerIdStatusOrOtherJobContent() throws Exception {
        String body = """
                {"companyName":"示例公司","jobTitle":"示例岗位","userId":%d,
                 "id":9223372036854775807,"status":"OFFER","educationRequirement":"EXTRA_CONTENT",
                 "sourcePlatform":"EXTRA_CONTENT","originalUrl":"https://example.com/ignored","applicationDeadline":"2030-01-01"}
                """.formatted(bob.getId());
        mvc.perform(post("/api/jobs").param("userId", bob.getId().toString())
                        .with(user(alice)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("TO_APPLY"));
        assertThat(jdbc.queryForObject("SELECT user_id FROM job_applications", Long.class)).isEqualTo(alice.getId());
        assertThat(jdbc.queryForObject("SELECT id FROM job_applications", Long.class)).isNotEqualTo(Long.MAX_VALUE);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM job_applications WHERE education_requirement IS NULL
                    AND source_platform IS NULL AND original_url IS NULL AND application_deadline IS NULL
                """, Long.class)).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions create(CreateJobRequest request) throws Exception {
        return createJson(json.writeValueAsString(request));
    }

    private org.springframework.test.web.servlet.ResultActions createJson(String body) throws Exception {
        return mvc.perform(post("/api/jobs").with(user(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private AppUserDetails seedUser(String username) {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, testHash);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username);
        account.setPasswordHash(testHash);
        return new AppUserDetails(account);
    }

    private long jobCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class);
    }
}
