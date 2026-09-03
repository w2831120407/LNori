package com.norios.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NoriOS Android 入口Activity
 * 纯 Android Framework 方案（无 AndroidX / Material 依赖）
 * 适配：一加 ACE5 至尊版 / ColorOS 16.0.10 / Android 16 (API 36)
 */
public class MainActivity extends Activity {

    private static final String TAG = "NoriOS";
    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private LocalAssetServer assetServer;   // 内嵌静态 HTTP 服务器
    private ValueCallback<Uri[]> filePathCallback;
    private String cameraPhotoPath;

    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ---- 一加 ACE5 至尊版 / ColorOS 16 视觉适配 ----
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // 挖孔屏适配 (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // 高画质 + 低延迟（骁龙8至尊版性能释放）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setPreferMinimalPostProcessing(true);
        }

        webView = new WebView(this);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollContainer(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setFadingEdgeLength(0);
        // --- BUGFIX v1.0.2: 防黑屏！强制白底，主题暗色模式下默认黑背景会透出导致全黑 ---
        getWindow().setBackgroundDrawableResource(android.R.color.white);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        // ---- 启动内嵌静态 HTTP 服务器 (解决file://下绝对路径 /xxx 找不到导致白屏的问题) ----
        final Exception[] startErrHolder = new Exception[1];
        try {
            assetServer = new LocalAssetServer(getAssets(), "www", LocalAssetServer.DEFAULT_PORT);
            assetServer.start();
        } catch (IOException e) {
            startErrHolder[0] = e;
            Log.e(TAG, "❌ LocalAssetServer 启动异常", e);
            assetServer = null;
        }

        setupWebView();
        requestNeededPermissions();

        // ---- BUGFIX v1.0.2: 先显示 Loading 占位页（绝对不会黑屏！），再后台跑 HTTP 自测（必须异步：避免NetworkOnMainThreadException） ----
        showLoadingPlaceholder();
        final LocalAssetServer serverFinal = assetServer;
        new Thread("NoriSelfTest") {
            @Override public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                boolean ok = false;
                String info = "";
                Exception testErr = null;
                if (serverFinal != null) {
                    try {
                        String u = serverFinal.getBaseUrl() + "index.html";
                        Object[] r = selfTestHttpServer(u, 8000);
                        int code = (int) r[0]; long len = (long) r[1];
                        // BUGFIX v1.0.3: index.html 实际大小只有约1136B（骨架，主体在index-xxx.js里），
                        // 所以 len > 5000 的判断过于严苛，会误判为FAIL。改为 len > 100 且 code==200 即视为PASS
                        // code==200 是第一优先级，body只要>100字节就说明有响应内容（排除空200异常情况）
                        ok = (code == 200 && len > 100);
                        info = "HTTP " + code + " / body=" + len + "B / " + r[2];
                        Log.i(TAG, "🧪 服务器自测 → " + info + " → " + (ok ? "✅PASS" : "❌FAIL"));
                    } catch (Exception e) {
                        testErr = e;
                        info = "Exception(" + e.getClass().getSimpleName() + "): " + e.getMessage();
                        Log.e(TAG, "🧪 自测异常", e);
                    }
                }
                final boolean fOK = ok;
                final String fInfo = info;
                final Exception fTestErr = testErr != null ? testErr : startErrHolder[0];
                runOnUiThread(() -> applyLoadingResult(fOK, serverFinal, fInfo, fTestErr));
            }
        }.start();
    }

    /* ---------------- v1.0.2 黑屏修复：占位页 & 自测结果处理 ---------------- */

    /** Loading 占位页（HTTP服务器启动/自测期间显示，保证绝对不是黑屏） */
    private void showLoadingPlaceholder() {
        String html = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">" +
                "<title>NoriOS Loading</title>" +
                "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#ffffff;color:#333;" +
                "font-family:-apple-system,BlinkMacSystemFont,\"PingFang SC\",\"Microsoft YaHei\",sans-serif;" +
                "display:flex;align-items:center;justify-content:center;text-align:center}" +
                ".box{padding:40px}.cat{font-size:72px;margin-bottom:16px;animation:bounce 1.2s infinite}" +
                "h1{margin:0 0 12px;color:#6B5BFF;font-size:24px}" +
                "p{margin:0;font-size:14px;color:#666}@keyframes bounce{0%,100%{transform:translateY(0)}50%{transform:translateY(-16px)}}" +
                " .bar{width:220px;height:6px;border-radius:999px;background:#eee;margin:24px auto 0;overflow:hidden}" +
                " .bar::after{content:'';display:block;width:40%;height:100%;background:linear-gradient(90deg,#6B5BFF,#03DAC5);border-radius:999px;animation:move 1.2s infinite}" +
                "@keyframes move{0%{transform:translateX(-100%)}100%{transform:translateX(350%)}}</style></head>" +
                "<body><div class=box><div class=cat>🐱</div><h1>NoriOS 正在启动喵～</h1>" +
                "<p>正在准备小精灵Nori的家园...稍等一会马上好！</p><div class=bar></div>" +
                "<p style=\"margin-top:18px;font-size:12px;color:#999\">v1.0.2 · ColorOS 16 优化版</p>" +
                "</div></body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html; charset=utf-8", "UTF-8", null);
    }

    /** 后台自测结束 → UI线程处理最终加载策略 */
    private void applyLoadingResult(boolean serverOK, LocalAssetServer srv, String selfTestInfo, Exception err) {
        if (serverOK && srv != null) {
            String url = srv.getBaseUrl() + "index.html";
            Log.i(TAG, "🌐 HTTP自测通过，加载: " + url);
            webView.loadUrl(url);
        } else {
            Log.w(TAG, "⚠️ HTTP自测未通过，先显示诊断页 → 15秒后file fallback");
            showErrorDiagnosticPage(err, selfTestInfo, 15); // BUGFIX v1.0.3: 1.5s → 15s
            webView.postDelayed(() -> {
                Log.i(TAG, "⏰ 15秒到，触发file fallback");
                tryFileFallback();
            }, 15_000L);
        }
    }

    /** HTTP 自测：GET url 返回 [code, bodyLen, extraInfo] */
    @android.annotation.SuppressLint("DefaultLocale")
    private Object[] selfTestHttpServer(String urlStr, int timeoutMs) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        long len = 0;
        String ct = conn.getContentType();
        java.io.InputStream in = null;
        try {
            in = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) len += n;
            }
        } finally { if (in != null) in.close(); conn.disconnect(); }
        return new Object[]{ code, len, String.format("CT=%s", ct) };
    }

    /** 显示诊断页（v1.0.3：HTTP 200用绿卡温和提示，异常用红卡；倒计时15秒+可取消） */
    private void showErrorDiagnosticPage(Exception err, String selfTestInfo, int countdownSec) {
        boolean http200 = (selfTestInfo != null && selfTestInfo.startsWith("HTTP 200"));
        String cardBg    = http200 ? "#e8f5e9" : "#fff5f5";
        String cardBd    = http200 ? "#a5d6a7" : "#ffcdd2";
        String titleClr  = http200 ? "#2e7d32" : "#d32f2f";
        String titleStr  = http200 ? "🐱 NoriOS v1.0.3 · 启动信息（绿色=HTTP服务已正常）" : "🐱 NoriOS v1.0.3 · 启动诊断页";
        String desc      = http200
            ? "✅ HTTP服务器自测正常（200 OK）。<br>为保险起见系统仍展示完整诊断信息，请点击下方绿色「立即进入 HTTP 加载」按钮即可正常使用喵～"
            : "内嵌HTTP服务器未达到最佳启动状态，下方是完整诊断信息，<b>"+countdownSec+"秒后</b>将自动切换到备用加载方式喵～<br>如仍无法正常运行，请将下方截图发给开发者～小月会马上修喵！";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=zh><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>NoriOS 诊断 v1.0.3</title>");
        sb.append("<style>");
        sb.append("*{box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,\"PingFang SC\",\"Microsoft YaHei\",sans-serif;");
        sb.append("max-width:720px;margin:24px auto;padding:16px;background:#fff;color:#111;line-height:1.7}");
        sb.append(".card{border:1px solid ").append(cardBd).append(";border-radius:14px;padding:16px 20px;background:").append(cardBg).append("}");
        sb.append("h1{color:").append(titleClr).append(";font-size:22px;margin:0 0 10px}h2{font-size:16px;color:#1976d2;margin:20px 0 10px}");
        sb.append("code{background:#f5f5f5;padding:2px 6px;border-radius:4px;font-size:13px}");
        sb.append(".st{border-collapse:collapse;width:100%}.st td{padding:6px 10px;border-bottom:1px solid #eee;font-size:14px}");
        sb.append(".st td:first-child{width:140px;color:#666;font-weight:bold}");
        sb.append(".btn{display:inline-block;margin:8px 8px 0 0;padding:12px 22px;border-radius:12px;text-decoration:none;font-weight:700;font-size:15px;box-shadow:0 2px 6px rgba(0,0,0,.08)}");
        sb.append(".btn-primary{background:#2e7d32;color:#fff}.btn-secondary{background:#1976d2;color:#fff}.btn-warn{background:#ef6c00;color:#fff}.btn-gray{background:#78909c;color:#fff}");
        sb.append(".countdown{background:#fff3e0;border-left:4px solid #ef6c00;padding:12px 16px;border-radius:10px;margin:16px 0;font-size:15px;color:#e65100;font-weight:600}");
        sb.append(".countdown b{font-size:22px;color:#bf360c;margin:0 4px}");
        sb.append("</style></head><body>");
        sb.append("<div class=card><h1>").append(titleStr).append("</h1>");
        sb.append("<div>").append(desc).append("</div></div>");
        if (!http200) {
            sb.append("<div class=countdown id=cdBox>⏰ 自动切换倒计时：<b id=cd>").append(countdownSec).append("</b>秒（点「取消自动跳转」停止）</div>");
        }
        sb.append("<h2>📋 诊断信息</h2><table class=st>");
        sb.append("<tr><td>版本号</td><td>NoriOS v1.0.3 · OnePlus ACE5 至尊版 ColorOS 16</td></tr>");
        sb.append("<tr><td>时间戳</td><td>").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date())).append("</td></tr>");
        sb.append("<tr><td>机型/系统</td><td>").append(android.os.Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")</td></tr>");
        String stDisp = (selfTestInfo==null||selfTestInfo.isEmpty()) ? "—" : selfTestInfo;
        String stColor = http200 ? "color:#2e7d32;font-weight:700" : (err!=null ? "color:#c62828" : "");
        sb.append("<tr><td>HTTP自测结果</td><td style=\"").append(stColor).append("\">").append(stDisp).append("</td></tr>");
        if (err != null) {
            sb.append("<tr><td>异常类型</td><td><code>").append(err.getClass().getName()).append("</code></td></tr>");
            sb.append("<tr><td>异常消息</td><td style=color:#c62828>").append(err.getMessage() == null ? "(无)" : android.text.Html.escapeHtml(err.getMessage())).append("</td></tr>");
        }
        sb.append("<tr><td>预期HTTP地址</td><td><code>http://127.0.0.1:").append(LocalAssetServer.DEFAULT_PORT).append("/index.html</code></td></tr>");
        sb.append("<tr><td>Fallback地址</td><td><code>file:///android_asset/www/index.html</code></td></tr>");
        sb.append("<tr><td>Assets资源数</td><td>").append(countAssets("www")).append(" 个文件（index.html存在性: ").append(hasAsset("www/index.html") ? "✅" : "❌").append(")</td></tr>");
        sb.append("</table>");
        sb.append("<h2>🚀 操作按钮（请优先点绿色HTTP加载！）</h2>");
        sb.append("<p><a class=\"btn btn-primary\" href=http://127.0.0.1:").append(LocalAssetServer.DEFAULT_PORT).append("/index.html>✅ 立即进入 HTTP 加载（推荐）</a>");
        sb.append("<a class=\"btn btn-secondary\" href=\"file:///android_asset/www/index.html\">📁 尝试 file:// 备用加载</a>");
        if (!http200) {
            sb.append("<a class=\"btn btn-warn\" href=\"javascript:stopCd()\">🛑 取消自动跳转</a>");
            sb.append("<a class=\"btn btn-gray\" href=\"javascript:startCd()\">▶️ 重新开始倒计时</a>");
        }
        sb.append("</p>");
        sb.append("<p><small style=color:#888>NoriOS v1.0.3 小月修复版 · 一加ACE5至尊版/ColorOS 16专用 · 永久密钥存 keystores_permanent/release_v102.jks</small></p>");
        if (!http200) {
            sb.append("<script>var sec=").append(countdownSec).append(";var timer=null;var box=document.getElementById('cd');function tick(){sec--;if(sec<=0){document.getElementById('cdBox').innerHTML='⏳ 已切换file备用加载，若白屏请点上方蓝色「尝试file加载」或绿色HTTP加载';return;}box.textContent=sec;timer=setTimeout(tick,1000);}function stopCd(){clearTimeout(timer);document.getElementById('cdBox').innerHTML='✅ 自动跳转已取消，请自由截图/点按钮～';}function startCd(){clearTimeout(timer);sec=").append(countdownSec).append(";box.textContent=sec;document.getElementById('cdBox').innerHTML='⏰ 自动切换倒计时：<b id=cd>').concat(String.valueOf(countdownSec)).concat("</b>秒（点「取消自动跳转」停止）';box=document.getElementById('cd');tick();}tick();</script>");
        }
        sb.append("</body></html>");
        webView.loadDataWithBaseURL("file:///android_asset/", sb.toString(), "text/html; charset=utf-8", "UTF-8", null);
    }

    /** file:// fallback —— 通过 <base> 注入 + 直接加载 file:///android_asset/www/index.html */
    private void tryFileFallback() {
        try {
            Log.i(TAG, "♻️ file fallback: file:///android_asset/www/index.html");
            webView.loadUrl("file:///android_asset/www/index.html");
        } catch (Throwable t) {
            Log.e(TAG, "fallback 加载异常", t);
        }
    }

    /** 诊断辅助：统计 assets/子目录 的资源文件数 */
    private int countAssets(String sub) {
        try { return countAssetsRecursive(getAssets(), sub); }
        catch (IOException e) { return -1; }
    }
    private static int countAssetsRecursive(android.content.res.AssetManager am, String path) throws IOException {
        int c = 0;
        String[] items = am.list(path);
        if (items == null) return 0;
        if (items.length == 0) return 1; // 空array=文件（不同系统行为不一致，做兜底）
        // 判断是否是文件：尝试open不抛就是文件
        java.io.InputStream test = null;
        try { test = am.open(path); c = 1; }
        catch (IOException maybeDir) {
            for (String it : items) c += countAssetsRecursive(am, path.equals("") ? it : path + "/" + it);
        } finally { if (test != null) try { test.close(); } catch (IOException ignored) {} }
        return c;
    }
    /** 诊断辅助：判断assets里某文件是否存在 */
    private boolean hasAsset(String path) {
        try (java.io.InputStream ignored = getAssets().open(path)) { return true; }
        catch (IOException e) { return false; }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // 基础功能
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // file:// URL 跨域权限（Live2D / Cubism 资源必须）
        if (Build.VERSION.SDK_INT >= 16) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        // 渲染 & 视口
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setTextZoom(100);

        if (Build.VERSION.SDK_INT >= 21) {
            // 宽松混合内容策略：本地 HTTP + 外部 HTTPS 资源均可混用
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            settings.setOffscreenPreRaster(true);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        if (Build.VERSION.SDK_INT >= 11) {
            settings.setEnableSmoothTransition(true);
        }

        // 硬件加速层
        if (Build.VERSION.SDK_INT >= 11) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        // WebViewClient：URL 路由 + SSL 容错 + 错误日志
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                // 本地服务器 / 文件 / data/blob → 交给 WebView 自己处理
                if ("file".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) return false;
                if ("http".equals(scheme) && "127.0.0.1".equals(uri.getHost())) return false;
                view.loadUrl(uri.toString());
                return true;
            }

            // 本地 HTTP/HTTPS 场景全部放行 SSL（ColorOS 16 可能对自签名/本地SSL卡得严）
            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "✅ onPageFinished: " + url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= 23) {
                    Log.e(TAG, "❌ WebResourceError: " + error.getDescription()
                            + " (code=" + error.getErrorCode() + ") url=" + request.getUrl());
                }
            }
        });

        // WebChromeClient：JS 弹窗 / 文件上传 / 权限 / 日志
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, cm.messageLevel() + ": " + cm.message() +
                        " -- line " + cm.lineNumber() + " @ " + cm.sourceId());
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= 21) {
                    request.grant(request.getResources());
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;

                Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File photoFile = null;
                if (takePicture.resolveActivity(getPackageManager()) != null) {
                    try {
                        photoFile = createImageFile();
                        cameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                        takePicture.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                    } catch (IOException ex) {
                        Log.e(TAG, "Photo file failed", ex);
                        takePicture = null;
                    }
                } else {
                    takePicture = null;
                }

                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
                pickIntent.setType("*/*");

                Intent[] extraIntents = takePicture != null
                        ? new Intent[]{takePicture} : new Intent[0];

                Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                chooser.putExtra(Intent.EXTRA_INTENT, pickIntent);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

                try {
                    startActivityForResult(chooser, INPUT_FILE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e(TAG, "Chooser fail", e);
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onExceededDatabaseQuota(String url, String databaseIdentifier,
                                                long currentQuota, long estimatedSize,
                                                long totalUsedQuota,
                                                WebStorage.QuotaUpdater quotaUpdater) {
                quotaUpdater.updateQuota(Math.max(estimatedSize * 2, currentQuota * 2));
            }
        });
    }

    private File createImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("IMG_" + ts + "_", ".jpg", dir);
    }

    // 运行时权限（minSdk 26，原生API即可）
    private void requestNeededPermissions() {
        String[] all = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        List<String> needed = new ArrayList<>();
        for (String p : all) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "Permission result: " + Arrays.toString(permissions));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INPUT_FILE_REQUEST_CODE && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getDataString() != null) {
                    results = new Uri[]{Uri.parse(data.getDataString())};
                } else if (cameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(cameraPhotoPath)};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onDestroy() {
        // 先关 WebView，再关 HTTP 服务器（防止 WebView 请求途中被断）
        if (webView != null) {
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        if (assetServer != null) {
            assetServer.stop();
            assetServer = null;
        }
        super.onDestroy();
    }
}
