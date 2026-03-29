# frontend (v0.1.0)

Nuxt 3 + TypeScript + PrimeVue + Pinia + Tailwind 前端子工程。

## 开发命令

```bash
npm install
npm run dev
```

## 环境变量

- 根目录的 `.env.example` 是开发默认值清单。
- `.env` 与 `.env.example` 使用相同的变量名，但填当前环境可直接使用的实际值，并保持未跟踪。
- `.env` 中的 `JWT_SECRET`、`CONNECTOR_SECRET_KEY`、`INTERNAL_API_TOKEN` 必须与 `.env.example` 不同，避免把开发示例 secret 带入实际环境。
- 受版本控制的敏感默认值现在是占位符；无论本地还是部署环境，都需要先准备未跟踪的 `.env` 才能成功启动。
- `AUTH_COOKIE_SECURE` 默认应保持为 `true`；只有在本地纯 HTTP 调试时才应在未跟踪的 `.env` 中临时覆盖为 `false`。

## 路由

- `/login`
- `/register`
- `/app`

## Mock 模式演示账号

仅当 `NUXT_PUBLIC_API_MODE=mock` 时可用；HTTP/API 模式不再提供仓库内默认可登录账号。

- principal: `demo`
- password: `demo123456`
