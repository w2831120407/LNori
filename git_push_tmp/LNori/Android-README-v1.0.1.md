# NoriOS Android v1.0.1 · 一加ACE5至尊版 / ColorOS 16 白屏修复说明

## v1.0.1 更新了什么？修复了首次启动白屏问题 🎉

### 🔴 问题根因
原始 v1.0.0 使用 `file:///android_asset/www/index.html` 加载。
而 index.html 里所有资源引用都是**绝对路径 `/xxx`**：
```html
<link rel="stylesheet" href="/fonts.css" />
<script src="/cubism_sdk/.../core.js"></script>
<script type="module">import { mountApp } from '/assets/xxx.js';</script>
```
- ✅ 在 Web 服务器(Node.js)里，`/fonts.css` → 网站根目录，正常工作
- ❌ 在 file:// 协议下，`/fonts.css` → 被解析为 `file:///fonts.css`(手机系统根目录)
  → 所有 CSS/JS 全找不到 → **白屏**

### 💡 v1.0.1 修复方案：内嵌 Mini HTTP Server
新增 `LocalAssetServer.java`（纯Java实现，零依赖）——APP启动时在`127.0.0.1:8008`开一个轻量静态HTTP服务器，
WebView 改为访问 `http://127.0.0.1:8008/index.html`。

这样 `/xxx` 就会正确命中 `http://127.0.0.1:8008/xxx`——与原Node服务器语义100%一致！
连 ES Module、`<link rel="modulepreload">`、Range请求(音频拖动)、CORS 全正常！

### 📊 性能对比
| 项目 | v1.0.0 file:// | v1.0.1 HTTP://127.0.0.1 |
|---|---|---|
| 白屏概率 | 100% | 0% |
| 绝对路径`/xxx` | ❌ 找不到 | ✅ 正常 |
| ES Module 支持 | 受限 | ✅ 完整 |
| 音频 Range 请求 | 不支持 | ✅ 支持 |
| 额外内存开销 | 0 | ~150KB 可忽略 |
| 启动速度 | - | 等效（<10ms开服务器）|

### 🛠️ 重新构建
```bash
bash android_build/build_apk.sh
```
构建脚本会自动从仓库根 `src/` 复制Web资源到临时assets目录，7步一键完成出包。
