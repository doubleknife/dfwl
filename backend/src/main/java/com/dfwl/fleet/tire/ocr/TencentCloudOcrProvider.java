package com.dfwl.fleet.tire.ocr;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.TextDetection;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "fleet.ocr", name = "provider", havingValue = "TENCENT")
public class TencentCloudOcrProvider implements OcrProvider {

    private static final String PROVIDER_NAME = "TENCENT_GENERAL_ACCURATE";

    private final OcrProperties properties;

    public TencentCloudOcrProvider(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public OcrProviderResult recognize(String imageBase64, String contentType) {
        if (!StringUtils.hasText(properties.getTencent().getSecretId())
                || !StringUtils.hasText(properties.getTencent().getSecretKey())) {
            throw new OcrProviderException("OCR_CONFIG_MISSING", "Tencent OCR secret is not configured");
        }
        try {
            Credential credential = new Credential(
                    properties.getTencent().getSecretId(),
                    properties.getTencent().getSecretKey());
            OcrClient client = new OcrClient(credential, properties.getTencent().getRegion());
            GeneralAccurateOCRRequest request = new GeneralAccurateOCRRequest();
            request.setImageBase64(imageBase64);
            GeneralAccurateOCRResponse response = client.GeneralAccurateOCR(request);
            TextDetection[] textDetections = response.getTextDetections();
            List<OcrProviderResult.DetectedText> detections = textDetections == null ? List.of() : Arrays.stream(textDetections)
                    .map(detection -> new OcrProviderResult.DetectedText(
                            detection.getDetectedText(),
                            detection.getConfidence() == null ? null : detection.getConfidence().doubleValue()))
                    .toList();
            return new OcrProviderResult(
                    PROVIDER_NAME,
                    response.getRequestId(),
                    GeneralAccurateOCRResponse.toJsonString(response),
                    detections);
        } catch (TencentCloudSDKException ex) {
            throw new OcrProviderException(ex.getErrorCode() == null ? "TENCENT_OCR_ERROR" : ex.getErrorCode(),
                    ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new OcrProviderException("TENCENT_OCR_ERROR", ex.getMessage(), ex);
        }
    }
}
