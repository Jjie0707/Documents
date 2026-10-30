package jjie.job_spring.vision.common;

import java.time.LocalDate;
import jjie.job_spring.job.common.ApplicationDeadlineDeserializer;
import jjie.job_spring.job.validation.ValidJobUrl;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

// 识别草稿允许缺失必填岗位字段；最终保存仍必须经过 CreateJobRequest 校验。
public record JobRecognitionDraft(
        @CodePointLength(max = 200) String companyName,
        @CodePointLength(max = 200) String jobTitle,
        @CodePointLength(max = 100) String city,
        @CodePointLength(max = 100) String salary,
        @CodePointLength(max = 10000) String responsibilities,
        @CodePointLength(max = 10000) String requirements,
        @CodePointLength(max = 100) String educationRequirement,
        @CodePointLength(max = 100) String sourcePlatform,
        @CodePointLength(max = 2048) @ValidJobUrl String originalUrl,
        @JsonDeserialize(using = ApplicationDeadlineDeserializer.class) LocalDate applicationDeadline
) {
    public JobRecognitionDraft {
        companyName = normalize(companyName);
        jobTitle = normalize(jobTitle);
        city = normalize(city);
        salary = normalize(salary);
        responsibilities = normalize(responsibilities);
        requirements = normalize(requirements);
        educationRequirement = normalize(educationRequirement);
        sourcePlatform = normalize(sourcePlatform);
        originalUrl = normalize(originalUrl);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Override
    public String toString() {
        return "JobRecognitionDraft[content=REDACTED]";
    }
}
