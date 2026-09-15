package com.dfwl.fleet.tire;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.tire.ocr.OcrProperties;
import com.dfwl.fleet.tire.ocr.OcrProviderResult;
import com.dfwl.fleet.tire.ocr.TencentCloudOcrProvider;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual Tencent Cloud OCR smoke test. Configure credentials and TEST_TENCENT_OCR_IMAGE_BASE64 before enabling.")
class TencentCloudOcrProviderIntegrationTest {

    @Test
    void callsTencentGeneralAccurateOcr() {
        OcrProperties properties = new OcrProperties();
        properties.setProvider("TENCENT");
        properties.getTencent().setSecretId(System.getenv("TENCENT_CLOUD_SECRET_ID"));
        properties.getTencent().setSecretKey(System.getenv("TENCENT_CLOUD_SECRET_KEY"));
        properties.getTencent().setRegion(System.getenv("TENCENT_OCR_REGION"));
        TencentCloudOcrProvider provider = new TencentCloudOcrProvider(properties);

        String imageBase64 = System.getenv("TEST_TENCENT_OCR_IMAGE_BASE64");
        assertThat(imageBase64).isNotBlank();
        Base64.getDecoder().decode(imageBase64);

        OcrProviderResult result = provider.recognize(imageBase64, "image/jpeg");

        assertThat(result.provider()).isEqualTo("TENCENT_GENERAL_ACCURATE");
        assertThat(result.requestId()).isNotBlank();
        assertThat(result.rawResultJson()).contains("TextDetections");
    }
}
