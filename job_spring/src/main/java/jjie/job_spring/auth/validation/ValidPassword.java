package jjie.job_spring.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface ValidPassword {
    String message() default "密码至少8个字符，且UTF-8编码不超过72字节";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
