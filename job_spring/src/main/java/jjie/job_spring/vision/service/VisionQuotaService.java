package jjie.job_spring.vision.service;

import java.time.Clock;
import java.time.ZoneId;
import jjie.job_spring.config.VisionQuotaProperties;
import jjie.job_spring.vision.common.VisionQuotaResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VisionQuotaService {
    public static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private final StringRedisTemplate redis;
    private final VisionQuotaProperties properties;
    private final VisionQuotaKeyService keys;
    private final Clock clock;

    public VisionQuotaService(StringRedisTemplate redis, VisionQuotaProperties properties,
                              VisionQuotaKeyService keys, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.keys = keys;
        this.clock = clock;
    }

    public VisionQuotaResponse query(String clientAddress) {
        // 一次查询只取一次日期，避免刚好跨零点时 key 与 resetAt 不一致。
        var day = clock.instant().atZone(QUOTA_ZONE).toLocalDate();
        var resetAt = day.plusDays(1).atStartOfDay(QUOTA_ZONE).toOffsetDateTime();
        int limit = properties.getDailyLimit();
        if (!properties.isConfigured()) {
            return new VisionQuotaResponse(false, "NOT_CONFIGURED", limit, null, null, resetAt);
        }
        if (clientAddress == null || clientAddress.isBlank()) {
            return new VisionQuotaResponse(false, "CLIENT_UNAVAILABLE", limit, null, null, resetAt);
        }
        try {
            // 仅 GET：缺失的 key 按未使用处理，不初始化、不续期、不扣额。
            String raw = redis.opsForValue().get(keys.dailyIpKey(day, clientAddress));
            long used = raw == null ? 0 : parseCount(raw);
            long remaining = Math.max(0L, (long) limit - used);
            return new VisionQuotaResponse(remaining > 0, remaining > 0 ? "AVAILABLE" : "EXHAUSTED",
                    limit, used, remaining, resetAt);
        } catch (NumberFormatException exception) {
            return new VisionQuotaResponse(false, "INVALID_COUNTER", limit, null, null, resetAt);
        } catch (DataAccessException | IllegalStateException exception) {
            // 不回传 Redis 地址、连接异常、IP 或密钥；故障不能被当成还有完整额度。
            return new VisionQuotaResponse(false, "STORE_UNAVAILABLE", limit, null, null, resetAt);
        }
    }

    private static long parseCount(String raw) {
        if (!raw.matches("0|[1-9][0-9]{0,18}")) throw new NumberFormatException();
        return Long.parseLong(raw);
    }
}
