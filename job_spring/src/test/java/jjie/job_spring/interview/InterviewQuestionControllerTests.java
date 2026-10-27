package jjie.job_spring.interview;

import java.util.UUID;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.config.SecurityConfig;
import jjie.job_spring.interview.common.CreateInterviewQuestionRequest;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.controller.InterviewQuestionCreateController;
import jjie.job_spring.interview.controller.InterviewQuestionReadController;
import jjie.job_spring.interview.service.InterviewQuestionCreateService;
import jjie.job_spring.interview.service.InterviewQuestionReadService;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({InterviewQuestionCreateController.class, InterviewQuestionReadController.class,
        jjie.job_spring.interview.controller.InterviewQuestionUpdateController.class,
        jjie.job_spring.interview.controller.InterviewQuestionDeleteController.class})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(SecurityConfig.class)
class InterviewQuestionControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private InterviewQuestionCreateService createService;
    @MockitoBean private InterviewQuestionReadService readService;
    @MockitoBean private UserDetailsService users;
    @MockitoBean private jjie.job_spring.interview.service.InterviewQuestionWriteService writeService;

    @Test
    void createDatabaseFailureIsSanitized503() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any())).thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(post(path()).with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"PRIVATE_QUESTION\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_QUESTION"))));
    }

    @Test
    void readDatabaseFailureIsSanitized503() throws Exception {
        when(readService.listForUser(eq(10L), eq(1L), any())).thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(get(path()).with(user(identity())).param("keyword", "PRIVATE_SEARCH").param("topic", "PRIVATE_TOPIC"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_SEARCH"))))
                .andExpect(content().string(not(containsString("PRIVATE_TOPIC"))));
    }

    @Test
    void constraintFailureIsSanitized409() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any())).thenThrow(new DataIntegrityViolationException("PRIVATE_CONSTRAINT_DETAILS"));
        mvc.perform(post(path()).with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"PRIVATE_QUESTION\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("PRIVATE_CONSTRAINT_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_QUESTION"))));
    }

    @Test
    void unicodeUsesCodePointsAtLimitAndDtoDoesNotPrintContent() throws Exception {
        when(createService.createForUser(eq(10L), eq(1L), any())).thenAnswer(invocation -> {
            CreateInterviewQuestionRequest values = invocation.getArgument(2);
            return new InterviewQuestionResponse(1L, values.question(), values.answerNotes(), values.knowledgeTopic(), values.reviewNotes());
        });
        var values = new CreateInterviewQuestionRequest("😀".repeat(2000), "😀".repeat(4000), "😀".repeat(100), "😀".repeat(2000));
        assertThat(values.toString()).doesNotContain("😀");
        mvc.perform(post(path()).with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(values)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.knowledgeTopic").value("😀".repeat(100)));
        mvc.perform(post(path()).with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateInterviewQuestionRequest("虚构问题", null, "😀".repeat(101), null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.knowledgeTopic").isString());
    }

    @Test
    void updateAndDeleteDatabaseFailuresAreSanitized() throws Exception {
        when(writeService.updateForUser(eq(10L), eq(1L), any())).thenThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS"));
        mvc.perform(put("/api/interview-questions/10").with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"PRIVATE_QUESTION\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))))
                .andExpect(content().string(not(containsString("PRIVATE_QUESTION"))));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("PRIVATE_DATABASE_DETAILS")).when(writeService).deleteForUser(10L, 1L);
        mvc.perform(delete("/api/interview-questions/10").with(user(identity())).with(csrf()))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAILS"))));
    }

    @Test
    void updateAcceptsCodePointLimitsAndRedactsDto() throws Exception {
        var values = new jjie.job_spring.interview.common.UpdateInterviewQuestionRequest("😀".repeat(2000), "😀".repeat(4000), "😀".repeat(100), "😀".repeat(2000));
        assertThat(values.toString()).doesNotContain("😀");
        when(writeService.updateForUser(eq(10L), eq(1L), any())).thenAnswer(invocation -> {
            jjie.job_spring.interview.common.UpdateInterviewQuestionRequest value = invocation.getArgument(2);
            return new InterviewQuestionResponse(10L, value.question(), value.answerNotes(), value.knowledgeTopic(), value.reviewNotes());
        });
        mvc.perform(put("/api/interview-questions/10").with(user(identity())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(values))).andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeTopic").value("😀".repeat(100)));
    }

    private String path() { return "/api/interview-rounds/10/questions"; }
    private AppUserDetails identity() {
        UserAccount account = new UserAccount(); account.setId(1L); account.setUsername("question_controller_demo");
        account.setPasswordHash(UUID.randomUUID().toString()); return new AppUserDetails(account);
    }
}
