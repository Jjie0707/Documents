package jjie.job_spring.interview.controller;

import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.service.InterviewQuestionWriteService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview-questions")
public class InterviewQuestionDeleteController {
    private final InterviewQuestionWriteService service;

    public InterviewQuestionDeleteController(InterviewQuestionWriteService service) { this.service = service; }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails user) {
        service.deleteForUser(id, user.getId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
