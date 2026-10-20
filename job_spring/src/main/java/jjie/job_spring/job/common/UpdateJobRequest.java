package jjie.job_spring.job.common;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

// 编辑当前开放的四个基本信息字段；不接收归属、主键或状态。
public record UpdateJobRequest(
        @NotBlank(message = "公司名不能为空")
        @CodePointLength(max = 200, message = "公司名不能超过200个字符")
        String companyName,
        @NotBlank(message = "岗位名不能为空")
        @CodePointLength(max = 200, message = "岗位名不能超过200个字符")
        String jobTitle,
        @CodePointLength(max = 100, message = "城市不能超过100个字符")
        String city,
        @CodePointLength(max = 100, message = "薪资不能超过100个字符")
        String salary
) {
    public UpdateJobRequest {
        companyName = normalize(companyName);
        jobTitle = normalize(jobTitle);
        city = normalize(city);
        salary = normalize(salary);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Override
    public String toString() {
        return "UpdateJobRequest[content=REDACTED]";
    }
}
