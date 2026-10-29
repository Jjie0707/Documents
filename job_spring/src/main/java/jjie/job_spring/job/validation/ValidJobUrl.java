package jjie.job_spring.job.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobUrlValidator.class)
public @interface ValidJobUrl {
    String message() default "原始链接须为不含账号密码的完整HTTP或HTTPS网址";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
