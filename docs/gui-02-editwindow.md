# 主编辑窗口（EditWindow）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.ui。本文件由 AI 生成。

## 模块概述

EditWindow 是 Electric 用户 GUI 的核心显示组件：一个用于显示电路（Cell 内容）的编辑窗口，占据了主窗口框架（WindowFrame）的主体区域（位于资源管理器面板右侧）。它实现了 `WindowContent` 接口，由 `WindowFrame` 持有和管理（每个 WindowFrame 恰好一个 WindowContent），顶层窗口则由 `TopLevel` 管理。EditWindow 本身是一个 `JPanel`，但它暴露给外界的根面板是包含自身与两个滚动条的 `overall` 面板。EditWindow 不直接渲染版图内容，而是把离屏渲染委托给 `AbstractDrawing`（默认实现为 `PixelDrawing`）在后台 `RenderJob` 线程中完成，自己只负责把离屏图像与网格、高亮、拖拽框、单元边框（Cell Frame）、in-place 阴影等合成到屏幕上（`paintComponent`）。它同时管理三个 `Highlighter`（选择高亮、鼠标悬停高亮、标尺高亮），同一 Cell 的多个窗口共享同一组 Highlighter。EditWindow 还承担坐标系转换（屏幕坐标与数据库坐标互转）、缩放平移（scale/offset，滚动条同步）、层次下降/上升（downHierarchy/upHierarchy，含 in-place 编辑）、文本查找替换、跨探针（cross-probe）电平显示、打印与 PNG 导出等功能。窗口的缩放/平移历史由内部类 `EditWindowFocusBrowser`（类似浏览器历史）保存，Cell 切换历史则由 WindowFrame 管理。

---

## EditWindow

**文件**: `com/sun/electric/tool/user/ui/EditWindow.java`（4143 行）
**职责**: 定义显示电路内容的编辑窗口。作为 WindowContent 出现在窗口主体部分；负责内容显示（委托 AbstractDrawing 渲染）、滚动条控制、鼠标/键盘事件转发（转发给 WindowFrame 的监听器）、缩放平移与视口历史、层次遍历、文本查找替换、跨探针显示、打印与图像导出。
**继承/实现**:
```java
public class EditWindow extends JPanel
	implements EditWindow_, WindowContent, MouseMotionListener, MouseListener, MouseWheelListener, KeyListener,
		HighlightListener, DatabaseChangeListener
```
（类头 javadoc：L149-153）

**关键字段**:
- `scale, scale_, factorX, factorY (double/float)` — 当前窗口缩放比例及屏幕/数据库坐标换算因子（scale_ = scale/DBMath.GRID）
- `scaleRequested (double)` — 请求中的缩放比例（延迟到下一次渲染生效）
- `globalTextScale (double)`、`defaultFont (String)` — 全局文字缩放倍数与默认字体名
- `offx, offy (double)`、`offxRequested, offyRequested (double)` — 当前/请求中的窗口偏移（数据库坐标）
- `sz (Dimension)`、`szHalfWidth, szHalfHeight (int)` — 窗口像素尺寸与半宽半高
- `drawing (AbstractDrawing)` — 显示缓存/渲染引擎（默认 PixelDrawing，由 setDrawingAlgorithm 按用户设置创建）
- `cell (Cell)`、`pageNumber (int)` — 窗口显示的单元与多页原理图页码（0 基）
- `inPlaceTextObjects (List<GetInfoText.EditInPlaceListener>)` — 本窗口的 in-place 文本编辑对象栈
- `inPlaceDisplay (boolean)`、`intoCell/outofCell (FixpTransform)`、`inPlaceDescent (List<NodeInst>)` — in-place（就地下降）显示标志、屏幕↔当前单元变换、下降路径
- `repaintRequest (volatile boolean)`、`fullInstantiateBounds (ERectangle)` — 重绘请求标志与整层实例化（peek）边界
- `cellVarContext (VarContext)` — 当前 Cell 的变量上下文（层次位置）
- `wf (WindowFrame)`、`overall (JPanel)` — 所属窗口框架与顶层面板（含滚动条）
- `bottomScrollBar, rightScrollBar (JScrollBar)` — 底部水平/右侧垂直滚动条
- `showGrid, showGridWarning (boolean)`、`gridXSpacing, gridYSpacing (double)` — 网格显示开关、分辨率警告标志、网格间距
- `doingAreaDrag (boolean)`、`startDrag, endDrag (Point)` — 区域拖选状态与起止屏幕点
- `showPopupCloud (boolean)` — 弹出云提示显示开关（当前绘制代码被禁用）
- `lv (LayerVisibility)` — 本窗口的层可见性设置
- `highlighter, mouseOverHighlighter, rulerHighlighter (Highlighter)` — 选择/悬停/标尺三个高亮器（同一 Cell 的窗口间共享）
- `textInCell (RTNode<Highlighter.TextHighlightBound>)` — Cell 内全部可选中文本的 R 树
- `viewBrowser (EditWindowFocusBrowser)` — 缩放/平移焦点历史浏览器
- `lock (static final Object)`、`runningNow (static RenderJob)` — 渲染作业调度锁与当前运行中的渲染作业（全类共享）
- `pulsatingTimer (Timer)` — 错误高亮闪烁定时器（100ms）
- `SCROLLBARRESOLUTION = 200`、`scrollRangeMult = 100`、`scrollPagePercent = 0.2`、`ignoreScrollChange (boolean)` — 滚动条分辨率/精度与忽略程序化滚动回事件标志
- `selectionLine, inPlaceMarker (static BasicStroke)` — 选择框虚线画笔与 in-place 边界粗线画笔
- `editWindowDropTarget (static EditWindowDropTarget)` — 从资源管理器树拖入单元名的 DropTarget
- `lastXPosition, lastYPosition (int)` — 最近一次鼠标位置（供 centerCursor 使用）
- `textSearch (StringSearch)`、`crossProbeObjects (List<CrossProbe>)` — 文本查找状态与跨探针显示对象列表
- `cache (LRUCache<Map.Entry<String,Font>,Rectangle2D>)` — 字形边界缓存（16K 条）

### 方法

以下按源码声明顺序排列，小节标题对应源码中的分节注释。

#### 构造与工厂

#### `private EditWindow(Cell cell, WindowFrame wf, Dimension approxSZ)`
- 行号: L224
- 描述: **`[重要]`** 构造函数。初始化尺寸/缩放/偏移、创建 overall 面板与两个滚动条（挂 ScrollAdjustmentListener）、设置 DropTarget、安装高亮器与键鼠监听器、启动错误高亮闪烁定时器；若 wf 非空则注册数据库变更监听并调用 setCell 显示初始 Cell。

#### `private void setDrawingAlgorithm()`
- 行号: L316
- 描述: 按用户显示算法设置创建/重建 `drawing`（AbstractDrawing.createDrawing），并同步 LayerTab 的显示算法标志（是否有透明度）。

#### `private void installHighlighters()`
- 行号: L323
- 描述: 若同一 Cell 已在其他窗口显示则共享其三个 Highlighter，否则新建（SELECT/MOUSEOVER/RULER 三类）；并注册本对象自身的 Key/Mouse/MouseMotion/MouseWheel 监听器（与 uninstallHighlighters 配对，在 finished 中必须移除）。

#### `private void uninstallHighlighters()`
- 行号: L358
- 描述: 移除键鼠监听器；若 Highlighter 不再被其他显示同一 Cell 的窗口使用，则将其 delete。

#### `public static EditWindow CreateElectricDoc(Cell cell, WindowFrame wf, Dimension approxSZ)`
- 行号: L397
- 描述: **`[重要]`** 工厂方法，创建显示指定 Cell 的新 EditWindow。

#### 事件监听（Event Listeners）

#### `public void mousePressed(MouseEvent evt)`
- 行号: L409
- 描述: 请求焦点、通知 userCommandIssued、记录鼠标位置、刷新状态栏坐标，然后转发给 WindowFrame 的鼠标监听器。

#### `public void mouseReleased(MouseEvent evt)`
- 行号: L419
- 描述: 记录位置与坐标显示后转发给 WindowFrame 的鼠标监听器。

#### `public void mouseClicked(MouseEvent evt)`
- 行号: L427
- 描述: 记录位置后转发给 WindowFrame 的鼠标监听器。

#### `public void mouseEntered(MouseEvent evt)`
- 行号: L434
- 描述: 记录位置与坐标显示后转发给 WindowFrame 的鼠标监听器。

#### `public void mouseExited(MouseEvent evt)`
- 行号: L442
- 描述: 记录位置后转发给 WindowFrame 的鼠标监听器。

#### `public void mouseMoved(MouseEvent evt)`
- 行号: L450
- 描述: 记录位置与坐标显示后转发给 WindowFrame 的鼠标移动监听器。

#### `public void mouseDragged(MouseEvent evt)`
- 行号: L458
- 描述: 记录位置与坐标显示后转发给 WindowFrame 的鼠标移动监听器。

#### `private void showCoordinates(MouseEvent evt)`
- 行号: L465
- 描述: 把鼠标屏幕坐标转为数据库坐标、对齐网格并写入 StatusBar；若用户开启层次坐标显示且 VarContext 非全局，则构造并显示层次路径坐标。

#### `public void mouseWheelMoved(MouseWheelEvent evt)`
- 行号: L511
- 描述: 直接转发给 WindowFrame 的滚轮监听器。

#### `public void keyPressed(KeyEvent evt)`
- 行号: L515
- 描述: 通知 userCommandIssued 后转发给 WindowFrame 的按键监听器。

#### `public void keyReleased(KeyEvent evt)` / `public void keyTyped(KeyEvent evt)`
- 行号: L522 / L525
- 描述: 直接转发给 WindowFrame 的按键监听器。

#### `public void highlightChanged(Highlighter which)`
- 行号: L528
- 描述: HighlightListener 回调；若变化的正是本窗口选择高亮器则触发 repaint。

#### `public void highlighterLostFocus(Highlighter highlighterGainedFocus)`
- 行号: L539
- 描述: HighlightListener 回调；当前为空实现（保留接口）。

#### `public Point getLastMousePosition()`
- 行号: L541
- 描述: 返回最近一次记录的鼠标位置。

#### 拖放（从资源管理器树拖入单元名）

#### `private Cell whichCellInGroup(CellGroup group)`
- 行号: L554
- 描述: 从被拖拽的 CellGroup 中挑选适合本窗口的视图：跳过 DOC，优先匹配当前窗口视图（如原理图窗口取 ICON/SCHEMATIC）。

#### `public void showDraggedBox(Object toDraw, int oldx, int oldy, int rotation)`
- 行号: L581
- 描述: **`[重要]`** 清除高亮后按拖入对象（NodeInst/NodeProto/CellGroup/PrimitiveNode）计算旋转、尺寸并高亮显示将被放置的实例轮廓；必要时先 zoomWindowToFitCellInstance 缩放窗口，rotation 非零时显示旋转角度消息。

#### `public void zoomWindowToFitCellInstance(NodeProto np)`
- 行号: L671
- 描述: 当窗口为空（无弧、无节点或仅单元中心标记）且放置的是大于窗口的 Cell 实例时，自动缩小比例以容纳该实例。

#### 信息（Information）

#### `public JPanel getPanel()`
- 行号: L912
- 描述: 返回顶层 JPanel（overall，含滚动条），供 WindowFrame 放置。

#### `public Point getScreenLocationOfCorner()`
- 行号: L919
- 描述: 返回 overall 面板在屏幕上的位置。

#### `public static EditWindow getCurrent()`
- 行号: L925
- 描述: 返回当前窗口框架中的 EditWindow（无则返回 null）。

#### `public static EditWindow needCurrent()`
- 行号: L938
- 描述: 同 getCurrent，但无当前编辑窗口时打印错误信息并返回 null。

#### EditWindow 基本方法

#### `public Cell getCell()`
- 行号: L956
- 描述: 返回本窗口显示的 Cell。

#### `public void setMultiPageNumber(int pageNumber)`
- 行号: L963
- 描述: 设置多页原理图页码（0 基）；页码变化时更新窗口标题并 fillScreen。

#### `public int getMultiPageNumber()`
- 行号: L976
- 描述: 返回多页原理图页码。

#### `public boolean isInPlaceEdit()`
- 行号: L984
- 描述: 返回是否处于 in-place（就地下降）显示模式。

#### `public Cell getInPlaceEditTopCell()`
- 行号: L993
- 描述: 返回 in-place 显示的顶层 Cell；非 in-place 时返回当前 cell。

#### `public List<NodeInst> getInPlaceEditNodePath()`
- 行号: L1001
- 描述: 返回 in-place 编辑的 NodeInst 下降路径列表。

#### `private void setInPlaceEditNodePath(WindowFrame.DisplayAttributes da)`
- 行号: L1009
- 描述: 从 DisplayAttributes 恢复 in-place 路径与正反变换，并据此设置 inPlaceDisplay 标志。

#### `public FixpTransform getInPlaceTransformIn()` / `public FixpTransform getInPlaceTransformOut()`
- 行号: L1022 / L1030
- 描述: 返回顶层 Cell → 当前 Cell（in）与当前 Cell → 顶层 Cell（out）的变换矩阵。

#### `public Highlighter getHighlighter()` / `getMouseOverHighlighter()` / `getRulerHighlighter()`
- 行号: L1037 / L1043 / L1049
- 描述: 返回选择高亮器 / 鼠标悬停高亮器 / 标尺高亮器。

#### `public RTNode<Highlighter.TextHighlightBound> getTextInCell()` / `setTextInCell(RTNode<Highlighter.TextHighlightBound> tic)`
- 行号: L1055 / L1061
- 描述: 获取/设置 Cell 中全部文本的 R 树（用于文本命中测试）。

#### `public WindowFrame getWindowFrame()`
- 行号: L1067
- 描述: 返回包含本 EditWindow 的 WindowFrame。

#### `public void setCell(Cell cell, VarContext context, WindowFrame.DisplayAttributes displayAttributes)`
- 行号: L1073
- 描述: **`[重要]`** 设置窗口显示的 Cell。displayAttributes 为 null 时按当前缩放/偏移构造并令 fillTheScreen=true；重新计算窗口尺寸后委托 showCell 完成实际切换。

#### `private void showCell(Cell cell, VarContext context, boolean fillTheScreen, WindowFrame.DisplayAttributes displayAttributes)`
- 行号: L1097
- 描述: **`[重要]`** 实际切换 Cell：先保存当前 Cell 历史（wf.saveCurrentCellHistoryState）、卸载高亮器；更新 cell/页码/VarContext、库当前 Cell、显示算法；重装高亮器、清空视点历史、更新标题；必要时自动切换工艺；fillTheScreen 时 fillScreen，否则恢复保存的缩放/偏移；检查单元日期、清除跨探针、刷新状态栏。

#### `public void setWindowTitle()`
- 行号: L1150
- 描述: 委托 wf.composeTitle 设置窗口标题（含 Cell 名与页码）。

#### `public static EditWindow findWindow(Cell cell)`
- 行号: L1161
- 描述: 遍历所有窗口，返回显示给定 Cell 的 EditWindow（无则 null）。

#### `public static EditWindow showEditWindowForCell(Cell c, VarContext varC)`
- 行号: L1180
- 描述: **`[重要]`** 前置显示给定 Cell（且 VarContext 匹配）的窗口；不存在则用 WindowFrame.createEditWindow 新建并 setCell。

#### `public List<MutableTreeNode> loadExplorerTrees()`
- 行号: L1209
- 描述: 委托 wf.loadDefaultExplorerTree 加载资源管理器树。

#### `public void loadTechnologies()`
- 行号: L1215
- 描述: 工艺库加载后刷新 LayerVisibility 并重建显示算法。

#### `public void finished()`
- 行号: L1225
- 描述: **`[重要]`** 窗口关闭时由 WindowFrame 调用：卸载高亮器与键鼠监听器、停止闪烁定时器、移除数据库变更与高亮监听器、释放本窗口高亮。

#### 滚动（Scrolling）

#### `public static int getScrollBarResolution()`
- 行号: L1242
- 描述: 返回滚动条分辨率常量 SCROLLBARRESOLUTION（200）。

#### `public JScrollBar getBottomScrollBar()` / `public JScrollBar getRightScrollBar()`
- 行号: L1272 / L1278
- 描述: 返回底部水平滚动条 / 右侧垂直滚动条。

#### 重绘（Repaint）

#### `public void paintComponent(Graphics graphics)`
- 行号: L1288
- 描述: **`[重要]`** 核心绘制方法：无 Cell 时画背景与提示文字；否则取 drawing 的离屏图像（失败则调度 fullRepaint）、更新滚动条位置、绘制跨探针显示、单元边框（drawCellFrame）、标尺高亮、拖选框（showDragBox）、弹出云、in-place 阴影与红框；若缩放/偏移/尺寸仍与请求值不一致则再次 fullRepaint。

#### `public void addInPlaceTextObject(GetInfoText.EditInPlaceListener tl)`
- 行号: L1439
- 描述: 压入 in-place 文本编辑对象并把其文本组件加入本面板。

#### `public GetInfoText.EditInPlaceListener getInPlaceTextObject()`
- 行号: L1449
- 描述: 返回栈顶的 in-place 文本编辑对象（栈空时 null）。

#### `public void removeInPlaceTextObject(GetInfoText.EditInPlaceListener tl)`
- 行号: L1459
- 描述: 移除指定 in-place 文本编辑对象并摘除其组件。

#### `public void removeAllInPlaceTextObjects()`
- 行号: L1469
- 描述: 关闭本窗口所有 in-place 文本编辑（平移/缩放时调用，防止文本错位）。

#### `public static void displayAlgorithmChanged()`
- 行号: L1483
- 描述: 全局显示算法改变：所有 EditWindow 重建 drawing 并 fullRepaint。

#### `public LayerVisibility getLayerVisibility()`
- 行号: L1496
- 描述: 返回本窗口层可见性设置。

#### `public EditingPreferences getEditingPreferences()` / `public GraphicsPreferences getGraphicsPreferences()`
- 行号: L1500 / L1504
- 描述: 委托 UserInterfaceMain 返回全局编辑偏好 / 图形偏好。

#### `public void setLayerVisibility(LayerVisibility lv)`
- 行号: L1508
- 描述: 设置层可见性：若影响可见对象集合则 fullRepaint 否则 repaint，并同步到 wf 的 3D 层可见性。

#### `public static void repaintAllContents()`
- 行号: L1520
- 描述: 所有 EditWindow 重新渲染内容（fullRepaint）。

#### `public static void setLayerVisibilityAll(LayerVisibility lv)`
- 行号: L1535
- 描述: 为所有 EditWindow 设置同一 LayerVisibility。

#### `public static void repaintAll()`
- 行号: L1550
- 描述: 所有 EditWindow 仅重画（不重新渲染离屏内容）。

#### `public void fullRepaint()`
- 行号: L1566
- 描述: 请求重新渲染本窗口全部内容（repaintContents(null, false)）。

#### `public void repaintContents(ERectangle bounds, boolean fullInstantiate)`
- 行号: L1576
- 描述: 请求重新渲染（bounds 为 null 表示全部；fullInstantiate 表示展开至层次底部用于 peek），使用默认绘制/图形偏好。

#### `private void repaintContents(ERectangle bounds, boolean fullInstantiate, AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp)`
- 行号: L1585
- 描述: 若已有渲染作业在跑且本窗口已排队则直接返回；fullInstantiate 时把边界变换到顶层单元坐标存入 fullInstantiateBounds，然后 invokeRenderJob 启动渲染。

#### `public static void invokeRenderJob()`
- 行号: L1612
- 描述: 以默认偏好启动渲染作业（内部用，无特定窗口时只处理已排队窗口）。

#### `private static void invokeRenderJob(EditWindow wnd, AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp)`
- 行号: L1616
- 描述: 给目标窗口打上 repaintRequest 并中止其当前渲染；若已有 RenderJob 运行则标记 hasTasks 复用，否则创建新 RenderJob 并 startJob。

#### `public void testJogl()`
- 行号: L1725
- 描述: 委托 drawing.testJogl 测试 JOGL 渲染。

#### `void opacityChanged()`
- 行号: L1729
- 描述: （包内可见）通知 drawing 透明度设置已变化。

#### 跨探针电平显示（Simulation CrossProbe）

#### `public void clearCrossProbeLevels()`
- 行号: L1750
- 描述: 清空本窗口的跨探针显示对象列表（切换 Cell 时调用）。

#### `public boolean hasCrossProbeData()`
- 行号: L1761
- 描述: 是否存在跨探针显示数据。

#### `public void addCrossProbeLine(Point2D start, Point2D end, Color color)` / `public void addCrossProbeBox(Rectangle2D box, Color color)`
- 行号: L1775 / L1792
- 描述: 向跨探针列表添加一条线 / 一个填充矩形（来自 WaveformWindow 的仿真值显示）。

#### `private void showCrossProbeLevels(Graphics g)`
- 行号: L1801
- 描述: 在 paintComponent 中绘制所有跨探针线/框。

#### 拖选框（Drag Box）

#### `public boolean isDoingAreaDrag()` / `setDoingAreaDrag()` / `clearDoingAreaDrag()`
- 行号: L1828 / L1830 / L1832
- 描述: 查询 / 置位 / 清除区域拖选状态标志 doingAreaDrag。

#### `public Point getStartDrag()` / `setStartDrag(int x, int y)` / `getEndDrag()` / `setEndDrag(int x, int y)`
- 行号: L1834 / L1836 / L1838 / L1840
- 描述: 获取/设置拖选框起止屏幕点。

#### `private void showDragBox(Graphics g)`
- 行号: L1842
- 描述: 用 selectionLine 虚线画笔绘制拖选矩形（颜色取用户高亮色）。

#### 单元边框（Cell Frame）

#### `private void drawCellFrame(Graphics g)`
- 行号: L1862
- 描述: 构造 DisplayedFrame 并渲染单元边框（原理图图框、标题栏等）。

#### 网格（Grid）

#### `public void setGrid(boolean showG)`
- 行号: L1964
- 描述: 设置网格显示开关（同时复位分辨率警告标志）并 fullRepaint。

#### `public boolean isGrid()`
- 行号: L1977
- 描述: 返回网格是否显示。

#### `public void printGridWarning()`
- 行号: L1983
- 描述: 若网格开启但因分辨率过低未绘制，打印一次警告（由绘制引擎回调）。

#### `public double getGridXSpacing()` / `setGridXSpacing(double spacing)` / `getGridYSpacing()` / `setGridYSpacing(double spacing)`
- 行号: L1997 / L2002 / L2009 / L2014
- 描述: 获取/设置网格 X、Y 方向点间距。

#### `public Rectangle2D displayableBounds()`
- 行号: L2020
- 描述: 返回当前窗口可视范围对应的数据库坐标矩形。

#### 文本查找（Text Search）

#### `public void initTextSearch(String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, boolean highlightedOnly)`
- 行号: L2505
- 描述: 初始化文本查找：把搜索参数委托给内部 StringSearch（查找节点名/弧名/端口名/变量文本等，可限定高亮区域内）。

#### `private static String repeatChar(char c, int num)` / `private static void printFind(StringsInCell sic)` / `private static void printChange(StringsInCell sic, String newString)`
- 行号: L2512 / L2518 / L2632
- 描述: 查找结果控制台输出的辅助方法（重复字符、打印"Found"与"Change"带 ^ 指示符）。

#### `public boolean findNextText(boolean reverse)`
- 行号: L2534
- 描述: 查找下一个（reverse 为 true 时反向）匹配文本并高亮，返回是否找到。

#### `public void replaceText(String replace)`
- 行号: L2544
- 描述: 替换刚由 findNextText 定位的文本：标记 replaced 后启动 ReplaceTextJob 执行。

#### `public void replaceAllText(String replace)`
- 行号: L2562
- 描述: 全部替换：剔除已替换项、标记全部后启动 ReplaceAllTextJob 执行。

#### 弹出云（Popup Cloud）

#### `public boolean getShowPopupCloud()` / `setShowPopupCloud(List<String> text, Point2D point)` / `clearShowPopupCloud()`
- 行号: L2645 / L2647 / L2654
- 描述: 查询 / 置位 / 清除弹出云标志（绘制代码当前被禁用，见 drawPopupCloud）。

#### `private void drawPopupCloud(Graphics2D g)`
- 行号: L2656
- 描述: 空实现（原绘制代码被注释禁用，作者注：待决定是否有用）。

#### 缩放与平移（Zoom and Pan）

#### `public Dimension getScreenSize()`
- 行号: L2680
- 描述: 返回窗口像素尺寸 sz。

#### `public double getScale()` / `getGlobalTextScale()` / `getDefaultFont()`
- 行号: L2687 / L2694 / L2701
- 描述: 返回当前缩放比例 / 全局文字缩放 / 默认字体名。

#### `public void setGlobalTextScale(double gts)`
- 行号: L2707
- 描述: 设置全局文字缩放倍数。

#### `public void setScale(double scale)`
- 行号: L2714
- 描述: **`[重要]`** 请求新缩放比例（≤0 抛 IllegalArgumentException）：仅写入 scaleRequested 并关闭所有 in-place 文本编辑，实际缩放在下一次渲染时生效。

#### `public Point2D getOffset()`
- 行号: L2727
- 描述: 返回当前窗口偏移（数据库坐标）。

#### `public Point2D getScheduledOffset()`
- 行号: L2734
- 描述: 返回已排队尚未生效的最终偏移。

#### `public void setOffset(Point2D off)`
- 行号: L2741
- 描述: **`[重要]`** 请求新偏移：写入 offxRequested/offyRequested 并关闭所有 in-place 文本编辑，实际平移在渲染时生效。

#### `private void setScreenBounds(Rectangle2D bounds)`
- 行号: L2748
- 描述: 计算使给定数据库区域以 90% 余量充满窗口的缩放与偏移，调用 setScale/setOffset（注意会改变请求值，getBoundsInWindow 中依赖此副作用）。

#### `public Rectangle2D getDisplayedBounds()`
- 行号: L2764
- 描述: 返回当前显示的数据库范围矩形。

#### `public void setScrollPosition()`
- 行号: L2780
- 描述: 在 EDT 上更新滚动条位置（非 EDT 线程则 invokeLater 调度 setScrollPositionUnsafe）。

#### `private void setScrollPositionUnsafe()`
- 行号: L2794
- 描述: 按 Cell 边界与当前可视范围设置滚动条的 value/extent/min/max 与增量；设置 ignoreScrollChange 防止程序化修改触发滚动事件回环（允许滚出 Cell 边界 20%）。

#### `public void bottomScrollChanged(int value)` / `public void rightScrollChanged(int value)`
- 行号: L2840 / L2860
- 描述: 滚动条值变化回调（忽略程序化变化）：换算为新的 X/Y 偏移、setOffset、更新焦点历史并 fullRepaint。

#### `public void focusScreen(Rectangle2D bounds)`
- 行号: L2883
- 描述: **`[重要]`** 平移+缩放使给定区域充满屏幕：in-place 模式下先把边界变换到顶层单元坐标；调用 setScreenBounds、更新滚动条、保存焦点历史并 fullRepaint。

#### `public Rectangle2D getBoundsInWindow()`
- 行号: L2910
- 描述: 计算 Cell 在窗口中的完整边界（含图框、大字文本、多页偏移、标题框），供 fillScreen 等使用；文本边界通过构造 EditWindowSmall 迭代 2 次逼近。

#### 高亮委托（EditWindow_ 接口实现）

以下方法均直接委托给选择高亮器 `highlighter`（L2980-3073）：

#### `public Highlight addElectricObject(ElectricObject eObj, Cell cell)`
- 行号: L2980
- 描述: 高亮一个 ElectricObject。

#### `public Rectangle2D getHighlightedArea()`
- 行号: L2986
- 描述: 返回高亮对象覆盖区域。

#### `public Highlight addHighlightArea(Rectangle2D rect, Cell cell)`
- 行号: L2992
- 描述: 高亮一个矩形区域。

#### `public Highlight addHighlightLine(Point2D pt1, Point2D pt2, Cell cell, boolean thick, boolean isError)`
- 行号: L2998
- 描述: 高亮一条线（可粗线、可标记为错误）。

#### `public Highlight addHighlightMessage(Cell cell, String message, Point2D loc)`
- 行号: L3004
- 描述: 在指定位置显示高亮消息。

#### `public Highlight addHighlightText(ElectricObject eObj, Cell cell, Variable.Key varKey)`
- 行号: L3010
- 描述: 高亮对象上的文本变量。

#### `public ElectricObject getOneElectricObject(Class<?> clz)`
- 行号: L3016
- 描述: 返回高亮中第一个指定类型的 ElectricObject。

#### `public List<Geometric> getHighlightedEObjs(boolean wantNodes, boolean wantArcs)`
- 行号: L3019
- 描述: 返回高亮中的节点/弧列表。

#### `public Set<Network> getHighlightedNetworks()`
- 行号: L3025
- 描述: 返回高亮涉及的网络集合。

#### `public Point2D getHighlightOffset()` / `public void setHighlightOffset(int dX, int dY)`
- 行号: L3031 / L3037
- 描述: 获取/设置高亮偏移（拖动时高亮随鼠标移动的量）。

#### `public List<Highlight> saveHighlightList()`
- 行号: L3043
- 描述: 保存当前高亮列表快照。

#### `public void restoreHighlightList(List<Highlight> list)`
- 行号: L3052
- 描述: 恢复之前保存的高亮列表。

#### `public void removeHighlight(Highlight h)`
- 行号: L3058
- 描述: 移除单个高亮。

#### `public void clearHighlighting()` / `public void finishedHighlighting()`
- 行号: L3064 / L3070
- 描述: 清空高亮 / 结束一批高亮操作（通知监听器）。

#### `public void fillScreen()`
- 行号: L3079
- 描述: **`[重要]`** 平移缩放使整个 Cell 充满窗口：非文本视图取 getBoundsInWindow 后 focusScreen；文本视图仅保存焦点并 repaint。

#### `public void zoomOutContents()` / `public void zoomInContents()`
- 行号: L3095 / L3104
- 描述: 缩放减半 / 加倍，保存焦点历史并 fullRepaint。

#### `public void focusOnHighlighted()`
- 行号: L3113
- 描述: 平移缩放使高亮对象充满屏幕。

#### `public EditWindowFocusBrowser getSavedFocusBrowser()`
- 行号: L3123
- 描述: 返回本窗口的视点历史浏览器 viewBrowser。

#### 层次遍历（Hierarchy Traversal）

#### `public VarContext getVarContext()`
- 行号: L3132
- 描述: 返回当前 Cell 的 VarContext。

#### `public void downHierarchy(boolean keepFocus, boolean newWindow, boolean inPlace)`
- 行号: L3140
- 描述: **`[重要]`** 下降到高亮单元实例内部：取高亮 NodeInst（或 PortInst 所在实例）、确定目标 Cell（原理图/图标等价视图、in-place 时取 icon）、按 keepFocus 换算偏移、组装 in-place 下降路径；多个 Nodable 可选时弹窗询问（isArrayedContextMatter 判断）；newWindow 时新建窗口否则复用本窗口并记录 Cell 历史；若高亮的是端口则在下降后高亮对应 Export。

#### `public static boolean isArrayedContextMatter(Nodable no)`
- 行号: L3282
- 描述: 判断下降进入数组化实例时是否需要询问用户选择索引：用户开启提示、存在波形窗口、或参数含 "LE.getdrive" 代码时返回 true。

#### `public void upHierarchy(boolean keepFocus)`
- 行号: L3311
- 描述: **`[重要]`** 上升到父单元：确定当前网络对应的选中 Export；若有历史记录则经 wf.setCellByHistory 恢复（含 keepFocus 时换算偏移），否则构造新 DisplayAttributes 调用 showCell 并记录历史；无父单元时 icon 视图跳回原理图，或搜索所有父单元（唯一则直接切换并高亮来路实例，多个则弹 JPopupMenu 让用户选择，见 UpHierarchyPopupListener）；异常记入 ActivityLogger。

#### `public static void clearSubCellCache()`
- 行号: L3603
- 描述: 清除展开子单元缓存（层可见性改变导致全部重画时使用）。

#### `private static void endBatch(AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp, SnapshotAnalyze sa)`
- 行号: L3612
- 描述: 渲染作业对数据库快照差异分析后，通知 VectorCache 更新，并对可见性变化的 Cell 所在窗口强制重绘。

#### `public static void expansionChanged(Cell cell)`
- 行号: L3648
- 描述: 单元展开状态变化：仅在旧显示算法（算法 0）下沿层次向上递归标记并重画受影响窗口。

#### `private static void markCellForRedrawRecursively(Cell cell, Set<Cell> marked)` / `private static void forceRedraw(Cell cell)`
- 行号: L3669 / L3681
- 描述: 沿展开路径向上递归收集需重画 Cell；强制 AbstractDrawing 重新渲染指定 Cell。

#### 坐标转换（Coordinates）

#### `public void setupCoordinates(Dimension sz, WindowFrame.DisplayAttributes da)`
- 行号: L3694
- 描述: 由重显引擎回调：更新窗口尺寸、缩放、偏移与换算因子；若缩放/偏移与上次渲染不同则清空 textInCell。

#### `public Point2D screenToDatabase(long screenX, long screenY)`
- 行号: L3715
- 描述: **`[重要]`** 屏幕坐标 → 数据库坐标；in-place 模式再经 intoCell 变换到当前单元。

#### `public Rectangle2D screenToDatabase(Rectangle2D screenRect)`
- 行号: L3734
- 描述: 屏幕矩形 → 数据库矩形（补偿 Y 轴方向差异）。

#### `public Point2D deltaScreenToDatabase(int screenDX, int screenDY)`
- 行号: L3749
- 描述: 屏幕距离 → 数据库距离。

#### `public ScreenPoint databaseToScreen(double dbX, double dbY)`
- 行号: L3763
- 描述: **`[重要]`** 数据库坐标 → 屏幕坐标；in-place 模式先经 outofCell 变换到顶层单元。

#### `public ScreenPoint databaseToScreen(Point2D db)` / `public Rectangle databaseToScreen(Rectangle2D db)`
- 行号: L3785 / L3795
- 描述: 点/矩形版数据库坐标 → 屏幕坐标。

#### `public ScreenPoint deltaDatabaseToScreen(double dbDX, double dbDY)`
- 行号: L3814
- 描述: 数据库距离 → 屏幕距离。

#### `public void gridToScreen(long dbX, long dbY, Point result)`
- 行号: L3827
- 描述: 数据库网格单位坐标 → 屏幕像素坐标（用 factorX/factorY/scale_ 快速换算）。

#### `public static void gridAlign(Point2D pt)` / `public static void gridAlignSize(Point2D pt, int direction)`
- 行号: L3839 / L3849
- 描述: 把点对齐到最近网格（direction 限定只对齐 X 或 Y）。

#### 文本（Text）

#### `public double getTextUnitSize(double pointSize)` / `public double getTextScreenSize(double dbSize)`
- 行号: L3862 / L3873
- 描述: 点尺寸 ↔ 数据库文本尺寸换算（除以/乘以窗口 scale）。

#### `public static int getDefaultFontSize()`
- 行号: L3878
- 描述: 返回 TextDescriptor 默认字体大小。

#### `public Font getFont(TextDescriptor descript)`
- 行号: L3885
- 描述: 按 TextDescriptor 获取本窗口使用的 Font（过小不可显示时返回 null）。

#### `public double getFontHeight(TextDescriptor descript)`
- 行号: L3895
- 描述: 返回文本真实显示高度（点）。

#### `public Rectangle2D getGlyphBounds(String text, Font font)`
- 行号: L3912
- 描述: 经 LRUCache 获取字形视觉边界（带缓存）。

#### `public GlyphVector getGlyphs(String text, Font font)`
- 行号: L3920
- 描述: 委托 TextDescriptor.getGlyphs 生成 GlyphVector。

#### 打印与导出（Printing）

#### `public void databaseChanged(DatabaseChangeEvent e)`
- 行号: L3927
- 描述: **`[重要]`** DatabaseChangeListener 回调：Cell 名变化时更新标题；Cell 被删除时 showCell(null,...) 并回退 Cell 历史；清空 textInCell 缓存。

#### `public void writeImage(ElectricPrinter ep, String filePath)`
- 行号: L3949
- 描述: 把打印图像直接导出为 PNG 文件。

#### `public boolean initializePrinting(ElectricPrinter ep, PageFormat pageFormat)`
- 行号: L3962
- 描述: 打印初始化：按 DPI 缩放因子（上限 2）调整组件尺寸、validate、repaint，返回 true。

#### `public BufferedImage getPrintImage(ElectricPrinter ep)`
- 行号: L3981
- 描述: 取本窗口打印图像：渲染区域缺省为 getBoundsInWindow，委托 getPrintImageFromData。

#### `public static BufferedImage getPrintImageFromData(ElectricPrinter ep, EditWindow wnd, Rectangle2D cellBounds, Cell cell, VarContext vc, LayerVisibility lv, boolean isDisplay)`
- 行号: L3994
- 描述: 用离屏 PixelDrawing 按页面尺寸渲染打印图像（支持彩色/黑白模式），必要时在页面上绘制单元边框（临时改窗口变换后 drawCellFrame 再恢复）。

#### `public void panXOrY(int direction, double[] panningAmounts, int ticks)`
- 行号: L4112
- 描述: 按用户设置的平移距离沿 X（direction=0）或 Y（1）平移 ticks 格，更新偏移与焦点历史并 fullRepaint。

#### `public void centerCursor()`
- 行号: L4134
- 描述: 平移窗口使上次鼠标位置成为屏幕中心。

### 内部类

- **NodeProtoDataFlavor**（L704，public static，extends DataFlavor）：自定义数据 flavor，封装要创建的 Cell/CellGroup 与来源 ExplorerTree；`getFlavorObject()` 返回 Cell 或 group。
- **NodeProtoTransferable**（L730，public static，implements Transferable）：封装被拖拽的 Cell/CellGroup 与对应 DataFlavor，用于从资源管理器树拖出单元。
- **EditWindowDropTarget**（L779，private static，implements DropTargetListener）：捕获拖入编辑窗口的单元（dragEnter/dragOver 时调用 showDraggedBox 显示放置轮廓，drop 时对齐网格后启动 PaletteFrame.PlaceNewNode 放置实例；也支持 "LOADCELL xxx" 字符串）。
- **ScrollAdjustmentListener**（L1247，private static，implements AdjustmentListener）：滚动条变化分发到 bottomScrollChanged/rightScrollChanged。
- **RenderJob**（L1638，private static，extends Job）：渲染作业线程：循环处理所有 repaintRequest 的窗口，做快照差异分析（endBatch），调用各窗口 drawing.render 重新渲染并 repaint；有积压任务时自动重启下一轮。
- **CrossProbe**（L1735，private static）：跨探针显示条目（线或框 + 颜色）。
- **DisplayedFrame**（L1872，private static，extends Cell.FrameDescription）：把单元边框（图框/标题栏）绘制到 Graphics（showFrameLine/showFrameText，文字按需缩放以适配最大宽高）。
- **StringSearch**（L2036，private static，implements Serializable）：文本查找引擎：按节点名/弧名/端口名/单元变量搜索（支持正则、大小写、高亮区域限定），管理 foundInCell 列表与当前查找位置，changeOneText 执行单处替换（支持正则替换并同步同串其他匹配位置），replaceAllText 全部替换。
- **StringsInCell**（L2459，private static，implements Serializable）：单条查找结果（对象、Variable.Key、行号、起止位置、原串、正则、是否已替换）。
- **ReplaceTextJob**（L2581，private static，extends Job）：单处替换作业（CHANGE 类型，构造即 startJob，doIt 调用 changeOneText）。
- **ReplaceAllTextJob**（L2608，private static，extends Job）：全部替换作业（同 ReplaceTextJob，doIt 调用 replaceAllText）。
- **UpHierarchyPopupListener**（L3538，private，implements ActionListener）：upHierarchy 弹出菜单项响应：切换窗口到所选父单元、记录历史、高亮来路实例。
- **匿名类**：`pulsatingTimer` 的 ActionListener（L291，错误高亮脉动时强制重画）；`setScrollPosition` 中 EDT 调度的 Runnable（L2782）；`cache` 字段的 LRUCache 匿名子类（L3903-3910，cacheMiss 计算字形边界）。

---

## EditWindowFocusBrowser（简述）

**文件**: `com/sun/electric/tool/user/ui/EditWindowFocusBrowser.java`（188 行）
**职责**: 像网页浏览器保存浏览历史一样保存编辑窗口的"焦点"（focus = 某个缩放 + 平移位置）。支持前后导航；新建焦点时销毁当前焦点之后的所有历史（类似输入新 URL）。最多保存 MAXSAVEDFOCI = 10 个焦点。

- 构造 `EditWindowFocusBrowser(EditWindow controller)`（L72，包内可见）：关联 EditWindow 并 clear。
- `public void goBack()`（L81）/ `public void goForward()`（L102）：先 updateCurrentFocus 记录当前位置，再恢复上一个/下一个焦点（无历史时打印提示）。
- `private void restoreFocus(Focus focus)`（L124）：恢复焦点 = setOffset + setScale + fullRepaint。
- `void saveCurrentFocus()`（L135，包内可见）：保存当前焦点并销毁其后所有历史（置 firstUpdate）。
- `private void saveCurrentFocusNoUpdateClear()`（L141）：实际保存逻辑：删除当前焦点之后的历史、追加新焦点、超过上限删除最旧。
- `void clear()`（L163，包内可见）：清空全部历史。
- `public void updateCurrentFocus()`（L172）：平移发生时更新当前焦点；若距上次保存后首次更新则先保存为新焦点。
- 内部类 `Focus`（L56）：保存 offset（Point2D）与 scale（double）的不可变焦点记录。
