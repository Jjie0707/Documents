package jjie.job_spring.job.controller;

import jakarta.validation.Valid;
import java.net.URI;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.service.JobCreateService;
import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.CreateJobRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobCreateController {
    private final JobCreateService service;

    public JobCreateController(JobCreateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request,
                                              @AuthenticationPrincipal AppUserDetails user) {
        JobResponse created = service.createForUser(request, user.getId());
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }
}
