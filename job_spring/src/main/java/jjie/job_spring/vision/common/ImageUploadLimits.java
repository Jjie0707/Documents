package jjie.job_spring.vision.common;

public final class ImageUploadLimits {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DIMENSION = 10000;
    public static final long MAX_PIXELS = 12000000L;

    private ImageUploadLimits() { }
}
