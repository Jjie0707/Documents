package jjie.job_spring.interview.advice;

import java.util.Map;
import java.util.TreeMap;
import jjie.job_spring.interview.exception.DuplicateInterviewRoundException;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import jjie.job_spring.interview.controller.InterviewRoundUpdateController;
import jjie.job_spring.interview.controller.InterviewRoundCreateController;
import jjie.job_spring.interview.controller.InterviewRoundDeleteController;
import jjie.job_spring.interview.controller.InterviewRoundReadController;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {InterviewRoundCreateController.class, InterviewRoundReadController.class, InterviewRoundUpdateController.class, InterviewRoundDeleteController.class})
public class InterviewRoundExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RoundError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = "interviewTimeSupported".equals(error.getField()) ? "interviewTime" : error.getField();
            String message = error.isBindingFailure() ? "查询参数格式不正确" : error.getDefaultMessage();
            if ("page".equals(field)) message = "页码必须是大于等于1的整数";
            if ("roundId".equals(field)) message = "轮次记录ID必须是正整数";
            fields.putIfAbsent(field, message);
        });
        return ResponseEntity.badRequest().body(new RoundError("INVALID_REQUEST", "请检查面试轮次参数", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<RoundError> unreadableBody() {
        return ResponseEntity.badRequest().body(new RoundError("INVALID_REQUEST",
                "请求体格式不正确，请检查轮次整数和面试时间", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<RoundError> invalidId() {
        return ResponseEntity.badRequest().body(new RoundError("INVALID_REQUEST", "记录ID格式不正确", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<RoundError> missingJob() {
        return ResponseEntity.status(404).body(new RoundError("NOT_FOUND", "岗位不存在", Map.of()));
    }

    @ExceptionHandler(InterviewRoundNotFoundException.class)
    ResponseEntity<RoundError> missingRound() {
        return ResponseEntity.status(404).body(new RoundError("ROUND_NOT_FOUND", "面试轮次不存在或无法访问", Map.of()));
    }

    @ExceptionHandler(DuplicateInterviewRoundException.class)
    ResponseEntity<RoundError> duplicateRound() {
        return ResponseEntity.status(409).body(new RoundError("DUPLICATE_ROUND",
                "该岗位已存在这一轮次，本次未保存，原记录未修改",
                Map.of("roundNo", "该岗位已存在这一轮次，请填写其他轮次号")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<RoundError> conflict() {
        // 其他完整性约束仍由数据库原子检查，不能把所有约束错误都当作重复轮次。
        return ResponseEntity.status(409).body(new RoundError("CONFLICT",
                "关联岗位已变化或存在约束冲突，请重新加载岗位后核对", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<RoundError> unavailable() {
        return ResponseEntity.status(503).body(new RoundError("SERVICE_UNAVAILABLE",
                "面试轮次服务暂不可用，请稍后重试", Map.of()));
    }

    public record RoundError(String code, String message, Map<String, String> fields) { }
}
