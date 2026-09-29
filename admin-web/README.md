# admin-web

Vue3 管理端，正式业务规则以仓库根目录 `AGENTS.md` 与 `docs/01～07 V3.0` 为准。V3 线路模板与运输任务是不同对象。

## 命令

```bash
npm install
npm run dev
npm run build
```

接口地址按 Vite 模式配置：

- `.env`：`VITE_API_BASE_URL=/api/v1`，生产构建使用同源接口地址。
- `.env.development`：`VITE_API_BASE_URL=/api/v1`，`npm run dev` 通过 Vite 的 `/api/v1` 代理访问本机后端，避免浏览器跨源请求。

Vite 在 `npm run build` 时将配置值写入构建产物，部署时不需要额外复制 `.env` 文件。修改配置后需要重新打包；开发时修改后需要重启开发服务。生产部署需将 `/api/v1` 请求转发到后端。`VITE_` 配置属于浏览器可见内容，不要填写密钥。
