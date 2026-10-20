package jjie.job_spring.job.advice;

import java.util.Map;
import jjie.job_spring.job.controller.JobDeleteController;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = JobDeleteController.class)
public class JobDeleteExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<DeleteError> invalidId() {
        return ResponseEntity.badRequest().body(
                new DeleteError("INVALID_REQUEST", "岗位ID格式不正确", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<DeleteError> missingJob() {
        return ResponseEntity.status(404).body(new DeleteError("NOT_FOUND", "岗位不存在", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<DeleteError> conflict() {
        return ResponseEntity.status(409).body(new DeleteError(
                "CONFLICT", "岗位存在关联数据或约束冲突，无法删除", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<DeleteError> unavailable() {
        return ResponseEntity.status(503).body(new DeleteError(
                "SERVICE_UNAVAILABLE", "岗位暂时无法删除，请稍后重试", Map.of()));
    }

    public record DeleteError(String code, String message, Map<String, String> fields) { }
}
