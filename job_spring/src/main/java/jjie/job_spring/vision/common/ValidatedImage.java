package jjie.job_spring.vision.common;

/** 同一次有界读取的字节与校验结果；仅用于内存中的模型请求，不回传或持久化。 */
public record ValidatedImage(byte[] bytes, ImageValidationResponse metadata) {
    @Override public String toString() { return "ValidatedImage[REDACTED]"; }
}
