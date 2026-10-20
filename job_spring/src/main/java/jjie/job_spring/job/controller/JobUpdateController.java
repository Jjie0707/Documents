package jjie.job_spring.job.controller;

import jakarta.validation.Valid;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.UpdateJobRequest;
import jjie.job_spring.job.service.JobUpdateService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobUpdateController {
    private final JobUpdateService service;

    public JobUpdateController(JobUpdateService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateJobRequest request,
                                              @AuthenticationPrincipal AppUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateForUser(id, user.getId(), request));
    }
}
