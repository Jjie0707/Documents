package jjie.job_spring.job.controller;

import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.job.service.JobDeleteService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobDeleteController {
    private final JobDeleteService service;

    public JobDeleteController(JobDeleteService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AppUserDetails user) {
        service.deleteForUser(id, user.getId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
