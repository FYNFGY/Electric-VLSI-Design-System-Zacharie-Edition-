# 剪贴板与快捷键（Clipboard / KeyBindingManager）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user。本文件由 AI 生成。

## 模块概述

- `Clipboard` 负责电路剪贴板：把选中的版图对象（节点、弧、文本）复制到数据库中一个名为 "Clipboard!!" 的特殊 Cell 里，再粘贴回当前 Cell；剪贴、粘贴、复制（duplicate）以及交互式"跟随鼠标"粘贴均由它驱动，底层通过一组 `Job` 内部类完成数据库修改。
- `KeyBindingManager` 是全局快捷键管理器：实现 `KeyEventDispatcher` 并注册到 `KeyboardFocusManager`，拦截所有按键事件，通过 `inputMap`/`actionMap` 及支持两键（前缀键）序列的 `prefixedInputMapMaps` 把按键映射到动作；支持默认键与用户自定义键，并把用户改键结果持久化到 `Preferences`。
- `KeyBindings` 保存单个动作（`actionDesc`）的全部绑定信息：默认键列表、当前生效键列表、动作监听器列表、启用状态以及伪造事件源，并提供绑定集合与字符串之间的序列化/反序列化。
- `KeyStrokePair` 是"前缀键 + 主键"的不可变组合（主键必填、前缀键可空），通过静态缓存实现唯一实例（享元），使 `==` 等价于 `equals()`；它还负责 `KeyStroke` 与字符串的双向转换，含 Mac 特殊键处理。
- 协作关系：菜单/工具代码先用 `KeyBindingManager.addDefaultKeyBinding` + `addActionListener` 注册快捷键（例如把 Ctrl+C 等绑定到 `Clipboard.copy` 等动作）；用户改键后，`KeyBindingManager` 把 `KeyBindings.bindingsToString()` 的字符串写入 `Preferences`，下次启动时 `restoreSavedBindings` 读回并覆盖默认键。

---

## Clipboard
**文件**: `com/sun/electric/tool/user/Clipboard.java`（1863 行）
**职责**: 管理电路剪贴板（copy 和 paste）。被复制的几何对象与文本存放在数据库内置的剪贴板 Cell（"Clipboard!!"）中；提供复制、剪切、粘贴、复制（duplicate）四个顶层入口，以及粘贴时"跟随鼠标定位"的交互监听器 `PasteListener`。
**继承/实现**: 普通类，无继承（注释掉的 `implements ClipboardOwner` 已废弃）；内部含 6 个 `Job` 子类和一个实现 `WindowFrame.ElectricEventListener` 的内部类。
**关键字段**:
- `CLIPBOARD_LIBRAY_NAME (public static final String)` — 剪贴板库名常量 "Clipboard!!"（源码拼写如此）
- `CLIPBOARD_CELL_NAME (public static final String)` — 剪贴板 Cell 全名 "Clipboard!!;1{}"
- `CLIPBOARD_CELL_INDEX (public static final int)` — 剪贴板 Cell 的索引（0）
- `lastDupX, lastDupY (private static double)` — 上次粘贴/复制对象后的偏移量，用于下一次粘贴错开位置（初值 10, 10）

### 方法

#### `public String toString()`
- 行号: L104
- 描述: 返回剪贴板的可打印名称 "Clipboard"。

#### `public static void editClipboard()`
- 行号: L107
- 描述: 在当前的 EditWindow 中打开剪贴板 Cell（仅用于调试）。

#### `public static void copy()`
- 行号: L116
- 描述: **`[重要]`** 把选中的对象复制到剪贴板。先清空系统文本剪贴板；若焦点在 MessagesWindow 或 TextWindow 则委托它们复制文本；否则收集高亮几何对象与文本，若仅选中一个文本对象则同时把其文本放入系统剪贴板，再启动 `CopyObjects` Job 复制到剪贴板 Cell（含 "down in place" 编辑的旋转变换处理）。

#### `public static void cut()`
- 行号: L182
- 描述: **`[重要]`** 把选中对象复制到剪贴板后将其删除。先清空高亮，若仅选中一个文本对象则把文本放入系统剪贴板，再启动 `CutObjects` Job（复制 + 删除原件 + 清理变量/导出）。

#### `public static void paste()`
- 行号: L247
- 描述: **`[重要]`** 把剪贴板内容粘贴回当前 Cell。若剪贴板为空但系统文本剪贴板有文本，则启动 `PasteSpecialText` 给选中的文本对象改名；若粘贴目标是选中的单个节点/弧，则分别启动 `PasteNodeToNode`/`PasteArcToArc` 做"粘贴到对象上"；一般情况按用户偏好启动 `PasteListener`（移动后粘贴）、原地粘贴或偏移 `lastDupX/lastDupY` 的 `PasteObjects`。

#### `public static void duplicate()`
- 行号: L403
- 描述: **`[重要]`** 复制选中的对象（duplicate，不经过剪贴板 Cell）。若启用"移动后复制"则挂上 `PasteListener` 交互定位，否则启动 `DuplicateObjects` 在原 Cell 内偏移复制（原地复制时把 `lastDupX/lastDupY` 清零）。

#### `private static void copySelectedText(List<DisplayedText> highlightedText)`
- 行号: L435
- 描述: 辅助方法：仅当恰好选中一个文本对象（弧名/节点名/导出名或普通变量）时，把其文本内容放入系统剪贴板。

#### `public static void databaseChanged(DatabaseChangeEvent e)`
- 行号: L470
- 描述: 数据库变更回调，当前为空实现。

### 内部类 CopyObjects（L475，extends Job）
把高亮对象复制到剪贴板 Cell 的变更 Job。
#### `protected CopyObjects(Cell cell, List<Geometric>, List<DisplayedText>, EDimension, FixpTransform, Orientation)`
- 行号: L488
- 描述: 构造 "Copy" Job（CHANGE 类型、USER 优先级），记录参数、初始化 expansionState 与 nodesToExpand，并立即 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L502
- 描述: 先 `clear()` 清空剪贴板，再调用 `copyListToCell` 把对象复制进剪贴板 Cell，记录需要展开的节点。
#### `public void terminateOK()`
- 行号: L516
- 描述: 把复制过程中记录的 `nodesToExpand` 设为展开状态。

### 内部类 CutObjects（L524，extends Job）
复制并删除原件的变更 Job。
#### `protected CutObjects(Cell, List<Geometric>, List<DisplayedText>, EDimension, boolean, FixpTransform, Orientation)`
- 行号: L540
- 描述: 构造 "Cut" Job，记录参数并 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L556
- 描述: 清空剪贴板；用 `CircuitChangeJobs.cantEdit` 检查可编辑性；复制对象到剪贴板 Cell；调用 `eraseObjectsInList` 删除原件并收集重建对象；再删除文本列表对应的节点名/弧名/导出名/变量。
#### `public void terminateOK()`
- 行号: L640
- 描述: 展开记录的节点，清空高亮并仅高亮删除后被重建（如重建的弧/导出所在节点）的对象。

### 内部类 DuplicateObjects（L663，extends Job）
在原 Cell 内偏移复制对象的变更 Job。
#### `protected DuplicateObjects(Cell, List<Geometric>, List<DisplayedText>, EDimension)`
- 行号: L673
- 描述: 构造 "Duplicate" Job，记录参数并 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L688
- 描述: 调用 `copyListToCell` 以偏移 `(lastDupX, lastDupY)` 把对象复制回同一个 Cell，收集新对象列表。
#### `public void terminateOK()`
- 行号: L703
- 描述: 通过 `showCopiedObjects` 高亮新复制的对象，并展开记录的节点。

### 内部类 PasteArcToArc（L715，extends Job）
把源弧属性粘贴到目标弧上的变更 Job。
#### `protected PasteArcToArc(ArcInst dst, ArcInst src)`
- 行号: L719
- 描述: 构造 "Paste Arc to Arc" Job 并 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L727
- 描述: 检查目标弧所在 Cell 可编辑后，调用 `pasteArcToArc` 完成属性粘贴。
#### `public void terminateOK()`
- 行号: L739
- 描述: 高亮粘贴后的新弧。

### 内部类 PasteNodeToNode（L759，extends Job）
把源节点"粘贴"到目标节点上（目标被替换）的变更 Job。
#### `protected PasteNodeToNode(NodeInst dst, NodeInst src)`
- 行号: L763
- 描述: 构造 "Paste Node to Node" Job 并 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L771
- 描述: 检查可编辑性后，调用 `pasteNodeToNode` 用源节点内容替换目标节点。
#### `public void terminateOK()`
- 行号: L783
- 描述: 令新节点继承源节点的展开状态，并高亮新节点。

### 内部类 PasteObjects（L804，extends Job）
把对象列表粘贴进目标 Cell 的变更 Job（粘贴的核心 Job）。
#### `protected PasteObjects(Cell fromCell, Cell toCell, List<Geometric>, List<DisplayedText>, double dX, double dY, EDimension, boolean, boolean, boolean, boolean, FixpTransform, Orientation)`
- 行号: L817
- 描述: 构造 "Paste" Job；参数依次为源 Cell、目标 Cell、几何/文本列表、偏移 dX/dY、网格对齐、是否复制导出、是否递增最右下标、是否生成唯一弧名、是否原理图/版图视图转换、down-in-place 变换与方向，随后 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L839
- 描述: 检查目标 Cell 可编辑后，调用 `copyListToCell` 完成粘贴并收集新对象列表。
#### `public void terminateOK()`
- 行号: L857
- 描述: 高亮粘贴出的新对象，展开记录的节点，若展开了节点则重绘所有窗口内容。

### 内部类 PasteSpecialText（L871，extends Job）
把系统剪贴板文本写入选中文本对象（改名）的变更 Job。
#### `protected PasteSpecialText(List<DisplayedText> tList, String newT, Cell c)`
- 行号: L877
- 描述: 构造 "Paste Text" Job 并 `startJob()`。
#### `public boolean doIt() throws JobException`
- 行号: L886
- 描述: 检查可编辑性后，对每个选中文本按其类型（弧名/节点名/导出名）设置新文本。

### 方法（CHANGE JOB SUPPORT）

#### `public static CellId getClipCellId()`
- 行号: L918
- 描述: 返回剪贴板 Cell 的 `CellId`（通过 `IdManager` 按 `CLIPBOARD_CELL_INDEX` 获取）。

#### `private static Cell getClipCell()`
- 行号: L925
- 描述: 从数据库取回剪贴板 Cell 对象（可能为 null）。

#### `public static void clear()`
- 行号: L933
- 描述: 清空剪贴板 Cell 的全部内容：依次删除所有弧、导出、节点、参数与变量。

#### `public static NodeInst copyListToCell(Cell toCell, List<Geometric> geomList, List<DisplayedText> textList, List<Geometric> newGeomList, List<DisplayedText> newTextList, Point2D delta, boolean copyExports, boolean fromRight, boolean uniqueArcs, boolean convertSchLay, EDimension alignment, FixpTransform inPlace, Orientation inPlaceOrient, ExpansionState expansionState, List<NodeInst> nodesToExpand, EditingPreferences ep)`
- 行号: L990
- 描述: **`[重要]`** 复制引擎核心：把 `geomList`/`textList` 复制到 `toCell`（复制/粘贴/duplicate 共用）。自动补全弧的端点节点，检查实例化递归，按名字排序后创建新节点/新弧（支持视图转换 `convertSchLay`、down-in-place 变换、唯一命名、网格对齐），可选复制导出端口，并复制 Cell 级变量。返回最后创建的 `NodeInst`（出错返回 null）。

#### `private static Cell findAlternate(Cell cell, Cell destination)`
- 行号: L1244
- 描述: 视图转换辅助：目标为 schematic 时找 icon 视图（反之亦然），通过 `otherView` 返回 `cell` 的替代视图 Cell，找不到则返回原 Cell。

#### `private static void showCopiedObjects(List<Geometric> newGeomList, List<DisplayedText> newTextList)`
- 行号: L1256
- 描述: 在当前窗口高亮刚复制出来的新对象与文本（对"不可见引脚上的文本"做特殊处理，仅高亮其文本）。

#### `private static NodeInst pasteNodeToNode(NodeInst destNode, NodeInst srcNode, EditingPreferences ep)`
- 行号: L1299
- 描述: 把源节点"粘贴"到目标节点：用 `CircuitChangeJobs.replaceNodeInst` 替换目标节点原型，对齐尺寸、删除源上没有的参数/变量、复制变量与状态位，返回新目标节点。

#### `private static ArcInst pasteArcToArc(ArcInst destArc, ArcInst srcArc, EditingPreferences ep)`
- 行号: L1368
- 描述: 把源弧属性粘贴到目标弧：必要时替换弧类型，统一线宽，删除源上没有的变量、补齐源上有的变量，复制属性，返回目标弧。

#### `private static Rectangle2D getPasteBounds(List<Geometric> geomList, List<DisplayedText> textList, EditWindow wnd)`
- 行号: L1418
- 描述: 计算待粘贴对象集合（含文本多边形、节点锚点、弧多边形）在数据库坐标下的包围矩形，用于交互式粘贴的锚点与方向键移动步长。使用前通常需要网格对齐。

### 内部类 PasteListener（L1485，implements WindowFrame.ElectricEventListener）
交互式粘贴监听器：粘贴/复制后先高亮"幽灵"对象跟随鼠标，按下鼠标左键才真正落位。
#### `private PasteListener(Cell fromCell, EditWindow wnd, List<Geometric>, List<DisplayedText>, WindowFrame.ElectricEventListener currentListener, FixpTransform, Orientation, boolean dup, boolean convertSchLay)`
- 行号: L1507
- 描述: 构造监听器：记录原监听器（结束后恢复）、计算 pasteBounds、初始化右键弹出菜单；复制（dup=true）时把边界框中心移到当前鼠标位置；压入高亮栈并首次显示对象。
#### `private Point2D getDelta(Point2D mouseDB, boolean orthogonal)`
- 行号: L1544
- 描述: 根据鼠标位置（数据库坐标）计算网格对齐的位移增量；`orthogonal` 为 true（按住 Ctrl）时按"对象范围内取单轴、否则取位移较大轴"的规则把移动限制为水平或垂直。
#### `private void showList(Point2D delta)`
- 行号: L1599
- 描述: 按增量在窗口里绘制待粘贴对象的轮廓高亮（节点外形/弧多边形/文本位置十字线），并显示位移数值；对象被移出屏幕时绘制指向它们的箭头。
#### `private void showPoints(Point2D [] points, double oX, double oY, Cell cell, Highlighter highlighter)`
- 行号: L1699
- 描述: 把多边形顶点序列按偏移 (oX,oY) 画成闭合折线高亮。
#### `public void mousePressed(MouseEvent e)`
- 行号: L1713
- 描述: 右键（meta down）按下时显示弹出菜单（移动/中止）。
#### `public void mouseDragged(MouseEvent evt)`
- 行号: L1721
- 描述: 拖拽委托给 `mouseMoved` 处理。
#### `public void mouseReleased(MouseEvent evt)`
- 行号: L1726
- 描述: 右键释放直接返回；左键释放时按最终位置（Ctrl 时正交）计算增量，恢复原监听器、弹出高亮栈，并启动 `PasteObjects` 把对象真正粘贴到该处。
#### `public void mouseMoved(MouseEvent evt)`
- 行号: L1746
- 描述: 记录鼠标位置（`lastMouseDB`），刷新高亮位置并重绘窗口。
#### `public void mouseClicked / mouseEntered / mouseExited / mouseWheelMoved`
- 行号: L1757-L1760
- 描述: 接口空实现，无操作。
#### `public void keyPressed(KeyEvent evt)`
- 行号: L1761
- 描述: ESC 中止粘贴；方向键按 pasteBounds 半宽/半高步进移动待粘贴对象。
#### `public void keyReleased / keyTyped`
- 行号: L1780-L1781
- 描述: 接口空实现，无操作。
#### `public void databaseChanged(DatabaseChangeEvent e)`
- 行号: L1782
- 描述: 数据库变化时从待粘贴列表中移除已失效（未链接）的对象与文本。
#### `private void abort()`
- 行号: L1795
- 描述: 中止粘贴：清空高亮、恢复原监听器并重绘。
#### `private void initPopup()`
- 行号: L1802
- 描述: 构建右键弹出菜单："Move objects left/right/up/down"（加速键为方向键）与 "Abort"（加速键 ESC）。
#### `private void moveObjectsLeft / moveObjectsRight / moveObjectsUp / moveObjectsDown`
- 行号: L1841 / L1846 / L1851 / L1856
- 描述: 把累计偏移 `translateX/translateY` 按 pasteBounds 半宽/半高沿相应方向移动，并刷新高亮显示。

---

## KeyBindingManager
**文件**: `com/sun/electric/tool/user/KeyBindingManager.java`（1034 行）
**职责**: 管理快捷键绑定及其关联动作。作为 `KeyEventDispatcher` 注册到全局 `KeyboardFocusManager` 以拦截所有按键事件；维护单键 `inputMap`、动作表 `actionMap` 以及支持两键序列的前缀键映射 `prefixedInputMapMaps`，并提供用户改键与 `Preferences` 持久化。
**继承/实现**: `implements KeyEventDispatcher`；含内部类 `KeyBindingColumn`、`KeyBindingColumnSort`、`PrefixAction`、`KeyMaps`、`MyAbstractAction`。
**关键字段**:
- `inputMap (private Map<KeyStroke,Set<String>>)` — 单键绑定表：KeyStroke -> 动作描述字符串集合
- `actionMap (private Map<String,Object>)` — 动作表：动作描述 -> `KeyBindings`（或 `PrefixAction`）
- `lastPrefix (private KeyStroke)` — 最近按下的前缀键（实现两键序列的状态）
- `prefixedInputMapMaps (private Map<KeyStroke,Map<KeyStroke,Set<String>>>)` — 前缀键 -> 子绑定表的映射
- `prefixAction (private PrefixAction)` — 前缀键命中时执行的内部动作
- `prefs (private Preferences)` / `prefPrefix (private String)` — 用户改键的持久化目标及键名前缀
- `allManagers (private static List<KeyBindingManager>)` — 所有实例的全局列表（冲突检测遍历用）
- `debugPrefs / DEBUG (private static final boolean)` — 调试开关（均 false）
- `initialized (private boolean)` — 是否已做过一次首启恢复

### 方法

#### `public KeyBindingManager(String prefPrefix, Preferences prefs)`
- 行号: L115
- 描述: 构造管理器：初始化三个映射表，把 `PrefixAction` 放进 actionMap，向 `KeyboardFocusManager` 注册为 KeyEventDispatcher，并加入 `allManagers` 全局列表。

#### `public boolean dispatchKeyEvent(KeyEvent e)`
- 行号: L136
- 描述: `KeyEventDispatcher` 接口实现，直接委托给 `processKeyEvent`。

#### `public void finished()`
- 行号: L145
- 描述: 销毁时调用：从 `allManagers` 中移除自身，解除静态引用以便回收内存。

### 内部类 KeyBindingColumn（L153）
`printKeyBindings` 打印表格时的一个"列"。
#### `KeyBindingColumn(String n)` / `toString()` / `getHeader()` / `getColumn(Object)` / `addHit(Set<String>)` / `equals(Object)`
- 行号: L159 / L164 / L166 / L174 / L191 / L199
- 描述: 构造列名；toString 返回列名；getHeader/getColumn 按该列最大宽度补齐空格输出表头与单元格；addHit 统计该列命中次数并更新最大宽度；equals 按列名字符串比较（供 `indexOf` 查找）。

### 内部类 KeyBindingColumnSort（L207，implements Comparator<KeyBindingColumn>）
#### `public int compare(KeyBindingColumn s1, KeyBindingColumn s2)`
- 行号: L209
- 描述: 按命中次数从多到少排序（用于把最常用修饰键列排前面）。

#### `public void printKeyBindings()`
- 行号: L221
- 描述: 把现有快捷键以"按键 x 修饰键"表格形式打印到标准输出（帮助用途）：按键名作行、修饰键（KeyEvent.getModifiersExText）作列，按键名与修饰键均排序。

### 内部类 PrefixAction（L288，extends AbstractAction）
前缀键命中时执行的动作：把该键注册为当前前缀，从而支持两键序列。
#### `public static final String actionDesc`（L290）
- 描述: 该动作在 actionMap 中的固定描述字符串 "KeyBindingManager prefix action"。
#### `public PrefixAction(KeyBindingManager manager)`（L293）
- 描述: 构造并记录所属管理器。
#### `public void actionPerformed(ActionEvent e)`（L298）
- 描述: 从事件取出 `KeyStroke`，调用 `manager.setPrefixKey(stroke)` 记录前缀键。

#### `private synchronized void setPrefixKey(KeyStroke prefix)`
- 行号: L311
- 描述: 记录最近按下的前缀键（由 PrefixAction 回调）。

#### `public static boolean validKeyEvent(KeyEvent e)`
- 行号: L322
- 描述: 判断该按键事件是否值得处理：方向键始终有效；仅处理 KEY_PRESSED；忽略单独的修饰键（CTRL/SHIFT/ALT/META）按下。

#### `public synchronized boolean processKeyEvent(KeyEvent e)`
- 行号: L345
- 描述: **`[重要]`** 按键处理主入口。步骤：有效性检查；文本框（JTextComponent）里无 Ctrl 的键放行、有 Ctrl 的 A/C/X/V 直接执行全选/复制/剪切/粘贴；沿组件树向上判断来源（EditWindow/TopLevel/WaveformWindow.OnePanel 接受全部按键，无模式对话框/文本面板到此为止，模态对话框直接放行）；若当前有打开的弹出菜单则清前缀放行；移除 Shift 修饰符以便 `'<'` 等键正确匹配；按 `lastPrefix` 选择普通或前缀子映射，查 `actionMap` 执行动作（前缀动作只执行一次并保留前缀），未命中时检查该键本身是否为新前缀；命中则 consume 事件返回 true，否则返回 false。

#### `public static List<KeyBindings> getConflictsAllManagers(KeyStrokePair pair)`
- 行号: L499
- 描述: 遍历 `allManagers` 汇总所有管理器上与 `pair` 冲突的绑定列表。

#### `public synchronized void addDefaultKeyBinding(String actionDesc, KeyStrokePair pair)`
- 行号: L519
- 描述: 注册默认快捷键：把 `pair` 加入 `actionDesc` 对应 `KeyBindings` 的默认绑定列表（已存在用户绑定时不覆盖生效绑定）。

#### `public synchronized void addUserKeyBinding(String actionDesc, KeyStrokePair pair)`
- 行号: L541
- 描述: **`[重要]`** 注册用户自定义快捷键：调用 `addKeyBinding` 使其立即生效，标记为使用用户键，并把该动作的全部绑定写入 Preferences。

#### `public synchronized void addActionListener(String actionDesc, ActionListener action)`
- 行号: L556
- 描述: 为 `actionDesc` 动作添加一个动作监听器（不存在时先创建 `KeyBindings` 对象）。

#### `public synchronized void removeKeyBinding(String actionDesc, KeyStrokePair k)`
- 行号: L572
- 描述: 移除一条生效绑定：从前缀映射或主映射中删除该动作描述（必要时清理前缀动作占位），从 `KeyBindings` 中删除该键对，标记为使用用户键并写回 Preferences。

#### `public synchronized void resetKeyBindings(String actionDesc)`
- 行号: L608
- 描述: 把 `actionDesc` 恢复为默认键：逐个移除当前全部绑定，删除 Preferences 中的用户键存储，重新加入默认键对并标记 `usingDefaultKeys = true`。

#### `public synchronized KeyBindings getKeyBindings(String actionDesc)`
- 行号: L636
- 描述: 返回 `actionDesc` 对应的 `KeyBindings` 对象（不存在返回 null）。

### 内部类 KeyMaps（L643，public static）
把内部映射表转换为真正的 Swing `InputMap`/`ActionMap` 对象（供把绑定挂到具体组件上）。
#### `KeyMaps(KeyBindingManager kbm, Map<KeyStroke,Set<String>> inputMap, Map<String,Object> actionMap)`
- 行号: L648
- 描述: 遍历 inputMap，为每个按键取第一个动作名填入 InputMap，并在 ActionMap 中放入包装动作 `MyAbstractAction`。
#### `public InputMap getInputMap() / public ActionMap getActionMap()`
- 行号: L664 / L666
- 描述: 纯 getter，返回转换出的 InputMap / ActionMap。

### 内部类 MyAbstractAction（L669，private static，extends AbstractAction）
把 InputMap 命中的动作名转发给 KeyBindingManager 的包装动作。
#### `MyAbstractAction(String actionName, KeyBindingManager kbm)`（L674）
- 描述: 构造并记录动作名与管理器。
#### `public void actionPerformed(ActionEvent event)`（L680）
- 描述: 按动作名取 `KeyBindings` 并调用其 `actionPerformed`。

#### `public KeyMaps getKeyMaps()`
- 行号: L691
- 描述: 用当前 inputMap/actionMap 生成一个 `KeyMaps` 对象返回。

#### `public synchronized void setEventSource(String actionDesc, Object source)`
- 行号: L703
- 描述: 设置 `actionDesc` 动作的"伪造事件源"（供对事件来源敏感的动作使用），转发给对应 `KeyBindings.setEventSource`。

#### `public synchronized List<KeyBindings> getConflictingKeyBindings(KeyStrokePair pair)`
- 行号: L742
- 描述: **`[重要]`** 查找与 `pair` 冲突的绑定：规则为"现有单键 == 新前缀键"、"现有单键 == 新主键（新绑定无前缀时）"或"现有前缀+主键组合完全相同"。返回的是新建的、只含冲突键对的 `KeyBindings` 列表（空列表表示无冲突）。

#### `public synchronized void setEnabled(String actionDesc, boolean b)`
- 行号: L834
- 描述: 设置 `actionDesc` 动作的启用状态（前缀动作除外），b=false 时禁用该动作触发的所有事件。

#### `public synchronized boolean getEnabled(String actionDesc)`
- 行号: L847
- 描述: 返回 `actionDesc` 动作是否启用（动作不存在或为前缀动作时返回 false）。

#### `public synchronized void deleteEmptyBindings()`
- 行号: L859
- 描述: 清理没有挂任何动作监听器的"死"绑定：打印警告并从 Preferences 中删除对应存储。

#### `private synchronized KeyBindings addKeyBinding(String actionDesc, KeyStrokePair pair)`
- 行号: L884
- 描述: 私有核心：把 `pair` 作为生效绑定加入——若含前缀键则在主 inputMap 登记 PrefixAction 占位并在 `prefixedInputMapMaps` 建立子映射；把 actionDesc 放入对应键的集合；最后向 `KeyBindings` 对象登记键对。存在冲突时打印警告。

#### `private synchronized void setBindingsToPrefs(String actionDesc)`
- 行号: L941
- 描述: 把 `actionDesc` 的当前全部绑定（`bindingsToString()` 结果）写入 Preferences；键名过长时截取尾部以符合 `Preferences.MAX_KEY_LENGTH`。

#### `private synchronized List<KeyStrokePair> getBindingsFromPrefs(String actionDesc)`
- 行号: L963
- 描述: 从 Preferences 读回 `actionDesc` 的用户绑定字符串并解析为 `KeyStrokePair` 列表（无存储返回 null）。

#### `public synchronized void restoreSavedBindings(boolean initialCall)`
- 行号: L989
- 描述: **`[重要]`** 从 Preferences 恢复用户保存的绑定（通常在菜单创建后调用）：`initialCall` 且已初始化过则跳过；遍历 actionMap 中每个动作，先清空生效键，Preferences 中有存储则用用户键、否则回落到默认键，并相应设置 `usingDefaultKeys` 标志。

#### `private synchronized void removeBindingsFromPrefs(String actionDesc)`
- 行号: L1027
- 描述: 删除 Preferences 中 `actionDesc` 的绑定存储（键为 `prefPrefix+actionDesc`）。

---

## KeyBindings
**文件**: `com/sun/electric/tool/user/ui/KeyBindings.java`（219 行）
**职责**: 保存一个动作（`actionDesc`）的快捷键信息：0 个或多个程序设定的默认键、0 个或多个用户自定义键（由 KeyBindingManager 存为 Preferences），以及动作发生时被激活的动作监听器列表；动作可被禁用/启用。
**继承/实现**: `implements ActionListener`
**关键字段**:
- `actionDesc (private String)` — 动作描述（标识字符串，向用户展示用）
- `keyStrokePairs (private List<KeyStrokePair>)` — 当前生效的键对列表
- `defaultKeyStrokePairs (private List<KeyStrokePair>)` — 程序设定的默认键对列表
- `actionListeners (private List<ActionListener>)` — 动作触发时要通知的监听器
- `eventSource (private Object)` — 伪造的事件源（可空）
- `usingDefaultKeys (private boolean)` — 是否正在使用默认键（供 KeyBindingManager 跟踪）
- `enabled (private boolean)` — 绑定是否启用
- `sep (private static final String)` — 序列化分隔符 `"; "`

### 方法

#### `public KeyBindings(String actionDesc)`
- 行号: L57
- 描述: 构造：初始化三个列表，默认使用默认键且处于启用状态。

#### `public void addKeyBinding(KeyStroke prefixStroke, KeyStroke stroke)`
- 行号: L74
- 描述: 添加一条单键或两键（前缀+主键）生效绑定。

#### `public void addKeyBinding(KeyStrokePair k)`
- 行号: L83
- 描述: 添加一个键对到生效绑定列表。

#### `public void addKeyBindings(String str)`
- 行号: L92
- 描述: 批量添加绑定：按分隔符 `sep` 切分字符串，逐段解析为 `KeyStrokePair` 并加入列表（解析失败跳过）。

#### `public void removeKeyBinding(KeyStrokePair k)`
- 行号: L107
- 描述: 从生效绑定中移除一个键对。

#### `public void addDefaultKeyBinding(KeyStroke prefixStroke, KeyStroke stroke)`
- 行号: L116
- 描述: 添加一条单键或两键默认绑定。

#### `public void addDefaultKeyBinding(KeyStrokePair k)`
- 行号: L125
- 描述: 添加一个键对到默认绑定列表。

#### `public void clearKeyBindings()`
- 行号: L132
- 描述: 清空全部生效绑定。

#### `public void addActionListener(ActionListener a)`
- 行号: L142
- 描述: 注册一个动作监听器（动作触发时被调用）。

#### `public void removeActionListener(ActionListener a)`
- 行号: L147
- 描述: 移除一个动作监听器。

#### `public void actionPerformed(ActionEvent e)`
- 行号: L155
- 描述: `ActionListener` 实现：禁用时直接返回；设置了 `eventSource` 则伪造事件源，然后依次调用所有已注册监听器的 `actionPerformed`。

#### `public String bindingsToString()`
- 行号: L169
- 描述: 把当前生效键对序列化为字符串（键对间以 `sep` 分隔），供写入 Preferences。

#### `public String describe()`
- 行号: L179
- 描述: 返回人类可读描述："KeyBindings for '<actionDesc>': [ ... ]"。

#### `public String getActionDesc() / public Iterator<ActionListener> getActionListeners() / public Iterator<KeyStrokePair> getKeyStrokePairs() / public Iterator<KeyStrokePair> getDefaultKeyStrokePairs()`
- 行号: L184 / L187 / L190 / L193
- 描述: 纯 getter：依次返回动作描述、动作监听器迭代器、生效键对迭代器、默认键对迭代器。

#### `public void setEventSource(Object source)`
- 行号: L203
- 描述: 设置伪造事件源：动作激活时传给监听器的 ActionEvent 将以此为 getSource() 返回值。

#### `public void setUsingDefaultKeys(boolean b) / public boolean getUsingDefaultKeys()`
- 行号: L212 / L214
- 描述: setter/getter：设置/读取"是否使用默认键"标志（由 KeyBindingManager 维护）。

#### `public void setEnabled(boolean b) / public boolean getEnabled()`
- 行号: L216 / L218
- 描述: setter/getter：设置/读取绑定启用状态。

---

## KeyStrokePair
**文件**: `com/sun/electric/tool/user/ui/KeyStrokePair.java`（282 行）
**职责**: 表示一对按键：可选的前缀键 `prefixStroke` + 必需的主键 `stroke`。通过静态缓存保证同一对按键只有一个实例（享元），因此 `==` 等价于 `equals()`；同时提供 `KeyStroke` 与字符串的双向转换（含 Mac 平台特殊键处理），是快捷键序列化的基础。
**继承/实现**: 普通类，无继承、未实现接口（未覆盖 `equals()`，依赖缓存保证实例唯一）。
**关键字段**:
- `prefixStroke (private KeyStroke)` — 前缀键（可为 null）
- `stroke (private KeyStroke)` — 主键（必填）
- `cache (private static HashMap<KeyStrokePair,KeyStrokePair>)` — 已创建键对的缓存（按 hashCode 查找）
- `sep (private static final String)` — toString 分隔符 `", "`
- `specialKeyStrokes (private static int[])` — 特殊键码表（Insert/Home/PageUp/DELETE/标点/方向键等），用于 `KeyStroke.getKeyStroke(String)` 解析失败时的回退匹配

### 方法

#### `private KeyStrokePair()`
- 行号: L82
- 描述: 私有构造，仅通过工厂方法创建。

#### `public static KeyStrokePair getKeyStrokePair(KeyStroke prefixStroke, KeyStroke stroke)`
- 行号: L92
- 描述: 工厂方法：返回该键对对应的唯一缓存实例（同一键对两次调用得到同一对象）。

#### `public static KeyStrokePair getKeyStrokePair(String keyString)`
- 行号: L103
- 描述: 从字符串解析键对：按分隔符切分，1 段为单键、2 段为"前缀, 主键"，其余情况返回 null；每段经 `stringToKeyStroke` 转换。

#### `public String toString()`
- 行号: L129
- 描述: 序列化为字符串：无主键返回 ""；无前缀键返回主键字符串；否则"前缀键 + `sep` + 主键"。

#### `public static String getStringFromKeyStroke(KeyStroke key)`
- 行号: L135
- 描述: 取按键的文本标识：键码未定义（如 `'>'`）时用按键字符，否则用 `KeyEvent.getKeyText`。

#### `public static String keyStrokeToString(KeyStroke key)`
- 行号: L151
- 描述: 把 KeyStroke 转成可被 `KeyStroke.getKeyStroke(String)` 解析的字符串：修饰键文本（小写、'+' 替换为空格）+ 按键标识。

#### `public static KeyStroke stringToKeyStroke(String str)`
- 行号: L164
- 描述: 字符串转 KeyStroke：先统一 "NumPad-" 为 "NUMPAD"；对 Mac 的特殊字符（⌃⌘⇧ 等）做两次 hack 兼容（Leopard/Tiger）并补回修饰键掩码；标准解析失败时按 `specialKeyStrokes` 表逐项比对（含 Shift/Ctrl/Meta/Alt 组合）回退。

#### `public KeyStroke getPrefixStroke() / public KeyStroke getStroke()`
- 行号: L250 / L252
- 描述: 纯 getter：返回前缀键 / 主键。

#### `public int hashCode()`
- 行号: L260
- 描述: 由前缀键与主键的 hashCode 组合出近似唯一的哈希值 `(prefixCode + 1) * strokeCode`（供缓存查找）。

#### `private static KeyStrokePair getCachedKeyStrokePair(KeyStroke prefixStroke, KeyStroke stroke)`
- 行号: L267
- 描述: 缓存查找：前缀与主键都为空返回 null；否则构造临时对象按 hashCode 查 `cache`，命中则返回已有实例，未命中则存入缓存。
