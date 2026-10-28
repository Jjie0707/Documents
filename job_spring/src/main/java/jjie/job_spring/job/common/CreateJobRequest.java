package jjie.job_spring.job.common;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

// 只接收开放的六个内容字段；归属、主键和初始状态由服务端决定。
public record CreateJobRequest(
        @NotBlank(message = "公司名不能为空")
        @CodePointLength(max = 200, message = "公司名不能超过200个字符")
        String companyName,
        @NotBlank(message = "岗位名不能为空")
        @CodePointLength(max = 200, message = "岗位名不能超过200个字符")
        String jobTitle,
        @CodePointLength(max = 100, message = "城市不能超过100个字符")
        String city,
        @CodePointLength(max = 100, message = "薪资不能超过100个字符")
        String salary,
        @CodePointLength(max = 10000, message = "岗位职责不能超过10000个字符")
        String responsibilities,
        @CodePointLength(max = 10000, message = "任职要求不能超过10000个字符")
        String requirements
) {
    public CreateJobRequest {
        companyName = normalize(companyName);
        jobTitle = normalize(jobTitle);
        city = normalize(city);
        salary = normalize(salary);
        responsibilities = normalize(responsibilities);
        requirements = normalize(requirements);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Override
    public String toString() {
        return "CreateJobRequest[content=REDACTED]";
    }
}
