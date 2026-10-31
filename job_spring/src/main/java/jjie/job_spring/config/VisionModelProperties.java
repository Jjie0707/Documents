package jjie.job_spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vision.model")
public class VisionModelProperties {
    private static final java.util.Set<String> FREE_VISION_MODELS = java.util.Set.of(
            "glm-4.1v-thinking-flash", "glm-4.6v-flash");
    private boolean enabled;
    private String name = "glm-4.1v-thinking-flash";
    private String apiKey = "";
    private int timeoutSeconds = 45;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    public boolean isConfigured() {
        return FREE_VISION_MODELS.contains(name == null ? "" : name) && apiKey != null && !apiKey.isBlank() && apiKey.length() <= 4096
                && apiKey.chars().allMatch(c -> c >= 33 && c <= 126)
                && timeoutSeconds >= 1 && timeoutSeconds <= 90;
    }
    @Override public String toString() { return "VisionModelProperties[REDACTED]"; }
}
