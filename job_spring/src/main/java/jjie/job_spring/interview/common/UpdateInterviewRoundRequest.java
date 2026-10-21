package jjie.job_spring.interview.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

// PUT 提交五个字段的完整目标值；选填项缺省或 null 表示清空，不允许移动所属岗位。
public record UpdateInterviewRoundRequest(
        @JsonDeserialize(using = RoundNoDeserializer.class)
        @NotNull(message = "请填写面试轮次")
        @Min(value = 1, message = "面试轮次必须是大于等于1的整数") Integer roundNo,
        @JsonDeserialize(using = InterviewTimeDeserializer.class) LocalDateTime interviewTime,
        @CodePointLength(max = 50, message = "轮次类型不能超过50个字符") String roundType,
        @CodePointLength(max = 100, message = "面试结果不能超过100个字符") String result,
        @CodePointLength(max = 2000, message = "整体复盘不能超过2000个字符") String review
) {
    public UpdateInterviewRoundRequest {
        roundType = normalize(roundType);
        result = normalize(result);
        review = normalize(review);
        // 与现有 DATETIME(3) 精度一致，保存响应与之后的回读保持一致。
        if (interviewTime != null) interviewTime = interviewTime.truncatedTo(ChronoUnit.MILLIS);
    }

    @AssertTrue(message = "面试时间年份必须在1000到9999之间")
    public boolean isInterviewTimeSupported() {
        return interviewTime == null || (interviewTime.getYear() >= 1000 && interviewTime.getYear() <= 9999);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Override
    public String toString() {
        return "UpdateInterviewRoundRequest[content=REDACTED]";
    }
}
