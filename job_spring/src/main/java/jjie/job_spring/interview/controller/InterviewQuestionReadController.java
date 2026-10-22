package jjie.job_spring.interview.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.interview.common.InterviewQuestionPageQuery;
import jjie.job_spring.interview.common.InterviewQuestionPageResponse;
import jjie.job_spring.interview.service.InterviewQuestionReadService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview-rounds/{id}/questions")
public class InterviewQuestionReadController {
    private final InterviewQuestionReadService service;

    public InterviewQuestionReadController(InterviewQuestionReadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InterviewQuestionPageResponse> list(@PathVariable Long id,
            @Valid @ModelAttribute InterviewQuestionPageQuery query, @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listForUser(id, user.getId(), query));
    }
}
