package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-additional-fields;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobAdditionalFieldsIntegrationTests {
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private Long jobId;
    private Long otherJobId;

    @BeforeAll
    static void generateTestOnlyCredential() {
        testHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    }

    @BeforeEach
    void seedIsolatedData() {
        alice = seedUser("fields_demo_a");
        bob = seedUser("fields_demo_b");
        jobId = seedJob(alice.getId());
        otherJobId = seedJob(bob.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void writesAllTenFieldsNormalizesAndReadsBackWithoutChangingOtherJob(String method) throws Exception {
        var otherBefore = row(otherJobId);
        Map<String, Object> body = body();
        body.put("educationRequirement", "　 本科及以上  ");
        body.put("sourcePlatform", "  虚构招聘平台  ");
        body.put("originalUrl", "  HTTPS://example.com/招聘?id=demo#岗位  ");
        body.put("applicationDeadline", "  2024-02-29  ");
        String response = write(method, body).andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.educationRequirement").value("本科及以上"))
                .andExpect(jsonPath("$.sourcePlatform").value("虚构招聘平台"))
                .andExpect(jsonPath("$.originalUrl").value("HTTPS://example.com/招聘?id=demo#岗位"))
                .andExpect(jsonPath("$.applicationDeadline").value("2024-02-29"))
                .andExpect(jsonPath("$.responsibilities").value("虚构职责\n负责接口"))
                .andExpect(jsonPath("$.requirements").value("虚构要求"))
                .andExpect(jsonPath("$.status").value(method.equals("POST") ? "TO_APPLY" : "INTERVIEWING"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        assertThat(row(id).get("EDUCATION_REQUIREMENT")).isEqualTo("本科及以上");
        assertThat(row(id).get("APPLICATION_DEADLINE").toString()).isEqualTo("2024-02-29");
        mvc.perform(get("/api/jobs/" + id).with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicationDeadline").value("2024-02-29"))
                .andExpect(jsonPath("$.originalUrl").value("HTTPS://example.com/招聘?id=demo#岗位"));
        mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sourcePlatform").value("虚构招聘平台"));
        mvc.perform(get("/api/jobs/" + id).with(user(bob))).andExpect(status().isNotFound());
        assertThat(row(otherJobId)).isEqualTo(otherBefore);
        if (method.equals("PUT")) {
            write(method, body).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Integer.class)).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @MethodSource("emptyCases")
    void omittedNullAndBlankFieldsBecomeSqlNull(String method, String mode) throws Exception {
        Map<String, Object> body = body();
        for (String field : List.of("educationRequirement", "sourcePlatform", "originalUrl", "applicationDeadline")) {
            if (mode.equals("omitted")) body.remove(field);
            else body.put(field, mode.equals("null") ? null : "　 \t\n ");
        }
        String response = write(method, body).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        var saved = row(id);
        for (String column : List.of("EDUCATION_REQUIREMENT", "SOURCE_PLATFORM", "ORIGINAL_URL", "APPLICATION_DEADLINE")) {
            assertThat(saved.get(column)).as(column).isNull();
        }
        assertThat(saved.get("RESPONSIBILITIES")).isEqualTo("虚构职责\n负责接口");
    }

    static Stream<Arguments> emptyCases() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of("omitted", "null", "blank")
                .map(mode -> Arguments.of(method, mode)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"educationRequirement", "sourcePlatform", "originalUrl", "applicationDeadline"})
    void clearsEachFieldIndependentlyPreservingTheOtherNine(String field) throws Exception {
        Map<String, Object> body = body();
        write("PUT", body).andExpect(status().isOk());
        var before = row(jobId);
        body.put(field, "");
        write("PUT", body).andExpect(status().isOk());
        var expected = new LinkedHashMap<>(before);
        String column = switch (field) {
            case "educationRequirement" -> "EDUCATION_REQUIREMENT";
            case "sourcePlatform" -> "SOURCE_PLATFORM";
            case "originalUrl" -> "ORIGINAL_URL";
            default -> "APPLICATION_DEADLINE";
        };
        expected.put(column, null);
        assertThat(row(jobId)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void acceptsUnicodeLengthBoundaries(String method) throws Exception {
        Map<String, Object> body = body();
        body.put("educationRequirement", "😀".repeat(100));
        body.put("sourcePlatform", "源".repeat(100));
        String url = "https://example.com/" + "岗".repeat(2048 - "https://example.com/".length());
        body.put("originalUrl", url);
        write(method, body).andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.educationRequirement").value("😀".repeat(100)))
                .andExpect(jsonPath("$.originalUrl").value(url));
    }

    @ParameterizedTest
    @MethodSource("validDates")
    void acceptsDatabaseDateBoundariesAndHistoricalDates(String method, String date) throws Exception {
        Map<String, Object> body = body();
        body.put("applicationDeadline", date);
        write(method, body).andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.applicationDeadline").value(date));
    }

    static Stream<Arguments> validDates() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of("1000-01-01", "9999-12-31", "2024-02-29")
                .map(date -> Arguments.of(method, date)));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidFieldRejectsEntireWriteWithFieldErrorAndNoEcho(String method, String field, Object value) throws Exception {
        var before = jdbc.queryForList("SELECT * FROM job_applications ORDER BY id");
        Map<String, Object> body = body();
        body.put("companyName", "DO_NOT_ECHO");
        body.put(field, value);
        String response = write(method, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields." + field).isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", "PRIVATE_LINK_MARKER");
        assertThat(jdbc.queryForList("SELECT * FROM job_applications ORDER BY id")).isEqualTo(before);
    }

    static Stream<Arguments> invalidFields() {
        List<Map.Entry<String, Object>> cases = new ArrayList<>();
        cases.add(Map.entry("educationRequirement", "😀".repeat(101)));
        cases.add(Map.entry("sourcePlatform", "源".repeat(101)));
        cases.add(Map.entry("originalUrl", "https://example.com/" + "x".repeat(2049 - "https://example.com/".length())));
        for (String url : List.of("example.com/job", "//example.com/job", "javascript:alert(1)", "ftp://example.com/job",
                "https:///job", "https://example.com:70000/job", "https://example.com:/job",
                "https://name:PRIVATE_LINK_MARKER@example.com/job", "https://example.com/bad path", "https://example.com/%ZZ")) {
            cases.add(Map.entry("originalUrl", url));
        }
        for (Object date : List.of("2025-02-29", "2026-02-30", "2026-2-03", "0999-12-31", "10000-01-01", "0000-01-01",
                "2026-01-01T00:00:00", "2026-01-01Z", List.of(2026, 1, 1), Map.of("year", 2026), true, 123)) {
            cases.add(Map.entry("applicationDeadline", date));
        }
        return Stream.of("POST", "PUT").flatMap(method -> cases.stream()
                .map(entry -> Arguments.of(method, entry.getKey(), entry.getValue())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void newFieldsCannotBypassOwnershipLoginOrCsrf(String method) throws Exception {
        var otherBefore = row(otherJobId);
        Map<String, Object> body = body();
        body.put("userId", bob.getId()); body.put("id", otherJobId); body.put("status", "OFFER");
        String content = json.writeValueAsString(body);
        mvc.perform(builder(method, jobId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isUnauthorized());
        mvc.perform(builder(method, jobId).with(user(alice)).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/jobs/" + otherJobId).with(user(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isNotFound());
        assertThat(row(otherJobId)).isEqualTo(otherBefore);
        write(method, body).andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").value(method.equals("POST") ? "TO_APPLY" : "INTERVIEWING"));
        assertThat(row(otherJobId)).isEqualTo(otherBefore);
    }

    private Map<String, Object> body() {
        return new LinkedHashMap<>(Map.of("companyName", "虚构公司", "jobTitle", "测试岗位", "city", "测试城市", "salary", "面议",
                "responsibilities", "虚构职责\n负责接口", "requirements", "虚构要求", "educationRequirement", "本科",
                "sourcePlatform", "虚构平台", "originalUrl", "https://example.com/job", "applicationDeadline", "2030-01-01"));
    }

    private ResultActions write(String method, Map<String, Object> body) throws Exception {
        return mvc.perform(builder(method, jobId).with(user(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private MockHttpServletRequestBuilder builder(String method, Long id) {
        return method.equals("POST") ? post("/api/jobs") : put("/api/jobs/" + id);
    }

    private AppUserDetails seedUser(String username) {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, testHash);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username); account.setPasswordHash(testHash);
        return new AppUserDetails(account);
    }

    private Long seedJob(Long userId) {
        jdbc.update("""
                INSERT INTO job_applications(user_id,company_name,job_title,source_platform,original_url,
                    education_requirement,application_deadline,status)
                VALUES (?, '虚构旧公司', '虚构旧岗位', '原平台', 'https://example.com/old', '不限', '2030-01-01', 'INTERVIEWING')
                """, userId);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, userId);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", id);
    }
}
