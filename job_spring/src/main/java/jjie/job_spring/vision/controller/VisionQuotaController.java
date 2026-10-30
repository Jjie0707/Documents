package jjie.job_spring.vision.controller;

import jakarta.servlet.http.HttpServletRequest;
import jjie.job_spring.vision.common.VisionQuotaResponse;
import jjie.job_spring.vision.service.VisionQuotaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisionQuotaController {
    private final VisionQuotaService service;

    public VisionQuotaController(VisionQuotaService service) { this.service = service; }

    @GetMapping("/api/vision/quota")
    public ResponseEntity<VisionQuotaResponse> quota(HttpServletRequest request) {
        // 暂只使用连接来源；不直接读取 X-Forwarded-For / CF-Connecting-IP 等不可信头。
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.query(request.getRemoteAddr()));
    }
}
