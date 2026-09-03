#!/usr/bin/env python3
import os, sys, socket, threading, urllib.request, urllib.parse, time
from http.server import HTTPServer, BaseHTTPRequestHandler

ROOT = "/workspace/src"

def guessMime(path):
    path = path.lower()
    if path.endswith(".html") or path.endswith(".htm"): return "text/html; charset=utf-8"
    if path.endswith(".css"):  return "text/css; charset=utf-8"
    if path.endswith(".js") or path.endswith(".mjs"): return "application/javascript; charset=utf-8"
    if path.endswith(".json"): return "application/json; charset=utf-8"
    if path.endswith(".svg"):  return "image/svg+xml"
    if path.endswith(".png"):  return "image/png"
    if path.endswith(".jpg") or path.endswith(".jpeg"): return "image/jpeg"
    if path.endswith(".gif"):  return "image/gif"
    if path.endswith(".ico"):  return "image/x-icon"
    if path.endswith(".webp"): return "image/webp"
    if path.endswith(".woff"): return "font/woff"
    if path.endswith(".woff2"): return "font/woff2"
    if path.endswith(".ttf"):  return "font/ttf"
    if path.endswith(".otf"):  return "font/otf"
    if path.endswith(".wasm"): return "application/wasm"
    try:
        import mimetypes
        m, _ = mimetypes.guess_type(path)
        if m: return m
    except: pass
    return "application/octet-stream"

class H(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): pass
    def do_GET(self):
        p = urllib.parse.urlparse(self.path).path
        if ".." in p: p = p.replace("..","")
        if not p.startswith("/"): p="/"+p
        p = urllib.parse.unquote(p)
        if p.endswith("/"): p += "index.html"
        fp = os.path.join(ROOT, p[1:])
        if not os.path.isfile(fp):
            body = f"404 {p}".encode(); self.send_response(404)
            self.send_header("Content-Type","text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body))); self.end_headers()
            self.wfile.write(body); return
        with open(fp,"rb") as f: data = f.read()
        ct = guessMime(fp)
        self.send_response(200)
        self.send_header("Content-Type", ct)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin","*")
        self.send_header("Access-Control-Allow-Methods","GET,HEAD,OPTIONS")
        self.send_header("Cross-Origin-Opener-Policy","same-origin")
        self.send_header("Cross-Origin-Embedder-Policy","require-corp")
        self.end_headers()
        self.wfile.write(data)
        sys.stdout.write(f"   ✔ {p:50s} 200 CT={ct:40s} len={len(data):8d}\n")

def free_port(p):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("127.0.0.1", p)); s.close(); return True
    except OSError:
        try:
            import subprocess
            subprocess.run(["fuser","-k","8008/tcp"], capture_output=True)
            time.sleep(1)
        except: pass
        return False

for _ in range(3):
    if free_port(8008): break
    time.sleep(1)

srv = HTTPServer(("127.0.0.1", 8008), H)
t = threading.Thread(target=srv.serve_forever, daemon=True); t.start()
time.sleep(0.4)

URLS = [
  "http://127.0.0.1:8008/index.html",
  "http://127.0.0.1:8008/fonts.css",
  "http://127.0.0.1:8008/cubism_sdk/Core/live2dcubismcore.js",
  "http://127.0.0.1:8008/assets/index-FU-0vwSE.css",
  "http://127.0.0.1:8008/assets/index-CyHAbkO5.js",
  "http://127.0.0.1:8008/assets/i18n-DtIC1LRi.js",
  "http://127.0.0.1:8008/assets/NormalApp-Cn6agT0F.js",
  "http://127.0.0.1:8008/assets/GameScreen-CgEXO_XJ.js",
  "http://127.0.0.1:8008/assets/ChessScreen-D3ynrc3S.js",
]

print("="*90)
print("🧪 模拟手机WebView HTTP GET (" + str(len(URLS)) + " 个关键资源，完全走APK同样的guessMime白名单优先)")
print("="*90)

allOK = True
for url in URLS:
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=8) as r:
            code = r.status
            ct = r.headers.get("Content-Type","")
            body = r.read()
            name = url.rsplit("/",1)[-1] if "?" not in url else url.split("?")[0].rsplit("/",1)[-1]
            relp = urllib.parse.urlparse(url).path
            fp = os.path.join(ROOT, relp[1:])
            expct = os.path.getsize(fp) if os.path.isfile(fp) else -1
            lenok = len(body) == expct
            ctok = True
            if code!=200: ctok=False
            elif url.endswith(".js"): ctok = "javascript" in ct or "ecmascript" in ct
            elif url.endswith(".css"): ctok = "text/css" in ct
            elif url.endswith(".html"): ctok = "text/html" in ct
            ok = lenok and ctok
            if not ok: allOK = False
            print(f"  {name:30s} code={code}  len={len(body):8d}{'✅' if lenok else '❌Len('+str(expct)+')'}  CT={ct:55s} {'🎉PASS' if ok else '⛔FAIL'}")
    except Exception as e:
        print(f"  {url:80s} ❌ Exception: {e}")
        allOK = False
print("="*90)

# 额外关键检查: live2dcubismcore.js 的CT
import subprocess
print("\n🤖 额外: WebView ES Module import 校验(最容易死静默默认):")
# 直接看index.html中所有<script type=module src=...> 和 <link rel=modulepreload href=...>
with open(os.path.join(ROOT,"index.html"),"r",encoding="utf-8") as f:
    htm = f.read()
import re
mods = re.findall(r"(?:from\s+['\"]([^'\"]+)['\"]|modulepreload[^>]*href=['\"]([^'\"]+)['\"])", htm)
mods = [a for x in mods for a in x if a]
print(f"   index.html 发现 {len(mods)} 个 module import/preload 目标: {mods}")
for m in mods:
    p = m.lstrip("/")
    fullp = os.path.join(ROOT, p)
    size = os.path.getsize(fullp) if os.path.isfile(fullp) else -1
    ct = guessMime(fullp)
    ctOk = "javascript" in ct
    print(f"   {p:40s} size={size:8d} CT={ct:40s} → {'✅JS CT正确' if ctOk else '❌JS CT错误→ES Module不执行!'}")
    if not ctOk: allOK=False

print("\n" + ("🎉🎉🎉 ALL TESTS PASS! 猜测v1.0.6里HTTP+MIME都是对的! 那主人全黑=WebView渲染问题！下一步: 升级崩溃兜底页版本号+v1.0.7 新增 【WebView控制台输出 + 资源加载错误 + DOM 内容检测】到诊断页！" if allOK else "⛔⛔⛔ FAIL: 上述失败项就是主人全黑没内容的根因！立刻修！！！"))
srv.shutdown()
