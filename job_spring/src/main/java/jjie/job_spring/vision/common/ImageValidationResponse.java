package jjie.job_spring.vision.common;

public record ImageValidationResponse(String format, String mediaType, int sizeBytes, int width, int height) { }
