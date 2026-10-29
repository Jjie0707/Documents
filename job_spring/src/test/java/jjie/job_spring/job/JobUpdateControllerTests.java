package jjie.job_spring.job;

import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.job.common.UpdateJobRequest;
import jjie.job_spring.job.controller.JobUpdateController;
import jjie.job_spring.job.service.JobUpdateService;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobUpdateController.class)
@Import(SecurityConfig.class)
class JobUpdateControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private JobUpdateService service;
    @MockitoBean private UserDetailsService users;

    @Test
    void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(2L);
        account.setUsername("update_controller_demo");
        account.setPasswordHash(UUID.randomUUID().toString());
        when(service.updateForUser(1L, 2L, new UpdateJobRequest("虚构公司", "测试岗位", null, null, "PRIVATE_RESPONSIBILITIES", "PRIVATE_REQUIREMENTS", null, null, null, null)))
                .thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(put("/api/jobs/1").with(user(new AppUserDetails(account))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"虚构公司","jobTitle":"测试岗位",
                                 "responsibilities":"PRIVATE_RESPONSIBILITIES","requirements":"PRIVATE_REQUIREMENTS"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_RESPONSIBILITIES"))))
                .andExpect(content().string(not(containsString("PRIVATE_REQUIREMENTS"))));
    }
}
