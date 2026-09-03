# 一加 ACE5 至尊版 / ColorOS 16.0.10 安装指南

本仓库适配了 NoriOS Web 应用为原生 Android APK。

## 📦 下载 Release APK

去 Releases 页面下载 `NoriOS-OnePlusACE5-ColorOS16-RELEASE.apk`（正式签名版，推荐）。

## 🚀 安装方式一：USB + ADB（最稳，绕过ColorOS风险拦截）

1. 手机 → 设置 → 关于手机 → 连续点击「版本号」7次 → 开启开发者模式
2. 系统设置 → 开发者选项 → 开启 3 项：
   - ✅ USB 调试
   - ✅ USB 安装（通过USB安装应用）
   - ✅ USB 调试（安全设置），允许通过USB调试修改权限或模拟点击
3. USB线连接电脑，手机弹窗「允许此计算机进行USB调试」→ 打勾并允许
4. 终端执行：
   ```bash
   adb install -r NoriOS-OnePlusACE5-ColorOS16-RELEASE.apk
   ```
5. 完成，桌面出现紫色"N"图标 → 点开即可游玩

## 📱 安装方式二：直接传到手机安装

1. 通过微信/网盘/OTG U盘把 APK 存到手机
2. 文件管理器里找到 APK 点击
3. ColorOS 16 弹风险提示 → 右上角三个点 → 允许本次安装 → 继续安装
4. 桌面出现 NoriOS 图标即完成

## 🛠️ 自行重新构建 APK

```bash
bash android_build/build_apk.sh
```
输出路径：`/workspace/NoriOS-OnePlusACE5-ColorOS16-RELEASE.apk`

## 🔑 Release 签名信息（升级时必须用同一个）
- 包名：`com.norios.app`
- minSdkVersion：26（Android 8.0+）
- targetSdkVersion：36（Android 16 / ColorOS 16）
- 签名：v2 + v3 双签名

## ✨ 已内置功能
- Live2D Nori（Cubism SDK，3套服装+表情动画）
- 浏览器 / CakeDuel卡牌对战 / 你画我猜 / 终端 / 文件管理器 / 邮件 等 Apps
- 沉浸式全屏 + 挖孔屏适配 + 高刷低延迟
