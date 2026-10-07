# 车队经营管理系统

本仓库按 `AGENTS.md` 和 `docs/01～07 V3.0` 唯一正式业务基线开发，包含 Java 后端、Vue3 管理端和微信小程序。其余历史文档仅供追溯，不能替代正式规则。

## 目录

- `docs/`: 需求、设计文档与 OpenAPI 契约
- `database/`: MySQL 基线建库脚本
- `backend/`: Java 后端
- `admin-web/`: Vue3 管理端
- `mini-program/`: 微信小程序

## 当前阶段

首次上线前 V3 统一模型实施中。V3-A～E 已完成基础任务、调度与固定任务导入、工资出勤、审批费用、经营月结；V3-F 正在收口轮胎业务和管理端多层导航。指定充电公司 Excel 适配属于后续 V3-G，不属于当前可用功能。真实人工 GUI 验收留到 V3-R，当前阶段的自动验证结果不能替代 GUI 验收。数据库使用单一初始化 baseline，当前可用接口以 `docs/openapi.yaml` 为准。

## Maven

使用 `D:\software\apache-maven-3.9.16\` 下的默认 `settings` 和 `repository`。项目内不要新增 `.mvn/maven.config` 覆盖 Maven 本地仓库。
