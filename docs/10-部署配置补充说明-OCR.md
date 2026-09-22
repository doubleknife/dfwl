# 腾讯云 OCR 部署配置补充说明

系统默认禁用真实 OCR。启用腾讯云 OCR 时，仅通过部署环境或 IDEA Run Configuration 注入以下配置：

```text
FLEET_OCR_PROVIDER=TENCENT
FLEET_OCR_TENCENT_SECRET_ID=<SecretId>
FLEET_OCR_TENCENT_SECRET_KEY=<SecretKey>
FLEET_OCR_TENCENT_REGION=ap-guangzhou
```

这些名称来自 `OcrProperties` 的 `fleet.ocr` 配置绑定。真实凭据不得写入 `application.yml`、Java、测试源码、文档或 Git。

真实 Provider 集成测试还需要：

```text
TEST_TENCENT_OCR_IMAGE_PATH=<本机真实轮胎照片绝对路径>
```

只有凭据、Region 和图片路径全部存在时，`TencentCloudOcrProviderIntegrationTest` 才会调用腾讯云；普通测试默认跳过。该测试调用腾讯云官方 Java SDK 的 `GeneralAccurateOCR`，不会使用 Fake Provider。
