package jjie.job_spring.vision;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import jjie.job_spring.vision.service.VisionQuotaKeyService;
import jjie.job_spring.vision.service.VisionQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:quota-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class VisionQuotaIntegrationTests {
    private static final String SECRET = UUID.randomUUID().toString();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vision.quota.hmac-secret", () -> SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired VisionQuotaKeyService keys;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean Clock clock;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-30T09:00:00Z"));
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void anonymousRequestIsRejectedBeforeQuotaLookup() throws Exception {
        mvc.perform(get("/api/vision/quota")).andExpect(status().isUnauthorized());
        verifyNoInteractions(redis);
    }

    @Test
    void authenticatedGetNeedsNoCsrfAndCannotBeCachedOrExposeIp() throws Exception {
        mvc.perform(get("/api/vision/quota").with(user("quota_demo")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.dailyLimit").value(30)).andExpect(jsonPath("$.remaining").value(30))
                .andExpect(jsonPath("$.resetAt").isString())
                .andExpect(content().string(not(containsString("127.0.0.1"))))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    void forgedForwardingHeadersDoNotSelectAnotherBucket() throws Exception {
        mvc.perform(get("/api/vision/quota").with(user("quota_demo"))
                        .header("X-Forwarded-For", "192.0.2.55")
                        .header("Forwarded", "for=192.0.2.55")
                        .header("CF-Connecting-IP", "192.0.2.55"))
                .andExpect(status().isOk());
        verify(values).get(keys.dailyIpKey(java.time.LocalDate.of(2026, 10, 30), "127.0.0.1"));
        verifyNoMoreInteractions(values);
    }

    @Test
    void redisFailureIsAnUnavailableSnapshotAndDoesNotBreakHealthEndpoint() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("PRIVATE_REDIS_ADDRESS"));
        mvc.perform(get("/api/vision/quota").with(user("quota_demo")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.remaining").isEmpty())
                .andExpect(content().string(not(containsString("PRIVATE_REDIS_ADDRESS"))));
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
