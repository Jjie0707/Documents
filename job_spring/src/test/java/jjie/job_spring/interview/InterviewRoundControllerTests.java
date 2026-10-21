package jjie.job_spring.interview;

import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.interview.controller.InterviewRoundCreateController;
import jjie.job_spring.interview.controller.InterviewRoundReadController;
import jjie.job_spring.interview.service.InterviewRoundCreateService;
import jjie.job_spring.interview.service.InterviewRoundReadService;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({InterviewRoundCreateController.class, InterviewRoundReadController.class})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(SecurityConfig.class)
class InterviewRoundControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private InterviewRoundCreateService createService;
    @MockitoBean private InterviewRoundReadService readService;
    @MockitoBean private UserDetailsService users;

    @Test
    void createDatabaseFailureIsSanitized503() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any()))
                .thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(post("/api/jobs/10/interview-rounds").with(user(identity())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1,\"review\":\"PRIVATE_REVIEW\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_REVIEW"))));
    }

    @Test
    void readDatabaseFailureIsSanitized503() throws Exception {
        when(readService.listForUser(10L, 1L)).thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(get("/api/jobs/10/interview-rounds").with(user(identity())))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))));
    }

    @Test
    void concurrentParentRemovalOrConstraintFailureReturnsSanitized409() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("PRIVATE_CONSTRAINT_DETAILS"));
        mvc.perform(post("/api/jobs/10/interview-rounds").with(user(identity())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("PRIVATE_CONSTRAINT_DETAILS"))));
    }

    @Test
    void acceptsSupplementaryUnicodeAtColumnCharacterLimits() throws Exception {
        // H2 VARCHAR 按 UTF-16 单元计长；此处单独验证 MVC 的 Unicode 码点校验。
        when(createService.createForUser(eq(10L), eq(1L), any())).thenAnswer(invocation -> {
            jjie.job_spring.interview.common.CreateInterviewRoundRequest request = invocation.getArgument(2);
            return new jjie.job_spring.interview.common.InterviewRoundResponse(1L, request.roundNo(),
                    request.interviewTime(), request.roundType(), request.result(), request.review());
        });
        mvc.perform(post("/api/jobs/10/interview-rounds").with(user(identity())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundNo\":1,\"roundType\":\"" + "😀".repeat(50) + "\",\"result\":\"" + "😀".repeat(100) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roundType").value("😀".repeat(50)));
    }

    @Test
    void duplicateRoundHasDedicatedCodeAndFieldMessage() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any()))
                .thenThrow(new jjie.job_spring.interview.exception.DuplicateInterviewRoundException());
        mvc.perform(post("/api/jobs/10/interview-rounds").with(user(identity())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roundNo\":1,\"review\":\"PRIVATE_REVIEW\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROUND"))
                .andExpect(jsonPath("$.fields.roundNo").isString())
                .andExpect(content().string(not(containsString("PRIVATE_REVIEW"))));
    }

    private AppUserDetails identity() {
        UserAccount account = new UserAccount();
        account.setId(1L); account.setUsername("round_controller_demo"); account.setPasswordHash(UUID.randomUUID().toString());
        return new AppUserDetails(account);
    }
}
