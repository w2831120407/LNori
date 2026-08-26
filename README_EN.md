# NoriOS Offline Archive

<div align="center">

![NoriOS Banner](src/icon.png)

**Web OS Offline Archive Refactored with Astro Islands Architecture**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Astro](https://img.shields.io/badge/Powered%20by-Astro-brightgreen.svg)](https://astro.build)
[![Node.js](https://img.shields.io/badge/Node.js-%3E%3D18-green.svg)](https://nodejs.org)

**English** | [简体中文](README.md)

</div>

---

## 📖 Introduction

This is an offline archive of the public frontend resources from `https://os.inori.ai/`. Resources are preserved in `src/` according to the original site paths, and the cloud world runtime is replaced with a local memory implementation, allowing access to the NoriOS desktop, viewing Nori Live2D, opening local applications, and using local materials even when offline.

This project has been fully refactored from the original H5 single-page application to **Astro Islands Architecture**, achieving:
- 🏝️ **Islands Architecture**: Each application independently encapsulated, loaded on demand
- 🎯 **Decentralized Layout**: Modular directory structure, decoupled components
- ⚡ **Zero JavaScript Output**: Static HTML first, interactive components hydrated on demand
- 🔧 **Maintainability**: Clear code organization, easy to extend

---

## 🚀 Quick Start

### Requirements

- Node.js >= 18
- pnpm (recommended) or npm

### Installation & Running

```bash
# Install dependencies
pnpm install

# Development mode (Astro Dev Server)
pnpm dev

# Build production version
pnpm build

# Preview build result
pnpm preview

# Or start with original backend server
pnpm start
```

Visit <http://127.0.0.1:4321> (dev mode) or <http://127.0.0.1:4173> (production mode).

### Port Configuration

Change port if occupied:

```bash
# Astro dev server
PORT=4322 pnpm dev

# Backend server
set PORT=4174 && pnpm start  # Windows
PORT=4174 pnpm start         # macOS/Linux
```

---

## 📦 Project Structure

```
nori-os-offline/
├── src/                      # Source directory (refactored)
│   ├── APPS/                 # Application modules (Islands Architecture core)
│   │   ├── browser/          # Browser application
│   │   ├── files/            # File manager
│   │   ├── mail/             # Mail client
│   │   ├── messenger/        # Messenger tool
│   │   ├── terminal/         # Terminal emulator
│   │   ├── preview/          # Preview component
│   │   ├── idle/             # Idle application
│   │   ├── login/            # Login interface
│   │   ├── markdown/         # Markdown renderer
│   │   ├── dock/             # Dock taskbar
│   │   ├── cakeduel/         # Cake Duel game
│   │   ├── pictionary/       # Pictionary game
│   │   └── index.astro       # Top-level app assembly entry
│   ├── ARGNori_web/          # ARGNori Live2D model assets
│   ├── Nori_web/             # Nori Live2D model assets
│   ├── pages/                # Astro page entries
│   │   └── index.astro       # Main page entry
│   ├── assets/               # Shared assets
│   ├── audio/                # Audio resources
│   ├── fonts/                # Font resources
│   ├── icons/                # Icon resources
│   └── vendor/               # Third-party libraries
├── public/                   # Public static resources
├── dist/                     # Build output directory
├── astro.config.mjs          # Astro configuration
├── package.json              # Project dependencies
├── LICENSE                   # GPL-3.0 License
├── CONTRIBUTING.md           # Contributor Agreement (English)
└── CONTRIBUTING_CN.md        # Contributor Agreement (Chinese)
```

---

## 🏗️ Refactoring Manifesto (ASTRO Migration Declaration)

> This document is the **core guideline for subsequent refactoring**. Any developer taking over this project must strictly follow the guidelines in this section.

### I. Why Refactor

The current `src/` is a massive bundled output from Vite (single files often 3~4MB, like `NormalApp-*.js`), where all applications, games, and interface logic are coupled together in compressed JS, making it difficult to maintain, load on demand, or reuse. We aim to **break it down** into independent, maintainable, on-demand loadable Astro components.

### II. Core Principles

1. **Decentralized Layout**: **Every software/module directory in `src/` must be independent**, each with its own `icons/`, `src/`, `README.md`, and `index.astro`, decoupled and independently developable/testable.

2. **Islands Architecture**: Use `index.astro` as the assembly entry for each island, treating only "interactive/scripted" parts as hydratable islands in static HTML, allowing page skeletons without JS; islands are assembled into a complete desktop via top-level `index.astro`.

3. **Preserve Original Functionality**: Refactoring only changes **organization and loading methods**, **no loss or alteration of original functionality is allowed**. Behavioral equivalence verification must be performed before and after each module migration.

4. **H5 → Astro**: Transform HTML/JS mixed H5 patterns into Astro `.astro` component syntax (frontmatter + template + scoped styles + `<script>` handling).

### III. Directory Convention

Example structure for each software/module directory:

```
src/APPS/<app>/
├── icons/          # App icons
├── src/            # App implementation (Astro / migrated bundles)
├── README.md       # App introduction
└── index.astro     # Island entry (Astro component)
```

Top-level `index.astro` only handles `import` and **assembly** of each directory's `index.astro`, without business logic.

---

## 📋 Included Content

- ✅ NoriOS desktop interface and localized Vite chunks
- ✅ Nori / ARGNori Live2D models, motions, expressions, and textures
- ✅ Desktop icons, game images, audio, fonts, and WebGL assets
- ✅ Local SPA fallback and offline CSP
- ✅ Guest entry without login and local memory world state
- ✅ Astro Islands Architecture refactoring completed
- ✅ Application modules independently encapsulated (browser, files, mail, messenger, terminal, preview, idle, login, markdown, dock)

---

## ⚠️ Offline Limitations

Chat responses, cloud saves, multiplayer features, and content requiring server-side artifacts from the original site cannot be restored from public frontend resources; the offline version will display interfaces with empty states or local placeholder responses. Links in browser applications pointing to external websites naturally won't work offline.

This is a local archive of publicly accessible frontend resources and does **not include** content that bypasses login, payment, or other access controls.

---

## 🤝 Contributing

We welcome contributions in all forms! Please refer to:

- [Contributor Agreement (Chinese)](CONTRIBUTING_CN.md)
- [Contributor Agreement (English)](CONTRIBUTING.md)
- [GPL-3.0 License](LICENSE)

### Contribution Flow

1. Fork this project
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add some amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) file for details.

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

## 🔗 Related Links

- [Astro Official Documentation](https://docs.astro.build)
- [Islands Architecture Introduction](https://jasonformat.com/islands-architecture/)
- [Live2D Cubism](https://www.live2d.com/en/)
- [Original Project](https://os.inori.ai/)

---

<div align="center">

**Made with ❤️ by NoriOS Community**

[Back to Top](#norios-offline-archive)

</div>
