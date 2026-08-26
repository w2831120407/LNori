# 故障排查

## 常见问题

### 构建失败

#### 问题：`Error: Cannot find module 'astro'`

**原因**: 依赖未安装或 node_modules 损坏

**解决方案**:
```bash
# 清理并重新安装
rm -rf node_modules package-lock.json
npm install
```

#### 问题：`Build failed with errors`

**原因**: TypeScript 类型错误或语法错误

**解决方案**:
```bash
# 查看详细错误
npm run build -- --verbose

# 检查 TypeScript
npx tsc --noEmit
```

### 开发服务器问题

#### 问题：端口被占用

**错误信息**: `EADDRINUSE: address already in use`

**解决方案**:
```bash
# 查找占用端口的进程
lsof -i :4322

# 杀死进程
kill -9 <PID>

# 或使用其他端口
npm run dev -- --port 3000
```

#### 问题：热重载不工作

**解决方案**:
1. 检查文件保存是否成功
2. 清除浏览器缓存
3. 重启开发服务器
4. 检查 `.gitignore` 是否排除了相关文件

### 应用加载问题

#### 问题：白屏/无内容

**排查步骤**:

1. **检查控制台错误**
   ```javascript
   // 打开浏览器 DevTools Console
   ```

2. **验证资源路径**
   ```astro
   <!-- 错误 -->
   <script src="./src/index.js"></script>
   
   <!-- 正确 -->
   <script>import './src/index.js'</script>
   ```

3. **检查 Astro 语法**
   ```astro
   ---
   // 确保 frontmatter 正确闭合
   const data = {}
   ---
   ```

#### 问题：样式不生效

**常见原因**:

1. CSS 文件路径错误
2. 选择器优先级问题
3. CSS 模块作用域

**解决方案**:
```astro
---
// 方法 1: 直接导入
import './style.css'
---

<!-- 方法 2: 内联 -->
<style>
  .my-class { color: red; }
</style>

<!-- 方法 3: 全局样式 -->
<style is:global>
  body { margin: 0; }
</style>
```

### 性能问题

#### 问题：页面加载缓慢

**诊断工具**:
```bash
# 运行 Lighthouse
npx lighthouse http://localhost:4322

# 分析构建产物
npm run build
du -sh dist/*
```

**优化方向**:
- 减少 JavaScript 体积
- 启用懒加载
- 优化图片资源
- 使用 CDN

#### 问题：内存泄漏

**检测方法**:
1. 打开 Chrome DevTools Memory 面板
2. 录制内存快照
3. 比较不同时间点的快照

**常见原因**:
- 未清理的事件监听器
- 定时器未清除
- 闭包引用

**解决方案**:
```javascript
// 组件卸载时清理
class App {
  init() {
    window.addEventListener('resize', this.handleResize)
  }
  
  destroy() {
    window.removeEventListener('resize', this.handleResize)
    clearTimeout(this.timer)
  }
}
```

### 部署问题

#### 问题：404 错误

**GitHub Pages**:
```javascript
// astro.config.mjs
export default defineConfig({
  base: '/仓库名/'
})
```

**SPA 路由**:
```nginx
# Nginx 配置
location / {
  try_files $uri $uri/ /index.html;
}
```

#### 问题：资源加载失败 (CORS)

**解决方案**:
1. 确保所有资源使用相对路径
2. 配置正确的 CORS 头
3. 使用 CDN 时检查跨域设置

## 调试技巧

### Astro 调试

```astro
---
// 输出调试信息
console.log('Server-side:', data)
---

<pre>{JSON.stringify(data, null, 2)}</pre>

<script>
  console.log('Client-side:', window.location)
</script>
```

### 网络请求调试

```javascript
// 拦截 fetch
const originalFetch = window.fetch
window.fetch = async (...args) => {
  console.log('Fetch:', ...args)
  const response = await originalFetch(...args)
  console.log('Response:', response)
  return response
}
```

### 性能分析

```javascript
// 测量执行时间
console.time('operation')
// ... 代码 ...
console.timeEnd('operation')

// Performance API
performance.mark('start')
// ... 代码 ...
performance.mark('end')
performance.measure('duration', 'start', 'end')
```

## 日志收集

### 客户端日志

```javascript
// 全局错误处理
window.onerror = (msg, url, line, col, error) => {
  console.error({
    message: msg,
    url,
    line,
    column: col,
    error: error?.stack
  })
  // 发送到日志服务
  navigator.sendBeacon('/api/logs', JSON.stringify({
    type: 'error',
    data: { msg, url, line, col, stack: error?.stack }
  }))
}
```

### 构建日志

```bash
# 详细日志
npm run build -- --verbose 2>&1 | tee build.log

# 分析日志
grep -i "error" build.log
```

## 恢复策略

### 回滚到上一版本

```bash
# Git 回滚
git revert HEAD
git push

# 或重置
git reset --hard HEAD~1
git push --force
```

### 紧急修复

```bash
# 创建热修复分支
git checkout -b hotfix/issue-name

# 修复并提交
git add .
git commit -m "fix: emergency fix for critical issue"

# 合并到主分支
git checkout main
git merge hotfix/issue-name
```

## 获取帮助

### 有用的命令

```bash
# 检查 Node 版本
node --version

# 检查 npm 版本
npm --version

# 查看 Astro 版本
npx astro --version

# 清理缓存
npx astro telemetry reset

# 重新安装依赖
rm -rf node_modules && npm install
```

### 资源链接

- [Astro 官方文档](https://docs.astro.build)
- [Astro GitHub Issues](https://github.com/withastro/astro/issues)
- [Stack Overflow - Astro](https://stackoverflow.com/questions/tagged/astro)
- [本项目 Issue 追踪](https://github.com/your-org/webos/issues)

## 检查清单

当遇到问题时，按顺序检查：

- [ ] 重现问题步骤
- [ ] 查看控制台错误
- [ ] 检查网络请求
- [ ] 验证文件路径
- [ ] 清除缓存重试
- [ ] 更新依赖
- [ ] 搜索类似问题
- [ ] 创建最小复现

---
*版本: v2.0.0 | 最后更新: 2024*
