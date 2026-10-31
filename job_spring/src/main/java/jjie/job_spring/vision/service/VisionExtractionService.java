package jjie.job_spring.vision.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jjie.job_spring.config.VisionModelProperties;
import jjie.job_spring.vision.common.JobRecognitionDraft;
import jjie.job_spring.vision.exception.VisionModelException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VisionExtractionService {
    private final VisionModelProperties properties;
    private final VisionAdmissionService admission;
    private final GlmVisionClient client;
    private final RecognitionResultService results;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    public VisionExtractionService(VisionModelProperties properties, VisionAdmissionService admission,
                                   GlmVisionClient client, RecognitionResultService results) {
        this.properties = properties; this.admission = admission; this.client = client; this.results = results;
    }

    public JobRecognitionDraft extract(Long userId, String address, MultipartFile image) {
        if (!properties.isEnabled()) throw new VisionModelException("RECOGNITION_DISABLED", "截图识别暂未开放，可继续检查图片或手动填写");
        if (!properties.isConfigured()) throw new VisionModelException("MODEL_NOT_CONFIGURED", "识别服务尚未配置，可继续检查图片或手动填写");
        if (!inFlight.add(userId)) throw new VisionModelException("RECOGNITION_IN_PROGRESS", "当前账号已有识别请求进行中，请等待结果");
        try {
            return admission.executeWithImage(image, address, validated -> results.parseAndValidate(client.extract(validated)));
        } finally { inFlight.remove(userId); }
    }
}
