# NoriOS v1.0.2 · 黑屏完全修复说明 (一加ACE5至尊版/ColorOS16)

## 🐞 v1.0.0/v1.0.1 白屏/黑屏 根因
| 版本 | 现象 | 根因 |
|---|---|---|
| v1.0.0 | 白屏 | file:// 下前端 `/xxx` 绝对路径→系统根目录→JS/CSS/Live2D全404 |
| v1.0.1 | 黑屏 | ① Theme默认纯黑背景；② WebView透明；③ 无兜底页=透出纯黑空壳 |

## ✅ v1.0.2 9项链路防御
1. Window强制白 + WebView强制白（绝对不透黑）
2. 🐱 Loading占位页（小猫动画+进度条）
3. 红色诊断错误页（机型/系统/状态码/异常/资源数+两个重试按钮）
4. 端口自动重试 8008→8017（10次）
5. 后台线程HTTP自测（GET /index.html 200+len>5KB才算PASS，避开NetworkOnMainThread）
6. 1.5秒自动file:// fallback
7. Assets资源递归计数+index存在性自检
8. 两个手动重试按钮（点我试HTTP/点我试file）
9. 密钥永久存 `android_build/keystores_permanent/`（不会被强清理删）

## ⚠️ 签名
- 新签名链：release_v102.jks（v101误删），安装v1.0.2**必须先卸载旧版NoriOS**
- 路径：android_build/keystores_permanent/release_v102.jks / alias:norios_v102 / pass:NoriOSv102Release
