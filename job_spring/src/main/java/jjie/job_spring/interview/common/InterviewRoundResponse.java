package jjie.job_spring.interview.common;

import java.time.LocalDateTime;

public record InterviewRoundResponse(Long id, Integer roundNo, LocalDateTime interviewTime,
                                      String roundType, String result, String review) {
    public static InterviewRoundResponse from(InterviewRound round) {
        return new InterviewRoundResponse(round.getId(), round.getRoundNo(), round.getInterviewTime(),
                round.getRoundType(), round.getResult(), round.getReview());
    }
}
