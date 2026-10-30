package jjie.job_spring.vision.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jjie.job_spring.config.VisionQuotaProperties;
import org.springframework.stereotype.Service;

@Service
public class VisionQuotaKeyService {
    private final VisionQuotaProperties properties;

    public VisionQuotaKeyService(VisionQuotaProperties properties) {
        this.properties = properties;
    }

    public String dailyIpKey(LocalDate day, String clientAddress) {
        try {
            // 每次创建 Mac，避免共享有状态实例的线程安全问题；加日期避免跨日关联标识。
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String digest = HexFormat.of().formatHex(mac.doFinal(
                    (day + "\n" + clientAddress).getBytes(StandardCharsets.UTF_8)));
            return "vision:quota:v1:{" + day + "}:ip:" + digest;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Quota key generation unavailable");
        }
    }
}
