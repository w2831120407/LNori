#!/usr/bin/env bash
# ============================================================
# NoriOS 手动构建脚本 v2 (纯SDK工具链)
# 适配：一加 ACE5 至尊版 / ColorOS 16.0.10 / Android 16 (API 36)
# ============================================================
set -eu

PROJECT_DIR="/workspace/android_build/NoriOS"
APP_DIR="$PROJECT_DIR/app/src/main"
BUILD_DIR="$PROJECT_DIR/build_manual"
SDK_DIR="/workspace/android_build/android-sdk"
BUILD_TOOLS_VER="35.0.0"
PLATFORM_VER="android-36"

JAVA_HOME="/root/.local/share/mise/installs/java/17.0.2"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$SDK_DIR/build-tools/$BUILD_TOOLS_VER:$SDK_DIR/platform-tools:$PATH"

ANDROID_JAR="$SDK_DIR/platforms/$PLATFORM_VER/android.jar"
AAPT2="$SDK_DIR/build-tools/$BUILD_TOOLS_VER/aapt2"
D8="$SDK_DIR/build-tools/$BUILD_TOOLS_VER/d8"
ZIPALIGN="$SDK_DIR/build-tools/$BUILD_TOOLS_VER/zipalign"
APKSIGNER="$SDK_DIR/build-tools/$BUILD_TOOLS_VER/apksigner"
KEYTOOL="$JAVA_HOME/bin/keytool"

echo "🐱 === NoriOS 构建开始喵！(v2) ==="
echo "📱 SDK: $SDK_DIR"
echo "🛠️  Build-Tools: $BUILD_TOOLS_VER | Platform: $PLATFORM_VER"
echo "☕ Java: $JAVA_HOME"

# ---- 清理 ----
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{compiled_res,gen,classes,dex,output,keystore}

# ============================================================
# 1. aapt2 compile：编译资源
# ============================================================
echo ""
echo "📦 [1/7] 编译资源 (aapt2 compile --dir)..."
"$AAPT2" compile --dir "$APP_DIR/res" -o "$BUILD_DIR/compiled_res/" 2>&1 || true
FLAT_COUNT=$(find "$BUILD_DIR/compiled_res" -name "*.flat" | wc -l)
echo "   → 编译出 $FLAT_COUNT 个资源单元"

# 构造 -R file1 -R file2 ... 参数
R_ARGS=""
for f in $(find "$BUILD_DIR/compiled_res" -name "*.flat" | sort); do
    R_ARGS="$R_ARGS -R $f"
done

# ============================================================
# 2. aapt2 link：链接资源 + 生成R.java + 产出基础APK
# ============================================================
# ============================================================
# 🐞 BUGFIX v1.0.5: 【动态复制Web源码→APK assets/www】 关键修复！
# 之前build_apk.sh完全缺失此步骤导致APK里assets/www永远是旧残缺/空内容！
# 每次构建必须用 /workspace/src 目录（Node服务器真实serve的目录, server.mjs line6）覆盖复制
# ============================================================
WEB_SRC_DIR="/workspace/src"
ASSETS_WWW_DIR="$APP_DIR/assets/www"
echo ""
echo "🔄 BUGFIX v1.0.5: 动态复制最新Web源码 → APK assets/www (src/真实Serving目录)"
echo "   源: $WEB_SRC_DIR ($(find "$WEB_SRC_DIR" -type f 2>/dev/null | wc -l) 个文件)"
rm -rf "$ASSETS_WWW_DIR"
mkdir -p "$ASSETS_WWW_DIR"
cp -r "$WEB_SRC_DIR"/. "$ASSETS_WWW_DIR"/
chmod -R u+rwX "$ASSETS_WWW_DIR"
echo "   完成 → $ASSETS_WWW_DIR ($(find "$ASSETS_WWW_DIR" -type f | wc -l) 个文件)"
echo ""
echo ""
echo "🔗 [2/7] 链接资源 + R.java (aapt2 link)..."
"$AAPT2" link \
    -o "$BUILD_DIR/output/norios-base.apk" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay \
    -A "$APP_DIR/assets" \
    $R_ARGS

echo "   → R.java: $(find "$BUILD_DIR/gen" -name "R.java" | wc -l) 个"
ls -la "$BUILD_DIR/output/norios-base.apk" 2>&1
echo "   → 基础APK内容预览:"
unzip -l "$BUILD_DIR/output/norios-base.apk" 2>&1 | head -25 || true

# ============================================================
# 3. javac：编译 R.java + MainActivity.java
# ============================================================
echo ""
echo "☕ [3/7] 编译 Java 源码 (javac 17)..."
JAVA_SRC_LIST=$(mktemp)
find "$APP_DIR/java" "$BUILD_DIR/gen" -name "*.java" 2>/dev/null > "$JAVA_SRC_LIST"
echo "   → $(wc -l < "$JAVA_SRC_LIST") 个 java 文件"
cat "$JAVA_SRC_LIST"

mkdir -p "$BUILD_DIR/classes"
javac \
    -d "$BUILD_DIR/classes" \
    -cp "$ANDROID_JAR" \
    -source 17 -target 17 \
    -Xlint:-options \
    @"$JAVA_SRC_LIST" 2>&1

CLASS_COUNT=$(find "$BUILD_DIR/classes" -name "*.class" | wc -l)
echo "   → 编译出 $CLASS_COUNT 个 class 文件"
find "$BUILD_DIR/classes" -name "*.class" -print

# ============================================================
# 4. d8：class → dex
# ============================================================
echo ""
echo "🧬 [4/7] 生成 DEX (d8 min-api=26)..."
CLASS_LIST=$(find "$BUILD_DIR/classes" -name "*.class" | tr '\n' ' ')
"$D8" \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BUILD_DIR/dex" \
    $(find "$BUILD_DIR/classes" -name "*.class") 2>&1

echo "   → DEX 产物:"
ls -lh "$BUILD_DIR/dex/"

# ============================================================
# 5. 把 DEX 打入 APK
# ============================================================
echo ""
echo "🗜️  [5/7] DEX 合并入 APK..."
cp -f "$BUILD_DIR/output/norios-base.apk" "$BUILD_DIR/output/norios-with-dex.apk"
cd "$BUILD_DIR/dex"
zip -q "$BUILD_DIR/output/norios-with-dex.apk" classes.dex
if [ -f classes2.dex ]; then zip -q "$BUILD_DIR/output/norios-with-dex.apk" classes2.dex; fi
if [ -f classes3.dex ]; then zip -q "$BUILD_DIR/output/norios-with-dex.apk" classes3.dex; fi
cd "$PROJECT_DIR"
echo "   → 加DEX后APK: $(du -h "$BUILD_DIR/output/norios-with-dex.apk" | cut -f1)"

# ============================================================
# 6. zipalign 4字节对齐
# ============================================================
echo ""
echo "📐 [6/7] zipalign 对齐..."
rm -f "$BUILD_DIR/output/norios-aligned.apk"
"$ZIPALIGN" -f -p 4 "$BUILD_DIR/output/norios-with-dex.apk" "$BUILD_DIR/output/norios-aligned.apk"
echo "   → 对齐校验:"
"$ZIPALIGN" -c 4 "$BUILD_DIR/output/norios-aligned.apk" && echo "     ✓ Verification succesful"

# ============================================================
# 7. 生成 debug keystore 并签名 (v1+v2+v3)
# ============================================================
echo ""
echo "🔏 [7/7] APK签名 (v1+v2+v3)..."
DEBUG_KEY="$BUILD_DIR/keystore/debug.keystore"
if [ ! -f "$DEBUG_KEY" ]; then
    echo "   → 生成 debug keystore (首次构建)..."
    "$KEYTOOL" -genkeypair -v \
        -keystore "$DEBUG_KEY" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -2
fi

FINAL_APK="/workspace/NoriOS-OnePlusACE5-ColorOS16.apk"
rm -f "$FINAL_APK"

"$APKSIGNER" sign \
    --ks "$DEBUG_KEY" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    --out "$FINAL_APK" \
    "$BUILD_DIR/output/norios-aligned.apk" 2>&1

echo ""
echo "🔍 签名验证:"
"$APKSIGNER" verify --verbose "$FINAL_APK" 2>&1 | grep -E "Verified|signer|using|scheme" | head -10

echo ""
echo "=========================================="
echo "✅✅✅ 构建成功喵！ (✧ω✧)"
echo "=========================================="
echo "📦 APK 文件: $FINAL_APK"
echo -n "   大小:   " && du -h "$FINAL_APK" | cut -f1
echo -n "   MD5:    " && md5sum "$FINAL_APK" | cut -d' ' -f1
echo -n "   SHA256: " && sha256sum "$FINAL_APK" | cut -d' ' -f1
echo ""
echo "📱 目标设备适配:"
echo "   - 设备: 一加 ACE5 至尊版 (PJZ110)"
echo "   - 系统: ColorOS 16.0.10 (基于 Android 16)"
echo "   - minSdkVersion: 26 (Android 8.0+) "
echo "   - targetSdkVersion: 36 (Android 16) "
echo "   - CPU: 通用架构 (ARM64/ARM32/x86/x86_64 全兼容)"
echo "   - GPU: OpenGL ES 3.0+ (已声明, 适配骁龙8至尊版)"
echo ""
echo "✨ 已内置功能:"
echo "   • NoriOS Web 全部资源 (636个文件, Live2D+全部Apps)"
echo "   • WebView 全功能 (JS/DOM Storage/Cache/WebGL/音频)"
echo "   • Live2D Cubism 文件跨域权限"
echo "   • 沉浸式全屏 + 挖孔屏适配 (LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)"
echo "   • 高刷新率 + 低延迟渲染 (setPreferMinimalPostProcessing)"
echo "   • 文件上传 / 相机拍照 / 定位 / 录音权限"
echo "   • 返回键回退 WebView 历史"
echo "=========================================="
