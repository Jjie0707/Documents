package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.common.UpdateInterviewQuestionRequest;
import jjie.job_spring.interview.service.InterviewQuestionWriteService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview-questions")
public class InterviewQuestionUpdateController {
    private final InterviewQuestionWriteService service;

    public InterviewQuestionUpdateController(InterviewQuestionWriteService service) { this.service = service; }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateInterviewQuestionRequest request, @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.updateForUser(id, user.getId(), request));
    }
}
