# 应用规范

## 标准结构

所有 APPs 必须遵循以下目录结构：

```
src/APPS/{app-name}/
├── index.astro          # 必需：Astro 封装入口
├── src/                 # 必需：源代码目录
│   ├── index.js         # 必需：主逻辑文件
│   └── style.css        # 必需：样式文件
├── icons/               # 必需：图标资源
│   ├── icon.svg         # 主图标
│   └── icon-*.svg       # 其他尺寸/状态图标
├── assets/              # 可选：其他静态资源
├── README.md            # 必需：应用说明文档
└── package.json         # 可选：独立依赖配置
```

## Astro 组件模板

```astro
---
// src/APPS/{app-name}/index.astro
// 应用元数据
const appName = '应用名称'
const appVersion = '1.0.0'
const appDescription = '应用描述'
---

<div class="app-container" data-app={appName}>
  <!-- 应用根元素 -->
  <div id="{appName}-root"></div>
</div>

<!-- 导入样式和脚本 -->
<link rel="stylesheet" href="./src/style.css" />
<script>
  import './src/index.js'
  
  // 可选：客户端交互指令
  // client:load - 页面加载时立即执行
  // client:idle - 浏览器空闲时执行
  // client:visible - 元素可见时执行
</script>

<style>
  /* 可选：组件级样式 */
  .app-container {
    width: 100%;
    height: 100%;
  }
</style>
```

## 命名规范

### 目录命名

- 使用小写字母
- 单词间用连字符 `-` 分隔
- 简洁且具有描述性

示例：
```
✅ browser
✅ file-manager
❌ Browser
❌ file_manager
❌ theBrowserApp
```

### 文件命名

| 类型 | 规范 | 示例 |
|------|------|------|
| Astro 组件 | `index.astro` | `index.astro` |
| JavaScript | 小写 + 连字符 | `main-logic.js` |
| CSS | 小写 + 连字符 | `app-styles.css` |
| 图标 | 描述性名称 | `icon-launch.svg` |

### CSS 类名 (BEM)

```css
/* Block */
.app-browser {}

/* Element */
.app-browser__toolbar {}
.app-browser__content {}

/* Modifier */
.app-browser--fullscreen {}
.app-browser__toolbar--hidden {}
```

## 性能要求

### 加载性能

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| FCP (First Contentful Paint) | < 1.5s | Lighthouse |
| LCP (Largest Contentful Paint) | < 2.5s | Lighthouse |
| TTI (Time to Interactive) | < 3.5s | Lighthouse |
| TBT (Total Blocking Time) | < 200ms | Lighthouse |

### 代码体积

| 类型 | 限制 | 说明 |
|------|------|------|
| JS (单个应用) | < 50KB (gzipped) | 不含第三方库 |
| CSS (单个应用) | < 10KB (gzipped) | |
| 图片资源 | < 100KB/个 | 使用 SVG 优先 |

### 最佳实践

1. **懒加载**: 非关键资源延迟加载
2. **代码分割**: 按功能模块拆分代码
3. **资源优化**: 压缩图片，使用现代格式
4. **缓存策略**: 利用浏览器缓存

## 安全规范

### 必须遵守

- ✅ 不使用 `eval()` 或 `Function()` 构造函数
- ✅ 对用户输入进行转义
- ✅ 使用 CSP nonce (Astro 自动处理)
- ✅ 避免内联事件处理器 (`onclick` 等)
- ✅ 使用 HTTPS 加载外部资源

### 禁止行为

- ❌ 直接操作 `document.write()`
- ❌ 使用不安全的 `innerHTML`
- ❌ 存储敏感信息在 localStorage
- ❌ 加载未经验证的第三方脚本

## 兼容性要求

### 浏览器支持

| 浏览器 | 最低版本 |
|--------|----------|
| Chrome | 90+ |
| Firefox | 88+ |
| Safari | 14+ |
| Edge | 90+ |

### 特性检测

```javascript
// 推荐：特性检测
if ('serviceWorker' in navigator) {
  // 使用 Service Worker
}

// 不推荐：浏览器检测
if (navigator.userAgent.includes('Chrome')) {
  // ...
}
```

## 可访问性 (A11y)

### 基本要求

- [ ] 语义化 HTML 标签
- [ ] 适当的 ARIA 属性
- [ ] 键盘导航支持
- [ ] 足够的颜色对比度 (4.5:1)
- [ ] 焦点指示器可见

### 示例

```html
<!-- 好的做法 -->
<button aria-label="关闭应用" onclick="closeApp()">
  <svg aria-hidden="true">...</svg>
</button>

<!-- 不好的做法 -->
<div onclick="closeApp()">✕</div>
```

## 测试清单

发布前必须完成以下检查：

- [ ] 功能完整性测试
- [ ] 跨浏览器测试
- [ ] 响应式布局测试
- [ ] 性能基准测试
- [ ] 可访问性审计
- [ ] 安全扫描
- [ ] 代码审查

---
*版本: v2.0.0 | 最后更新: 2024*
