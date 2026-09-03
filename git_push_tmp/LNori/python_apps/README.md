# NoriOS Python App Framework

## 概述
这是一个基于 Python 的应用框架，为 NoriOS 提供真正的后端支持。每个 App 都是一个独立的插件，支持热重载 (HMR)。

## 特性

### 🚀 核心功能
- **插件化架构**: 每个 App 都是独立的可插拔模块
- **热模块替换 (HMR)**: 源代码变化时自动重新加载应用
- **WebSocket 通信**: 前后端实时双向通信
- **终端模拟器**: 真正的前后端对接，可执行系统命令

### 📦 内置应用
1. **Terminal (终端)** - 命令行终端模拟器，支持真实命令执行
2. **Browser (浏览器)** - 网页浏览器
3. **Files (文件)** - 文件管理器
4. **Mail (邮件)** - 电子邮件客户端
5. **Messenger (通讯)** - 即时通讯工具
6. **Preview (预览)** - 文件预览器
7. **Idle (闲逛)** - 空闲状态
8. **Login (登录)** - 用户登录
9. **Markdown** - Markdown 编辑器
10. **CakeDuel (蛋糕对决)** - 卡牌对战游戏
11. **Pictionary (你画我猜)** - 绘画猜词游戏

## 安装

```bash
pip install websockets
```

## 启动服务

```bash
cd /workspace/python_apps
python3 framework.py
```

服务将在 `ws://0.0.0.0:8765` 启动 WebSocket 服务器。

## API 使用

### WebSocket 消息格式

#### 请求
```json
{
  "app_id": "terminal",
  "action": "execute",
  "params": {
    "command": "ls",
    "args": ["/workspace"]
  }
}
```

#### 响应
```json
{
  "type": "response",
  "app_id": "terminal",
  "action": "execute",
  "result": {
    "output": "file1.txt\nfile2.txt\n"
  }
}
```

### HMR 通知
当检测到源代码变化时，会自动推送重载通知：
```json
{
  "type": "reload",
  "timestamp": 1234567890.123,
  "changed_files": ["/path/to/changed/file.py"],
  "action": "full_reload"
}
```

## Terminal 应用支持的命令

| 命令 | 描述 |
|------|------|
| `help` | 显示帮助信息 |
| `ls [path]` | 列出目录内容 |
| `cd <path>` | 切换目录 |
| `pwd` | 显示当前目录 |
| `cat <path>` | 查看文件内容 |
| `echo <text>` | 输出文本 |
| `clear` | 清屏 |
| `whoami` | 显示当前用户 |
| `date` | 显示日期时间 |
| `uname [-a]` | 显示系统信息 |
| `python <code>` | 运行 Python 代码 |
| `exit` | 退出终端 |

## 开发新应用

### 1. 创建应用类

```python
from framework import BaseApp, PluginContext

class MyCustomApp(BaseApp):
    async def handle_request(self, action: str, params: dict) -> dict:
        if action == "do_something":
            return {"result": "done"}
        return {"error": f"Unknown action: {action}"}
    
    async def on_mount(self):
        print("MyCustomApp mounted")
    
    async def on_unmount(self):
        print("MyCustomApp unmounted")
```

### 2. 注册应用

在 `framework.py` 的 `discover_apps()` 方法中添加：

```python
builtin_apps = [
    # ... existing apps ...
    (MyCustomApp, "myapp", "我的应用", "描述信息"),
]
```

### 3. 前端调用

```javascript
import { terminalClient } from './terminal_client.js';

await terminalClient.connect();
const result = await terminalClient.sendRequest('myapp', 'do_something', {});
console.log(result);
```

## HMR 工作原理

1. **文件监控**: HMRPlugin 每秒扫描 `apps/` 目录
2. **变化检测**: 比较文件修改时间戳
3. **应用重载**: 自动卸载并重新加载受影响的应用
4. **前端通知**: 通过 WebSocket 推送重载事件到所有连接的客户端

## 项目结构

```
python_apps/
├── framework.py          # 主框架
├── terminal_client.js    # 前端客户端库
├── plugins/              # 第三方插件目录
└── apps/                 # 应用目录 (动态加载)
```

## 测试

```bash
python3 -c "
import asyncio
import websockets
import json

async def test():
    async with websockets.connect('ws://localhost:8765') as ws:
        await ws.send(json.dumps({
            'app_id': 'terminal',
            'action': 'execute',
            'params': {'command': 'help', 'args': []}
        }))
        response = await ws.recv()
        print(json.loads(response))

asyncio.run(test())
"
```

## 注意事项

- 确保端口 8765 未被占用
- 生产环境请添加适当的认证和授权机制
- 终端命令执行已做基本安全限制，但仍需谨慎使用
