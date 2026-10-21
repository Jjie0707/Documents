package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.InterviewRoundResponse;
import jjie.job_spring.interview.common.UpdateInterviewRoundRequest;
import jjie.job_spring.interview.service.InterviewRoundUpdateService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-rounds")
public class InterviewRoundUpdateController {
    private final InterviewRoundUpdateService service;

    public InterviewRoundUpdateController(InterviewRoundUpdateService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewRoundResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateInterviewRoundRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.updateForUser(id, user.getId(), request));
    }
}
