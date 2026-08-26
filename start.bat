@echo off
REM NoriOS Web 启动脚本 (Windows)
REM 自动检测依赖、安装依赖并启动服务

setlocal enabledelayedexpansion

echo ========================================
echo   NoriOS Web - 启动脚本 (Windows)
echo ========================================
echo.

REM 检测 Node.js
echo [1/6] 检测 Node.js...
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Node.js 未安装，请先安装 Node.js (>=18)
    exit /b 1
)
for /f "tokens=*" %%i in ('node -v') do set NODE_VERSION=%%i
echo [OK] Node.js 已安装：!NODE_VERSION!

REM 检测 npm
echo [2/6] 检测 npm...
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] npm 未安装
    exit /b 1
)
for /f "tokens=*" %%i in ('npm -v') do set NPM_VERSION=%%i
echo [OK] npm 已安装：!NPM_VERSION!

REM 检测 Python3
echo [3/6] 检测 Python3...
where python >nul 2>&1
if %errorlevel% neq 0 (
    where python3 >nul 2>&1
    if %errorlevel% neq 0 (
        echo [ERROR] Python3 未安装，请先安装 Python3 (>=3.8)
        exit /b 1
    )
    set PYTHON_CMD=python3
) else (
    set PYTHON_CMD=python
)
for /f "tokens=*" %%i in ('!PYTHON_CMD! --version') do set PYTHON_VERSION=%%i
echo [OK] Python3 已安装：!PYTHON_VERSION!

REM 检测 websockets 包
echo [4/6] 检测 Python websockets 包...
!PYTHON_CMD! -c "import websockets" 2>nul
if %errorlevel% neq 0 (
    echo [WARN] websockets 未安装，正在安装...
    !PYTHON_CMD! -m pip install websockets
)
echo [OK] websockets 已安装

REM 进入项目目录
cd /d "%~dp0"

REM 检测 node_modules
echo [5/6] 检测 Node.js 依赖...
if not exist "node_modules\" (
    echo [WARN] node_modules 不存在，正在安装依赖...
    call npm install
) else (
    echo [OK] node_modules 已存在
)

REM 验证关键依赖
echo [INFO] 验证关键依赖...
if not exist "node_modules\astro\" (
    echo [WARN] 缺少依赖 astro，重新安装...
    call npm install
)
echo [OK] 所有 Node.js 依赖已就绪

REM 显示依赖列表
echo.
echo [INFO] 已安装的 Node.js 包:
call npm ls --depth=0 2>nul | findstr /R "^├─ @ ^nori"
echo.

REM 创建日志目录
if not exist "logs\" mkdir logs

REM 启动 Python 后端
echo [6/6] 启动 Python 后端框架...
tasklist /FI "IMAGENAME eq python.exe" /FI "WINDOWTITLE eq *framework.py*" 2>nul | find "python.exe" >nul
if %errorlevel% equ 0 (
    echo [WARN] Python 后端已在运行中
) else (
    cd python_apps
    start /B cmd /c "!PYTHON_CMD! framework.py > ..\logs\python_backend.log 2>&1"
    cd ..
    echo [OK] Python 后端已启动
    timeout /t 2 /nobreak >nul
)

REM 检查后端是否正常运行
tasklist /FI "IMAGENAME eq python.exe" 2>nul | find "python.exe" >nul
if %errorlevel% equ 0 (
    echo [OK] Python WebSocket 服务运行中 (ws://0.0.0.0:8765)
) else (
    echo [ERROR] Python 后端启动失败，请查看 logs\python_backend.log
    exit /b 1
)

REM 启动 Astro 开发服务器
echo.
echo [INFO] 启动 Astro 开发服务器...
tasklist /FI "IMAGENAME eq node.exe" 2>nul | find "node.exe" >nul
if %errorlevel% equ 0 (
    echo [WARN] Astro 服务器已在运行中
) else (
    start /B cmd /c "npm run dev > logs\astro_dev.log 2>&1"
    echo [OK] Astro 开发服务器已启动
    timeout /t 3 /nobreak >nul
)

REM 检查 Astro 服务器
tasklist /FI "IMAGENAME eq node.exe" 2>nul | find "node.exe" >nul
if %errorlevel% equ 0 (
    echo [OK] Astro 服务器运行中
) else (
    echo [ERROR] Astro 服务器启动失败，请查看 logs\astro_dev.log
    exit /b 1
)

echo.
echo ========================================
echo   NoriOS Web 启动成功！
echo ========================================
echo.
echo [INFO] 访问地址：http://localhost:4321
echo [INFO] WebSocket: ws://localhost:8765
echo.
echo [INFO] 日志文件:
echo    - Astro: logs\astro_dev.log
echo    - Python: logs\python_backend.log
echo.
echo [INFO] 停止服务:
echo    任务管理器中结束 node.exe 和 python.exe 进程
echo.
echo ========================================

endlocal
