#!/usr/bin/env python3
"""
NoriOS Python App Framework
每个 App 都是一个独立的插件，支持热重载 (HMR)
"""

import os
import sys
import json
import asyncio
import websockets
from pathlib import Path
from typing import Dict, Any, Optional, Callable, List
from dataclasses import dataclass, field
import importlib.util
import traceback
import hashlib
import time

# 基础目录
BASE_DIR = Path(__file__).parent
APPS_DIR = BASE_DIR / "apps"
PLUGINS_DIR = BASE_DIR / "plugins"

@dataclass
class AppInfo:
    """应用信息"""
    id: str
    name: str
    description: str = ""
    version: str = "1.0.0"
    module_path: Optional[Path] = None
    is_active: bool = True
    last_modified: float = 0.0
    
@dataclass
class PluginContext:
    """插件上下文"""
    app_id: str
    ws_clients: set = field(default_factory=set)
    config: Dict[str, Any] = field(default_factory=dict)

class HMRPlugin:
    """
    热模块替换插件 (Hot Module Replacement)
    监控源代码变化，自动重新加载应用
    """
    
    def __init__(self, framework: "PythonAppFramework"):
        self.framework = framework
        self.watchers: Dict[str, float] = {}  # file_path -> last_modified
        self.clients: set = set()  # WebSocket clients
        
    def scan_directory(self, directory: Path) -> Dict[str, float]:
        """扫描目录获取所有文件的修改时间"""
        files = {}
        if directory.exists():
            for f in directory.rglob("*"):
                if f.is_file() and f.suffix in ['.py', '.html', '.css', '.js', '.json']:
                    files[str(f)] = f.stat().st_mtime
        return files
    
    async def check_changes(self) -> List[str]:
        """检查是否有文件发生变化"""
        changed = []
        current_files = self.scan_directory(APPS_DIR)
        
        for file_path, mtime in current_files.items():
            if file_path not in self.watchers or self.watchers[file_path] != mtime:
                changed.append(file_path)
                self.watchers[file_path] = mtime
        
        # 检查删除的文件
        for file_path in list(self.watchers.keys()):
            if file_path not in current_files:
                changed.append(file_path)
                del self.watchers[file_path]
        
        return changed
    
    async def notify_clients(self, event_type: str, data: Dict[str, Any]):
        """通知所有客户端"""
        message = json.dumps({
            "type": event_type,
            "timestamp": time.time(),
            **data
        })
        
        disconnected = set()
        for client in self.clients:
            try:
                await client.send(message)
            except:
                disconnected.add(client)
        
        self.clients -= disconnected
    
    async def watch_loop(self):
        """监控循环"""
        while True:
            try:
                changed = await self.check_changes()
                if changed:
                    print(f"[HMR] 检测到变化：{changed}")
                    
                    # 重新加载受影响的应用
                    for file_path in changed:
                        app_id = self._find_affected_app(file_path)
                        if app_id:
                            await self.framework.reload_app(app_id)
                    
                    # 通知前端
                    await self.notify_clients("reload", {
                        "changed_files": changed,
                        "action": "full_reload"
                    })
                
                await asyncio.sleep(1.0)  # 每秒检查一次
            except Exception as e:
                print(f"[HMR Error] {e}")
                await asyncio.sleep(2.0)
    
    def _find_affected_app(self, file_path: str) -> Optional[str]:
        """根据文件路径找到受影响的应用"""
        file_path = Path(file_path)
        for app_id, app_info in self.framework.apps.items():
            if app_info.module_path and str(app_info.module_path.parent) in file_path:
                return app_id
        return None


class BaseApp:
    """应用基类 - 所有 App 必须继承此类"""
    
    def __init__(self, context: PluginContext):
        self.context = context
        self.app_id = context.app_id
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """处理请求 - 子类必须实现"""
        raise NotImplementedError
    
    async def on_mount(self):
        """应用挂载时调用"""
        pass
    
    async def on_unmount(self):
        """应用卸载时调用"""
        pass
    
    async def on_command(self, command: str, args: List[str]) -> Dict[str, Any]:
        """处理终端命令 - 用于 Terminal 应用"""
        return {"error": f"Unknown command: {command}"}


class TerminalApp(BaseApp):
    """终端应用 - 实现真正的前后端对接"""
    
    def __init__(self, context: PluginContext):
        super().__init__(context)
        self.cwd = "/"
        self.history: List[str] = []
        self.processes: Dict[int, asyncio.Task] = {}
        
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """处理终端请求"""
        handlers = {
            "execute": self.execute_command,
            "list_dir": self.list_directory,
            "read_file": self.read_file,
            "write_file": self.write_file,
            "change_dir": self.change_dir,
            "getcwd": self.get_cwd,
        }
        
        handler = handlers.get(action)
        if not handler:
            return {"error": f"Unknown action: {action}"}
        
        try:
            return await handler(params)
        except Exception as e:
            return {"error": str(e), "traceback": traceback.format_exc()}
    
    async def execute_command(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """执行命令"""
        command = params.get("command", "")
        args = params.get("args", [])
        
        if not command:
            return {"error": "Empty command"}
        
        # 添加到历史
        full_command = f"{command} {' '.join(args)}".strip()
        if full_command:
            self.history.append(full_command)
        
        # 内置命令
        builtins = {
            "help": self.cmd_help,
            "ls": self.cmd_ls,
            "cd": self.cmd_cd,
            "pwd": self.cmd_pwd,
            "cat": self.cmd_cat,
            "echo": self.cmd_echo,
            "clear": self.cmd_clear,
            "whoami": self.cmd_whoami,
            "date": self.cmd_date,
            "uname": self.cmd_uname,
            "python": self.cmd_python,
            "exit": self.cmd_exit,
        }
        
        cmd_lower = command.lower()
        if cmd_lower in builtins:
            return await builtins[cmd_lower](args)
        
        # 尝试执行系统命令
        return await self.execute_system_command(command, args)
    
    async def execute_system_command(self, command: str, args: List[str]) -> Dict[str, Any]:
        """执行系统命令"""
        try:
            full_cmd = [command] + args
            proc = await asyncio.create_subprocess_exec(
                *full_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=self.cwd if self.cwd != "/" else "/"
            )
            
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=30.0)
            
            return {
                "stdout": stdout.decode('utf-8', errors='replace'),
                "stderr": stderr.decode('utf-8', errors='replace'),
                "returncode": proc.returncode
            }
        except FileNotFoundError:
            return {"error": f"Command not found: {command}"}
        except asyncio.TimeoutError:
            return {"error": "Command timed out"}
        except Exception as e:
            return {"error": str(e)}
    
    # === 内置命令实现 ===
    
    async def cmd_help(self, args: List[str]) -> Dict[str, Any]:
        """帮助命令"""
        commands = [
            "help     - 显示帮助信息",
            "ls       - 列出目录内容",
            "cd       - 切换目录",
            "pwd      - 显示当前目录",
            "cat      - 查看文件内容",
            "echo     - 输出文本",
            "clear    - 清屏",
            "whoami   - 显示当前用户",
            "date     - 显示日期时间",
            "uname    - 显示系统信息",
            "python   - 运行 Python 代码",
            "exit     - 退出终端",
        ]
        return {"output": "\n".join(commands)}
    
    async def cmd_ls(self, args: List[str]) -> Dict[str, Any]:
        """列出目录"""
        path = args[0] if args else self.cwd
        if not path.startswith("/"):
            path = os.path.join(self.cwd, path) if self.cwd != "/" else "/" + path
        
        try:
            items = os.listdir(path)
            result = []
            for item in sorted(items):
                full_path = os.path.join(path, item)
                prefix = "d" if os.path.isdir(full_path) else " "
                result.append(f"{prefix} {item}")
            return {"output": "\n".join(result) if result else "(empty directory)"}
        except Exception as e:
            return {"error": f"ls: cannot access '{path}': {e}"}
    
    async def cmd_cd(self, args: List[str]) -> Dict[str, Any]:
        """切换目录"""
        if not args:
            target = "/home"
        else:
            target = args[0]
        
        if not target.startswith("/"):
            target = os.path.join(self.cwd, target) if self.cwd != "/" else "/" + target
        
        try:
            target = os.path.normpath(target)
            if os.path.isdir(target):
                self.cwd = target
                return {"output": f"Changed directory to {self.cwd}"}
            else:
                return {"error": f"cd: no such directory: {target}"}
        except Exception as e:
            return {"error": f"cd: {e}"}
    
    async def cmd_pwd(self, args: List[str]) -> Dict[str, Any]:
        """显示当前目录"""
        return {"output": self.cwd}
    
    async def cmd_cat(self, args: List[str]) -> Dict[str, Any]:
        """查看文件内容"""
        if not args:
            return {"error": "cat: missing file operand"}
        
        path = args[0]
        if not path.startswith("/"):
            path = os.path.join(self.cwd, path) if self.cwd != "/" else "/" + path
        
        try:
            with open(path, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()
            return {"output": content}
        except Exception as e:
            return {"error": f"cat: {path}: {e}"}
    
    async def cmd_echo(self, args: List[str]) -> Dict[str, Any]:
        """输出文本"""
        return {"output": " ".join(args)}
    
    async def cmd_clear(self, args: List[str]) -> Dict[str, Any]:
        """清屏"""
        return {"action": "clear_screen"}
    
    async def cmd_whoami(self, args: List[str]) -> Dict[str, Any]:
        """显示当前用户"""
        return {"output": "nori_user"}
    
    async def cmd_date(self, args: List[str]) -> Dict[str, Any]:
        """显示日期"""
        from datetime import datetime
        return {"output": datetime.now().strftime("%a %b %d %H:%M:%S %Y")}
    
    async def cmd_uname(self, args: List[str]) -> Dict[str, Any]:
        """显示系统信息"""
        import platform
        if "-a" in args:
            return {"output": platform.platform()}
        return {"output": platform.system()}
    
    async def cmd_python(self, args: List[str]) -> Dict[str, Any]:
        """运行 Python 代码"""
        if not args:
            return {"error": "python: missing code argument"}
        
        code = " ".join(args)
        try:
            # 创建一个安全的执行环境
            local_vars = {"print": lambda x: x, "result": None}
            exec(code, {"__builtins__": __builtins__}, local_vars)
            result = local_vars.get("result", "")
            return {"output": str(result) if result else "Executed successfully"}
        except Exception as e:
            return {"error": f"Python error: {e}"}
    
    async def cmd_exit(self, args: List[str]) -> Dict[str, Any]:
        """退出"""
        return {"action": "close_terminal"}
    
    async def list_directory(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """API: 列出目录"""
        path = params.get("path", self.cwd)
        return await self.cmd_ls([path])
    
    async def read_file(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """API: 读取文件"""
        path = params.get("path")
        if not path:
            return {"error": "No path specified"}
        return await self.cmd_cat([path])
    
    async def write_file(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """API: 写入文件"""
        path = params.get("path")
        content = params.get("content", "")
        
        if not path:
            return {"error": "No path specified"}
        
        try:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            return {"success": True, "bytes_written": len(content)}
        except Exception as e:
            return {"error": str(e)}
    
    async def change_dir(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """API: 切换目录"""
        path = params.get("path")
        if not path:
            return {"error": "No path specified"}
        return await self.cmd_cd([path])
    
    async def get_cwd(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """API: 获取当前目录"""
        return {"cwd": self.cwd}


class BrowserApp(BaseApp):
    """浏览器应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "navigate":
            url = params.get("url", "")
            return {"success": True, "url": url}
        elif action == "get_bookmarks":
            return {"bookmarks": []}
        return {"error": f"Unknown action: {action}"}


class FilesApp(BaseApp):
    """文件管理器应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "list":
            path = params.get("path", "/")
            try:
                items = []
                for item in os.listdir(path):
                    full_path = os.path.join(path, item)
                    items.append({
                        "name": item,
                        "type": "dir" if os.path.isdir(full_path) else "file",
                        "size": os.path.getsize(full_path) if os.path.isfile(full_path) else 0
                    })
                return {"items": items}
            except Exception as e:
                return {"error": str(e)}
        elif action == "read":
            path = params.get("path")
            if path and os.path.isfile(path):
                with open(path, 'r', encoding='utf-8', errors='replace') as f:
                    return {"content": f.read()}
            return {"error": "File not found"}
        return {"error": f"Unknown action: {action}"}


class MailApp(BaseApp):
    """邮件应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "get_inbox":
            return {"emails": []}
        elif action == "send":
            return {"success": True, "message_id": "mock_123"}
        return {"error": f"Unknown action: {action}"}


class MessengerApp(BaseApp):
    """通讯应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "get_contacts":
            return {"contacts": []}
        elif action == "send_message":
            return {"success": True}
        return {"error": f"Unknown action: {action}"}


class PreviewApp(BaseApp):
    """预览应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "preview":
            file_id = params.get("file_id")
            return {"file_id": file_id, "ready": True}
        return {"error": f"Unknown action: {action}"}


class IdleApp(BaseApp):
    """闲逛应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        return {"status": "idle"}


class LoginApp(BaseApp):
    """登录应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "login":
            username = params.get("username")
            password = params.get("password")
            # Mock login
            return {"success": True, "user": username or "guest"}
        return {"error": f"Unknown action: {action}"}


class MarkdownApp(BaseApp):
    """Markdown 编辑器应用"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "save":
            content = params.get("content", "")
            return {"success": True, "saved_bytes": len(content)}
        elif action == "load":
            return {"content": "# Welcome to Markdown\n\nStart editing..."}
        return {"error": f"Unknown action: {action}"}


class CakeDuelApp(BaseApp):
    """蛋糕对决游戏"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "start_game":
            return {"game_id": "cake_123", "status": "started"}
        elif action == "play_card":
            return {"success": True}
        return {"error": f"Unknown action: {action}"}


class DockApp(BaseApp):
    """Dock 停靠栏应用 - 管理系统应用图标和启动器"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "get_apps":
            # 返回所有可用应用列表
            return {
                "apps": [
                    {"id": "terminal", "name": "终端", "icon": "terminal"},
                    {"id": "browser", "name": "浏览器", "icon": "browser"},
                    {"id": "files", "name": "文件", "icon": "files"},
                    {"id": "mail", "name": "邮件", "icon": "mail"},
                    {"id": "messenger", "name": "通讯", "icon": "messenger"},
                    {"id": "preview", "name": "预览", "icon": "preview"},
                    {"id": "markdown", "name": "Markdown", "icon": "markdown"},
                    {"id": "cakeduel", "name": "蛋糕对决", "icon": "game"},
                    {"id": "pictionary", "name": "你画我猜", "icon": "paint"},
                ]
            }
        elif action == "launch_app":
            app_id = params.get("app_id")
            return {"success": True, "launched": app_id}
        elif action == "close_app":
            app_id = params.get("app_id")
            return {"success": True, "closed": app_id}
        return {"error": f"Unknown action: {action}"}


class PictionaryApp(BaseApp):
    """你画我猜游戏"""
    
    async def handle_request(self, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if action == "start_game":
            return {"game_id": "piction_456", "word": "tree"}
        elif action == "submit_guess":
            guess = params.get("guess", "")
            return {"correct": guess.lower() == "tree"}
        return {"error": f"Unknown action: {action}"}


class PythonAppFramework:
    """
    Python 应用框架
    管理所有应用的加载、卸载和通信
    """
    
    def __init__(self):
        self.apps: Dict[str, AppInfo] = {}
        self.app_instances: Dict[str, BaseApp] = {}
        self.hmr_plugin: Optional[HMRPlugin] = None
        self.ws_server: Optional[websockets.WebSocketServerProtocol] = None
        self.running = False
        
    def register_app(self, app_class: type, app_id: str, name: str, description: str = ""):
        """注册应用"""
        app_info = AppInfo(
            id=app_id,
            name=name,
            description=description,
        )
        self.apps[app_id] = app_info
        print(f"[Framework] 注册应用：{app_id} - {name}")
    
    def discover_apps(self):
        """发现并注册所有内置应用"""
        builtin_apps = [
            (TerminalApp, "terminal", "终端", "命令行终端模拟器"),
            (BrowserApp, "browser", "浏览器", "网页浏览器"),
            (FilesApp, "files", "文件", "文件管理器"),
            (MailApp, "mail", "邮件", "电子邮件客户端"),
            (MessengerApp, "messenger", "通讯", "即时通讯工具"),
            (PreviewApp, "preview", "预览", "文件预览器"),
            (IdleApp, "idle", "闲逛", "空闲状态"),
            (LoginApp, "login", "登录", "用户登录"),
            (MarkdownApp, "markdown", "Markdown", "Markdown 编辑器"),
            (CakeDuelApp, "cakeduel", "蛋糕对决", "卡牌对战游戏"),
            (DockApp, "dock", "Dock", "应用停靠栏"),
            (PictionaryApp, "pictionary", "你画我猜", "绘画猜词游戏"),
        ]
        
        for app_class, app_id, name, desc in builtin_apps:
            self.register_app(app_class, app_id, name, desc)
    
    async def load_app(self, app_id: str) -> bool:
        """加载应用"""
        if app_id not in self.apps:
            print(f"[Framework] 应用不存在：{app_id}")
            return False
        
        app_info = self.apps[app_id]
        context = PluginContext(app_id=app_id)
        
        # 根据 app_id 找到对应的类
        app_classes = {
            "terminal": TerminalApp,
            "browser": BrowserApp,
            "files": FilesApp,
            "mail": MailApp,
            "messenger": MessengerApp,
            "preview": PreviewApp,
            "idle": IdleApp,
            "login": LoginApp,
            "markdown": MarkdownApp,
            "cakeduel": CakeDuelApp,
            "dock": DockApp,
            "pictionary": PictionaryApp,
        }
        
        app_class = app_classes.get(app_id)
        if not app_class:
            print(f"[Framework] 未知的类：{app_id}")
            return False
        
        try:
            instance = app_class(context)
            self.app_instances[app_id] = instance
            await instance.on_mount()
            print(f"[Framework] 加载应用成功：{app_id}")
            return True
        except Exception as e:
            print(f"[Framework] 加载应用失败 {app_id}: {e}")
            traceback.print_exc()
            return False
    
    async def unload_app(self, app_id: str) -> bool:
        """卸载应用"""
        if app_id not in self.app_instances:
            return False
        
        try:
            instance = self.app_instances[app_id]
            await instance.on_unmount()
            del self.app_instances[app_id]
            print(f"[Framework] 卸载应用：{app_id}")
            return True
        except Exception as e:
            print(f"[Framework] 卸载应用失败 {app_id}: {e}")
            return False
    
    async def reload_app(self, app_id: str) -> bool:
        """重新加载应用 (HMR)"""
        print(f"[Framework] 重新加载应用：{app_id}")
        await self.unload_app(app_id)
        return await self.load_app(app_id)
    
    async def handle_app_request(self, app_id: str, action: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """处理应用请求"""
        if app_id not in self.app_instances:
            await self.load_app(app_id)
        
        if app_id not in self.app_instances:
            return {"error": f"App not loaded: {app_id}"}
        
        instance = self.app_instances[app_id]
        return await instance.handle_request(action, params)
    
    async def start_hmr(self):
        """启动 HMR 监控"""
        self.hmr_plugin = HMRPlugin(self)
        print("[HMR] 启动监控...")
        asyncio.create_task(self.hmr_plugin.watch_loop())
    
    def get_app_list(self) -> List[Dict[str, Any]]:
        """获取应用列表"""
        return [
            {
                "id": info.id,
                "name": info.name,
                "description": info.description,
                "version": info.version,
                "active": info.is_active,
                "loaded": info.id in self.app_instances
            }
            for info in self.apps.values()
        ]


# 全局框架实例
framework = PythonAppFramework()


async def websocket_handler(websocket):
    """WebSocket 处理器"""
    print(f"[WS] 新连接：{websocket.remote_address}")
    
    # 注册到 HMR
    if framework.hmr_plugin:
        framework.hmr_plugin.clients.add(websocket)
    
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                app_id = data.get("app_id")
                action = data.get("action")
                params = data.get("params", {})
                
                if app_id and action:
                    result = await framework.handle_app_request(app_id, action, params)
                    response = {
                        "type": "response",
                        "app_id": app_id,
                        "action": action,
                        "result": result
                    }
                    await websocket.send(json.dumps(response))
                else:
                    await websocket.send(json.dumps({
                        "type": "error",
                        "error": "Missing app_id or action"
                    }))
            except json.JSONDecodeError:
                await websocket.send(json.dumps({
                    "type": "error",
                    "error": "Invalid JSON"
                }))
            except Exception as e:
                await websocket.send(json.dumps({
                    "type": "error",
                    "error": str(e)
                }))
    finally:
        if framework.hmr_plugin:
            framework.hmr_plugin.clients.discard(websocket)
        print(f"[WS] 断开连接：{websocket.remote_address}")


async def main():
    """主函数"""
    print("=" * 50)
    print("NoriOS Python App Framework")
    print("=" * 50)
    
    # 发现并注册应用
    framework.discover_apps()
    
    # 启动 HMR
    await framework.start_hmr()
    
    # 启动 WebSocket 服务器
    host = "0.0.0.0"
    port = 8765
    
    print(f"\n[Server] 启动 WebSocket 服务器 ws://{host}:{port}")
    
    async with websockets.serve(websocket_handler, host, port):
        print("[Server] 等待连接...")
        print("\n可用应用:")
        for app in framework.get_app_list():
            status = "✓" if app["loaded"] else "○"
            print(f"  {status} {app['id']}: {app['name']}")
        print("\n按 Ctrl+C 停止")
        
        # 保持运行
        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            pass


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[Server] 已停止")
