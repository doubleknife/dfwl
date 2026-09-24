# 腾讯云 OCR 部署配置补充说明

系统默认禁用真实 OCR。启用腾讯云 OCR 时，仅通过部署环境、IDEA Run Configuration 或不参与 Maven resources 打包的私有外部配置注入以下配置：

```text
FLEET_OCR_PROVIDER=TENCENT
FLEET_OCR_TENCENT_SECRET_ID=<SecretId>
FLEET_OCR_TENCENT_SECRET_KEY=<SecretKey>
FLEET_OCR_TENCENT_REGION=ap-guangzhou
```

这些名称来自 `OcrProperties` 的 `fleet.ocr` 配置绑定。真实凭据不得写入 `application.yml`、Java、测试源码、文档或 Git。

`prod` profile 默认选择腾讯云 OCR；缺少 SecretId/SecretKey 时服务在启动阶段明确失败，不会等待到第一次识别请求。`FLEET_OCR_TENCENT_REGION` 默认 `ap-guangzhou`。完整部署变量与外部文件用法参见 `11-R8-1-部署配置与敏感信息.md`。

真实 Provider 集成测试还需要：

```text
TEST_TENCENT_OCR_IMAGE_PATH=<本机真实轮胎照片绝对路径>
```

只有凭据、Region 和图片路径全部存在时，`TencentCloudOcrProviderIntegrationTest` 才会调用腾讯云；普通测试默认跳过。该测试调用腾讯云官方 Java SDK 的 `GeneralAccurateOCR`，不会使用 Fake Provider。
