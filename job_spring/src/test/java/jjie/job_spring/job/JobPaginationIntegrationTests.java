package jjie.job_spring.job;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:job-pages;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class JobPaginationIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private AppUserDetails owner;
    private AppUserDetails other;
    private final List<Long> ownIds = new ArrayList<>();
    private final List<Long> combinedIds = new ArrayList<>();

    @BeforeEach
    void seedIsolatedDatabase() {
        owner = seedUser("pages_demo_a");
        other = seedUser("pages_demo_b");
        for (int i = 0; i < 61; i++) {
            String company = "虚构分页公司" + i;
            jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title,city,status) VALUES (?,?,?,?,?)",
                    owner.getId(), company, i % 2 == 0 ? "Java岗位" : "普通岗位", "测试城市",
                    i % 3 == 0 ? "INTERVIEWING" : "APPLIED");
            Long id = jdbc.queryForObject("SELECT id FROM job_applications WHERE company_name=? AND user_id=?",
                    Long.class, company, owner.getId());
            ownIds.add(id);
            if (i % 6 == 0) combinedIds.add(id);
        }
        Collections.reverse(ownIds);
        Collections.reverse(combinedIds);
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title,status) VALUES (?,?,?,?)",
                other.getId(), "其他用户Java公司", "Java岗位", "INTERVIEWING");
    }

    @Test
    void defaultsBoundRowsAndCountOnlyCurrentUser() throws Exception {
        String body = read(query()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.total").value(61))
                .andExpect(jsonPath("$.totalPages").value(7)).andReturn().getResponse().getContentAsString();
        assertThat(ids(body)).containsExactlyElementsOf(ownIds.subList(0, 10));
        assertThat(body).doesNotContain("userId", "password", "其他用户");
    }

    @Test
    void allPagesHaveStableOrderWithoutOverlapOrMissingRows() throws Exception {
        List<Long> collected = new ArrayList<>();
        for (int page = 1; page <= 7; page++) {
            String body = read(query().param("page", String.valueOf(page)))
                    .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.total").value(61))
                    .andExpect(jsonPath("$.items.length()").value(page == 7 ? 1 : 10))
                    .andReturn().getResponse().getContentAsString();
            collected.addAll(ids(body));
        }
        assertThat(collected).containsExactlyElementsOf(ownIds).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "2147483647"})
    void outOfRangePageReturnsLastPageWithoutIntegerOverflow(String page) throws Exception {
        String body = read(query().param("page", page)).andExpect(jsonPath("$.page").value(7))
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(body)).containsExactly(ownIds.get(60));
    }

    @Test
    void countAndPageUseSameKeywordStatusAndOwnerConditions() throws Exception {
        List<Long> collected = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            String body = read(query().param("keyword", " Java ").param("status", "INTERVIEWING")
                            .param("userId", other.getId().toString()).param("size", "5").param("page", String.valueOf(page)))
                    .andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()").value(page == 3 ? 1 : 5))
                    .andReturn().getResponse().getContentAsString();
            collected.addAll(ids(body));
        }
        assertThat(collected).containsExactlyElementsOf(combinedIds);
        read(query().param("keyword", "Java")).andExpect(jsonPath("$.total").value(31));
        read(query().param("status", "INTERVIEWING")).andExpect(jsonPath("$.total").value(21));
    }

    @Test
    void maximumSizeWorksAndExactMultipleDoesNotAddPhantomPage() throws Exception {
        read(query().param("size", "50")).andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.totalPages").value(2));
        read(query().param("size", "50").param("page", "2"))
                .andExpect(jsonPath("$.items.length()").value(11));
        read(query().param("size", "1").param("page", "61"))
                .andExpect(jsonPath("$.totalPages").value(61)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void noMatchesAndEmptyAccountHaveConsistentZeroMetadata() throws Exception {
        read(query().param("keyword", "不存在的虚构关键词").param("page", "99"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.page").value(1));
        AppUserDetails empty = seedUser("pages_demo_empty");
        mvc.perform(get("/api/jobs").with(user(empty))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void omittedAndEmptyPaginationFieldsUseDefaultsIndependently() throws Exception {
        read(query().param("page", "").param("size", ""))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(10));
        read(query().param("page", "2")).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
        read(query().param("size", "20")).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalPages").value(4));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "page,1.5", "page,2147483648", "page,DO_NOT_ECHO",
            "size,0", "size,-1", "size,51", "size,1.5", "size,2147483648", "size,DO_NOT_ECHO"})
    void malformedOrOutOfBoundsParametersReturnSanitized400(String field, String value) throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class);
        String body = mvc.perform(query().param(field, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields." + field).isString()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_ECHO", "NumberFormatException", "Failed to convert");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(before);
    }

    @Test
    void paginationStillRequiresLogin() throws Exception {
        mvc.perform(get("/api/jobs").param("page", "2").param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder query() {
        return get("/api/jobs").with(user(owner));
    }

    private org.springframework.test.web.servlet.ResultActions read(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    private static List<Long> ids(String body) {
        List<Number> values = JsonPath.read(body, "$.items[*].id");
        return values.stream().map(Number::longValue).toList();
    }

    private AppUserDetails seedUser(String username) {
        // 仅给 MockMvc 身份和隔离 H2 使用，不通过密码登录。
        String hash = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, hash);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username);
        account.setPasswordHash(hash);
        return new AppUserDetails(account);
    }
}
