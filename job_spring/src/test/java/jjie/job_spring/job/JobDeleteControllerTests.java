package jjie.job_spring.job;

import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.job.controller.JobDeleteController;
import jjie.job_spring.job.service.JobDeleteService;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobDeleteController.class)
@Import(SecurityConfig.class)
class JobDeleteControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private JobDeleteService service;
    @MockitoBean private UserDetailsService users;

    @Test
    void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(2L);
        account.setUsername("delete_controller_demo");
        account.setPasswordHash(UUID.randomUUID().toString());
        doThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"))
                .when(service).deleteForUser(1L, 2L);
        mvc.perform(delete("/api/jobs/1").with(user(new AppUserDetails(account))).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))));
    }
}
