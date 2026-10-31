package jjie.job_spring.vision.advice;

import java.util.Map;
import jjie.job_spring.vision.controller.VisionExtractionController;
import jjie.job_spring.vision.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = VisionExtractionController.class)
public class VisionExtractionExceptionHandler {
    private ResponseEntity<Map<String, String>> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", code, "message", message));
    }
    @ExceptionHandler(VisionModelException.class)
    ResponseEntity<Map<String, String>> model(VisionModelException e) {
        int status = switch (e.getCode()) {
            case "RECOGNITION_IN_PROGRESS" -> 409;
            case "MODEL_TIMEOUT" -> 504;
            case "INVALID_MODEL_RESPONSE" -> 502;
            default -> 503;
        };
        return error(status, e.getCode(), e.getMessage());
    }
    @ExceptionHandler(VisionQuotaException.class)
    ResponseEntity<Map<String, String>> quota(VisionQuotaException e) {
        return error(e.getCode().endsWith("QUOTA_EXHAUSTED") ? 429 : 503, e.getCode(), e.getMessage());
    }
    @ExceptionHandler(RecognitionResultException.class)
    ResponseEntity<Map<String, String>> result(RecognitionResultException e) { return error(502, e.getCode(), e.getMessage()); }
}
