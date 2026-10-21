package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-search;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobSearchIntegrationTests {
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private Long companyJob;
    private Long titleJob;
    private Long cityJob;
    private Long plainJob;

    @BeforeAll
    static void generateTestOnlyCredential() {
        testHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    }

    @BeforeEach
    void seedIsolatedMemoryDatabase() {
        alice = seedUser("search_demo_a");
        bob = seedUser("search_demo_b");
        companyJob = seedJob(alice.getId(), "虚构Java公司", "普通岗位甲", null);
        titleJob = seedJob(alice.getId(), "虚构公司乙", "高级 JaVa 工程师", null);
        cityJob = seedJob(alice.getId(), "虚构公司丙", "普通岗位丙", "Java测试城");
        plainJob = seedJob(alice.getId(), "无关公司", "普通岗位丁", "其他城市");
        jdbc.update("UPDATE job_applications SET salary='Java', responsibilities='Java', requirements='Java' WHERE id=?", plainJob);
        // 三种 OR 分支都放置其他用户的匹配记录，检查分组不会绕过 user_id。
        seedJob(bob.getId(), "其他用户Java公司", "其他岗位甲", null);
        seedJob(bob.getId(), "其他用户公司乙", "其他用户Java岗位", null);
        seedJob(bob.getId(), "其他用户公司丙", "其他岗位丙", "Java测试城");
    }

    @Test
    void absentKeywordPreservesOrderAndFieldsInPage() throws Exception {
        String response = mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("INTERVIEWING"))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(plainJob, cityJob, titleJob, companyJob);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t\n", "\u2003"})
    void emptyOrWhitespaceKeywordIsEquivalentToOriginalList(String keyword) throws Exception {
        String original = mvc.perform(get("/api/jobs").with(user(alice))).andReturn().getResponse().getContentAsString();
        String filtered = search(keyword).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(filtered).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "java", "\u2003jAvA  "})
    void searchesThreeFieldsCaseInsensitivelyAndTrimsKeyword(String keyword) throws Exception {
        String response = search(keyword).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(cityJob, titleJob, companyJob);
        assertThat(response).doesNotContain("其他用户", "userId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java公司", "高级 JaVa", "Java测试城"})
    void matchesIndividualFieldSubstrings(String keyword) throws Exception {
        Long expected = switch (keyword) {
            case "Java公司" -> companyJob;
            case "高级 JaVa" -> titleJob;
            default -> cityJob;
        };
        String response = search(keyword).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --", "Java+&"})
    void metacharactersAndSqlLookingInputAreLiteralSubstrings(String keyword) throws Exception {
        Long literalJob = seedJob(alice.getId(), "字符测试公司", "前缀" + keyword + "后缀", null);
        List<Map<String, Object>> before = jobs();
        String response = search(keyword).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(literalJob);
        assertThat(jobs()).isEqualTo(before);
    }

    @Test
    void encodedPlusAndAmpersandAreReadAsPartOfKeyword() throws Exception {
        Long literalJob = seedJob(alice.getId(), "编码测试公司", "Java+&测试岗位", null);
        String response = mvc.perform(get(URI.create("/api/jobs?keyword=Java%2B%26")).with(user(alice)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(literalJob);
    }

    @ParameterizedTest
    @ValueSource(strings = {"界", "😀"})
    void acceptsOneHundredUnicodeCodePointsAfterTrimming(String unit) throws Exception {
        String keyword = unit.repeat(100);
        Long boundaryJob = seedJob(alice.getId(), "边界测试公司", keyword, null);
        String response = search("  " + keyword + "  ").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(boundaryJob);
    }

    @ParameterizedTest
    @MethodSource("overlongKeywords")
    void overlongKeywordReturnsFieldErrorWithoutEchoOrDatabaseChanges(String keyword) throws Exception {
        List<Map<String, Object>> before = jobs();
        String response = search(keyword).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields.keyword").value("搜索关键词不能超过100个字符"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", testHash);
        assertThat(jobs()).isEqualTo(before);
    }

    static Stream<String> overlongKeywords() {
        return Stream.of("界".repeat(101), "😀".repeat(101), "DO_NOT_ECHO" + "x".repeat(100));
    }

    @Test
    void forgedUserParameterCannotChangeOwnerAndEachUserGetsOwnMatches() throws Exception {
        String response = mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", "Java")
                        .param("userId", bob.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(cityJob, titleJob, companyJob);
        mvc.perform(get("/api/jobs").with(user(bob)).param("keyword", "Java"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].companyName").value("其他用户公司丙"));
    }

    @Test
    void noMatchOrForeignOnlyMatchReturnsEmptyPage() throws Exception {
        seedJob(bob.getId(), "FOREIGN_ONLY_MARKER", "其他用户专用岗位", null);
        for (String keyword : new String[] {"没有匹配的虚构关键词", "FOREIGN_ONLY_MARKER", "' OR 1=1 --"}) {
            search(keyword).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @Test
    void accountWithoutJobsGetsEmptyArrayWithAndWithoutKeyword() throws Exception {
        AppUserDetails emptyUser = seedUser("search_demo_empty");
        mvc.perform(get("/api/jobs").with(user(emptyUser))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/jobs").with(user(emptyUser)).param("keyword", "Java"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getSearchNeedsLoginButDoesNotRequireCsrfOrWriteData() throws Exception {
        List<Map<String, Object>> before = jobs();
        mvc.perform(get("/api/jobs").param("keyword", "Java")).andExpect(status().isUnauthorized());
        search("Java").andExpect(status().isOk());
        assertThat(jobs()).isEqualTo(before);
    }

    private ResultActions search(String keyword) throws Exception {
        return mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", keyword));
    }

    private static List<Long> ids(String response) {
        List<Number> values = JsonPath.read(response, "$.items[*].id");
        return values.stream().map(Number::longValue).toList();
    }

    private AppUserDetails seedUser(String username) {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, testHash);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username);
        account.setPasswordHash(testHash);
        return new AppUserDetails(account);
    }

    private Long seedJob(Long userId, String company, String title, String city) {
        jdbc.update("""
                INSERT INTO job_applications(user_id,company_name,job_title,city,status)
                VALUES (?,?,?,?,'INTERVIEWING')
                """, userId, company, title, city);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=? ORDER BY id DESC LIMIT 1",
                Long.class, userId, company);
    }

    private List<Map<String, Object>> jobs() {
        return jdbc.queryForList("SELECT * FROM job_applications ORDER BY id");
    }
}
