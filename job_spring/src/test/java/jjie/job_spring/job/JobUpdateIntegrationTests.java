package jjie.job_spring.job;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-update;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobUpdateIntegrationTests {
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private Long aliceJob;
    private Long bobJob;

    @BeforeAll
    static void generateTestOnlyCredential() {
        testHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    }

    @BeforeEach
    void seedIsolatedMemoryDatabase() {
        alice = seedUser("update_demo_a");
        bob = seedUser("update_demo_b");
        aliceJob = seedJob(alice.getId(), "虚构公司A");
        bobJob = seedJob(bob.getId(), "虚构公司B");
    }

    @Test
    void updatesOnlySixFieldsAndRepeatedIdenticalRequestIsSuccessful() throws Exception {
        Map<String, Object> expected = expectedRow("修改公司", "Java 测试岗位", "修改城市", "薪资待定",
                "负责 Java 开发\n  编写测试", "熟悉 Java\r\n了解 SQL 😀");
        Map<String, Object> otherBefore = row(bobJob);
        String body = """
                {"companyName":"  修改公司  ","jobTitle":" Java 测试岗位 ",
                 "city":" 修改城市 ","salary":" 薪资待定 ",
                 "responsibilities":"  负责 Java 开发\\n  编写测试  ",
                 "requirements":"  熟悉 Java\\r\\n了解 SQL 😀  "}
                """;
        for (int attempt = 0; attempt < 2; attempt++) {
            update(aliceJob, body).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.id").value(aliceJob))
                    .andExpect(jsonPath("$.companyName").value("修改公司"))
                    .andExpect(jsonPath("$.jobTitle").value("Java 测试岗位"))
                    .andExpect(jsonPath("$.city").value("修改城市"))
                    .andExpect(jsonPath("$.salary").value("薪资待定"))
                    .andExpect(jsonPath("$.responsibilities").value("负责 Java 开发\n  编写测试"))
                    .andExpect(jsonPath("$.requirements").value("熟悉 Java\r\n了解 SQL 😀"))
                    .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }
        assertThat(row(aliceJob)).isEqualTo(expected);
        assertThat(row(bobJob)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/jobs/" + aliceJob).with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("修改公司"))
                .andExpect(jsonPath("$.responsibilities").value("负责 Java 开发\n  编写测试"))
                .andExpect(jsonPath("$.requirements").value("熟悉 Java\r\n了解 SQL 😀"));
        mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].companyName").value("修改公司"))
                .andExpect(jsonPath("$.items[0].requirements").value("熟悉 Java\r\n了解 SQL 😀"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\"}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"city\":null,\"salary\":null,\"responsibilities\":null,\"requirements\":null}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"city\":\"  \",\"salary\":\"\\t\\n\",\"responsibilities\":\"\\n\\t　 \",\"requirements\":\"　\\r\\n \"}"
    })
    void omittedNullAndBlankOptionalFieldsClearExistingValues(String body) throws Exception {
        Map<String, Object> expected = expectedRow("修改公司", "修改岗位", null, null, null, null);
        update(aliceJob, body).andExpect(status().isOk());
        assertThat(row(aliceJob)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"responsibilities", "requirements"})
    void oneDescriptionCanBeClearedWhileOtherFieldsArePreserved(String field) throws Exception {
        Map<String, Object> before = row(aliceJob);
        Map<String, String> body = new LinkedHashMap<>(Map.of(
                "companyName", "虚构公司A", "jobTitle", "虚构测试岗位", "city", "测试城市", "salary", "面议",
                "responsibilities", "虚构职责", "requirements", "虚构要求"));
        body.put(field, "  \n\t");
        Map<String, Object> expected = new LinkedHashMap<>(before);
        expected.replaceAll((key, value) -> key.equalsIgnoreCase(field) ? null : value);
        update(aliceJob, json.writeValueAsString(body)).andExpect(status().isOk());
        assertThat(row(aliceJob)).isEqualTo(expected);
    }

    @Test
    void acceptsLengthBoundariesAfterTrimming() throws Exception {
        Map<String, String> fields = Map.of("companyName", "公".repeat(200), "jobTitle", "岗".repeat(200),
                "city", "城".repeat(100), "salary", "薪".repeat(100),
                "responsibilities", "😀".repeat(10000), "requirements", "要".repeat(10000));
        Map<String, String> raw = new LinkedHashMap<>();
        fields.forEach((key, value) -> raw.put(key, "  " + value + "  "));
        Map<String, Object> expected = expectedRow(fields.get("companyName"), fields.get("jobTitle"),
                fields.get("city"), fields.get("salary"), fields.get("responsibilities"), fields.get("requirements"));
        update(aliceJob, json.writeValueAsString(raw)).andExpect(status().isOk());
        assertThat(row(aliceJob)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidFieldsDoNotUpdateOrEchoInput(String field, String value) throws Exception {
        Map<String, Object> before = row(aliceJob);
        Map<String, String> body = new LinkedHashMap<>(Map.of("companyName", "DO_NOT_ECHO", "jobTitle", "DO_NOT_ECHO"));
        body.put(field, value);
        String response = update(aliceJob, json.writeValueAsString(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields." + field).isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", testHash);
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("companyName", null), Arguments.of("companyName", "  "),
                Arguments.of("jobTitle", null), Arguments.of("jobTitle", "\t\n"),
                Arguments.of("companyName", "公".repeat(201)), Arguments.of("jobTitle", "岗".repeat(201)),
                Arguments.of("city", "城".repeat(101)), Arguments.of("salary", "薪".repeat(101)),
                Arguments.of("responsibilities", "职".repeat(10001)), Arguments.of("responsibilities", "😀".repeat(10001)),
                Arguments.of("requirements", "要".repeat(10001)), Arguments.of("requirements", "😀".repeat(10001)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{\"companyName\":{}}", "{\"companyName\":[]}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"responsibilities\":{}}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"responsibilities\":[]}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"requirements\":{}}",
            "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\",\"requirements\":[]}"})
    void malformedBodyReturns400WithoutChanges(String body) throws Exception {
        Map<String, Object> before = row(aliceJob);
        update(aliceJob, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    @Test
    void missingRequiredFieldsCannotClearAnEntireJob() throws Exception {
        Map<String, Object> before = row(aliceJob);
        update(aliceJob, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyName").isNotEmpty())
                .andExpect(jsonPath("$.fields.jobTitle").isNotEmpty());
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    @Test
    void foreignAndMissingRecordsReturnIdentical404WithoutChangesOrInsert() throws Exception {
        Map<String, Object> before = row(bobJob);
        String body = """
                {"companyName":"修改公司","jobTitle":"修改岗位",
                 "responsibilities":"其他账号不可写入的职责","requirements":"其他账号不可写入的要求"}
                """;
        String foreign = update(bobJob, body).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = update(Long.MAX_VALUE, body).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(foreign).isEqualTo(missing).doesNotContain("虚构公司B");
        assertThat(row(bobJob)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Integer.class)).isEqualTo(2);
    }

    @Test
    void extraOwnerIdStatusAndOtherFieldsCannotBeWritten() throws Exception {
        Map<String, Object> otherBefore = row(bobJob);
        Map<String, Object> expected = expectedRow("修改公司", "修改岗位", "修改城市", "面议", null, null);
        String body = """
                {"companyName":"修改公司","jobTitle":"修改岗位","city":"修改城市","salary":"面议",
                 "userId":%d,"id":%d,"status":"OFFER","educationRequirement":"DO_NOT_WRITE",
                 "sourcePlatform":"DO_NOT_WRITE","originalUrl":"https://example.com/ignored","applicationDeadline":"2040-01-01"}
                """.formatted(bob.getId(), bobJob);
        mvc.perform(put("/api/jobs/" + aliceJob).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEWING"));
        assertThat(row(aliceJob)).isEqualTo(expected);
        mvc.perform(put("/api/jobs/" + bobJob).with(user(alice)).with(csrf())
                        .param("userId", bob.getId().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        assertThat(row(bobJob)).isEqualTo(otherBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void invalidIdReturns400(String id) throws Exception {
        Map<String, Object> before = row(aliceJob);
        mvc.perform(put("/api/jobs/" + id).with(user(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("岗位ID格式不正确"));
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    @Test
    void anonymousRequestWithValidCsrfStillRequiresLogin() throws Exception {
        Map<String, Object> before = row(aliceJob);
        mvc.perform(put("/api/jobs/" + aliceJob).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    @Test
    void missingOrInvalidCsrfCannotUpdate() throws Exception {
        Map<String, Object> before = row(aliceJob);
        String body = "{\"companyName\":\"修改公司\",\"jobTitle\":\"修改岗位\"}";
        mvc.perform(put("/api/jobs/" + aliceJob).with(user(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/jobs/" + aliceJob).with(user(alice)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(row(aliceJob)).isEqualTo(before);
    }

    private ResultActions update(Long id, String body) throws Exception {
        return mvc.perform(put("/api/jobs/" + id).with(user(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> expectedRow(String company, String title, String city, String salary,
                                             String responsibilities, String requirements) {
        Map<String, Object> expected = new LinkedHashMap<>(row(aliceJob));
        expected.replaceAll((key, value) -> switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "company_name" -> company;
            case "job_title" -> title;
            case "city" -> city;
            case "salary" -> salary;
            case "responsibilities" -> responsibilities;
            case "requirements" -> requirements;
            default -> value;
        });
        return expected;
    }

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
                    salary, responsibilities, requirements, education_requirement, application_deadline, status)
                VALUES (?, ?, '虚构测试岗位', '测试城市', 'TEST_ONLY', 'https://example.com/job',
                    '面议', '虚构职责', '虚构要求', '不限', '2030-01-01', 'INTERVIEWING')
                """, userId, company);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, userId);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", id);
    }
}
