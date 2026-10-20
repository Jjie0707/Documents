package jjie.job_spring.auth.common;

import jakarta.validation.constraints.NotBlank;
import jjie.job_spring.auth.validation.ValidPassword;
import org.hibernate.validator.constraints.CodePointLength;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @CodePointLength(max = 50, message = "用户名不能超过50个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @ValidPassword
        String password
) {

    public RegisterRequest {
        username = username == null ? null : username.strip();
        // 密码保留原样，不去除空白，不进行截断。
    }

    @Override
    public String toString() {
        return "RegisterRequest[password=REDACTED]";
    }
}
