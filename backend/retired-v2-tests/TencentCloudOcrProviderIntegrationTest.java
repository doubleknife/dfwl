package com.dfwl.fleet.tire;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.tire.ocr.OcrProperties;
import com.dfwl.fleet.tire.ocr.OcrProviderResult;
import com.dfwl.fleet.tire.ocr.TencentCloudOcrProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

class TencentCloudOcrProviderIntegrationTest {

    @Test
    void callsTencentGeneralAccurateOcr() throws Exception {
        String secretId = System.getenv("FLEET_OCR_TENCENT_SECRET_ID");
        String secretKey = System.getenv("FLEET_OCR_TENCENT_SECRET_KEY");
        String region = System.getenv("FLEET_OCR_TENCENT_REGION");
        String imagePath = System.getenv("TEST_TENCENT_OCR_IMAGE_PATH");
        Assumptions.assumeTrue(StringUtils.hasText(secretId)
                        && StringUtils.hasText(secretKey)
                        && StringUtils.hasText(region)
                        && StringUtils.hasText(imagePath),
                "Real Tencent OCR credentials, region and image path are required");

        OcrProperties properties = new OcrProperties();
        properties.setProvider("TENCENT");
        properties.getTencent().setSecretId(secretId);
        properties.getTencent().setSecretKey(secretKey);
        properties.getTencent().setRegion(region);
        TencentCloudOcrProvider provider = new TencentCloudOcrProvider(properties);

        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
        assertThat(imageBytes).isNotEmpty();

        long startedAt = System.nanoTime();
        OcrProviderResult result = provider.recognize(Base64.getEncoder().encodeToString(imageBytes), "image/jpeg");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(result.provider()).isEqualTo("TENCENT_GENERAL_ACCURATE");
        assertThat(result.requestId()).isNotBlank();
        assertThat(result.rawResultJson()).contains("TextDetections");
        assertThat(result.detections()).isNotEmpty();
        assertThat(result.detections()).anyMatch(detection -> StringUtils.hasText(detection.text()));

        long nonBlankDetections = result.detections().stream()
                .filter(detection -> StringUtils.hasText(detection.text()))
                .count();
        System.out.printf("Tencent OCR real call: region=%s, requestIdPresent=true, detections=%d, nonBlank=%d, elapsedMs=%d%n",
                region, result.detections().size(), nonBlankDetections, elapsedMillis);
        result.detections().stream()
                .filter(detection -> StringUtils.hasText(detection.text()))
                .forEach(detection -> System.out.printf("OCR text confidence=%s: %s%n",
                        detection.confidence(), detection.text().replaceAll("[\\r\\n]+", " ")));
    }
}
