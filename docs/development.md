# 开发指南

## 环境准备

### 系统要求

- Node.js >= 18.0
- npm >= 9.0
- Git

### 安装步骤

```bash
# 克隆仓库
git clone https://github.com/your-org/webos.git
cd webos

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问 `http://localhost:4322` 查看应用。

## 开发流程

### 1. 创建新应用

```bash
# 在 APPS 目录创建新应用文件夹
mkdir src/APPS/my-app

# 创建标准结构
mkdir -p src/APPS/my-app/{src,icons}
```

### 2. 应用结构

每个应用必须包含：

```
my-app/
├── index.astro      # Astro 封装组件
├── src/             # 源代码
│   ├── index.js     # 主逻辑
│   └── style.css    # 样式
├── icons/           # 图标资源
│   └── icon.svg
└── README.md        # 应用说明
```

### 3. 编写 Astro 组件

```astro
---
// src/APPS/my-app/index.astro
---

<div id="my-app-root">
  <!-- 应用内容 -->
</div>

<script>
  import './src/index.js'
  import './src/style.css'
</script>
```

### 4. 注册应用

在 `src/APPS/index.astro` 中导入新应用：

```astro
---
import MyApp from './my-app/index.astro'
---

<MyApp />
```

## 代码规范

### JavaScript

- 使用 ES6+ 语法
- 避免全局变量
- 使用模块系统

### CSS

- 使用 BEM 命名规范
- 避免 !important
- 优先使用 CSS 变量

### Astro

- 前端脚本使用 `client:load` 或 `client:idle`
- 静态内容直接写在模板中
- 避免在服务端脚本中使用 DOM API

## 调试技巧

### 开发模式

```bash
npm run dev -- --host
```

### 检查构建产物

```bash
npm run build
npm run preview
```

### 性能分析

使用 Chrome DevTools:
- Lighthouse 审计
- Performance 面板
- Coverage 工具

## 测试

### 手动测试清单

- [ ] 应用正常加载
- [ ] 所有功能可用
- [ ] 响应式布局正常
- [ ] 无控制台错误
- [ ] 性能指标达标

### 自动化测试 (待实现)

```bash
npm test
```

## 提交规范

遵循 Conventional Commits:

```
feat: 新增文件管理器拖拽功能
fix: 修复浏览器历史记录 bug
docs: 更新架构文档
style: 格式化代码
refactor: 重构邮件组件
test: 添加单元测试
chore: 更新依赖
```

---
*版本: v2.0.0 | 最后更新: 2024*
