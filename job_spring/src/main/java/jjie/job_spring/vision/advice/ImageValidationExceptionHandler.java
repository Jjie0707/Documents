package jjie.job_spring.vision.advice;

import java.util.Map;
import jjie.job_spring.vision.controller.ImageValidationController;
import jjie.job_spring.vision.controller.VisionExtractionController;
import jjie.job_spring.vision.exception.ImageValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ImageValidationController.class, VisionExtractionController.class})
public class ImageValidationExceptionHandler {
    @ExceptionHandler(ImageValidationException.class)
    ResponseEntity<Map<String, String>> invalidImage(ImageValidationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, String>> invalidContentType() {
        return ResponseEntity.status(415).body(Map.of("code", "INVALID_UPLOAD_REQUEST", "message", "请使用图片文件上传入口"));
    }
}
