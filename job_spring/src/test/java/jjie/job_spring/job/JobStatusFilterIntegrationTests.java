package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.EnumMap;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-status-filter;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobStatusFilterIntegrationTests {
    private static String testHash;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails alice;
    private AppUserDetails bob;
    private final Map<JobStatus, Long> matching = new EnumMap<>(JobStatus.class);
    private final Map<JobStatus, Long> plain = new EnumMap<>(JobStatus.class);

    @BeforeAll
    static void generateTestOnlyCredential() {
        testHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    }

    @BeforeEach
    void seedAllStatusesForTwoUsers() {
        alice = seedUser("filter_demo_a");
        bob = seedUser("filter_demo_b");
        for (JobStatus state : JobStatus.values()) {
            int field = state.ordinal() % 3;
            matching.put(state, seedJob(alice.getId(), state,
                    "虚构" + (field == 0 ? "Java" : "") + state + "公司",
                    field == 1 ? "Java测试岗位" : "普通岗位", field == 2 ? "Java测试城" : null));
            plain.put(state, seedJob(alice.getId(), state, "对照" + state + "公司", "无关键词岗位", null));
            seedJob(bob.getId(), state, "其他用户Java公司", "Java岗位", "Java测试城");
        }
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void eachStatusReturnsOnlyOwnedRowsInDescendingOrder(JobStatus state) throws Exception {
        List<Map<String, Object>> before = rows();
        String response = mvc.perform(get("/api/jobs").with(user(alice)).param("status", state.name()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].status").value(state.name()))
                .andExpect(jsonPath("$[1].status").value(state.name()))
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(plain.get(state), matching.get(state));
        assertThat(response).doesNotContain("其他用户");
        assertThat(rows()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void keywordAndStatusAreIntersectedWithOwnerForEveryStatus(JobStatus state) throws Exception {
        String response = mvc.perform(get("/api/jobs").with(user(alice))
                        .param("keyword", " java ").param("status", state.name())
                        .param("userId", bob.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(matching.get(state));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t\n"})
    void blankStatusMeansAllAndPreservesKeywordSearch(String value) throws Exception {
        String original = mvc.perform(get("/api/jobs").with(user(alice)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unfiltered = mvc.perform(get("/api/jobs").with(user(alice)).param("status", value))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(unfiltered).isEqualTo(original);
        String keywordOnly = mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", "Java"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String combined = mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", "Java").param("status", value))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(combined).isEqualTo(keywordOnly);
        assertThat(ids(combined)).hasSize(7);
    }

    @Test
    void trimsStatusCodesBeforeConversion() throws Exception {
        String response = mvc.perform(get("/api/jobs").with(user(alice)).param("status", " INTERVIEWING "))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(response)).containsExactly(plain.get(JobStatus.INTERVIEWING), matching.get(JobStatus.INTERVIEWING));
    }

    @ParameterizedTest
    @ValueSource(strings = {"applied", "NO_STATUS", "0", "1", "APPLIED,REJECTED", "DO_NOT_ECHO"})
    void invalidStatusReturnsSanitizedFieldErrorWithoutChangingData(String value) throws Exception {
        List<Map<String, Object>> before = rows();
        String response = mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", "Java").param("status", value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields.status").value("请选择有效的投递状态"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(value, testHash, "IllegalArgumentException", "Failed to convert");
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void unmatchedCombinationAndForeignOnlyMatchReturnEmptyArray() throws Exception {
        seedJob(bob.getId(), JobStatus.OFFER, "FOREIGN_ONLY_MARKER", "其他用户岗位", null);
        for (String keyword : new String[]{"无匹配的虚构关键词", "FOREIGN_ONLY_MARKER"}) {
            mvc.perform(get("/api/jobs").with(user(alice)).param("keyword", keyword).param("status", "OFFER"))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
    }

    @Test
    void emptyAccountHasNoMatchesAndAnonymousQueryStillRequiresLogin() throws Exception {
        AppUserDetails emptyUser = seedUser("filter_demo_empty");
        mvc.perform(get("/api/jobs").with(user(emptyUser)).param("status", "OFFER"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/jobs").param("status", "OFFER")).andExpect(status().isUnauthorized());
    }

    private static List<Long> ids(String response) {
        List<Number> values = JsonPath.read(response, "$[*].id");
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

    private Long seedJob(Long userId, JobStatus state, String company, String title, String city) {
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title,city,status) VALUES (?,?,?,?,?)",
                userId, company, title, city, state.name());
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=? ORDER BY id DESC LIMIT 1",
                Long.class, userId, company);
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM job_applications ORDER BY id");
    }
}
