package com.dfwl.fleet.tire.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fleet.ocr")
public class OcrProperties {

    private String provider = "DISABLED";
    private final Tencent tencent = new Tencent();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Tencent getTencent() {
        return tencent;
    }

    public static class Tencent {
        private String secretId = "";
        private String secretKey = "";
        private String region = "";

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }
}
