# 高亮系统（Highlighter / Highlight / NetworkHighlighter）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user。本文件由 AI 生成。

## 模块概述

高亮请求的流转路径是：GUI 代码（选择、鼠标悬停、测量、错误报告、网络显示等）调用 `Highlighter` 的一系列 `addXxx()` 工厂方法（`addElectricObject`、`addText`、`addLine`、`addArea`、`addPoly`、`addMessage`、`addObject`、`addNetwork` 等），每个方法创建一个 `Highlight` 子类实例并经 `addHighlight()` 放入 `highlightList`；随后调用 `finished()` 提交本次变更（剔除失效高亮、必要时设置当前 ArcProto、在 EDT 上通知 `HighlightListener`），由 redisplay 引擎在绘制窗口时回调 `showHighlights()` 完成实际渲染。

`Highlight` 是一个与具体窗口无关的抽象基类（`Cloneable`），其形态包括：`HighlightEOBJ`（指向 `ElectricObject` 的对象高亮，可携带 port/outline-point 信息）、`HighlightText`（指向对象上的某条文字，如节点名、Export 名）、`HighlightLine`（线段，可指定粗细/颜色/仅在某窗口显示）、`HighlightArea`（矩形区域）、`HighlightPoly`（多边形）、`Message`（带文字消息的标注，嵌套在 `Highlight` 内）与 `HighlightObject`（任意 Object 的占位高亮）。

每个 `EditWindow` 拥有三种类型的 `Highlighter`：`SELECT_HIGHLIGHTER`（选择）、`MOUSEOVER_HIGHLIGHTER`（鼠标悬停预高亮）与 `RULER_HIGHLIGHTER`（测量），绘制颜色分别取自用户偏好色 `HIGHLIGHT`/`MOUSEOVER_HIGHLIGHT`/`MEASUREMENT`；静态字段 `currentHighlighter` 跟踪当前获得焦点的选择高亮器，焦点切换时触发 `highlighterLostFocus` 通知。

高亮与 redisplay 引擎有两套协作通道：传统的 Java2D 通道（`showHighlight(EditWindow, Graphics, ...)` → 抽象方法 `showInternalHighlight`，屏幕坐标直接 `Graphics.drawXxx`，其中 `Highlight.drawLine` 先做屏幕裁剪以加速屏外大量高亮的绘制），以及 ERaster 离屏栅格通道（`showInRaster()` 返回 true 的对象走 `showHighlight(FixpTransform, AbstractLayerDrawing, ERaster)`）；不能走栅格的高亮被 `Highlighter` 单独放入 `difficultHighlightList`，由 `showHighlights(..., onlyDifficult=true)` 分次绘制。

网络高亮由 `NetworkHighlighter`（`HierarchyEnumerator.Visitor` 的子类）实现：`addNetwork` 只高亮当前 cell 内的网络对象，`showNetworks` 则逐层下钻层次结构（`startDepth`/`endDepth` 控制深度），在顶层生成 `HighlightEOBJ`/`HighlightText`、在下层生成变换到根 cell 坐标的 `HighlightPoly`，并支持跨窗口 cross-probe 同名网络。

此外，`HighlightConnectivity` 用 R-Tree 对金属/多晶/过孔做快速拓扑提取（结果以 `USER_topology_quick` 变量缓存在 cell 上），供 `showNetworks` 在“无拓扑信息”的 cell（如 IC 版图）中快速显示连通性；`ErrorHighlight` 及子类定义可序列化/可 XML 化的错误高亮（线、点、多边形、几何对象），由 DRC/ERC 等工具经 `addToHighlighter()` 灌入 `Highlighter`；`HighlightListener` 是监听高亮变更的接口。

---

## Highlighter

**文件**: `com/sun/electric/tool/user/Highlighter.java`（2467 行）
**职责**: 维护单个窗口的高亮对象列表（含“难画”对象列表、高亮栈、屏幕偏移等状态），提供所有添加入口、选择命中测试（点选/框选/循环选取）与绘制分发，并在高亮变更时通知监听器。
**继承/实现**: 实现 `DatabaseChangeListener`（数据库变化时重新校验高亮）。
**关键字段**:
- `currentHighlighter (static Highlighter)` — 当前持有焦点的选择高亮器（全局唯一引用）。
- `highOffX, highOffY (long)` — 高亮绘制的屏幕像素偏移。
- `highlightList (List<Highlight>)` — 当前高亮对象列表（按加入顺序）。
- `difficultHighlightList (List<Highlight>)` — 不能显示在 ERaster 栅格中的高亮对象。
- `highlightStack (List<List<Highlight>>)` — 高亮列表栈（push/pop 保存与恢复）。
- `changed (boolean)` — 高亮自上次 `finished()` 后是否发生过变化。
- `lastHighlightListEndObj (Highlight)` — 上次 `clear()` 前最后选中的对象，用于点击循环选取。
- `showNetworkLevel (int)` — `showNetworks` 命令当前的层次展开深度。
- `type (int)` — 高亮器类型（SELECT/MOUSEOVER/RULER 之一）。
- `highlightListeners (static Set<HighlightListener>)` — 高亮变更监听器集合。
- `SELECT_HIGHLIGHTER / MOUSEOVER_HIGHLIGHTER / RULER_HIGHLIGHTER (public static final int)` — 高亮器类型常量：0=选择、1=鼠标悬停、2=测量。
- `EXACTSELECTDISTANCE (public static final int)` — 精确选中允许的最大像素距离（5 像素）。

### 方法

#### `public Highlighter(int type, WindowFrame wf)`
- 行号: L113
- 描述: 构造函数。初始化偏移与状态，向 `UserInterfaceMain` 注册数据库变更监听；若 `currentHighlighter` 为空则将自己设为当前高亮器。注意参数 `wf` 在函数体内未使用。

#### `void setChanged(boolean c)`
- 行号: L123
- 描述: 设置 `changed` 标志（包内可见）。

#### `public void delete()`
- 行号: L128
- 描述: 析构函数，从 `UserInterfaceMain` 注销数据库变更监听。

#### `public Highlight addElectricObject(ElectricObject eobj, Cell cell)`
- 行号: L139
- 描述: 向高亮列表加入一个指向 `ElectricObject` 的 `HighlightEOBJ`（`highlightConnected=true`，point=-1），返回新建的 Highlight。

#### `public Highlight addElectricObject(ElectricObject eobj, boolean isError, Cell cell)`
- 行号: L146
- 描述: 同上，额外指定 `isError` 错误高亮标志（错误高亮可脉动闪烁）。

#### `public Highlight addElectricObject(ElectricObject eobj, Cell cell, Color col)`
- 行号: L159
- 描述: 同上，额外指定绘制颜色 `col`。

#### `public Highlight addElectricObject(ElectricObject eobj, Cell cell, boolean highlightConnected, Color col)`
- 行号: L175
- 描述: 同上，额外指定 `highlightConnected`（是否联动高亮电气相连对象；`addNetwork` 传 false 以避免重复）与颜色。

#### `public Highlight setPoint(Highlight oldHighlight, ElectricObject eobj, int point)`
- 行号: L189
- 描述: 用新的 eobj/point 替换列表中已有的 `HighlightEOBJ`（outline 编辑时节点不变、只改点）；若旧对象不在列表中或类型不符则原样返回。

#### `public Highlight addText(ElectricObject eobj, Cell cell, Variable.Key varKey)`
- 行号: L206
- 描述: 加入一个 `HighlightText` 文字高亮（`varKey` 指明是节点名/端口名/Export 名/某 Variable 的文字）。

#### `public Highlight addMessage(Cell cell, String message, Point2D loc)`
- 行号: L220
- 描述: 在数据库坐标 `loc` 处加入一条 `Highlight.Message` 文字标注（corner=0，无背景色）。

#### `public Highlight addMessage(Cell cell, String message, Point2D loc, int corner, Color backgroundColor)`
- 行号: L236
- 描述: 同上，额外指定文字角点（0=lowerLeft,1=upperLeft,2=upperRight,3=lowerRight）与文字背景色。

#### `public Highlight addArea(Rectangle2D area, Cell cell)`
- 行号: L249
- 描述: 加入一个 `HighlightArea` 矩形区域高亮。

#### `public Highlight addArea(Rectangle2D area, Color col, Cell cell)`
- 行号: L263
- 描述: 同上，指定颜色。

#### `public Highlight addObject(Object obj, Cell cell)`
- 行号: L276
- 描述: 加入一个 `HighlightObject`（任意 Object 的通用占位高亮，自身不绘制）。

#### `public Highlight addLine(Point2D start, Point2D end, Cell cell)`
- 行号: L290
- 描述: 加入一条普通线段高亮 `HighlightLine`。

#### `public Highlight addLine(Point2D start, Point2D end, Cell cell, WindowFrame wf)`
- 行号: L305
- 描述: 加入一条只在指定 `WindowFrame` 中绘制的线段高亮（弱引用持有 wf，其他窗口不画）。

#### `public Highlight addLine(Point2D start, Point2D end, Cell cell, boolean thick, boolean isError)`
- 行号: L320
- 描述: 加入一条可指定粗细与错误标志的线段高亮。

#### `public Highlight addLine(Point2D start, Point2D end, Cell cell, boolean thick, Color col, boolean isError)`
- 行号: L337
- 描述: 加入一条可指定粗细、颜色与错误标志的线段高亮。

#### `public Highlight addThickLine(Point2D start, Point2D end, Cell cell, boolean isError)`
- 行号: L351
- 描述: 加入一条粗线段高亮（等价于 thick=true 的 `addLine`）。

#### `public Highlight addPoly(Poly poly, Cell cell, Color color)`
- 行号: L365
- 描述: 加入一个 `HighlightPoly` 多边形高亮（color 为 null 时用默认色）。

#### `public void addNetwork(Network net, Cell cell)`
- 行号: L378
- 描述: **[重要]** 高亮 cell 中指定网络 `net` 上的所有对象（经由 `NetworkHighlighter.getHighlights`，深度 0，不进入层次）。网表不可用（死锁）时打印提示并返回。

#### `public void showNetworks(Cell cell)`
- 行号: L401
- 描述: **[重要]** “Show Network”命令：根据当前选中的网络（或唯一选中的 cell 实例的连线网络）逐层展开层次显示连通性；首次调用会 `clear()` 并重置其他窗口的高亮，之后每次调用多展开一层（`showNetworkLevel` 递增）。对没有拓扑信息的 cell（全是 Generic 弧），走 `HighlightConnectivity` 快速提取拓扑来显示几何；同时在其他显示同 cell group 的窗口中 cross-probe 同名网络。无更多层次时打印 “Nothing more in hierarchy...”。

#### `private boolean sameNetName(Network net1, Network net2)`
- 行号: L593
- 描述: 判断两个网络是否有同名（忽略大小写），用于跨窗口 cross-probe 匹配。

#### `public synchronized void addHighlight(Highlight h)`
- 行号: L610
- 描述: **[重要]** 核心添加入口：把 Highlight 加入 `highlightList`，若 `!h.showInRaster()` 同时加入 `difficultHighlightList`，并置 `changed=true`。

#### `public void clear()`
- 行号: L622
- 描述: 清空全部高亮（委托 `clear(true)`，保存最后选中对象）。

#### `private synchronized void clear(boolean resetLastHighlightListEndObj)`
- 行号: L627
- 描述: 内部清空实现：重置偏移与 `showNetworkLevel`，可选地记录 `lastHighlightListEndObj`，清空两个列表并置 `changed=true`；列表为空时直接返回。

#### `public void finished()`
- 行号: L647
- 描述: **[重要]** 提交高亮变更：剔除已失效（`!isValid()`）的高亮；若选择高亮器只选中了单一类型的弧，则把当前 ArcProto 设为该类型；随后（非 EDT 时用 `SwingUtilities.invokeLater`）通知 `HighlightListener` 并复位 `changed`。

#### `public void ensureHighlightingSeen(WindowFrame wf)`
- 行号: L708
- 描述: **[重要]** 保证高亮可见：若高亮区域在屏幕外，画一条带箭头的指向线；若高亮区域相对屏幕过小（面积比 < 1/500），从屏幕边角向高亮边界画提示线；这些临时线由 `FlashActionListener`（500ms Timer）自动移除。

#### `private static class FlashActionListener implements ActionListener`
- 行号: L785
- 描述: 内部类，`actionPerformed`（L801）在 500ms 后移除 `ensureHighlightingSeen` 添加的临时闪示线、调用 `finished()` 并重绘窗口内容。构造函数 L791 保存目标窗口、高亮器与四条临时线。

#### `private synchronized Highlight getLastSelected(List<Highlight> underCursor)`
- 行号: L820
- 描述: 返回“最后选中”的对象用于循环选取：若光标下的对象中已有与当前高亮相同者，返回上次 `clear()` 前保存的 `lastHighlightListEndObj`；否则返回当前列表最后一个。

#### `public synchronized void copyState(Highlighter highlighter)`
- 行号: L843
- 描述: 从另一个 Highlighter 克隆全部高亮与本对象状态（注释标明 “This is a hack, don't use it”），不继承偏移量以免破坏鼠标悬停高亮器。

#### `public void showHighlights(EditWindow wnd, Graphics g, boolean onlyDifficult)`
- 行号: L861
- 描述: **[重要]** 绘制入口：按高亮器类型选取颜色（SELECT/MOUSEOVER/RULER），只绘制属于当前 cell 的高亮；`onlyDifficult` 为 true 时只画栅格画不了的 `difficultHighlightList`；当只有一个高亮且开启 “Highlight Connected Objects” 偏好时，额外调用 `showHighlightsConnected` 绘制虚线连通对象。

#### `public static synchronized void addHighlightListener(HighlightListener l)`
- 行号: L906
- 描述: 注册高亮监听器。

#### `public static synchronized void removeHighlightListener(HighlightListener l)`
- 行号: L912
- 描述: 注销高亮监听器。

#### `private void fireHighlightChanged()`
- 行号: L918
- 描述: 仅对 SELECT 类型高亮器向全部监听器回调 `highlightChanged(this)`，随后复位 `changed`。

#### `private synchronized void fireHighlighterLostFocus(Highlighter highlighterGainedFocus)`
- 行号: L936
- 描述: 仅对 SELECT 类型高亮器向全部监听器回调 `highlighterLostFocus(highlighterGainedFocus)`。

#### `public void gainedFocus()`
- 行号: L954
- 描述: 本高亮器获得焦点：更新 `currentHighlighter` 并对旧的高亮器触发 `fireHighlighterLostFocus`。

#### `public synchronized void pushHighlight()`
- 行号: L968
- 描述: 把当前高亮列表的副本压入 `highlightStack`。

#### `public synchronized void popHighlight()`
- 行号: L980
- 描述: 从栈顶弹出高亮列表并调用 `showHighlights(List)` 恢复；栈空时打印提示。

#### `public void showHighlights(List<Highlight> popable)`
- 行号: L995
- 描述: **[重要]** 恢复一组已保存的高亮：先 `clear()`，逐项校验对象仍在 cell 中（`objInCell`），按类型重新构造等价高亮加入，最后 `finished()`。

#### `public synchronized void remove(Highlight h)`
- 行号: L1043
- 描述: 从列表（及 difficult 列表）移除指定 Highlight。

#### `public synchronized int getNumHighlights()`
- 行号: L1055
- 描述: 返回当前高亮对象数量。

#### `public synchronized List<Highlight> getHighlights()`
- 行号: L1061
- 描述: 返回高亮列表的副本（避免外部并发修改原列表）。

#### `public synchronized List<Highlight> getDifficultHighlights()`
- 行号: L1070
- 描述: 返回 difficult 高亮列表的副本。

#### `public synchronized void setHighlightListGeneral(List<Highlight> newHighlights)`
- 行号: L1078
- 描述: 清空后用给定列表整体替换高亮（与 `setHighlightList` 实现相同）。

#### `public synchronized void setHighlightList(List<Highlight> newHighlights)`
- 行号: L1091
- 描述: 清空后用给定列表整体替换高亮（与 `setHighlightListGeneral` 实现相同）。

#### `public List<Geometric> getHighlightedEObjs(boolean wantNodes, boolean wantArcs)`
- 行号: L1106
- 描述: 返回所有被高亮的 Geometric（NodeInst/ArcInst 按需过滤），由每个 Highlight 的多态方法汇总。

#### `public List<NodeInst> getHighlightedNodes()`
- 行号: L1121
- 描述: 返回所有被高亮的 NodeInst（PortInst 高亮折算为其节点，去重）。

#### `public List<ArcInst> getHighlightedArcs()`
- 行号: L1136
- 描述: 返回所有被高亮的 ArcInst（去重）。

#### `public Set<Network> getHighlightedNetworks()`
- 行号: L1152
- 描述: **[重要]** 返回当前选中的网络集合；若当前窗口是 `WaveformWindow` 则直接委托其 `getHighlightedNetworks()`；否则基于当前 cell 的网表逐高亮收集，网表不可用时打印提示并返回空集。

#### `public List<DisplayedText> getHighlightedText(boolean unique)`
- 行号: L1188
- 描述: 返回所有被高亮的文字对象；`unique=true` 时排除附属于其他已高亮对象的文字。

#### `public Rectangle2D getHighlightedArea(EditWindow wnd)`
- 行号: L1204
- 描述: 返回全部高亮的合并包围盒（无高亮时返回 null），等价于 `getHighlightedArea(wnd, false)`。

#### `public Rectangle2D getHighlightedArea(EditWindow wnd, boolean onlyEObjects)`
- 行号: L1215
- 描述: 返回高亮的合并包围盒；`onlyEObjects=true` 时只统计 `HighlightEOBJ` 的包围盒。

#### `public Highlight getOneHighlight()`
- 行号: L1254
- 描述: 返回唯一带 ElectricObject 的高亮；没有时打印 “Must select an object first” 并返回 null。

#### `public ElectricObject getOneElectricObject(Class<?> type)`
- 行号: L1279
- 描述: 返回唯一高亮的指定类型 ElectricObject（如 `NodeInst.class`、`ArcInst.class`）；PortInst 高亮在要求 NodeInst 时折算为节点；类型不符时打印错误并返回 null。

#### `private String getClassName(Class<?> type)`
- 行号: L1303
- 描述: 把 NodeInst/ArcInst 类映射为 “Node”/“Arc” 用于错误消息输出。

#### `public synchronized void setHighlightOffset(long offX, long offY)`
- 行号: L1315
- 描述: 设置高亮绘制的屏幕偏移（像素）。

#### `public synchronized Point2D getHighlightOffset()`
- 行号: L1325
- 描述: 返回当前高亮屏幕偏移。

#### `public void selectArea(EditWindow wnd, double minSelX, double maxSelX, double minSelY, double maxSelY, boolean invertSelection, boolean findSpecial)`
- 行号: L1340
- 描述: **[重要]** 框选：搜索矩形区域内全部对象；`invertSelection=true` 时反选（已选中的移除、未选中的加入），否则用结果整体替换选择。

#### `public Highlight overHighlighted(EditWindow wnd, int x, int y, boolean change)`
- 行号: L1374
- 描述: 在屏幕坐标 (x,y) 处寻找被点中的高亮（逐个委托 `Highlight.overHighlighted`）；`change` 控制是否同步更新高亮器状态（如端口/轮廓点）。

#### `private static Poly.Type getHighlightTextStyleBounds(EditWindow wnd, ElectricObject eObj, Variable.Key varKey, Rectangle2D bounds)`
- 行号: L1393
- 描述: 计算对象某 Variable 文字的 `Poly` 样式与屏幕包围盒（bounds 输出参数），处理文字旋转、TEXTBOX 特例；eObj 为 null 时返回 null（防御大删除场景）。

#### `public static Point2D [] describeHighlightText(EditWindow wnd, ElectricObject eObj, Variable.Key varKey)`
- 行号: L1429
- 描述: **[重要]** 把一条文字高亮转换成用于描边的点数组（每两条一组），根据文字样式（TEXTCENT/TEXTBOT/TEXTTOP/TEXTLEFT/TEXTRIGHT/TEXTTOPLEFT/TEXTBOTLEFT/TEXTTOPRIGHT/TEXTBOTRIGHT/TEXTBOX）在文字包围盒上生成 X 形、半框或收缩框等轮廓。

#### `public Highlight findObject(Point2D pt, EditWindow wnd, boolean exclusively, boolean another, boolean invert, boolean findPort, boolean findPoint, boolean findSpecial, boolean findText, boolean change)`
- 行号: L1566
- 描述: **[重要]** 点击选择主入口：在数据库坐标 `pt` 处搜索全部对象（`findAllInArea`），按 `getLastSelected` + `getSimiliarHighlight` 排序以实现循环选取；`another=true` 时在同一点的多对象间轮换；`invert=true` 反选；`exclusively` 表示仅限当前已选对象（outline 编辑）；`change=false` 时只返回结果不修改高亮器。

#### `public static List<Highlight> findAllInArea(Highlighter highlighter, Cell cell, boolean exclusively, boolean findPort, boolean findPoint, boolean findSpecial, boolean findText, Rectangle2D bounds, EditWindow wnd)`
- 行号: L1678
- 描述: **[重要]** 区域搜索核心：先按需搜索文字（`findTextNow`），再分三阶段（原语节点 → cell 实例 → 弧）遍历 cell 的 R-Tree（`cell.searchIterator`），对每个候选调用 `checkOutObject`；`exclusively=true` 时只复查当前已高亮的对象。返回按重要性排序的 Highlight 列表。

#### `public static class TextHighlightBound implements RTBounds`
- 行号: L1771
- 描述: 文字 R-Tree 叶子节点（存储 FixpRectangle 包围盒、所属 ElectricObject 与 Variable.Key）。构造函数 L1777；`getBounds()` L1785、`getElectricObject()` L1787、`getKey()` L1789 为取值方法；`toString()` L1792 返回 “TextBound”。

#### `private static void findTextNow(Cell cell, EditWindow wnd, double directHitDist, Rectangle2D bounds, boolean findSpecial, List<Highlight> list)`
- 行号: L1804
- 描述: **[重要]** 使用窗口缓存的文字 R-Tree（`wnd.getTextInCell()`，缺失时重建）搜索区域内的可选文字：重建时扫描 cell 文字、节点名/实例名/节点变量/端口变量/Export 名与变量、弧名与变量；命中时按各类文字可见性偏好过滤（`User.isTextVisibilityOnXxx`），并以 `boundsIsHit` 判定后加入 `HighlightText`。

#### `private static boolean boundsIsHit(Rectangle2D bounds, Rectangle2D selection, double directHitDist)`
- 行号: L1995
- 描述: 判定一个文字包围盒是否命中选择区域：若开启 “Dragging must enclose” 则要求完全包含，否则要求最大间隙不超过 `directHitDist`。

#### `public static List<Highlight> checkOutObject(Geometric geom, boolean findPort, boolean findPoint, boolean findSpecial, Rectangle2D bounds, EditWindow wnd, double directHitDist, boolean areaMustEnclose, boolean showTempNames)`
- 行号: L2030
- 描述: **[重要]** 判定单个 Geometric 是否被点/框选中：处理 hard-to-select 对象、不可见层对象与不可见引脚；`areaMustEnclose` 时要求轮廓完全包含于区域；命中节点时按需附加最近端口（`findPort`）与最近轮廓点（`findPoint`），命中弧直接生成 Highlight。返回命中生成的 Highlight 列表。

#### `public static Highlight getSimiliarHighlight(List<Highlight> highlights, Highlight exampleHigh)`
- 行号: L2198
- 描述: **[重要]** 从候选列表中挑出与 `exampleHigh` 最相似的高亮（循环选取排序依据）：先按类过滤，再对 `HighlightEOBJ` 按 ElectricObject 类、PortInst 的同原型/可连接端口、ArcInst 的同 ArcProto、端口-弧可连接性逐级择优。

#### `public static double distToNode(Rectangle2D bounds, NodeInst ni, EditWindow wnd, boolean showTempNames)`
- 行号: L2305
- 描述: 计算 bounds 到 NodeInst 的距离（负值表示命中）：对版图 FET/电阻检查 gate/active 层多边形，对端口伸出基体外的原语（缓存于 `portsOutsideHighlight`，L2294）、Artwork 节点与精确选取节点检查全部多边形，否则用基体形状的 `polyDistance`。

#### `public static double distToArc(Rectangle2D bounds, ArcInst ai, EditWindow wnd, boolean showTempNames)`
- 行号: L2429
- 描述: 计算 bounds 到 ArcInst 的距离：`ArcProto.isEdgeSelect()` 时按技术真实形状多边形精确计算，否则按弧的 lambda 多边形 `polyDistance` 计算。

#### `public void databaseChanged(DatabaseChangeEvent e)`
- 行号: L2463
- 描述: `DatabaseChangeListener` 回调：数据库变化后调用 `finished()` 剔除失效高亮并通知监听器。

---

## Highlight（抽象基类及其子类）

**文件**: `com/sun/electric/tool/user/Highlight.java`（2036 行）
**职责**: 抽象基类定义“高亮”这一概念——指向用户注意对象（ElectricObject/文字/区域/线等）的引用与渲染所需信息，且与具体 EditWindow 无关；同文件内含 7 个子类实现。
**继承/实现**: `public abstract class Highlight implements Cloneable`。嵌套公开类 `Message`、`HighlightSorting`；同文件包内类 `HighlightPoly`、`HighlightLine`、`HighlightObject`、`HighlightArea`、`HighlightEOBJ`、`HighlightText`。
**关键字段**:
- `solidLine / dottedLine / dashedLine / boldLine (public static final BasicStroke)` — 预定义画笔：实线（宽 0）、点线、虚线、粗线（宽 3）。
- `cell (protected final Cell)` — 高亮所属的 Cell。
- `color (protected final Color)` — 绘制颜色（null 用默认色）。
- `isError (public final boolean)` — 是否为错误高亮（true 时可脉动闪烁）。
- `CROSSSIZE (private static final int)` — 单点高亮十字的半径（3 像素）。
- `PULSATE_ROTATE_PERIOD / PULSATE_STRIPE_LENGTH / PULSATE_STRIPE_SEGMENTS (private static final int)` — 错误高亮脉动参数：旋转周期 1000ms、条纹长度 30、每条纹 10 段。

### 方法（Highlight 基类）

#### `Highlight(Cell c, Color color, boolean isError)`
- 行号: L96
- 描述: 包内可见的基类构造函数，初始化 cell/color/isError。

#### `public Cell getCell()`
- 行号: L103
- 描述: 返回所属 Cell（getter）。

#### `public boolean isValid()`
- 行号: L105
- 描述: 高亮是否仍有效：cell 为 null 或 cell 仍链接（`isLinked`）时有效。

#### `public boolean showInRaster()`
- 行号: L112
- 描述: 是否可显示在 ERaster 栅格中，基类默认 false（由 `HighlightEOBJ` 覆写）。

#### `public boolean isHighlightEOBJ()` / `public boolean isHighlightText()`
- 行号: L117 / L120
- 描述: 类型探测钩子（为使子类不必公开而设），基类默认 false。

#### `public Object getObject()` / `public Variable.Key getVarKey()` / `public int getPoint()`
- 行号: L122 / L124 / L127
- 描述: 默认值 getter：null / null / -1，由相应子类覆写。

#### `public Object clone()`
- 行号: L130
- 描述: 浅克隆（`super.clone()`），失败时打印栈并返回 null。

#### `public boolean sameThing(Highlight obj, boolean exact)`
- 行号: L147
- 描述: 判断两个高亮是否指向同一事物（`exact=true` 时连端口也要相同），基类默认 false。

#### `public boolean nodeMovesWithText()`
- 行号: L158
- 描述: 该高亮文字是否随节点移动（不可见引脚上的文字、或启用 “move exports with labels” 时的 Export 名），基类默认 false。

#### `public void showHighlight(EditWindow wnd, Graphics g_, long highOffX, long highOffY, boolean onlyHighlight, Color mainColor, Stroke primaryStroke)`
- 行号: L177
- 描述: **[重要]** Java2D 绘制入口：先校验 `isValid()`，设置颜色与画笔；若启用脉动偏好（`User.isErrorHighlightingPulsate()`）且 `isError`，用 HSV 渐变 + 旋转虚线分 10 段循环调用 `showInternalHighlight` 实现流动条纹效果，否则直接调用 `showInternalHighlight`。

#### `abstract void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY, boolean onlyHighlight)`
- 行号: L202
- 描述: 抽象绘制方法，由各子类实现实际绘制。

#### `public void showHighlight(FixpTransform outOfPlaceTransfrom, AbstractLayerDrawing ald, ERaster raster)`
- 行号: L205
- 描述: ERaster 栅格通道的绘制入口，基类默认抛 `UnsupportedOperationException`（仅 `HighlightEOBJ` 覆写）。

#### `void showHighlightsConnected(Graphics2D g2, EditWindow wnd)` / `public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raste)`
- 行号: L217 / L220
- 描述: 绘制与本高亮电气相连对象的钩子（Java2D/栅格两个版本），基类为空实现，仅 `HighlightEOBJ` 覆写。

#### `void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs)`
- 行号: L229
- 描述: 收集本高亮包含的 Geometric 的多态钩子，基类为空。

#### `static void getHighlightedEObjsInternal(Geometric geom, List<Geometric> list, boolean wantNodes, boolean wantArcs)`
- 行号: L231
- 描述: 静态辅助：按 wantNodes/wantArcs 过滤并把 geom 去重加入列表。

#### `public Geometric getGeometric()`
- 行号: L247
- 描述: 返回本高亮对应的 Geometric（PortInst/Export/注释文字高亮折算为其基节点），基类默认 null。

#### `void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set)` / `void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set)`
- 行号: L253 / L266
- 描述: 收集被高亮节点/弧的多态钩子，基类为空。

#### `static void getHighlightedNodesInternal(Geometric geom, Set<NodeInst> set)` / `static void getHighlightedArcsInternal(Geometric geom, Set<ArcInst> set)`
- 行号: L255 / L268
- 描述: 静态辅助：类型检查后把节点/弧加入集合。

#### `void getHighlightedNetworks(Set<Network> nets, Netlist netlist)`
- 行号: L281
- 描述: 收集本高亮涉及网络的多态钩子，基类为空。

#### `void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights)`
- 行号: L292
- 描述: 收集本高亮文字（`unique=true` 时排除附属于其他已高亮对象的文字）的多态钩子，基类为空。

#### `Rectangle2D getHighlightedArea(EditWindow wnd)`
- 行号: L299
- 描述: 返回本高亮的包围盒（包内可见的多态钩子），基类默认 null。

#### `public ElectricObject getElectricObject()`
- 行号: L305
- 描述: 返回本高亮关联的 ElectricObject，基类默认 null。

#### `Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change)`
- 行号: L315
- 描述: 判定屏幕点 (x,y) 是否在本高亮上（可返回更新后的 Highlight），基类默认 null。

#### `public String getInfo()`
- 行号: L317
- 描述: 返回用于排序/显示的单行描述文本，基类默认 null。

#### `public static List<ElectricObject> getEOBJElements(List<Highlight> list)`
- 行号: L325
- 描述: **[重要]** 把高亮列表转换为 ElectricObject 列表：PortInst 折算为其 NodeInst，ArcInst/NodeInst 原样加入，其余忽略。

#### `public static NodeInst getInfoCommand(List<Highlight> list, int[] counts)`
- 行号: L353
- 描述: **[重要]** 统计高亮列表中的对象数量并写入 counts（下标约定：0=arc、1=node、2=export、3=text、4=graphics），返回列表中出现的 NodeInst（供 Info 命令显示）。

#### `public static void drawOutlineFromPoints(EditWindow wnd, Graphics g, Point2D[] points, long offX, long offY, boolean opened, boolean thickLine)`
- 行号: L400
- 描述: **[重要]** Java2D 通道的轮廓绘制：把数据库坐标点数组转换到屏幕后连线；全部点重合时画十字；`opened=true` 不闭合首尾；`thickLine=true` 时沿中心方向多画一次模拟加粗。就地编辑（in-place edit）时应用 `getInPlaceTransformOut()` 变换。

#### `public static void drawOutlineFromPoints(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raster, Point2D[] points, int offX, int offY, boolean opened, boolean thickLine)`
- 行号: L470
- 描述: **[重要]** ERaster 栅格通道的轮廓绘制：经 `ald.databaseToScreen` 变换、可选地应用 `outOfPlaceTransform`，单点画十字，其余用 `ald.drawLine` 连成轮廓。

#### `void internalDescribe(StringBuffer desc)`
- 行号: L516
- 描述: 多态钩子：向描述缓冲追加本高亮的具体内容，基类为空。

#### `public String describe()`
- 行号: L522
- 描述: 返回形如 “类名 in cell: 细节” 的完整描述。

#### `public static Poly getNodeInstOutline(NodeInst ni)`
- 行号: L540
- 描述: **[重要]** 生成用于高亮 NodeInst 的轮廓 Poly：特殊处理 holds-outline 节点（用 trace 点集，NODE 功能填实/其他开口）、Artwork 圆形节点（fillEllipse）、弯曲引脚；否则返回 `ni.getBaseShape()`。

#### `public static void drawLine(Graphics g, EditWindow wnd, long x1, long y1, long x2, long y2)`
- 行号: L626
- 描述: **[重要]** 屏幕坐标画线：先用 `GenMath.clipLine` 按窗口尺寸裁剪（大量屏外高亮时显著提速），再 `g.drawLine`。

#### `public static class HighlightSorting implements Comparator<Highlight>`
- 行号: L645
- 描述: 按 `getInfo()` 输出对 Highlight 排序的比较器（null 视为空串）；`compare()` 在 L648。

### 子类：`public static class Message extends Highlight`（L661）
- `Message(Cell c, String m, Point2D p, int co, Color backgroundColor)`（L668）：构造文字消息高亮。字段：`msg`（消息文本）、`loc`（数据库坐标位置）、`corner`（文字角点）、`backgroundColor`（背景色）。
- `void internalDescribe(StringBuffer desc)`（L678）：追加消息文本。
- `public String getInfo()`（L684）：返回消息文本。
- `public void showInternalHighlight(...)`（L687）：**[重要]** 按 corner 计算文字绘制位置；无背景色时用补色画阴影再画文字，有背景色时先 `fillRect` 画背景块。
- `Rectangle2D getHighlightedArea(EditWindow wnd)`（L741）：返回 loc 处的零尺寸矩形。

### 子类：`class HighlightPoly extends Highlight`（L751）
- `HighlightPoly(Cell c, Poly p, Color col)`（L754）：构造多边形高亮，字段 `polygon`。
- `public void showInternalHighlight(...)`（L761）：**[重要]** FILLED 样式 `fillPolygon`、DISC 样式 `fillOval`，其余按 OPENED 与否用 `drawOutlineFromPoints` 描边；`color` 非 null 时切换画笔颜色。

### 子类：`class HighlightLine extends Highlight`（L805）
- 字段：`start, end, center (Point2D)`、`thickLine (boolean)`、`onlyThis (WeakReference<WindowFrame>)`（仅在该窗口显示）。
- `HighlightLine(Cell c, Point2D s, Point2D e, Point2D cen, boolean thick, Color col, boolean isError, WindowFrame onlyThis)`（L811）：构造线段高亮。
- `public void showInternalHighlight(...)`（L822）：**[重要]** 若 `onlyThis` 存在且不等于当前窗口则跳过；按两点调用 `drawOutlineFromPoints`（thickLine 控制粗细）。
- `Rectangle2D getHighlightedArea(EditWindow wnd)`（L847）：返回两点张成的矩形。
- `public String getInfo()`（L857）：返回 “Line from ... to ...” 描述。

### 子类：`class HighlightObject extends Highlight`（L865）
- `HighlightObject(Cell c, Object obj)`（L868）：构造通用对象高亮，字段 `object`。
- `public Object getObject()`（L875）：返回对象。
- `public void showInternalHighlight(...)`（L878）：占位实现，打印 “Should call this one?”。

### 子类：`class HighlightArea extends Highlight`（L888）
- `HighlightArea(Cell c, Color col, Rectangle2D area)`（L891）：构造矩形区域高亮（复制 bounds）。
- `public void showInternalHighlight(...)`（L899）：沿矩形四条边描边绘制。
- `void getHighlightedEObjs(...)`（L925）/ `void getHighlightedNodes(...)`（L946）/ `void getHighlightedArcs(...)`（L962）：借助 `Highlighter.findAllInArea` 搜索区域内的对象并折算成节点/弧加入结果。
- `Rectangle2D getHighlightedArea(EditWindow wnd)`（L976）：返回 bounds 本身。
- `public String getInfo()`（L982）：返回 “Area from ... to ...” 描述。

### 子类：`class HighlightEOBJ extends Highlight`（L993）
- 字段：`eobj (ElectricObject)`（被高亮对象）、`highlightConnected (boolean)`（是否联动高亮相连对象）、`point (int)`（NodeInst 上被高亮的轮廓点，-1 表示无）。
- 构造函数（均为 public，但类为包内可见）：L999 `(ElectricObject e, Cell c, boolean connected, int p)`、L1007（+`isError`）、L1015（+`Color col`）、L1023 拷贝构造 `(HighlightEOBJ h, ElectricObject eobj, int p)`。
- `void internalDescribe(StringBuffer desc)`（L1032）：按 PortInst/NodeInst/ArcInst 追加 `describe(true)`。
- `public ElectricObject getElectricObject()`（L1046）：getter。
- `public boolean isHighlightEOBJ()`（L1049）：返回 true。
- `public int getPoint()`（L1052）：getter。
- `public boolean isValid()`（L1055）：基类有效且 `eobj.isLinked()`。
- `public boolean showInRaster()`（L1062）：**[重要]** 非错误、无指定颜色、指向 NodeInst 且无轮廓点的高亮才可走 ERaster 栅格。
- `public boolean sameThing(Highlight obj, boolean exact)`（L1067）：比较 eobj（`exact=false` 时 PortInst 折算为 NodeInst）。
- `public void showInternalHighlight(...)`（L1086）：**[重要]** Java2D 核心绘制：ArcInst 用 `makeLambdaPoly` 描边（唯一高亮时在弧中心绘制 R/F/S/FS/X 约束字母）；NodeInst 先绘制选中轮廓点（十字 + 与前后点的连线 + 方向箭头，样条节点特殊处理）、再画节点轮廓、最后绘制选中端口（圆形端口用 fillEllipse）并在已接线的 cell 实例端口上标注端口名。
- `void showHighlightsConnected(Graphics2D g2, EditWindow wnd)`（L1358）：**[重要]** 当高亮的是 PortInst 且 `highlightConnected` 时，基于网表找出同网络的全部弧与节点，用虚线描弧、实心圆点标节点中心并连线（Java2D 通道）。
- `public void showHighlight(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raster)`（L1460）：栅格通道的节点轮廓绘制（断言 color==null）。
- `public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raster)`（L1485）：**[重要]** 连通对象绘制的栅格版本（`ald.drawLine`/`drawOval`）。
- `void getHighlightedEObjs(...)`（L1595）/ `void getHighlightedNodes(...)`（L1601）/ `void getHighlightedArcs(...)`（L1607）：经 `getGeometric()` 加入结果。
- `void getHighlightedNetworks(Set<Network> nets, Netlist netlist)`（L1613）：**[重要]** 单端口节点折算为其端口后，PortInst 经 `NetworkTool.getNetworksOnPort`、ArcInst 逐位（bus width）收集网络。
- `Rectangle2D getHighlightedArea(EditWindow wnd)`（L1642）：返回 Geometric 的 bounds。
- `public Geometric getGeometric()`（L1655）：PortInst 折算为 NodeInst，其余 Geometric 原样返回。
- `Highlight overHighlighted(...)`（L1664）：**[重要]** 重新用 `checkOutObject` 判定屏幕点是否命中本对象；命中且端口/轮廓点变化时（`change=true`）用 `Highlighter.setPoint` 更新选择。
- `public String getInfo()`（L1721）：返回 “Node .../Arc ...” 描述。

### 子类：`class HighlightText extends Highlight`（L1745）
- 字段：`eobj (ElectricObject)`、`varKey (Variable.Key)`（被高亮的文字变量键）。
- `public HighlightText(ElectricObject e, Cell c, Variable.Key key)`（L1750）：构造并校验 key 与对象类型匹配（NODE_NAME/NODE_PROTO→NodeInst、ARC_NAME→ArcInst、EXPORT_NAME→Export，不匹配抛 IllegalArgumentException）。
- `void internalDescribe(StringBuffer desc)`（L1769）：按 key 追加 “name:/instance:/export:/var:” 描述。
- `public ElectricObject getElectricObject()`（L1798）、`public boolean isHighlightText()`（L1802）、`public Variable.Key getVarKey()`（L1805）：getter / 类型钩子。
- `public boolean isValid()`（L1808）：基类有效、对象链接且（对于非固定键）`getParameterOrVariable(varKey)` 仍存在。
- `public boolean sameThing(Highlight obj, boolean exact)`（L1822）：比较 eobj、cell、varKey。
- `public void showInternalHighlight(...)`（L1838）：**[重要]** 用 `Highlighter.describeHighlightText` 得到轮廓点并描边；唯一高亮时在所属几何中心画点并用点线连到文字锚点。
- `public Geometric getGeometric()`（L1889）：当文字随对象移动（`DisplayedText.objectMovesWithText`）时返回对应几何。
- `void getHighlightedEObjs(...)`（L1900）/ `void getHighlightedNodes(...)`（L1906）/ `void getHighlightedArcs(...)`（L1912）：经 `getGeometric()` 加入结果。
- `void getHighlightedNetworks(Set<Network> nets, Netlist netlist)`（L1918）：Export 文字按 bus width 收集其网络。
- `DisplayedText makeDisplayedText()`（L1932）：由 eobj+varKey 构造 `DisplayedText`（包内）。
- `void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights)`（L1940）：**[重要]** 加入本文字；`unique=true` 且文字所属对象已被其他高亮选中时跳过。
- `Rectangle2D getHighlightedArea(EditWindow wnd)`（L1984）：返回文字 Poly 的包围盒。
- `Highlight overHighlighted(...)`（L1995）：用文字 Poly 的 `isInside` 判定命中。
- `public String describe()`（L2005）：返回 “Node name for .../Arc name for .../Export '...'” 等描述。
- `public String getInfo()`（L2032）：返回 “Text: ” + describe()。

---

## NetworkHighlighter

**文件**: `com/sun/electric/tool/user/NetworkHighlighter.java`（288 行）
**职责**: 对网络做层次化高亮：以 `HierarchyEnumerator.Visitor` 遍历 cell 层次，在顶层把网络上的弧/端口/Export 生成 `HighlightEOBJ`/`HighlightText`，在下层 cell 生成变换到根 cell 坐标的 `HighlightPoly`（画在实例上的网络形状）。
**继承/实现**: `extends HierarchyEnumerator.Visitor`。
**关键字段**:
- `TRIMMEDDISPLAY (private static final boolean)` — 编译期开关（恒 false）：为 true 时只画弧中心线、不收集端口与 Export。
- `cell / netlist / nets (Cell / Netlist / Set<Network>)` — 目标 cell、其网表、要显示的网络集合。
- `netIDs (BitSet)` — 各网络在层次遍历中的全局网络 ID 位集。
- `startDepth / endDepth / currentDepth (int)` — 层次搜索起止深度与当前深度。
- `highlights (List<Highlight>)` — 收集结果。

### 方法

#### `private NetworkHighlighter(Cell cell, Netlist netlist, Set<Network> nets, int startDepth, int endDepth)`
- 行号: L62
- 描述: 私有构造函数，记录参数并初始化 `currentDepth=0`。

#### `public static synchronized List<Highlight> getHighlights(Cell cell, Netlist netlist, Set<Network> nets, int startDepth, int endDepth)`
- 行号: L83
- 描述: **[重要]** 静态入口：构造实例后用 `HierarchyEnumerator.enumerateCell` 遍历层次（`VarContext.globalContext`），返回收集的 Highlight 列表。startDepth/endDepth 均为 0 时只高亮当前 cell。

#### `public boolean enterCell(HierarchyEnumerator.CellInfo info)`
- 行号: L93
- 描述: **[重要]** 层次遍历回调：深度 0 时建立全局网络 ID 位集；schematic cell 不下钻（只处理顶层）；`currentDepth` 在 [startDepth, endDepth] 区间内时，顶层调用 `addNetworkObjects()`（真实对象），下层调用 `addNetworkPolys(info)`（画在实例上的多边形）；返回 false 可终止下钻。

#### `public void exitCell(HierarchyEnumerator.CellInfo info)`
- 行号: L123
- 描述: 层次遍历回调：`currentDepth--`。

#### `public boolean visitNodeInst(Nodable ni, HierarchyEnumerator.CellInfo info)`
- 行号: L127
- 描述: 恒返回 true，允许遍历全部节点实例。

#### `private static Set<ElectricObject> getNetworkObjects(Cell cell, Netlist netlist, Set<Network> nets)`
- 行号: L138
- 描述: **[重要]** 收集指定网络上所有可高亮对象：全部端口实例（跳过总线/父图标）、全部弧（按 bus width 逐位检查，并附带弧端点处的原语节点端口）、顶层 Export（按 bus width 逐位检查）。`TRIMMEDDISPLAY=true` 时只收弧。

#### `private static Color getNodeOrPortColor()`
- 行号: L202
- 描述: 返回用户偏好色 `ColorPrefType.NODE_HIGHLIGHT`（用于节点/端口高亮）。

#### `private void addNetworkObjects()`
- 行号: L211
- 描述: **[重要]** 顶层处理：对 `getNetworkObjects` 的结果，Export 生成 `HighlightText`（EXPORT_NAME），其余生成 `HighlightEOBJ`；非弧对象用 `NODE_HIGHLIGHT` 色、弧用默认色。

#### `private void addNetworkPolys(HierarchyEnumerator.CellInfo info)`
- 行号: L233
- 描述: 下层处理第一步：找出该 cell 中与全局网络 ID 位集匹配的本地网络，再调用重载方法。

#### `private void addNetworkPolys(Set<Network> localNets, HierarchyEnumerator.CellInfo info)`
- 行号: L247
- 描述: **[重要]** 下层处理：对匹配的本地网络对象生成多边形——弧用 `makeLambdaPoly`（TRIMMEDDISPLAY 时只用中心线）、端口用 `Highlight.getNodeInstOutline`、Export 用其端口形状（黄色）；所有多边形经 `info.getTransformToRoot()` 变换到根 cell 后包装为 `HighlightPoly` 加入结果。

---

## HighlightConnectivity

**文件**: `com/sun/electric/tool/user/HighlightConnectivity.java`（1271 行）
**职责**: 对 cell 做“快速拓扑提取”：把金属/多晶/接触层几何装入 R-Tree，通过相交传播计算连通网络 ID，产出一组描述各网络所含节点/弧名称的字符串数组；结果以 `USER_topology_quick` 变量缓存于 cell，供 `Highlighter.showNetworks` 在无网表拓扑的 cell（如版图）中快速显示网络连通性。
**继承/实现**: 无（普通类），含公开静态嵌套类 `StoreTopology`（Job）、`QCNetID`、`QCBound`、`QCPoly`、`QCVia`。
**关键字段**:
- `cell (Cell)` — 被提取的 cell。
- `allConnectingLayers (List<ConnectingLayer>)` — 金属/多晶连接层组（每组合并同编号层，含 primaryLayer、layers、arcs、tree）。
- `allConnectingVias (List<ConnectingVia>)` — 接触（过孔）信息（viaLayer、layer1、layer2、tree）。
- `removeLayers (Map<Layer,Layer>)` — CUT 功能层到其切割的目标层映射（去除金属/多晶用）。
- `topology (String[])` — 提取结果：每个字符串以 “/” 分隔同一网络上的 “N节点名”/“A弧名”（可带 `[x;y;w;h]` 子矩形坐标）。
- `TOPOLOGY_QUICK_DATA / TOPOLOGY_QUICK_DATE (public static final Variable.Key)` — cell 变量键（`USER_topology_quick` / `USER_topology_quick_date`），分别存拓扑数组与生成时间戳。

### 方法

#### `public HighlightConnectivity(Cell cell)`
- 行号: L80
- 描述: **[重要]** 构造函数：若 cell 上缓存的拓扑日期晚于 `cell.getRevisionDate()` 则直接复用；否则依次执行 `initializeDesignRules()`（收集层/弧/过孔/切割层）、`buildRTrees()`（建树）、`extractRTrees()`（连通性传播），再按网络 ID 汇总成 “N.../A...[rect]” 字符串数组，并启动 `StoreTopology` Job 写回 cell 变量。

#### `public String[] getTopology()`
- 行号: L166
- 描述: 返回提取的拓扑数组（getter）。

#### `public static class StoreTopology extends Job`
- 行号: L171
- 描述: 把拓扑写入 cell 变量的 Job。构造函数 L176 以 `Job.Type.CHANGE`、`Job.Priority.USER` 启动；`public boolean doIt()`（L184）写入 `TOPOLOGY_QUICK_DATE`/`TOPOLOGY_QUICK_DATA` 后调用 `lowLevelSetRevisionDate` 恢复修订日期（使该写入不改变 cell 版本）。

#### `private void buildRTrees()`
- 行号: L202
- 描述: 递归收集 cell（含子 cell）全部几何到各层 R-Tree，然后对 CUT 层的矩形从目标层树中删除并回插剩余几何（`PolyMerge.subtract`）。

#### `private boolean addArea(Cell cell, boolean topLevel, FixpTransform transToTop, List<EPoint> linesInNonMahnattan, Map<Layer,List<Rectangle2D>> removeGeometry)`
- 行号: L271
- 描述: 递归加入一个 cell 的几何：cell 实例递归（应用 `transformOut`），原语节点（跳过 PIN）用 `getShapeOfNode`、弧用 `getShapeOfArc`，逐多边形调用 `addLayer`；返回是否发现非曼哈顿几何。

#### `private boolean addLayer(PolyBase poly, Geometric topGeom, FixpTransform trans, List<EPoint> linesInNonMahnattan, Map<Layer,List<Rectangle2D>> removeGeometry)`
- 行号: L322
- 描述: 把单个多边形归类：CUT 层几何记入 `removeGeometry`；属于连接层的 FILLED 多边形（矩形走 `addRectangle`，非矩形走 `addPolygon` 并检测非曼哈顿边）；属于过孔层的几何走 `addVia`。

#### `private QCBound addRectangle(Rectangle2D bounds, ConnectingLayer cl, Geometric topGeom)`
- 行号: L387
- 描述: 把矩形加入金属 R-Tree：若被已有矩形完全覆盖则忽略，若完全覆盖已有矩形则先删除旧矩形（去重），再 `RTNode.linkGeom` 挂入。

#### `private void addPolygon(PolyBase poly, ConnectingLayer cl, Geometric topGeom)`
- 行号: L446
- 描述: 把非矩形多边形包装为 `QCPoly` 加入 R-Tree。

#### `private void addVia(ERectangle rect, ConnectingVia cv)`
- 行号: L467
- 描述: 把过孔加入 via R-Tree；与已有过孔中心重合时去重。

#### `private void extractRTrees()`
- 行号: L496
- 描述: **[重要]** 连通性传播主循环：遍历每层 R-Tree，对尚未编号的几何分配新网络 ID 并调用 `growArea` 扩散。

#### `private void growArea(QCBound sBound, ConnectingLayer cl, MutableInteger idNumber)`
- 行号: L512
- 描述: **[重要]** 从一块几何出发递归传播网络 ID：同层相交几何（多边形需 `doesIntersect` 精确判定）并入同一网络；过孔层相交时跨层进入另一连接层（`growPoint`）继续传播。

#### `private boolean growPoint(double x, double y, ConnectingLayer layer, MutableInteger idNumber)`
- 行号: L570
- 描述: 在指定层上从坐标点继续传播网络 ID（过孔连通两层的桥梁）。

#### `private boolean doesIntersect(QCBound bound1, QCBound bound2)`
- 行号: L594
- 描述: 两几何的精确相交判定：非曼哈顿直接视为相交；曼哈顿时逐边线段相交检测，最后检查完全包含。

#### `private boolean initializeDesignRules()`
- 行号: L858
- 描述: **[重要]** 从技术库收集设计规则：按层功能层级合并金属层/多晶层为 ConnectingLayer（maskColor 0 者为 primaryLayer），关联各层的弧原型，由接触节点（function=CONTACT）推导 ConnectingVia 的过孔层与上下连接层，并建立 CUT 层到被切割层的 `removeLayers` 映射。

#### 内部类 `class ConnectingLayer`（L1083） / `class ConnectingVia`（L1098）
- 描述: 数据结构：ConnectingLayer 持有 primaryLayer/layers/arcs/GeometryTree；ConnectingVia 持有 viaLayer/layer1/layer2/GeometryTree。

#### `private static class GeometryTree`（L1110）
- 描述: 封装 `RTNode<QCBound>` 的包装：`getRoot()` L1118、`setRoot()` L1120、`isEmpty()` L1122、`search(Rectangle2D)` L1124（空树返回空迭代器）。

#### `public static class QCNetID`（L1133）
- 描述: 网络 ID 与顶层几何的关联。构造函数 L1138；`getNetID()` L1150、`getTopGeom()` L1156 为 getter；`setNetID(MutableInteger n)` L1162 为 setter；`public void updateNetID(MutableInteger n, Map<Integer,List<MutableInteger>> netIDsByValue)`（L1167）在网络合并时批量改值；`public boolean isSameBasicNet(MutableInteger otherNetID)`（L1193）判断是否同一网络。

#### `public static class QCBound extends QCNetID implements RTBounds`（L1205）
- 描述: R-Tree 叶子节点（矩形几何）。构造函数 L1209；`getBounds()` L1216；`containsPoint(double x, double y)` L1218 点包含判定；`isManhattan()` L1223 恒 true；`toString()` L1226。

#### `public static class QCPoly extends QCBound`（L1229）
- 描述: R-Tree 叶子节点（多边形几何）。构造函数 L1233；`containsPoint` L1239 用 `poly.isInside`；`isManhattan()` L1244 逐边判断；`getPoly()` L1254 返回 PolyBase。

#### `public static class QCVia extends QCBound`（L1260）
- 描述: R-Tree 叶子节点（过孔）。构造函数 L1262；`toString()` L1268 返回 “Via on net ...”。

---

## ErrorHighlight

**文件**: `com/sun/electric/tool/user/ErrorHighlight.java`（549 行）
**职责**: 定义“高亮错误”的抽象表示：以 `CellId`/对象 ID（而非对象引用）持久引用出错位置，支持二进制序列化（`IdWriter`/`IdReader`）与 XML 读写（DTD 标签 ERRORTYPEGEOM/ERRORTYPELINE/ERRORTYPETHICKLINE/ERRORTYPEPOINT/ERRORTYPEPOLY），供 DRC/ERC 等工具保存错误并随时用 `addToHighlighter()` 重新显示。
**继承/实现**: `public abstract class ErrorHighlight implements Serializable`。同文件包内子类 `ErrorHighExport`、`ErrorHighPoly`、`ErrorHighLine`、`ErrorHighPoint`、`ErrorHighNode`、`ErrorHighArc`。
**关键字段**:
- `NULL_ARRAY (public static final ErrorHighlight[])` — 空数组常量。
- `HAS_CELL_ID (private static final byte)` — 序列化 tag 中“带 CellId”的位标志（0x08）。
- `cellId (private final CellId)` — 出错 cell 的持久 ID。
- `context (private final VarContext)` — 变量上下文（注释 “Yet do be immutable”，序列化中暂未保存）。

### 方法

#### `ErrorHighlight(Cell c, VarContext con)` / `ErrorHighlight(CellId cellId)`
- 行号: L56 / L62
- 描述: 包内构造函数：由 Cell 取其 ID，或直接以 CellId 构造；后者 context 为 null。

#### `public Cell getCell(EDatabase database)`
- 行号: L67
- 描述: 按 ID 从数据库解析 Cell；database 为 null（回归测试场景）或 ID 为 null 时返回 null。

#### `protected String getCellName(EDatabase database)`
- 行号: L74
- 描述: 返回 cell 描述名，找不到时返回 “NotFound{lay}”（XML 输出需要非空名称）。

#### `VarContext getVarContext()`
- 行号: L80
- 描述: 返回 VarContext（包内 getter）。

#### `boolean containsObject(Cell cell, Object obj)` / `Object getObject(EDatabase database)`
- 行号: L82 / L84
- 描述: 是否包含某对象 / 解析出实际对象（供 DRC 结果匹配），基类默认 false / null。

#### `static String getImplementedXmlHeaders()`
- 行号: L86
- 描述: 返回支持的 XML 标签列表字符串 “ERRORTYPEGEOM|ERRORTYPETHICKLINE|ERRORTYPELINE|ERRORTYPEPOINT|ERRORTYPEPOLY”。

#### `static boolean isErrorHighlightBody(String name)` / `static boolean isErrorPoly(String name)`
- 行号: L87 / L92
- 描述: 判断 XML 标签名是否为受支持的错误高亮体 / 是否为 POLY 类型。

#### `static List<ErrorHighlight> addErrorHighlight(String qName, Cell curCell, String geomName, EPoint p1, EPoint p2, List<ErrorHighlight> list)`
- 行号: L94
- 描述: **[重要]** XML 驱动的工厂：按标签名创建对应错误高亮加入 list——ERRORTYPEGEOM 按名称先找节点再找弧；ERRORTYPELINE/THICKLINE 创建 `ErrorHighLine`；ERRORTYPEPOLY 创建 `ErrorHighPoly`（返回其 linesList 以便继续填充）；ERRORTYPEPOINT 创建 `ErrorHighPoint`。找不到几何时打印 “Invalid geometry”。

#### `public static void writeXmlHeader(String indent, PrintStream ps)` / `void writeXmlDescription(String tabs, PrintStream msg, EDatabase database)`
- 行号: L135 / L136
- 描述: 基类占位实现，打印 “Not implemented in writeXmlHeader/Description”（DTD 头由各子类静态覆写，描述由子类覆写）。

#### `boolean isValid(EDatabase database)`
- 行号: L141
- 描述: 错误是否仍有效（cell 仍存在）；注释注明 minArea DRC 错误仍有问题。

#### `public void addToHighlighter(Highlighter h, EDatabase database)`
- 行号: L143
- 描述: 把本错误高亮加入 Highlighter 的多态钩子，基类为空。

#### `public static ErrorHighlight newInst(VarContext cont, Geometric geom)`
- 行号: L145
- 描述: 工厂：NodeInst → `ErrorHighNode`，ArcInst → `ErrorHighArc`。

#### `public static ErrorHighlight newInst(CellId cellId, ImmutableNodeInst n)`
- 行号: L151
- 描述: 工厂：由不可变节点构造 `ErrorHighNode`。

#### `public static ErrorHighlight newInst(Cell cell, Point2D p1, Point2D p2)`
- 行号: L155
- 描述: 工厂：构造 `ErrorHighLine`（细线，坐标 snap 到 EPoint）。

#### `public static ErrorHighlight newInst(Cell cell, Point2D pt)`
- 行号: L159
- 描述: 工厂：构造 `ErrorHighPoint`。

#### `public static ErrorHighlight newInst(Export e)`
- 行号: L163
- 描述: 工厂：构造 `ErrorHighExport`。

#### `abstract void write(IdWriter writer) throws IOException`
- 行号: L167
- 描述: 抽象的二进制序列化入口（各子类覆写）。

#### `void writeTag(IdWriter writer, byte tag) throws IOException`
- 行号: L168
- 描述: 写类型 tag；cellId 非 null 时置 HAS_CELL_ID 位并写入 `writeNodeProtoId(cellId)`。

#### `static ErrorHighlight read(IdReader reader) throws IOException`
- 行号: L178
- 描述: **[重要]** 按 tag 反序列化：1=ErrorHighExport、2=ErrorHighPoly（读行数后递归读子行）、3=ErrorHighLine、4=ErrorHighPoint、5=ErrorHighNode、6=ErrorHighArc；未知 tag 抛 `NotSerializableException`。

### 子类 `class ErrorHighExport extends ErrorHighlight`（L215）
- 构造函数 L219（`VarContext, Export`）、L225（`ExportId`）；`isValid(EDatabase)` L230 校验 Export 存在；`public void addToHighlighter(...)` L232 经 `h.addText(e, e.getParent(), Export.EXPORT_NAME)` 显示；`write(IdWriter)` L239 写 tag 1 + PortProtoId。

### 子类 `class ErrorHighPoly extends ErrorHighlight`（L245）
- 字段 `linesList`（组成多边形的线段错误）、`origId`/`origCellId`（原始几何 ID/cell，-1 表示无）。构造函数 L251（`Cell, Geometric, List`）、L271（`CellId`）。`public static void writeXmlHeader(...)` L278 输出 POLY DTD；`void writeXmlDescription(...)` L288 输出 `<ERRORTYPEPOLY>` 及其子行；`Cell getOrigCell(EDatabase)` L307；`public void addToHighlighter(...)` L309 逐行转发；`write(IdWriter)` L316 写 tag 2 + 行数 + 各行。

### 子类 `class ErrorHighLine extends ErrorHighlight`（L324）
- 字段 `p1, p2 (EPoint)`、`thickLine (boolean)`。构造函数 L329（Cell 版）、L337（CellId 版）。`public static void writeXmlHeader(...)` L345 输出 LINE/THICKLINE DTD；`void writeXmlDescription(...)` L362；`public void addToHighlighter(...)` L371 经 `h.addLine(p1, p2, cell, thickLine, true)` 显示（isError=true 可脉动）；`write(IdWriter)` L378 写 tag 3 + 两点 + thick 标志。

### 子类 `class ErrorHighPoint extends ErrorHighlight`（L386）
- 字段 `point (EPoint)`。构造函数 L389 / L395。`public void addToHighlighter(...)` L401 在点位置画两条交叉线（十字，consize=5）；`public static void writeXmlHeader(...)` L411；`void writeXmlDescription(...)` L421；`write(IdWriter)` L430 写 tag 4 + 点。

### 子类 `class ErrorHighNode extends ErrorHighlight`（L436）
- 字段 `nodeId (int)`。构造函数 L440（`VarContext, NodeInst`）、L446（`CellId, int`）。`boolean containsObject(...)` L452 / `Object getObject(EDatabase)` L458 按 ID 解析节点；`public static void writeXmlHeader(...)` L464 输出 GEOM DTD；`void writeXmlDescription(...)` L473；`boolean isValid(EDatabase)` L482 校验节点存在；`public void addToHighlighter(...)` L484 经 `h.addElectricObject(ni, true, ni.getParent())` 显示（错误高亮）；`write(IdWriter)` L492 写 tag 5 + nodeId。

### 子类 `class ErrorHighArc extends ErrorHighlight`（L498）
- 字段 `arcId (int)`。构造函数 L502（`VarContext, ArcInst`）、L508（`CellId, int`）。`containsObject` L514 / `getObject` L520 按 ID 解析弧；`writeXmlDescription` L526；`isValid(EDatabase)` L535 校验弧存在；`public void addToHighlighter(...)` L537 经 `h.addElectricObject(ai, true, ai.getParent())` 显示（错误高亮）；`write(IdWriter)` L545 写 tag 6 + arcId。

---

## HighlightListener

**文件**: `com/sun/electric/tool/user/HighlightListener.java`（42 行）
**职责**: 监听高亮变化的回调接口，由需要感知选择/高亮状态变化的 GUI 组件（如工具条、菜单）实现并注册到 `Highlighter`。
**继承/实现**: `public interface HighlightListener`。

### 方法

#### `public void highlightChanged(Highlighter which)`
- 行号: L34
- 描述: 高亮管理器在高亮发生变更并即将刷新到屏幕时回调；参数为发生变化的高亮器。

#### `public void highlighterLostFocus(Highlighter highlighterGainedFocus)`
- 行号: L41
- 描述: 某 Highlighter 失去焦点时回调；参数为获得焦点的（当前窗口的）高亮器，可能为 null。
