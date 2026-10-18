package jjie.job_spring.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值交给 @NotBlank，避免同一个字段产生重复的空值提示。
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.codePointCount(0, value.length()) >= 8
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
