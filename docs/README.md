# Electric VLSI 用户 GUI 模块文档

> 基于 Electric 9.08.1 源码（解压自 `electric-9.08.1.jar`），包 `com.sun.electric.tool.user`。
> 本目录文档由 AI 生成，供后续 AI Agent / 开发者修改 GUI 代码时参考。
> 文中方法行号基于当前源码快照，修改代码前请以实际文件为准。

## 文档索引

| 文件 | 内容 | 核心类 |
|---|---|---|
| [gui-01-launcher-core.md](gui-01-launcher-core.md) | GUI 启动与核心工具类 | `UserInterfaceMain`、`User`、`ErrorLogger`、`Exec`、`CellChangeJobs`、`CircuitChangeJobs`、`ViewChanges`、`ExportChanges` |
| [gui-02-editwindow.md](gui-02-editwindow.md) | 主编辑窗口 | `EditWindow`（155 个 public 方法全量）、`EditWindowFocusBrowser` |
| [gui-03-window-framework.md](gui-03-window-framework.md) | 窗口框架 | `WindowFrame`、`TopLevel`、`WindowContent`、`ExternalEditing`、`ElectricPrinter` |
| [gui-04-window-widgets.md](gui-04-window-widgets.md) | 窗口部件 | `ToolBar`、`StatusBar`、`LayerTab`、`LayerVisibility`、`PaletteFrame`、`LibraryPalette`、`TechPalette` |
| [gui-05-aux-windows.md](gui-05-aux-windows.md) | 辅助窗口 | `ExplorerTree`（单元浏览器）、`ExplorerTreeModel`、`MessagesWindow`、`TextWindow`、`JobTree` |
| [gui-06-redisplay-core.md](gui-06-redisplay-core.md) | 重绘引擎核心 | `PixelDrawing`、`LayerDrawing`、`AbstractDrawing`、`AbstractLayerDrawing` |
| [gui-07-redisplay-support.md](gui-07-redisplay-support.md) | 重绘引擎支撑 | `VectorDrawing`、`VectorCache`、`AlphaBlender`、`ERaster` 家族（Transparent/Opaque/PatternedRaster） |
| [gui-08-highlighting.md](gui-08-highlighting.md) | 高亮系统 | `Highlighter`、`Highlight`（及 7 子类）、`NetworkHighlighter`、`HighlightConnectivity`、`ErrorHighlight` |
| [gui-09-clipboard-keys.md](gui-09-clipboard-keys.md) | 剪贴板与快捷键 | `Clipboard`、`KeyBindingManager`、`KeyBindings`、`KeyStrokePair` |
| [gui-10-menus.md](gui-10-menus.md) | 菜单系统 | `EMenuBar`、`EMenu`、`EMenuItem`、`MenuCommands`、File/Edit/Cell/View/Window/Tool/Export/Help/PublicDebug 各菜单 |
| [gui-11-dialogs.md](gui-11-dialogs.md) | 对话框体系 | `EDialog` 基类 + 59 个操作对话框 + 57 个选项对话框 + projectSettings |

## GUI 模块架构总览

### 启动流程

```
Electric.main (com.sun.electric.Electric, 内存检查后重启动/直接启动)
  └─> com.sun.electric.Main.main          启动 Job 框架、装载技术库
        └─> UserInterfaceMain             GUI 入口（gui-01）
              ├─ 初始化数据库/Job/Tool 各子系统
              ├─ 构建 splash 窗口
              └─ 创建首个 TopLevel 窗口（含 WindowFrame + EditWindow）
```

### 窗口层级（gui-02/03/04/05）

```
TopLevel (JFrame, SDI 顶层窗口；MDI 模式下窗口直接是 WindowFrame/JInternalFrame)
  ├─ EMenuBar (菜单栏, gui-10)
  ├─ ToolBar  (工具栏, gui-04)
  ├─ StatusBar(状态栏, gui-04)
  ├─ ExplorerTree (单元/库浏览器侧栏, gui-05)
  ├─ LayerTab / PaletteFrame / TechPalette (图层与组件面板, gui-04)
  └─ WindowFrame (窗口框架, gui-03)
       ├─ WindowContent 接口实现之一：
       │    ├─ EditWindow    (版图/原理图编辑窗, gui-02)
       │    └─ TextWindow / MessagesWindow 等 (gui-05)
       └─ 每个 WindowFrame 恰好一个 WindowContent
```

### 渲染管线（gui-06/07）

```
EditWindow 不直接渲染。渲染分两阶段：
1. Job 线程（RenderJob, EditWindow 内）：AbstractDrawing.render()
   ├─ PixelDrawing (默认像素引擎): int[] 全色图 + 每透明层 1 位位平面 → composite() 合成
   │    └─ displayAlgorithm==1 时委托 VectorDrawing 渲染进其缓冲
   └─ LayerDrawing (分层光栅引擎): 每层 TransparentRaster，VectorCache 缓存矢量形状
        → AlphaBlender 在 AWT 线程逐行 alpha 混合 → VolatileImage
2. AWT 事件线程：Drawing.paintComponent()
   贴离屏图 → 叠网格 → 叠 Highlighter/MouseOverHighlighter 交互高亮 → 拖选框/单元边框
```

缓存失效：cell 修改 → `AbstractDrawing.forceRedraw(cell)` 登记 `changedCells` → 下次渲染逐出 `expandedCells` 缓存条目；缩放变化整体清空。

### 交互事件流

```
鼠标/键盘事件: EditWindow (先记坐标、请求焦点)
  └─ 转发 → WindowFrame 的事件监听器链（各工具监听器注册于此）
       └─ 命令执行（多数来自菜单, gui-10）
            ├─ 直接改数据库: Database 变更事件 → EditWindow 请求重绘
            └─ 或提交 Job: 后台线程执行 → Job 框架通知 → 重绘 + JobTree 更新
```

### 高亮系统（gui-08）

- 每个 Cell 的窗口间共享三个 `Highlighter`：选择（SELECT）、悬停（MOUSEOVER）、标尺（RULER）
- `Highlight` 抽象基类 7 个子类：Message/Poly/Line/Object/Area/EOBJ/Text
- `NetworkHighlighter`：整网络层次高亮（多窗口同步）
- `HighlightConnectivity`：快速拓扑提取（R-Tree/四叉树）
- `ErrorHighlight`：错误检查结果高亮（闪烁定时器在 EditWindow 中）

## 类速查表（按职责）

| 想找什么 | 类 | 文档 |
|---|---|---|
| 程序入口/启动 | `UserInterfaceMain` | gui-01 |
| 用户偏好/全局设置 | `User`（getXxxPref/setXxxPref 工厂三元组） | gui-01 |
| 编辑窗显示/缩放/层次 | `EditWindow` | gui-02 |
| 窗口创建/MDI-SDI 分支 | `WindowFrame` | gui-03 |
| 顶层窗口 | `TopLevel` | gui-03 |
| 工具栏按钮/光标模式 | `ToolBar` | gui-04 |
| 图层显示开关 | `LayerTab` / `LayerVisibility` | gui-04 |
| 组件摆放面板 | `PaletteFrame` / `TechPalette` / `LibraryPalette` | gui-04 |
| 单元树浏览/跨库拖放 | `ExplorerTree` / `ExplorerTreeModel` | gui-05 |
| 日志/文本输出 | `MessagesWindow` / `TextWindow` | gui-05 |
| 画什么（渲染入口） | `PixelDrawing` / `LayerDrawing` / `AbstractDrawing` | gui-06 |
| 矢量缓存/alpha 混合 | `VectorCache` / `VectorDrawing` / `AlphaBlender` | gui-07 |
| 选中/悬停高亮 | `Highlighter` / `Highlight` | gui-08 |
| 网络高亮/连通性提取 | `NetworkHighlighter` / `HighlightConnectivity` | gui-08 |
| 复制粘贴 | `Clipboard` | gui-09 |
| 快捷键绑定 | `KeyBindingManager` | gui-09 |
| 菜单项如何绑定命令 | `EMenuBar` / `EMenuItem` / 各 *Menu.makeMenu() | gui-10 |
| 对话框基类/已有对话框 | `EDialog` + dialogs 目录 | gui-11 |

## 常见修改任务 → 建议阅读顺序

- **加菜单/按钮命令**：gui-10（对应 *Menu.makeMenu）→ gui-09（若需快捷键）→ gui-01（命令类如 CircuitChanges）
- **改图层显示/颜色**：gui-04（LayerTab）→ gui-06/07（渲染按 LayerVisibility 绘制）
- **改绘制效果/性能**：gui-06（引擎选择与合成）→ gui-07（矢量缓存/光栅）
- **改选中/高亮行为**：gui-08 → gui-02（EditWindow 高亮委托）
- **加对话框**：gui-11（EDialog 生命周期 + 现有模式）→ gui-10（调起入口）
- **改窗口布局**：gui-03（WindowFrame/TopLevel）→ gui-04（部件挂载点）

## 未覆盖/浅覆盖的子包

- `com.sun.electric.tool.user.ncc` — NCC 比较结果 GUI（独立工具界面，未展开）
- `com.sun.electric.tool.user.tecEdit` / `tecEditWizard` / `tecEditWizard2` — 技术编辑向导（独立向导流，未展开）
- `com.sun.electric.tool.user.waveform` — 波形窗口（模拟波形显示，未展开）
- `com.sun.electric.tool.user.help` — 帮助系统（1 个文件）
- `com.sun.electric.tool.user.tests` — 回归测试（28 个文件，非 GUI 功能代码）
- GUI 依赖的外部框架（不在 tool.user 内）：`com.sun.electric.Main`（进程入口）、`com.sun.electric.tool.Tool`/`Job`（插件与任务框架）、`com.sun.electric.database.*`（数据库层）、`com.sun.electric.api.movie`（录像回放）

## 已知源码备注（供修改时注意）

- `WindowContextClass` 已无子类引用，属遗留代码（gui-03）
- `WindowFrame.show3DHighlight`/`set3DLayerVisibility` 为空实现，3D 代码被注释（gui-03）
- `EditWindow.drawPopupCloud` 绘制代码被禁用（gui-02）
- `PixelDrawing` 内嵌旧版 `ERaster` 接口与包级 `ERaster.java` 并存，勿混淆（gui-06/07）
- `InvisibleLayerConfiguration` 硬绑定 SHIFT+0~9 快捷键（gui-03）
