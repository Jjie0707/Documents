package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.CreateInterviewQuestionRequest;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.service.InterviewQuestionCreateService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview-rounds/{id}/questions")
public class InterviewQuestionCreateController {
    private final InterviewQuestionCreateService service;

    public InterviewQuestionCreateController(InterviewQuestionCreateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InterviewQuestionResponse> create(@PathVariable Long id,
            @Valid @RequestBody CreateInterviewQuestionRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.createForUser(id, user.getId(), request));
    }
}
