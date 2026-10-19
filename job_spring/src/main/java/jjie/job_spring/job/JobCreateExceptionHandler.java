package jjie.job_spring.job;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = JobCreateController.class)
public class JobCreateExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<JobCreateError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        // 只返回字段名和提示，不回显用户输入的岗位内容。
        return ResponseEntity.badRequest().body(
                new JobCreateError("INVALID_REQUEST", "请检查岗位参数", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<JobCreateError> unreadableBody() {
        return ResponseEntity.badRequest().body(
                new JobCreateError("INVALID_REQUEST", "请求体格式不正确", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<JobCreateError> databaseUnavailable() {
        return ResponseEntity.status(503).body(
                new JobCreateError("SERVICE_UNAVAILABLE", "岗位暂时无法保存，请稍后重试", Map.of()));
    }

    public record JobCreateError(String code, String message, Map<String, String> fields) { }
}
