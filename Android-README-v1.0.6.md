# NoriOS v1.0.6 · 启动崩溃紧急修复(requestWindowFeature must be before setContentView)

## 🐞 主人截图(v1.0.4)
机型 PLC110 / Android16 SDK36 / v1.0.4 (versionCode=104) 启动即崩溃兜底页:
崩溃类型: `android.util.AndroidRuntimeException: requestFeature() must be called before adding content`
堆栈: `MainActivity.java:99 → requestWindowFeature(Window.FEATURE_NO_TITLE)`

## 🔗 根因(Android 16/ColorOS16强制要求)
v1.0.0-v1.0.5 `MainActivity.onCreate` 顺序错误:
```
第95行: setContentView(rootFrame)     ← 先add content
第99行: requestWindowFeature(NO_TITLE) ← 然后才request Feature → 必崩！
```
Android 8.0+ 开始有警告；**Android 16 / ColorOS 16 变严 → 直接 throw RuntimeException kill app！**

## ✅ 修复(v1.0.6 最小变更集)
整块 Window 相关设置前移到 super.onCreate 之后、setContentView 之前，严格遵循顺序:
| 顺序 | 内容 | 说明 |
|---|---|---|
| 1 | `super.onCreate()` | Activity基础初始化 |
| 2 | ✨ `requestWindowFeature(FEATURE_NO_TITLE)` | **必须最早！任何content前** |
| 3 | `getWindow().setFlags(FULLSCREEN+HARDWARE_ACCELERATED)` | Window FLAG |
| 4 | `getDecorView().setSystemUiVisibility(IMMERSIVE_STICKY+FULLSCREEN等)` | 沉浸式 |
| 5 | `layoutInDisplayCutoutMode SHORT_EDGES` (P+) + `setPreferMinimalPostProcessing` (R+) | 刘海屏+低延迟渲染 |
| 6 | `setBackgroundDrawable白 + DecorView白` | 防黑刷白 |
| 7 | `FrameLayout new + 🔴 红色锚点View addView` | 白容器+锚点 |
| 8 | **setContentView(rootFrame)** | 最后！所有Window属性设置完才调！ |

同 v102 签名链(release_v102.jks) → v1.0.2~v1.0.5 直接覆盖升级，不用卸载！

## ⚠️ 附带v1.0.5已修内容
- build_apk.sh 动态复制src→assets/www
- guessMime白名单前置(.js→application/javascript，防ES Module拒载空白)
- HTTP零静默+Content-Length写入校验+readLine限长
