# NoriOS v1.0.8 · mountApp SyntaxError 终极修复！

## 🎯 主人诊断报告实锤根因（来自复制诊断报告！太有用了！）
```
[CON_ERROR] L19 @ http://127.0.0.1:8008/index.html ::
   Uncaught SyntaxError: The requested module '/assets/index-CyHAbkO5.js' does not provide an export named 'mountApp'
[DOM_RESULT] {"rootHtmlLen":0,"bodyTextLen":0,"rootChildren":0}
```

## 🔗 根因链（100%闭环！）
1. `index.html` 第17-25行写了一段**死代码script**：
   ```html
   <script type="module">
     import { mountApp } from '/assets/index-CyHAbkO5.js'; // ❌ 目标JS根本没有这个具名导出！
     document.addEventListener('DOMContentLoaded', () => {
       mountApp(document.getElementById('root'));
     });
   </script>
   ```
2. **ES Module 严格检查具名导出！** 找不到 `mountApp` → **立刻抛 SyntaxError！** → 整个 inline script 直接停止执行！
3. 主人没连电脑看不到 Console ERROR！只看到 `class="dark theme-nori"` 生效的**纯黑背景** → 游戏没内容！
4. **为什么说starpak/LNori作者也写错了这段？** 小月克隆starpak/LNori比对：
   - index.html 里**同样有这段错误的import mountApp**（一模一样）
   - `/assets/index-CyHAbkO5.js` 最后1行SHA256: `865c9053927a...` 我们和starpak**完全一致**
   - `grep -c mountApp` starpak index-CyHAbkO5.js = 0！同样没有导出！
   - 真实挂载逻辑：JS文件倒数第3行**自动执行** → `H2.createRoot(document.getElementById('root')).render(...)`！
   → **这段import mountApp完全是过时的残留死代码！starpak自己也忘了删！**

## ✅ 修复（v1.0.8！3行→搞定！）
删除 `/src/index.html` 第17-25行那段错误的 inline `<script type="module">`，用注释说明根因！
→ 让 `index-CyHAbkO5.js` 最后一行自动执行的 `createRoot().render()` 正常挂载！

## 验证
- 修复后src/index.html: `grep -c mountApp` = 0
- APK解包 assets/www/index.html: `grep -c mountApp` = 0
- 诊断报告不会再有「does not provide an export named 'mountApp'」SyntaxError
- DOM检查: rootHtmlLen>10000 字符 ✅ 有游戏内容！

## 版本
Manifest versionCode=108 / versionName=1.0.8
签名：release_v102.jks 同链 → v1.0.2~v1.0.7 直接覆盖升级不用卸载！
