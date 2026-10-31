package jjie.job_spring.vision;

import java.time.Clock;
import java.util.UUID;
import jjie.job_spring.config.VisionQuotaProperties;
import jjie.job_spring.vision.exception.VisionQuotaException;
import jjie.job_spring.vision.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VisionQuotaReservationTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final VisionQuotaProperties config = new VisionQuotaProperties();
    private final VisionQuotaService service;
    VisionQuotaReservationTests() {
        config.setHmacSecret(UUID.randomUUID().toString());
        service = new VisionQuotaService(redis, config, new VisionQuotaKeyService(config), Clock.systemUTC());
    }
    @ParameterizedTest
    @CsvSource({"0,IP_QUOTA_EXHAUSTED", "-1,GLOBAL_QUOTA_EXHAUSTED", "-2,INVALID_COUNTER", "-3,QUOTA_DAY_CHANGED", "99,STORE_UNAVAILABLE"})
    void resultCodesRejectWithoutLeakingDetails(long code, String expected) {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(code);
        assertThatThrownBy(() -> service.reserve("192.0.2.1")).isInstanceOfSatisfying(VisionQuotaException.class,
                error -> assertThat(error.getCode()).isEqualTo(expected));
    }
    @Test void nullReplyAndConnectionFailureNeverRetry() {
        assertThatThrownBy(() -> service.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        reset(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("PRIVATE_CONNECTION"));
        assertThatThrownBy(() -> service.reserve("192.0.2.1"))
                .isInstanceOf(VisionQuotaException.class).hasMessageNotContaining("PRIVATE_CONNECTION").hasNoCause();
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void unavailableConfigOrIdentityNeverTouchesRedis() {
        config.setGlobalDailyLimit(0);
        assertThatThrownBy(() -> service.reserve("192.0.2.1")).isInstanceOf(VisionQuotaException.class);
        config.setGlobalDailyLimit(100);
        assertThatThrownBy(() -> service.reserve("localhost")).isInstanceOf(VisionQuotaException.class);
        verifyNoInteractions(redis);
    }

    @Test void redisFailureStopsControlledClientAfterImageValidation() {
        ImageValidationService images = mock(ImageValidationService.class);
        java.util.function.Supplier<String> client = mock(java.util.function.Supplier.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("PRIVATE_CONNECTION"));
        var admission = new VisionAdmissionService(images, service);
        var image = new org.springframework.mock.web.MockMultipartFile("image", new byte[]{1});
        assertThatThrownBy(() -> admission.execute(image, "192.0.2.1", client)).isInstanceOf(VisionQuotaException.class);
        verify(images).validate(image);
        verifyNoInteractions(client);
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
