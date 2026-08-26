#!/bin/bash

# NoriOS Web 启动脚本 (Linux/Mac)
# 自动检测依赖、安装依赖并启动服务

set -e

echo "========================================"
echo "  NoriOS Web - 启动脚本"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检测 Node.js
echo "📦 检测 Node.js..."
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Node.js 未安装，请先安装 Node.js (>=18)${NC}"
    exit 1
fi
NODE_VERSION=$(node -v)
echo -e "${GREEN}✅ Node.js 已安装: $NODE_VERSION${NC}"

# 检测 npm
echo "📦 检测 npm..."
if ! command -v npm &> /dev/null; then
    echo -e "${RED}❌ npm 未安装${NC}"
    exit 1
fi
NPM_VERSION=$(npm -v)
echo -e "${GREEN}✅ npm 已安装: $NPM_VERSION${NC}"

# 检测 Python3
echo "🐍 检测 Python3..."
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python3 未安装，请先安装 Python3 (>=3.8)${NC}"
    exit 1
fi
PYTHON_VERSION=$(python3 --version)
echo -e "${GREEN}✅ Python3 已安装: $PYTHON_VERSION${NC}"

# 检测 websockets 包
echo "📦 检测 Python websockets 包..."
if ! python3 -c "import websockets" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  websockets 未安装，正在安装...${NC}"
    pip3 install websockets
fi
echo -e "${GREEN}✅ websockets 已安装${NC}"

# 进入项目目录
cd "$(dirname "$0")"

# 检测 node_modules
echo "📂 检测 Node.js 依赖..."
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}⚠️  node_modules 不存在，正在安装依赖...${NC}"
    npm install
else
    echo -e "${GREEN}✅ node_modules 已存在${NC}"
fi

# 验证关键依赖
echo "🔍 验证关键依赖..."
REQUIRED_DEPS=("astro")
for dep in "${REQUIRED_DEPS[@]}"; do
    if [ ! -d "node_modules/$dep" ]; then
        echo -e "${YELLOW}⚠️  缺少依赖 $dep，重新安装...${NC}"
        npm install
        break
    fi
done
echo -e "${GREEN}✅ 所有 Node.js 依赖已就绪${NC}"

# 显示依赖列表
echo ""
echo "📋 已安装的 Node.js 包:"
npm ls --depth=0 2>/dev/null | grep -E "^├─|@|^nori" || true
echo ""

# 启动 Python 后端
echo "🚀 启动 Python 后端框架..."
if pgrep -f "python3.*framework.py" > /dev/null; then
    echo -e "${YELLOW}⚠️  Python 后端已在运行中${NC}"
else
    cd python_apps
    nohup python3 framework.py > ../logs/python_backend.log 2>&1 &
    PYTHON_PID=$!
    echo -e "${GREEN}✅ Python 后端已启动 (PID: $PYTHON_PID)${NC}"
    cd ..
    
    # 等待后端启动
    sleep 2
fi

# 检查后端是否正常运行
if pgrep -f "python3.*framework.py" > /dev/null; then
    echo -e "${GREEN}✅ Python WebSocket 服务运行中 (ws://0.0.0.0:8765)${NC}"
else
    echo -e "${RED}❌ Python 后端启动失败，请查看 logs/python_backend.log${NC}"
    exit 1
fi

# 启动 Astro 开发服务器
echo ""
echo "🌐 启动 Astro 开发服务器..."
if pgrep -f "node.*server.mjs" > /dev/null; then
    echo -e "${YELLOW}⚠️  Astro 服务器已在运行中${NC}"
else
    mkdir -p logs
    nohup npm run dev > logs/astro_dev.log 2>&1 &
    ASTRO_PID=$!
    echo -e "${GREEN}✅ Astro 开发服务器已启动 (PID: $ASTRO_PID)${NC}"
    
    # 等待服务器启动
    sleep 3
fi

# 检查 Astro 服务器
if pgrep -f "node.*server.mjs" > /dev/null; then
    echo -e "${GREEN}✅ Astro 服务器运行中${NC}"
else
    echo -e "${RED}❌ Astro 服务器启动失败，请查看 logs/astro_dev.log${NC}"
    exit 1
fi

echo ""
echo "========================================"
echo -e "${GREEN}  🎉 NoriOS Web 启动成功！${NC}"
echo "========================================"
echo ""
echo "📍 访问地址: http://localhost:4321"
echo "📍 WebSocket: ws://localhost:8765"
echo ""
echo "📝 日志文件:"
echo "   - Astro: logs/astro_dev.log"
echo "   - Python: logs/python_backend.log"
echo ""
echo "🛑 停止服务:"
echo "   pkill -f 'node.*server.mjs'"
echo "   pkill -f 'python3.*framework.py'"
echo ""
echo "========================================"
