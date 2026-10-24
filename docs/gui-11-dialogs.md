# 对话框体系（EDialog 及全部对话框）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.dialogs。本文件由 AI 生成。

## 模块概述

- `com.sun.electric.tool.user.dialogs` 主目录存放"操作对话框"：由菜单/工具栏命令调起、执行某项具体操作的窗口（新建单元、阵列、搜索替换、属性编辑、生成器等），共 60 个 .java 文件；其子目录 `options` 存放"选项对话框"：Preferences（偏好设置）窗口中的选项卡，共 57 个 .java 文件。
- 几乎所有操作对话框继承 `EDialog`（模态基类，JDialog 子类）或 `EModelessDialog`（无模态基类，JFrame 子类）；少数是普通类（OpenFile、Progress、SpecialProperties）或可复用 Swing 面板（AttributesTable、TextInfoPanel、TextAttributesPanel、ColorPatternPanel、DesignRulesPanel）。
- 对话框由菜单命令调起：菜单代码（如 `com.sun.electric.tool.user.menus.EditMenu`、`CellMenu`、`ToolMenu`）中调用各对话框类的静态入口方法（`showArrayDialog()`、`findTextDialog()`、`generalCellListsCommand()` 等），方法内部 `new Xxx(TopLevel.getCurrentJFrame())` 后 `setVisible(true)`。
- 典型对话框结构：构造函数中 `super(parent, modal)` → `initComponents()`（NetBeans 表单编辑器生成的布局代码，勿手改）→ 用上一次的值填充控件（`Preferences`/`Pref`/`Setting`/静态字段）→ `finishInitialization()` 恢复上次尺寸和位置；ESC 键触发 `escapePressed()`（通常等同 Cancel）。
- 无模态对话框（EModelessDialog 子类）多以静态单例 `theDialog` 缓存，并实现 `HighlightListener`/`DatabaseChangeListener`，随当前高亮选中和数据库变化实时刷新（Linux 平台因窗口置顶 bug 每次重建）。
- 修改电路的对话框不直接改库，而是 new 一个内部 `Job` 子类，在后台线程 `doIt()` 中执行数据库操作，`terminateOK()` 中刷新界面。
- `options` 包全部选项卡继承 `PreferencePanel`（它又继承 EDialog），遵循统一生命周期：`init()` 加载当前值到控件、`term()` 将控件值写回（用户点 OK 时）、`reset()`/`resetThis()` 恢复出厂值；用户偏好写入 `User`/`IOTool`/`SimulationTool` 等静态设置，工程偏好通过 `Setting` 读写。
- 与 `projectSettings` 包的配合：`PreferencesFrame`（选项对话框的宿主）把改动打包成 `Setting.SettingChangeBatch` 提交数据库；`ProjSettings` 负责把工程偏好导出/导入 projsettings.xml。

---

## EDialog（基类，重点）

**文件**: `com/sun/electric/tool/user/dialogs/EDialog.java`（329 行）
**职责**: 所有模态对话框的基类。按"对话框类"全局记住上次的窗口位置与尺寸（静态 Map），在构造时恢复；为根窗格注册 ESC 快捷键（触发 `escapePressed()`）；提供文本域/列表/树/字体框等一批静态工具方法，以及记录窗口移动/缩放的内部监听器。
**继承/实现**: `extends javax.swing.JDialog`
**关键字段**:
- `locations (static Map<Class<?>,Point>)` — 按对话框类记录上次位置（行 61）
- `sizes (static Map<Class<?>,Dimension>)` — 按对话框类记录上次尺寸（行 62）
- `textBoxFocusListener / textBoxMouseListener (static)` — 实现"Tab 进入时全选文本"的共享监听器（行 63-64）
- `justMoused (static JTextField)` — 记录刚被鼠标点过的文本域，避免鼠标点击后仍被全选（行 65）

### 方法

- `protected EDialog(Frame parent, boolean modal)`（行 68）— 构造器：多显示器下用当前窗口的 GraphicsConfiguration 创建；恢复上次位置；注册 ESC 键到 `escapePressed()`；非模态时通过 `TopLevel.addModelessDialog(this)` 纳入置顶管理。
- `public static Point getDialogLocation(Class<?> clz)`（行 98）— 返回该类对话框上次的位置；没有记录时取 `User.getDefaultWindowPos()` 并偏移 (+100,+50)。
- `public static Dimension getDialogSize(Class<?> clz)`（行 110）— 返回该类对话框上次的尺寸（可能为 null）。
- `protected void finishInitialization()`（行 120）— 完成对话框初始化：若曾记录过尺寸则恢复之（子类构造器末尾必调）。
- `protected void ensureMinimumSize()`（行 130）— 保证窗口不小于用户上次指定的大小（若记录了尺寸）。
- `protected void escapePressed()`（行 143）— ESC 键回调，空实现，子类覆写为"取消对话框"。
- `protected void closeDialog()`（行 145）— 隐藏对话框（setVisible(false)），子类通常覆写为 dispose。
- `public static void ensureComboBoxFont(JComboBox fontBox, String font)`（行 156）— 保证字体下拉框中包含指定字体名，没有则加入。
- `public static boolean recursivelyHighlight(JTree tree, DefaultMutableTreeNode curNode, DefaultMutableTreeNode objWanted, TreePath topPath)`（行 176）— 递归在树中查找并高亮（选中+滚动到可见）指定节点。
- `public static void focusOnTextField(JTextComponent textComponent)`（行 200）— 聚焦文本组件并全选其中的文本。
- `public static void focusClearOnTextField(JTextComponent textComponent)`（行 209）— 聚焦文本组件并把光标移到开头（取消全选）。
- `public static void makeTextFieldSelectAllOnTab(JTextField textField)`（行 218）— 给文本域挂监听器，使其在 Tab 聚焦时自动全选（几乎每个对话框都对输入框调用）。
- `public static void centerSelection(JList list)`（行 273）— 使列表的选中项显示在可视区中央（优于 JDK 的 ensureIndexIsVisible，因为选中项可能停在底部）。
- `public static class MoveComponentListener implements ComponentListener`（行 288）— 监听组件移动/缩放并写回 `locations`/`sizes` 静态表（`componentResized` 行 293、`componentMoved` 行 300）；EDialog 与 EModelessDialog 都使用它。

> 另一个基类 `EModelessDialog`（141 行，`extends JFrame`）：无模态对话框基类，构造器同样注册 ESC 键、恢复位置、`TopLevel.addModelessDialog(this)` 管理置顶，并在 Mac 上挂菜单栏；`protected finishInitialization()`（行 79）恢复尺寸、`protected ensureProperSize()`（行 91）保证不小于上次尺寸且不超过屏幕、`public void toFront()`（行 136）置顶并把窗口从最小化恢复。

---

## 操作对话框目录

（dialogs 主目录除 EDialog 外的全部 59 个类；"操作对话框"= 菜单命令调起、执行操作的窗口）

### About
**文件**: `com/sun/electric/tool/user/dialogs/About.java`
**职责**: "About Electric" 对话框：显示版本、版权/保修信息，随机挑选 3 位作者展示，并可浏览 C 团队/Java 团队贡献者名单（CastOfThousands）、查看插件列表、保存信息到文件、打开许可细节。
**主要方法**:
- `About(Frame parent)` — 唯一 public 入口，构造模态对话框并加载版本与作者信息。

### AnnularRing
**文件**: `com/sun/electric/tool/user/dialogs/AnnularRing.java`
**职责**: 操作对话框：在版图上生成圆环（annulus）节点，可选层、内外半径、段数与角度（静态字段缓存上次输入）。
**主要方法**:
- `showAnnularRingDialog()` (static) — 校验当前单元与可用纯层节点后弹窗。
- `AnnularRing(Frame parent, Cell cell)` — 私有构造器，填充层列表与上次的半径/段数/角度。
- `MakeAnnulus` (public static class extends Job) — 后台线程按参数生成圆环轨迹（`doIt()` 用 DRC 分辨率做网格对齐）；`getNodeInst()` 返回生成的 NodeInst。

### Array
**文件**: `com/sun/electric/tool/user/dialogs/Array.java`
**职责**: 操作对话框（EModelessDialog）：把选中电路按 X/Y 重复数阵列复制，支持边重叠/中心线/特征边界/测量距离四种间距方式、线性-对角线排列、转置、翻转、交错（stagger）、添加下标名等选项（Pref 持久化）。
**主要方法**:
- `showArrayDialog()` (static) — 弹窗（无选中对象时报错）。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` — 选中变化时刷新对话框。
- `databaseChanged(DatabaseChangeEvent)` — 数据库变化时刷新。

### ArtworkLook
**文件**: `com/sun/electric/tool/user/dialogs/ArtworkLook.java`
**职责**: 操作对话框（EModelessDialog，单例）：编辑所选 Artwork 技术节点/弧的颜色与图案，内嵌 ColorPatternPanel；点击 Apply 生成 ApplyChanges Job 把 ART_COLOR/ART_PATTERN 变量写到对象上。
**主要方法**:
- `showArtworkLookDialog()` (static) — 查找选中 Artwork 对象后显示（无则提示）。
- `ArtworkLook(Frame parent, List<Geometric> artObjects)` — 构造并显示颜色/图案面板。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` — 选中变化时重新加载外观。

### Attributes
**文件**: `com/sun/electric/tool/user/dialogs/Attributes.java`
**职责**: 操作对话框（EModelessDialog，单例）："Edit Parameters"对话框，按 Cell/Node/Arc/Export/Port 单选按钮切换对象，编辑其参数/变量（非 Debug 模式只显示参数），内嵌 TextInfoPanel 与 TextAttributesPanel，修改即时生效（内部 Job）。
**主要方法**:
- `showDialog()` (static) — 显示对话框并加载当前高亮对象的属性。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` — 高亮变化时重载。
- `databaseChanged(DatabaseChangeEvent)` — 所选对象被修改时重载。

### AttributesTable
**文件**: `com/sun/electric/tool/user/dialogs/AttributesTable.java`
**职责**: 可复用组件（extends JTable）：在其他对话框（Attributes、GetInfoNode、GetInfoArc 等）中显示对象的属性/参数表，支持单元格内编辑（Code/Display/Units 列为下拉框）、右键菜单新建/复制/删除属性、Show 子菜单开关各列；`AttValPair` 静态类供 GetInfoArc 等读取原始键值。
**主要方法**:
- `AttributesTable(ElectricObject owner, boolean showCode, boolean showDispPos, boolean showUnits)` — 构造表格并设置各列编辑器。
- `applyChanges()` — 把表格中所有增删改提交为内部 ApplyChanges Job。
- `cancelChanges()` — 停止单元格编辑并回退到数据库值。
- `setElectricObject(ElectricObject eobj)` — 加载指定对象（NodeInst 只列参数和属性）的属性。
- `databaseChanged(DatabaseChangeEvent)` — 重载当前对象属性。

### BusParameters
**文件**: `com/sun/electric/tool/user/dialogs/BusParameters.java`
**职责**: 操作对话框："Bus Parameters"（总线参数）管理：在库变量 LIB_Bus_Variables 中维护 name=value 参数表，给选中节点/弧/导出名创建总线模板变量，并可按 `$(param)` 语法加四则运算批量替换命名。
**主要方法**:
- `showBusParametersDialog()` (static) — 弹窗编辑各库的总线参数。
- `makeBusParameter()` (static) — 把选中对象的名称做成总线模板（校验选中类型后走 AddTemplate Job）。
- `updateBusParametersInt()` (static) — 内部接口：刷新全部库的总线参数（供 ArchGen 插件）。
- `updateCellBusParameterInt(Cell cell, Library lib, EditingPreferences ep)` (static) — 更新单个单元的总线参数。
- `replaceBusParameterInt(String varString)` (static) — 对字符串做总线参数替换。
- `addTemplateWithString(ElectricObject owner, String suffix)` (static) — 创建带后缀的总线模板。
- 常量 `BUS_VARIABLES`、`NODE_BUS_TEMPLATE`、`ARC_BUS_TEMPLATE`、`EXPORT_BUS_TEMPLATE` (public static final Variable.Key)。

### CVSLog
**文件**: `com/sun/electric/tool/user/dialogs/CVSLog.java`
**职责**: 操作对话框：显示库文件的 CVS 版本日志表（Version/Branch/Date/Author/Commit Message/State/Tag），右键菜单提供 Compare with Local、Compare Versions、Revert to this Version。
**主要方法**:
- `CVSLog(List<Log.LogEntry> entries, String title, String workingVersion)` — 构造并以 LogTableModel 填充表格。
- `showContextMenu(MouseEvent e)` — 按选中行数启用右键菜单项并弹出。

### CellBrowser
**文件**: `com/sun/electric/tool/user/dialogs/CellBrowser.java`
**职责**: 操作对话框：通用单元浏览器，按 Library/View 下拉框 + 正则 Filter 过滤单元列表，根据传入的 DoAction 执行不同动作（新建实例、编辑、重命名、复制、删除、选择单元），各动作有专属附加控件，偏好用 java.util.prefs 按动作分别记忆。
**主要方法**:
- `CellBrowser(Frame parent, boolean modal, DoAction action)` — 构造；标题与按钮文字来自 action。
- `databaseChanged(DatabaseChangeEvent)` — 数据库变化时重载列表。
- `getSelectedCell()` — 返回列表当前选中的 Cell（取消则 null）。
- `DoAction` (public static class) — 动作定义；常量 `newInst`、`editCell`、`renameCell`、`duplicateCell`、`deleteCell`、`selectCellToAssoc`、`selectCellToCopy`。

### CellLists
**文件**: `com/sun/electric/tool/user/dialogs/CellLists.java`
**职责**: 操作对话框："Cell Lists"对话框按条件（全部/被引用/未被引用/当前单元之下/占位单元、按视图、排除新旧版本、排序方式）生成单元列表，输出到消息窗口或磁盘文件；类中还集中了一批"列表/统计"命令的静态入口。
**主要方法**:
- `generalCellListsCommand()` (static) — 弹出通用单元列表对话框。
- `listNodesAndArcsInCellCommand()` (static) — 递归列出当前单元的节点和弧。
- `listCellInstancesCommand()` (static) — 列出当前单元的实例。
- `numberOfTransistorsCommand()` (static) — 统计当前单元晶体管数。
- `listCellUsageCommand(boolean recursive)` (static) — 列出当前单元的使用情况。
- `designSummaryCommand(boolean onlySelected)` (static) — 输出类 Cadence 的设计摘要。
- `describeThisCellCommand()` (static) — 详细描述当前单元（内部类 DescribeThisCellJob 执行）。

### CellProperties
**文件**: `com/sun/electric/tool/user/dialogs/CellProperties.java`
**职责**: 操作对话框："Cell Properties"：按库/单元列表切换，编辑单元级属性——禁止修改整体/实例、标准单元库标记、技术编辑器库标记、默认展开、帧尺寸/方向/标题框、设计者名、技术、单元文字字体与字号（PerCellValues 用 TempPref 惰性缓存初值，OK 时 diff 提交）。
**主要方法**:
- `CellProperties(Frame parent)` — 构造并加载当前库的单元列表。

### Change
**文件**: `com/sun/electric/tool/user/dialogs/Change.java`
**职责**: 操作对话框（EModelessDialog，单例，implements HighlightListener）：把选中节点/弧替换为别的原型（PrimitiveNode/ArcProto/单元，树形浏览），范围可选选中/相连/本单元/本库/全部库，支持"连弧一起换"、忽略端口名、允许缺端口、保留参数等选项；执行后重新连接拓扑。
**主要方法**:
- `showChangeDialog()` (static) — 显示对话框并加载当前高亮对象。
- （继承自 HighlightListener 的 `highlightChanged`/`highlighterLostFocus` 刷新对话框。）

### ChangeCellGroup
**文件**: `com/sun/electric/tool/user/dialogs/ChangeCellGroup.java`
**职责**: 操作对话框：把一个或多个单元的 CellGroup 改为"自成一组"或移动到某个已有组（下拉框按名排序），单选状态用 Preferences 记住。
**主要方法**:
- `ChangeCellGroup(Frame parent, boolean modal, List<Cell> cellsToRegroup, Library initialLibrary)` — 构造；OK 走内部 ChangeCellGroupJob 改库。

### ChangeCurrentLib
**文件**: `com/sun/electric/tool/user/dialogs/ChangeCurrentLib.java`
**职责**: 操作对话框："Set Current Library"：列出所有可见库，双击或 OK 切换当前库。
**主要方法**:
- `showDialog()` (static) — 弹窗。
- `repaintAllAfterCurLibAction()` (static) — 切库后统一刷新窗口标题、库树并 repaint 所有编辑窗。

### ChangeText
**文件**: `com/sun/electric/tool/user/dialogs/ChangeText.java`
**职责**: 操作对话框："Change Text Size"：批量修改文字大小/字体/粗斜体/下划线，对象类型（节点/弧/导出/注释/实例/单元文字）与范围（选中对象/本单元/某视图全部单元/本库全部单元）可组合选择，底部实时显示被改文字的大小范围统计。
**主要方法**:
- `changeTextDialog()` (static) — 弹窗。
- 内部 `ChangeTextSizes` (Job) — 实际扫描并修改 TextDescriptor。

### ColorPatternPanel
**文件**: `com/sun/electric/tool/user/dialogs/ColorPatternPanel.java`
**职责**: 可复用面板（extends JPanel）：编辑颜色与填充图案（显示/打印点刻、轮廓样式、透明层、RGB 颜色、不透明度），带 16x16 点阵图案编辑视图（PatternView）与图案选择器（PatternChoices）、颜色选择器预览；被 LayersTab（选项对话框）与 ArtworkLook（操作对话框）共用。
**主要方法**:
- `ColorPatternPanel(boolean showPrinter)` — 构造；showPrinter=false 时隐藏打印相关控件。
- `setColorMap(Color[] map)` — 设置透明层颜色映射。
- `setOtherTransparentLayerNames(String names)` — 记录共享透明层的其他层名（用于警告）。
- `setColorPattern(Info li)` — 用 Info 结构填充面板。
- `Info` (public static class) — 面板数据载体：`Info(EGraphics)` 从图形对象取色/图案；`Info(User.ColorPrefType)` 只取纯色；`updateGraphics(EGraphics)` 把面板值写回 EGraphics。

### ComponentMenu
**文件**: `com/sun/electric/tool/user/dialogs/ComponentMenu.java`
**职责**: 操作对话框：编辑技术的组件菜单布局（拖放节点/弧/单元到菜单格、编辑节点函数与名称、支持拖拽 DnD 与恢复出厂菜单），既作为 Preferences 的 ComponentMenuTab 面板，也可被技术编辑器独立调起。
**主要方法**:
- `showComponentMenuDialog(String techName, Xml.MenuPalette xmp, List<Xml.PrimitiveNodeGroup> nodeGroups, List<Xml.ArcProto> arcs)` (static) — 技术编辑器调用的独立弹窗入口。
- `ComponentMenu(Frame parent, boolean techEdit)` — 构造面板。
- `showTechnology(...)` / `getPanel()` / `isSaveChanges()` / `getMenuInfo()` / `factoryReset()` / `isRedisplayMenu()` — 与 ComponentMenuTab 协作的存取方法（由 ComponentMenuTab 调用）。

### CrossLibCopy
**文件**: `com/sun/electric/tool/user/dialogs/CrossLibCopy.java`
**职责**: 操作对话框："Cross Library Copy"：左右两个库并排（可同步滚动的三列表：左库单元/日期对比列/右库单元），把选中单元从一侧复制/移动到另一侧，选项含"复制后删除"、"复制相关视图"、"复制子单元"、"使用已存在子单元"，中间列显示新旧/内容异同（Compare 按钮可选对比内容）。
**主要方法**:
- `CrossLibCopy(Frame parent)` — 构造并加载两库单元。
- `CrossLibraryCopyJob` (public static class extends Job) — 后台执行复制（copyRecursively），terminateOK 后刷新对话框并重选原行。

### DesignRulesPanel
**文件**: `com/sun/electric/tool/user/dialogs/DesignRulesPanel.java`
**职责**: 可复用面板（extends JPanel）：设计规则编辑面板（节点尺寸/层宽/面积/包围/间距/宽度规则、宽线间距规则列表），被选项对话框 DesignRulesTab 内嵌使用；从 DRC XMLRules 加载并写回。
**主要方法**:
- `DesignRulesPanel()` — 构造并注册所有输入框的全选监听。
- `init(Technology tech, Foundry.Type foun, XMLRules drcR)` — 加载某技术（+代工厂）的规则并显示。

### EModelessDialog
**文件**: `com/sun/electric/tool/user/dialogs/EModelessDialog.java`
**职责**: 无模态对话框基类（extends JFrame），见上文 EDialog 一节的补充说明。
**主要方法**:
- `EModelessDialog(Frame parent)` — 构造器：注册 ESC、恢复位置、设置图标、纳入置顶管理。
- `finishInitialization()` (protected) — 恢复上次尺寸。
- `ensureProperSize()` (protected) — 尺寸不小于上次且不超出所在显示器。
- `escapePressed()` (protected) / `closeDialog()` (protected) — 供子类覆写。
- `toFront()` — 置顶并把最小化的窗口恢复正常。

### EditKeyBinding
**文件**: `com/sun/electric/tool/user/dialogs/EditKeyBinding.java`
**职责**: 操作对话框：给单个菜单项添加快捷键绑定（主键 + 可选第二键），实时显示与已有绑定的冲突列表；Add 时可选移除冲突绑定，ALT 组合与下拉菜单助记符冲突会被拒绝。
**主要方法**:
- `EditKeyBinding(EMenuItem item, EMenuBar menuBar)` — 构造（header 显示菜单项描述）。

### EditKeyBindingConflict
**文件**: `com/sun/electric/tool/user/dialogs/EditKeyBindingConflict.java`
**职责**: 操作对话框：快捷键冲突提示框（"Shortcut conflicts with other key bindings"），提供 Remove/Leave/Cancel 三按钮（仅静态表单，逻辑由调用方处理）。
**主要方法**:
- `EditKeyBindingConflict(java.awt.Frame parent, boolean modal)` — 构造。

### EditKeyBindings
**文件**: `com/sun/electric/tool/user/dialogs/EditKeyBindings.java`
**职责**: 选项对话框（extends PreferencePanel，挂在 Preferences 的 General 区）：管理全部菜单命令的快捷键——树形浏览菜单结构，右侧列表显示某命令的绑定，支持 Add/Remove/Reset 单个或全部（KeyBoundTreeNode 在树中显示命令及绑定）。
**主要方法**:
- `EditKeyBindings(EMenuBar menuBar, java.awt.Frame parent, boolean modal)` / `EditKeyBindings(EMenuBar, PreferencesFrame, boolean)` — 两个构造器。
- `getUserPreferencesPanel()` — 返回编辑面板。
- `getName()` — 返回 "Key Bindings"。
- `resetThis()` — 就地重置全部绑定并刷新树（返回 true）。
- `reset()` — 重置全部绑定。
- `valueChanged(TreeSelectionEvent)` — 树选择变化时刷新右侧绑定列表。

### ExecDialog
**文件**: `com/sun/electric/tool/user/dialogs/ExecDialog.java`
**职责**: 操作对话框：运行外部进程并交互——输出区滚动显示 stdout/stderr，底部输入框可向进程写行，进程结束弹错误框（退出码非 0）并显示状态；一次只能跑一个进程。
**主要方法**:
- `ExecDialog(java.awt.Frame parent, boolean modal)` — 构造。
- `addFinishedListener(Exec.FinishedListener l)` / `removeFinishedListener(...)` — 在 startProcess 前注册/注销结束监听。
- `startProcess(String[] command, String[] envVars, File dir)` — 启动外部进程并显示对话框。
- `processFinished(Exec.FinishedEvent e)` — 进程结束回调，更新状态栏。

### FillGenDialog
**文件**: `com/sun/electric/tool/user/dialogs/FillGenDialog.java`
**职责**: 操作对话框（类头注明 "Unused class to manage fill generators"）：填充单元生成器对话框——按金属层配置 Vdd/Gnd 保留间距与线宽、模板尺寸、平铺尺寸（2x2..13x13）；可选加载插件类 com.sun.electric.plugins.generator.FillCelllGenPanel 扩展。
**主要方法**:
- `FillGenDialog(Technology tech, Frame parent)` — 构造并按该技术金属层数建面板。
- `generalSetup(boolean flatSelected, boolean createMaster)` — 按选项启用/禁用面板区域。
- `optionAction(boolean flatSelected, boolean createMaster, boolean isCellSelected)` — 插件回调入口，设置平铺区可用性。
- `okButtonClick(boolean isFlatSelected, boolean createMaster, boolean binary, boolean around, double gap, FillGenConfig.FillGenType genType, int level)` — 收集面板值生成 FillGenConfig。
- `openFillGeneratorDialog(Technology tech)` (static) — 外部弹窗入口。
- `main(String args[])` (static) — 独立测试入口。

### FindText
**文件**: `com/sun/electric/tool/user/dialogs/FindText.java`
**职责**: 操作对话框（EModelessDialog，单例）："Search and Replace"——在文本窗口（TextWindow）中查找/替换，支持正则、大小写、反向、行号跳转，以及限定搜索对象（节点名/变量、弧名/变量、导出名/变量、单元变量、临时名、仅高亮区域）和 Code/Units 限制（全部选项 Pref 持久化）。
**主要方法**:
- `findTextDialog()` (static) — 弹窗。

### GetInfoArc
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoArc.java`
**职责**: 操作对话框（EModelessDialog，单例）："Arc Properties"——编辑所选弧的名称、宽度、易选标志、刚性/定角/可滑动、端点延伸、方向与取反，显示头/尾坐标（带 See 定位按钮），内嵌 AttributesTable 与总线成员列表，另可弹 ColorPatternPanel 改颜色图案。
**主要方法**:
- `showDialog()` (static) — 显示并加载当前高亮弧。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` — 高亮变化时重载。
- `databaseChanged(DatabaseChangeEvent)` — 弧被外部修改（或先前未拿到 Examine 锁）时重载。

### GetInfoExport
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoExport.java`
**职责**: 操作对话框（EModelessDialog，单例）："Export Properties"——编辑所选导出的名称、特性（characteristic）、Body only/Always drawn、参考名（仅 reference 特性可编辑），显示中心坐标，内嵌 TextInfoPanel 改文字显示；ChangeExport Job 提交并把特性同步到同组其他视图。
**主要方法**:
- `showDialog()` (static) — 显示并加载当前高亮导出。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` / `databaseChanged(DatabaseChangeEvent)` — 随高亮/数据库变化重载。

### GetInfoMulti
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoMulti.java`
**职责**: 操作对话框（EModelessDialog，单例）："Multi-object Get Info"——按当前高亮对象的组合动态生成可修改属性列表（节点 X/Y 尺寸、位置、旋转、镜像、展开、锁定；弧宽度/刚性/定角/可滑动/延伸/方向/取反；导出与文字的各种 TextDescriptor 属性），一次 Apply 改所有选中对象。
**主要方法**:
- `showDialog()` (static) — 显示并加载当前高亮集合。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` / `databaseChanged(DatabaseChangeEvent)` — 随高亮/数据库变化重载。

### GetInfoNode
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoNode.java`
**职责**: 操作对话框（EModelessDialog，单例）："Node Properties"——编辑所选节点的名称、X/Y 位置与尺寸、旋转、镜像、易选/单元外不可见/锁定、展开状态，特殊节点（可缩放晶体管、多切口、碳纳米管、布线排除、工艺特定字段 ExtraField）有动态附加控件；内嵌 AttributesTable；ChangeNode Job 提交修改。
**主要方法**:
- `showDialog()` (static) — 显示并加载当前高亮节点。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` / `databaseChanged(DatabaseChangeEvent)` — 随高亮/数据库变化重载。

### GetInfoOutline
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoOutline.java`
**职责**: 操作对话框（EModelessDialog，单例）："Outline Properties"——以列表编辑所选节点的轮廓顶点（trace），支持修改 X/Y 坐标、删除点、复制点，Apply 走 AdjustOutline Job 写回 setTrace。
**主要方法**:
- `showOutlinePropertiesDialog()` (static) — 显示并加载当前节点轮廓。
- `GetInfoOutline(Frame parent)` — 构造并注册高亮/数据库监听。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` / `databaseChanged(DatabaseChangeEvent)` — 随高亮/数据库变化重载。

### GetInfoText
**文件**: `com/sun/electric/tool/user/dialogs/GetInfoText.java`
**职责**: 操作对话框（EModelessDialog，单例）："Text Properties"——编辑所选文字的文本、多行开关、Code 求值显示，内嵌 TextInfoPanel（显示选项）与 TextAttributesPanel（Code/单位/显示方式）；还实现就地编辑（edit-in-place）：在电路上文字位置直接放 JTextField/JEditorPane 编辑（EditInPlaceListener 支持撤销/重做、ESC 放弃）。
**主要方法**:
- `showDialog()` (static) — 显示并加载当前高亮文字。
- `editTextInPlace()` (static) — 若只有一段文字被选中且字号可显示，则在原地编辑；否则回退到对话框。
- `EIPTextField` / `EIPEditorPane` (static classes) — 就地编辑用的文本组件（paint 时抢占焦点）。
- `EditInPlaceListener` (public static class) — 就地编辑会话：`EditInPlaceListener(...)` 构造、`getTextComponent()`、`undo()`、`redo()`、`closeEditInPlace()`。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` / `databaseChanged(DatabaseChangeEvent)` — 随高亮/数据库变化重载。

### LanguageScripts
**文件**: `com/sun/electric/tool/user/dialogs/LanguageScripts.java`
**职责**: 操作对话框："Language Scripts"：管理挂在菜单上的 Bean Shell 脚本列表（文件+助记符），增删脚本（OpenFile 选 .java 文件）、编辑助记符，OK 保存到 User.prefScriptList 并重建动态语言菜单。
**主要方法**:
- `LanguageScripts()` — 构造（自带 setVisible(true)）。
- `getScripts()` (static) — 解析 prefScriptList 为 ScriptBinding 列表。
- `saveScripts()` — 把列表序列化回 prefScriptList。
- `ScriptBinding` (public static class) — 脚本绑定（mnemonic + fileName）。

### LayoutImage
**文件**: `com/sun/electric/tool/user/dialogs/LayoutImage.java`
**职责**: 操作对话框："Layout Image"：读入 JPEG/PNG 图片，按灰度在版图上用纯层节点点阵重建（最小/最大点宽、点间距、层、反色/单色选项），LayoutImageJob 后台生成。
**主要方法**:
- `LayoutImage(Frame parent)` — 构造（填充当前技术的 NODE 类原语到层下拉框）。

### LayoutText
**文件**: `com/sun/electric/tool/user/dialogs/LayoutText.java`
**职责**: 操作对话框："Make Layout Text"：把多行文字以指定字体/字号/缩放/点间距渲染成栅格，再用纯层节点点阵放到版图上（支持粗体/斜体/下划线/反色），静态字段记住上次设置。
**主要方法**:
- `LayoutText(Frame parent)` — 构造（加载系统字体与当前技术的 NODE 原语）。

### ManageInductors
**文件**: `com/sun/electric/tool/user/dialogs/ManageInductors.java`
**职责**: 操作对话框（EModelessDialog）："Manage Inductors"——计算版图电感的电感量（面积因子/长度因子/每拐角方块数），列出单元内电感及其弧、可重命名电感、为 FastHenry 指定厚度/Z 高度/剖分数（StoreFastHenryFactors Job）。
**主要方法**:
- `showInductorManagementDialog()` (static) — 弹窗。
- `databaseChanged(DatabaseChangeEvent)` — 勾选 stay current 时随数据库变化更新当前单元。
- `RenameInductor` / `StoreFastHenryFactors` (public classes extends Job) — 改名与 FastHenry 参数写库。

### ManipulateExports
**文件**: `com/sun/electric/tool/user/dialogs/ManipulateExports.java`
**职责**: 操作对话框：以表格批量管理当前单元的全部导出（复选/名称/层/特性下拉/仅本体），支持全选、反选、重编号数值导出名、删除、显示、高亮；单元格直接编辑名称/特性/Body-only 即时写库（ExportChanges 系列 Job）。
**主要方法**:
- `showDialog()` (static) — 弹窗（需当前单元）。
- `databaseChanged(DatabaseChangeEvent)` — 导出变化时重载表格。

### ManipulatePorts
**文件**: `com/sun/electric/tool/user/dialogs/ManipulatePorts.java`
**职责**: 操作对话框：以表格批量管理所选节点的全部端口（复选/名称/特性/连接弧/弧数/导出），支持全选、反选、重新导出（ReExportPorts）、删除导出、显示端口、高亮端口；表头点击排序。
**主要方法**:
- `showDialog()` (static) — 弹窗（需选中节点）。
- `highlightChanged(Highlighter)` — 随高亮切换显示的节点。
- `databaseChanged(DatabaseChangeEvent)` — 节点/导出变化时重载。

### ModalCommandDialog
**文件**: `com/sun/electric/tool/user/dialogs/ModalCommandDialog.java`
**职责**: 操作对话框：CVS 等外部命令运行期间的模态阻塞框（两条消息 + Cancel 按钮，目前 Cancel 被禁用以免破坏数据库），windowOpened 时启动 Exec，进程结束自动关闭。
**主要方法**:
- `ModalCommandDialog(Frame owner, boolean modal, Exec e, String message1, String message2)` — 构造并注册 Exec 结束监听。
- `processFinished(Exec.FinishedEvent e)` — 进程结束，移除窗口监听并关闭。
- `windowOpened(WindowEvent e)` — 窗口打开时启动外部进程。

### MoveBy
**文件**: `com/sun/electric/tool/user/dialogs/MoveBy.java`
**职责**: 操作对话框："Move By Amount"：输入 dX/dY 距离移动选中电路（CircuitChanges.manyMove），数值用 Preferences 记住。
**主要方法**:
- `showMoveByDialog()` (static) — 弹窗。
- `MoveBy(Frame parent)` — 构造（按当前技术格式化上次的 dX/dY）。

### MultiFingerTransistor
**文件**: `com/sun/electric/tool/user/dialogs/MultiFingerTransistor.java`
**职责**: 操作对话框："Multi-Finger Transistors"：按技术/晶体管原语/接触孔原语与长度、宽度、指间距、指数、切口数、额外多晶长度，自动生成多指晶体管单元（用 DRC 规则推算最优间距），MakeTransistorCell Job 建单元并导出 F1a/F1b、Act_1 等端口。
**主要方法**:
- `showMultiFingerTransistorDialog()` (static) — 弹窗。
- `MakeTransistorCell` (public static class extends Job) — 后台生成多指晶体管单元，terminateOK 打开新窗口。

### NCCAnnotations
**文件**: `com/sun/electric/tool/user/dialogs/NCCAnnotations.java`
**职责**: 操作对话框："NCC Annotations"：按库浏览各单元的 NCC 注释（NCC_ANNOTATION_KEY 变量），支持编辑（弹 EditAnnotationDialog 多行编辑）与删除，可过滤只显示有注释的单元。
**主要方法**:
- `NCCAnnotations(Frame parent)` — 构造并列出当前库单元注释。
- `doEdit(JButton but)` — 打开编辑对话框（EditAnnotationDialog，public 内部类）。
- `doDelete(JButton but)` — 删除注释（DeleteAnnotationJob）。
- `UpdateAnnotationJob` / `DeleteAnnotationJob` (public static classes extends Job) — 写库。

### NewCell
**文件**: `com/sun/electric/tool/user/dialogs/NewCell.java`
**职责**: 操作对话框："New Cell"：输入单元名，选库、视图（布局视图可选技术）、是否开新窗口，CreateCell Job 建单元并打开。
**主要方法**:
- `NewCell(Frame parent, Library curLib, String defaultName)` — 构造。

### NewExport
**文件**: `com/sun/electric/tool/user/dialogs/NewExport.java`
**职责**: 操作对话框："Create New Export"：在选中节点/端口上新建导出——名称、特性下拉框、Always drawn、Body only、参考名（reference 特性时启用），MakeExport Job 建导出（含多页原理图重名检查）。
**主要方法**:
- `NewExport(Frame parent)` — 构造（预选上次特性或高亮端口特性，setVisible(true) 内嵌）。

### OpenFile
**文件**: `com/sun/electric/tool/user/dialogs/OpenFile.java`
**职责**: 文件对话框工厂（普通类，非 EDialog）：封装 JFileChooser 提供打开/保存/选目录对话框，处理扩展名自动补全、覆盖确认、.delib 库目录遍历（LibDirFileSystemView）、按 FileType 过滤与组路径记忆。
**主要方法**:
- `chooseInputFile(FileType type, String title, EditingPreferences ep)` (static) — 打开文件对话框（默认从该类型的组路径开始）。
- `chooseDirectory(String title)` (static) — 选目录对话框。
- `chooseInputFile(FileType type, String title, boolean wantDirectory)` (static) — 带"是否选目录"的重载。
- `chooseInputFile(FileType type, String title, boolean wantDirectory, String initialDir, boolean setSelectedDirAsWorkingDir, EditingPreferences ep)` (static) — 完整重载。
- `chooseOutputFile(FileType type, String title, String defaultFile)` / `chooseOutputFile(FileType[] types, String title, String defaultFile)` (static) — 保存对话框（自动补扩展名）。
- `getOpenFileType(String libName, FileType def)` (static) — 按扩展名推断文件类型。
- `OpenFileSwing` (public static class extends JFileChooser) — 内部定制 chooser：`approveSelection()` 处理保存覆盖确认与 LIBDIR 重定向，`isTraversable(File)` / `isDirectorySelectionEnabled()` 使 .delib 目录可当文件选。

### OptionReconcile
**文件**: `com/sun/electric/tool/user/dialogs/OptionReconcile.java`
**职责**: 操作对话框："Project Preferences Reconcile"：读入带工程偏好的库时，把库中设置与当前逐项对比成表格（每行两组单选：CURRENT VALUE / LIBRARY VALUE），用户逐项或整体选择采用哪边，DoReconciliation Job 应用后重启读库 Job。
**主要方法**:
- `reconcileSettings(Map<String,Object> settingsNondefault, String libName, ReadLibrary job)` (static) — 判断是否有差异需要调和；需要则弹窗并返回 true。

### PreferencesFrame
**文件**: `com/sun/electric/tool/user/dialogs/PreferencesFrame.java`
**职责**: 选项对话框宿主（EModelessDialog，单例）：左侧分类树（General/Display/I-O/Tools/Technology 及插件），右侧三栏显示所选 Tab 的用户偏好面板 + 工程偏好面板；底部 Export/Import（工程偏好 XML）、Reset/Reset All（仅用户偏好）、Help、Apply/Cancel/OK；OK 时调用各 Tab 的 term() 并提交 Setting.SettingChangeBatch（OKUpdate Job），必要时提示保存到库或工程偏好文件。
**主要方法**:
- `preferencesCommand()` (static) — 显示 Preferences 窗口。
- `preferencesCommand(String tabName, String sectionName)` (static) — 显示并定位到指定选项卡。
- `updateLayerPreferencesColors()` (static) — 颜色方案变化时刷新 Layers 选项卡。
- `updateLayersSortingTabs()` (static) — 刷新所有面板（层排序变化时）。
- `PreferencesFrame(Frame parent)` — 构造整棵树（此处注册了全部 options 包的 Tab）。
- `getContext()` — 返回当前 Setting 值 Map（供各 Tab 读写）。
- `getEditingPreferences()` / `setEditingPreferences(EditingPreferences)` — 编辑偏好存取。

### Progress
**文件**: `com/sun/electric/tool/user/dialogs/Progress.java`
**职责**: 进度对话框（普通类）：进度条 + 文本说明，MDI 模式下为 JInternalFrame 否则为 JFrame，记住上次位置。
**主要方法**:
- `Progress(String title)` — 构造并显示。
- `close()` — 关闭并记忆位置。
- `setProgress(int progress)` / `getProgress()` — 设置/读取进度（0-100）。
- `setNote(String note)` / `getNote()` — 设置/读取说明文字。

### PromptAt
**文件**: `com/sun/electric/tool/user/dialogs/PromptAt.java`
**职责**: 操作对话框：定位在电路某节点上方的小提问框（Yes/No、字符串输入、下拉选择、自定义 Field 数组/网格、颜色选择、自定义按钮），对话结束后从 Field.getFinal() 取结果。
**主要方法**:
- `showPromptAt(EditWindow_ wnd, NodeInst ni, String title, String label, boolean initial)` (static) — Yes/No 提问，返回布尔。
- `showPromptAt(EditWindow_ wnd, NodeInst ni, String title, String label, String initial, String[] choices)` (static) — 下拉选择。
- `showPromptAt(EditWindow_ wnd, NodeInst ni, String title, String label, String initial)` (static) — 字符串输入。
- `showPromptAt(EditWindow_ wnd, NodeInst ni, String title, Field[] fields)` / `showPromptAt(..., Field[][] fields)` (static) — 自定义一维/二维字段。
- `PromptAt(boolean yesNo)` — 构造。
- `Field` (public static class) — 字段定义，5 个构造器（消息/布尔/字符串/选择/颜色/按钮）+ `getFinal()`。

### SavedViews
**文件**: `com/sun/electric/tool/user/dialogs/SavedViews.java`
**职责**: 操作对话框（EModelessDialog，单例，implements HighlightListener）："Window Views"——把当前窗口的缩放/偏移存为单元变量 USER_windowview_<名>，并可恢复；列表随当前单元重载。
**主要方法**:
- `showSavedViewsDialog()` (static) — 弹窗。
- `highlightChanged(Highlighter)` / `highlighterLostFocus(Highlighter)` — 高亮变化时重载视图列表。

### SeaOfGatesCell
**文件**: `com/sun/electric/tool/user/dialogs/SeaOfGatesCell.java`
**职责**: 操作对话框（EDialog，单例）："Sea-of-Gates Cell Preferences"——配置 SoG 布线工具的单元级参数：弧属性（层/宽度/间距）、网格点、网络-层映射、阻断层、布线边界层、技术选择等（SeaOfGatesCellParameters），可导出数据。
**主要方法**:
- `showSeaOfGatesCellDialog()` (static) — 弹窗（需当前单元）。

### SelectObject
**文件**: `com/sun/electric/tool/user/dialogs/SelectObject.java`
**职责**: 操作对话框（EModelessDialog，单例，implements DatabaseChangeListener）："Select Object"——按节点/弧/导出/网络单选按钮列出当前单元对象（数字感知排序），点选即高亮到版图；支持正则/普通搜索（Find 按钮最多 1000 个，确认后显示）；让 Electric 快捷键穿透对话框。
**主要方法**:
- `selectObjectDialog(Cell thisCell, boolean updateOnlyIfVisible)` (static) — 显示并加载指定单元。
- `databaseChanged(DatabaseChangeEvent)` — 数据库变化时重载列表。

### SetFocus
**文件**: `com/sun/electric/tool/user/dialogs/SetFocus.java`
**职责**: 操作对话框："Set Focus"：设置编辑窗口的中心坐标（X/Y Center）与横向网格单位数（据此换算出缩放），并更新焦点浏览器。
**主要方法**:
- `showSetFocusDialog()` (static) — 弹窗（需正在编辑单元）。
- `SetFocus(Frame parent, EditWindow wnd)` — 构造（显示当前偏移与缩放）。

### SpecialProperties
**文件**: `com/sun/electric/tool/user/dialogs/SpecialProperties.java`
**职责**: 操作对话框调度器（普通类）：双击节点时按节点类型弹专用属性小框——电阻/电容/电感（带单位量纲下拉）、二极管面积、NPN/PNP 面积、其他晶体管宽/长（带 Code 下拉）、全局信号（名称+特性）；返回 -1 表示应显示通用 Node Properties 对话框，0 表示就地文字编辑。
**主要方法**:
- `doubleClickOnNode(EditWindow wnd, NodeInst ni)` (static) — 双击分发入口；内部类 NodePropertiesDialog / GlobalPropertiesDialog / TransistorPropertiesDialog（均 extends EDialog）及 ModifyNodeProperties (Job)。

### Spread
**文件**: `com/sun/electric/tool/user/dialogs/Spread.java`
**职责**: 操作对话框："Spread About Highlighted"：把选中节点附近的电路向上/下/左/右摊开指定距离（SpreadJob → CircuitChangeJobs.spreadCircuitry），方向与距离用静态字段记住。
**主要方法**:
- `showSpreadDialog()` (static) — 弹窗（需高亮一个 NodeInst）。
- `Spread(Frame parent, NodeInst ni)` — 构造。

### TextAttributesPanel
**文件**: `com/sun/electric/tool/user/dialogs/TextAttributesPanel.java`
**职责**: 可复用面板（extends JPanel）：编辑属性/变量文字的特性选项——Code（表达式类型）、Units（单位）、Show（显示方式，含 None），并显示属性继承提示；修改可即时生效（updateChangesInstantly=true）或由宿主对话框统一 Apply。
**主要方法**:
- `TextAttributesPanel(boolean updateChangesInstantly)` — 构造并填充 Code/Units/Show 下拉框。
- `setVariable(Variable.Key varKey, ElectricObject owner)` — 加载指定变量（varKey 为 null 时整体禁用）。
- `withPanelValues(TextDescriptor td)` — 把面板的单位/显示设置应用到给定 TextDescriptor。
- `withPanelCode(Object value)` — 把面板的 Code 设置应用到值。
- `applyChanges()` — 提交修改（内部 ChangeText Job），返回是否有改动。

### TextInfoPanel
**文件**: `com/sun/electric/tool/user/dialogs/TextInfoPanel.java`
**职责**: 可复用面板（extends JPanel）：编辑文字的显示选项——字号（点数/单位）、字体、粗/斜/下划线、锚点位置、旋转、颜色、偏移、盒式锚点宽高、单元外不可见；可选"Highlight Owner"按钮定位所属对象（paramDialog 时移除）。
**主要方法**:
- `TextInfoPanel(boolean updateChangesInstantly, boolean paramDialog)` — 构造。
- `setTextDescriptor(Variable.Key varKey, ElectricObject owner)` — 加载指定变量/名称的文字描述符。
- `withPanelValues(TextDescriptor td)` — 把面板设置应用到给定 TextDescriptor。
- `applyChanges(boolean adjustErrors)` — 提交修改（内部 Job），返回是否有改动。

### ViewControl
**文件**: `com/sun/electric/tool/user/dialogs/ViewControl.java`
**职责**: 操作对话框："View Control"：浏览全部 View（名称+缩写后缀），创建新 View（可设文字视图）、删除非永久 View（CreateView/DeleteView Job 后台执行）。
**主要方法**:
- `ViewControl(Frame parent)` — 构造并加载视图列表。

### WaveformZoom
**文件**: `com/sun/electric/tool/user/dialogs/WaveformZoom.java`
**职责**: 操作对话框："Set Window Extents"：精确设置波形窗口的纵轴范围（低/高/线性缩放或对数、一键 Scale to 2 Pi）与横轴（时间）范围，OK 后调用 WaveformWindow.setZoomExtents。
**主要方法**:
- `WaveformZoom(Frame parent, double lowVert, double highVert, double scaleVert, boolean logVert, double lowHoriz, double highHoriz, WaveformWindow curWindow, Panel curPanel)` — 构造（带 YScaleListener 实时换算缩放与范围）。

---

## 选项对话框目录（options 包）

（全部 57 个类；"选项对话框"= Preferences 窗口中的选项卡，除 PreferencePanel 基类外均继承它。所有 Tab 的公共套路：构造器 `Xxx(PreferencesFrame parent, boolean modal)` 调 initComponents()；`getUserPreferencesPanel()` / `getProjectPreferencesPanel()` 返回面板（无则该侧为 null）；`getName()` 返回树中显示名；`init()` 加载当前值；`term()` 写回；`reset()`（部分还有 `resetThis()`）恢复出厂。用户偏好直接改 User/IOTool/SimulationTool/Compaction/Extract 等工具静态设置；工程偏好通过 Setting 的 getBoolean/getInt/getDouble/getString/setXxx 读写 PreferencesFrame 的 context Map，OK 时统一入库。下面只列各 Tab 的个性内容。）

### PreferencePanel（options 包基类）
**文件**: `com/sun/electric/tool/user/dialogs/options/PreferencePanel.java`
**职责**: Preferences 选项卡基类（extends EDialog）：定义 init()/term()/reset()/resetThis()/refresh() 生命周期钩子，保存 PreferencesFrame 父引用，提供 `curTech`/`curLib` 以及 Setting 值的安全读写（getBoolean/getInt/getLong/getDouble/getString/setXxx，值写入父窗口的 context Map），并提供 putPrefs/getTechPool/getEditingPreferences。
**主要方法**:
- `PreferencePanel(Frame parent, boolean modal)` / `PreferencePanel(PreferencesFrame parent, boolean modal)` — 两个构造器。
- `getUserPreferencesPanel()` / `getProjectPreferencesPanel()` — 默认返回 null。
- `getName()` — 默认返回 ""。
- `refresh()` — 层排序等外部变化时刷新（默认空）。
- `isInited()` / `setInited()` — 惰性初始化标记。
- `getTech()` — 当前技术。
- `init()` / `term()` / `reset()` / `resetThis()` — 生命周期钩子（默认空/返回 false）。
- `putPrefs(PrefPackage pp)` — 把偏好包写入 Electric 偏好子树。
- `getBoolean(Setting)` / `getInt(Setting)` / `getLong(Setting)` / `getDouble(Setting)` / `getFormattedDouble(Setting)` / `getString(Setting)` / `setBoolean(Setting, boolean)` / `setInt(Setting, int)` / `setLong(Setting, long)` / `setDouble(Setting, double)` / `setString(Setting, String)` — Setting 值读写。

### AddedTechnologiesTab
**文件**: `.../options/AddedTechnologiesTab.java`
**职责**: 工程偏好页："Added Technologies"：管理通过 Technology Editor/Wizard 加入的 XML 技术文件列表（User.getSoftTechnologiesSetting，分号分隔），Add 用 OpenFile 选 XML、Remove 删除（重启生效）。
**主要方法**: 构造器；`getProjectPreferencesPanel()`；`getName()` 返回 "Added Technologies"；`init()` 建列表；`term()` 序列化回 Setting；`reset()` 清空。

### AntennaRulesTab
**文件**: `.../options/AntennaRulesTab.java`
**职责**: 用户偏好页："Antenna Rules"：按技术列出金属/多晶弧并编辑其最大天线比（ERCAntenna.AntennaPreferences 的 antennaRatio Map），即时更新列表显示。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Antenna Rules"；`init()` 建技术+弧列表；`term()` putPrefs；`reset()` 写回出厂偏好。

### CDLTab
**文件**: `.../options/CDLTab.java`
**职责**: 用户偏好页："CDL"：CDL 网表输出设置——Cadence 库名/路径、Include 文件、方括号转换（SimulationTool 的 CDL 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "CDL"；`init()`/`term()`/`reset()` 读写 SimulationTool。

### CIFTab
**文件**: `.../options/CIFTab.java`
**职责**: 混合页（用户+工程）："CIF"：输入把线方化（cifInSquaresWires，用户偏好）；输出模仿显示/合并盒/实例化顶层/比例因子（工程偏好 Setting）；并按技术列出所有层的 CIF 层名供编辑。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "CIF"；`init()` 建层列表与设置；`term()`/`reset()`。

### CVSTab
**文件**: `.../options/CVSTab.java`
**职责**: 用户偏好页："CVS"：启用 CVS、CVS 程序路径与仓库位置（CVS 类设置）；启用/关闭时自动给所有库添加/移除 CVSLibrary 并更新状态。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "CVS"；`init()`/`term()`/`reset()`。

### CellModelTab
**文件**: `.../options/CellModelTab.java`
**职责**: 用户偏好页（按 modelPrefs 类型实例化两次：Spice/Verilog）："Spice/Verilog Model Files"：为每个单元指定行为模型来源——无/模型文件/用版图网表/用 Verilog 网表（CellModelPrefs），列表+单选。
**主要方法**: `CellModelTab(PreferencesFrame parent, boolean modal, CellModelPrefs modelPrefs)`；`getUserPreferencesPanel()`；`getName()` 返回 "<类型> Model Files"；`init()`/`term()`/`reset()`。

### CompactionTab
**文件**: `.../options/CompactionTab.java`
**职责**: 用户偏好页："Compaction"：唯一选项 "Allow spreading"（Compaction.isAllowsSpreading）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Compaction"；`init()`/`term()`/`reset()` 读写 Compaction。

### ComponentMenuTab
**文件**: `.../options/ComponentMenuTab.java`
**职责**: 用户偏好页："Component Menu"：内嵌 dialogs.ComponentMenu 面板，把当前技术的组件菜单转换为 Xml.Technology 编辑；term() 时若 isSaveChanges 则 putPrefs 保存菜单 XML，若 isRedisplayMenu 则刷新所有窗口的 palette。
**主要方法**: 构造器（内嵌 ComponentMenu）；`getUserPreferencesPanel()` 返回 theMenu.getPanel()；`getName()` 返回 "Component Menu"；`init()`；`term()`；`resetThis()` 就地恢复出厂；`reset()`。

### CopyrightTab
**文件**: `.../options/CopyrightTab.java`
**职责**: 工程偏好页："Copyright"：为所有生成文件附加版权信息——开关（useCopyrightMessageSetting）+ 消息文本（copyrightMessageSetting，多行，无注释符）。
**主要方法**: 构造器；`getProjectPreferencesPanel()`；`getName()` 返回 "Copyright"；`init()`/`term()`/`reset()` 读写两个 Setting。

### CoverageTab
**文件**: `.../options/CoverageTab.java`（文件头注释误写 "NewArcsTab.java"）
**职责**: 用户偏好页："Coverage"：层覆盖率工具（LayerCoverageTool）设置——按技术/层编辑面积、宽高、deltaX/Y（LayerCoveragePreferences），切换层即时显示对应值。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Coverage"；`init()` 建技术/层列表；`term()`/`reset()`。

### DEFTab
**文件**: `.../options/DEFTab.java`
**职责**: 用户偏好页："DEF"：DEF 输入选项——物理/逻辑互连放置、纯层节点、忽略 SPECIALNETS/NETS 中互连、未知单元生成 dummy、忽略未生成引脚、忽略 vias block、连接全部引脚、未知层处理（IOTool 的 DEF 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "DEF"；`init()`/`term()`/`reset()`。

### DRCTab
**文件**: `.../options/DRCTab.java`
**职责**: 用户偏好页："DRC"：增量 DRC、拖动时交互式检查、错误检查级别、日志类型、最小面积算法、忽略中心切口/面积/延伸规则、日期存内存、交互式日志、清除有效日期、多线程（DRC.DRCPreferences）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "DRC"；`init()`/`term()`/`reset()`。

### DXFTab
**文件**: `.../options/DXFTab.java`
**职责**: 混合页（用户+工程）："DXF"：输入展平层次/读全部层（用户偏好）；Artwork 层的 DXF 层名（逗号分隔列表，输出用第一个）与 DXF 比例（工程偏好）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "DXF"；`init()`/`term()`/`reset()`。

### DaisTab
**文件**: `.../options/DaisTab.java`
**职责**: 用户偏好页："Dais"：Dais 格式输入选项——Display-only、读单元实例/细节线/全局线/电源地线/连接性（IOTool 的 Dais 系列设置；无 Dais 插件时全部禁用）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Dais"；`init()`/`term()`/`reset()`。

### DesignRulesTab
**文件**: `.../options/DesignRulesTab.java`
**职责**: 混合页（用户+工程）："Design Rules"：内嵌 DesignRulesPanel 编辑当前技术（+代工厂）的 DRC 规则，另设最小分辨率；term() 把规则与分辨率写回 DRC（putPrefs + DRC.setRules），reset() 恢复所有技术出厂规则。
**主要方法**: 构造器（内嵌 DesignRulesPanel）；`getUserPreferencesPanel()`；`getName()` 返回 "Design Rules"；`init()`/`term()`/`reset()`。

### DisplayControlTab
**文件**: `.../options/DisplayControlTab.java`
**职责**: 用户偏好页："Display Control"：分层光标坐标、侧栏在右、下钻时上层变暗、新窗口显示单元、错误高亮闪烁、窗口移到错误处、无模态对话框置顶、Cadence 风格测量、消息窗口停靠、平移距离、MDI/SDI 风格、屏幕缩放、显示算法（像素/矢量/层）、希腊化显示及阈值、图案缩放阈值、alpha 混合及阈值（User 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Display Control"；`init()`/`term()`/`reset()`。

### EDIFTab
**文件**: `.../options/EDIFTab.java`
**职责**: 用户偏好页："EDIF"：使用原理图视图、输入比例、拼接单元、Cadence 兼容、显示弧/节点名、配置文件、接受的参数列表（文本区逐行）+ 全部参数单选（IOTool 的 EDIF 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "EDIF"；`init()`/`term()`/`reset()`。

### FastHenryTab
**文件**: `.../options/FastHenryTab.java`
**职责**: 用户偏好页："Fast Henry"：单频/扫频（起止频率、每十倍频点数）、默认厚度、宽/高剖分数、最大段长（SimulationTool 的 FastHenry 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Fast Henry"；`init()`/`term()`/`reset()`。

### FrameTab
**文件**: `.../options/FrameTab.java`
**职责**: 用户偏好页："Frame"：帧（Frame）默认公司/设计者/工程名（User 全局默认），以及每个库各自的三个名字（存在库变量 FRAME_COMPANY_NAME/FRAME_DESIGNER_NAME/FRAME_PROJECT_NAME，TempPref 缓存初值）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Frame"；`init()`/`term()`/`reset()`。

### GDSTab
**文件**: `.../options/GDSTab.java`
**职责**: 混合页（用户+工程，最大的 IO 选项卡）："GDS"：输入合并盒/展开单元/简化单元/实例化阵列、阵列简化方式、未知层处理、Cadence 兼容、可读 dump（用户偏好）；按技术/层编辑 GDS 层号与类型、Pin/Text/HighVoltage 层号、单元名最大长度、默认文字层、输入比例、输出精度、输出单位/米、输出合并盒/导出引脚/大写/括号转换/折叠 Vdd-Gnd 名/写特性（工程偏好 Setting）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "GDS"；`init()`/`term()`/`reset()`。

### GateLayGenTab
**文件**: `.../options/GateLayGenTab.java`
**职责**: 工程偏好页："Gate Layout Generator"：门级版图生成器设置——代工厂、NCC 开关、尺寸量化误差、最大 MOS 宽度、Vdd/Gnd Y 位置、N/P 阱高度、简单命名（GateLayGenSettings 的 Setting，带 Help 按钮）。
**主要方法**: `GateLayGenTab(PreferencesFrame parent, boolean modal)`；`initializeFields()`；`getUserPreferencesPanel()` 返回 getContentPane()；`getName()` 返回 "Gate Layout Generator"。

### GeneralTab
**文件**: `.../options/GeneralTab.java`
**职责**: 用户偏好页："General"：文件对话框开关、各 FileTypeGroup 工作目录、当前工作目录、长任务蜂鸣、冗余模式、错误上限、撤销历史上限、日志（开关/多日志）、内存上限/永久代上限（带超限警告）、快照日志、客户端-服务器模式。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "General"；`init()`/`term()`/`reset()`。

### GerberTab
**文件**: `.../options/GerberTab.java`
**职责**: 用户偏好页："Gerber"：填充多边形、读目录中全部 .GBR 文件（IOTool）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Gerber"；`init()`/`term()`/`reset()`。

### GridAndAlignmentTab
**文件**: `.../options/GridAndAlignmentTab.java`
**职责**: 用户偏好页："Grid"：当前窗口网格间距（只读显示）、新建窗口默认网格间距与加粗频率、显示坐标轴、测量光标对齐网格，以及 5 组"对齐到网格"X/Y 尺寸（EditingPreferences 的 alignmentToGridVector）与当前选用的组。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Grid"；`init()`/`term()`/`reset()`。

### IconTab
**文件**: `.../options/IconTab.java`
**职责**: 用户偏好页："Icon"：图标生成——导出放置方式（按特性/按原单元位置+精确位置）、输入/输出/双向/电源导出放置的边、引脚长度/间距、图标文字字号；含 "Make Icon Now" 按钮即时生成当前单元图标。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Icon"；`init()`/`term()`/`reset()`。

### LEFTab
**文件**: `.../options/LEFTab.java`
**职责**: 用户偏好页："LEF"：忽略无位置引脚、忽略技术信息、未知层处理（IOTool 的 LEF 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "LEF"；`init()`/`term()`/`reset()`。

### LayersTab
**文件**: `.../options/LayersTab.java`
**职责**: 用户偏好页："Colors and Layers"：内嵌 ColorPatternPanel(true) 编辑各层颜色/图案（含打印选项）、透明层与特殊颜色；层排序方式下拉框（LayerSortingType）；`cacheLayerInfo(boolean)` 供 PreferencesFrame.updateLayerPreferencesColors 调用。
**主要方法**: 构造器（建排序框与颜色面板）；`getUserPreferencesPanel()`；`getName()` 返回 "Layers"；`init()`/`term()`/`reset()`；另有 `refresh()`（层排序变化时刷新，PreferencesFrame.updateLayersSortingTabs 调用）。

### LibraryTab
**文件**: `.../options/LibraryTab.java`
**职责**: 用户偏好页："Library"：库文件备份冗余——不备份/备份上一版/备份历史（IOTool.getBackupRedundancy 三级单选）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Library"；`init()`/`term()`/`reset()`。

### LogicalEffortTab
**文件**: `.../options/LogicalEffortTab.java`
**职责**: 工程偏好页："Logical Effort"：逻辑努力工具——本地设置开关、全局扇出、收敛阈值、最大迭代、keeper 尺寸比；按技术编辑门电容、默认线电容比、扩散-栅电容比（LETool 与技术的 Setting）。
**主要方法**: 构造器；`getUserPreferencesPanel()` 返回 null、`getProjectPreferencesPanel()` 返回面板；`getName()` 返回 "Logical Effort"；`init()`/`term()`/`reset()`。

### NCCTab
**文件**: `.../options/NCCTab.java`
**职责**: 用户偏好页："NCC"：网络比较——尺寸/电阻/体检查开关、相对/绝对尺寸容差、首错即停、跳过已通过、最大匹配/不匹配类数、每类最大成员数、操作方式（分层逐单元/展平顶层/列注释）、状态输出频率（NccPreferences）。
**主要方法**: `NCCTab(java.awt.Frame parent, boolean modal)`；`getUserPreferencesPanel()`；`getName()` 返回 "NCC"；`init()`/`term()`/`reset()`。

### NetworkTab
**文件**: `.../options/NetworkTab.java`
**职责**: 混合页（用户+工程）："Network"：总线升序/降序（NetworkTool）；节点抽取——网格对齐、active 处理、近似切口、忽略小多边形、最小多边形尺寸、单元展开模式、展平 P 单元、纯层节点（Extract）；忽略电阻、输出含日期版本号（工程偏好 Setting）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "Network"；`init()`/`term()`/`reset()`。

### NewArcsTab
**文件**: `.../options/NewArcsTab.java`
**职责**: 用户偏好页："Arcs"：新建弧的默认值——按技术/弧原语编辑宽度、角度、刚性、定角、可滑动、方向性、端点延伸、单弧/双弧名（ArcProto 的 defWidth 等默认属性，newArcsPrimDataChanged 即时写库）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Arcs"；`init()`/`term()`/`reset()`。

### NewNodesTab
**文件**: `.../options/NewNodesTab.java`
**职责**: 用户偏好页："Nodes"：新建节点的默认值——按技术/节点原语编辑默认 X/Y 尺寸（PrimitiveNode 默认尺寸），以及单元/节点通用复选：检查单元日期、自动切换技术、放置单元中心、重建被删单元的弧与导出、粘贴时转换原理图/版图、下钻提示索引、禁止改复杂/锁定原语、复制后移动、原位复制、复制导出、索引右增、抽取复制导出（User/EditingPreferences）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Nodes"；`init()`/`term()`/`reset()`。

### ParasiticTab
**文件**: `.../options/ParasiticTab.java`
**职责**: 混合页（用户+工程）："Parasitic"：寄生抽取——冗余名、回注版图、抽电源地、豁免网文件及忽略/抽取开关、抽 R/抽 C、最大距离（用户偏好，SimulationTool/ParasiticTool）；按技术/层编辑电阻、电容、边缘电容、电感面积/长度因子、最小电阻/电容、最大串联电阻、栅长扣除（工程偏好 Setting）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "Parasitic"；`init()`/`term()`/`reset()`。

### PlacementTab
**文件**: `.../options/PlacementTab.java`
**职责**: 用户偏好页："Placement"：选择放置算法（PlacementFrame 插件枚举）并动态显示其参数（PlacementParameter 按类型生成文本框/复选框/下拉框），通用选项为放置边距与放置后跑布线（Placement.PlacementPreferences）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Placement"；`init()`/`term()`/`reset()`。

### PortsAndExportsTab
**文件**: `.../options/PortsAndExportsTab.java`
**职责**: 用户偏好页："Ports/Exports"：端口与导出的显示方式（全名/短名/十字，GraphicsPreferences 的 portDisplayLevel/exportDisplayLevel）、移动节点时带动导出名。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Ports/Exports"；`init()`/`term()`/`reset()`。

### PrintingTab
**文件**: `.../options/PrintingTab.java`
**职责**: 用户偏好页："Printing"：打印/绘图设置——区域（整单元/高亮区/显示窗）、分辨率、封装 PostScript、角落日期、打印机/绘图仪、宽高与边距、线宽、旋转、黑白/彩色方式，以及 EPS 比例与同步文件（按单元记忆）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Printing"；`init()`/`term()`/`reset()`。

### ProjectManagementTab
**文件**: `.../options/ProjectManagementTab.java`
**职责**: 用户偏好页："Project Management"：项目仓库位置与用户名；低安全模式直接填用户名，高安全模式显示已登录用户与用户列表（Users 类，add/delete 需授权）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Project Management"；`init()`/`term()`/`reset()`。

### RoutingTab
**文件**: `.../options/RoutingTab.java`
**职责**: 用户偏好页："Routing"：缝合器（无/模仿/自动）；Sea-of-Gates 参数——最大弧宽、复杂度上限、最大距离、全局/脊骨布线、重跑失败、连接路由上运行、接触子单元策略、重跑复杂度、处理器数、并行开关；并按路由算法插件动态显示参数（RoutingFrame.RoutingPrefs）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Routing"；`init()`/`term()`/`reset()`。

### SUETab
**文件**: `.../options/SUETab.java`
**职责**: 用户偏好页："SUE"：SUE 输入——生成 4 端口晶体管、把 SUE 表达式转换为 Electric（IOTool）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "SUE"；`init()`/`term()`/`reset()`。

### SVGTab
**文件**: `.../options/SVGTab.java`
**职责**: 用户偏好页："SVG"：SVG 输出的比例因子与边距（IOTool）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "SVG"；`init()`/`term()`/`reset()`。

### ScaleTab
**文件**: `.../options/ScaleTab.java`
**职责**: 工程偏好页："Scale"：按技术列表编辑每技术的缩放因子（纳米/网格单位，tech.getScaleSetting()），输入即时更新列表显示并换算微米。
**主要方法**: 构造器；`getProjectPreferencesPanel()`；`getName()` 返回 "Scale"；`init()`/`reset()`（值随输入即时写入 Setting，无 term()）。

### SelectionTab
**文件**: `.../options/SelectionTab.java`
**职责**: 用户偏好页："Selection"：单元实例易选、拖动需包围整个对象、取消移动延迟（毫秒，ClickZoomWireListener）、鼠标悬停高亮、高亮相连对象、可选不可见层对象、布线模式（切换 ToolBar 光标，User 系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Selection"；`init()`/`term()`/`reset()`。

### SiliconCompilerTab
**文件**: `.../options/SiliconCompilerTab.java`
**职责**: 用户偏好页："Silicon Compiler"：硅编译器布局参数——行数、水平/垂直布线弧与线宽、电源线宽与主电源弧、P/N 阱高度与偏移、过孔尺寸、最小金属间距、馈通尺寸、最小端口/有源距离（SilComp.SilCompPrefs）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Silicon Compiler"；`init()`/`term()`/`reset()`。

### SimulatorsTab
**文件**: `.../options/SimulatorsTab.java`
**职责**: 用户偏好页："Simulators"：内置仿真器通用——每次修改重仿真、自动推进时间、多态波形显示；IRSIM 专用——显示命令、延迟 X、调试位开关（EV/DC/TAU/TAUP/SPK/TW）、参数文件、步进模型（RC/Linear）（SimulationTool 系列设置；无 IRSIM 时禁用）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Simulators"；`init()`/`term()`/`reset()`。

### SkillTab
**文件**: `.../options/SkillTab.java`
**职责**: 混合页（用户+工程）："Skill"：排除子单元、展平层次、GDS 名限制（用户偏好，IOTool）；按层编辑 Skill 层名（工程偏好，layer.getSkillLayerSetting()）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "Skill"；`init()`/`term()`/`reset()`。

### SmartTextTab
**文件**: `.../options/SmartTextTab.java`
**职责**: 用户偏好页："Smart Text"：弧与导出文字的智能放置——水平（内部/上方/下方）与垂直（内部/左侧/右侧）默认位置（EditingPreferences 的 SmartVertical/HorizontalPlacement* 系列）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Smart Text"；`init()`/`term()`/`reset()`。

### SpiceTab
**文件**: `.../options/SpiceTab.java`
**职责**: 用户偏好页："Spice/CDL"：SPICE 引擎（2/3/NG/H/P/G/S/O/Xyce/H-Assura/H-Calibre/J）、级别、电阻短接、寄生级别、全局处理方式、器件库文件、行宽上限、顶层写电源地子电路、单元参数、λ 尺寸、顶层子电路、末尾 .END、空子电路、新版 LTSpice、运行程序与参数、工作目录、头/尾卡片（SimulationTool 的 Spice 系列）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Spice/CDL"；`init()`/`term()`/`reset()`。

### SunRouterTab
**文件**: `.../options/SunRouterTab.java`
**职责**: 用户偏好页："Sun Router"：SunRouter 布线参数（全局布线瓦片/位尺寸、割线偏差、详细度；容量因子与窗口；ripup 增量/过载/代价；层分配参数；细节布线偏移/模数/阻塞因子、ripup 上限/惩罚/扩展/搜索、通道扩展、过孔区、路径搜索代价等，Routing 的 SunRouter 系列；无 SunRouter 时全禁用）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Sun Router"；`init()`/`term()`/`reset()`。

### TechnologyTab
**文件**: `.../options/TechnologyTab.java`
**职责**: 混合页（用户+工程）："Technology"：旋转版图晶体管（用户偏好）；原理图原语的 VHDL 输出名（GenerateVHDL.VHDLPreferences）；默认技术、原理图技术、P 衬底工艺版图技术、MOCMOS 规则集/金属层数/第二多晶/禁堆叠过孔/替代有源多晶规则/模拟（工程偏好 Setting），并可加载技术扩展插件的附加 Tab。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "Technology"；`init()`/`term()`/`reset()`。

### TextTab
**文件**: `.../options/TextTab.java`
**职责**: 用户偏好页："Text"：默认字体（GraphicsPreferences.defaultFont）、单元文字字体与字号、文字默认字号（点/单位）、全局缩放、文字窗口缩放、锚点、外部编辑器；对每种 TextDescriptor.TextType 维护可编辑的 MutableTextDescriptor（含高级显示控制），term() 写回 EditingPreferences 与 GraphicsPreferences。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Text"；`init()`/`term()`/`reset()`。

### ThreeDTab
**文件**: `.../options/ThreeDTab.java`
**职责**: 用户偏好页："3D"：层截面编辑——每层厚度与层间距（Layer 的 Setting），带截面示意图（threeDSideView 自绘）；`create3DTab(PreferencesFrame, boolean)` 静态工厂优先加载 3D 插件的 JThreeDTab，无插件时用内置实现。
**主要方法**: `create3DTab(PreferencesFrame parent, boolean modal)` (static)；构造器；`getUserPreferencesPanel()`；`getName()` 返回 "3D"；`init()`/`term()`/`reset()`。

### ToolbarTab
**文件**: `.../options/ToolbarTab.java`
**职责**: 用户偏好页："Toolbar"：工具栏定制——左侧命令树（CommandTree，按菜单结构），拖拽命令/分隔符到示例工具栏，拖到垃圾桶删除；保存后重建所有窗口工具栏；含 LanguageScripts 脚本列表入口。
**主要方法**: `ToolbarTab(PreferencesFrame parent, boolean modal, PreferencesFrame trueParent)`（三参构造器，PreferencesFrame 用 `new ToolbarTab(this, true, this)` 注册）；`getUserPreferencesPanel()`；`getName()` 返回 "Toolbar"；`init()`/`term()`/`reset()`；`valueChanged(TreeSelectionEvent)`。

### UnitsTab
**文件**: `.../options/UnitsTab.java`
**职责**: 用户偏好页："Units"：距离显示单位（可缩放/毫米/微米/纳米/皮米），预留了电阻/电容/电感/电流/电压/时间单位（OTHERUNITS=false 未启用）（User 的单位系列设置）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Units"；`init()`/`term()`/`reset()`。

### VerilogTab
**文件**: `.../options/VerilogTab.java`
**职责**: 混合页（用户+工程）："Verilog"：输入——导入后跑放置、生成版图单元；输出——标准单元不网表化、保留格式、模块名参数化、每图标一模块、网表化非标准单元、不含空模块（用户偏好，SimulationTool/IOTool）；工程偏好——使用 ASSIGN 结构、默认 wire 为 trireg（Setting）。
**主要方法**: 构造器；`getUserPreferencesPanel()` + `getProjectPreferencesPanel()`；`getName()` 返回 "Verilog"；`init()`/`term()`/`reset()`。

### WellCheckTab
**文件**: `.../options/WellCheckTab.java`
**职责**: 用户偏好页："Well Check"：阱检查——P 阱接触规则（全接触/至少 1 个/不检查）、P 阱必须接地、N 阱接触规则、N 阱必须接电源、最坏情况距离、顺带 DRC、并行分析及进程数（ERCWellCheck.WellCheckPreferences）。
**主要方法**: 构造器；`getUserPreferencesPanel()`；`getName()` 返回 "Well Check"；`init()`/`term()`/`reset()`。

---

## projectSettings 包

**位置**: `com/sun/electric/tool/user/projectSettings/`（2 个类）。该包不提供对话框，而是支撑 Preferences 对话框体系中的"工程偏好（Project Preferences）"持久化：工程偏好（Setting）可导出为 projsettings.xml 随项目分发，读库时再导入。

### ProjSettings
**文件**: `com/sun/electric/tool/user/projectSettings/ProjSettings.java`
**职责**: 工程偏好的全局单例管理器：以 ProjSettingsNode 树（按 Setting 的 XML 路径 "a.b.c" 分层）保存键值，提供导出（writeSettings/exportSettings，经 OpenFile 选文件）、导入（importSettings → ImportSettingsJob）、读取并提交到数据库（readSettings → commit 生成 Setting.SettingChangeBatch，allowOverride 控制冲突时覆盖还是保留并警告）；PreferencesFrame 与 OptionReconcile 都引用它。
**主要方法**:
- `getSettings()` (static) — 取单例。
- `putValue(String xmlPath, Object value)` / `getValue(String xmlPath)` — 按 XML 路径写/读节点树。
- `putAllSettings(Map<Setting,Object> settings)` — 批量放入。
- `writeSettings(Map<Setting,Object> addSettings, File file)` (static) — 序列化到文件。
- `getLastProjectSettingsFile()` (static) — 上次读/写的工程设置文件。
- `readSettings(File file, EDatabase database, boolean allowOverride, boolean disablePopups)` (static) — 读文件、对比并提交差异。
- `exportSettings()` / `importSettings()` (static) — 带文件对话框的导出/导入。
- `ImportSettingsJob` (public static class extends Job) — 后台执行导入。

### ProjSettingsNode
**文件**: `com/sun/electric/tool/user/projectSettings/ProjSettingsNode.java`
**职责**: 工程偏好树的节点（包级类，implements Serializable）：持有 LinkedHashMap<String,Object> 键值（值可为基本类型或子节点），键顺序即写入文件顺序（确定性）；路径用 "." 连接；提供节点/键遍历、按路径取子节点（自动创建）、整树比较与差异打印（printDifferences 递归输出冲突详情，供 readSettings 冲突报告使用）。
**主要方法**:
- `ProjSettingsNode()` — 根节点构造。
- `getPath()` — 从根到本节点的路径。
- `getKeys()` — 键集合（插入序）。
- `getNode(String key)` — 取/创建子节点。
- `get(String key)` / `put(String key, Object node)` (protected) — 键值存取。
- `equals(Object node)` — 两树相等比较。
- `printDifferences(Object node)` — 打印两树差异（返回是否有差异）。
- `describeContext(Stack<String> context)` (static) — 把键栈格式化为 "a.b.c" 路径字符串。
