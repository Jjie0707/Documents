package jjie.job_spring.interview.common;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

// 只开放内容字段，主键、用户与所属轮次均由服务端决定。
public record CreateInterviewQuestionRequest(
        @NotBlank(message = "问题内容不能为空")
        @CodePointLength(max = 2000, message = "问题内容不能超过2000个字符") String question,
        @CodePointLength(max = 4000, message = "回答要点不能超过4000个字符") String answerNotes,
        @CodePointLength(max = 100, message = "知识主题不能超过100个字符") String knowledgeTopic,
        @CodePointLength(max = 2000, message = "问题复盘不能超过2000个字符") String reviewNotes
) {
    public CreateInterviewQuestionRequest {
        question = normalize(question);
        answerNotes = normalize(answerNotes);
        knowledgeTopic = normalize(knowledgeTopic);
        reviewNotes = normalize(reviewNotes);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Override
    public String toString() {
        return "CreateInterviewQuestionRequest[content=REDACTED]";
    }
}
