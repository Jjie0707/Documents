package jjie.job_spring.interview.common;

import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.CodePointLength;

public record InterviewQuestionPageQuery(@Min(value = 1, message = "页码必须是大于等于1的整数") Integer page,
        @Min(value = 1, message = "问题记录ID必须是正整数") Long questionId,
        @CodePointLength(max = 100, message = "搜索关键词不能超过100个字符") String keyword,
        @CodePointLength(max = 100, message = "知识主题不能超过100个字符") String topic) {
    public InterviewQuestionPageQuery {
        if (page == null) page = 1;
        if (keyword != null) {
            keyword = keyword.strip();
            if (keyword.isEmpty()) keyword = null;
        }
        if (topic != null) {
            topic = topic.strip();
            if (topic.isEmpty()) topic = null;
        }
    }

    @Override
    public String toString() {
        return "InterviewQuestionPageQuery[content=REDACTED]";
    }
}
