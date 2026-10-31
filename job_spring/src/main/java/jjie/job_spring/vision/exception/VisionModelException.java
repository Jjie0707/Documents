package jjie.job_spring.vision.exception;

public class VisionModelException extends RuntimeException {
    private final String code;
    public VisionModelException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
