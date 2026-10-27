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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"create", "update"})
    void renumberAndAnotherWriteCannotClaimSameNumber(String secondAction) throws Exception {
        AppUserDetails owner = seedUser();
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构并发改号公司','虚构测试岗位')", owner.getId());
        Long jobId = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, owner.getId());
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", owner.getId(), jobId);
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,2)", owner.getId(), jobId);
        Long first = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=? AND round_no=1", Long.class, jobId);
        Long second = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=? AND round_no=2", Long.class, jobId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                final int action = index;
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未启动");
                    var request = action == 0
                            ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/interview-rounds/" + first)
                            : ("create".equals(secondAction) ? post("/api/jobs/" + jobId + "/interview-rounds")
                            : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/interview-rounds/" + second));
                    return mvc.perform(request.with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"roundNo\":3,\"review\":\"并发改号虚构复盘\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Integer> statuses = List.of(responses.get(0).get(15, TimeUnit.SECONDS), responses.get(1).get(15, TimeUnit.SECONDS));
            assertThat(statuses).contains(409);
            assertThat(statuses.stream().filter(status -> status >= 200 && status < 300).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE job_application_id=? AND round_no=3", Long.class, jobId)).isEqualTo(1);
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"job-create", "round-update"})
    void deletionCoordinatesWithConcurrentCreationAndEditing(String scenario) throws Exception {
        AppUserDetails owner = seedUser();
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构并发删除公司','虚构测试岗位')", owner.getId());
        Long jobId = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, owner.getId());
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", owner.getId(), jobId);
        Long roundId = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=?", Long.class, jobId);
        jdbc.update("INSERT INTO interview_questions(user_id,interview_round_id,question) VALUES (?,?,'虚构并发问题')", owner.getId(), roundId);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                final int action = index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未启动");
                    boolean jobScenario = "job-create".equals(scenario);
                    var request = action == 0
                            ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(jobScenario ? "/api/jobs/" + jobId : "/api/interview-rounds/" + roundId)
                            : (jobScenario ? post("/api/jobs/" + jobId + "/interview-rounds")
                            : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/interview-rounds/" + roundId));
                    return mvc.perform(request.with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                    .content(jobScenario ? "{\"roundNo\":2}" : "{\"roundNo\":1,\"review\":\"并发修改虚构复盘\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(results.get(0).get(15, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(results.get(1).get(15, TimeUnit.SECONDS)).isIn("job-create".equals(scenario) ? 201 : 200, 404);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE job_application_id=?", Long.class, jobId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions WHERE interview_round_id=?", Long.class, roundId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, jobId))
                    .isEqualTo("job-create".equals(scenario) ? 0 : 1);
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"round", "job"})
    void questionCreationCoordinatesWithParentDeletion(String kind) throws Exception {
        AppUserDetails owner = seedUser();
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构问题并发公司','虚构岗位')", owner.getId());
        Long jobId = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, owner.getId());
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", owner.getId(), jobId);
        Long roundId = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=?", Long.class, jobId);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                final int action = index;
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未启动");
                    var request = action == 0
                            ? post("/api/interview-rounds/" + roundId + "/questions")
                            : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                    "round".equals(kind) ? "/api/interview-rounds/" + roundId : "/api/jobs/" + jobId);
                    return mvc.perform(request.with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"question\":\"虚构并发问题\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(responses.get(0).get(15, TimeUnit.SECONDS)).isIn(201, 404);
            assertThat(responses.get(1).get(15, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions WHERE interview_round_id=?", Long.class, roundId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE id=?", Long.class, roundId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, jobId))
                    .isEqualTo("job".equals(kind) ? 0 : 1);
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"update-question", "update-round", "update-job", "delete-round", "delete-job"})
    void questionWritesCoordinateWithQuestionAndParentDeletion(String scenario) throws Exception {
        AppUserDetails owner = seedUser();
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,'虚构问题写入并发公司','虚构岗位')", owner.getId());
        Long jobId = jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=?", Long.class, owner.getId());
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,1)", owner.getId(), jobId);
        Long roundId = jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=?", Long.class, jobId);
        jdbc.update("INSERT INTO interview_questions(user_id,interview_round_id,question) VALUES (?,?,'虚构并发目标')", owner.getId(), roundId);
        Long questionId = jdbc.queryForObject("SELECT id FROM interview_questions WHERE interview_round_id=?", Long.class, roundId);
        String scope = scenario.substring(scenario.indexOf('-') + 1);
        boolean deletingQuestion = scenario.startsWith("delete-");
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var responses = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                final int action = index;
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未启动");
                    var request = action == 0
                            ? (deletingQuestion ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/interview-questions/" + questionId)
                            : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/interview-questions/" + questionId))
                            : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                    "question".equals(scope) ? "/api/interview-questions/" + questionId
                                    : "round".equals(scope) ? "/api/interview-rounds/" + roundId : "/api/jobs/" + jobId);
                    return mvc.perform(request.with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"question\":\"虚构并发更新\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(responses.get(0).get(15, TimeUnit.SECONDS)).isIn(deletingQuestion ? 204 : 200, 404);
            assertThat(responses.get(1).get(15, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions WHERE id=?", Long.class, questionId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_rounds WHERE id=?", Long.class, roundId)).isEqualTo("question".equals(scope) ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE id=?", Long.class, jobId)).isEqualTo("job".equals(scope) ? 0 : 1);
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
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
