package jjie.job_spring.vision.controller;

import jakarta.servlet.http.HttpServletRequest;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.vision.common.JobRecognitionDraft;
import jjie.job_spring.vision.exception.ImageValidationException;
import jjie.job_spring.vision.service.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VisionExtractionController {
    private final VisionExtractionService service;
    private final VisionClientIpService clients;
    public VisionExtractionController(VisionExtractionService service, VisionClientIpService clients) {
        this.service = service; this.clients = clients;
    }
    @PostMapping(path = "/api/vision/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobRecognitionDraft> extract(@RequestParam MultiValueMap<String, MultipartFile> files,
            HttpServletRequest request, @AuthenticationPrincipal AppUserDetails user) {
        var csrf = (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        boolean extra = request.getParameterMap().keySet().stream()
                .anyMatch(name -> csrf == null || !name.equals(csrf.getParameterName()));
        if (files.size() != 1 || !files.containsKey("image") || files.get("image").size() != 1
                || extra)
            throw new ImageValidationException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_PARTS", "请通过image字段上传一张图片，不附加其他字段");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.extract(user.getId(), clients.resolve(request), files.getFirst("image")));
    }
}
