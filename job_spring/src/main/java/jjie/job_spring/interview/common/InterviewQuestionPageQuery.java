package jjie.job_spring.interview.common;

import jakarta.validation.constraints.Min;

public record InterviewQuestionPageQuery(@Min(value = 1, message = "页码必须是大于等于1的整数") Integer page,
        @Min(value = 1, message = "问题记录ID必须是正整数") Long questionId) {
    public InterviewQuestionPageQuery {
        if (page == null) page = 1;
    }
}
