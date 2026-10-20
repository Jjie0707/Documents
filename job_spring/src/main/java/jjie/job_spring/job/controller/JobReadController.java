package jjie.job_spring.job.controller;

import java.util.List;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.service.JobReadService;
import jjie.job_spring.job.common.JobResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobReadController {
    private final JobReadService service;

    public JobReadController(JobReadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> list(@AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listForUser(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> detail(@PathVariable Long id,
                                            @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.findForUser(id, user.getId()));
    }
}
