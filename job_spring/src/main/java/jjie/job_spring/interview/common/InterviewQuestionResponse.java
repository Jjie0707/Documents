package jjie.job_spring.interview.common;

public record InterviewQuestionResponse(Long id, String question, String answerNotes,
                                        String knowledgeTopic, String reviewNotes) {
    public static InterviewQuestionResponse from(InterviewQuestion row) {
        return new InterviewQuestionResponse(row.getId(), row.getQuestion(), row.getAnswerNotes(),
                row.getKnowledgeTopic(), row.getReviewNotes());
    }
}
