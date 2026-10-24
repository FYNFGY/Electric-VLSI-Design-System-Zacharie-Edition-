# Electric VLSI Design System v1.0.0（JLCEDA 布线增强版）

基于 [Electric VLSI Design System](https://www.staticfreesoft.com/) 9.08.1（GPL 开源 EDA 工具）源码，面向原理图设计体验的定制增强版本。核心目标：让原理图布线交互接近嘉立创EDA（JLCEDA）的操作习惯。

## v1.0.0 特性

### 端子标记体系
- **黄点** = 未连接端子：元件引脚（单元实例 + 原始元件如 CN 晶体管/电阻）、接地/电源网络标签、悬空导线端、导线连接节点
- **红点** = 已连接端子
- 导线交叉点保留 **Electric 原生圆盘**（导线同色、随缩放变大），可区分"连接的导线"与"跨过的导线"

### 点击连线（嘉立创式）
- 点击黄点 → 进入连线模式：**弧线实时预览跟随鼠标** + 水平/竖直十字虚线辅助对齐 → 再点定线
- 45° 走线、ESC/右键/再点起点 = 取消
- **W 键**：切换连线模式（十字光标），点击端子/导线（自动分叉）/空白（生成临时端子）起线
- **DEL 键**：连线模式下删除与起点相连的导线段并退出
- 双击端子仍弹出原生 GetInfo 信息框（无冲突）
- 右键悬空线端快速点击 → Create Export 菜单（右键按住拖动仍是原生连线）

### 自动连接
- **拖动元件**：端子与导线/端子接触 → 松手自动连接（导线中部自动拆线并生成原生 wirepin；接触点实时显示绿色十字，支持多网络同时高亮）
- **放置元件**（palette / component→Cell）：端子落在导线上直接连接
- **粘贴元件**：同样自动连接
- 安全规则：已连接的端子永不自动改接；移动导线不触发自动连接
- **R 键**：一键连接当前原理图中所有重叠的未连接端子

### 右键菜单
- 原理图**空白处右键**：Wire (W) / Auto-Connect Overlaps (R) / Manipulate Exports...（Electric 原生）

### 渲染增强
- 原理图导线加粗 5px
- Ctrl+G 网格改为**半透明线网格**（所有窗口，导线渲染在网格上层）
- 所有高亮（NCC 超链接、DRC 错误、选中对象）统一**绿色**并叠加绿色十字位置标记

### Explorer 树功能
- 右键 cell → **Rename All Views of Cell...**：同名的 {sch}/{lay}/{ic} 等视图一起重命名
- New Cell 对话框：视图列表带复选框，**一次创建多个同名视图**（自动归入同一分组）

## 运行

```
双击 target\run-electric.bat
或
java -jar target\electric-9.08.1.jar
```

要求：JDK 17+。

## 构建

```bash
# 全量编译（等价于官方 Ant 构建的 compile 目标）
javac -g -encoding UTF-8 --release 17 -cp ".;lib/*" -d target/classes @sources.txt

# 复制资源（*.jelib/*.prm/*.xml/*.gif/*.png 等）与 slf4j（org/）到 target/classes
# 打包
jar cfm target/electric-9.08.1.jar META-INF/MANIFEST.MF -C target/classes .
```

依赖：`lib/` 下的 JOGL Java3D 三个 jar（`java3d-core`、`java3d-utils`、`vecmath`，1.7.2）；slf4j 类位于 `org/`。

## 快捷键（仅原理图窗口生效）

| 快捷键 | 功能 |
|---|---|
| W | 进入/退出连线模式 |
| R | 连接所有重叠的未连接端子 |
| DEL（连线中） | 删除与起点相连的导线段 |
| ESC / 右键 / W | 取消连线并退出 |
| Ctrl+G | 网格开关（半透明线网格） |

## 文档

- `docs/` — GUI 模块方法级中文文档（README 总索引 + gui-01~gui-11 分节）
- `docs/work/` — 开发工作总结
- `docs/prompt/` — 需求提示词存档

## 许可证

GPL v3（沿用 Electric 的许可证）。本版本仅做交互/渲染层增强，未修改数据库格式与文件兼容性。

---

*版本：v1.0.0 · 基于 Electric 9.08.1 · 2026-10-24*
