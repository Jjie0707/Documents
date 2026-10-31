package jjie.job_spring.vision;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import jjie.job_spring.config.VisionModelProperties;
import jjie.job_spring.vision.common.*;
import jjie.job_spring.vision.exception.VisionModelException;
import jjie.job_spring.vision.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class VisionExtractionServiceTests {
    private final VisionModelProperties config = new VisionModelProperties();
    private final VisionAdmissionService admission = mock(VisionAdmissionService.class);
    private final GlmVisionClient client = mock(GlmVisionClient.class);
    private final RecognitionResultService results = mock(RecognitionResultService.class);
    private final VisionExtractionService service = new VisionExtractionService(config, admission, client, results);
    private final MockMultipartFile image = new MockMultipartFile("image", new byte[]{1});
    private void enabled() { config.setEnabled(true); config.setApiKey(UUID.randomUUID().toString()); }
    @Test void disabledAndMissingKeyNeverTouchQuotaOrClient() {
        assertThatThrownBy(() -> service.extract(1L, "192.0.2.1", image)).isInstanceOf(VisionModelException.class);
        config.setEnabled(true);
        assertThatThrownBy(() -> service.extract(1L, "192.0.2.1", image)).isInstanceOf(VisionModelException.class);
        verifyNoInteractions(admission, client, results);
    }
    @Test void paidOrMissingModelConfigurationNeverConsumesQuota() {
        enabled(); config.setName("glm-4.6v");
        assertThatThrownBy(() -> service.extract(1L, "192.0.2.1", image)).isInstanceOf(VisionModelException.class);
        config.setName(null);
        assertThatThrownBy(() -> service.extract(1L, "192.0.2.1", image)).isInstanceOf(VisionModelException.class);
        verifyNoInteractions(admission, client, results);
    }
    @Test void admissionUsesSameValidatedBytesThenRunsStrictResultValidation() {
        enabled();
        var validated = new ValidatedImage(new byte[]{1}, new ImageValidationResponse("PNG", "image/png", 1,1,1));
        when(admission.executeWithImage(eq(image), eq("192.0.2.1"), any())).thenAnswer(call ->
                ((Function<ValidatedImage, JobRecognitionDraft>) call.getArgument(2)).apply(validated));
        when(client.extract(validated)).thenReturn("fictional JSON");
        service.extract(1L, "192.0.2.1", image);
        var order = inOrder(client, results);
        order.verify(client).extract(validated); order.verify(results).parseAndValidate("fictional JSON");
    }
    @Test void duplicateAccountRequestIsBlockedAndLockReleasedAfterFailure() throws Exception {
        enabled();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(admission.executeWithImage(any(), anyString(), any())).thenAnswer(call -> {
            entered.countDown(); release.await(3, TimeUnit.SECONDS); throw new VisionModelException("MODEL_TIMEOUT", "fictional timeout");
        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> service.extract(1L, "192.0.2.1", image));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.extract(1L, "192.0.2.1", image)).isInstanceOfSatisfying(VisionModelException.class,
                    error -> assertThat(error.getCode()).isEqualTo("RECOGNITION_IN_PROGRESS"));
            verify(admission).executeWithImage(any(), anyString(), any());
            release.countDown();
            assertThatThrownBy(() -> first.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            reset(admission);
            service.extract(1L, "192.0.2.1", image);
            verify(admission).executeWithImage(any(), anyString(), any());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
