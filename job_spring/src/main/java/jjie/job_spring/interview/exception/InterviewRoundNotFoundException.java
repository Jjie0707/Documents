package jjie.job_spring.interview.exception;

public class InterviewRoundNotFoundException extends RuntimeException {
    public InterviewRoundNotFoundException() {
        super("面试轮次不存在或无法访问");
    }
}
