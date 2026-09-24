package com.dfwl.fleet.tire;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.tire.ocr.OcrProperties;
import com.dfwl.fleet.tire.ocr.TencentCloudOcrProvider;
import org.junit.jupiter.api.Test;

class TencentCloudOcrConfigurationTests {

    @Test
    void selectedTencentProviderRejectsMissingSecretsAtStartup() {
        OcrProperties properties = new OcrProperties();
        properties.setProvider("TENCENT");

        assertThatThrownBy(() -> new TencentCloudOcrProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FLEET_OCR_TENCENT_SECRET_ID")
                .hasMessageContaining("FLEET_OCR_TENCENT_SECRET_KEY");
    }
}
