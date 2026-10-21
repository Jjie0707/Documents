package jjie.job_spring.interview;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:round-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/interview-rounds-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class InterviewRoundConcurrencyIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentFirstRoundRequestsCommitExactlyOneRecord() throws Exception {
        AppUserDetails owner = seedUser();
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构并发公司','虚构测试岗位')", owner.getId());
        Long jobId = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, owner.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未启动");
                    return mvc.perform(post("/api/jobs/" + jobId + "/interview-rounds")
                                    .with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"roundNo\":1,\"review\":\"虚构并发复盘\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(responses.get(0).get(15, TimeUnit.SECONDS), responses.get(1).get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE job_application_id=? AND round_no=1",
                    Long.class, jobId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private AppUserDetails seedUser() {
        String username = "concurrent_" + UUID.randomUUID().toString().replace("-", "");
        String credential = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, credential);
        UserAccount account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        account.setUsername(username);
        account.setPasswordHash(credential);
        return new AppUserDetails(account);
    }
}
