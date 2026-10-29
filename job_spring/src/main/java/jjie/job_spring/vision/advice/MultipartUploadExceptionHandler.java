package jjie.job_spring.vision.advice;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

// Multipart 解析可早于 Controller 匹配；此处仅全局处理上传解析异常，不改变其他业务错误。
@RestControllerAdvice
public class MultipartUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("code", "IMAGE_TOO_LARGE", "message", "图片不能超过5 MiB，整个上传请求不能超过6 MiB"));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Map<String, String>> invalidMultipart() {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_UPLOAD_REQUEST", "message", "图片上传请求无法读取，请重新选择图片"));
    }
}
