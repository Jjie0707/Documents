package jjie.job_spring.job.advice;

import java.util.Map;
import java.util.TreeMap;

import jjie.job_spring.job.controller.JobReadController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = JobReadController.class)
public class JobReadExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<QueryError> invalidQuery(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            // 类型转换错误的默认消息可能包含原输入或异常细节，返回固定业务提示。
            String message = error.isBindingFailure() ? "查询参数格式不正确" : error.getDefaultMessage();
            if ("status".equals(error.getField())) message = "请选择有效的投递状态";
            if ("page".equals(error.getField())) message = "页码必须是大于等于1的整数";
            if ("size".equals(error.getField())) message = "每页条数必须是1到50之间的整数";
            fields.putIfAbsent(error.getField(), message);
        });
        return ResponseEntity.badRequest().body(
                new QueryError("INVALID_REQUEST", "请检查岗位查询参数", fields));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> missingJob() {
        return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND", "message", "岗位不存在"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "SERVICE_UNAVAILABLE", "message", "岗位暂时无法加载，请稍后重试"));
    }

    public record QueryError(String code, String message, Map<String, String> fields) { }
}
