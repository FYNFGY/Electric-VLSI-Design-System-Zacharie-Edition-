# 重绘引擎支撑（VectorDrawing / VectorCache / AlphaBlender / Raster 家族）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.redisplay。本文件由 AI 生成。

## 模块概述

本模块是屏幕重绘（redisplay）的底层支撑。`VectorCache` 把电路数据库中的图形对象（矩形、线、多边形、圆、弧、文本、十字、子 cell 实例）转换为与屏幕缩放无关的"网格坐标向量"缓存（每个 cell × 每个朝向一份，支持增量更新），使 `VectorDrawing` 不必每次重绘都遍历数据库。`VectorDrawing` 负责把向量缓存按当前窗口缩放/平移换算成屏幕坐标，并按形状类型分发到 `PixelDrawing` 的 drawBox/drawLine/drawPolygon/drawText 等方法；它还实现了层次递归渲染、裁剪、greeking（过小对象退化为色块或 greeked 图像）与渲染中止机制。`AlphaBlender` 服务于 `LayerDrawing`：把各层的每像素 1 位位图与各层颜色、透明度通过预计算查色表混合成最终的不透明 RGB 缓冲。`ERaster` 接口定义"单层离屏数据"的填充/画线/拷贝操作，其实现家族按层性质分工：`TransparentRaster` 用 1 位/像素位图表示实心透明层；`OpaqueRaster` 直接向不透明缓冲写 ARGB 颜色（实心不透明层）；`PatternedTransparentRaster` 在位图上叠加 16 行图案掩码（带图案的透明层）；`EmptyPatternedOutlinedTransparentRaster` 不填充任何位、只携带轮廓样式（仅轮廓的图案层）。

---

## VectorCache
**文件**: `com/sun/electric/tool/user/redisplay/VectorCache.java`（2134 行）
**职责**: 保存电路显示的可缩放向量表示。为每个 Cell 的每种朝向缓存一份 `VectorCell`（形状按层组织、子 cell 用 R-Tree 索引），在数据库变更时做增量更新，并提供 greeked（淡化）图像数据，供 `VectorDrawing` 快速重绘。
**继承/实现**: 直接继承 Object，无接口。
**关键字段**:
- `theCache (public static final VectorCache)` — 绑定客户端数据库的全局单例缓存。
- `database (public final EDatabase)` — 缓存所服务的数据库。
- `cachedCells (private final ArrayList<VectorCellDef>)` — 以 cellIndex 为下标的 cell 缓存组列表（可含 null）。
- `addPolyToCell (private final Map<CellId, List<VectorBase>>)` — "偷塞"进某 cell 的额外多边形（如临时绘制）。
- `addInstToCell (private final Map<CellId, List<VectorLine>>)` — "偷塞"进某 cell 的额外实例边框线。
- `shapeBuilder (private final ShapeBuilder)` — 本地形状构建器，供 PrimitiveNode.genShape 回调。
- `boxBuilders / pureBoxBuilders (private final ArrayList<List<VectorManhattanBox>>)` — 按层索引累积 Manhattan 盒（普通层 / pure layer 各一份）。
- `varContext (private VarContext)` — 当前渲染所用 VarContext（参数化 cell 时非空）。
- `curScale (private double)` — 当前缩放（文本定位用）。
- `clearFadeImages / clearCache (private boolean)` — 待处理的"清 greeked 图像 / 清缓存"标记。
- `updateStep (private long)` — 更新计数；VectorCell.validStep 与之相等才视为有效。
- `instanceGraphics (private EGraphics)` — 未展开实例边框所用的 EGraphics。
- `dummyWnd (private final EditWindow0)` — 匿名 EditWindow0，为 getDisplayableVariables 提供字体/缩放/上下文。
- `shapeByLayer (public static Comparator<VectorBase>)` — 按层渲染深度排序形状的共享比较器。
- 静态开关: `USE_CELL_RTREE = true`、`USE_ELECTRICAL = false`、`WIPE_PINS = true`、`DEBUG = false`。

### 方法

#### `public VectorCache(EDatabase database)`
- 行号: L1189
- 描述: 构造缓存实例并绑定给定数据库。

#### `public VectorCell findVectorCell(CellId cellId, Orientation orient)`
- 行号: L1194
- 描述: 查找（不存在则创建）指定 cell 在规范化朝向下的 VectorCell 缓存。

#### `public static Cell getCellFromId(CellId cellId)`
- 行号: L1214
- 描述: 由 CellId 找 Cell；先查缓存数据库，查不到再到当前 Library 找（支持变更任务中渲染）。

#### `public VectorCell drawCell(CellId cellId, Orientation prevTrans, VarContext context, double scale, boolean makeTopLevel)`
- 行号: L1226
- 描述: **`[重要]`** 获取 cell 的向量缓存，若 cell 参数化或缓存过期（validStep != updateStep）则重建；makeTopLevel 时同时构建 topOnlyShapes（cell 变量、内部可见节点、export）。

#### `public static VectorBase[] drawNode(NodeInst ni)`
- 行号: L1257
- 描述: **`[重要]`** 单独缓存一个 NodeInst 的图形（TechPalette 等用），返回按层排序的形状数组。

#### `public static VectorBase[] drawPolys(ImmutableArcInst a, Poly[] polys)`
- 行号: L1275
- 描述: 单独缓存一组多边形（弧的图形表示），返回按层排序的形状数组。

#### `public void addBoxToCell(ImmutableElectricObject origin, double lX, double lY, double hX, double hY, Layer layer, CellId cellId)`
- 行号: L1306
- 描述: 向指定 cell 的缓存插入一个额外矩形（后续重建时并入 organizedShapes）。

#### `public void addInstanceToCell(ImmutableElectricObject origin, double lX, double lY, double hX, double hY, CellId cellId)`
- 行号: L1326
- 描述: 向指定 cell 的缓存插入一个额外实例（用 4 条 VectorLine 画边框，线型 instanceGraphics）。

#### `public Set<CellId> updateChange(Set<CellId> topCells, SnapshotAnalyze sa)`
- 行号: L1567
- 描述: **`[重要]`** 数据库变更入口：先增量更新缓存，再收集"自身变化 + 展开子 cell 内容变化"的 cell，返回需要重绘的 CellId 集合。

#### `public void forceRedraw()`
- 行号: L1675
- 描述: 处理挂起的 clearCache/clearFadeImages 请求：清缓存、清 fade 图像，并同步各 cell 的边界变化。

#### `public synchronized void clearCache()`
- 行号: L1714
- 描述: 请求清空整个向量缓存（技术参数改变、一切需重算时调用），随后立即 forceRedraw。

#### `public synchronized void clearFadeImages()`
- 行号: L1724
- 描述: 请求清除所有 greeked 图像（可见层集合改变时调用）。

#### `VectorCellDef findCellGroup(CellId cellId)`（包私有）
- 行号: L617
- 描述: 按 cellIndex 查找（不存在则创建）cell 的 VectorCellDef 缓存组。

#### `private static boolean isCellParameterized(CellRevision cellRevision)`
- 行号: L1351
- 描述: 判断 cell 是否参数化：有参数、节点/弧上有 Java 代码变量，或含 bus pin export（其外观依赖父 cell）。

#### `private void updateVectorCache(SnapshotAnalyze sa)`
- 行号: L1389
- 描述: **`[重要]`** 增量更新缓存：删除已删 cell 的缓存，刷新变更 export/变量的缓存，对每个有效朝向先移除被删对象、再用 ShapeBuilder 补入新增对象（增量而非全量重建）。

#### `private boolean contentsChanged(CellId cellId, CellRevision cellRevision, Set<CellId> cellsThatChanged, Set<CellId> cellsThatChangedSize, Map<CellId,Boolean> contentsState, SnapshotAnalyze sa)`
- 行号: L1627
- 描述: 递归（带备忘录）判断 cell 内容是否变化：展开子 cell 自身变化，或未展开子 cell 尺寸变化，则顶层需重绘。

#### `private static long databaseToGrid(double lambdaValue)`
- 行号: L1729
- 描述: 数据库 lambda 坐标转网格坐标的薄封装。

#### `private void addBoxesFromBuilder(VectorCell vc, Technology tech, ArrayList<List<VectorManhattanBox>> boxBuilders, boolean pureArray)`
- 行号: L1734
- 描述: 把按层累积的 VectorManhattanBox 转成 VectorManhattan 加入 cell 的对应层形状列表。

#### `private void drawSubcell(NodeInst ni, FixpTransform trans, VectorCell vc)`
- 行号: L1789
- 描述: 缓存一个 cell 实例：创建 VectorSubCell 并链入 R-Tree（或列表），记录需显示的端口位，缓存实例上的显示变量文本。

#### `private void drawPrimitiveNode(NodeInst ni, FixpTransform trans, VectorCell vc)`
- 行号: L1833
- 描述: 缓存一个基本节点：设置 ShapeBuilder 的文本类型/pureLayer/hideOnLowLevel 后调用 pn.genShape 生成形状，并缓存节点显示变量。

#### `private void drawArc(ArcInst ai, FixpTransform trans, VectorCell vc)`
- 行号: L1854
- 描述: 缓存一条弧：设置 pureLayer 后调用 shapeBuilder.genShapeOfArc，并缓存弧上的显示变量文本。

#### `private void drawPolys(ImmutableElectricObject origin, Poly[] polys, FixpTransform trans, VectorCell vc, boolean hideOnLowLevel, int textType, boolean pureLayer)`
- 行号: L1871
- 描述: 变换并缓存一组多边形（逐条 renderPoly）。

#### `private void drawTextPolys(ImmutableElectricObject origin, Poly[] polys, FixpTransform trans, VectorCell vc, boolean hideOnLowLevel, int textType, boolean pureLayer, boolean tempOwnerName)`
- 行号: L1897
- 描述: 变换并缓存一组文本多边形，支持临时名（tempOwnerName）标记。

#### `private void renderPoly(ImmutableElectricObject origin, Poly poly, VectorCell vc, boolean hideOnLowLevel, int textType, boolean pureLayer, boolean tempOwnerName)`
- 行号: L1922
- 描述: **`[重要]`** 核心分发：按 Poly.Type 把多边形转成 VectorManhattan/VectorPolygon/VectorLine/VectorText/VectorCircle/VectorCircleArc/VectorCross 并加入形状列表（FILLED 矩形还走 boxBuilders 合并），同时维护 maxFeatureSize。

#### `private static void putBox(ImmutableElectricObject origin, int layerIndex, ArrayList<List<VectorManhattanBox>> boxBuilders, long lX, long lY, long hX, long hY)`
- 行号: L2101
- 描述: 把一个盒追加到指定层索引的构建器列表（列表按需扩展）。

#### `private VectorCell newDummyVectorCell()`
- 行号: L1292
- 描述: 创建无 VectorCellDef/朝向的 dummy VectorCell（TechPalette 绘制用）。

---

### 内部类 VectorBase（public static abstract）
- 行号: L140
- 描述: 所有可缓存显示对象的公共基类。
- 关键字段: `layer (Layer)` — 所属层；`graphicsOverride (EGraphics)` — 图形描述覆盖（可为 null）；`origin (ImmutableElectricObject)` — 来源数据库对象（增量更新时按它删除）。

#### `VectorBase(ImmutableElectricObject origin, Layer layer, EGraphics graphicsOverride)`（包私有构造）
- 行号: L146

#### `boolean isFilled()`（包私有）
- 行号: L156
- 描述: 是否填充图元，默认 false，由子类覆盖。

#### `public EGraphics getGraphics()` / `public Layer getLayer()`
- 行号: L158 / L160
- 描述: 纯 getter。

### 内部类 VectorManhattan（static，extends VectorBase）
- 行号: L166
- 描述: 缓存的 Manhattan 矩形（坐标数组每 4 个一组 lX,lY,hX,hY，可含多个盒）。
- 关键字段: `coords (private long[])`；`pureLayer (private boolean)`。

#### `VectorManhattan(...)`（3 个重载构造，double 坐标 / long 坐标 / 坐标数组）
- 行号: L172 / L178 / L183

#### `public long[] getCoords()` / `public boolean isPureLayer()`
- 行号: L189 / L191
- 描述: 纯 getter。

#### `boolean isFilled()`
- 行号: L194
- 描述: 恒返回 true（矩形是填充图形）。

#### `public String toString()`
- 行号: L196
- 描述: 输出层名与各盒坐标范围（调试用）。

### 内部类 VectorManhattanBox（static）
- 行号: L210
- 描述: 带来源对象的 Manhattan 盒，供 boxBuilders 按层累积。
- 关键字段: `origin`、`lX, lY, hX, hY`。

#### `public VectorManhattanBox(ImmutableElectricObject origin, long lX, long lY, long hX, long hY)`
- 行号: L215

### 内部类 VectorPolygon（static，extends VectorBase）
- 行号: L228
- 描述: 缓存的多边形（非 Manhattan），顶点存为网格坐标 EPoint 数组。
- 关键字段: `points (private EPoint[])`。

#### `VectorPolygon(ImmutableElectricObject origin, Point2D[] points, Layer layer, EGraphics graphicsOverride)`
- 行号: L232

#### `public EPoint[] getPoints()`
- 行号: L243

#### `boolean isFilled()`
- 行号: L246
- 描述: 恒返回 true。

### 内部类 VectorLine（static，extends VectorBase）
- 行号: L252
- 描述: 缓存的线段（含线型纹理 texture）。
- 关键字段: `fX, fY, tX, tY (long)`、`texture (int)`。

#### `VectorLine(...)`（long 坐标 / double 坐标两个构造）
- 行号: L257 / L267

#### `public long getFromX()` / `getFromY()` / `getToX()` / `getToY()` / `public int getTexture()`
- 行号: L277 / L279 / L281 / L283 / L285
- 描述: 纯 getter。

#### `public String toString()`
- 行号: L287

### 内部类 VectorCircle（static，extends VectorBase）
- 行号: L296
- 描述: 缓存的圆（轮廓 0 / 粗轮廓 1 / 实心盘 2，见 getNature）。
- 关键字段: `cX, cY, eX, eY (long)`、`nature (int)`。

#### `VectorCircle(ImmutableElectricObject origin, double cX, double cY, double eX, double eY, int nature, Layer layer, EGraphics graphicsOverride)`
- 行号: L301

#### `public long getCenterX()` / `getCenterY()` / `getEdgeX()` / `getEdgeY()`
- 行号: L311 / L313 / L315 / L317
- 描述: 纯 getter。

#### `public int getNature()`
- 行号: L326
- 描述: 圆类型：0 轮廓、1 粗轮廓、2 实心盘。

#### `boolean isFilled()`
- 行号: L329
- 描述: 仅 nature==2（实心盘）为 true。

### 内部类 VectorCircleArc（static，extends VectorBase）
- 行号: L339
- 描述: 缓存的圆弧（普通或加粗，bigArc 表示超过 180 度的大弧）。
- 关键字段: `cX, cY, eX1, eY1, eX2, eY2 (long)`、`thick, bigArc (boolean)`。

#### `VectorCircleArc(ImmutableElectricObject origin, double cX, double cY, double eX1, double eY1, double eX2, double eY2, boolean thick, boolean bigArc, Layer layer, EGraphics graphicsOverride)`
- 行号: L345

#### `public long getCenterX()` / `getCenterY()` / `getEdge1X()` / `getEdge1Y()` / `getEdge2X()` / `getEdge2Y()` / `public boolean isThick()` / `public boolean isBigArc()`
- 行号: L359 / L361 / L363 / L365 / L367 / L369 / L371 / L373
- 描述: 纯 getter。

### 内部类 VectorText（static，extends VectorBase）
- 行号: L379
- 描述: 缓存的文本（位置、样式、描述符、字符串、高度、类型、临时名标记）。
- 关键字段: `cX, cY, wid, hei (long)`；`style (Poly.Type)`；`descript (TextDescriptor)`；`str (String)`；`height (float)`；`textType (int)`；`basePort (PrimitivePort)`；`tempName (boolean)`。
- 类型常量: `TEXTTYPECELL=1` L381、`TEXTTYPEEXPORT=2` L382、`TEXTTYPENODE=3` L383、`TEXTTYPEARC=4` L384、`TEXTTYPEANNOTATION=5` L385、`TEXTTYPEINSTANCE=6` L386。

#### `VectorText(ImmutableElectricObject origin, Rectangle2D bounds, Poly.Type style, TextDescriptor descript, String str, int textType, Export e, Layer layer, boolean tempName)`
- 行号: L397
- 描述: 由 bounds 换算网格坐标；非绝对尺寸时取相对高度。

#### `public float getHeight()` / `public boolean isTempName()` / `public TextDescriptor getTextDescriptor()` / `public int getTextType()` / `public long getCX()` / `getCY()` / `getWid()` / `getHei()` / `public Poly.Type getStyle()` / `public PrimitivePort getBasePort()` / `public String getString()`
- 行号: L420 / L422 / L424 / L426 / L428 / L430 / L432 / L434 / L436 / L438 / L440
- 描述: 纯 getter。

#### `public String toString()`
- 行号: L442

### 内部类 VectorCross（static，extends VectorBase）
- 行号: L451
- 描述: 缓存的十字（小 cross / 大 BIGCROSS）。
- 关键字段: `x, y (long)`、`small (boolean)`。

#### `VectorCross(ImmutableElectricObject origin, double x, double y, boolean small, Layer layer, EGraphics graphicsOverride)`
- 行号: L456

#### `public long getCenterX()` / `getCenterY()` / `public boolean isSmall()`
- 行号: L464 / L466 / L468
- 描述: 纯 getter。

#### `public String toString()`
- 行号: L470

### 内部类 VectorSubCell（static，implements RTBounds）
- 行号: L479
- 描述: 缓存的子 cell 实例引用（供 R-Tree 空间索引）。
- 关键字段: `n (ImmutableNodeInst)`；`subCellId (CellId)`；`offsetX, offsetY (long)`；`bounds (AbstractFixpRectangle)`；`shownPorts (BitSet)`。

#### `VectorSubCell(NodeInst ni, Point2D offset)`
- 行号: L487
- 描述: 记录实例的不可变数据、子 cell ID、网格偏移与包围盒。

#### `public long getOffsetX()` / `getOffsetY()` / `public CellId getCellId()` / `public AbstractFixpRectangle getBounds()` / `public boolean isPortShown(int index)` / `public ImmutableNodeInst getNode()`
- 行号: L497 / L499 / L501 / L503 / L505 / L507
- 描述: 纯 getter。

### 内部类 VectorCellDef（包私有 class）
- 行号: L515
- 描述: 一个 Cell 的缓存组；因同一 cell 会按每种朝向各缓存一份，故持有 orientations 映射与共享的边界/export 信息。
- 关键字段: `cellId (CellId)`；`bounds (ERectangle)`；`cellArea, cellMinSize (float)`；`techId (TechId)`；`isParameterized (boolean)`；`orientations (Map<Orientation, VectorCell>)`；`exports (List<VectorCellExport>)`。

#### `VectorCellDef(CellId cellId)`（包私有构造）
- 行号: L526
- 描述: 初始化朝向映射，读边界与技术/参数化信息，clear 后收集 exports。

#### `public CellId getCellId()` / `public ERectangle getBounds()` / `public float getArea()` / `public float getMinimumSize()`
- 行号: L541 / L543 / L545 / L547
- 描述: 纯 getter。

#### `public List<VectorCellExport> getPortShapes()`
- 行号: L549
- 描述: 返回 export 形状列表（为 null 时重建）。

#### `public VectorCell getAnyCell()`
- 行号: L555
- 描述: 返回任一 validStep 与 updateStep 相符的朝向缓存，没有则 null。

#### `private boolean updateBounds(Snapshot snapshot)`
- 行号: L564
- 描述: 从快照刷新边界、面积与最小尺寸，并让各朝向缓存同步更新。

#### `private void updateExports()`
- 行号: L582
- 描述: 清空并重建 export 缓存列表（导出中心用于检测层次变化）。

#### `private void updateVariables()`
- 行号: L603
- 描述: 让各朝向缓存刷新 cell 级变量显示。

#### `private void clear()`
- 行号: L609
- 描述: 清空各朝向缓存并置 exports 为 null。

### 内部类 VectorCellExport（static）
- 行号: L634
- 描述: cell 上 export 的缓存（用于判断 export 是否变化及绘制端口文本）。
- 关键字段: `exportCtr (Poly.Point)`；`e (ImmutableExport)`；`style (Poly.Type)`；`descript (TextDescriptor)`；`height (float)`；`basePort (PrimitivePort)`。

#### `VectorCellExport(Export e)`
- 行号: L643
- 描述: 缓存 export 名文本的中心、样式与高度。

#### `public int getChronIndex()`
- 行号: L664
- 描述: 返回 export 的 chronIndex（端口显示位用）。

#### `public String getName(boolean shortName)`
- 行号: L666
- 描述: 取 export 名；shortName 时截断到第一个非字母数字字符。

#### `public Poly.Type getStyle()` / `public TextDescriptor getTextDescriptor()` / `public float getHeight()` / `public PrimitivePort getBasePort()`
- 行号: L682 / L684 / L686 / L688
- 描述: 纯 getter。

### 内部类 VectorCell（包私有 class）
- 行号: L694
- 描述: 一个 Cell 在一种朝向下的缓存：按层组织的形状、topOnlyShapes、子 cell R-Tree、greeked 图像与 fade 颜色。
- 关键字段: `vcd (final VectorCellDef)`；`orient (final Orientation)`；`outlinePoints (long[8])`；`lX, lY, hX, hY (long)`；`portCenters (int[])`；`validStep (long)`；`organizedShapes (Map<Layer,List<VectorBase>>)`；`topOnlyShapes (ArrayList<VectorBase>)`；`subCells (ArrayList<VectorSubCell>)`；`subCellTree (RTNode<VectorSubCell>)`；`hasFadeColor, fadeImage (boolean)`；`fadeColor (int)`；`maxFeatureSize (float)`；`fadeOffsetX/Y (int)`；`fadeImageColors (int[])`；`fadeImageWid/Hei (int)`。

#### `VectorCell(VectorCellDef vcd, Orientation orient)`（包私有构造）
- 行号: L721
- 描述: 初始化并创建 R-Tree、更新边界。

#### `private VectorCell()`
- 行号: L732
- 描述: 无参构造（TechPalette 用），vcd/orient 为 null，仅建空 topOnlyShapes。

#### `public VectorCellDef getCellDef()` / `public Orientation getOrientation()`
- 行号: L743 / L749
- 描述: 纯 getter。

#### `public RTNode<VectorSubCell> getSubCellTree()`
- 行号: L756
- 描述: 返回子 cell 的 R-Tree 头（未启用 R-Tree 时为 null）。

#### `public List<VectorSubCell> getSubCells()`
- 行号: L763
- 描述: 以列表返回子 cell 实例（使用 R-Tree 时本方法无效）。

#### `public void setFadeOffset(int oX, int oY)` / `getFadeOffsetX()` / `getFadeOffsetY()`
- 行号: L765 / L771 / L773
- 描述: greeked 图像调试摆放偏移的 getter/setter。

#### `public boolean isFadeImage()` / `public void setFadeImage(boolean f)` / `public void setFadeImageSize(int wid, int hei)` / `getFadeImageWidth()` / `getFadeImageHeight()` / `public void setFadeColors(int[] colors)` / `public int[] getFadeColors()`
- 行号: L775 / L777 / L779 / L785 / L787 / L789 / L791
- 描述: greeked 图像及其尺寸/颜色数组的 getter/setter。

#### `public void setFadeColor(int color)` / `public int getFadeColor()` / `public void setHasFadeColor(boolean h)` / `public boolean hasFadeColor()`
- 行号: L793 / L795 / L797 / L799
- 描述: fade 颜色缓存的 getter/setter。

#### `public long getLX()` / `getHX()` / `getLY()` / `getHY()`
- 行号: L805 / L811 / L817 / L823
- 描述: 缓存 cell 边界（网格坐标）getter。

#### `public long[] getOutlinePoints()`
- 行号: L829
- 描述: 返回 black box（未展开实例）轮廓的 4 顶点坐标。

#### `public float getMaxFeatureSize()`
- 行号: L831
- 描述: 返回缓存中最大特征尺寸（用于判断内容是否全 tiny）。

#### `public List<Layer> getKnownLayers()`
- 行号: L837
- 描述: 返回按渲染顺序（ByFunctionLevel 排序）排列的层列表。

#### `public List<VectorBase> getShapes(Layer layer)`
- 行号: L850
- 描述: 返回指定层上的形状列表（可能为 null）。

#### `private void buildCache(Cell cell)`
- 行号: L852
- 描述: **`[重要]`** 全量重建缓存：清空后依次缓存所有弧、基本节点、子 cell 实例，合并"偷塞"形状与 boxBuilders，最后刷新 validStep。

#### `private void addShape(VectorBase vb)`
- 行号: L917
- 描述: 把形状加入其层（null 层归入 glyphLay）的列表。

#### `private List<VectorBase> getShapeList(Layer layer)`
- 行号: L931
- 描述: 取（必要时创建）指定层的形状列表。

#### `private void updateBounds()`
- 行号: L942
- 描述: 按朝向旋转后的 cell 边界刷新 lX/lY/hX/hY 与 outlinePoints。

#### `private void clear()`
- 行号: L967
- 描述: 清空形状、子 cell（重建 R-Tree）与 fade 状态。

#### `private void addExport(Export e, FixpTransform trans)`
- 行号: L978
- 描述: 把 export 名文本与其显示变量加入 topOnlyShapes。

#### `private void clearExports()`
- 行号: L994
- 描述: 置 portCenters 为 null。

#### `private void updateVariables()`
- 行号: L1002
- 描述: 移除旧的 cell 级变量文本后重新缓存 cell 的显示变量。

#### `public int[] getPortCenters()`
- 行号: L1028
- 描述: 返回各 export 中心的屏幕网格坐标（每端口 2 个 int，惰性计算）。

#### `private void initPortCenters()`
- 行号: L1034
- 描述: 按朝向变换各 export 中心，填入 portCenters。

#### `public ArrayList<VectorBase> getTopOnlyShapes()`
- 行号: L1049
- 描述: 返回顶层形状（cell 变量、内部可见节点、export），为 null 时构建。

#### `private void buildTopOnlyShapes()`
- 行号: L1055
- 描述: 构建 topOnlyShapes：缓存 cell 变量、仅内部可见的节点、export 及其变量，并按层排序。

### 内部类 ShapeBuilder（private class，extends AbstractShapeBuilder）
- 行号: L1089
- 描述: 技术形状构建回调的适配器：把 PrimitiveNode.genShape/弧生成器产生的 Poly 转成 VectorCache 形状。
- 关键字段: `vc (VectorCell)`；`hideOnLowLevel (boolean)`；`textType (int)`；`pureLayer (boolean)`。

#### `public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp)`
- 行号: L1097
- 描述: OPENED* 样式转成折线，其余样式转 Poly 后交给 renderPoly。

#### `public void addTextPoly(int numPoints, Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor)`
- 行号: L1127
- 描述: 把带文本的多边形转 Poly 后交给 renderPoly。

#### `private void addLine(int numPoints, int lineType, Layer layer, EGraphics graphicsOverride)`
- 行号: L1140
- 描述: 把固定点折线转成逐段 VectorLine（hideOnLowLevel 时进 topOnlyShapes）。

#### `public void addBox(Layer layer)`
- 行号: L1157
- 描述: **`[重要]`** 把坐标换算成网格后，本技术层走 boxBuilders 累积（否则直接建 VectorManhattan），并更新 maxFeatureSize（substrate 层忽略）。

### 公共比较器 shapeByLayer
- 行号: L1753（compare 方法 L1758）
- 描述: 按"填充图形在后、contact 层优先、层功能级别"排序 VectorBase，供 topOnlyShapes 等使用。

---

## VectorDrawing
**文件**: `com/sun/electric/tool/user/redisplay/VectorDrawing.java`（1121 行）
**职责**: 通过缓存所有对象的向量坐标实现快速重绘。它把 VectorCache 中的网格坐标形状按窗口缩放/平移换算成屏幕坐标，分发到 PixelDrawing 绘制；负责层次递归渲染、屏幕裁剪、greeking（过小对象画色块或 greeked 图像）与渲染中止。
**继承/实现**: 直接继承 Object（类为包私有）。
**关键字段**:
- `offscreen (private PixelDrawing)` — 当前渲染目标。
- `scale, scale_ (private float)` — 窗口缩放（scale_ 为每网格单位的像素数）。
- `factorX, factorY (private float)`、`factorX_, factorY_ (private int)`、`scale_int (private int)` — 坐标换算用的平移与定点缩放因子。
- `fullInstantiate (private boolean)` — 是否展开到层次最底。
- `inPlaceNodePath (private List<NodeInst>)`、`inPlaceCurrent (private Cell)` — 就地编辑路径与当前 cell。
- `timer (private ElapseTimer)`、`takingLongTime (private boolean)` — 渲染计时与耗时提示。
- `stopRendering (private boolean)` — 中止标记。
- `szHalfWidth, szHalfHeight (private int)` — 窗口半宽/半高（像素）。
- `screenLX, screenHX, screenLY, screenHY (private int)` — 屏幕裁剪范围。
- `boxCount ... invisSubCellCount (private int)` — 各类形状渲染统计计数。
- `objectVisibleThreshold, objectGreekThreshold (private float)` — 可见性阈值 / greek 阈值（lambda）。
- `useCellGreekingImages (private boolean)` — 是否启用 greeked 图像。
- `maxTextSize (private float)` — 文本高度阈值（低于则跳过）。
- `maxCellSize (private float)` — 允许 greek 的最大 cell 面积。
- `topVD (private static VectorDrawing)` — 顶层渲染实例（greeked 图像调试摆放用）。
- `debugXP, debugYP (private static int)` — greeked 图像调试摆放位置。
- 常量: `TAKE_STATS = false` L68、`MAXGREEKSIZE = 25` L69（greeked 图像最大边长）、`SCALE_SH = 20` L70。

### 方法

#### `public VectorDrawing(boolean useCellGreekingImages)`
- 行号: L109
- 描述: 构造，记录是否使用 greeked 图像。

#### `public void render(PixelDrawing offscreen, double scale, Point2D offset, Cell cell, boolean fullInstantiate, List<NodeInst> inPlaceNodePath, Cell inPlaceCurrent, Rectangle screenLimit, VarContext context, double greekSizeLimit, double greekCellSizeLimit, LayerVisibility lv)`
- 行号: L123
- 描述: **`[重要]`** 绘制 cell 的主入口：检测高亮层、设置缩放/阈值/裁剪，缓存顶层 cell 后递归渲染并画 topOnlyShapes；处理中止、耗时提示与统计输出。

#### `public void render(PixelDrawing offscreen, double scale, Point2D offset, VectorCache.VectorBase[] shapes)`
- 行号: L220
- 描述: **`[重要]`** 绘制技术菜单（TechPalette）形状数组的主入口：设置缩放与文本颜色后直接 drawList。

#### `public void abortRendering()`
- 行号: L263
- 描述: 请求中止当前渲染（置 stopRendering，渲染循环会抛 AbortRenderingException）。

#### 内部类 `AbortRenderingException extends Exception`
- 行号: L255
- 描述: 渲染中止信号异常（空类）。

#### `private void render(VectorCache.VectorCell vc, long oX, long oY, VarContext context, int level, LayerVisibility lv)`
- 行号: L276
- 描述: **`[重要]`** 递归渲染缓存 cell：先画其他技术层、再画当前技术层；然后遍历子 cell（支持 R-Tree 裁剪），处理不可见/过小（greek 或画 tiny 盒）、展开递归、未展开画 black box 轮廓与实例名，最后画端口。

#### `private void drawList(long oX, long oY, List<VectorCache.VectorBase> shapes, int level, boolean forceVisible)`
- 行号: L456
- 描述: **`[重要]`** 绘制缓存形状列表（逆序，因 PixelDrawing 不覆盖不透明层）：按 VectorBase 子类分发——Manhattan 盒（含 tiny 退化、屏幕裁剪）、线、多边形（GenMath.clipPoly 裁剪）、十字、文本（可见性/阈值/export 显示级别）、圆/弧；greeked 层跳过 contact/well/substrate。

#### `private void drawPortList(VectorCache.VectorSubCell vsc, VectorCache.VectorCell subVC_, long oX, long oY, boolean expanded, boolean onPathDown)`
- 行号: L688
- 描述: 在实例上绘制端口：按 portDisplayLevel 画十字或文本（可短名），受 isPortShown 与文本阈值约束。

#### `private void gridToScreen(long dbX, long dbY, Point result)`
- 行号: L741
- 描述: 数据库网格坐标转屏幕坐标（浮点路径，四舍五入）。

#### `void screenToGrid(long scrX, long scrY, Point result)`（包私有）
- 行号: L759
- 描述: 屏幕坐标转数据库网格坐标。

#### `private void drawTinyBox(long lX, long hX, long lY, long hY, int col, VectorCache.VectorCell greekedCell)`
- 行号: L775
- 描述: **`[重要]`** 绘制过小对象的替代小盒：启用 greeked 图像时按面积加权采样图像缩放（与背景色接近的像素跳过）；否则用 fade 色逐点填充。

#### `private boolean isContentsTiny(Cell cell, VectorCache.VectorCell vc, Orientation trans, VarContext context)`
- 行号: L870
- 描述: 递归判断 cell 及展开子 cell 的所有特征是否均小于 greek 阈值（用于整体 greek 判定）。

#### `private void makeGreekedImage(VectorCache.VectorCell subVC, LayerVisibility lv)`
- 行号: L899
- 描述: **`[重要]`** 用独立的 PixelDrawing/VectorDrawing 按 ≤25 像素边长渲染 cell 缩略图，保存为 fade 颜色数组并标记 isFadeImage。

#### `private int getFadeColor(VectorCache.VectorCell vc, VarContext context, LayerVisibility lv)`
- 行号: L985
- 描述: **`[重要]`** 计算 cell 的 fade 色：按各可见层覆盖面积加权平均层颜色（无内容时取背景色），结果缓存在 vc。

#### `private void gatherContents(VectorCache.VectorCell vc, Map<Layer, MutableDouble> layerAreas, VarContext context)`
- 行号: L1036
- 描述: 递归统计 cell 及子 cell 各层的覆盖面积（矩形、多边形、圆；忽略 implant/substrate 层）。

#### `private VectorCache.VectorCell drawCell(Cell cell, Orientation prevTrans, VarContext context, boolean makeTopLevel)`
- 行号: L1106
- 描述: 委托 VectorCache.theCache 缓存 cell；首次超过 1 秒时显示忙碌光标并提示。

---

## AlphaBlender
**文件**: `com/sun/electric/tool/user/redisplay/AlphaBlender.java`（967 行）
**职责**: 为 LayerDrawing 执行 alpha 混合：把各层 1 位/像素位图与层颜色、透明度（预乘 alpha）混合成不透明缓冲中的 RGB 像素。通过把层分组成每组约 13 层的 AlphaBlendGroup 并预计算 2^len 种层组合的 RGBA 查色表实现高速合成。
**继承/实现**: 直接继承 Object（类为包私有）。
**关键字段**:
- `background (private Color)` — 合成底色。
- `opaqueData (private int[])` — 输出不透明缓冲。
- `SCALE_SH = 8`、`SCALE = 256` — 定点小数位（8 位精度）。
- `groups (private AlphaBlendGroup[])` — 层分组（每组约 13 层）。
- `layerBits (private int[][])` — 各层位图数组。
- `r0..r31, g0..g31, b0..b31 (private int)` — 32 个像素的 RGB 累计寄存器。

### 方法

#### `public AlphaBlender()`
- 行号: L37
- 描述: 空构造。

#### `void init(Color background, ArrayList<AbstractDrawing.LayerColor> colors, ArrayList<int[]> bits)`（包私有）
- 行号: L48
- 描述: **`[重要]`** 初始化：记录背景色与层位图，把层按每约 13 层切成 AlphaBlendGroup（第一个组负责填背景），各组预计算查色表。

#### `void composeLine(int inputOffset, int minX, int maxX, int[] opaqueData, int opaqueOffset)`（包私有）
- 行号: L136
- 描述: **`[重要]`** 合成一条扫描线 [minX,maxX]：头尾不满 32 位的部分用 composeBits 按掩码写，中间整字批量 unpack 后 storeRGB32。

#### `void composeBits(int inputOffset, int mask, int outputOffset)`（包私有）
- 行号: L164
- 描述: **`[重要]`** 对单个 32 位字：先让各组 unpackBytes，再按掩码逐位把 r/g/b 寄存器写入选中的像素。

#### `private int storeRGB32(int baseIndex)`
- 行号: L301
- 描述: 把 32 个像素的寄存器值连续写出，返回末尾索引。

#### `private void storeRGB(int baseIndex, int red, int green, int blue)`
- 行号: L362
- 描述: 写单个像素颜色；RGB 分量越界时调用 normalizeRgbDim 归一化。

#### `private int normalizeRgbDim(int red, int green, int blue)`
- 行号: L382
- 描述: **`[重要]`** 越界颜色归一化（dim 处理）：范围 ≤255 时整体平移回 [0,255]；否则以 max 为基准整体下移并裁剪，保持色调近似。

### 内部类 AlphaBlendGroup（private）
- 行号: L413
- 描述: 一组（≤13 个）层：预计算 2^len 种层组合下的预乘 RGBA 查色表。
- 关键字段: `redMap, greenMap, blueMap, inverseAlphaMap (int[])` — 4 张 2^len 项查色表；`bits (int[][])` — 本组各层位图。

#### `AlphaBlendGroup(int[][] bits, List<AbstractDrawing.LayerColor> cols, int offset, int len, boolean fillBackround)`
- 行号: L423
- 描述: 对每种层组合 k 按"后画层覆盖先画层"的预乘 alpha 公式算出 RGB（乘 SCALE*255）与 inverseAlpha（乘 SCALE）；fillBackround 组先以背景色打底。

#### `private void unpackBytes(int index)`
- 行号: L470
- 描述: **`[重要]`** 把本组各层某 int 字的 32 位"转置"成 32 个像素的组合索引，逐像素查表并累计 RGBA 到 r0..r31/g0..g31/b0..b31（逆 alpha 累乘）。

---

## ERaster
**文件**: `com/sun/electric/tool/user/redisplay/ERaster.java`（114 行）
**职责**: 单层离屏数据接口：定义填充（可带图案）与画线（恒实心）两大类操作，以及轮廓样式查询和位图拷贝。PixelDrawing 通过本接口对每层位图/不透明缓冲做绘制。
**继承/实现**: 接口，实现类见 TransparentRaster / OpaqueRaster / PatternedTransparentRaster / EmptyPatternedOutlinedTransparentRaster。

### 方法（接口声明）

#### `public void fillBox(int lX, int hX, int lY, int hY)`
- 行号: L40
- 描述: 填充矩形 [lX,hX]×[lY,hY]（两端含），填充可能带图案。

#### `public void fillHorLine(int y, int lX, int hX)`
- 行号: L50
- 描述: 填充水平扫描线（可能带图案）。

#### `public void fillVerLine(int x, int lY, int hY)`
- 行号: L60
- 描述: 填充垂直扫描线（可能带图案）。

#### `public void fillPoint(int x, int y)`
- 行号: L68
- 描述: 填充单个点（可能带图案）。

#### `public void drawHorLine(int y, int lX, int hX)`
- 行号: L78
- 描述: 画水平线，恒实心（无图案）。

#### `public void drawVerLine(int x, int lY, int hY)`
- 行号: L88
- 描述: 画垂直线，恒实心。

#### `public void drawPoint(int x, int y)`
- 行号: L95
- 描述: 画单个点。

#### `public EGraphics.Outline getOutline()`
- 行号: L101
- 描述: 返回本 ERaster 的轮廓样式，null 表示无轮廓。

#### `public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy)`
- 行号: L113
- 描述: 把源 TransparentRaster 中矩形区域的位拷贝到本 ERaster（dx/dy 为平移量）。

---

## TransparentRaster
**文件**: `com/sun/electric/tool/user/redisplay/TransparentRaster.java`（299 行）
**职责**: 实心透明层的 ERaster 实现：内部为每像素 1 位的位图（每个 int 存 32 像素，每行 intsPerRow 个 int）。是其他透明光栅类的基类，也提供像素级位拷贝（支持非整字对齐）。
**继承/实现**: `implements ERaster`。
**关键字段**:
- `layerBitMap (final int[])` — 位图数据。
- `intsPerRow (final int)` — 每行 int 个数。

### 方法

#### `TransparentRaster(int intsPerRow, int[] layerBitMap)`（包私有构造）
- 行号: L34
- 描述: 包装既有位图数据。

#### `public TransparentRaster(int numIntsPerRow, int height)`
- 行号: L39
- 描述: 按行宽与高度分配新的空位图。

#### `public void fillBox(int lX, int hX, int lY, int hY)`
- 行号: L45
- 描述: 置位矩形区域；单字分支循环上界为 `y < hY`（不含 hY 行），跨字分支为 `y <= hY`。

#### `public void eraseBox(int lX, int hX, int lY, int hY)`
- 行号: L70
- 描述: 清除矩形区域的位（fillBox 的反操作）。

#### `public void eraseAll()`
- 行号: L98
- 描述: 整张位图清零。

#### `public void fillHorLine(int y, int lX, int hX)`
- 行号: L105
- 描述: 置位水平扫描线。

#### `public void fillVerLine(int x, int lY, int hY)`
- 行号: L121
- 描述: 置位垂直扫描线（逐行置同一位）。

#### `public void fillPoint(int x, int y)`
- 行号: L131
- 描述: 置位单个点。

#### `public void drawHorLine(int y, int lX, int hX)` / `public void drawVerLine(int x, int lY, int hY)` / `public void drawPoint(int x, int y)`
- 行号: L136 / L152 / L162
- 描述: 画线/画点，透明位图无实心概念，实现与对应 fill 方法相同。

#### `public EGraphics.Outline getOutline()`
- 行号: L167
- 描述: 恒返回 null（透明层无轮廓）。

#### `public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy)`
- 行号: L172
- 描述: **`[重要]`** 按位拷贝并处理任意位偏移：分单字/双字/通用三类优化路径做移位合并后 OR 入目标位图。

---

## OpaqueRaster
**文件**: `com/sun/electric/tool/user/redisplay/OpaqueRaster.java`（116 行）
**职责**: 实心不透明层的 ERaster 实现：直接向不透明像素缓冲写入本层颜色值（ARGB int），用于无需混合的层。
**继承/实现**: `implements ERaster`（类为包私有）。
**关键字段**:
- `opaqueData (int[])` — 不透明像素缓冲。
- `width (int)` — 缓冲行宽（像素）。
- `col (int)` — 本层颜色（ARGB int）。

### 方法

#### `OpaqueRaster(int[] opaqueData, int width, int col)`（包私有构造）
- 行号: L35

#### `public void fillBox(int lX, int hX, int lY, int hY)`
- 行号: L42
- 描述: 逐像素写入颜色填充矩形。

#### `public void fillHorLine(int y, int lX, int hX)` / `public void fillVerLine(int x, int lY, int hY)` / `public void fillPoint(int x, int y)`
- 行号: L53 / L61 / L70
- 描述: 写入颜色填充扫描线/点。

#### `public void drawHorLine(int y, int lX, int hX)` / `public void drawVerLine(int x, int lY, int hY)` / `public void drawPoint(int x, int y)`
- 行号: L75 / L82 / L92
- 描述: 不透明层画线与填充等价，直接写颜色。

#### `public EGraphics.Outline getOutline()`
- 行号: L97
- 描述: 恒返回 null。

#### `public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy)`
- 行号: L102
- 描述: 逐位检查源位图，为 1 的位置在目标处写入本层颜色。

---

## PatternedTransparentRaster
**文件**: `com/sun/electric/tool/user/redisplay/PatternedTransparentRaster.java`（145 行）
**职责**: 带图案透明层的 ERaster 实现：在 TransparentRaster 位图基础上叠加 16 行图案掩码（按 y&15 取行），填充时仅置图案为 1 的位；携带轮廓样式。
**继承/实现**: `extends TransparentRaster`（类为包私有）。
**关键字段**:
- `pattern (final int[])` — 16 行图案掩码。
- `outline (final EGraphics.Outline)` — 轮廓样式。

### 方法

#### `PatternedTransparentRaster(int[] layerBitMap, int intsPerRow, int[] pattern, EGraphics.Outline outline)`（包私有构造）
- 行号: L35

#### `public void fillBox(int lX, int hX, int lY, int hY)`
- 行号: L42
- 描述: 按图案掩码置位矩形；单字分支循环上界为 `y < hY`，跨字分支为 `y <= hY`。

#### `public void fillHorLine(int y, int lX, int hX)`
- 行号: L74
- 描述: 按该行图案掩码置位水平扫描线（图案为空直接返回）。

#### `public void fillVerLine(int x, int lY, int hY)`
- 行号: L97
- 描述: 逐行检查图案位，置位垂直扫描线。

#### `public void fillPoint(int x, int y)`
- 行号: L109
- 描述: 仅当该点落在图案上时置位。

#### `public EGraphics.Outline getOutline()`
- 行号: L117
- 描述: 返回轮廓样式（可能非 null）。

#### `public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy)`
- 行号: L122
- 描述: 逐位拷贝并按目标行图案过滤（带边界断言）；该实现未继承基类的移位优化路径。

---

## EmptyPatternedOutlinedTransparentRaster
**文件**: `com/sun/electric/tool/user/redisplay/EmptyPatternedOutlinedTransparentRaster.java`（60 行）
**职责**: 仅轮廓、无填充图案的透明层 ERaster 实现：所有 fill* 操作为空（图案层不填充任何位），只提供轮廓样式；画线/画点操作继承自 TransparentRaster（仍为实心）。
**继承/实现**: `public class ... extends TransparentRaster`（public 类）。
**关键字段**:
- `outline (final EGraphics.Outline)` — 轮廓样式。

### 方法

#### `EmptyPatternedOutlinedTransparentRaster(int[] layerBitMap, int intsPerRow, EGraphics.Outline outline)`（包私有构造）
- 行号: L35

#### `public void fillBox(int lX, int hX, int lY, int hY)` / `public void fillHorLine(int y, int lX, int hX)` / `public void fillVerLine(int x, int lY, int hY)` / `public void fillPoint(int x, int y)`
- 行号: L41 / L45 / L49 / L53
- 描述: 全部为空实现——该层不填充任何像素位。

#### `public EGraphics.Outline getOutline()`
- 行号: L57
- 描述: 返回轮廓样式。
