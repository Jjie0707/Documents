package jjie.job_spring.vision.exception;

// 尚无识别 HTTP 接口，先保留稳定业务错误码，接入时由对应 advice 映射状态。
public class RecognitionResultException extends RuntimeException {
    private final String code;

    public RecognitionResultException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
