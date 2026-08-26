// ═══════════════════════════════════════════════════
// NoriOS — Astro 配置
// publicDir 指向原有前端资源目录，APPS 桌面骨架由 src/pages/index.astro 组装。
// 加载/挂载细节（原 Vite 打包产物）后续再完善。
// APPS 目录被 Astro 忽略，由 Python 后端处理
// ═══════════════════════════════════════════════════
import { defineConfig } from 'astro/config';

export default defineConfig({
  // 静态资源目录（原有前端资源：图标、音频、Live2D 等）
  publicDir: './src',
  // 生成产物
  outDir: './dist',
  // 无特殊集成，先保持纯净
  integrations: [],
  // 忽略 APPS 目录，由 Python 后端处理
  build: {
    inlineStylesheets: 'auto'
  },
  vite: {
    server: {
      watch: {
        ignored: ['**/APPS/**', '**/python_apps/**']
      }
    }
  }
});
