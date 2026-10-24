# 窗口部件（ToolBar / StatusBar / LayerTab / PaletteFrame 等）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.ui。本文件由 AI 生成。

## 模块概述

这些部件是 Electric 主窗口的"外围装饰"，由 WindowFrame（SDI 单文档窗口）和 TopLevel（MDI 顶层窗口）负责组装：

- **ToolBar** 由 `TopLevel` 通过 `ToolBar.createToolBar()` 创建（TopLevel.java L193），作为 JToolBar 挂在窗口顶部；SDI 模式下每个 WindowFrame 的 JFrame 也各有一个（`TopLevel.getToolBars()` 遍历所有窗口的工具栏）。
- **StatusBar** 在 `TopLevel` 构造时以 `new StatusBar(frame)` 创建（TopLevel.java L197），置于编辑窗口底部，显示选中对象、单元尺寸、技术、坐标、层次坐标等信息。
- **LayerTab** 与 **PaletteFrame** 由 WindowFrame 构造时创建（WindowFrame.java L422-423），分别装入窗口侧边栏 `JTabbedPane sideBar` 的 "Layers" 与 "Components" 标签页（WindowFrame.java L426-428），侧边栏通过 JSplitPane 放在内容区的左侧或右侧（可在偏好中设置）。
- **PaletteFrame** 内部持有 `TechPalette`（组件面板，绘制当前技术下的节点/弧）与技术下拉框；**LibraryPalette** 是另一类可放入 PaletteFrame 的面板（按库列出单元），但 9.08.1 中主要由 TechPalette 承担组件菜单职责。
- **LayerVisibility** 是 LayerTab 的数据后端（图层可见性/高亮/透明度/排序的缓存与偏好持久化）；**ToolBarButton** 是 SDI 模式下跨窗口同步按钮状态的按钮类。
- 这些部件全部通过静态方法（如 `StatusBar.updateStatusBar()`、`ToolBar.updateToolBarButtons()`）互相通知刷新，实现 MDI/SDI 双模式下的界面一致。

---

## ToolBar

**文件**: `com/sun/electric/tool/user/ui/ToolBar.java`（1417 行）
**职责**: 管理 Electric 工具栏：维护按钮顺序（可持久化到偏好设置）、创建按钮对应的 Swing 组件、切换光标模式（编辑/平移/缩放/轮廓/测量）、切换选择模式，并提供网格间距下拉按钮与单元历史前进/后退按钮。
**继承/实现**: `public class ToolBar extends JToolBar`
**关键字段**:
- `unknownIcon (private static ImageIcon)` — 找不到图标时使用的默认图标（ButtonUnknown.gif）
- `currentToolbarButtons (private static EToolBarButton[])` — 当前工具栏按钮顺序（所有窗口共享）
- `allButtons (private static List<EToolBarButton>)` — 所有已知工具栏按钮列表（懒初始化）
- `commandToIconMap (private static Map<String,String>)` — 命令名到磁盘图标文件的映射
- `curMode (private static CursorMode)` — 当前光标模式，默认 CLICKZOOMWIRE
- `zoomCursor / zoomOutCursor / panCursor / wiringCursor / outlineCursor / measureCursor / routingCursor / hardSelectCursor / areaSelectCursor (static final Cursor)` — 各模式的定制光标
- `curSelectMode (private static SelectMode)` — 当前选择模式（OBJECTS/AREA）
- `selectSpecial (private static boolean)` — "特殊选择" 开关状态
- `currentGridAmount (private JButton)` — 显示当前网格间距、点击弹出对齐级别菜单的按钮
- `goBackButton / goForwardButton (private CellHistoryButton)` — 每个窗口独立的单元历史前进/后退按钮

### 方法

#### `public static ToolBar createToolBar()`
- 行号: L86
- 描述: 工厂方法，创建新的工具栏实例。

#### `private ToolBar()`
- 行号: L88
- 描述: 私有构造器：设置可浮动、可 rollover、不可聚焦，并按当前配置重建按钮。

#### `public static EToolBarButton[] getFactoryButtons()`
- 行号: L100
- 描述: 返回"出厂默认"工具栏按钮数组（null 元素表示分隔符）。

#### `public static List<EToolBarButton> getAllButtons()`
- 行号: L139
- 描述: 返回所有已知工具栏按钮列表，首次调用时填充。

#### `public static EToolBarButton[] getToolbarButtons()`
- 行号: L170
- 描述: 返回当前工具栏按钮数组；尚未设置时取默认值（getDefaultToolbarButtons）。

#### `public static ImageIcon getUnknownIcon()`
- 行号: L181
- 描述: 返回未知图标。

#### `public static void setToolbarButtons(EToolBarButton[] buttons)`
- 行号: L189
- 描述: **`[重要]`** 设置工具栏按钮顺序：序列化为 `B=图标名` / `U=命令名=U=图标路径` 字符串存入 `User.toolbarOrderPref`，并立即重建 MDI 或所有 SDI 窗口的工具栏。

#### `public static ImageIcon getProperSizeIcon(String fileName)`
- 行号: L243
- 描述: 从磁盘读取图像文件并缩放为高度不超过 16 的工具栏图标；读取失败返回 null 并打印错误。

#### `public static Map<String,String> getCommandToIconMap()`
- 行号: L289
- 描述: 返回命令名到图标文件的映射；从 `User.toolbarFilesPref` 偏好字符串解析（懒初始化）。

#### `public static void setCommandToIconMap(Map<String,String> newMap)`
- 行号: L312
- 描述: 设置命令名到图标文件的映射并序列化保存到偏好。

#### `private static EToolBarButton[] getDefaultToolbarButtons()`
- 行号: L332
- 描述: 从偏好读取上次保存的按钮顺序（B= 内置、U= 用户自定义）；解析失败时回退出厂设置并清空偏好。

#### `private static EToolBarButton makeButtonFromMenuItem(EMenuItem mi, String command, String menu)`
- 行号: L416
- 描述: 由菜单项生成用户自定义按钮（run 委托给菜单项）。

#### `private void redoToolbar()`
- 行号: L426
- 描述: 清空并按当前按钮配置重建工具栏：null 生成分隔符，静态的 goBack/goForward 占位按钮替换为本窗口实例，并插入网格间距按钮。

#### `private void chooseGridAmount()`
- 行号: L460
- 描述: 在网格间距按钮位置弹出菜单，提供 5 个网格对齐级别和 "Grid Preferences..." 入口。

#### `private static void rewriteGridDistance()`
- 行号: L487
- 描述: 重写 MDI 或所有 SDI 窗口工具栏上网格间距按钮的文本（如 " 0.5/0.25 "）。

#### `private static EMenuItem findMenuItem(EMenu menu, String menuName, String command)`
- 行号: L517
- 描述: 按完整命令名（菜单路径:条目名）递归查找菜单项。

#### 内部类 `EToolBarButton`
- 行号: L539（`public abstract static class EToolBarButton extends EMenuItem`）
- 描述: 通用工具栏按钮，桥接菜单项与 Swing 按钮。
- 字段: `defaultIcon (private ImageIcon)` — 按钮当前图标；`iconName (private String)` — 内置图标文件名（无扩展名）；`menuName (private String)` — 所属菜单名；`fullPathToIcon (private String)` — 用户自定义图标的完整路径。

##### `EToolBarButton(String text, KeyStroke accelerator, String iconName, String menuName)`
- 行号: L555
- 描述: 构造器，加载 `iconName + ".gif"` 资源作为默认图标。

##### `EToolBarButton(String text, char acceleratorChar, String iconName, String menuName)`
- 行号: L570
- 描述: 构造器（以字符形式指定快捷键）。

##### `public String getMenuName()` / `public ImageIcon getIcon()`
- 行号: L583 / L589
- 描述: 简单 getter：返回所属菜单名 / 当前图标。

##### `public void setIcon(ImageIcon i, String path)`
- 行号: L596
- 描述: 设置按钮图标及其文件路径。

##### `public AbstractButton genToolBarButton()`
- 行号: L606
- 描述: **`[重要]`** 生成 Swing 按钮组件：设置 tooltip、图标、注册自身为 ActionListener，并调用 updateToolBarButton 同步状态。

##### `AbstractButton createToolBarButton()`
- 行号: L616
- 描述: 创建底层按钮，默认返回 JButton；子类（如单选按钮）可重写。

##### `void updateToolBarButton(AbstractButton item)`
- 行号: L621
- 描述: 更新按钮的 enabled/selected/tooltip 以匹配菜单项状态。

##### `protected void registerItem()` / `protected void updateButtons()`
- 行号: L629 / L636
- 描述: 注册时同时注册为可更新项；按钮状态变化时调用 updateToolBarButtons 全量刷新。

#### 内部类 `EToolBarGeneralMenuButton`
- 行号: L645（`public static class ... extends EToolBarButton`）
- 描述: 委托给任意 EMenuItem 的通用按钮。

##### `public EToolBarGeneralMenuButton(String text, String iconName, String menuName, EMenuItem item)` / `public void run()`
- 行号: L649 / L655
- 描述: 构造器保存目标菜单项；run 直接调用 `item.run()`。

#### 内部类 `EToolBarRadioButton`
- 行号: L661（`private abstract static class ... extends EToolBarButton`）
- 描述: 通用工具栏单选按钮（光标模式/选择模式按钮的基类）。

##### 构造器 `EToolBarRadioButton(...)`
- 行号: L663、L666
- 描述: 两个构造器，分别接收 KeyStroke 或 char 快捷键。

##### `protected JMenuItem createMenuItem()` / `JToggleButton createToolBarButton()`
- 行号: L669 / L676
- 描述: Mac 上菜单项用 JMenuItem，其余平台用 JRadioButtonMenuItem；工具栏侧一律用 JToggleButton。

#### 静态按钮实例
- `openLibraryCommand (public static final EToolBarButton)` — L681，运行 `FileMenu.openLibraryCommand()`
- `saveLibraryCommand (public static final EToolBarButton)` — L686，运行 `FileMenu.saveLibraryCommand(Library.getCurrent())`；无当前库时禁用
- `preferencesCommand (public static final EToolBarButton)` — L1136，打开偏好设置
- `expandOneLevelCommand / unexpandOneLevelCommand (public static final EToolBarButton)` — L1141 / L1147，调用 `CircuitChanges.DoExpandCommands` 展开/收起一层
- `undoCommand / redoCommand (public static final EToolBarButton)` — L1155 / L1226，见下
- `goBackButtonStatic / goForwardButtonStatic (private static final EToolBarButton)` — L1264 / L1267，静态占位（每个工具栏替换为实例按钮）

#### `public static void setSaveLibraryButton()`
- 行号: L693
- 描述: 刷新工具栏按钮状态（保存库按钮可用性）。

#### `public static enum CursorMode`
- 行号: L705
- 描述: 类型安全枚举，描述光标交互模式：CLICKZOOMWIRE（默认，普通编辑）、PAN、ZOOM、OUTLINE（轮廓编辑）、MEASURE（测距）、ROUTING（布线）。

#### `public static Cursor readCursor(String cursorName, int hotX, int hotY)`
- 行号: L726
- 描述: 从资源加载光标图像，并按系统最佳光标尺寸缩放或补白后创建定制光标。

#### `public static CursorMode getCursorMode()`
- 行号: L765
- 描述: 返回当前光标模式。

#### `public static void setCursorMode(CursorMode cm)`
- 行号: L775
- 描述: **`[重要]`** 设置光标模式（changeCursorMode）并刷新所有工具栏按钮的选中状态。

#### `private static void changeCursorMode(CursorMode cm)`
- 行号: L781
- 描述: **`[重要]`** 核心模式切换：安装对应监听器（ClickZoomWireListener / ZoomAndPanListener / OutlineListener / MeasureListener）、设置光标；对 PAN/ZOOM/OUTLINE 的重复点击会回退到 CLICKZOOMWIRE，并尝试恢复之前被临时替换的监听器。

#### `private static void checkLeavingOutlineMode()`
- 行号: L908
- 描述: 退出轮廓编辑模式时关闭特殊显示并重绘窗口。

#### 内部类 `CursorModeButton`
- 行号: L944（`private static class ... extends EToolBarRadioButton`）
- 描述: 光标模式单选按钮，isSelected 比较 `getCursorMode()` 与目标模式。

##### `CursorModeButton(...)` / `public boolean isSelected()` / `public void run()`
- 行号: L947 / L952 / L953
- 描述: 构造器记录目标 CursorMode；选中状态来自当前模式；run 调用 changeCursorMode。

#### `public static void setGridAligment()`
- 行号: L961
- 描述: 网格对齐变化时通知工具栏刷新按钮状态。

#### 私有网格命令字段
- `gridDistance1Command` 到 `gridDistance5Command (private static EMenuItem)` — L963-972，调用 `changeGridSize(0..4)`
- `gridLarger (private static final EToolBarButton)` — L976，快捷键 F，调用 `changeGridSize(true)`；已在最大网格时禁用
- `gridSmaller (private static final EToolBarButton)` — L987，快捷键 H，调用 `changeGridSize(false)`；已在最小网格时禁用

#### `private static void changeGridSize(int size)` / `private static void changeGridSize(boolean larger)`
- 行号: L999 / L1007
- 描述: 按索引直接设置网格对齐级别 / 按方向增大或减小一级，然后重写按钮文本并刷新按钮。

#### `public static enum SelectMode`
- 行号: L1036
- 描述: 选择模式枚举：OBJECTS（点选对象）、AREA（区域选择）。

#### `public static SelectMode getSelectMode()` / `private static void setSelectMode(SelectMode selectMode)`
- 行号: L1046 / L1048
- 描述: getter / setter（setSelectMode 为私有，仅 SelectModeButton 调用）。

#### 内部类 `SelectModeButton`
- 行号: L1055（`public static class ... extends EToolBarRadioButton`）
- 描述: 选择模式单选按钮。

##### `SelectModeButton(...)` / `public boolean isSelected()` / `public void run()`
- 行号: L1059 / L1065 / L1066
- 描述: run 设置选择模式并把光标模式切回 CLICKZOOMWIRE。

#### `public static boolean isSelectSpecial()` / `private static void setSelectSpecial(boolean b)`
- 行号: L1077 / L1083
- 描述: 查询 / 切换"特殊选择"状态；切换后重设光标（特殊选择使用硬选择光标）。

#### 字段 `toggleSelectSpecialCommand`
- 行号: L1092（private static EToolBarButton）
- 描述: 特殊选择切换按钮：菜单项用 JCheckBoxMenuItem，工具栏用 JToggleButton，按选中状态切换 On/Off 图标。

#### 字段 `modesSubMenu`
- 行号: L1110（public static final EMenu）
- 描述: Edit 菜单的 Modes 子菜单树（Edit / Movement / Select 三个子菜单），供菜单系统挂载。

#### 字段 `undoCommand` / `redoCommand` 的 run()
- 行号: L1157-1222 / L1228-1253
- 描述: **`[重要]`** Undo：文本窗口调用 `tw.undo()`；EditWindow 有 in-place 文本编辑时撤销该编辑；有 change job 运行或撤销 Read/Write 任务时弹确认框；最后调用 `Undo.undo()`。Redo 逻辑类似。两者的 isEnabled 分别来自 `UserInterfaceMain.getUndoEnabled()` / `getRedoEnabled()`。

#### `public static void updateUndoRedoButtons(boolean undo, boolean redo)`
- 行号: L1257
- 描述: 撤销/重做可用性变化时刷新工具栏按钮。

#### 内部类 `CellHistoryButton`
- 行号: L1292（`private abstract static class ... extends EToolBarButton implements MouseListener`）
- 描述: 单元历史前进/后退按钮基类，右键弹出历史列表。

##### `CellHistoryButton(...)` / `public AbstractButton genToolBarButton()` / `public boolean isEnabled()` / `void setEnabled(boolean b)`
- 行号: L1295 / L1297 / L1304 / L1305
- 描述: 构造器；生成按钮时额外注册鼠标监听器；可用状态存于私有 enabled 字段。

##### `public void mouseReleased(MouseEvent e)`
- 行号: L1311
- 描述: 右键在按钮上释放时弹出单元历史菜单（showHistoryPopup）。

#### `public void updateCellHistoryStatus(boolean backEnabled, boolean forwardEnabled)`
- 行号: L1324
- 描述: **`[重要]`** 更新本工具栏前进/后退按钮的可用状态并刷新。

#### `private static void showHistoryPopup(MouseEvent e)`
- 行号: L1331
- 描述: 弹出当前窗口的单元浏览历史（去重、标记当前项），点击项通过 HistoryPopupAction 跳到对应单元。

#### `private static class HistoryPopupAction implements ActionListener`
- 行号: L1364
- 描述: 弹出菜单项动作，调用 `wf.setCellByHistory(historyLocation)`。

#### `public static void updateToolBarButtons()`
- 行号: L1383
- 描述: **`[重要]`** 刷新 MDI/所有 SDI 窗口工具栏上每个按钮的 enabled/selected/tooltip 状态（遍历 `TopLevel.getToolBars()` 中按钮的 ActionListener），并同步刷新菜单栏。

#### `public void finished()`
- 行号: L1416
- 描述: 释放资源（当前为空实现）。

---

## ToolBarButton

**文件**: `com/sun/electric/tool/user/ui/ToolBarButton.java`（302 行）
**职责**: SDI 模式下用于工具栏的按钮类：按名称同步多个工具栏上同名按钮的选中状态，使各窗口界面保持一致；默认是 toggle 按钮。
**继承/实现**: `public class ToolBarButton extends AbstractButton implements Accessible, ActionListener`
**关键字段**:
- `allButtons (private static HashMap<String,List<AbstractButton>>)` — 按名称分组的所有按钮实例
- `updater (public static ToolBarButton)` — 用于跨按钮同步状态的静态监听器（dummy 实例）
- `name (private String)` — 按钮名称（必须唯一）
- `buttonLogger (private static final ButtonLogger)` — 把按钮点击写入活动日志
- `uiClassID (private String)` — 默认 "ToggleButtonUI"

### 方法

#### `private ToolBarButton(String text, Icon icon)`
- 行号: L82
- 描述: 私有构造器：使用 `JToggleButton.ToggleButtonModel`，初始化名称。

#### `public String getName()`
- 行号: L91
- 描述: 返回按钮名称。

#### `public static ToolBarButton newInst(String text, Icon icon)`
- 行号: L103
- 描述: **`[重要]`** 工厂方法：创建按钮、注册 updater 和 buttonLogger 监听器，并把实例登记进 allButtons 分组。

#### `public void addActionListener(ActionListener l)`
- 行号: L123
- 描述: 注册监听器，保证 buttonLogger 始终位于列表末尾（最先被激活）。

#### `public void actionPerformed(ActionEvent e)`
- 行号: L133
- 描述: 点击事件：把选中状态同步到所有同名按钮（多工具栏一致性）。

#### `public static void setIconForButton(String name, Icon icon)`
- 行号: L159
- 描述: 为所有同名按钮设置新图标。

#### `public static boolean getButtonState(String name)`
- 行号: L173
- 描述: 返回指定名称按钮的选中状态（取组内第一个实例）。

#### `public static void doClick(String name)`
- 行号: L184
- 描述: 模拟点击指定名称的按钮。

#### `public void finished()`
- 行号: L196
- 描述: SDI 顶层窗口销毁时调用：移除全部监听器并从登记表删除自身，以便 GC 回收。

#### 内部类 `ButtonLogger implements ActionListener`
- 行号: L210
- 描述: 把按钮激活事件写入 ActivityLogger（`logToolBarButtonActivated`）。

#### `public void updateUI()`
- 行号: L225
- 描述: 从当前 L&F 重置 UI。

#### `public String getUIClassID()`
- 行号: L241
- 描述: 返回 L&F 类 ID（"ToggleButtonUI"）。

#### `public AccessibleContext getAccessibleContext()`
- 行号: L264
- 描述: 返回无障碍上下文（懒创建 AccessibleJButton）。

#### 内部类 `AccessibleJButton extends AccessibleAbstractButton`
- 行号: L288
- 描述: JButton 无障碍接口的复制实现，角色为 PUSH_BUTTON。

---

## StatusBar

**文件**: `com/sun/electric/tool/user/ui/StatusBar.java`（590 行）
**职责**: 管理编辑窗口底部的状态栏：显示选中对象信息（数量/名称/尺寸）、单元尺寸（SIZE）、技术（TECH）、鼠标坐标与层次坐标；支持保存/恢复选择集；debug 模式下显示内存使用。
**继承/实现**: `public class StatusBar extends JPanel implements HighlightListener, DatabaseChangeListener`
**关键字段**:
- `frame (private WindowFrame)` — 所属窗口（SDI 模式使用）
- `coords / hierCoords (private String)` — 普通坐标与层次坐标文本（由外部静态方法写入）
- `fieldSelected / fieldSize / fieldTech / fieldCoords / fieldHierCoords / memoryUsage (private JLabel)` — 六个显示字段
- `selectionOverride (private static String)` — 覆盖"选中对象"字段的临时文本（如 TechPalette 悬停提示）
- `allSelections (private static List<SavedSelection>)` — 已保存的选择集列表

### 方法

#### `public StatusBar(WindowFrame frame)`
- 行号: L82
- 描述: 构造器：GridBagLayout + 凹陷 BevelBorder；依次添加选择/尺寸/技术/坐标标签（竖分隔条隔开）、第二行层次坐标标签，debug 模式加内存标签；注册 Highlight 与 DatabaseChange 监听器。

#### 内部类 `SavedSelection`
- 行号: L151
- 描述: 保存的选择集：名称 + `List<Highlight>`。

#### `private void doSelectionPopup()`
- 行号: L162
- 描述: 点击选择字段弹出菜单：列出已保存选择、清除全部、保存当前选择。

#### 内部类 `SaveSelection / ClearAllSelections / RestoreSelection`
- 行号: L188 / L211 / L224
- 描述: 三个 ActionListener：把当前高亮保存为选择集；清空全部选择集；恢复某选择集（必要时打开/激活对应 EditWindow 并 showHighlights）。

#### `private void addField(JComponent field, int x, int y, int width, double weight)`
- 行号: L255
- 描述: 按 GridBagConstraints 把字段加入状态栏（Mac 平台调整右边距）。

#### `private Highlighter getHighlighter()`
- 行号: L272
- 描述: 根据 MDI/SDI 模式取得当前窗口的 Highlighter。

#### `public static void setCoordinates(String coords, WindowFrame wf)` / `public static void setHierarchicalCoordinates(String hierCoords, WindowFrame wf)`
- 行号: L283 / L294
- 描述: 静态入口：把坐标文本写入当前 MDI 顶层或指定窗口的状态栏并刷新。

#### `public static void setSelectionOverride(String ov)`
- 行号: L305
- 描述: 设置选择字段的覆盖文本（null 恢复正常显示）并刷新所有状态栏。

#### `public void highlightChanged(Highlighter which)` / `public void highlighterLostFocus(Highlighter highlighterGainedFocus)`
- 行号: L311 / L321
- 描述: HighlightListener 回调：高亮变化时更新选择文本；失焦回调为空实现。

#### `public static void updateStatusBar()`
- 行号: L328
- 描述: **`[重要]`** 刷新 MDI 当前窗口或所有 SDI 窗口的状态栏。

#### `private void updateUsedMemory()`
- 行号: L347
- 描述: 更新内存使用标签（MemoryUsage 单例）。

#### `private void redoStatusBar()`
- 行号: L353
- 描述: **`[重要]`** 重绘本状态栏：更新选择文本、（debug）内存、SIZE（文本窗口显示行数，单元显示边界尺寸）、TECH（技术名 + 可选 scale/foundry）、坐标与层次坐标（超宽时截断）。

#### `private void updateSelectedText()`
- 行号: L423
- 描述: 统计当前高亮的节点/弧/文本数量生成选择信息；单个对象时显示详细描述（含节点尺寸）；多个时显示计数并附最后一个对象的描述。

#### `private String addLayerInfo(PortProto pp)`
- 行号: L504
- 描述: 为端口描述附加可连接层信息（通用端口显示 [ALL]）。

#### `private String getSelectedText(Highlight h)`
- 行号: L533
- 描述: 生成单个高亮对象的描述文本：端口 → "NODE: ... PORT: '...'"；节点 → "NODE: ..."；弧 → 网络信息 + "ARC: ..."；文本 → "TEXT: ..."。

#### `public void finished()`
- 行号: L580
- 描述: 清理：移除 Highlight/DatabaseChange 监听器引用。

#### `public void databaseChanged(DatabaseChangeEvent e)`
- 行号: L587
- 描述: 数据库变化时重绘状态栏。

---

## LayerTab

**文件**: `com/sun/electric/tool/user/ui/LayerTab.java`（1305 行）
**职责**: 窗口侧边栏的 "Layers" 标签页：列出当前技术的所有图层（勾选=可见）、控制图层高亮与透明度（layerDrawing 模式）、管理文本可见性复选框、管理"可见性配置"（保存/应用/重命名/删除），并支持在图层面板内拖拽重排图层顺序。
**继承/实现**: `public class LayerTab extends JPanel implements DragSourceListener, DragGestureListener`
**关键字段**:
- `layerList / configurationList (private final JList<String>)` — 图层列表、可见性配置列表
- `layerListModel / configurationModel (private final DefaultListModel<String>)` — 两个列表的模型
- `layersInList (private List<Layer>)` — 与图层列表行一一对应的 Layer 对象
- `dragSource (private final DragSource)` — 图层列表拖拽源
- `loading (private boolean)` — 加载技术期间抑制刷新的标志
- `layerDrawing (private boolean)` — 是否处于图层绘制（透明度）模式
- `lv (private LayerVisibility)` — 图层可见性后端
- `invLayerConfigs (private final InvisibleLayerConfiguration)` — 不可见层配置单例
- GUI 组件（NetBeans 生成，L1280-1302）: `technology (JComboBox<String>)`、`layerPane / configurationPane (JScrollPane)`、`makeVisible / makeInvisible / selectAll / toggleHighlight / unhighlightAll / resetOpacity / newConfiguration / setConfiguration / renameConfiguration / deleteConfiguration (JButton)`、`nodeText / arcText / portText / exportText / annotationText / cellText (JCheckBox)`、`opacitySlider (JSlider)`、`jPanel1/jPanel2/jPanel3`

### 方法

#### `public LayerTab(WindowFrame wf)`
- 行号: L95
- 描述: 构造器：初始化 GUI、设置 4 个配置按钮图标、构建图层/配置两个 JList（单击/双击监听）、配置拖拽与 DropTarget、给文本复选框和透明度滑杆挂监听器、加载技术并注册 `CurTechControlListener`。

#### `public void finished()`
- 行号: L189
- 描述: 释放资源（清空 layersInList）便于 GC。

#### `public void loadTechnologies(boolean makeCurrent)`
- 行号: L200
- 描述: **`[重要]`** 重建技术下拉框（列出所有技术并保持当前选择）；初始化或新技术创建时调用。

#### `private void renameConfiguration()`
- 行号: L219
- 描述: 重命名选中的可见性配置（弹输入框，重名校验）。

#### `private void setConfiguration()`
- 行号: L243
- 描述: 把当前图层可见性保存到选中的配置。

#### `private void newConfiguration()`
- 行号: L258
- 描述: 以当前可见性创建新配置（弹输入框）。

#### `private void updateConfiguration(String cName, int hardWiredIndex)`
- 行号: L271
- 描述: 收集当前不可见层，调用 `invLayerConfigs.addConfiguration` 保存。

#### `private void deleteThisConfiguration()`
- 行号: L287
- 描述: 删除选中的可见性配置。

#### `private void useConfiguration(MouseEvent e)`
- 行号: L309
- 描述: 配置列表双击事件：应用该配置。

#### `public void setInvisibleLayerConfiguration(String cName)`
- 行号: L325
- 描述: **`[重要]`** 应用不可见层配置到当前技术；配置为"硬连线"金属层数时改用 `setVisibilityLevel`。

#### `private void showConfigurations()`
- 行号: L353
- 描述: 填充配置列表：普通配置按技术过滤，硬连线配置在当前技术适用时显示并以 "* " 前缀标记。

#### `private void setSelectedTechnology(Technology tech)`
- 行号: L378
- 描述: 设置技术下拉框选中项（相同则跳过）。

#### `public void setDisplayAlgorithm(boolean layerDrawing)`
- 行号: L391
- 描述: 切换图层绘制（透明度）模式，变化时刷新面板。

#### `public void updateLayersTab()`
- 行号: L403
- 描述: **`[重要]`** 刷新整个面板：同步 6 个文本可见性复选框、重建图层列表（按保存顺序排列，dummy 层置后，layout 技术追加 drc/route/afg 等特殊层）、按需显示透明度控件、刷新配置列表。

#### `private String lineName(Layer layer)`
- 行号: L466
- 描述: 生成图层列表行文本："✓/空格 + 层名 + (HIGHLIGHTED) + (透明度)"。

#### `private void apply(MouseEvent e)`
- 行号: L486
- 描述: 图层列表点击：单选时把该层透明度同步到滑杆；双击切换所有选中行的可见性。

#### `private void sliderChanged()`
- 行号: L520
- 描述: 透明度滑杆变化：更新选中层的透明度并刷新该行文本，然后通知各窗口。

#### `private void opacityChanged()`
- 行号: L537
- 描述: 通知所有 EditWindow 透明度已变化（`wnd.opacityChanged()`）并重绘。

#### `private void clearAllHighlight()` / `private void toggleHighlight()`
- 行号: L555 / L563
- 描述: 清除全部图层高亮 / 切换选中图层高亮。

#### `private void selectAll()`
- 行号: L576
- 描述: 选中全部图层行。

#### `private void setVisibility(boolean on)`
- 行号: L588
- 描述: 把选中图层统一设为可见/不可见并更新显示。

#### `private boolean isLineChecked(int i)` / `private Layer getSelectedLayer(int i)`
- 行号: L598 / L605
- 描述: 判断某行是否勾选（首字符非空格）；由列表行文本解析出 Layer 对象（先当前技术后 Generic 查找）。

#### `private void setVisibility(int[] indices, boolean[] visible)`
- 行号: L628
- 描述: 批量设置指定行图层的可见性并刷新列表行文本。

#### `public void setVisibilityLevel(Technology tech, int level)`
- 行号: L658
- 描述: **`[重要]`** 按金属层级别设置可见性：level 0 全开；否则按层功能级别（contact/diff/gate/metal/poly/well 等）计算每个图层是否可见，同时处理 ART/OVERGLASS 等无级别层。

#### `private void changeHighlighted(int i, int how)`
- 行号: L744
- 描述: 改变某行图层的高亮状态（how=1 切换，0 清除）并更新显示。

#### `private void update()`
- 行号: L762
- 描述: **`[重要]`** 提交变化：读取 6 个文本可见性复选框更新 GraphicsPreferences；同步其他窗口的 LayerTab；可见性或文本可见性变化时调用 `layerVisibilityChanged`。

#### `private static void layerVisibilityChanged(LayerVisibility lv, boolean onlyText)`
- 行号: L795
- 描述: 可见层变化时清空 VectorCache 的淡化图与子单元缓存，并调用 `EditWindow.setLayerVisibilityAll(lv)`。

#### 拖拽源实现
- `public void dragGestureRecognized(DragGestureEvent dge)` — L805，开始拖拽（携带选中行索引字符串）
- `public void dragEnter / dragExit / dragOver / dragDropEnd / dropActionChanged` — L812-824，空实现

#### 内部类 `LayerTabTreeDropTarget implements DropTargetListener`
- 行号: L830
- 描述: 图层列表的 DropTarget：拖过时画红色目标线，drop 时按索引重排 `layersInList` 并保存顺序到 LayerVisibility。

##### `public void drop(DropTargetDropEvent dtde)`
- 行号: L875
- 描述: **`[重要]`** 处理图层拖放重排：解析被拖行索引、插入到目标位置、重建列表模型并调用 `lv.setSavedLayerOrder` 持久化。

#### `private void initComponents()` 及 `*ActionPerformed` 系列
- 行号: L926（生成代码）、L1228-1277
- 描述: NetBeans 表单编辑器生成的 GUI 初始化与事件转发方法（分别转调 renameConfiguration / setConfiguration / deleteThisConfiguration / newConfiguration / resetOpacity / toggleHighlight / clearAllHighlight / setVisibility / selectAll）。

---

## LayerVisibility

**文件**: `com/sun/electric/tool/user/ui/LayerVisibility.java`（533 行）
**职责**: 图层可见性数据模型：按技术缓存每层的可见性/高亮/透明度与保存的图层顺序，负责偏好持久化（PrefPackage），并派生可见节点（PrimitiveNode）与弧（ArcProto）集合供绘制使用。可以有多实例（如每个 EditWindow），通常使用"标准"静态实例。
**继承/实现**: `public class LayerVisibility extends PrefPackage`
**关键字段**:
- `stdLayerVisibility (private static LayerVisibility)` — 全局"标准"实例
- `techPool (private TechPool)` — 技术池
- `visibleNodes (private HashSet<PrimitiveNode>)` — 派生的可见节点集（懒计算缓存）
- `visibleArcs (private HashSet<ArcProto>)` — 派生的可见弧集（懒计算缓存）
- `visibilityChanged (private boolean)` — 脏标志（可见性/高亮/顺序变化）
- `techData (private TechData[])` — 按 techIndex 索引的每技术数据

### 内部类 TechData

#### `public class TechData implements Serializable`
- 行号: L62
- 描述: 单个技术的数据：tech、保存的层顺序字符串、可见性布尔数组、高亮布尔数组、透明度 float 数组。

#### `TechData(Technology tech)`
- 行号: L69
- 描述: 构造器：初始化数组（全部可见、不高亮），设置默认透明度。

#### `public boolean[] getVisibleLayers()`
- 行号: L80
- 描述: 返回可见性数组（按 layer index 访问）。

#### `private void setDefaultOpacity()`
- 行号: L86
- 描述: 按 `getDefaultOpacity` 计算并设置该技术每层的默认透明度，有变化时置脏标志。

### 方法

#### `public LayerVisibility(boolean factory)` / `LayerVisibility(boolean factory, TechPool techPool)`
- 行号: L98 / L102
- 描述: 构造器：创建实例并调用 loadCache（factory=true 不读偏好）。

#### `private void loadCache(boolean factory)`
- 行号: L108
- 描述: 为 techPool 中每个技术建立 TechData；非 factory 时从偏好节点读取保存的层顺序与可见性。

#### `public TechData[] getTecDataForJob()`
- 行号: L135
- 描述: 返回 TechData 数组供 Job 线程访问。

#### `public void putPrefs(Preferences prefRoot, boolean removeDefaults)`
- 行号: L138
- 描述: 重写 PrefPackage：把各技术的层顺序与层可见性写入偏好（removeDefaults 时删除默认值）。

#### `void setVisible(Layer layer, boolean visible)` / `void setVisible(Map<Layer,Boolean> visibilityChange)`
- 行号: L168 / L177
- 描述: 设置单层 / 批量层的可见性；变化时置脏并清空派生缓存。

#### `void setHighlighted(Layer layer, boolean highlighted)`
- 行号: L200
- 描述: 设置层的高亮状态（高亮层绘制更亮）。

#### `public List<Layer> getSavedLayerOrder(Technology tech)`
- 行号: L212
- 描述: 解析偏好字符串，返回该技术保存的图层顺序（无保存返回 null）。

#### `public void setSavedLayerOrder(Technology tech, List<Layer> layers)`
- 行号: L249
- 描述: 保存图层顺序为 "层名," 序列（跨技术层带 "技术名:" 前缀）。

#### `public void setOpacity(Layer layer, double opacity)` / `private void resetOpacity()` / `void resetOpacity(Technology tech)`
- 行号: L266 / L288 / L295
- 描述: 设置单层透明度 / 重置全部技术默认透明度 / 重置单技术默认透明度。

#### `private void reset()`
- 行号: L276
- 描述: 恢复出厂值：全部层可见、取消高亮、清空层顺序。

#### `boolean clearChanged()`
- 行号: L303
- 描述: 报告并清除脏标志。

#### `public boolean isVisible(Layer layer)` / `public boolean[] getLayersVisibility(Technology tech)` / `public boolean isVisible(PrimitiveNode pn)` / `public boolean isVisible(ArcProto ap)`
- 行号: L314 / L326 / L338 / L359
- 描述: 查询层 / 整技术可见性数组 / 节点原型 / 弧原型是否可见（后两者触发 gatherVisiblePrims 懒计算）。

#### `public HashSet<PrimitiveNode> getVisibleNodes()`
- 行号: L347
- 描述: 返回可见节点集（懒计算）。

#### `public boolean isHighlighted(Layer layer)` / `public float getOpacity(Layer layer)`
- 行号: L371 / L376
- 描述: 查询层高亮状态 / 层透明度。

#### `private void gatherVisiblePrims()`
- 行号: L380
- 描述: 遍历技术池，把任一图层可见的节点原型 / 弧原型收集进缓存集合。

#### `private float getDefaultOpacity(Layer layer)`
- 行号: L419
- 描述: 按层功能计算默认透明度：金属 0.75-0.05*层级，contact 0.7/1.0，OVERGLASS 0.2，poly/diff/well/implant 等底层 1.0，glyph 层 0.1，其余 0.4。

#### `private TechData getTechData(Technology tech)`
- 行号: L455
- 描述: 按 techIndex 取 TechData，缺失时创建。

#### `public boolean[][] getTechDataArray()`
- 行号: L470
- 描述: 返回各技术可见性数组的二维数组（供 Job 使用）。

#### 静态方法
- `public static void updateLayerVisibility()` — L483，技术变化后更新标准实例的 techPool 并重新加载偏好
- `public static LayerVisibility getLayerVisibility()` — L493，返回标准实例（须在 client 线程调用）
- `public static void preserveVisibility()` — L501，把标准实例写入偏好
- `public static LayerVisibility loadPreferences()` — L510，从偏好加载并返回标准实例
- `public static void factoryReset()` — L519，恢复出厂值
- `public static void setDefaultOpacity()` — L528，重置全部默认透明度

---

## PaletteFrame

**文件**: `com/sun/electric/tool/user/ui/PaletteFrame.java`（699 行）
**职责**: 侧边栏的"组件面板"（Components 标签页）：上部是技术下拉框，主体是 TechPalette；负责随当前单元技术加载组件菜单，并实现"交互式放置节点实例"流程（PlaceNodeListener + PlaceNewNode Job）。
**继承/实现**: `public class PaletteFrame implements MouseListener`
**关键字段**:
- `topPanel (private JPanel)` — 面板根容器
- `techPalette (private TechPalette)` — 技术组件面板（实际绘制组件菜单）
- `techSelector (private JComboBox<String>)` — 技术选择下拉框

### 方法

#### `private PaletteFrame()` / `public static PaletteFrame newInst(WindowFrame ww)`
- 行号: L75 / L81
- 描述: 私有构造器；工厂方法创建面板并初始化组件。

#### `public void loadTechnologies(boolean makeCurrent)`
- 行号: L98
- 描述: **`[重要]`** 重建技术下拉框（排除 Generic 技术）；初始化或新技术创建时调用。

#### `public void setSelectedItem(Object anObject)`
- 行号: L117
- 描述: 设置技术下拉框选中项。

#### `private void initComponents(WindowFrame wf)`
- 行号: L119
- 描述: GridBagLayout 布局 techSelector 与 techPalette，加载技术并注册 `CurTechControlListener`。

#### `public void setCursor(Cursor cursor)`
- 行号: L161
- 描述: 设置 TechPalette 的光标。

#### `public TechPalette getTechPalette()` / `public JPanel getMainPanel()`
- 行号: L166 / L171
- 描述: 简单 getter：返回 TechPalette / 根面板。

#### `public void arcProtoChanged()`
- 行号: L176
- 描述: 弧原型变化时重绘 TechPalette。

#### `public void loadForTechnology(Technology tech, WindowFrame ww)`
- 行号: L184
- 描述: **`[重要]`** 把技术加载进组件面板（更新下拉框、调用 `techPalette.loadForTechnology`），可见时按返回的尺寸调整面板大小。

#### `public static interface PlaceNodeEventListener`
- 行号: L212
- 描述: 放置节点过程的事件回调接口：`placeNodeStarted(Object)` / `placeNodeFinished(boolean cancelled)`。

#### `public static PlaceNodeListener placeInstance(Object obj, PlaceNodeEventListener palette, boolean export)`
- 行号: L235
- 描述: **`[重要]`** 交互式放置节点实例：obj 可为 NodeProto/NodeInst/String（文本）；单元有 icon 视图时询问是否放置 icon；复用现有 PlaceNodeListener 或安装新的，设置手形光标并把窗口缩放到实例大小。

#### 内部类 `PlaceNodeListener implements WindowFrame.ElectricEventListener`
- 行号: L324
- 描述: 监听编辑窗口鼠标/键盘以确定新节点放置位置与角度。

##### 构造器 / `makePortWhenCreated(boolean m)` / `setParameter(Object toDraw)` / `setTextNode(String varName)`
- 行号: L343 / L355 / L357 / L359
- 描述: 记录待放置对象、旧监听器/光标；设置"创建后加端口"、替换放置对象、设置文本节点变量名。

##### `public void mouseReleased(MouseEvent evt)`
- 行号: L361
- 描述: **`[重要]`** 在编辑窗口释放鼠标：坐标转换 + 网格对齐；技术编辑单元中做合法性校验；启动 `PlaceNewNode` Job 创建节点，然后恢复原监听器。

##### `public void finished(EditWindow wnd, boolean cancelled)`
- 行号: L414
- 描述: 结束放置：清理高亮、恢复原监听器与光标、通知 palette 回调。

##### `public void mouseMoved / mouseDragged / mouseWheelMoved / mousePressed / mouseClicked / mouseEntered / mouseExited`
- 行号: L427-474
- 描述: 移动/拖动时显示节点拖拽预览框（`wnd.showDraggedBox`）；其余空实现。

##### `private void getCoords(MouseEvent evt)`
- 行号: L451
- 描述: 记录鼠标坐标；按住 Ctrl 时把位置强制吸附到 X/Y 轴上。

##### `public void keyPressed(KeyEvent evt)`
- 行号: L476
- 描述: A 或 Escape 中止放置；"," / "." 以 90/270 度步进旋转待放置节点（生成 dummy 实例并刷新预览）。

#### 内部类 `PlaceNewNode extends Job`
- 行号: L522
- 描述: 实际创建节点的数据库任务（CHANGE 类型 Job）。

##### `public PlaceNewNode(...)`
- 行号: L537
- 描述: 构造器：记录原型/实例来源信息（复制方向、techBits、弧角度等）并 `startJob()`。

##### `public boolean doIt() throws JobException`
- 行号: L568
- 描述: **`[重要]`** 创建 NodeInst；cellCenter/essentialBounds/pureWell 节点设为硬选择；routing exclusion 弹输入框；文本对象创建变量；按原型种类（电阻/电容/电感/二极管/晶体管/部分圆等）写入默认显示变量。

##### `public void terminateOK()`
- 行号: L673
- 描述: 完成后高亮新节点（文本则高亮文本变量）、把焦点还给编辑窗口；技术编辑单元标记新几何；export 为 true 时弹出 NewExport 对话框。

---

## LibraryPalette

**文件**: `com/sun/electric/tool/user/ui/LibraryPalette.java`（269 行）
**职责**: 可放入 PaletteFrame 的库单元面板：列出某个库中的单元，单击放置实例、双击编辑、右键弹出编辑/复制/重命名/删除菜单；记忆每个库的滚动位置；监听数据库变化自动刷新。
**继承/实现**: `public class LibraryPalette extends JPanel implements DatabaseChangeListener, MouseListener, PaletteFrame.PlaceNodeEventListener`
**关键字段**:
- `library (private Library)` — 当前显示的库
- `scrollPane (private JScrollPane)` / `cellJList (private JList)` — 滚动面板与单元列表
- `viewPortMap (private Map<Library,Rectangle>)` — 各库的滚动视口位置记忆
- `cellPopup (private JPopupMenu)` — 右键菜单（懒创建）
- `lastPlaceNodeListener (private PaletteFrame.PlaceNodeListener)` — 进行中的放置监听器

### 方法

#### `public LibraryPalette(Dimension preferredSize)`
- 行号: L74
- 描述: 构造器（工厂 newInst 使用）：初始化组件并注册数据库监听器。

#### `public void setLibrary(Library lib)`
- 行号: L84
- 描述: **`[重要]`** 切换显示的库：先记录旧库滚动位置，再刷新列表。

#### `private void initComponents(Dimension preferredSize)`
- 行号: L96
- 描述: 创建 JList（自定义渲染器、单选、鼠标监听）放入 JScrollPane。

#### `private void updateCellList()`
- 行号: L112
- 描述: 用库中单元填充列表，并恢复该库保存的滚动位置。

#### `public void databaseChanged(DatabaseChangeEvent e)`
- 行号: L135
- 描述: 数据库变化时刷新单元列表。

#### `public void mousePressed(MouseEvent e)`
- 行号: L143
- 描述: **`[重要]`** 右键弹出单元操作菜单；双击编辑单元并取消进行中的放置；单击放置单元实例（schematic 单元忽略）。

#### `public void placeNodeStarted(Object nodeToBePlaced)` / `public void placeNodeFinished(boolean cancelled)`
- 行号: L177 / L179
- 描述: 放置开始为空实现；放置结束清除列表选择并清空引用。

#### `private void initCellPopup()`
- 行号: L184
- 描述: 懒创建右键菜单（Edit/Duplicate/Rename/Delete）。

#### `private void selectedCellEdit() / selectedCellDuplicate() / selectedCellRename() / selectedCellDelete()`
- 行号: L212 / L223 / L229 / L238
- 描述: 编辑选中单元（复用当前窗口或新建）/ 通过 `CellMenu.NewCellName` 复制 / 弹输入框后 `CircuitChanges.renameCellInJob` 重命名 / `CircuitChanges.deleteCell` 删除。

#### 内部类 `CustomCellRenderer extends DefaultListCellRenderer`
- 行号: L247
- 描述: 自定义列表渲染器，用 `cell.noLibDescribe()` 显示单元名。

---

## TechPalette

**文件**: `com/sun/electric/tool/user/ui/TechPalette.java`（1410 行）
**职责**: 组件菜单（Component Menu）：以网格形式绘制当前技术下的节点与弧，支持点击放置节点、设置当前弧、弹出子菜单（Pure 层节点、Spice 单元、Misc 等），支持拖拽放置，悬停时更新状态栏提示。
**继承/实现**: `public class TechPalette extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, PaletteFrame.PlaceNodeEventListener, ComponentListener, DragGestureListener, DragSourceListener`
**关键字段**:
- `menuX / menuY (private int)` — 组件菜单的列数 / 行数
- `entrySize (private int)` — 每个条目的像素尺寸
- `inPalette (private List<Object>)` — 面板条目列表（NodeInst/ArcProto/String/List）
- `highlightedNode (private Object)` — 正在放置的节点（用于高亮）
- `paletteImage (private VolatileImage)` / `paletteImageStale (private boolean)` — 面板缓存图像与失效标志
- `dragSource (private DragSource)` — 拖拽源
- `offscreen (private PixelDrawing)` — 离屏绘制缓冲
- `curTech (private Technology)` — 当前技术

### 方法

#### `TechPalette()`
- 行号: L143
- 描述: 构造器：注册键盘/鼠标/滚轮/组件监听器，初始化拖拽源。

#### `public Dimension loadForTechnology(Technology tech, Cell curCell, EditingPreferences ep)`
- 行号: L165
- 描述: **`[重要]`** 加载技术进面板：调用 `getNodesGrouped` 构建条目矩阵（按列填充 inPalette）、处理晶体管旋转与"在用量"过滤，根据屏幕尺寸计算条目大小与面板尺寸。

#### `private NodeInst rotateTransistor(NodeInst ni, EditingPreferences ep)`
- 行号: L224
- 描述: layout 技术下按偏好 `User.isRotateLayoutTransistors()` 旋转晶体管条目。

#### `private static Object getInUse(Object item)`
- 行号: L243
- 描述: 过滤未启用（isNotUsed）的原型，返回 null 表示不显示。

#### `public static String getItemName(Technology tech, Object item, boolean getVarName)`
- 行号: L264
- 描述: 按条目类型取显示名：PrimitiveNode/NodeInst/ArcProto 取原型名，schematics 的 NodeInst 可返回 TECH_TMPVAR 变量值。

#### `private Object[][] getNodesGrouped(Technology tech, Cell curCell, EditingPreferences ep)`
- 行号: L309
- 描述: **`[重要]`** 构建面板内容：Artwork 技术编辑单元用硬编码 16 项；否则调用 `ComponentMenu.getMenuPalette` 后转换。

#### `private static Object[][] convertMenuPalette(...)` / `private static Object convertMenuItem(...)`
- 行号: L341 / L373
- 描述: 把 Xml.MenuPalette 转换为条目矩阵（List 表示可切换的组）；把 Xml 菜单项转换为 ArcProto/PrimitiveNode/NodeInst/字符串。

#### `private static NodeInst makeNodeInst(...)`
- 行号: L394 / L407
- 描述: 为面板创建临时 dummy 节点实例（定位到原型中心、设置方向/功能/默认轮廓，可选显示变量名）。

#### `public void mouseReleased(MouseEvent e)`
- 行号: L436
- 描述: **`[重要]`** 点击条目：取光标下对象；List 且光标在右下角时弹出子菜单（PlacePopupListListener）；否则按当前接触配置选条目。ArcProto → 设为当前弧；节点 → `PaletteFrame.placeInstance`；特殊字符串条目弹出对应菜单（LOADCELL / SPECALMENUCELL / PURE / MISC / SPICE / EXPORT / TEXT / HIGH / PORT）。

#### 内部类 `LongListPopup extends EDialog`
- 行号: L672
- 描述: "Pure" 条目弹窗：用带滚动条的列表显示 pure 层节点或当前库单元（JPopupMenu 无法容纳大列表）。

##### `private LongListPopup(TechPalette panel, int x, int y, boolean pures)`
- 行号: L678
- 描述: 构造器：无装饰窗口、单选 JList、鼠标点击/回车选择、失焦关闭；pures=true 收集 pure 层节点，否则收集当前库中视图匹配的单元。

##### `protected void escapePressed()` / `private void entryClicked()`
- 行号: L816 / L824
- 描述: Escape 关闭窗口；点击条目后按 describe 文本反查 NodeProto 并放置。

#### `private void makeExport(String type)`
- 行号: L837
- 描述: 按类型放置导出端口（wire/bus → Schematics pin 节点，universal → invisiblePin），放置后自动创建导出。

#### `public void makeLayoutTextCommand()` / `public void makeLayoutImageCommand()`
- 行号: L844 / L856
- 描述: 打开 LayoutText / LayoutImage 对话框（仅 layout 技术）。

#### `public void placeNodeStarted(Object nodeToBePlaced)` / `public void placeNodeFinished(boolean cancelled)`
- 行号: L868 / L872
- 描述: 记录/清除正在放置的节点并重绘。

#### 拖拽实现
- `public void dragGestureRecognized(DragGestureEvent e)` — L879，创建 NodeProtoTransferable 开始拖拽
- `dragEnter / dragOver / dragExit / dragDropEnd / dropActionChanged` — L890-898，空实现

#### 内部类 `ReadSpiceLibrary extends Job`
- 行号: L903
- 描述: 后台线程读取 Spice 库，完成后填充弹出菜单。

##### `protected ReadSpiceLibrary(...)` / `public boolean doIt()` / `public void terminateOK()` / `public static void loadSpiceCells(...)`
- 行号: L911 / L923 / L933 / L940
- 描述: 构造器启动 Job；doIt 读取 .jelib 库；terminateOK 把库中 icon 单元加入菜单并显示；loadSpiceCells 静态填充菜单。

#### 内部类 `PlacePopupListener implements ActionListener`
- 行号: L954
- 描述: 弹出菜单项动作：放置指定对象。

#### 内部类 `PlacePopupListListener extends PlacePopupListener`
- 行号: L967
- 描述: 组条目弹出菜单动作：弧 → 交换列表顺序并设为当前弧；节点 → 放置并设为该组的默认子条目，然后重绘所有窗口的组件面板。

##### `public void actionPerformed(ActionEvent evt)`
- 行号: L982
- 描述: 见上；遍历所有 WindowFrame 更新各 TechPalette。

#### `private Object getObjectUnderCursor(int xp, int yp)`
- 行号: L1022
- 描述: 按坐标计算条目索引并返回条目对象。

#### `private boolean isCursorOnCorner(MouseEvent e)`
- 行号: L1037
- 描述: 判断光标是否在条目右下角（deltaX>0.75 且 deltaY>0.65），用于触发组子菜单。

#### `public void mouseMoved(MouseEvent e)`
- 行号: L1052
- 描述: **`[重要]`** 悬停时通过 `StatusBar.setSelectionOverride` 显示 "CREATE NODE: ..." / "USE ARC: ..." 提示。

#### `public void mouseExited(MouseEvent e)`
- 行号: L1084
- 描述: 鼠标离开时清除状态栏覆盖文本。

#### `public void componentResized(ComponentEvent e)`
- 行号: L1094
- 描述: 尺寸变化时置图像失效标志（其余 ComponentListener 方法为空）。

#### `public void paint(Graphics g)`
- 行号: L1098
- 描述: **`[重要]`** 绘制面板：维护 VolatileImage 缓存（失效或丢失时重渲染），随后画放置中节点的蓝色双框高亮和当前弧的红色双框高亮。

#### `private void renderPaletteImage()`
- 行号: L1176
- 描述: **`[重要]`** 重渲染全部条目：弧（VectorCache 画形状 + 红色边框）、节点/单元实例（几何或文字 + 蓝色边框）、字符串条目（居中文字）、组条目画箭头标记，最后画网格分隔线。

#### `private double getLargestDimension(PrimitiveNode np, EditingPreferences ep)`
- 行号: L1364
- 描述: 计算节点渲染基准尺寸：pin 按最大弧宽，其他取默认宽高最大值。

#### `private void drawArrow(Graphics g, int x, int y)` / `private void drawShapes(...)`
- 行号: L1389 / L1407
- 描述: 在条目右下角画小箭头（表示有子菜单）；委托 `AbstractDrawing.drawShapes` 画形状集。
