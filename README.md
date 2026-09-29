# 车队经营管理系统

本仓库按 `AGENTS.md` 和 `docs/01～07 V3.0` 唯一正式业务基线开发，包含 Java 后端、Vue3 管理端和微信小程序。其余历史文档仅供追溯，不能替代正式规则。

## 目录

- `docs/`: 需求、设计文档与 OpenAPI 契约
- `database/`: MySQL 基线建库脚本
- `backend/`: Java 后端
- `admin-web/`: Vue3 管理端
- `mini-program/`: 微信小程序

## 当前阶段

首次上线前 V3 统一模型实施中。V3-A 当前启用线路模板、运输任务、参与司机工资、基础资料、认证和站内通知读取入口；审批、费用、轮胎、经营报表、月结及对应页面暂时退役，待 V3-D/E/F 按最终模型重建。数据库使用单一初始化 baseline，不保留旧开发版本迁移兼容链。当前可用接口以 `docs/openapi.yaml` 为准。

## Maven

使用 `D:\software\apache-maven-3.9.16\` 下的默认 `settings` 和 `repository`。项目内不要新增 `.mvn/maven.config` 覆盖 Maven 本地仓库。
