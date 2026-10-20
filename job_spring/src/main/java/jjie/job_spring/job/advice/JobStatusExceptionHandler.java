package jjie.job_spring.job.advice;

import java.util.Map;
import java.util.TreeMap;
import jjie.job_spring.job.controller.JobStatusController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = JobStatusController.class)
public class JobStatusExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<StatusError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                new StatusError("INVALID_REQUEST", "请检查投递状态", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<StatusError> unreadableBody() {
        // 不回显状态输入、枚举异常或原始 JSON。
        return ResponseEntity.badRequest().body(
                new StatusError("INVALID_REQUEST", "请求体格式或投递状态不正确", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<StatusError> invalidId() {
        return ResponseEntity.badRequest().body(
                new StatusError("INVALID_REQUEST", "岗位ID格式不正确", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<StatusError> missingJob() {
        return ResponseEntity.status(404).body(new StatusError("NOT_FOUND", "岗位不存在", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<StatusError> unavailable() {
        return ResponseEntity.status(503).body(new StatusError(
                "SERVICE_UNAVAILABLE", "岗位状态暂时无法更新，请稍后重试", Map.of()));
    }

    public record StatusError(String code, String message, Map<String, String> fields) { }
}
