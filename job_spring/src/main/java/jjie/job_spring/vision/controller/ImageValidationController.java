package jjie.job_spring.vision.controller;

import jjie.job_spring.vision.common.ImageValidationResponse;
import jjie.job_spring.vision.exception.ImageValidationException;
import jjie.job_spring.vision.service.ImageValidationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageValidationController {
    private final ImageValidationService service;

    public ImageValidationController(ImageValidationService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/vision/images/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageValidationResponse> validate(@RequestParam MultiValueMap<String, MultipartFile> files) {
        if (files.size() != 1 || !files.containsKey("image") || files.get("image").size() != 1) {
            throw new ImageValidationException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_PARTS", "请通过image字段上传一张图片");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.validate(files.getFirst("image")));
    }
}
