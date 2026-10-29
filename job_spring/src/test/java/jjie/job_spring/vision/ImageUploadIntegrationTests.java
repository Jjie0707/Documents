package jjie.job_spring.vision;

import jjie.job_spring.vision.common.ImageUploadLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:image-upload;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ImageUploadIntegrationTests {
    private static final String PATH = "/api/vision/images/validate";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void validatesOnlyReturnsMetadataAndNeverCreatesJob(String format) throws Exception {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class);
        mvc.perform(multipart(PATH).file(image(format)).with(user("upload_demo")).with(csrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.format").value(format.equals("png") ? "PNG" : "JPEG"))
                .andExpect(jsonPath("$.width").value(20)).andExpect(jsonPath("$.height").value(30))
                .andExpect(jsonPath("$.sizeBytes").isNumber()).andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(content().string(not(containsString("PRIVATE_FILENAME"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Long.class)).isEqualTo(count);
    }

    @Test
    void missingPartWrongNameAndMultipleFilesAreRejected() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder[] {
                multipart(PATH),
                multipart(PATH).file(new MockMultipartFile("wrong", "a.png", "image/png", new byte[] {1})),
                multipart(PATH).file(image("png")).file(image("png")),
                multipart(PATH).file(image("png")).file(new MockMultipartFile("other", new byte[] {1}))}) {
            mvc.perform(request.with(user("upload_demo")).with(csrf())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IMAGE_PARTS"));
        }
    }

    @Test
    void rejectsInvalidContentWithoutLeakingFilename() throws Exception {
        mvc.perform(multipart(PATH).file(new MockMultipartFile("image", "PRIVATE_FILENAME.png", "image/png", "PRIVATE_CONTENT".getBytes()))
                        .with(user("upload_demo")).with(csrf()))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_FORMAT"))
                .andExpect(content().string(not(containsString("PRIVATE"))));
    }

    @Test
    void handlesEmptyOversizedDamagedAndPixelExcessFiles() throws Exception {
        mvc.perform(multipart(PATH).file(new MockMultipartFile("image", new byte[0])).with(user("upload_demo")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_IMAGE"));
        mvc.perform(multipart(PATH).file(new MockMultipartFile("image", new byte[ImageUploadLimits.MAX_BYTES + 1]))
                        .with(user("upload_demo")).with(csrf()))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        mvc.perform(multipart(PATH).file(new MockMultipartFile("image", ImageValidationServiceTests.pngWithDimensions(4001, 3000)))
                        .with(user("upload_demo")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_DIMENSIONS_EXCEEDED"));
    }

    @Test
    void loginAndCsrfRemainRequired() throws Exception {
        mvc.perform(multipart(PATH).file(image("png")).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(PATH).file(image("png")).with(user("upload_demo"))).andExpect(status().isForbidden());
        mvc.perform(multipart(PATH).file(image("png")).with(user("upload_demo")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void jsonIsNotAcceptedAsAnImageUpload() throws Exception {
        mvc.perform(post(PATH).with(user("upload_demo")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private MockMultipartFile image(String format) throws Exception {
        return new MockMultipartFile("image", "PRIVATE_FILENAME.bin", "application/octet-stream",
                ImageValidationServiceTests.image(format, 20, 30));
    }
}
