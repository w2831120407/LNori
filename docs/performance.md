# 性能优化

## 性能基准

### 目标指标

| 指标 | 优秀 | 良好 | 需改进 |
|------|------|------|--------|
| FCP | < 1.0s | 1.0-2.5s | > 2.5s |
| LCP | < 1.5s | 1.5-2.5s | > 2.5s |
| TTI | < 2.0s | 2.0-3.5s | > 3.5s |
| TBT | < 100ms | 100-300ms | > 300ms |
| CLS | < 0.1 | 0.1-0.25 | > 0.25 |

## Astro 优化策略

### 1. 部分 Hydration

```astro
---
import InteractiveComponent from '../components/InteractiveComponent.astro'
---

<!-- 立即加载关键交互 -->
<InteractiveComponent client:load />

<!-- 非关键交互延迟加载 -->
<InteractiveComponent client:idle />

<!-- 可见时加载 -->
<InteractiveComponent client:visible />

<!-- 媒体查询条件加载 -->
<InteractiveComponent client:media="(max-width: 768px)" />
```

### 2. 静态内容优化

```astro
---
// 服务端渲染静态内容
const staticData = await fetchStaticData()
---

<!-- 直接输出 HTML，无 JS 开销 -->
<div class="static-content">
  {staticData.map(item => (
    <div>{item.title}</div>
  ))}
</div>
```

### 3. 资源内联

```astro
---
import criticalCSS from './critical.css?inline'
---

<style set:html={criticalCSS} />
```

## JavaScript 优化

### 代码分割

```javascript
// 动态导入
async function loadApp(appName) {
  const module = await import(`../APPS/${appName}/index.js`)
  return module.default
}

// 仅在需要时加载
button.addEventListener('click', async () => {
  const app = await loadApp('browser')
  app.init()
})
```

### Tree Shaking

```javascript
// ✅ 好的做法 - 允许 tree shaking
import { debounce } from './utils.js'

// ❌ 不好的做法 - 阻止 tree shaking
import * as utils from './utils.js'
utils.debounce()
```

### Web Workers

```javascript
// 将计算密集型任务移至 Worker
if (window.Worker) {
  const worker = new Worker('./worker.js')
  worker.postMessage({ type: 'COMPUTE', data })
  worker.onmessage = (e) => console.log(e.data)
}
```

## CSS 优化

### 关键 CSS 提取

```javascript
// vite.config.js
export default {
  css: {
    modules: {
      generateScopedName: '[hash:base64:5]'
    }
  },
  build: {
    cssCodeSplit: true
  }
}
```

### CSS 变量

```css
:root {
  --primary-color: #007bff;
  --spacing-unit: 8px;
  --font-size-base: 16px;
}

.app {
  padding: calc(var(--spacing-unit) * 2);
  color: var(--primary-color);
}
```

### 避免布局抖动

```css
/* ✅ 使用 transform */
.element {
  transform: translateX(100px);
}

/* ❌ 触发重排 */
.element {
  margin-left: 100px;
}
```

## 图片优化

### 现代格式

```astro
---
import image from './image.png'
---

<picture>
  <source srcset={image.src} type="image/webp" />
  <img src={image.src} alt="description" loading="lazy" />
</picture>
```

### 响应式图片

```html
<img 
  src="image-800.jpg"
  srcset="image-400.jpg 400w, image-800.jpg 800w, image-1200.jpg 1200w"
  sizes="(max-width: 600px) 400px, (max-width: 1200px) 800px, 1200px"
  alt="description"
  loading="lazy"
/>
```

### SVG 优化

```bash
# 使用 SVGO 压缩
npm install -g svgo
svgo icon.svg
```

## 缓存策略

### HTTP 缓存头

```nginx
# Nginx 配置
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
  expires 1y;
  add_header Cache-Control "public, immutable";
}

location ~* \.html$ {
  expires -1;
  add_header Cache-Control "no-cache, no-store, must-revalidate";
}
```

### Service Worker

```javascript
// sw.js
const CACHE_NAME = 'webos-v1'
const ASSETS = ['/', '/index.html', '/styles.css']

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
  )
})

self.addEventListener('fetch', (e) => {
  e.respondWith(
    caches.match(e.request).then(response => response || fetch(e.request))
  )
})
```

## 测量工具

### Lighthouse

```bash
# CLI 运行
lighthouse http://localhost:4322 --output html --output-path report.html

# CI 集成
npm install -g @lhci/cli
lhci autorun
```

### Chrome DevTools

1. **Performance 面板**: 记录和分析运行时性能
2. **Coverage 工具**: 识别未使用的代码
3. **Network 面板**: 分析资源加载
4. **Memory 面板**: 检测内存泄漏

### Web Vitals

```javascript
import { getCLS, getFID, getFCP, getLCP, getTTFB } from 'web-vitals'

const sendToAnalytics = (metric) => {
  // 发送到分析服务
  navigator.sendBeacon('/analytics', JSON.stringify(metric))
}

getCLS(sendToAnalytics)
getFID(sendToAnalytics)
getFCP(sendToAnalytics)
getLCP(sendToAnalytics)
getTTFB(sendToAnalytics)
```

## 优化清单

### 构建时

- [ ] 启用生产模式构建
- [ ] 压缩 HTML/CSS/JS
- [ ] Tree shaking 移除死代码
- [ ] 代码分割按需加载
- [ ] 图片格式转换 (WebP/AVIF)
- [ ] 生成 Source Map (仅开发)

### 运行时

- [ ] 懒加载非关键资源
- [ ] 预加载关键资源
- [ ] 使用 requestIdleCallback
- [ ] 虚拟化长列表
- [ ] 防抖节流频繁操作
- [ ] 使用 CSS containment

### 网络

- [ ] 启用 Gzip/Brotli 压缩
- [ ] 配置 CDN
- [ ] 设置合理缓存头
- [ ] HTTP/2 或 HTTP/3
- [ ] 预连接到关键域
- [ ] 减少 DNS 查找

## 性能预算

在 `package.json` 中设置：

```json
{
  "scripts": {
    "build": "astro build",
    "test:perf": "lighthouse-ci"
  },
  "lighthouse": {
    "extends": "lighthouse:default",
    "settings": {
      "performanceBudget": [
        {
          "resourceType": "script",
          "budget": 50000
        },
        {
          "resourceType": "stylesheet",
          "budget": 10000
        },
        {
          "resourceType": "image",
          "budget": 100000
        }
      ]
    }
  }
}
```

---
*版本: v2.0.0 | 最后更新: 2024*
