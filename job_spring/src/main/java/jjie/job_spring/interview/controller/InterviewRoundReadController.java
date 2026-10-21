package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.InterviewRoundPageQuery;
import jjie.job_spring.interview.common.InterviewRoundPageResponse;
import org.springframework.web.bind.annotation.ModelAttribute;
import jjie.job_spring.interview.service.InterviewRoundReadService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs/{id}/interview-rounds")
public class InterviewRoundReadController {
    private final InterviewRoundReadService service;

    public InterviewRoundReadController(InterviewRoundReadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InterviewRoundPageResponse> list(@PathVariable Long id,
            @Valid @ModelAttribute InterviewRoundPageQuery query,
            @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listForUser(id, user.getId(), query));
    }
}
