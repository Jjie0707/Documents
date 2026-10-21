package jjie.job_spring.interview.exception;

public class DuplicateInterviewRoundException extends RuntimeException {
    public DuplicateInterviewRoundException() {
        super("面试轮次已存在");
    }
}
