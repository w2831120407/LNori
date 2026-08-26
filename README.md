# NoriOS Offline Archive

<div align="center">

![NoriOS Banner](src/icon.png)

**基于 Astro 岛屿架构重构的 Web 操作系统离线归档**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Astro](https://img.shields.io/badge/Powered%20by-Astro-brightgreen.svg)](https://astro.build)
[![Node.js](https://img.shields.io/badge/Node.js-%3E%3D18-green.svg)](https://nodejs.org)

[English](README_EN.md) | **简体中文**

</div>

---

## 📖 项目简介

这是 `https://os.inori.ai/` 的公开前端资源离线归档项目。资源按原站路径保存在 `src/`，并将云端世界运行时替换为本地内存实现，因此断网时仍可进入 NoriOS 桌面、查看 Nori Live2D、打开本地应用和使用本地素材。

本项目已完成从原始 H5 单页应用到 **Astro 岛屿架构**的全面重构，实现了：
- 🏝️ **岛屿架构**：每个应用独立封装，按需加载
- 🎯 **分散式布局**：模块化目录结构，互不耦合
- ⚡ **零 JavaScript 输出**：静态 HTML 优先，交互组件按需水合
- 🔧 **可维护性**：清晰的代码组织，易于扩展

---

## 🚀 快速开始

### 环境要求

- Node.js >= 18
- pnpm (推荐) 或 npm

### 安装与运行

```bash
# 安装依赖
pnpm install

# 开发模式（Astro Dev Server）
pnpm dev

# 构建生产版本
pnpm build

# 预览构建结果
pnpm preview

# 或使用原有后端服务器启动
pnpm start
```

访问 <http://127.0.0.1:4321> (开发模式) 或 <http://127.0.0.1:4173> (生产模式)。

### 端口配置

端口被占用时可以更换：

```bash
# Astro 开发服务器
PORT=4322 pnpm dev

# 后端服务器
set PORT=4174 && pnpm start  # Windows
PORT=4174 pnpm start         # macOS/Linux
```

---

## 📦 项目结构

```
nori-os-offline/
├── src/                      # 源代码目录（重构后）
│   ├── APPS/                 # 应用模块目录（岛屿架构核心）
│   │   ├── browser/          # 浏览器应用
│   │   ├── files/            # 文件管理器
│   │   ├── mail/             # 邮件客户端
│   │   ├── messenger/        # 通讯工具
│   │   ├── terminal/         # 终端模拟器
│   │   ├── preview/          # 预览组件
│   │   ├── idle/             # 闲逛应用
│   │   ├── login/            # 登录界面
│   │   ├── markdown/         # Markdown 渲染器
│   │   ├── dock/             # Dock 停靠栏
│   │   ├── cakeduel/         # 蛋糕对决游戏
│   │   ├── pictionary/       # 你画我猜游戏
│   │   └── index.astro       # 顶层应用组装入口
│   ├── ARGNori_web/          # ARGNori Live2D 模型资源
│   ├── Nori_web/             # Nori Live2D 模型资源
│   ├── pages/                # Astro 页面入口
│   │   └── index.astro       # 主页面入口
│   ├── assets/               # 共享资源
│   ├── audio/                # 音频资源
│   ├── fonts/                # 字体资源
│   ├── icons/                # 图标资源
│   └── vendor/               # 第三方库
├── public/                   # 静态公共资源
├── dist/                     # 构建输出目录
├── astro.config.mjs          # Astro 配置文件
├── package.json              # 项目依赖配置
├── LICENSE                   # GPL-3.0 许可证
├── CONTRIBUTING.md           # 贡献者协议（英文）
└── CONTRIBUTING_CN.md        # 贡献者协议（中文）
```

---

## 🏗️ 重构大业（ASTRO 迁移宣言）

> 本文档是本项目**后续重构的核心纲领**。任何接手本项目的开发者都必须严格遵循本节的指引。

### 一、为什么重构

当前 `src/` 是 Vite 构建出的巨型打包产物（单文件动辄 3~4MB，如 `NormalApp-*.js`），所有应用、游戏、界面逻辑全部耦合在一团压缩 JS 里，难以维护、难以按需加载、难以复用。我们要把它**打散**成一个个独立、可维护、可按需加载的 Astro 组件。

### 二、核心原则

1. **分散式布局（Decentralized）**：`src/` 中**每一个软件/模块目录都必须独立成目录**，各自拥有自己的 `icons/`、`src/`、`README.md` 与 `index.astro`，互不耦合、可独立开发与测试。

2. **岛屿架构（Islands）**：以 `index.astro` 作为每个岛的组装入口，只把「需要交互/脚本」的部分作为静态 HTML 中的可水合岛屿（island），尽量让页面在无 JS 时也能有骨架；各岛通过顶层 `index.astro` 拼接成完整桌面。

3. **保持原功能**：重构只改变**组织与加载方式**，**不允许丢失或篡改原有功能**。每个模块迁移前后必须做行为等价验证。

4. **H5 → Astro**：把 HTML/JS 混合的 H5 写法，改造成 Astro 的 `.astro` 组件语法（frontmatter + 模板 + 局部样式 + `<script>` 处理）。

### 三、目录规范

每个软件/模块目录结构示例：

```
src/APPS/<app>/
├── icons/          # 该应用的图标
├── src/            # 该应用的实现（Astro / 迁移的打包产物）
├── README.md       # 该应用的介绍
└── index.astro     # 该应用的岛屿入口（Astro 组件）
```

顶层 `index.astro` 只负责 `import` 并**拼接**各个目录的 `index.astro`，不写业务逻辑。

---

## 📋 已包含内容

- ✅ NoriOS 桌面界面和本地化 Vite 分块
- ✅ Nori / ARGNori Live2D 模型、动作、表情和纹理
- ✅ 桌面图标、游戏图片、音频、字体和 WebGL 素材
- ✅ 本地 SPA 回退和离线 CSP
- ✅ 无登录访客入口与本地内存世界状态
- ✅ Astro 岛屿架构重构完成
- ✅ 应用模块独立封装（browser, files, mail, messenger, terminal, preview, idle, login, markdown, dock）

---

## ⚠️ 离线边界

原站的聊天回复、云端存档、多人联机和需要服务端制品的内容无法从公开前端资源中恢复；离线版本会用空状态或本地占位响应继续显示界面。浏览器应用中原本指向外部网站的链接在断网时自然不可用。

这是对公开可访问前端资源的本地归档，**不包含**绕过登录、付费或其他访问控制的内容。

---

## 🤝 参与贡献

我们欢迎各种形式的贡献！请参阅：

- [贡献者协议（中文）](CONTRIBUTING_CN.md)
- [Contributor Agreement (English)](CONTRIBUTING.md)
- [GPL-3.0 许可证](LICENSE)

### 贡献流程

1. Fork 本项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

---

## 📄 许可证

本项目采用 **GNU General Public License v3.0** 许可证。详见 [LICENSE](LICENSE) 文件。

```
Copyright (C) 2024 NoriOS Community

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```

---

## 🔗 相关链接

- [Astro 官方文档](https://docs.astro.build)
- [岛屿架构介绍](https://jasonformat.com/islands-architecture/)
- [Live2D Cubism](https://www.live2d.com/en/)
- [原项目地址](https://os.inori.ai/)

---

<div align="center">

**Made with ❤️ by NoriOS Community**

[返回顶部](#norios-offline-archive)

</div>
