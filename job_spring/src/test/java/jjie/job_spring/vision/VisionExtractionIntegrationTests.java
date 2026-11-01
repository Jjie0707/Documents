package jjie.job_spring.vision;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.Map;
import javax.imageio.ImageIO;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import jjie.job_spring.config.VisionModelProperties;
import jjie.job_spring.vision.service.GlmVisionClient;
import jjie.job_spring.vision.exception.VisionModelException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:vision-extract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"})
@AutoConfigureMockMvc
@Sql(scripts={"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class VisionExtractionIntegrationTests {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("vision.quota.hmac-secret", () -> UUID.randomUUID().toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired VisionModelProperties model;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean GlmVisionClient client;
    private AppUserDetails account() {
        var account = new UserAccount(); account.setId(1L); account.setUsername("extract_demo"); account.setPasswordHash("fictional");
        return new AppUserDetails(account);
    }
    @BeforeEach void setUp() {
        model.setEnabled(true); model.setApiKey(UUID.randomUUID().toString());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        when(client.extract(any())).thenReturn("{\"jobTitle\":\"Java工程师\",\"sourcePlatform\":null}");
    }
    private MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("image", "PRIVATE_FILENAME.png", "image/png", bytes.toByteArray());
    }
    @Test void requiresLoginAndCsrfBeforeQuotaOrModel() throws Exception {
        mvc.perform(multipart("/api/vision/extract").file(image()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account()))).andExpect(status().isForbidden());
        verifyNoInteractions(redis,client);
    }
    @Test void returnsPartialValidatedDraftNoStoreAndNeverCreatesJob() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications",Long.class);
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.jobTitle").value("Java工程师"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(content().string(not(containsString("PRIVATE_FILENAME"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications",Long.class)).isEqualTo(before);
        verify(redis).execute(any(RedisScript.class),anyList(),any(Object[].class));
        verify(client).extract(any());
    }
    @Test void invalidImageOrPartsNeverConsumes() throws Exception {
        mvc.perform(multipart("/api/vision/extract").file(new MockMultipartFile("image",new byte[]{1})).with(user(account())).with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/vision/extract").file(image()).file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/vision/extract").file(image()).param("ip","192.0.2.99").with(user(account())).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/vision/extract").with(user(account())).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(redis,client);
    }
    @Test void disabledDoesNotConsumeAndQuotaRejectionNeverCallsModel() throws Exception {
        model.setEnabled(false);
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RECOGNITION_DISABLED"));
        verifyNoInteractions(redis,client);
        model.setEnabled(true);
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(-1L);
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("GLOBAL_QUOTA_EXHAUSTED"));
        verifyNoInteractions(client);
    }
    @Test void malformedDraftOrProviderTimeoutIsFixedErrorAndOnlyOneModelCallPerRequest() throws Exception {
        when(client.extract(any())).thenReturn("PRIVATE_PROVIDER_CONTENT");
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("INVALID_RECOGNITION_RESULT"))
                .andExpect(content().string(not(containsString("PRIVATE_PROVIDER_CONTENT"))));
        verify(client).extract(any());
        reset(client);
        when(client.extract(any())).thenThrow(new VisionModelException("MODEL_TIMEOUT","识别超时"));
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(account())).with(csrf()))
                .andExpect(status().isGatewayTimeout());
        verify(client).extract(any());
    }

    @Test @Transactional
    void partialDraftRequiresExplicitCompletionAndSaveThenRemainsOwnedByCurrentUser() throws Exception {
        var owner = seedAccount("stage59_owner");
        var other = seedAccount("stage59_other");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class);
        var extracted = mvc.perform(multipart("/api/vision/extract").file(image()).with(user(owner)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> values = json.readValue(extracted.getResponse().getContentAsString(),
                json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        // 草稿允许缺公司名；原创建接口依然拒绝不完整的最终表单。
        mvc.perform(post("/api/jobs").with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(values))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyName").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(before);
        values.put("companyName", "手动补齐的虚构公司");
        values.put("jobTitle", "人工修正后的Java岗位");
        values.put("responsibilities", "检查后保存\n编写测试");
        values.put("userId", other.getId());
        values.put("status", "OFFER");
        String body = json.writeValueAsString(values);
        mvc.perform(post("/api/jobs").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        var saved = mvc.perform(post("/api/jobs").with(user(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("TO_APPLY"))
                .andExpect(jsonPath("$.sourcePlatform").isEmpty()).andReturn();
        long id = json.readTree(saved.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/jobs/" + id).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("人工修正后的Java岗位"))
                .andExpect(jsonPath("$.responsibilities").value("检查后保存\n编写测试"));
        mvc.perform(get("/api/jobs/" + id).with(user(other))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT user_id FROM job_applications WHERE id=?", Long.class, id)).isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(before + 1);
        verify(client).extract(any()); // 修改/保存表单不重复识别、不重复扣额。
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test @Transactional
    void redisFailureStopsRecognitionButDoesNotBlockManualJobCreation() throws Exception {
        var owner = seedAccount("stage59_manual");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("fictional outage"));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class);
        mvc.perform(multipart("/api/vision/extract").file(image()).with(user(owner)).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(client);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(before);
        mvc.perform(post("/api/jobs").with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\":\"虚构手动公司\",\"jobTitle\":\"手动岗位\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(before + 1);
    }

    private AppUserDetails seedAccount(String name) {
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", name, UUID.randomUUID().toString());
        var account = new UserAccount();
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name));
        account.setUsername(name); account.setPasswordHash("fictional");
        return new AppUserDetails(account);
    }
}
