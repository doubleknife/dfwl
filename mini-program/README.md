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
