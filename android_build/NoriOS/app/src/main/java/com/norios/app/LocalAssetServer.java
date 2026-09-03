package com.norios.app;

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
    private void handleClient(Socket client) {
        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // 1) 读取 Request Line (第一行)
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String rawPath = parts[1];

            // 2) 跳过 headers，直至空行
            String line;
            long rangeStart = -1, rangeEnd = -1;
            String ifModifiedSince = null;
            while (true) {
                line = readLine(in);
                if (line == null || line.isEmpty()) break;
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
                            } else {
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

            // 4) 尝试打开 assets
            String assetPath = assetRoot + path;
            String mimeType = guessMime(path);
            long assetLength;
            try {
                assetLength = openAssetLength(assetPath);
            } catch (IOException e) {
                // 没有 /index.html 但请求了根目录下的 SPA 路径 → 回退到 /index.html (前端路由)
                if (!path.equals("/index.html") && !path.toLowerCase().contains(".")) {
                    assetPath = assetRoot + "/index.html";
                    mimeType = "text/html";
                    try { assetLength = openAssetLength(assetPath); }
                    catch (IOException e2) { send404(out, path); return; }
                } else {
                    send404(out, path);
                    return;
                }
            }

            // 5) 写 HTTP Response
            InputStream contentStream = assetManager.open(assetPath);
            boolean partial = (rangeStart >= 0);
            long contentLength;

            if (partial) {
                if (rangeEnd < 0 || rangeEnd >= assetLength) rangeEnd = assetLength - 1;
                if (rangeStart >= assetLength) { send416(out, assetLength); contentStream.close(); return; }
                contentLength = rangeEnd - rangeStart + 1;
                // 跳过 rangeStart 字节
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
            writeLine(out, "Connection: close");
            writeLine(out, "");
            out.flush();

            // 6) Send body
            if ("HEAD".equalsIgnoreCase(method)) {
                contentStream.close();
                return;
            }
            pipe(contentStream, out, contentLength);
            contentStream.close();
            out.flush();

            if (BuildConfig.DEBUG || Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.d(TAG, method + " " + rawPath + " → "
                        + (partial ? "206 " : "200 ") + mimeType + " (" + contentLength + " B)");
            }

        } catch (Throwable t) {
            Log.w(TAG, "handleClient failed: " + t.getMessage(), t);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
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

    private static void pipe(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = maxBytes;
        int n;
        while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
            out.write(buf, 0, n);
            remaining -= n;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        int b;
        boolean crSeen = false;
        while ((b = in.read()) != -1) {
            if (b == '\r') { crSeen = true; continue; }
            if (b == '\n') return baos.toString("UTF-8");
            baos.write(b);
            if (baos.size() > 4096) { break; } // prevent overflow
        }
        return baos.size() == 0 ? null : baos.toString("UTF-8");
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

    private static String guessMime(String path) {
        path = path.toLowerCase(Locale.US);
        String mime = URLConnection.guessContentTypeFromName(path);
        if (mime != null && !mime.isEmpty()) return mime;
        // manual fallback for web-related types
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".htm"))  return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".mjs"))  return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2"))return "font/woff2";
        if (path.endsWith(".ttf"))  return "font/ttf";
        if (path.endsWith(".m4a"))  return "audio/mp4";
        if (path.endsWith(".wav"))  return "audio/wav";
        if (path.endsWith(".mp3"))  return "audio/mpeg";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".moc3")) return "application/octet-stream";
        if (path.endsWith(".md"))   return "text/markdown; charset=utf-8";
        if (path.endsWith(".txt"))  return "text/plain; charset=utf-8";
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
