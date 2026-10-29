package jjie.job_spring.job.advice;

import java.util.Map;
import java.util.TreeMap;
import jjie.job_spring.job.controller.JobUpdateController;
import jjie.job_spring.job.common.ApplicationDeadlineDeserializer;
import tools.jackson.core.JacksonException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = JobUpdateController.class)
public class JobUpdateExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<UpdateError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                new UpdateError("INVALID_REQUEST", "请检查岗位参数", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<UpdateError> unreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JacksonException json
                && json.getPath().stream().anyMatch(part -> "applicationDeadline".equals(part.getPropertyName()))) {
            return ResponseEntity.badRequest().body(new UpdateError("INVALID_REQUEST", "请检查岗位参数",
                    Map.of("applicationDeadline", ApplicationDeadlineDeserializer.ERROR_MESSAGE)));
        }
        return ResponseEntity.badRequest().body(
                new UpdateError("INVALID_REQUEST", "请求体格式不正确", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<UpdateError> invalidId() {
        return ResponseEntity.badRequest().body(
                new UpdateError("INVALID_REQUEST", "岗位ID格式不正确", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<UpdateError> missingJob() {
        return ResponseEntity.status(404).body(new UpdateError("NOT_FOUND", "岗位不存在", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<UpdateError> unavailable() {
        return ResponseEntity.status(503).body(new UpdateError(
                "SERVICE_UNAVAILABLE", "岗位信息暂时无法更新，请稍后重试", Map.of()));
    }

    public record UpdateError(String code, String message, Map<String, String> fields) { }
}
