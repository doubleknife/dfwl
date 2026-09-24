# mini-program

微信小程序原生工程，面向司机、自有司机、外协司机、财务与高管审批等移动端闭环。

## 页面

- `pages/login`：手机号密码登录。
- `pages/home`：按 `/me` 权限和司机类型生成移动端工作台。
- `pages/routes`：司机线路列表。
- `pages/route-detail`：线路详情、发车、卸货入口。
- `pages/weight`：毛重/皮重同页提交。
- `pages/salary`：自有司机工资单。
- `pages/tire-request`：换胎 OCR 确认与申请。
- `pages/expense-apply`：费用审批申请。
- `pages/approvals`：待审批、我的申请、审批历史。
- `pages/approval-detail`：审批详情、时间线、通过和打回。
- `pages/profile`：当前账号信息。

接口统一走 `utils/request.js`，默认 `baseUrl` 为 `http://localhost:8080/api/v1`。

这个地址只用于本机开发工具。体验版和正式版打包前，需要在 `app.js` 的
`globalData.baseUrl` 改为对应环境已获准的 HTTPS API 域名并重新打包；不得
把 `localhost` 版本上传为体验版或正式版。该地址是公开客户端配置，不能
放数据库密码、腾讯云 SecretId/SecretKey 或其他服务端凭据。开发工具的
`project.private.config.json` 为本机私有文件，已由 Git 忽略。
