# 部署配置补充说明：腾讯云 OCR

## 轮胎 OCR Provider

生产环境使用腾讯云通用文字识别高精度版：

- Action: `GeneralAccurateOCR`
- API Version: `2018-11-19`
- Endpoint: `ocr.tencentcloudapi.com`
- SDK: 腾讯云官方 Java SDK `com.tencentcloudapi:tencentcloud-sdk-java`

## 配置文件

在后端 application.yml 或服务器外置 application.yml 中直接填写下列属性，不使用环境变量占位符。真实密钥仅保存在服务器私有配置文件中，不得提交 Git。仓库默认 provider 为 DISABLED，密钥与 region 为空字符串。

| 名称 | 必填 | 用途 | 示例 |
| --- | --- | --- | --- |
| `fleet.ocr.provider` | 生产必填 | OCR Provider 开关。生产使用 `TENCENT`；未配置时为 `DISABLED`，不会调用外部 OCR。 | `TENCENT` |
| `fleet.ocr.tencent.secret-id` | `fleet.ocr.provider=TENCENT` 时必填 | 腾讯云 SecretId，仅服务器私有配置文件配置。 | `AKID...` |
| `fleet.ocr.tencent.secret-key` | `fleet.ocr.provider=TENCENT` 时必填 | 腾讯云 SecretKey，仅服务器私有配置文件配置。 | `******` |
| `fleet.ocr.tencent.region` | 可选 | 传给腾讯云 Java SDK 的 region。该 OCR 接口使用 endpoint `ocr.tencentcloudapi.com`，如腾讯云 SDK/账号要求 region 时再配置。 | `ap-guangzhou` |

## 安全要求

- 不要把 `fleet.ocr.tencent.secret-id` / `fleet.ocr.tencent.secret-key` 写入源码、Git、前端、小程序或普通日志。
- 小程序只上传 `TIRE_OCR` 原图附件并调用后端 `/ocr/tire-number`，不得直接调用腾讯云。
- 后端保存腾讯云 `RequestId`、`TextDetections` 和原始响应 JSON；响应和落库内容不得包含密钥。

