package jjie.job_spring.interview.common;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

// PUT 表示四个内容字段的完整目标值；选填项缺省/null/空白均清空，归属不能修改。
public record UpdateInterviewQuestionRequest(
        @NotBlank(message = "问题内容不能为空")
        @CodePointLength(max = 2000, message = "问题内容不能超过2000个字符") String question,
        @CodePointLength(max = 4000, message = "回答要点不能超过4000个字符") String answerNotes,
        @CodePointLength(max = 100, message = "知识主题不能超过100个字符") String knowledgeTopic,
        @CodePointLength(max = 2000, message = "问题复盘不能超过2000个字符") String reviewNotes
) {
    public UpdateInterviewQuestionRequest {
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
        return "UpdateInterviewQuestionRequest[content=REDACTED]";
    }
}
