package jjie.job_spring.job;

import java.util.UUID;
import jjie.job_spring.auth.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
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

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobCreateController.class)
@Import(SecurityConfig.class)
class JobCreateControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private JobCreateService service;
    @MockitoBean private UserDetailsService users;

    @Test
    void databaseFailureReturnsGenericErrorWithoutDatabaseOrJobDetails() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setUsername("controller_demo");
        account.setPasswordHash(UUID.randomUUID().toString());
        when(service.createForUser(any(), eq(1L)))
                .thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(post("/api/jobs").with(user(new AppUserDetails(account))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"PRIVATE_JOB_CONTENT\",\"jobTitle\":\"示例岗位\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("岗位暂时无法保存，请稍后重试"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_JOB_CONTENT"))));
    }
}
