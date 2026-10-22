package jjie.job_spring.interview.advice;

import java.util.Map;
import java.util.TreeMap;
import jjie.job_spring.interview.controller.InterviewQuestionCreateController;
import jjie.job_spring.interview.controller.InterviewQuestionReadController;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {InterviewQuestionCreateController.class, InterviewQuestionReadController.class})
public class InterviewQuestionExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<QuestionError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(),
                "page".equals(error.getField()) ? "页码必须是大于等于1的整数" : error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new QuestionError("INVALID_REQUEST", "请检查面试问题参数", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<QuestionError> unreadableBody() {
        return ResponseEntity.badRequest().body(new QuestionError("INVALID_REQUEST", "请求体格式不正确，请检查问题字段", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<QuestionError> invalidId() {
        return ResponseEntity.badRequest().body(new QuestionError("INVALID_REQUEST", "记录ID格式不正确", Map.of()));
    }

    @ExceptionHandler(InterviewRoundNotFoundException.class)
    ResponseEntity<QuestionError> missingRound() {
        return ResponseEntity.status(404).body(new QuestionError("ROUND_NOT_FOUND", "面试轮次不存在或无法访问", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<QuestionError> conflict() {
        return ResponseEntity.status(409).body(new QuestionError("CONFLICT", "关联轮次已变化或存在约束冲突，请重新加载后核对", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<QuestionError> unavailable() {
        return ResponseEntity.status(503).body(new QuestionError("SERVICE_UNAVAILABLE", "面试问题服务暂不可用，请稍后重试", Map.of()));
    }

    public record QuestionError(String code, String message, Map<String, String> fields) { }
}
