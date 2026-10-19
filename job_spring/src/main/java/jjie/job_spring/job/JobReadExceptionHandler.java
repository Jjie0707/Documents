package jjie.job_spring.job;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = JobReadController.class)
public class JobReadExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> missingJob() {
        return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND", "message", "岗位不存在"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "SERVICE_UNAVAILABLE", "message", "岗位暂时无法加载，请稍后重试"));
    }
}
