package jjie.job_spring.vision.common;

import java.time.OffsetDateTime;

/** available 仅代表 IP 额度可用，不代表模型已启用。未知次数用 null，不能冒充 0。 */
public record VisionQuotaResponse(boolean available, String reason, int dailyLimit,
                                  Long used, Long remaining, OffsetDateTime resetAt) {
}
