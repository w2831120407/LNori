# NoriOS v1.0.9 · 主人3指令执行版(相对路径+assets路由+新APK)

## ✅ 主人3条指令 100%落地

### 1. 打包使用相对资源路径 ./
因仓库无vite.config/astro.config(使用starpak预构建好的636资源)，用后处理sed批量替换：
- `src/index.html` 6处 href/src 绝对路径 → 全部加 `./` 前缀
  - `/icon.png` → `./icon.png`
  - `/fonts.css` → `./fonts.css`
  - `/cubism_sdk/Core/live2dcubismcore.js` → `./cubism_sdk/Core/live2dcubismcore.js`
  - `/assets/index-FU-0vwSE.css` → `./assets/index-FU-0vwSE.css`
  - 2处 modulepreload `/assets/*.js` → `./assets/*.js`
- 全 `src/` 目录下 所有 *.js *.css 批量 sed 替换：
  - `@import url('/fonts.css')` → `./fonts.css`（影响5个懒加载style-in-js bundle）
  - `'/assets/'` → `'./assets/'`（影响动态import、懒加载chunk）
  - `'/cubism_sdk/'` → `./cubism_sdk/`（Live2D Cubism Core 路径）
  - `'/ARGNori/' / '/expressions/' / '/motions/'` → 加 `./` 前缀（Live2D 模型资源）
- **验证结果**：grep -E "['\"]/assets/" src 剩余计数 = **0** ✅ 绝对路径全部消除！

### 2. 修正 127.0.0.1:8008 静态服务器 /assets 路由映射
`LocalAssetServer.java` 的 `normalizePath()` 方法大升级（v1.0.9）：
- 处理相对路径 ./ 请求：`./assets/xxx` → 自动归一 `/assets/xxx`
- 处理路径中 `/./` 段 → 合并为 `/`
- 处理连续 `//` → 合并单个 `/`
- 保留原有的 `..` 穿越防御 + %xx 解码
- assetPath = assetRoot(www) + normalizedPath → 映射完全命中 `assets/www/assets/**` ✅
- **兜底双重验证**：打开 assets/www/assets 目录文件数（真实懒加载chunk+主bundle共600+）大小与src一致

### 3. 重新打包APK + 编译验证（v1.0.9）
- Manifest versionCode=109 / versionName=1.0.9
- MainActivity.APP_VERSION 同步v1.0.9 (诊断面板显示真实版本号)
- 强清理 build_manual + 7步(aapt2 compile/link javac17 d8 zip zipalign apksigner)构建
- release_v102.jks v1+v2+v3 三签 → **覆盖升级v1.0.2~v1.0.8不用卸载**
- 解RELEASE APK验证(按经验2082877):
  - assets/www总文件数=636 ✅
  - index.html 相对路径匹配数>=5 / 绝对路径=0 ✅
  - 6大核心bundle大小与src 100%字节一致 ✅

## 附带v1.0.2~v1.0.8全部修复
- v1.0.8: 删错误的import mountApp死代码（消除ES Module SyntaxError）
- v1.0.7: 15秒DOM自动体检+200条诊断环形缓冲+原生诊断面板（大功臣！抓出了SyntaxError）
- v1.0.6: requestWindowFeature前移setContentView之前（Android16崩溃修）
- v1.0.5: build_apk.sh 动态复制 src→assets/www + guessMime白名单优先 + HTTP零静默 + Content-Length强校验
