# NoriOS v1.0.5 · Web资源补全 + LocalAssetServer HTTP健壮性大修

## 🐞 用户截图反馈（1.0.4→1.0.5）
> 「浏览器访问127.0.0.1:8008，服务确实在运行，但服务里没有内容」
根因链（层层递进，共5层）：
1. **build_apk.sh缺动态复制步骤**（最致命）→ aapt2 link用的`-A assets/www`永远是旧/空/残缺缓存（之前小月都是手动复制）
2. src/ 目录 636个文件 vs starpak/LNori原版 → **实际是100%全匹配**（大JS bundle大小一致）→ 排除资源缺失问题
3. **MIME判断顺序反了(最关键)** → `guessMime()` 先跑系统URLConnection，可能误判.js为`application/octet-stream`，再白名单兜底。WebView对ES Module(`<script type="module">`)的`import`加载做严格MIME类型检查（必须是合法的application/javascript）→ **JS bundle全被拒绝加载 → HTML骨架1136B有，但页面空壳无渲染**
4. **HTTP请求解析失败时静默return** → 连400/500响应都不发 → 浏览器收到空TCP连接→显示空白
5. **assetManager.open、Content-Length写入无校验** → 大文件(2.3MB)传输时可能没写够Content-Length字节→浏览器一直等待剩余字节→超时空白

## ✅ v1.0.5 修复清单

### 🛠️ 构建脚本(build_apk.sh) — 1处
- **第二步aapt2 link之前强制动态复制**：`rm -rf $APP_DIR/assets/www && cp -r /workspace/src/. $APP_DIR/assets/www/`
  → 每次构建保证最新Node服务器src/目录进APK，不再依赖手动复制

### 🌐 LocalAssetServer.java — 5大修
1. **MIME白名单前置(最高优先级)**：.js/.mjs=application/javascript，CSS/JSON/图片/字体全硬编码正确
2. **HTTP解析全链路响应兜底**：RequestLine空/非法→sendBadRequest(400 HTML)；open失败→sendInternalError(500 HTML)；handleClient外层Throwable→尝试发500
3. **readLine加maxBytes限制**：防止超长headers卡住真实浏览器
4. **Content-Length强校验**：写完body后验证written=contentLength，不等则打警告Log+shutdownOutput防止浏览器死等
5. **加CORS/COOP/COEP头** + OPTIONS方法支持 + 512行headers限制防无限循环

### 🏗️ 仓库补全(starpak/LNori原版同步)
- /src/ 636文件全量同步（100%一致）
- /dist/ 641个生产构建文件补全
- 根目录 ARGNori.* Live2D模型 + expressions/ + motions/ 补全
- live2dcubismcore.js、server.mjs、start.sh、start.bat、package.json等补全

## 📦 签名(继续v102)
keystores_permanent/release_v102.jks / alias:norios_v102 / pass:NoriOSv102Release
→ v1.0.2/1.0.3/1.0.4用户可直接覆盖升级，不用卸载
