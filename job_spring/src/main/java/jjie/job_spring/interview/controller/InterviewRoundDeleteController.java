package jjie.job_spring.interview.controller;

import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.service.InterviewRoundDeleteService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-rounds")
public class InterviewRoundDeleteController {
    private final InterviewRoundDeleteService service;

    public InterviewRoundDeleteController(InterviewRoundDeleteService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails user) {
        service.deleteForUser(id, user.getId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
