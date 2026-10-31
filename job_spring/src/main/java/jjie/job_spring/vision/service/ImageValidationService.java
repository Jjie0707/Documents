package jjie.job_spring.vision.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import jjie.job_spring.vision.common.ImageUploadLimits;
import jjie.job_spring.vision.common.ImageValidationResponse;
import jjie.job_spring.vision.common.ValidatedImage;
import jjie.job_spring.vision.exception.ImageValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageValidationService {
    private static final byte[] PNG_END = {0, 0, 0, 0, 73, 69, 78, 68, (byte) 174, 66, 96, (byte) 130};

    public ImageValidationResponse validate(MultipartFile file) {
        return validateAndRead(file).metadata();
    }

    public ValidatedImage validateAndRead(MultipartFile file) {
        if (file.getSize() > ImageUploadLimits.MAX_BYTES) throw tooLarge();
        try (var source = file.getInputStream()) {
            // 即使声明大小不准确，也只读取上限 + 1 字节，不使用无界 readAllBytes。
            byte[] bytes = source.readNBytes(ImageUploadLimits.MAX_BYTES + 1);
            if (bytes.length > ImageUploadLimits.MAX_BYTES) throw tooLarge();
            if (bytes.length == 0) {
                throw new ImageValidationException(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE", "请选择非空的PNG或JPEG图片");
            }
            return new ValidatedImage(bytes, inspect(bytes));
        } catch (IOException exception) {
            throw invalidImage();
        } catch (RuntimeException exception) {
            if (exception instanceof ImageValidationException known) throw known;
            // 不让图片解码器的畸形输入异常或细节直接进入响应。
            throw invalidImage();
        }
    }

    private ImageValidationResponse inspect(byte[] bytes) throws IOException {
        // 显式使用内存缓存，不调用 ImageIO 默认可能产生磁盘缓存的便捷方法。
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw unsupportedImage();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                boolean png = "PNG".equalsIgnoreCase(format);
                boolean jpeg = "JPEG".equalsIgnoreCase(format);
                if (!png && !jpeg) throw unsupportedImage();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // 在分配完整像素缓冲区之前检查尺寸，乘法提升为 long，防止溢出。
                if (width < 1 || height < 1 || width > ImageUploadLimits.MAX_DIMENSION
                        || height > ImageUploadLimits.MAX_DIMENSION
                        || (long) width * height > ImageUploadLimits.MAX_PIXELS) {
                    throw new ImageValidationException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED",
                            "图片单边不能超过10000像素，总像素不能超过1200万，请选择较小的截图");
                }
                if (!hasCompleteEnding(bytes, png)) throw invalidImage();
                AtomicBoolean warned = new AtomicBoolean();
                reader.addIIOReadWarningListener((ignored, warning) -> warned.set(true));
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw invalidImage();
                try {
                    if (warned.get() || decoded.getWidth() != width || decoded.getHeight() != height) throw invalidImage();
                } finally {
                    decoded.flush();
                }
                return new ImageValidationResponse(png ? "PNG" : "JPEG", png ? "image/png" : "image/jpeg",
                        bytes.length, width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean hasCompleteEnding(byte[] bytes, boolean png) {
        if (png) return bytes.length >= PNG_END.length
                && Arrays.equals(bytes, bytes.length - PNG_END.length, bytes.length, PNG_END, 0, PNG_END.length);
        return bytes.length >= 2 && bytes[bytes.length - 2] == (byte) 255 && bytes[bytes.length - 1] == (byte) 217;
    }

    private ImageValidationException tooLarge() {
        return new ImageValidationException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "图片不能超过5 MiB");
    }

    private ImageValidationException unsupportedImage() {
        return new ImageValidationException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT", "仅支持实际内容为PNG或JPEG的图片");
    }

    private ImageValidationException invalidImage() {
        return new ImageValidationException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "图片已损坏或无法完整读取，请换一张截图");
    }
}
