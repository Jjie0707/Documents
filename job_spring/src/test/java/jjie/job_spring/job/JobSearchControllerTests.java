package jjie.job_spring.job;

import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.job.common.JobListQuery;
import jjie.job_spring.job.controller.JobReadController;
import jjie.job_spring.job.service.JobReadService;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobReadController.class)
@Import(SecurityConfig.class)
class JobSearchControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private JobReadService service;
    @MockitoBean private UserDetailsService users;

    @Test
    void searchDatabaseFailurePreservesGeneric503WithoutLeakingDetailsOrKeyword() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(2L);
        account.setUsername("search_controller_demo");
        account.setPasswordHash(UUID.randomUUID().toString());
        when(service.listForUser(2L, new JobListQuery("DO_NOT_ECHO", null, 1, 10)))
                .thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(get("/api/jobs").with(user(new AppUserDetails(account))).param("keyword", "DO_NOT_ECHO"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("DO_NOT_ECHO"))));
    }
}
