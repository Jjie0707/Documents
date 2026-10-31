package jjie.job_spring.vision.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 识别入口共用前置顺序；没有 HTTP 测试入口，也不直接绑定模型供应商。 */
@Service
public class VisionAdmissionService {
    private final ImageValidationService images;
    private final VisionQuotaService quotas;
    public VisionAdmissionService(ImageValidationService images, VisionQuotaService quotas) {
        this.images = images;
        this.quotas = quotas;
    }

    public <T> T execute(MultipartFile image, String clientAddress, Supplier<T> recognition) {
        java.util.Objects.requireNonNull(recognition);
        images.validate(image);
        quotas.reserve(clientAddress);
        // 不捕获后返还，不重试。结果未知也不能假定服务商没有计次。
        return recognition.get();
    }

    public <T> T executeWithImage(MultipartFile image, String clientAddress,
            java.util.function.Function<jjie.job_spring.vision.common.ValidatedImage, T> recognition) {
        java.util.Objects.requireNonNull(recognition);
        var validated = images.validateAndRead(image);
        quotas.reserve(clientAddress);
        return recognition.apply(validated);
    }
}
