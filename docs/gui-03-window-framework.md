# 窗口框架（WindowFrame / TopLevel / WindowContent）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.ui。本文件由 AI 生成。

## 模块概述
- `WindowFrame` 是整个 GUI 窗口框架的核心：一个 `WindowFrame` 对应一个"编辑窗口"，左侧是含 Components / Explorer / Layers 三个选项卡的侧边栏，右侧是 `WindowContent` 内容区；它同时管理 MDI/SDI 两种窗口模式、单元历史（CellHistory）以及窗口生命周期。
- `WindowContent` 是右侧内容区的接口，`EditWindow`（电路图）、`TextWindow`（文本）、`WaveformWindow`（波形，位于 user.waveform 包）均实现该接口；`WindowFrame` 只面向此接口编程。
- `TopLevel` 是顶层 `JFrame`：SDI 模式下每个 `WindowFrame` 拥有一个 `TopLevel`；MDI 模式下全局只有唯一一个 `TopLevel` + `JDesktopPane`。`TopLevel` 承载菜单栏（EMenuBar）、工具栏（ToolBar）、状态栏（StatusBar）。
- `WindowContextClass` 是早期"窗口内容类"的抽象基类（JPanel + HighlightListener），在 9.08.1 中已无任何子类引用，属遗留代码。
- 本包还有三个配套类：`ExternalEditing`（用外部文本编辑器编辑文本 cell）、`InvisibleLayerConfiguration`（管理"不可见层配置"，支持 SHIFT+0~9 硬绑定快捷键）、`ElectricPrinter`（实现 Printable/ImageObserver 的打印与图像导出接口）。

---

## WindowFrame
**文件**: `com/sun/electric/tool/user/ui/WindowFrame.java`（1617 行）
**职责**: 定义带左侧 cell 浏览器的编辑窗口。负责组装窗口结构（侧边栏 + 内容区的 JSplitPane）、按 MDI/SDI 模式创建承载容器（JInternalFrame / TopLevel）、维护全局窗口列表与"当前窗口"概念、管理 cell 历史（前进/后退）、分发全局事件监听器。
**继承/实现**: `public class WindowFrame`（无继承；`/*extends Observable */` 为被注释掉的旧设计）
**关键字段**:
- `content (WindowContent)` — 右侧内容区（EditWindow / TextWindow / WaveformWindow 等）
- `js (JSplitPane)` — 分隔侧边栏与内容区的水平分隔面板
- `jif (JInternalFrame)` — MDI 模式下的内部框架（SDI 下为 null）
- `jf (TopLevel)` — SDI 模式下的顶层框架（MDI 下为 null）
- `sideBar (JTabbedPane)` / `sideBarOnLeft (boolean)` — 侧边栏及其左右位置
- `explorerTab (ExplorerTree)` / `paletteTab (PaletteFrame)` / `layersTab (LayerTab)` — 侧边栏三个选项卡
- `finished (boolean)` — 窗口已关闭标志
- `index (int)` / `usageClock (int)` — 窗口唯一编号 / 最近使用计数
- `dynamicMenu (JMenu)` — 放置 WindowMenu 动态菜单项的菜单
- `windowList (static List<WindowFrame>)` / `curWindowFrame (static WindowFrame)` — 全局窗口列表 / 当前窗口
- `curListener (static ElectricEventListener)` — 全局唯一的事件监听器（由工具栏控制）
- `cellHistory (List<CellHistory>)` / `cellHistoryLocation (int)` / `cellHistoryLimit (=20)` — cell 历史列表 / 当前位置 / 上限

### 方法
#### `public static void showFrame(WindowFrame wf)`
- 行号: L129
- 描述: **`[重要]`** 供高亮工具把窗口带到前台。MDI 下取消图标化并选中内部框架；SDI 下恢复 `Frame.NORMAL` 状态并置前显示。

#### `public static void repaintAllWindows()`
- 行号: L152
- 描述: 对所有窗口执行 `fullRepaint()`、按当前工艺重载组件菜单，并更新层颜色偏好。

#### `static { new LibraryTreeUpdater(); }`（静态初始化块）
- 行号: L168
- 描述: 类加载时注册 `LibraryTreeUpdater` 数据库变更监听器。

#### `public static class DisplayAttributes`（嵌套类）
- 行号: L170
- 描述: 记录窗口显示属性（缩放、偏移、down-in-place 下降路径），用于 cell 历史保存/恢复。
- 方法:
  - `public DisplayAttributes()` — L176，默认 scale=1、偏移 0、空路径。
  - `public DisplayAttributes(EditWindow wnd)` — L180，从 EditWindow 抓取缩放/偏移/in-place 路径。
  - `public DisplayAttributes(double scale, double offX, double offY, List<NodeInst> inPlaceDescent)` — L184，全参构造。
  - `public FixpTransform getIntoCellTransform()` — L191，对路径逐级 `preConcatenate` 得到"进入 cell"坐标变换。
  - `public FixpTransform getOutofCellTransform()` — L198，逐级 `concatenate` 得到"退出 cell"坐标变换。
  - `public boolean equals(Object o)` / `public int hashCode()` — L206/L216，按 scale/offX/offY/inPlaceDescent 判等。

#### `public WindowFrame()`
- 行号: L228
- 描述: 构造器：分配唯一 `index`（`windowIndexCounter++`），初始化空 cell 历史列表。

#### `public void setDynamicMenu(JMenu menu)` / `public JMenu getDynamicMenu()`
- 行号: L239/L240
- 描述: 动态菜单（WindowMenu 挂载点）的 setter/getter。

#### `public static WindowFrame createEditWindow(final Cell cell)`
- 行号: L247
- 描述: **`[重要]`** 创建一个显示指定 Cell 的编辑窗口。文本视图创建 `TextWindow`，否则 `EditWindow.CreateElectricDoc`；随后 `finishWindowFrameInformation` 收尾、`fillScreen` 填充；移除 F6/F8 键的 UI 绑定；cell 非空时异步在 Explorer 树中打开其库。

#### `public int getIndex()`
- 行号: L282
- 描述: 返回本窗口的唯一编号。

#### `public static WindowFrame findFromIndex(int index)`
- 行号: L289
- 描述: 按编号在 `windowList` 中查找 WindowFrame（找不到返回 null）。

#### `public void finishWindowFrameInformation(WindowContent wnd, Cell cell)`
- 行号: L299
- 描述: **`[重要]`** 窗口创建收尾：构建窗口结构、把本窗口设为当前窗口、装配 JFrame、更新 Window 菜单动态项。

#### `public static void show3DHighlight()`
- 行号: L315
- 描述: 3D 高亮接口，当前为空实现（方法体被注释）。

#### `public void set3DLayerVisibility(LayerVisibility lv)`
- 行号: L329
- 描述: 通知 3D 视图层可见性变化，当前为空实现（方法体被注释）。

#### `public static WindowFrame createWaveformWindow(Stimuli sd)`
- 行号: L345
- 描述: **`[重要]`** 根据仿真数据创建波形窗口（`WaveformWindow`），收尾后异步打开 Explorer 的 SIGNALS 面板。

#### `public static void updateTechnologyLists()`
- 行号: L362
- 描述: 新技术创建后调用：刷新所有窗口的层选项卡、组件选项卡与内容区的技术列表。

#### `public static class CurTechControlListener implements ActionListener`（嵌套类）
- 行号: L376
- 描述: 响应侧边栏层选项卡/组件选项卡中 Technology 下拉框的选择变化。
- 方法:
  - `CurTechControlListener(WindowFrame wf)` — L380，包私有构造，保存所属 WindowFrame。
  - `public void actionPerformed(ActionEvent evt)` — L385，把当前工艺切换为所选工艺（`Job.getExtendedUserInterface().setCurrentTechnology`），刷新组件/层选项卡并 `fullRepaint()`。

#### `public List<MutableTreeNode> loadDefaultExplorerTree()`
- 行号: L402
- 描述: 用 `ExplorerTreeModel.makeLibraryTree()` 构建默认库树节点列表（单元素列表）。

#### `private Dimension buildWindowStructure(WindowContent content, Cell cell, GraphicsConfiguration gc)`
- 行号: L408
- 描述: **`[重要]`**（private）组装窗口结构：创建 ExplorerTree、PaletteFrame、LayerTab 装入侧边栏 JTabbedPane（Mac 上选项卡置于左侧）；若启用了消息窗口停靠则垂直拆分；按 `User.isSideBarOnRight()` 决定侧边栏在 JSplitPane 的左/右；最后把本窗口加入 `windowList`。

#### `private Dimension createJFrame(String title, GraphicsConfiguration gc)`
- 行号: L485
- 描述: **`[重要]`**（private）创建承载容器：MDI 模式建 `JInternalFrame`（带图标、自动滚动）；SDI 模式建 `TopLevel("Electric - " + title, ...)`，优先使用 `User.getDefaultWindowSize()/Pos()` 用户偏好。返回窗口尺寸（同时推进 `windowOffset` 错位量，超过 300 归零）。

#### `public void loadComponentMenuForTechnology()`
- 行号: L511
- 描述: 按内容区 cell 的工艺（无 cell 时用 `Technology.getCurrent()`）刷新组件选项卡与层选项卡。

#### `private void populateJFrame()`
- 行号: L527
- 描述: **`[重要]`**（private）把 JSplitPane 装入容器并挂事件监听：MDI 下加入 JInternalFrame 内容面板、挂 `InternalWindowsEvents`、`TopLevel.addToDesktop`；SDI 下加入 TopLevel 内容面板、挂 `WindowsEvents`（窗口+焦点监听）并 `setVisible(true)`。

#### `public Component getComponent()`
- 行号: L554
- 描述: 返回本窗口的顶层组件（MDI 返回 `jif`，SDI 返回 `jf`）。

#### `public int getUsageClock()`
- 行号: L566
- 描述: 返回使用时钟值；每次窗口激活自增，值最大者即最近激活的窗口。

#### `private void depopulateJFrame()`
- 行号: L577
- 描述: （private）从旧容器移除 JSplitPane 与事件监听器。仅用于 SDI 模式跨显示器移动窗口时（见 `moveEditWindow`）。要求 EDT 线程。

#### `public void setCellWindow(Cell cell, CellHistory history)`
- 行号: L604
- 描述: **`[重要]`** 在本窗口右侧显示指定 Cell（同时处理电路与文本 cell）。若现有内容类型（TextWindow/EditWindow）与 cell 视图不符则重建内容区；类型相符则直接 `content.setCell(...)` 切换；带 `history` 时恢复显示属性与上下文，并自动追加历史记录、刷新组件菜单与 Window 菜单。

#### `public void moveEditWindow(GraphicsConfiguration gc)`
- 行号: L661
- 描述: 仅 SDI 有效：把窗口从一个显示器移到另一个。隐藏并拆解旧 TopLevel，在目标 `GraphicsConfiguration` 上创建新 TopLevel 并重新装配，最后销毁旧框架。

#### `public static void wantToOpenCurrentLibrary(boolean openLib, Cell cell)`
- 行号: L684
- 描述: 强制所有窗口的 Explorer 树打开当前库（`openLib=true`）或信号列表（`openLib=false`），可选选中指定 cell。

#### `public static void wantToRedoLibraryTree()`
- 行号: L696
- 描述: 请求所有窗口重载库树内容。

#### `public void wantToRedoSignalTree()`
- 行号: L705
- 描述: 在 EDT 上重建本窗口的信号树（非 EDT 调用时通过 `invokeLater` 转发）。

#### `public static void setSideBarLocation(boolean onLeft)`
- 行号: L717
- 描述: 切换当前窗口侧边栏的左右位置，并保持分隔条距另一侧的距离不变。

#### `public WindowContent getContent()`
- 行号: L746
- 描述: 返回右侧内容区对象（EditWindow、WaveformWindow 等）。

#### `public static WindowFrame getCurrentWindowFrame()`
- 行号: L753
- 描述: **`[重要]`** 返回当前窗口；没有当前窗口时创建一个新的 EditWindow（等价于 `getCurrentWindowFrame(true)`）。

#### `public static WindowFrame getCurrentWindowFrame(boolean makeNewFrame)`
- 行号: L765
- 描述: **`[重要]`** 返回当前窗口；`makeNewFrame=false` 时若不存在则返回 null（并顺便从 windowList 末尾找回最近窗口），`true` 时最终会 `createEditWindow(null)` 新建。方法体在 `synchronized(windowList)` 内。

#### `public static void setListener(ElectricEventListener listener)`
- 行号: L784
- 描述: 设置全局唯一的事件监听器（鼠标/按键/数据库变更，通常由工具栏控制）。旧设计中分别设置各监听器的代码已注释。

#### `public static interface ElectricEventListener extends MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, DatabaseChangeListener`
- 行号: L793
- 描述: 组合接口，把各类 Swing 监听器与数据库变更监听合为一个"当前监听器"。

#### `public static ElectricEventListener getListener()`
- 行号: L802
- 描述: 返回当前监听器；为 null 时默认使用 `ClickZoomWireListener.theOne`。

#### `public static MouseListener getMouseListener()` / `public static MouseMotionListener getMouseMotionListenerListener()` / `public static MouseWheelListener getMouseWheelListenerListener()` / `public static KeyListener getKeyListenerListener()`
- 行号: L813 / L823 / L833 / L843
- 描述: 均委托 `getListener()` 返回当前监听器的对应接口视图。

#### `public static Cell getCurrentCell()`
- 行号: L851
- 描述: 返回当前窗口内容区的 Cell（无当前窗口返回 null）；要求 EDT 线程（assert）。

#### `public static Cell needCurCell()`
- 行号: L865
- 描述: 获取当前 Cell，若不存在则打印提示信息（提示用 Cell 菜单的 New Cell 命令创建）并返回 null。

#### `public void setCursor(Cursor cursor)`
- 行号: L879
- 描述: 对 SDI 下的 TopLevel、JSplitPane 和内容区统一设置光标（MDI 下跳过 TopLevel）。

#### `public static void removeLibraryReferences(Library lib)`
- 行号: L888
- 描述: 库被删除后清理：所有显示该库 cell 的窗口把内容置空（`content.setCell(null, null, null)`）并重绘。

#### `public void requestFocus()`
- 行号: L904
- 描述: 请求本窗口获得焦点；非 EDT 调用时通过 `invokeLater` 转发到 `requestFocusUnsafe`。

#### `private void requestFocusUnsafe()`
- 行号: L914
- 描述: （private）MDI 下内部框架置前并选中；SDI 下 TopLevel 置前并 `requestFocus()`。

#### `public boolean isFocusOwner()`
- 行号: L927
- 描述: 判断本窗口是否拥有焦点（MDI 看 jif 是否选中，SDI 看 jf 是否焦点所有者）。

#### `private static void setCurrentWindowFrame(WindowFrame wf, boolean autoSwitchTech)`
- 行号: L938
- 描述: **`[重要]`**（private）把 wf 设为当前窗口：更新 `curWindowFrame`、移到 `windowList` 末尾、调用 `currentCellChanged`、通知高亮器 `gainedFocus()`、重建所有窗口标题。窗口已 finished 时直接忽略。

#### `private void currentCellChanged(boolean autoSwitchTech)`
- 行号: L958
- 描述: （private）把库的当前 cell 设为本窗口 cell；`autoSwitchTech=true` 时执行 `autoTechnologySwitch`。

#### `public static void wantToRedoTitleNames()`
- 行号: L970
- 描述: 重建所有窗口标题（`content.setWindowTitle()`）并刷新 Window 菜单。

#### `public void setWindowSize(Rectangle frameRect)`
- 行号: L983
- 描述: 按给定矩形设置窗口尺寸与位置（MDI 操作 jif，SDI 操作 jf 并 `validate()`）。

#### `public void finished()`
- 行号: L1002
- 描述: **`[重要]`** 记录窗口已关闭并清理：从 `windowList` 移除、置 `finished=true`、清除 `curWindowFrame` 引用、通知内容区/层选项卡收尾、清空 Explorer 模型、拆解 JFrame；SDI 下若只剩最后一个窗口则调用 `FileMenu.quitCommand()` 退出程序。

#### `public WaveformWindow getWaveformWindow()`
- 行号: L1043
- 描述: 把内容区强转为 WaveformWindow 返回。

#### `public ExplorerTree getExplorerTab()` / `public PaletteFrame getPaletteTab()` / `public LayerTab getLayersTab()`
- 行号: L1049 / L1055 / L1061
- 描述: 分别返回侧边栏的 Explorer 树选项卡、组件面板、层可见性选项卡。

#### `public TopLevel getFrame()`
- 行号: L1070
- 描述: 返回本窗口的 TopLevel；MDI 模式返回全局唯一 TopLevel（`TopLevel.getCurrentJFrame()`），SDI 返回本窗口自己的 jf。

#### `public JInternalFrame getInternalFrame()`
- 行号: L1082
- 描述: 返回本窗口的 JInternalFrame；SDI 模式返回 null。

#### `public boolean generatedEvent(java.awt.AWTEvent e)`
- 行号: L1096
- 描述: 判断事件是否由本窗口（或其组件）产生——当前只识别 `InternalFrameEvent` 且来源为 jif 的情形。

#### `public static int getNumWindows()`
- 行号: L1109
- 描述: 返回当前 WindowFrame 数量。

#### `public static Iterator<WindowFrame> getWindows()`
- 行号: L1119
- 描述: 返回所有 WindowFrame 的迭代器（windowList 的快照副本，线程安全）。

#### `public String composeTitle(Cell cell, String prefix, int pageNo)`
- 行号: L1133
- 描述: 集中式窗口标题拼装：`prefix + cell.libDescribe()`，多页 cell 追加 " - Page n"，cell 不属于当前库时追加 " - Current library: xxx"；cell 未链接时返回 "***NONE***"。

#### `public void setTitle(String title)` / `public String getTitle()`
- 行号: L1158 / L1173
- 描述: 设置/读取窗口标题（MDI 操作 jif，SDI 操作 jf；setTitle 在标题相同时跳过）。

#### `private static void removeUIBinding(JComponent comp, KeyStroke key)` / `private static void removeUIBinding(InputMap map, KeyStroke key)`
- 行号: L1183 / L1188
- 描述: 递归移除组件各级 InputMap 中的按键绑定（用于去掉 F6/F8 的默认 UI 绑定）。

#### `public static void autoTechnologySwitch(Cell cell, WindowFrame wf)`
- 行号: L1200
- 描述: 若用户开启自动工艺切换（`User.isAutoTechnologySwitch()`）且 cell 工艺与当前不同，则切换当前工艺并刷新该窗口的组件/层选项卡；文本视图直接返回。

#### `public static class CellHistory`（嵌套类）
- 行号: L1224
- 描述: 一条 cell 历史记录：cell、VarContext、选中端口、显示属性（DisplayAttributes）、高亮列表与高亮偏移。
- 方法（getter/setter，合并）:
  - `getCell()` L1233 / `getContext()` L1235 / `setContext(VarContext)` L1237 / `setSelPort(PortInst)` L1239 / `getDisplayAttributes()` L1241 / `setDisplayAttributes(DisplayAttributes)` L1243。

#### `public List<CellHistory> getCellHistoryList()` / `public int getCellHistoryLocation()`
- 行号: L1250 / L1256
- 描述: 返回 cell 历史列表 / 当前历史位置。

#### `public void cellHistoryGoBack()` / `public void cellHistoryGoForward()`
- 行号: L1261 / L1271
- 描述: 在 cell 历史中后退/前进一格（已在首/尾则不做任何事），通过 `setCellByHistory` 恢复。

#### `private boolean cellHistoryCanGoBack()` / `private boolean cellHistoryCanGoForward()`
- 行号: L1278 / L1285
- 描述: 判断历史是否可后退/前进。

#### `private void fireCellHistoryStatus()`
- 行号: L1296
- 描述: 把历史可前进/后退状态推给工具栏按钮（用于跨显示器重建工具栏时同步）。

#### `private ToolBar getToolBar()`
- 行号: L1310
- 描述: 返回本窗口关联的工具栏（MDI 为唯一工具栏，SDI 为本窗口 TopLevel 的工具栏）。

#### `public void addToHistory(Cell cell, VarContext context, DisplayAttributes da)`
- 行号: L1321
- 描述: **`[重要]`** 追加一条 cell 历史记录（cell 为 null 直接返回）；若当前位置在历史中间则先截断后续记录；超过上限（20）丢弃最旧记录；最后刷新工具栏按钮状态。只应由非历史遍历性修改调用（如 New Cell 命令）。

#### `public void saveCurrentCellHistoryState()`
- 行号: L1372
- 描述: **`[重要]`** 把当前编辑窗口状态（VarContext、缩放/平移/in-place 的 DisplayAttributes、选中端口、高亮列表与偏移）存入当前位置的历史记录，供后退后返回时恢复。

#### `public void setCellByHistory(int location)`
- 行号: L1418
- 描述: **`[重要]`** 从历史记录恢复窗口：cell 已失效（未链接）则清空该记录；调 `setCellWindow` 切换 cell，对电路窗口恢复选中端口或高亮列表与偏移；最后 `fullRepaint` 并更新 `cellHistoryLocation`。

#### `public int findCellHistoryIndex(Cell cell, VarContext context)`
- 行号: L1464
- 描述: 从后向前查找显示给定 cell/context 的历史记录下标（找不到返回 -1）。

#### `public static void showCellHistoryStats()`
- 行号: L1478
- 描述: 统计并打印所有窗口历史记录涉及的 Topology（用于内存使用分析）。

#### `private class WindowsEvents extends WindowAdapter`（内部类）
- 行号: L1519
- 描述: 处理 SDI 模式 JFrame 的激活与关闭事件；持 WindowFrame 的 `WeakReference`。
- 方法:
  - `WindowsEvents(WindowFrame wf)` — L1524，构造，保存弱引用。
  - `public void windowActivated(WindowEvent evt)` — L1530，递增 `usageClock`、`setCurrentWindowFrame(realWF, false)`、刷新历史按钮，并调 `ExternalEditing.updateEditors()` 检查外部编辑器。
  - `public void windowClosing(WindowEvent evt)` — L1541，调 `finished()` 关窗。

#### `private class InternalWindowsEvents extends InternalFrameAdapter`（内部类）
- 行号: L1550
- 描述: 处理 MDI 模式 JInternalFrame 的激活与关闭事件；同样持弱引用。
- 方法:
  - `InternalWindowsEvents(WindowFrame wf)` — L1555，构造。
  - `public void internalFrameClosing(InternalFrameEvent evt)` — L1561，调 `finished()` 关窗。
  - `public void internalFrameActivated(InternalFrameEvent evt)` — L1566，激活逻辑同 SDI 版（递增 usageClock、设为当前窗口、刷新历史按钮）。

#### `private static class LibraryTreeUpdater implements DatabaseChangeListener`（内部类）
- 行号: L1578
- 描述: 数据库变更监听器，负责在 cell 树变化时刷新所有窗口的库树。
- 方法:
  - `LibraryTreeUpdater()` — L1580，构造时向 `UserInterfaceMain` 注册自身。
  - `public void databaseChanged(DatabaseChangeEvent e)` — L1583，`cellTreeChanged()` 为真时调 `wantToRedoLibraryTree()`；并把事件转发给当前监听器 `curListener`。

---

## TopLevel
**文件**: `com/sun/electric/tool/user/ui/TopLevel.java`（629 行）
**职责**: 定义顶层窗口。MDI 模式（Windows 平台默认）下全局只有唯一一个 TopLevel，内含 JDesktopPane 承载所有子窗口；SDI 模式（Unix/Mac 默认）下每个 WindowFrame 各有一个 TopLevel。同时承载菜单栏、工具栏、状态栏，管理光标、modeless 对话框置顶、屏幕/显示器信息。
**继承/实现**: `public class TopLevel extends JFrame`
**关键字段**:
- `mode (static UserInterfaceMain.Mode)` — MDI/SDI 模式
- `desktop (static JDesktopPane)` — MDI 桌面面板
- `topLevel (static TopLevel)` — MDI 主框架
- `scrnSize (static Dimension)` — 屏幕尺寸
- `doubleClickDelay (static int)` — 双击间隔（毫秒）
- `cursor (static Cursor)` / `busyCursorOn (static boolean)` — 当前光标 / 忙光标覆盖标志
- `modelessDialogs (static Set<Window>)` / `onTopWindowAdapter` — 需保持置顶的 modeless 对话框集合及监听器
- `sb (StatusBar)` / `menuBar (EMenuBar.Instance)` / `toolBar (ToolBar)` — 状态栏 / 菜单栏 / 工具栏

### 方法
#### `public TopLevel(String name, Rectangle bound, WindowFrame frame, GraphicsConfiguration gc, boolean createStructure)`
- 行号: L92
- 描述: **`[重要]`** 构造顶层窗口：校验 bound 是否落在某个显示器内（否则改用最大显示器区域）；设置位置/尺寸与 BorderLayout、窗口图标；`createStructure=true` 时创建菜单/工具/状态栏结构；MDI 下挂关闭（退出程序）与移动/缩放监听（保存窗口位置偏好）；注册 modeless 置顶适配器并初始化默认光标；关闭轻量级弹出与工具提示（为 3D 混用 AWT/Swing）。

#### `public static void addModelessDialog(Window dialog)` / `public static void removeModelessDialog(JFrame dialog)`
- 行号: L159 / L165
- 描述: 注册/注销需保持置顶的 modeless 对话框。

#### `private static class MyWindowAdapter extends WindowAdapter`（内部类）
- 行号: L170
- 描述: 窗口获得焦点时，若用户开启"保持 modeless 对话框置顶"偏好，则把所有可见 modeless 对话框 `toFront()`。
- 方法: `public void windowGainedFocus(WindowEvent e)` — L172。

#### `public void createStructure(WindowFrame frame)`
- 行号: L182
- 描述: **`[重要]`** 创建窗口结构：由 `MenuCommands.menuBar().genInstance()` 生成菜单栏并安装；创建工具栏置于 NORTH；创建 StatusBar 置于 SOUTH（frame 传入但 MDI 初始化时可为 null）。

#### `public static ImageIcon getFrameIcon()`
- 行号: L205
- 描述: 返回窗口图标资源（IconElectric.gif）。

#### `public static void InitializeMessagesWindow()`
- 行号: L210
- 描述: **`[重要]`** MDI 模式下从偏好读取上次窗口位置（`parseBound`）、创建唯一 TopLevel 与 JDesktopPane 并显示；最后初始化 MessagesWindow。

#### `public static void InitializeWindows()`
- 行号: L237
- 描述: 初始化窗口系统：MDI 下为唯一 TopLevel 创建结构；创建初始编辑窗口（`WindowFrame.createEditWindow(null)`）；更新最近打开库列表；MDI 下触发一次尺寸微调强制重绘。

#### `public static void OSInitialize(UserInterfaceMain.Mode mode)`
- 行号: L255
- 描述: **`[重要]`** 初始化窗口环境：测量屏幕尺寸与双击间隔（`awt.multiClickInterval`）；按操作系统决定模式（WINDOWS→MDI 且屏幕高度减 30，UNIX/MAC→SDI）；显式传入 mode 时优先使用传入值。

#### `private static Rectangle parseBound(String loc)`
- 行号: L298
- 描述: 解析 "x,y widthxheight" 格式的窗口位置字符串为 Rectangle（格式不符返回 null）。

#### `public static boolean isMDIMode()`
- 行号: L321
- 描述: 判断是否 MDI 模式（Windows 上多文档合一窗口；Unix/Mac 为 SDI 每文档一窗口）。

#### `public StatusBar getStatusBar()` / `public ToolBar getToolBar()`
- 行号: L327 / L333
- 描述: 返回本 TopLevel 的状态栏 / 工具栏。

#### `public EMenuBar.Instance getTheMenuBar()` / `public EMenuBar getEMenuBar()`
- 行号: L336 / L339
- 描述: 返回菜单栏实例（因 JFrame.getMenuBar() 已被占用而另命名）/ 返回其 EMenuBar 组。

#### `public static int getDoubleClickSpeed()`
- 行号: L345
- 描述: 返回系统双击间隔（毫秒）。

#### `public static Dimension getScreenSize()`
- 行号: L351
- 描述: 返回可用屏幕尺寸：MDI 返回桌面面板尺寸（未就绪时按主窗口尺寸估算），SDI 返回 `scrnSize`。

#### `public static void addToDesktop(JInternalFrame jif)` / `public static void removeFromDesktop(JInternalFrame jif)`
- 行号: L371 / L383
- 描述: **`[重要]`** 向 MDI 桌面添加/移除内部框架；桌面可见且非客户端线程时经 `SwingUtilities.invokeLater` 在 EDT 执行，否则立即执行。

#### `private static class ModifyToDesktopSafe implements Runnable`（内部类）
- 行号: L390
- 描述: EDT 安全的桌面增删操作。
- 方法: `ModifyToDesktopSafe(JInternalFrame jif, boolean add)` — L395，构造；`public void run()` — L397，add 时 `desktop.add(jif)` 并 `jif.show()`（捕获 Jake Baker 报告的 ClassCastException），否则 `desktop.remove(jif)`。

#### `public static Cursor getCurrentCursor()` / `public static synchronized void setCurrentCursor(Cursor cursor)`
- 行号: L417 / L419
- 描述: 读取/设置全局光标（set 时同步应用，见 `setCurrentCursorPrivate`）。

#### `private static synchronized void setCurrentCursorPrivate(Cursor cursor)`
- 行号: L425
- 描述: 把光标实际应用到 MDI 主框架及所有 WindowFrame。

#### `public static synchronized List<ToolBar> getToolBars()` / `public static synchronized List<EMenuBar.Instance> getMenuBars()`
- 行号: L438 / L451
- 描述: 返回所有工具栏/菜单栏：MDI 一个，SDI 每窗口一个（供批量刷新用）。

#### `public static synchronized void setBusyCursor(boolean on)`
- 行号: L470
- 描述: 忙光标（WAIT_CURSOR）覆盖/恢复：`on=true` 记标志并应用忙光标，`on=false` 恢复上次的正常光标。

#### `public static TopLevel getCurrentJFrame()`
- 行号: L487
- 描述: **`[重要]`** 返回当前顶层 JFrame（等价于 `getCurrentJFrame(true)`，无当前窗口时会新建）。

#### `public static TopLevel getCurrentJFrame(boolean makeNewFrame)`
- 行号: L497
- 描述: **`[重要]`** 返回当前顶层 JFrame：MDI 直接返回唯一 topLevel；SDI 取 `WindowFrame.getCurrentWindowFrame(makeNewFrame)` 的框架，无窗口时返回 null。

#### `public void finished()`
- 行号: L518
- 描述: **`[重要]`** 本 TopLevel 用毕清理：置空菜单栏、移除置顶监听、`menuBar.finished()`、移除并销毁工具栏与状态栏，最后 `super.dispose()`。主要为 SDI 模式让 GC 回收静态表中引用（MDI 下仅在退出时调用）。

#### `public static Rectangle[] getWindowAreas()`
- 行号: L549
- 描述: 返回可用窗口区域：MDI 一个（主窗口内容区），SDI 每个显示器一个。

#### `public static Rectangle[] getDisplays()`
- 行号: L569
- 描述: 返回每个显示器（显示头）的屏幕区域。

#### `public static void printError(boolean print, String msg)`
- 行号: L589
- 描述: `print=true` 时打印错误信息与堆栈，并写入活动日志（ActivityLogger）。

#### `private static class ReshapeComponentAdapter extends ComponentAdapter`（内部类）
- 行号: L598
- 描述: 监听 MDI 主窗口移动/缩放并把新位置写入 `User.cacheWindowLoc` 偏好。
- 方法: `componentMoved` L600 / `componentResized` L601 均调 `saveLocation`；`private void saveLocation(ComponentEvent e)` — L603，序列化 bounds 为 "x,y widthxheight" 保存。

#### `private static class WindowsEvents extends WindowAdapter`（内部类）
- 行号: L615
- 描述: 处理 MDI 模式主 JFrame 的关闭与激活事件。
- 方法:
  - `WindowsEvents()` — L617，构造。
  - `public void windowClosing(WindowEvent evt)` — L619，调 `FileMenu.quitCommand()` 退出程序。
  - `public void windowActivated(WindowEvent evt)` — L623，调 `ExternalEditing.updateEditors()` 检查外部文本编辑器。

---

## WindowContent
**文件**: `com/sun/electric/tool/user/ui/WindowContent.java`（136 行）
**职责**: 定义窗口右侧内容区的接口（与左侧 explorer 树相对）。实现类有 `EditWindow`（电路图）、`TextWindow`（文本）、`WaveformWindow`（波形）。窗口框架只依赖此接口与内容区交互。
**继承/实现**: `public interface WindowContent`
**关键字段**: 无（接口）。

### 方法
#### `public abstract void initTextSearch(String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, boolean highlightedOnly)`
- 行号: L55
- 描述: 初始化文本搜索：搜索串、大小写敏感、正则表达式、搜索对象集合、Code/Units 类型限制、是否只搜高亮区。

#### `public abstract boolean findNextText(boolean reverse)`
- 行号: L64
- 描述: 查找下一个（`reverse=true` 为反向）匹配，返回是否找到。

#### `public abstract void replaceText(String replace)` / `public abstract void replaceAllText(String replace)`
- 行号: L70 / L76
- 描述: 替换刚被 `findNextText()` 选中的文本 / 替换所有匹配文本。

#### `public abstract void finished()` / `repaint()` / `fullRepaint()` / `fillScreen()` / `zoomOutContents()` / `zoomInContents()` / `focusOnHighlighted()` / `panXOrY(int direction, double[] panningAmounts, int ticks)` / `centerCursor()`
- 行号: L78–L86
- 描述: 一批无 javadoc 的基础操作：窗口收尾 / 重绘 / 全量重绘 / 填充屏幕 / 缩小 / 放大 / 聚焦到高亮对象 / 按方向平移指定量 / 把光标居中。

#### `public abstract void setCell(Cell cell, VarContext context, WindowFrame.DisplayAttributes displayAttributes)` / `public abstract Cell getCell()`
- 行号: L88 / L89
- 描述: 切换内容区显示的 cell（带上下文与显示属性）/ 返回当前 cell。

#### `public abstract Highlighter getHighlighter()`
- 行号: L92
- 描述: 返回本窗口的 Highlighter。

#### `public abstract JPanel getPanel()`
- 行号: L101
- 描述: 返回内容区的顶层 JPanel（注意：不一定是实现类自身，如 EditWindow 本体外还包着滚动条）。

#### `public abstract void bottomScrollChanged(int e)` / `public abstract void rightScrollChanged(int e)`
- 行号: L102 / L103
- 描述: 底部/右侧滚动条位置变化回调。

#### `public abstract List<MutableTreeNode> loadExplorerTrees()` / `public abstract void loadTechnologies()` / `public abstract void setWindowTitle()`
- 行号: L104 / L105 / L106
- 描述: 加载本窗口在 Explorer 中显示的树节点 / 重载工艺列表 / 更新窗口标题。

#### `public abstract void setCursor(Cursor cursor)`
- 行号: L112
- 描述: 设置内容区光标（波形窗口的绘图面板不是 getPanel() 返回的面板，故需单独设置）。

#### `public abstract boolean initializePrinting(ElectricPrinter ep, PageFormat pageFormat)`
- 行号: L120
- 描述: 初始化打印；返回是否无错误。

#### `public abstract BufferedImage getPrintImage(ElectricPrinter ep)`
- 行号: L127
- 描述: 用离屏画布渲染打印图像（出错返回 null）。

#### `public abstract void writeImage(ElectricPrinter ep, String filePath)`
- 行号: L135
- 描述: 把窗口图像保存到文件（应在显示线程执行，3D 视图有此要求以保证光栅化正确）。

---

## WindowContextClass
**文件**: `com/sun/electric/tool/user/ui/WindowContextClass.java`（89 行）
**职责**: 早期"窗口内容类"的抽象基类：以 JPanel 形式持有 cell、Highlighter 与所属 WindowFrame，并实现 HighlightListener。注意：在 9.08.1 源码中已无任何类继承它（EditWindow 直接 extends JPanel 并实现 WindowContent），属遗留代码，修改 GUI 时一般不需要动它。
**继承/实现**: `public abstract class WindowContextClass extends JPanel implements HighlightListener`
**关键字段**:
- `cell (Cell)` — 窗口中显示的 cell（protected）
- `highlighter (Highlighter)` — 本窗口的高亮器（protected）
- `wf (WindowFrame)` — 包含本内容区的窗口框架（protected）

### 方法
#### `public WindowContextClass(Cell c, WindowFrame wf)`
- 行号: L42
- 描述: 构造：保存 cell 与 wf，创建 `SELECT_HIGHLIGHTER` 类型的 Highlighter 并向全局注册本对象为 HighlightListener。

#### `public Cell getCell()` / `public void setCell(Cell cell)`
- 行号: L56 / L58
- 描述: cell 的 getter/setter。

#### `public String composeTitle(String prefix, int pageNo)`
- 行号: L68
- 描述: 集中式窗口标题拼装：`prefix + cell.libDescribe()`，多页 cell 追加 " - Page n"，cell 不属于当前库时追加 " - Current library: xxx"；cell 为 null 时返回 "***NONE***"。（与 `WindowFrame.composeTitle` 逻辑基本一致，但无 isLinked 检查。）

---

## ExternalEditing
**文件**: `com/sun/electric/tool/user/ui/ExternalEditing.java`（402 行）
**职责**: 支持用外部文本编辑器编辑文本 cell：把文本导出到临时文件、启动外部编辑器进程、编辑器退出后回读临时文件并写回 TextWindow 或 Cell。
**继承/实现**: `public class ExternalEditing`（无继承）
**关键字段**:
- `cellName (String)` / `fileName (String)` / `lastModified (long)` — 被编辑 cell 名 / 临时文件名 / 初始修改时间戳
- `activeExternalEditors (static List<ExternalEditing>)` — 活动的外部编辑会话列表

### 方法
#### `ExternalEditing(String cellName, String fileName)`
- 行号: L57
- 描述: 包私有构造：记录 cell 名、临时文件名与文件当前时间戳，并加入活动会话列表。

#### `void finishedEditing()`
- 行号: L66
- 描述: 包私有：把本会话从活动列表移除。

#### `public static void updateEditors()`
- 行号: L71
- 描述: 轮询所有活动会话的临时文件时间戳，发现文件被外部修改时打印提示并调 `updateFromExternalEditor(false)` 回读。（在窗口激活事件中被调用，见 WindowFrame.WindowsEvents。）

#### `public static void editExternally()`
- 行号: L91
- 描述: **`[重要]`** 把当前文本窗口的 cell 导出到临时文件（扩展名按 View 决定）并用外部编辑器打开；对应 "Edit / Text / Edit Text Cell Externally" 命令。无文本窗口或未配置外部编辑器时报错提示。

#### `public static void editTextCellExternally(Cell cell)`
- 行号: L145
- 描述: **`[重要]`** 指定 cell（从 Explorer 触发）的版本：把 `cell.getTextViewContents()` 写入临时文件后启动外部编辑器。

#### `private static String getExtension(Cell cell)`
- 行号: L193
- 描述: 按 cell 视图返回临时文件扩展名：VERILOG→.v、DOC→.doc、VHDL→.vhdl、其他→.tmp。

#### `private static class EditExternally extends Thread`（内部类）
- 行号: L204
- 描述: 在独立线程中启动外部编辑器进程，使 Electric 主界面不被阻塞。
- 方法:
  - `EditExternally(String cellName, String externalEditor, String fileName)` — L209，构造：创建 ExternalEditing 会话并记录编辑器程序名。
  - `public void run()` — L215，按 OS 拼接命令（Windows: `cmd /c "编辑器" 文件`；Mac: 定位 .app 包内可执行文件路径；其他: 直接执行）并 `Runtime.exec` + `waitFor()`；进程结束后在 EDT 上回调 `ee.endExternalEditing()`。

#### `private void endExternalEditing()`
- 行号: L268
- 描述: 外部编辑结束后的收尾：从活动列表移除，并调 `updateFromExternalEditor(true)` 回读（带删除临时文件标志）。

#### `private void updateFromExternalEditor(boolean deleteTempFile)`
- 行号: L276
- 描述: 按 cell 名在数据库中找 Cell（找不到则报错忽略）；若某窗口正在显示该 cell 则创建 `UpdateTextWindow` 任务，否则创建 `UpdateTextCell` 任务。

#### `private static class UpdateTextWindow extends Job`（内部类）
- 行号: L300
- 描述: 把外部编辑结果读回正在显示的 TextWindow。
- 方法:
  - `UpdateTextWindow(String fileName, Cell cell, boolean deleteTempFile)` — L306，构造 CLIENT_EXAMINE 类型 Job 并启动。
  - `public boolean doIt() throws JobException` — L315，找到显示该 cell 的 TextWindow 后 `readTextCell(fileName)` 并跳到第 1 行；`deleteTempFile=true` 时删除临时文件（失败仅警告）。

#### `private static class UpdateTextCell extends Job`（内部类）
- 行号: L342
- 描述: 把外部编辑结果读回未被显示的 Cell（CHANGE 类型 Job）。
- 方法:
  - `UpdateTextCell(String fileName, Cell cell, boolean deleteTempFile)` — L348，构造 CHANGE 类型 Job 并启动。
  - `public boolean doIt() throws JobException` — L358，逐行读取临时文件为 String[]，调 `cell.setTextViewContents(lines, ep)` 写回；可选删除临时文件。

---

## InvisibleLayerConfiguration
**文件**: `com/sun/electric/tool/user/ui/InvisibleLayerConfiguration.java`（378 行）
**职责**: 管理用户保存的"不可见层配置"集合。每个配置有名称、若干不可见 Layer 列表（按 Technology 分组）；部分配置硬绑定到 SHIFT+0~SHIFT+9 快捷键。配置以自定义文本格式持久化在 `User.savedConfigurations` 偏好中。单例。
**继承/实现**: `public class InvisibleLayerConfiguration`（无继承）
**关键字段**:
- `NUM_CONFIGS (static final int)` — 配置槽位数（= 最大透明层数+1，注释注明为 13）
- `onlyOne (static)` — 单例实例
- `configurations (Map<String,List<String>>)` — 配置名 → 各工艺条目字符串列表（每条目形如 `(n)techName,layer1,layer2`，`(n)` 为硬绑定索引）

### 方法
#### `public static InvisibleLayerConfiguration getOnly()`
- 行号: L54
- 描述: 返回单例实例。

#### `private InvisibleLayerConfiguration()`
- 行号: L59
- 描述: **`[重要]`** 私有构造：从 `User.savedConfigurations` 解析已存配置（`[...]` 包围、`\t` 分隔工艺条目、`(n)` 标记硬绑定索引）；未被覆盖的槽位补默认配置（`getDefaultHardwiredName`）。

#### `private String getDefaultHardwiredName(int index)`
- 行号: L99
- 描述: 生成默认硬绑定配置名：index 0 为 "Set All Visible"，其余为 "Set Mn Visible"。

#### `public String getMenuName(int index)`
- 行号: L108
- 描述: 返回槽位 index 在菜单中显示的名称：有硬绑定配置时用配置名，否则用默认名。

#### `private void saveConfigurations()`
- 行号: L118
- 描述: 把所有配置序列化回 `User.savedConfigurations` 偏好，并调 `WindowMenu.setDynamicVisibleLayerMenus()` 刷新菜单。

#### `public boolean exists(String cName)`
- 行号: L142
- 描述: 判断指定名称的配置是否存在。

#### `public void addConfiguration(String cName, int hardWiredIndex, Technology tech, List<Layer> layers)`
- 行号: L155
- 描述: **`[重要]`** 添加/更新配置：把不可见层列表按 `techName,layer1,layer2`（跨工艺层带 `techName:layerName` 前缀）序列化；同名配置中同工艺条目被替换，否则追加；`hardWiredIndex>=0` 时条目带 `(n)` 前缀。最后保存。

#### `public void renameConfiguration(String cName, String newName)`
- 行号: L195
- 描述: 重命名配置（原名不存在则忽略），保存。

#### `public void deleteConfiguration(String cName, Technology tech)`
- 行号: L209
- 描述: 删除配置中指定工艺的条目；若删空但存在硬绑定索引则回退为纯 `(n)` 条目；完全为空则删除整个配置。保存。

#### `public List<String> getConfigurationNames()`
- 行号: L246
- 描述: 返回排序后的全部配置名列表。

#### `public List<Technology> getConfigurationTechnology(String cName)`
- 行号: L261
- 描述: 返回配置涉及的工艺列表（可能为空）。

#### `public String findHardWiredConfiguration(int index)`
- 行号: L289
- 描述: 查找硬绑定到 index（0~9）的配置名（无则返回 null）。

#### `public int getConfigurationHardwiredIndex(String cName)`
- 行号: L314
- 描述: 返回配置绑定的硬索引（即其 SHIFT+0~9 快捷键编号，无绑定返回 -1）。

#### `public Map<Technology,List<Layer>> getConfigurationValue(String cName)`
- 行号: L340
- 描述: **`[重要]`** 解析配置为 `Map<Technology, List<Layer>>`：键为工艺，值为该工艺下不可见的层列表；不在 map 中的工艺视为全部层可见。跨工艺层名按 `tech:layer` 解析。

---

## ElectricPrinter
**文件**: `com/sun/electric/tool/user/ui/ElectricPrinter.java`（206 行）
**职责**: 定义打印机接口：实现 `Printable` 与 `ImageObserver`，把 EditWindow（单页图像）或 TextWindow（分页文本）内容渲染到打印页；同时被图像导出（writeImage）复用，携带打印相关的偏好快照（DPI、显示算法、greeking 参数等）。
**继承/实现**: `public class ElectricPrinter implements Printable, ImageObserver`
**关键字段**:
- `context (WindowContent)` — 被打印的内容区（可为 null）
- `wnd (EditWindowSmall)` — EditWindow 状态快照（`EditWindow.getCurrent()` 的轻量副本；context 为 null 时为空快照）
- `pageFormat (PageFormat)` / `printJob (PrinterJob)` — 页面格式 / 打印任务
- `img (BufferedImage)` / `graphics (Graphics)` / `oldSize (Dimension)` — 离屏图像、绘图上下文、旧窗口尺寸
- `desiredDPI (int)` — 打印分辨率（来自 `IOTool.getPrintResolution()`）
- `gp (GraphicsPreferences)` / `dp (AbstractDrawing.DrawingPreferences)` — 图形偏好快照
- `renderArea (Rectangle2D)` / `displayAlgorithm` / `greekImages` / `greekSizeLimit` / `greekCellSizeLimit` — 渲染区域与显示算法、greeking 参数快照
- `allStrings (String[])` / `startLine` / `startChar` / `printFont` / `pageHeight` / `fontHeight` / `yOffset` / `linesPerPage` / `startLineAtPage` / `startCharAtPage` / `startPageNumber` / `frc` — 文本打印的分页状态（起始行/字符、字体度量、每页行数、记忆位置等）

### 方法
#### `public ElectricPrinter(WindowContent context, PageFormat pageFormat, PrinterJob printJob)`
- 行号: L83
- 描述: 构造：context 为 null 时创建空 EditWindowSmall 并把 context 置 null；否则从 `EditWindow.getCurrent()` 生成快照并保存 context。

#### getter/setter（合并，L100–L116）
- 行号: L100–L116
- 描述: `getBufferedImage()/setBufferedImage()`、`getGraphics()`、`getPageFormat()`、`getPrintJob()`、`getDesiredDPI()`、`setOldSize()/getOldSize()`、`setRenderArea()/getRenderArea()`、`getGraphicsPreferences()`、`getDisplayAlgorithm()`、`isUseCellGreekingImages()`、`getGreekSizeLimit()`、`getGreekCellSizeLimit()`、`getWindow()`（返回 EditWindow0 快照）、`getDrawingPreferences()`。

#### `public int print(Graphics g, PageFormat pf, int page) throws PrinterException`
- 行号: L118
- 描述: **`[重要]`** Printable 核心实现。TextWindow 分支：第 0 页初始化（把文本转 String[]、设 Helvetica 10 号字体、计算每页行数），按页拆分输出，行超宽时按 GlyphVector 逻辑宽度自动断行并记忆断点，跨页续打；EditWindow 分支：单页，page==0 时记录 Graphics/PageFormat 并调 `context.getPrintImage(this)` 渲染。返回 `Printable.PAGE_EXISTS` 或 `NO_SUCH_PAGE`。

#### `public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height)`
- 行号: L202
- 描述: ImageObserver 实现，恒返回 false（3D 视图需要此接口）。
