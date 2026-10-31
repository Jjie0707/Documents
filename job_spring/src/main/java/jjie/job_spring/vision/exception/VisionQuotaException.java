package jjie.job_spring.vision.exception;

/** 内部前置规则错误；5.7 的识别接口再映射为 HTTP 429/503。 */
public class VisionQuotaException extends RuntimeException {
    private final String code;
    public VisionQuotaException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}
