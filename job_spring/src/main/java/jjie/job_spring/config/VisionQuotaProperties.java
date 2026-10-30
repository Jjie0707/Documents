package jjie.job_spring.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vision.quota")
public class VisionQuotaProperties {
    private int dailyLimit = 5;
    private String hmacSecret = "";

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }

    public boolean isConfigured() {
        return dailyLimit > 0 && hmacSecret != null && !hmacSecret.isBlank()
                && hmacSecret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    // 不使用会自动输出密钥字段的 Lombok @Data / record toString。
    @Override public String toString() { return "VisionQuotaProperties[REDACTED]"; }
}
