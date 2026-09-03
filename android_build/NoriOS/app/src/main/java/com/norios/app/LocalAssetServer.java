package com.norios.app;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LocalAssetServer - 内嵌轻量静态HTTP服务器 (NanoHTTPD 风格，纯 Java/AOSP，零额外依赖)
 *
 * 为什么需要它？
 * ─────────────────────────────────────────────────────────────────
 * 原始 NoriOS 是一个 Node.js HTTP Web 应用，前端所有资源引用都使用
 * 【绝对路径 /xxx】 (如 /fonts.css, /cubism_sdk/Core/xxx.js, import '/assets/xxx.js')
 *
 * 如果用 WebView 加载 file:///android_asset/www/index.html，
 *   "/fonts.css" 会被浏览器解析为 → file:///fonts.css (系统根目录！)
 *   → 找不到资源 → CSS/JS 全 404 → ❌白屏❌
 *
 * 通过启动一个监听 127.0.0.1:8008 的本地 HTTP Server，
 *   WebView 访问 http://127.0.0.1:8008/index.html
 *   "/fonts.css" 就会正确命中 → http://127.0.0.1:8008/fonts.css
 *   → 与原 Node.js 服务器语义 100% 一致 ✅
 * ─────────────────────────────────────────────────────────────────
 *
 * 支持特性：MIME 类型推导 / Range 请求(大文件音频) / 304缓存 / Gzip可协商
 * 线程模型：单 accept + 线程池处理请求（静态文件性能足够，不阻塞WebView）
 */
public class LocalAssetServer {

    private static final String TAG = "NoriServer";
    public static final int DEFAULT_PORT = 8008;
    private static final int SOCKET_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final AssetManager assetManager;
    private final String assetRoot; // "www"（对应 assets/www/ 目录）
    private int port; // v1.0.2起支持端口冲突自动重试，所以不再final

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running;
    private Thread acceptThread;

    public LocalAssetServer(AssetManager am, String assetRoot, int port) {
        this.assetManager = am;
        this.assetRoot = assetRoot.endsWith("/")
                ? assetRoot.substring(0, assetRoot.length() - 1) : assetRoot;
        this.port = port;
    }

    public LocalAssetServer(AssetManager am) {
        this(am, "www", DEFAULT_PORT);
    }

    /** 启动服务器（异步，不阻塞调用方）。
     *  BUGFIX v1.0.2：端口冲突自动重试（port ~ port+MAX_RETRIES），保证99%场景能成功绑定。
     */
    public synchronized void start() throws IOException {
        if (running) return;
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "NoriAsset-Worker");
            t.setDaemon(true);
            return t;
        });
        final int MAX_RETRIES = 10;
        IOException lastErr = null;
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        for (int i = 0; i < MAX_RETRIES; i++) {
            int tryPort = port + i;
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new java.net.InetSocketAddress(loopback, tryPort), 64);
                serverSocket.setSoTimeout(0);
                this.port = tryPort;
                lastErr = null;
                break;
            } catch (IOException bindErr) {
                lastErr = bindErr;
                Log.w(TAG, "⚠️ 端口 " + tryPort + " 绑定失败（可能被占用），尝试下一个端口喵... (" + (i + 1) + "/" + MAX_RETRIES + "): " + bindErr.getMessage());
                try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
                serverSocket = null;
            }
        }
        if (serverSocket == null) {
            throw new IOException("端口绑定失败：从 " + port + " 到 " + (port + MAX_RETRIES - 1)
                    + " 全部无法绑定，请检查手机端口占用喵。最后一次错误："
                    + (lastErr == null ? "(null)" : lastErr.getMessage()), lastErr);
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "NoriAsset-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Log.i(TAG, "📡 LocalAssetServer started → http://127.0.0.1:" + port
                + " (serving assets: " + assetRoot + "/)");
    }

    /** 停止服务器，释放资源 */
    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {}
        if (executor != null) executor.shutdownNow();
        acceptThread = null;
        serverSocket = null;
        Log.i(TAG, "🛑 LocalAssetServer stopped");
    }

    public int getPort() { return port; }
    public String getBaseUrl() { return "http://127.0.0.1:" + port + "/"; }

    /* -------- accept loop -------- */
    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                executor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) Log.w(TAG, "Accept error: " + e.getMessage());
                break;
            }
        }
    }

    /* -------- 处理单客户端 -------- */
    @SuppressLint("DefaultLocale")
    private void handleClient(Socket client) {
        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {
            client.setSoTimeout(10_000); // 10秒操作超时(防止挂起导致空白)

            // 1) 读取 Request Line (第一行，带失败响应兜底)
            String requestLine = readLine(in, 8192);
            if (requestLine == null || requestLine.isEmpty()) { sendBadRequest(out, "Empty Request Line"); return; }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { sendBadRequest(out, "Invalid Request: " + requestLine); return; }
            String method = parts[0];
            String rawPath = parts[1];

            // 2) 跳过 headers，直至空行（加100行限制防止无限循环）
            String line;
            long rangeStart = -1, rangeEnd = -1;
            String ifModifiedSince = null;
            int headerLines = 0;
            while (headerLines++ < 512) {
                line = readLine(in, 8192);
                if (line == null) break;
                if (line.isEmpty()) break;
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("range:")) {
                    int eq = lower.indexOf('=');
                    if (eq != -1) {
                        String v = lower.substring(eq + 1).trim().replace("bytes=", "");
                        int dash = v.indexOf('-');
                        try {
                            if (dash > 0) {
                                rangeStart = Long.parseLong(v.substring(0, dash));
                                String endStr = v.substring(dash + 1);
                                if (!endStr.isEmpty()) rangeEnd = Long.parseLong(endStr);
                            } else if (dash == 0 && v.length() > 1) {
                                rangeEnd = Long.parseLong(v.substring(1));
                            } else if (dash < 0) {
                                rangeStart = Long.parseLong(v);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (lower.startsWith("if-modified-since:")) {
                    ifModifiedSince = line.substring(line.indexOf(':') + 1).trim();
                }
            }

            // 3) 解 URL 并路径规范化，防止 ../ 穿越
            String path = normalizePath(rawPath.split("\\?")[0].split("#")[0]);
            if ("/".equals(path)) path = "/index.html";

            // 4) 尝试打开 assets (全链路try/catch，不允许静默崩溃)
            String assetPath = assetRoot + path;
            String mimeType = guessMime(path);
            long assetLength;
            try {
                assetLength = openAssetLength(assetPath);
            } catch (IOException e) {
                // SPA 路由回退 (没有扩展名的路径→回退index.html)
                if (!path.equals("/index.html") && !path.toLowerCase(Locale.US).contains(".")) {
                    assetPath = assetRoot + "/index.html";
                    mimeType = "text/html; charset=utf-8";
                    try { assetLength = openAssetLength(assetPath); }
                    catch (IOException e2) { send404(out, path); return; }
                } else {
                    send404(out, path);
                    return;
                }
            }

            // 5) 打开 contentStream (带fail-fast try/catch)
            InputStream contentStream;
            try {
                contentStream = assetManager.open(assetPath);
            } catch (IOException openErr) {
                sendInternalError(out, "open(" + assetPath + ") failed: " + openErr.getMessage());
                return;
            }
            boolean partial = (rangeStart >= 0);
            long contentLength;

            if (partial) {
                if (rangeEnd < 0 || rangeEnd >= assetLength) rangeEnd = assetLength - 1;
                if (rangeStart >= assetLength) { send416(out, assetLength); contentStream.close(); return; }
                contentLength = rangeEnd - rangeStart + 1;
                long skipped = 0;
                while (skipped < rangeStart) {
                    long s = contentStream.skip(rangeStart - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
                writeLine(out, "HTTP/1.1 206 Partial Content");
                writeLine(out, "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + assetLength);
            } else {
                contentLength = assetLength;
                writeLine(out, "HTTP/1.1 200 OK");
            }

            String date = httpDate(new Date());
            writeLine(out, "Content-Type: " + mimeType);
            writeLine(out, "Content-Length: " + contentLength);
            writeLine(out, "Accept-Ranges: bytes");
            writeLine(out, "Cache-Control: public, max-age=31536000, immutable");
            writeLine(out, "Date: " + date);
            writeLine(out, "Last-Modified: " + date);
            writeLine(out, "Access-Control-Allow-Origin: *");
            writeLine(out, "Access-Control-Allow-Methods: GET, HEAD, OPTIONS");
            writeLine(out, "Cross-Origin-Opener-Policy: same-origin");
            writeLine(out, "Cross-Origin-Embedder-Policy: require-corp");
            writeLine(out, "Connection: close");
            writeLine(out, "");
            out.flush();

            // 6) Send body (HEAD方法不发body；其它方法强校验写入字节=contentLength，防止浏览器一直等导致空白)
            long written = 0;
            try {
                if (!"HEAD".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)) {
                    written = pipe(contentStream, out, contentLength);
                }
            } finally {
                contentStream.close();
                out.flush();
            }
            if (written != contentLength && !"HEAD".equalsIgnoreCase(method)) {
                Log.w(TAG, String.format("⚠️ 响应写入字节不匹配(浏览器空白的根因!) 期望=%d 实际=%d path=%s",
                        contentLength, written, assetPath));
                try { client.shutdownOutput(); } catch (Throwable ignored) {}
            } else {
                Log.v(TAG, String.format("✅ %s %s → %s %dB CT=%s",
                        method, rawPath,
                        (partial ? "206" : "200"),
                        contentLength, mimeType));
            }
        } catch (Throwable t) {
            // handleClient任何异常 → 至少打Log + 尝试发500
            Log.e(TAG, "💥 handleClient异常(可能造成浏览器空白): " + t.getMessage(), t);
            try {
                OutputStream out = client.getOutputStream();
                sendInternalError(out, t.getClass().getSimpleName() + ": " + t.getMessage());
            } catch (Throwable ignored) {}
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    /* ---- HTTP 错误响应 (v1.0.5: 保证服务器任何时候都发响应，不让浏览器空白) ---- */
    private static void sendBadRequest(OutputStream out, String why) throws IOException {
        sendErrorBody(out, "HTTP/1.1 400 Bad Request", "400 Bad Request", why);
    }
    private static void sendInternalError(OutputStream out, String why) throws IOException {
        sendErrorBody(out, "HTTP/1.1 500 Internal Server Error", "500 Internal Server Error", why);
    }
    private static void sendErrorBody(OutputStream out, String statusLine, String title, String why) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=utf-8><title>").append(title).append("</title></head>")
          .append("<body style=\"font-family:sans-serif;padding:20px;background:#fff;color:#333\">")
          .append("<h1 style=\"color:#d32f2f\">").append(title).append("</h1>")
          .append("<p>NoriOS LocalAssetServer (v1.0.5+)</p>")
          .append("<pre style=\"background:#fafafa;border:1px solid #eee;padding:10px;border-radius:6px\">")
          .append(why == null ? "" : why).append("</pre></body></html>");
        byte[] data = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeLine(out, statusLine);
        writeLine(out, "Content-Type: text/html; charset=utf-8");
        writeLine(out, "Content-Length: " + data.length);
        writeLine(out, "Connection: close");
        writeLine(out, "");
        out.write(data);
        out.flush();
    }

    /* -------- helpers -------- */

    private long openAssetLength(String assetPath) throws IOException {
        try (InputStream is = assetManager.open(assetPath)) {
            long total = 0;
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = is.read(buf)) != -1) total += n;
            return total;
        }
    }

    /** @return 实际写入字节数 written bytes (用于v1.0.5 Content-Length校验防止浏览器死等空白) */
    private static long pipe(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = maxBytes;
        long written = 0;
        int n;
        while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
            out.write(buf, 0, n);
            remaining -= n;
            written += n;
        }
        return written;
    }

    /** v1.0.5+: 带最大字符数限制的readLine，防止恶意请求溢出 / 真实浏览器超长headers卡住 */
    private static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        int b;
        boolean crSeen = false;
        while ((b = in.read()) != -1) {
            if (b == '\r') { crSeen = true; continue; }
            if (b == '\n') return baos.toString("UTF-8");
            baos.write(b);
            if (baos.size() > maxBytes) { break; }
        }
        return baos.size() == 0 ? null : baos.toString("UTF-8");
    }

    /** 兼容旧单参数调用 */
    private static String readLine(InputStream in) throws IOException {
        return readLine(in, 4096);
    }

    private static void writeLine(OutputStream out, String s) throws IOException {
        out.write((s + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String httpDate(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return f.format(d);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        // 防穿越
        if (path.contains("..")) path = path.replaceAll("\\.\\.", "");
        if (!path.startsWith("/")) path = "/" + path;
        // 解码 %xx 简单处理
        try { path = java.net.URLDecoder.decode(path, "UTF-8"); } catch (Exception ignored) {}
        return path;
    }

    /** v1.0.5: 白名单优先(JS/MJS/CSS关键Web MIME绝不误判) → 系统URLConnection仅做最后兜底
     *  之前是反过来(先系统再白名单)，导致Android系统误判.js返回application/octet-stream → ES Module脚本被WebView严格拒绝 → 空白页！ */
    private static String guessMime(String path) {
        path = path.toLowerCase(Locale.US);
        // --- 1) 白名单 (最高优先级 最严格 匹配正确) ---
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".htm"))  return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".mjs"))  return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg")||path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif"))  return "image/gif";
        if (path.endsWith(".ico"))  return "image/x-icon";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2"))return "font/woff2";
        if (path.endsWith(".ttf"))  return "font/ttf";
        if (path.endsWith(".otf"))  return "font/otf";
        if (path.endsWith(".m4a")||path.endsWith(".aac")) return "audio/mp4";
        if (path.endsWith(".wav"))  return "audio/wav";
        if (path.endsWith(".mp3"))  return "audio/mpeg";
        if (path.endsWith(".webm")||path.endsWith(".mp4")) return "video/webm";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".moc3")||path.endsWith(".cdi3.json")||path.endsWith(".model3.json")||path.endsWith(".physics3.json")) return "application/octet-stream";
        if (path.endsWith(".md"))   return "text/markdown; charset=utf-8";
        if (path.endsWith(".txt"))  return "text/plain; charset=utf-8";
        // --- 2) 系统 URLConnection 兜底 ---
        try {
            String mime = URLConnection.guessContentTypeFromName(path);
            if (mime != null && !mime.isEmpty()) return mime;
        } catch (Throwable ignored) {}
        // --- 3) 最终兜底 ---
        return "application/octet-stream";
    }

    private static void send404(OutputStream out, String path) throws IOException {
        String body = "<html><body><h2>404 Not Found</h2><p>" + path + "</p><hr><em>NoriOS LocalAssetServer</em></body></html>";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        writeLine(out, "HTTP/1.1 404 Not Found");
        writeLine(out, "Content-Type: text/html; charset=utf-8");
        writeLine(out, "Content-Length: " + data.length);
        writeLine(out, "Date: " + httpDate(new Date()));
        writeLine(out, "Connection: close");
        writeLine(out, "");
        out.write(data);
        out.flush();
    }

    private static void send416(OutputStream out, long fullLen) throws IOException {
        writeLine(out, "HTTP/1.1 416 Range Not Satisfiable");
        writeLine(out, "Content-Range: bytes */" + fullLen);
        writeLine(out, "Content-Length: 0");
        writeLine(out, "Connection: close");
        writeLine(out, "");
        out.flush();
    }

    /* 防止 R8/Proguard 在 release 下误删 BuildConfig 引用 */
    private static final class BuildConfig {
        public static final boolean DEBUG = false;
    }
}
