package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.CreateInterviewRoundRequest;
import jjie.job_spring.interview.common.InterviewRoundResponse;
import jjie.job_spring.interview.service.InterviewRoundCreateService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs/{id}/interview-rounds")
public class InterviewRoundCreateController {
    private final InterviewRoundCreateService service;

    public InterviewRoundCreateController(InterviewRoundCreateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InterviewRoundResponse> create(@PathVariable Long id,
            @Valid @RequestBody CreateInterviewRoundRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.createForUser(id, user.getId(), request));
    }
}
