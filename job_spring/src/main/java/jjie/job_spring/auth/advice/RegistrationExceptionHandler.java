package jjie.job_spring.auth.advice;

import java.util.Map;
import java.util.TreeMap;

import jjie.job_spring.auth.exception.UsernameAlreadyExistsException;
import jjie.job_spring.auth.controller.AuthController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class RegistrationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RegistrationError> invalidArguments(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        // 只返回字段名和提示，绝不返回 rejectedValue（其中可能有明文密码）。
        return ResponseEntity.badRequest().body(
                new RegistrationError("INVALID_REQUEST", "请检查注册参数", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<RegistrationError> unreadableBody() {
        return ResponseEntity.badRequest().body(
                new RegistrationError("INVALID_REQUEST", "请求体格式不正确", Map.of()));
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<RegistrationError> duplicateUsername() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new RegistrationError("USERNAME_ALREADY_EXISTS", "用户名已存在", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<RegistrationError> databaseUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new RegistrationError("SERVICE_UNAVAILABLE", "服务暂不可用，请稍后重试", Map.of()));
    }

    public record RegistrationError(String code, String message, Map<String, String> fields) {
    }
}
