# 重绘引擎核心（AbstractDrawing / PixelDrawing / LayerDrawing）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.redisplay。本文件由 AI 生成。

## 模块概述

Electric 的窗口重绘采用"离屏渲染 + 贴屏"模型：渲染线程（Job 线程）先把版图内容画到离屏缓冲，AWT 事件线程随后在 `paintComponent` 中把离屏图像贴到 `Graphics2D` 上，再叠加 `Highlighter`/`MouseOverHighlighter` 的交互高亮（两个 Drawing 子类的 `paintComponent` 都直接调用 `wnd.getMouseOverHighlighter().showHighlights(...)` 与 `wnd.getHighlighter().showHighlights(...)`）。绘制算法的选择由 `User.getDisplayAlgorithm()` 决定（0=像素、1=矢量、2=分层），`AbstractDrawing.createDrawing()` 工厂负责实例化：算法 2 且当前 cell 为版图（非 icon、非原理图、技术为 layout）时创建 `LayerDrawing.Drawing`，否则创建 `PixelDrawing.Drawing`（算法 1 时其内部委托 `VectorDrawing` 渲染进 PixelDrawing 缓冲）。`AbstractDrawing` 是共同的抽象基类，持有 `EditWindow` 与 `DisplayAttributes`，定义 `paintComponent`（AWT 线程）与 `render`（Job 线程）两个核心契约方法，并统一提供缓存清理、强制重绘登记、调色板绘制等静态工具。

`PixelDrawing` 是经典像素引擎：一个全色 `int[]` 不透明图像加每透明层一张 1 位位平面（bitplane），绘制直接写内存位，最后 `composite()` 按技术颜色表合成；它自带 Bresenham 线、扫描线多边形、圆/盘/圆弧的光栅化实现，并对展开子 cell 做离屏缓存复用。`LayerDrawing` 是分层光栅引擎：每层一个 `TransparentRaster`（int 位图，每 int 存 32 像素），经 `VectorCache` 缓存后的矢量形状直接光栅化进各层，合成在 AWT 线程由 `AlphaBlender` 逐行完成并写入 `VolatileImage`，支持层不透明度混合。`AbstractLayerDrawing` 为 LayerDrawing 提供与具体光栅无关的共享光栅化基元（框、线、多边形、圆、弧）与坐标变换，绘制目标抽象为包级接口 `ERaster`（`ERaster.java`，由 `TransparentRaster`、`OpaqueRaster` 等实现）。

缓存失效方面，两个引擎各自维护静态 `expandedCells`（展开 cell 缓存，key 为 cell+朝向）与 `changedCells` 集合：cell 被修改时调用 `AbstractDrawing.forceRedraw(Cell)` 登记，下次渲染前逐出对应缓存条目；缩放变化或 `fullInstantiate` 模式切换时整体清空缓存（`clearSubCellCache`）。包内其余支撑文件：`VectorCache.java`（矢量形状缓存与 R-Tree）、`VectorDrawing.java`（矢量算法渲染）、`AlphaBlender.java`（逐行 alpha 混合）、`TransparentRaster.java`/`OpaqueRaster.java`/`PatternedTransparentRaster.java`/`EmptyPatternedOutlinedTransparentRaster.java`（ERaster 实现）。

---

## PixelDrawing
**文件**: `com/sun/electric/tool/user/redisplay/PixelDrawing.java`（4160 行）
**职责**: 为关联的 EditWindow 管理离屏显示缓冲，把 cell 渲染成可直接贴屏的 Image。离屏数据由两部分组成：全色不透明图像（`img`/`opaqueData`）与按需分配的透明层 1 位位平面（`layerBitMaps`），渲染结束时 `composite()` 将二者按技术颜色表合成。为提高速度：直接写位内存（比 Swing 快约 3 倍）、微小节点/弧用单点近似、展开 cell 内容按 cell+朝向组合缓存复用。打印（`printImage`）与调色板预览（VectorDrawing 路径）也复用本类离屏缓冲。
**继承/实现**: 无父类（独立渲染引擎）；内嵌 `static class Drawing extends AbstractDrawing` 作为窗口接入点；内嵌 `static interface ERaster`（旧版光栅接口，与包级 `ERaster.java` 同名，仅含 8 个绘制方法、无 `copyBits`；VectorDrawing 路径通过包内 `OpaqueRaster`（实现包级 ERaster、直接写入 int[] 像素缓冲）写入本类缓冲）。
**关键字段**:
- `sz (Dimension)` — 窗口尺寸
- `scale / originX / originY (double)` — 显示比例与 cell 原点在屏幕上的位置（坐标变换基准）
- `varContext (VarContext)` — 渲染时的变量上下文（层次展开）
- `nowPrinting (int)` — 0=彩色显示、1=彩色打印、2=黑白打印
- `drawBounds (Rectangle2D)` — 本窗口可视区域（数据库单位），用于快速裁剪剔除
- `img (BufferedImage)` / `opaqueData (int[])` / `total (int)` — 不透明全色图像及其底层 int 数组（alpha 高字节表示未填充/文本抗锯齿）
- `layerBitMaps (byte[][][])` — 透明层位平面数组（按需分配）；`compositeRows (byte[][])` — composite 时的行指针
- `numLayerBitMaps / trueColorMap / curTech` — 透明层数、合成颜色表、当前技术
- `patternedOpaqueLayers (Map<Layer, PatternedOpaqueLayer>)` — 图案化不透明层（POL）位图（缓存 cell 内用）
- `renderedWindow (boolean)` — 是否顶层窗口（false 表示正在渲染缓存 cell）
- `clipLX / clipHX / clipLY / clipHY (int)` — 当前裁剪矩形
- `static expandedCells (Map<ExpandedCellKey, ExpandedCellInfo>)` — 展开 cell 全局缓存（cell+朝向为 key）
- `static changedCells (Set<CellId>)` — 已修改 cell 集合（forceRedraw 登记）
- `static topSz (Dimension)` — 顶层窗口尺寸（递归渲染缓存 cell 时对照用）
- `static gp / dp / lv` — 当前 GraphicsPreferences / DrawingPreferences / LayerVisibility
- `static textGraphics / gridGraphics / instanceGraphics (EGraphics)` — 文本/网格/实例线样式
- `wnd (EditWindow0)` — 被渲染的窗口（打印时为 EditWindow0）
- `dummyWnd (EditWindow0)` — 假窗口实现（getVarContext/getScale/getGlobalTextScale/getDefaultFont），供文本排版使用
- `textTimer / polyTimer (ElapseTimer)` — 调试计时器（DEBUGRENDERTIMING）

### 静态常量
- `MINIMUMTEXTSIZE = 5 (int)`（L209）— 小于此像素值的文本不绘制（改为 Greek 线）
- `MAXIMUMTEXTSIZE = 100 (int)`（L210）— 大于此值的文本按 2 的幂缩小（shiftUp 机制）
- `SINGLETONSTOADD = 5 (int)`（L211）— 每次重绘允许额外缓存的单实例 cell 数

### 嵌套类 Drawing（extends AbstractDrawing，窗口接入点）
字段：`displayAlgorithm`、`useCellGreekingImages`、`greekSizeLimit`、`greekCellSizeLimit`、`vd (VectorDrawing)`、`volatile offscreen (PixelDrawing)`。

#### `public boolean paintComponent(Graphics2D g, LayerVisibility lv, Dimension sz)`
- 行号: L373
- 描述: **`[重要]`** AWT 线程调用：把离屏 `BufferedImage` 画到 g 上，恢复窗口坐标（`setupEditWindowCoordinates`），设置默认字体后依次调用 MouseOverHighlighter 与 Highlighter 的 `showHighlights` 叠加交互高亮。offscreen 未建或尺寸不符时返回 false。

#### `public void render(Dimension sz, WindowFrame.DisplayAttributes da, GraphicsPreferences gp, DrawingPreferences dp, boolean fullInstantiate, ERectangle bounds)`
- 行号: L394
- 描述: **`[重要]`** 渲染入口（Job 线程调用）：记录静态 gp/dp/da，按需重建 offscreen，然后调用 `drawImage()`。`displayAlgorithm == 0` 时走本类像素路径，否则委托 `VectorDrawing.render()`（算法 1 矢量渲染）。

#### `public void abortRendering()`
- 行号: L407
- 描述: 当 displayAlgorithm > 0 时把中止请求转发给 VectorDrawing。

### 嵌套类（数据与接口）
- `ExpandedCellKey (private static, L228)` — cell+Orientation 的缓存键（equals/hashCode 按引用+朝向比较）。
- `ExpandedCellInfo (private static, L260)` — 记录 singleton、instanceCount、offscreen（缓存 cell 的独立 PixelDrawing）。
- `PatternedOpaqueLayer (private static, L306)` — 包装一个 POL 位平面 byte[][]。
- `PolySeg (private static, L213)` — 扫描线多边形填充的边表节点。
- `RenderTextInfo (private static, L3356)` — 文本渲染信息（font、GlyphVector、bounds、锚点），`buildInfo`（L3367）完成布局计算。
- `static interface ERaster (L1923)` — 旧版光栅接口（fillBox/fillHorLine/fillVerLine/fillPoint/drawHorLine/drawVerLine/drawPoint/getOutline）。

### 构造方法
#### `public PixelDrawing(Dimension sz)`
- 行号: L419
- 描述: 顶层窗口离屏构造：分配 `TYPE_INT_RGB` BufferedImage 并取出 int[] 作为 opaqueData，裁剪区设为整个窗口。

#### `public PixelDrawing(double scale, Rectangle screenBounds)`
- 行号: L436
- 描述: 缓存 cell 用离屏构造：img 为 null，仅分配 int[] opaqueData，按 screenBounds 设置原点并清空。

### 顶层方法
#### `void initOrigin(double scale, double offx, double offy)`（包内）
- 行号: L457
- 描述: 由渲染偏移设置 originX/originY。

#### `void initDrawing(double scale)`（包内）
- 行号: L463
- 描述: 清图并把原点设为 (0,0) 偏移（VectorDrawing 调色板路径使用）。

#### `public void setPrintingMode(int mode)`
- 行号: L473
- 描述: 设置打印模式 0/1/2（彩色显示/彩色打印/黑白打印）。

#### `public void setBackgroundColor(Color bg)`
- 行号: L482
- 描述: 覆盖背景色（打印时强制白色），须在 drawImage 之前调用。

#### `public BufferedImage getBufferedImage()` / `int[] getOpaqueData()` / `public Dimension getSize()`
- 行号: L490 / L499 / L507
- 描述: 获取渲染结果图像 / RGB 数组（包内）/ 离屏尺寸。

#### `public static void clearSubCellCache()`
- 行号: L515
- 描述: **`[重要]`** 清空展开 subcell 缓存（新建 expandedCells 表）；层可见性变化等导致全部重绘时调用。

#### `public void setWindow(EditWindow0 wnd)`
- 行号: L523
- 描述: 设置渲染关联的 EditWindow0（打印时有用）。

#### `private void drawImage(Drawing drawing, boolean fullInstantiate, Rectangle2D drawLimitBounds, boolean isPixelDrawing, double greekSizeLimit, double greekCellSizeLimit)`
- 行号: L534
- 描述: **`[重要]`** 离屏渲染主入口：初始化比例/原点/裁剪/颜色（textGraphics 等静态样式），处理 changedCells 缓存逐出与 VectorCache 强制刷新；先 `countCell` 预扫描统计实例数，再 `drawCell` 递归渲染（isPixelDrawing=false 时委托 VectorDrawing）；随后在 `synchronized(img)` 内先叠加网格（drawGrid）再调用 `composite(renderBounds)` 合成透明层。统计开关 TAKE_STATS 打开时输出计时/内存报告。

#### `public void printImage(double scale, Point2D offset, Cell cell, VarContext varContext, ElectricPrinter ep, LayerVisibility lv)`
- 行号: L664
- 描述: 打印渲染入口：按打印机偏好设置颜色与比例，算法 0 走像素路径、否则 VectorDrawing，最后 `composite(null)` 合成。

#### `public void clearImage(Rectangle bounds, Cell cell)`
- 行号: L744
- 描述: **`[重要]`** 渲染前清屏：检测技术变化并初始化透明层（initForTechnology），清空全部透明位平面与 POL 位图，将不透明区域填充为背景色（bounds 为 null 时清全图，否则只清区域）。

#### `public Image composite(Rectangle bounds)`
- 行号: L806
- 描述: **`[重要]`** 渲染收尾合成：扫描不透明图，对 alpha 为 0xFF（未填充）的像素按各透明位平面组合索引查 `trueColorMap` 取色并写入 opaqueData；层高亮/变暗时先生成调整后的颜色表；文本抗锯齿的中间 alpha 经 `alphaBlend` 与底色混合。返回合成好的 img。

#### `private void drawGrid(EditWindow wnd, WindowFrame.DisplayAttributes da)`
- 行号: L976
- 描述: 在合成前把网格点直接写入 opaqueData：普通点、每 10 格粗点（allBoldDots 时全部画粗点）、可选坐标轴（dp.gridAxesShown）。

#### `private Rectangle2D displayableBounds(FixpTransform intoCellTransform)`
- 行号: L1108
- 描述: 返回窗口可视范围（数据库坐标），经 intoCellTransform 变换。

#### `private void initForTechnology(Cell cell)`
- 行号: L1124
- 描述: 初始化透明层：从 GraphicsPreferences 取当前技术的透明层数与颜色表；技术库 cell（Artwork "factors"）时从 factors cell 提取颜色表；技术变化时重建 layerBitMaps 数组。

#### `private void periodicRefresh()`
- 行号: L1172
- 描述: 长时间渲染的周期性刷新：每画 100 个对象且距上次刷新超过 1 秒时，若 wnd 是 EditWindow 则 repaint（打印用 EditWindow0 不刷新）。

#### `private void drawCell(Cell cell, Rectangle2D drawLimitBounds, boolean fullInstantiate, Orientation orient, FixpTransform prevTrans, Cell topCell)`
- 行号: L1192
- 描述: **`[重要]`** 层次遍历：先画 cell 的 arc（drawArc），再画 node（drawNode），顶层时绘制 cell 显示变量（文本）。

#### `private void drawNode(NodeInst ni, Orientation orient, FixpTransform trans, Cell topCell, Rectangle2D drawLimitBounds, boolean fullInstantiate, boolean forceVisible)`
- 行号: L1254
- 描述: **`[重要]`** 单个 NodeInst 的绘制分派：cell 实例先做屏幕范围剔除，展开实例优先走 expandedCellCached 缓存、否则递归 drawCell；未展开实例画黑盒轮廓+实例名（drawUnexpandedCell）；基元节点按层画形状（drawPolys），过小基元用 drawTinyLayers 单点近似；最后画 export 文本与变量。

#### `private void drawArc(ArcInst ai, FixpTransform trans, boolean forceVisible)`
- 行号: L1416
- 描述: 画 ArcInst：过小弧（arcSize < maxObjectSize）直接丢弃计数；细长弧用 drawTinyArc 两点近似画线；否则画弧形状与弧文本。

#### `private void showCellPorts(NodeInst ni, FixpTransform trans, boolean expanded, boolean onPathDown)`
- 行号: L1462
- 描述: 画实例端口：按 portDisplayLevel（2=十字、1=短名、0=全名）画端口文本或十字，端口颜色取自 PrimitivePort。

#### `private void drawUnexpandedCell(NodeInst ni, Poly poly)`
- 行号: L1516
- 描述: 画未展开实例的黑盒：轮廓线（instanceGraphics）加实例名文本。

#### `private void drawTinyLayers(Iterator<Layer> layerIterator, int x, int y)`
- 行号: L1540
- 描述: 微小基元近似：对该节点各层在 (x,y) 处写一个像素（透明层置位、不透明层写 opaqueData），尊重图案模式。

#### `private void drawTinyArc(Iterator<Layer> layerIterator, Point head, Point tail)`
- 行号: L1575
- 描述: 细长弧近似：对每个层从 head 到 tail 画一条线。

#### `private boolean expandedCellCached(Cell subCell, Orientation orient, FixpTransform origTrans, Cell topCell, Rectangle2D drawLimitBounds, boolean fullInstantiate)`
- 行号: L1597
- 描述: **`[重要]`** 展开 cell 缓存核心：判断该 cell+朝向组合是否可复用缓存；实例数不足 2（singleton）时不缓存（每帧 numberToReconcile 个例外）；宽高均超过半屏的 cell 不缓存；首次使用时创建独立 PixelDrawing 离屏并递归渲染，之后通过 `copyBits` 把缓存位复制回主缓冲。

#### `private void countCell(Cell cell, Rectangle2D drawLimitBounds, boolean fullInstantiate, Orientation orient, FixpTransform prevTrans)` / `private void countNode(NodeInst ni, Rectangle2D drawLimitBounds, boolean fullInstantiate, Orientation orient, FixpTransform trans)`
- 行号: L1669 / L1695
- 描述: **`[重要]`** 渲染前预扫描：递归统计每个（cell, 朝向）组合的实例出现次数，决定哪些展开 cell 值得缓存（出现 2 次以上）。

#### `public static void forceRedraw(Cell cell)`
- 行号: L1760
- 描述: **`[重要]`** 登记 cell 已修改：把 cell id 加入静态 changedCells 集合，下一次渲染时该 cell 的缓存被逐出。

#### `private static void forceRedraw(Set<CellId> changedCells)`
- 行号: L1767
- 描述: 渲染开始时执行：从 expandedCells 中删除所有在 changedCells 里的缓存条目。

#### `private void copyBits(PixelDrawing srcOffscreen, int centerX, int centerY)`
- 行号: L1785
- 描述: 把缓存 cell 的离屏位复制进主缓冲（不透明像素 + 各透明位平面 + POL 位图；顶层渲染时 POL 转回屏幕对齐的图案）。

#### `private void drawPolys(Poly[] polys, FixpTransform trans, boolean forceVisible)`
- 行号: L1996
- 描述: 批量绘制 Poly 数组：逐层检查可见性/变暗，变换后交 renderPoly 光栅化，并做周期刷新。

#### `byte[][] getLayerBitMap(int layerNum)` / `private byte[][] newLayerBitMap(int layerNum)`
- 行号: L2032 / L2043
- 描述: 按需获取/分配透明层位平面（首次使用时分配并清零）。

#### `private void renderPoly(Poly poly, EGraphics graphics, boolean dimmed)`
- 行号: L2060
- 描述: **`[重要]`** Poly 光栅化分派：按 Poly.Type 分派到 drawBox/drawPolygon/drawLine/drawCircle/drawDisc/drawCircleArc/drawCross/drawText；缓存 cell 中图案化不透明层转写 POL 位图。

#### `EGraphics getPortGraphics(PrimitivePort basePort)` / `int getTheColor(EGraphics desc, boolean dimmed)`（包内）
- 行号: L2243 / L2255
- 描述: 端口颜色缓存查询 / 取得绘制色（黑白打印返回 0；层高亮时 dim 或 brighten）。

#### `void drawBox(int lX, int hX, int lY, int hY, byte[][] layerBitMap, EGraphics desc, boolean dimmed)`（包内）
- 行号: L2412
- 描述: 矩形填充：纯色或图案填充进 opaqueData 或透明位平面，图案化填充后再画 outline。

#### `void drawLine(Point pt1, Point pt2, byte[][] layerBitMap, EGraphics desc, int texture, boolean dimmed)`（包内）
- 行号: L2502
- 描述: 线绘制入口：先裁剪（GenMath.clipLine），按 texture（0 实线、1/2 图案线、3 粗线）分派。

#### `void drawPolygon(Point[] points, byte[][] layerBitMap, EGraphics desc, boolean dimmed)`（包内）
- 行号: L2818
- 描述: 扫描线多边形填充（PolySeg 边表 + 活动边表），支持图案填充与 outline。

#### `public void drawText(Rectangle rect, Poly.Type style, TextDescriptor descript, String s, byte[][] layerBitMap, EGraphics desc, boolean dimmed)`
- 行号: L3028
- 描述: **`[重要]`** 文本绘制：解析 TextDescriptor（字体、旋转、颜色索引、大小），过小文本转为 Greek 线（drawBox 画横线），否则经 RenderTextInfo 布局、renderText 光栅化为灰度图，再按旋转（0/90/180/270）逐像素带 alpha 混合写入缓冲。

#### `void drawCircle(Point center, Point edge, byte[][] layerBitMap, EGraphics desc, boolean dimmed)` / `void drawThickCircle(...)` / `void drawDisc(...)` / `void drawCircleArc(Point center, Point p1, Point p2, boolean thick, boolean bigArc, ...)`（包内）
- 行号: L3550 / L3668 / L3780 / L3986
- 描述: Bresenham 圆/粗圆、扫描线填充圆盘、八分圆法圆弧（bigArc 控制大弧）。

#### `void drawPoint(int x, int y, byte[][] layerBitMap, int col)`（包内）
- 行号: L4084
- 描述: 单点写入：不透明层直接写 int，透明层置位。

#### `public static Font getFont(String msg, String font, int tSize, boolean italic, boolean bold, boolean underline)`
- 行号: L3530
- 描述: 按名称/风格构造 Font，失败时打印错误。

### 内部辅助方法（简述）
- **坐标变换**：`databaseToScreen(double,double,Point)`（L4123）、`screenToDatabase(int,int,Point2D)`（L4130）、`databaseToScreen(Rectangle2D)`（L4139）——数据库坐标与屏幕像素互转。
- **Bresenham 线族**：`drawSolidLine`（L2538）、`drawPatLine`（L2638，图案线）、`drawThickLine`（L2738，粗线）、`drawOutline`（L2625）、`drawCross(Poly,...)`（L2528）。
- **圆盘扫描线**：`drawDiscRow`（L3734）。
- **粗点**：`drawThickPoint`（L4092，中心点加四邻点）。
- **HSV 颜色**：`dimColor`（L2275）、`brightenColor`（L2290）、`fromRGBtoHSV`（L2306）、`fromHSVtoRGB`（L2351）——层高亮/变暗的颜色调整。
- **alpha 混合**：`alphaBlend(int,int,int)`（L3341）。
- **文本光栅化**：`getTextCorner`（L3429，锚点偏移计算）、`renderText(RenderTextInfo)`（L3486，用复用的灰度 BufferedImage 渲染 GlyphVector）。
- **圆弧八分法**：`arcFindOctant`（L3844）、`arcXformOctant`（L3865）、`arcDoPixel`（L3879）、`arcOutXform`（L3889）、`arcBresCW`（L3908）/`arcBresMidCW`（L3929）/`arcBresMidCCW`（L3945）/`arcBresCCW`（L3960）、`MODM/MODP`（L4074/L4078）——按八分圆表逐象限 Bresenham 出点。

---

## LayerDrawing
**文件**: `com/sun/electric/tool/user/redisplay/LayerDrawing.java`（2984 行）
**职责**: 分层光栅绘制引擎（displayAlgorithm=2 时用于版图窗口）：把 VectorCache 缓存的矢量形状按层光栅化进各自的 `TransparentRaster`（每 int 存 32 像素的位图），文本/端口/十字等收集为绘制列表；合成不在渲染线程进行，而是由内嵌 Drawing 在 AWT 线程用 `AlphaBlender` 逐行 alpha 混合写进 VolatileImage 再贴屏。支持层不透明度（非 legacy 合成模式）与图案化显示（缩小到一定比例后）。渲染与合成通过 `DrawingData` 快照解耦两个线程。
**继承/实现**: `class LayerDrawing extends AbstractLayerDrawing`（包私有）；消费包级 `ERaster` 接口（实现类见 TransparentRaster/OpaqueRaster/PatternedTransparentRaster/EmptyPatternedOutlinedTransparentRaster）；内嵌 `static class Drawing extends AbstractDrawing`。
**关键字段**:
- `globalTextScale (double)` / `defaultFont (String)` — 全局文本比例与默认字体
- `varContext (VarContext)` — 渲染变量上下文
- `inPlaceSubcellPath (BitSet)` / `inPlaceCurrent (Cell)` — in-place 编辑路径与当前 cell
- `canDrawText (boolean)` / `canDrawRelativeText (double)` — 文本可见性阈值
- `static maxObjectSize (double)` — 微小对象近似阈值
- `total (int)` — 窗口像素总数
- `renderTextList / greekTextList / crossTextList (ArrayList<...>)` — 待 AWT 线程绘制的文本/Greek 线/十字列表
- `numIntsPerRow (int)` — 每行 int 数（每 int 存 32 像素）
- `layerRasters (Map<Layer, TransparentRaster>)` — 每层的透明光栅
- `instanceRaster / gridRaster / highlightRaster (TransparentRaster)` — 实例轮廓/网格/高亮专用光栅
- `transparentRasterPool (List<SoftReference<TransparentRaster>>)` — 光栅软引用池（复用）
- `renderedWindow (boolean)` — 是否顶层窗口
- `patternedDisplay (boolean)` — 是否图案化显示（scale > patternedScaleLimit）
- `static alphaBlendingOvercolor (boolean)` — overcolor alpha 混合（scale > alphaBlendingOvercolorLimit）
- `static expandedCells / changedCells / expandedScale / numberToReconcile` — 展开 cell 缓存与失效机制（同 PixelDrawing 思路）
- `gp (GraphicsPreferences)` / `dp (DrawingPreferences)` / `textColor (Color)` / `portColorsCache (HashMap<PrimitivePort, Color>)` — 绘图偏好与颜色缓存
- `static topSz (Dimension)` — 顶层窗口尺寸
- `dummyWnd (EditWindow0)` — 假窗口（供文本/变量排版）

### 静态常量
- `MINIMUMTEXTSIZE = 5`（L199）、`SINGLETONSTOADD = 5`（L201）、`MAXIMUMTEXTSIZE = 200`（L203，文本大小上限）
- `ENHANCE_EMPTY_PATTERNS = true`（L210，空图案层增强为描边）、`USE_HIGHLIGHT_RASTER = false`（L211，高亮光栅开关）

### 嵌套类 Drawing（extends AbstractDrawing，AWT 侧控制器）
字段：`SMALL_IMG_HEIGHT = 2`（每次合成的行高）、`vImg (VolatileImage)`、`smallImg (BufferedImage)` + `smallOpaqueData (int[])`、`alphaBlender (AlphaBlender)`、`volatile needComposite`、`volatile drawingData (DrawingData)`、`highlightingLayers`、`patternedScaleLimit`、`alphaBlendingOvercolorLimit`。

#### `public boolean paintComponent(Graphics2D g, LayerVisibility lv, Dimension sz)`
- 行号: L453
- 描述: **`[重要]`** AWT 线程调用：校验 drawingData；按需创建 VolatileImage 与 smallImg；若 vImg 内容丢失/不兼容或 needComposite 为真，先 `renderOffscreen` 重合成；随后把 vImg 贴到 g、恢复窗口坐标，并绘制两个 Highlighter（`highlightRaster` 非空时通知 Highlighter 栅格高亮已画；当前 USE_HIGHLIGHT_RASTER=false，实际恒为 null）。

#### `private void renderOffscreen(GraphicsPreferences gp, LayerVisibility lv, DrawingData dd)`
- 行号: L514
- 描述: **`[重要]`** 在 vImg 上执行合成：计算层高亮状态，按 `User.isLegacyComposite()` 选择 legacyLayerComposite 或 layerComposite（AlphaBlender 逐行 alpha 混合），然后依次绘制 GreekTextInfo/CrossTextInfo/RenderTextInfo 文本。

#### `public void opacityChanged()`
- 行号: L569
- 描述: 层不透明度变化通知（EDT）：置 needComposite=true，下次 paintComponent 重合成。

#### `public boolean visibilityChanged()`
- 行号: L579
- 描述: 层可见性变化通知（EDT）：置 needComposite=true 并返回 true（需要全量重绘，渲染线程会重新渲染）。

#### `public boolean hasOpacity()`
- 行号: L586
- 描述: 返回 `!User.isLegacyComposite()`：非 legacy 合成模式支持层不透明度。

#### `private void layerComposite(Graphics2D g, GraphicsPreferences gp, LayerVisibility lv, DrawingData dd)`
- 行号: L590
- 描述: 新式合成：按 getBlendingOrder 的层序与 alpha，将各层位图连同 instance/grid/highlight 光栅交给 AlphaBlender，每次合成 SMALL_IMG_HEIGHT 行后把 smallImg 画到 vImg，循环至整屏。

#### `private void legacyLayerComposite(Graphics2D g, GraphicsPreferences gp, LayerVisibility lv, DrawingData dd)`
- 行号: L652
- 描述: 旧式合成：不透明层覆盖、透明层按技术颜色表（colormap）组合（含层变暗/增亮调整），逐行写入 smallImg 再画到 vImg。

#### `int getTheColor(EGraphics desc, boolean dimmed)` / `private int dimColor(int col)` / `private int brightenColor(int col)` / `private void fromRGBtoHSV(...)` / `private int fromHSVtoRGB(...)`
- 行号: L797 / L818 / L833 / L850 / L896
- 描述: 取色与 HSV 颜色调整（与 PixelDrawing 同款实现）。

#### `private List<AbstractDrawing.LayerColor> getBlendingOrder(Set<Layer> layersAvailable, GraphicsPreferences gp, LayerVisibility lv, boolean patternedDisplay, boolean alphaBlendingOvercolor)`
- 行号: L974
- 描述: **`[重要]`** 计算 alpha 混合层序：按 `Layer.getLayersSortedByRule(ByHeightContact)` 排序，过滤不可见层，按 `lv.getOpacity(layer)` 构造 LayerColor（预乘或 overcolor 两种方式）。

#### `public void render(Dimension sz, WindowFrame.DisplayAttributes da, GraphicsPreferences gp, DrawingPreferences dp, boolean fullInstantiate, ERectangle bounds)`
- 行号: L1021
- 描述: **`[重要]`** 渲染入口（Job 线程调用）：复用或新建 LayerDrawing 离屏，先后调用 `offscreen.drawImage(false, null, ...)` 与（fullInstantiate 时）`drawImage(true, bounds, ...)` 两遍渲染；完成后回收旧 DrawingData 光栅并生成新 DrawingData 快照，置 needComposite=true。

#### `public static boolean hasJogl()`
- 行号: L1065
- 描述: 通过反射探测 JOGL 插件（com.sun.electric.plugins.jogl.LayerDrawer.showLayer）是否可用。

#### `public void testJogl()`
- 行号: L1080
- 描述: JOGL 自测（生成 100 万个随机 box 的输入，当前实现仅构造数据）。

### 嵌套类 DrawingData（L357）
- 描述: `render` 完成后对离屏数据的快照（width/height/numIntsPerRow/patternedDisplay、各光栅引用、三类文本数组），构造时把数据从 LayerDrawing 中"取走"（清空原列表）；`recycleRasters()`（L418）把光栅归还软引用池。

### 嵌套类（其他）
- `ExpandedCellKey (private static, L214)` — CellId+Orientation 缓存键。
- `ExpandedCellInfo (private static, L247)` — singleton/instanceCount/tooLarge/offscreen（LayerDrawing）。
- `TextInfo (private static, L2720)` — 文本绘制公共基类（颜色 + baseNode），`isDrawn` 检查基元可见性并设色。
- `RenderTextInfo (private static, L2742)` — 文本布局信息，`buildInfo`（L2757）计算 GlyphVector/锚点/包围盒，`draw`（L2841）在 vImg 上按旋转绘制（支持下划线）。
- `GreekTextInfo (private class, L2867)` — 过小文本的"Greek 线"。
- `CrossTextInfo (private class, L2887)` — 端口/export 的十字标记。

### 顶层方法
#### `static void drawTechPalette(Graphics2D g, GraphicsPreferences gp, int imgX, int imgY, Rectangle entrySize, double scale, VectorCache.VectorBase[] shapes)`（包内静态）
- 行号: L1260
- 描述: 技术调色板（technology palette）预览绘制：建临时 LayerDrawing 光栅化 shape 列表，用 AlphaBlender 合成后画到 g（透明度固定 0.7），文本逐个偏移绘制。由 AbstractDrawing.drawShapes 在算法 ≥2 时调用。

#### `private static List<AbstractDrawing.LayerColor> getBlendingOrderForTechPalette(GraphicsPreferences gp, Set<Layer> layersAvailable)`
- 行号: L1353
- 描述: 调色板专用的混合顺序（固定 opacity 0.7、overcolor 混合）。

#### `public LayerDrawing(Dimension sz)` / `public LayerDrawing(double scale, int lX, int hX, int lY, int hY)`
- 行号: L1389 / L1398
- 描述: 顶层窗口构造 / 缓存 cell 离屏构造（按网格范围设置原点与裁剪）。

#### `public Dimension getSize()`
- 行号: L1411
- 描述: 返回离屏尺寸。

#### `public static void clearSubCellCache()`
- 行号: L1419
- 描述: **`[重要]`** 清空展开 cell 缓存。

#### `private void drawImage(Drawing drawing, boolean fullInstantiate, ERectangle drawLimitBounds, double patternedScaleLimit, double alphaBlendingOvercolorLimit)`
- 行号: L1430
- 描述: **`[重要]`** 离屏渲染主入口：设置 in-place 路径、比例/原点/图案化与 alpha 阈值；clearImage 清屏；处理 changedCells 与 VectorCache 强制刷新；countCell 预扫描后 drawCell 递归渲染；最后叠加网格（drawGrid）与（可选）高亮光栅（drawHighlight）。

#### `public void clearImage(Rectangle bounds)`
- 行号: L1570
- 描述: 清屏：擦除全部或裁剪框内的各层光栅（eraseAll/eraseBox）。

#### `private void drawGrid(EditWindow wnd, WindowFrame.DisplayAttributes da)`
- 行号: L1604
- 描述: 把网格写入 gridRaster 光栅（点/粗点/坐标轴），与 PixelDrawing 版本逻辑相同但目标是 ERaster。

#### `private void drawHighlight(EditWindow wnd)`
- 行号: L1780
- 描述: （USE_HIGHLIGHT_RASTER 开启时）把 Highlighter 的栅格高亮写入 highlightRaster。

#### `private void drawCell(Cell cell, ERectangle drawLimitBounds, boolean fullInstantiate, Orientation orient, long oX, long oY, int level, VarContext context)`
- 行号: L1837
- 描述: **`[重要]`** 层次遍历：从 VectorCache 取 VectorCell（按 cellId+朝向+context+scale），子 cell 经 R-Tree 搜索裁剪（getSearchBounds）；展开子 cell 优先 expandedCellCached 缓存复用，未展开画黑盒轮廓到 instanceRaster；最后按层 drawList 绘制基元形状，顶层再画 topOnlyShapes。

#### `private boolean expandedCellCached(Cell subCell, Orientation orient, long oX, long oY, int level, VarContext context, boolean fullInstantiate)`
- 行号: L1967
- 描述: **`[重要]`** 展开 cell 缓存核心（同 PixelDrawing 策略）：singleton 计数、过大 cell（宽高均超 topSz/32）不缓存、首次创建独立 LayerDrawing 离屏渲染、之后 copyBits 复用。

#### `private void countCell(Cell cell, ERectangle drawLimitBounds, boolean fullInstantiate, Orientation orient, FixpTransform prevTrans)` / `private void countNode(Cell parent, ImmutableNodeInst n, boolean fullInstantiate, Orientation orient, FixpTransform trans)`
- 行号: L2063 / L2088
- 描述: **`[重要]`** 预扫描统计展开 cell 实例数（直接遍历 cell 的 ImmutableNodeInst 备份）。

#### `public static void forceRedraw(Cell cell)` / `private static void forceRedraw(Set<CellId> changedCells)`
- 行号: L2155 / L2162
- 描述: **`[重要]`** 登记 cell 修改 / 渲染前逐出 changedCells 对应的缓存条目。

#### `private void copyBits(LayerDrawing srcOffscreen, long gridCenterX, long gridCenterY)`
- 行号: L2182
- 描述: 把缓存 cell 离屏的各层光栅与 instanceRaster 按位复制进主缓冲。

#### `ERaster getRaster(Layer layer, EGraphics graphics, boolean forceVisible)`（包内）
- 行号: L2217
- 描述: 取层的 TransparentRaster（按需分配）；ENHANCE_EMPTY_PATTERNS 时对空图案层返回 EmptyPatternedOutlinedTransparentRaster，图案化显示时返回 PatternedTransparentRaster。

#### `ERaster getInstanceRaster()` / `ERaster getGridRaster()` / `ERaster getHighlightRaster()`（包内）
- 行号: L2271 / L2278 / L2285
- 描述: 取实例/网格/高亮专用光栅（按需分配）。

#### `private synchronized TransparentRaster allocateTransparentRaster()` / `private synchronized void recycleTransparentRaster(TransparentRaster raster)`
- 行号: L2293 / L2304
- 描述: 光栅池分配（优先复用软引用池，否则新建）与回收。

#### `private void drawList(long oX, long oY, List<VectorCache.VectorBase> shapes)` / `private void drawList(long oX, long oY, Layer layer, List<VectorCache.VectorBase> shapes)`
- 行号: L2321 / L2350
- 描述: 绘制缓存形状列表：VectorText 走 drawText，其余按层取光栅后 drawShape；第二个重载按层批处理（glyphLay 特殊处理）。

#### `private void drawText(long oX, long oY, VectorCache.VectorText vt)`
- 行号: L2377
- 描述: 处理缓存文本：按 TextType 检查可见性开关、相对尺寸阈值与临时名开关；export 按 displayLevel 转十字（crossTextList）或文本，其余转 drawText 排队。

#### `private void drawShape(long oX, long oY, ERaster raster, VectorCache.VectorBase vb)`
- 行号: L2462
- 描述: 单个缓存形状分派：VectorManhattan→drawBox、VectorPolygon→drawPolygon、VectorLine→drawLine、VectorCross→drawCross、VectorCircle→drawCircle/drawThickCircle/drawDisc、VectorCircleArc→drawCircleArc（均调用 AbstractLayerDrawing 继承方法）。

#### `private void drawPortList(VectorCache.VectorSubCell vsc, VectorCache.VectorCell subVC_, long oX, long oY, boolean expanded, boolean onPathDown)`
- 行号: L2535
- 描述: 画实例端口列表：按 portDisplayLevel 画十字（crossTextList）或端口名文本，端口颜色取自 PrimitivePort（缓存）。

#### `public void drawText(Rectangle rect, Poly.Type style, TextDescriptor descript, String s, Color color, PrimitiveNode baseNode)`
- 行号: L2596
- 描述: **`[重要]`** 文本绘制入口：解析 TextDescriptor、过小转 Greek 线（greekTextList）、否则创建 RenderTextInfo 加入 renderTextList，等待 AWT 线程合成阶段真正绘制。

#### `public static Font getFont(String msg, String font, int tSize, boolean italic, boolean bold, boolean underline)`
- 行号: L2968
- 描述: 按名称/风格构造 Font（与 PixelDrawing 同款）。

### 内部辅助方法（简述）
- `displayableBounds(FixpTransform)`（L1803）— 窗口可视范围（数据库坐标）。
- `periodicRefresh()`（L1819）— 每 100 对象/1 秒周期刷新窗口。
- `getTextCorner(...)`（L2915，private static）— 文本锚点偏移计算。
- 光栅化基元（框/线/多边形/圆/弧/坐标变换）全部继承自 AbstractLayerDrawing，本类不含重复实现。

---

## AbstractDrawing
**文件**: `com/sun/electric/tool/user/redisplay/AbstractDrawing.java`（161 行）
**职责**: 重绘算法的抽象基类（"Base class for redisplay algorithms"）。持有 EditWindow 与 DisplayAttributes，定义 `paintComponent`/`render` 两个核心契约及生命周期钩子（中止渲染、不透明度/可见性变化通知），并提供算法工厂、缓存清理与调色板绘制等静态工具方法。
**继承/实现**: 无父类；PixelDrawing.Drawing 与 LayerDrawing.Drawing 继承它。
**关键字段**:
- `wnd (final EditWindow)` — 关联的编辑窗口（public final）
- `da (WindowFrame.DisplayAttributes)` — 当前显示属性（比例、偏移等）

### 方法
#### `protected AbstractDrawing(EditWindow wnd)`
- 行号: L48
- 描述: 构造：仅保存 wnd。

#### `public static AbstractDrawing createDrawing(EditWindow wnd, AbstractDrawing drawing, Cell cell)`
- 行号: L52
- 描述: **`[重要]`** 算法工厂：displayAlgorithm==2 且 cell 为版图（非 icon、非原理图、技术是 layout）时返回 LayerDrawing.Drawing，否则返回 PixelDrawing.Drawing。

#### `public abstract boolean paintComponent(Graphics2D g, LayerVisibility lv, Dimension sz)`
- 行号: L63
- 描述: **`[重要]`** 抽象契约：AWT 线程调用，把离屏图像贴到 g 并叠加高亮；返回是否绘制成功。

#### `public abstract void render(Dimension sz, WindowFrame.DisplayAttributes da, GraphicsPreferences gp, DrawingPreferences dp, boolean fullInstantiate, ERectangle bounds)`
- 行号: L65
- 描述: **`[重要]`** 抽象契约：离屏渲染入口，由 Job 线程调用；fullInstantiate 为 peek 模式（渲染到底层），bounds 限制绘制区域。

#### `void setupEditWindowCoordinates(Dimension sz, WindowFrame.DisplayAttributes da)`（包内）
- 行号: L68
- 描述: 转发给 `wnd.setupCoordinates(sz, da)`，恢复窗口坐标（供鼠标事件/高亮换算）。

#### `public void abortRendering()`
- 行号: L72
- 描述: 中止渲染钩子（基类空实现，PixelDrawing.Drawing 转发给 VectorDrawing）。

#### `public void opacityChanged()`
- 行号: L75
- 描述: 层不透明度变化通知（基类空实现，LayerDrawing.Drawing 置 needComposite）。

#### `public boolean visibilityChanged()`
- 行号: L82
- 描述: 层可见性变化通知；返回 true 表示需要全量重绘。

#### `public boolean hasOpacity()`
- 行号: L86
- 描述: 是否支持层不透明度（基类返回 false）。

#### `public void testJogl()`
- 行号: L90
- 描述: JOGL 自测钩子（基类空实现）。

#### `public static void clearSubCellCache(boolean layerAlso)`
- 行号: L97
- 描述: **`[重要]`** 清空展开 subcell 缓存：总是清 PixelDrawing 缓存，layerAlso 为 true 时同时清 LayerDrawing 缓存（层可见性变化导致全量重绘时使用）。

#### `public static void forceRedraw(Cell cell)`
- 行号: L104
- 描述: **`[重要]`** 通知两个引擎该 cell 已修改（登记进各自的 changedCells），下次渲染逐出相关缓存。

#### `public static void drawShapes(Graphics2D g, GraphicsPreferences gp, int imgX, int imgY, double scale, VectorCache.VectorBase[] shapes, PixelDrawing offscreen, Rectangle entryRect)`
- 行号: L112
- 描述: 绘制变换后的形状组（技术调色板预览用）：算法 <2 或 legacy 合成时用 VectorDrawing 渲染进 PixelDrawing 离屏再 composite 贴图；否则调用 LayerDrawing.drawTechPalette。

### 嵌套类
#### `public static class LayerColor`
- 行号: L126
- 描述: alpha 混合层描述：`premultipliedRed/Green/Blue` + `inverseAlpha`（合成公式 nextRgb = inverseAlpha*prevRgb + premultipliedRgb）+ 关联 layer。
- 构造 `public LayerColor(Layer layer, float premultipliedRed, float premultipliedGreen, float premultipliedBlue, float inverseAlpha)`（L135）；构造 `public LayerColor(Color color)`（L143，layer 为 null、inverseAlpha 为 0）。

#### `public static class DrawingPreferences`
- 行号: L153
- 描述: 一次性读取的绘图偏好快照（包内字段）：`gridAxesShown`、`gridXBoldFrequency`、`gridYBoldFrequency`、`globalTextScale`、`highlightConnectedObjects`。

---

## AbstractLayerDrawing
**文件**: `com/sun/electric/tool/user/redisplay/AbstractLayerDrawing.java`（1392 行）
**职责**: LayerDrawing 的抽象基类，提供与具体光栅实现无关的共享光栅化基元（框/线/十字/多边形/圆/盘/圆弧）与数据库-屏幕坐标变换。所有绘制都通过传入的 `ERaster` 目标完成，因此可同时服务于 TransparentRaster/OpaqueRaster 等多种光栅。
**继承/实现**: 无父类；被 LayerDrawing 继承。使用包级接口 `ERaster`（ERaster.java）作为绘制目标抽象。
**关键字段**:
- `sz (final Dimension)` — 窗口尺寸
- `scale (double)` — 显示比例；`scale_ (private double)` = scale/DBMath.GRID（网格比例）
- `originX / originY (double)` — cell 原点在屏幕上的位置
- `factorX / factorY (private float)` — 网格坐标换算的窗口平移因子
- `clipLX / clipHX / clipLY / clipHY (int)` — 当前裁剪矩形
- `drawLimitBounds (ERectangle)` — 显示范围限制（数据库坐标）
- `tempPt1-tempPt4 (final Point)` / `tempFixpRect (FixpRectangle)` — 复用临时对象

### 方法
#### `public AbstractLayerDrawing(Dimension sz)`
- 行号: L67
- 描述: 构造：保存尺寸副本并设置全窗口裁剪。

#### `void initOrigin(double scale, int originX, int originY)` / `void initOrigin(double scale, Point2D offset)`（包内）
- 行号: L73 / L83
- 描述: 设置比例/原点（两种偏移表达），并计算 scale_ 与 factorX/factorY。

#### `Rectangle setClip(ERectangle drawLimitBounds)`（包内）
- 行号: L96
- 描述: **`[重要]`** 设置裁剪：根据窗口反推屏幕数据库范围（screenDb），与 drawLimitBounds 求交得 drawLimitBounds，同时把裁剪矩形设为相应屏幕区域（null 时为全窗口）；返回渲染区屏幕矩形。

#### `private void setClip(int clipLX, int clipHX, int clipLY, int clipHY)`
- 行号: L111
- 描述: 直接设置四个裁剪边界。

#### `FixpRectangle getSearchBounds(long oX, long oY, Orientation orient)`（包内）
- 行号: L118
- 描述: 把 drawLimitBounds 平移到 (oX,oY) 并做逆朝向变换，得到子 cell 坐标系的搜索范围（供 R-Tree 搜索用）。

#### `public void drawBox(long gridLX, long gridLY, long gridHX, long gridHY, ERaster raster)`
- 行号: L132
- 描述: 矩形绘制：网格坐标转屏幕并裁剪后，填充满矩形（fillBox）；若带 Outline 样式则按实线（含 thickness 逐层内缩）或图案（drawHorOutline/drawVerOutline）描边。

#### `public void drawLine(long gridX1, long gridY1, long gridX2, long gridY2, int texture, ERaster raster)`
- 行号: L261
- 描述: 线绘制（网格坐标入口）：转屏幕坐标后按 texture（0 实线、1/2 图案线、3 粗线）分派。

#### `public void drawCross(long gridX, long gridY, int size, ERaster raster)`
- 行号: L293
- 描述: 十字绘制：过中心画水平与垂直两条实线（各自裁剪）。

#### `void drawPolygon(long gridOX, long gridOY, EPoint[] gridPoints, ERaster raster)`（包内）
- 行号: L586
- 描述: 多边形绘制：顶点转屏幕坐标、clipPoly 裁剪后建 PolySeg 边表，扫描线填充（fillHorLine），带 Outline 时按角度偏移画厚度描边。

#### `void drawCircle(long gridCenterX, long gridCenterY, long gridEdgeX, long gridEdgeY, ERaster raster)` / `void drawThickCircle(...)` / `void drawDisc(...)`（包内）
- 行号: L764 / L871 / L969
- 描述: Bresenham 圆（含快速无裁剪与裁剪两个版本）/ 粗圆（drawThickPoint 出点）/ 实心圆盘（drawDiscRow 逐扫描线 fillHorLine，带 Outline 时先画圆）。

#### `public void drawOval(int gridCenterX, int gridCenterY, int pixelRadius, ERaster raster)`
- 行号: L975
- 描述: 以像素半径画实心圆（drawDisc 的像素半径入口）。

#### `void drawCircleArc(long gridCenterX, long gridCenterY, long gridX1, long gridY1, long gridX2, long gridY2, boolean thick, ERaster raster)`（包内）
- 行号: L1213
- 描述: 顺时针圆弧：过圆心、起点 (x1,y1) 与终点 (x2,y2)；八分圆 Bresenham 出点（arcOctTable 控制哪些八分圆输出）。

#### `void drawThickPoint(int x, int y, ERaster raster)`（包内）
- 行号: L1319
- 描述: 粗点：中心点加四邻点。

#### `public void databaseToScreen(double dbX, double dbY, Point result)`
- 行号: L1341
- 描述: **`[重要]`** 数据库坐标转屏幕像素（四舍五入），Y 轴反向（originY - dbY*scale）。

#### `void gridToScreen(long dbX, long dbY, Point result)`（包内）
- 行号: L1348
- 描述: **`[重要]`** 网格坐标转屏幕像素：用 factorX/factorY 与 scale_ 换算（快路径）。

#### `void screenToGrid(int scrX, int scrY, Point result)`（包内）
- 行号: L1355
- 描述: 屏幕像素转网格坐标（gridToScreen 的逆变换）。

#### `void screenToDatabase(int x, int y, Point2D result)`（包内）
- 行号: L1362
- 描述: 屏幕像素转数据库坐标。

#### `public Rectangle databaseToScreen(Rectangle2D db)`
- 行号: L1371
- 描述: 数据库矩形转屏幕矩形（两角点转换后归一化低/高坐标）。

### 内部辅助方法（简述）
- `drawLine(Point, Point, int, ERaster)`（L270，private）— 线分派；`drawSolidLine`（L313）、`drawPatLine`（L404，图案线）、`drawThickLine`（L498，粗线）— Bresenham 变体；`drawOutline`（L390）— 图案线描边入口；`drawHorOutline`/`drawVerOutline`（L231/L244，private static）— 图案化描边。
- `drawCircle(Point, Point, ERaster)`（L773）、`drawThickCircle(Point, Point, ERaster)`（L880）、`drawDiscRow`（L950）、`drawDisc(Point, Point, ERaster)`（L983）、`drawDisc(Point, int, ERaster)`（L993）— 像素坐标版本。
- 圆弧辅助：`arcFindOctant`（L1045）、`arcXformOctant`（L1070）、`arcDoPixel`（L1092）、`arcOutXform`（L1103）、`arcBresCW`（L1130）/`arcBresMidCW`（L1153）/`arcBresMidCCW`（L1170）/`arcBresCCW`（L1185）、`MODM/MODP`（L1310/L1314）；`drawCircleArc(Point,Point,Point,boolean,ERaster)`（L1224，像素坐标版本）。
- `PolySeg`（private static，L576）— 扫描线填充边表节点。
