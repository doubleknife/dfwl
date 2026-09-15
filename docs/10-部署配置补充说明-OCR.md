# 部署配置补充说明：腾讯云 OCR

## 轮胎 OCR Provider

生产环境使用腾讯云通用文字识别高精度版：

- Action: `GeneralAccurateOCR`
- API Version: `2018-11-19`
- Endpoint: `ocr.tencentcloudapi.com`
- SDK: 腾讯云官方 Java SDK `com.tencentcloudapi:tencentcloud-sdk-java`

## 环境变量

| 名称 | 必填 | 用途 | 示例 |
| --- | --- | --- | --- |
| `FLEET_OCR_PROVIDER` | 生产必填 | OCR Provider 开关。生产使用 `TENCENT`；未配置时为 `DISABLED`，不会调用外部 OCR。 | `TENCENT` |
| `TENCENT_CLOUD_SECRET_ID` | `FLEET_OCR_PROVIDER=TENCENT` 时必填 | 腾讯云 SecretId，仅服务器环境变量配置。 | `AKID...` |
| `TENCENT_CLOUD_SECRET_KEY` | `FLEET_OCR_PROVIDER=TENCENT` 时必填 | 腾讯云 SecretKey，仅服务器环境变量配置。 | `******` |
| `TENCENT_OCR_REGION` | 可选 | 传给腾讯云 Java SDK 的 region。该 OCR 接口使用 endpoint `ocr.tencentcloudapi.com`，如腾讯云 SDK/账号要求 region 时再配置。 | `ap-guangzhou` |

## 安全要求

- 不要把 `TENCENT_CLOUD_SECRET_ID` / `TENCENT_CLOUD_SECRET_KEY` 写入源码、Git、前端、小程序或普通日志。
- 小程序只上传 `TIRE_OCR` 原图附件并调用后端 `/ocr/tire-number`，不得直接调用腾讯云。
- 后端保存腾讯云 `RequestId`、`TextDetections` 和原始响应 JSON；响应和落库内容不得包含密钥。

