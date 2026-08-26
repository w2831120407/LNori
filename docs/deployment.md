# 构建部署

## 构建命令

### 开发环境

```bash
# 启动开发服务器 (带热重载)
npm run dev

# 指定端口
npm run dev -- --port 3000

# 暴露给局域网
npm run dev -- --host
```

### 生产构建

```bash
# 执行生产构建
npm run build

# 查看构建输出
ls -la dist/
```

### 预览构建

```bash
# 本地预览生产构建
npm run preview

# 指定端口预览
npm run preview -- --port 4000
```

## 构建配置

### astro.config.mjs

```javascript
import { defineConfig } from 'astro/config'

export default defineConfig({
  // 输出模式
  output: 'static',
  
  // 基础路径 (部署到子目录时设置)
  base: '/',
  
  // 构建优化
  compressHTML: true,
  
  // Markdown 配置
  markdown: {
    shikiConfig: {
      theme: 'dark-plus'
    }
  },
  
  // Vite 配置
  vite: {
    build: {
      cssMinify: true,
      minify: 'esbuild'
    }
  }
})
```

## 输出结构

构建后 `dist/` 目录结构：

```
dist/
├── index.html              # 主入口
├── favicon.ico             # 网站图标
├── assets/                 # 静态资源
│   ├── js/                 # JavaScript 文件
│   │   └── [hash].js       # 带哈希的 JS
│   └── css/                # CSS 文件
│       └── [hash].css      # 带哈希的 CSS
└── apps/                   # 应用模块
    └── [app-name]/         # 各应用目录
        └── ...             # 应用资源
```

## 部署平台

### GitHub Pages

```yaml
# .github/workflows/deploy.yml
name: Deploy to GitHub Pages

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Build
        run: npm run build
      
      - name: Deploy
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./dist
```

**配置步骤：**

1. 在 `astro.config.mjs` 中设置 `base: '/仓库名/'`
2. 推送代码到 main 分支
3. GitHub Actions 自动部署
4. 访问 `https://用户名.github.io/仓库名/`

### Netlify

```bash
# 安装 Netlify CLI
npm install -g netlify-cli

# 登录
netlify login

# 部署
netlify deploy --prod --dir=dist
```

**netlify.toml 配置：**

```toml
[build]
  command = "npm run build"
  publish = "dist"

[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

### Vercel

```bash
# 安装 Vercel CLI
npm install -g vercel

# 部署
vercel --prod
```

Vercel 自动检测 Astro 项目，无需额外配置。

### Docker

```dockerfile
# Dockerfile
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

```nginx
# nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # 缓存静态资源
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

```bash
# 构建和运行
docker build -t webos .
docker run -p 8080:80 webos
```

## 环境变量

创建 `.env` 文件：

```bash
# 开发环境
PUBLIC_API_URL=http://localhost:3000
DEBUG=true

# 生产环境 (使用 .env.production)
PUBLIC_API_URL=https://api.example.com
DEBUG=false
```

在代码中使用：

```javascript
const apiUrl = import.meta.env.PUBLIC_API_URL
const isDebug = import.meta.env.DEBUG === 'true'
```

## CDN 配置

### Cloudflare

1. 将域名 DNS 迁移到 Cloudflare
2. 配置 Page Rules 缓存静态资源
3. 启用 Auto Minify
4. 开启 Brotli 压缩

### AWS CloudFront

```yaml
# CloudFormation 模板片段
Resources:
  Distribution:
    Type: AWS::CloudFront::Distribution
    Properties:
      DistributionConfig:
        Origins:
          - Id: S3Origin
            DomainName: !GetAtt Bucket.DomainName
            S3OriginConfig: {}
        DefaultCacheBehavior:
          TargetOriginId: S3Origin
          ForwardedValues:
            QueryString: false
            Cookies:
              Forward: none
          Compress: true
        Enabled: true
```

## 性能监控

### Lighthouse CI

```bash
# 安装
npm install -g @lhci/cli

# 运行审计
lhci autorun
```

### Web Vitals

在应用中集成 Web Vitals：

```javascript
import { onCLS, onFID, onFCP, onLCP, onTTFB } from 'web-vitals'

onCLS(console.log)
onFID(console.log)
onFCP(console.log)
onLCP(console.log)
onTTFB(console.log)
```

## 回滚策略

### 版本备份

```bash
# 备份当前版本
cp -r dist dist-backup-$(date +%Y%m%d-%H%M%S)

# 快速回滚
rm -rf dist && cp -r dist-backup-latest dist
```

### 蓝绿部署

使用支持蓝绿部署的平台 (如 Netlify、Vercel) 实现零停机发布。

---
*版本: v2.0.0 | 最后更新: 2024*
