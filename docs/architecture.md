# 架构设计

## 整体架构

本项目采用 **Astro 岛屿架构 (Islands Architecture)** 重构原有 H5 应用，实现了静态站点生成 (SSG) 与客户端交互的完美结合。

### 核心设计理念

1. **零 JavaScript 默认**: 页面默认不发送任何 JavaScript，仅在需要交互的组件处按需加载
2. **组件隔离**: 每个 APP 作为独立岛屿，互不影响
3. **渐进增强**: 基础功能无需 JS，高级交互按需加载

## 目录结构

```
src/
├── pages/          # Astro 页面路由
│   └── index.astro # 主入口
├── APPS/           # 应用模块集合
│   ├── browser/    # 浏览器应用
│   ├── files/      # 文件管理器
│   ├── mail/       # 邮件客户端
│   └── ...         # 其他应用
├── components/     # 共享组件
├── layouts/        # 布局模板
├── styles/         # 全局样式
└── public/         # 静态资源
```

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 框架 | Astro 4.x | 静态站点生成器 |
| UI | HTML/CSS/JS | 原生 Web 技术 |
| 构建 | Vite | 快速构建工具 |
| 部署 | 任意静态托管 | Netlify/Vercel/GitHub Pages |

## 数据流

```mermaid
graph LR
    A[用户访问] --> B[Astro SSG]
    B --> C[静态 HTML]
    C --> D[按需 hydration]
    D --> E[交互岛屿激活]
```

## 安全架构

- ✅ 无服务端代码，消除 SSR 攻击面
- ✅ CSP 策略内置
- ✅ 依赖最小化原则
- ✅ 自动 XSS 防护 (Astro 默认转义)

---
*版本: v2.0.0 | 最后更新: 2024*
