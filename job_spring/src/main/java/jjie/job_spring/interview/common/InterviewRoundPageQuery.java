package jjie.job_spring.interview.common;

import jakarta.validation.constraints.Min;

public record InterviewRoundPageQuery(
        @Min(value = 1, message = "页码必须是大于等于1的整数") Integer page,
        @Min(value = 1, message = "轮次记录ID必须是正整数") Long roundId
) {
    public InterviewRoundPageQuery {
        if (page == null) page = 1;
    }
}
