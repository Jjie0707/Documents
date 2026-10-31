package jjie.job_spring.vision.controller;

import jakarta.servlet.http.HttpServletRequest;
import jjie.job_spring.vision.common.VisionQuotaResponse;
import jjie.job_spring.vision.service.VisionQuotaService;
import jjie.job_spring.vision.service.VisionClientIpService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisionQuotaController {
    private final VisionQuotaService service;
    private final VisionClientIpService clients;

    public VisionQuotaController(VisionQuotaService service, VisionClientIpService clients) {
        this.service = service;
        this.clients = clients;
    }

    @GetMapping("/api/vision/quota")
    public ResponseEntity<VisionQuotaResponse> quota(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.query(clients.resolve(request)));
    }
}
