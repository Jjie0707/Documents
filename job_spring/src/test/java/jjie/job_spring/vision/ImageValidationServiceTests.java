package jjie.job_spring.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import jjie.job_spring.vision.common.ImageUploadLimits;
import jjie.job_spring.vision.exception.ImageValidationException;
import jjie.job_spring.vision.service.ImageValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ImageValidationServiceTests {
    private final ImageValidationService service = new ImageValidationService();

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void acceptsActualFormatRegardlessOfFilenameOrMime(String format) throws Exception {
        byte[] bytes = image(format, 31, 47);
        var response = service.validate(new MockMultipartFile("image", "PRIVATE_NAME.pdf", "application/pdf", bytes));
        assertThat(response.format()).isEqualTo(format.equals("png") ? "PNG" : "JPEG");
        assertThat(response.mediaType()).isEqualTo("image/" + format);
        assertThat(response.sizeBytes()).isEqualTo(bytes.length);
        assertThat(response.width()).isEqualTo(31);
        assertThat(response.height()).isEqualTo(47);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gif", "bmp"})
    void rejectsOtherActualFormatsEvenIfNamedPng(String format) throws Exception {
        assertCode(image(format, 3, 4), "UNSUPPORTED_IMAGE_FORMAT");
    }

    @Test
    void rejectsFakeImageAndEmptyFile() {
        assertCode("PRIVATE_NOT_IMAGE".getBytes(java.nio.charset.StandardCharsets.UTF_8), "UNSUPPORTED_IMAGE_FORMAT");
        assertCode(new byte[0], "EMPTY_IMAGE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void rejectsTruncatedImagesAndMissingEndMarkers(String format) throws Exception {
        byte[] bytes = image(format, 30, 40);
        assertCode(Arrays.copyOf(bytes, bytes.length / 2), "INVALID_IMAGE");
        assertCode(Arrays.copyOf(bytes, bytes.length - 1), "INVALID_IMAGE");
    }

    @Test
    void refusesHugeHeaderBeforeTryingToDecodeItsTinyPayload() throws Exception {
        for (int[] dimensions : new int[][] {{10001, 1}, {1, 10001}, {4001, 3000}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
            assertCode(pngWithDimensions(dimensions[0], dimensions[1]), "IMAGE_DIMENSIONS_EXCEEDED");
        }
    }

    @ParameterizedTest
    @CsvSource({"10000,1", "1,10000", "4000,3000"})
    void acceptsDimensionAndPixelBoundaries(int width, int height) throws Exception {
        var result = service.validate(file(image("png", width, height)));
        assertThat(result.width()).isEqualTo(width);
        assertThat(result.height()).isEqualTo(height);
    }

    @Test
    void corruptPixelsWithValidDimensionsAndEndMarkerAreStillRejected() throws Exception {
        byte[] bytes = image("png", 32, 32);
        // IDAT 压缩数据起点由 PNG chunk 结构找到；不破坏头部和结尾，验证必须真正解码。
        for (int pos = 8; pos < bytes.length - 12;) {
            int length = ByteBuffer.wrap(bytes, pos, 4).getInt();
            if (bytes[pos + 4] == 'I' && bytes[pos + 5] == 'D') {
                bytes[pos + 8] = 0;
                bytes[pos + 9] = 0;
                break;
            }
            pos += length + 12;
        }
        assertCode(bytes, "INVALID_IMAGE");
    }

    @Test
    void acceptsExactlyFiveMibButRejectsOneByteMore() throws Exception {
        byte[] original = image("png", 2, 2);
        int padding = ImageUploadLimits.MAX_BYTES - original.length - 12;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(original, 0, original.length - 12);
        output.write(ByteBuffer.allocate(4).putInt(padding).array());
        byte[] chunk = new byte[padding + 4];
        System.arraycopy(new byte[] {'n', 'p', 'A', 'D'}, 0, chunk, 0, 4);
        output.write(chunk);
        CRC32 crc = new CRC32(); crc.update(chunk);
        output.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        output.write(original, original.length - 12, 12);
        byte[] exact = output.toByteArray();
        assertThat(exact.length).isEqualTo(ImageUploadLimits.MAX_BYTES);
        assertThat(service.validate(file(exact)).sizeBytes()).isEqualTo(ImageUploadLimits.MAX_BYTES);
        assertCode(Arrays.copyOf(exact, exact.length + 1), "IMAGE_TOO_LARGE");
    }

    @Test
    void declaredOversizeIsRejectedBeforeOpeningStream() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(ImageUploadLimits.MAX_BYTES + 1L);
        assertThatThrownBy(() -> service.validate(file)).isInstanceOf(ImageValidationException.class)
                .extracting("code").isEqualTo("IMAGE_TOO_LARGE");
        verify(file, never()).getInputStream();
    }

    @Test
    void boundedReadChecksActualBytesEvenWhenDeclaredSizeIsWrong() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[ImageUploadLimits.MAX_BYTES + 2]));
        assertThatThrownBy(() -> service.validate(file)).isInstanceOf(ImageValidationException.class)
                .extracting("code").isEqualTo("IMAGE_TOO_LARGE");
    }

    @Test
    void readFailureDoesNotExposeFilenameOrInternalException() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenThrow(new IOException("PRIVATE_IO_DETAILS"));
        assertThatThrownBy(() -> service.validate(file)).isInstanceOf(ImageValidationException.class)
                .hasMessageNotContaining("PRIVATE_IO_DETAILS");
    }

    static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            return output.toByteArray();
        } finally { image.flush(); }
    }

    static byte[] pngWithDimensions(int width, int height) throws IOException {
        byte[] bytes = image("png", 1, 1);
        ByteBuffer.wrap(bytes, 16, 8).putInt(width).putInt(height);
        CRC32 crc = new CRC32(); crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
        return bytes;
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("image", "PRIVATE_FILENAME.png", "image/png", bytes);
    }

    private void assertCode(byte[] bytes, String code) {
        assertThatThrownBy(() -> service.validate(file(bytes))).isInstanceOf(ImageValidationException.class)
                .hasMessageNotContaining("PRIVATE").extracting("code").isEqualTo(code);
    }
}
