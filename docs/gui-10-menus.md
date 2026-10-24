# 菜单系统（EMenuBar / EMenu / EMenuItem 及各菜单）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.menus。本文件由 AI 生成。

## 模块概述

Electric 的菜单系统采用"模板 + 实例"两层架构：每个菜单项用一个轻量的 `EMenuItem`（或其子类 `EMenu`）模板对象描述，模板只保存文字、助记符、快捷键和 `run()` 命令，不保存 Swing 组件；真正的 `JMenu`/`JMenuItem`/`JMenuBar` 组件由 `genMenu()`/`genInstance()` 按需生成，供 SDI 模式下的每个窗口各持有一份，而所有实例共享同一模板状态。模板树在 `EMenuBar` 构造时通过 `registerTree()` 一次性注册：为每个菜单项分配从菜单栏到该项的整型下标路径 `path`，并把文本描述串（"Menu | SubMenu | item" 形式）作为键登记到 `KeyBindingManager` 以统一管理快捷键（默认键、用户自定义键、偏好持久化）。菜单按窗口类型/工具动态构建：`MenuCommands.menuBar()` 汇集各菜单类的 `makeMenu()` 结果，条件项（null 跳过）、插件菜单（`makeExtraMenu` 反射加载）、调试菜单（`Job.getDebug()` 时附加）都在构建期决定；运行期则通过 `setDynamicItems()` 替换子菜单尾部条目（如"Open Recent Library"、Window 菜单的窗口列表、Languages 菜单的脚本列表）。菜单项通过 `actionPerformed()` 绑定命令：统一记录 ActivityLogger、缓存"最后激活项"以支持 Repeat Last Action，然后执行 `run()`，最后刷新所有注册为可更新的菜单项（勾选框/灰化项通过 `registerUpdatable()` 注册）。`EMenuBar.Instance` 重写 `processKeyBinding()`，把按键事件直接交给 `KeyBindingManager` 处理而不是分发给菜单组件，从而保证所有窗口快捷键行为一致。

---

## EMenuBar

**文件**: `com/sun/electric/tool/user/menus/EMenuBar.java`（368 行）

**职责**: 菜单栏模板类。把若干菜单模板关联成一个"组"，维护组成员（各窗口的 `JMenuBar` 实例）之间的状态一致性和快捷键绑定一致性；同时充当监听者，在状态变化时刷新所有可更新的菜单项。

**继承/实现**: `public class EMenuBar extends EMenu`（继承 EMenuItem 的模板树能力）

**关键字段**:
- `prefs (Preferences)` — 用户快捷键绑定的偏好存储节点（`Preferences.userNodeForPackage(EMenuBar.class)`）
- `menuItems (Map<String,EMenuItem>)` — 全部已注册菜单项，以描述串（getDescription()）为键
- `keyBindingManager (public final KeyBindingManager)` — 该菜单组的快捷键管理器（构造参数 `name+"MenuKeyBinding-"`）
- `hiddenMenu (private final EMenu)` — 隐藏菜单（"Shortcuts"），其菜单项只通过快捷键触发、不出现在菜单栏上
- `updatableItems (private final List<EMenuItem>)` — 注册为"可更新"（灰化/勾选状态会变化）的菜单项列表

### 方法

#### `EMenuBar(String name, EMenu hiddenMenu, List<EMenuItem> items)`
- 行号: L63
- 描述: **`[重要]`** 包内构造器。调用父类构造后，把自身 path 置为空数组 `int[0]`，对 items 逐个调用 `registerTree(this, path, indexInParent++)` 注册模板树（null 项跳过），最后把 hiddenMenu 以 -1 下标注册为树根（因此 `getDescription()` 中 path[0] 为 -1 表示隐藏菜单）。

#### `public EMenu getHiddenMenu()`
- 行号: L83
- 描述: 返回隐藏菜单（其中条目仅由快捷键调用）。

#### `public String getDescription(int[] path)`
- 行号: L94
- 描述: 按路径生成 "Menu | SubMenu | SubMenu | item" 形式的描述串；path[0] 为 -1 时从 hiddenMenu 开始。

#### `void registerKeyBindings(EMenuItem menuItem)`
- 行号: L111
- 描述: 包内方法。把菜单项放入 `menuItems` 映射，注册其全部默认加速键，并将该模板对象注册为 KeyBindingManager 的 ActionListener（实例菜单项按下时经 keyBindingManager 触发模板的 `actionPerformed`）。

#### `void registerUpdatable(EMenuItem menuItem)`
- 行号: L127
- 描述: 包内方法。把菜单项加入 updatableItems 列表。

#### `public void updateAllButtons()`
- 行号: L134
- 描述: 遍历 updatableItems，调用每个模板的 `updateJMenuItems()` 刷新其所有窗口实例的灰化/勾选状态。

#### `public KeyBindings getKeyBindings(EMenuItem item)`
- 行号: L143
- 描述: 取该菜单项在 KeyBindingManager 中的绑定集合（无绑定返回 null）。

#### `private void addDefaultKeyBinding(EMenuItem item, KeyStroke stroke)`
- 行号: L152
- 描述: 为菜单项添加一条默认快捷键。

#### `public void addUserKeyBinding(EMenuItem item, KeyStroke stroke, KeyStroke prefixStroke)`
- 行号: L163
- 描述: 添加用户自定义快捷键（含可选前缀键），写入偏好存储，并调用 `updateAccelerator` 刷新菜单显示。

#### `public void resetKeyBindings(EMenuItem item)`
- 行号: L173
- 描述: 将单个菜单项恢复到默认快捷键并刷新加速键显示。

#### `public void resetAllKeyBindings()`
- 行号: L182
- 描述: 将全部菜单项恢复到默认快捷键。

#### `public void removeKeyBinding(String actionDesc, KeyStrokePair pair)`
- 行号: L192
- 描述: 按描述串移除一条用户绑定，并更新对应菜单项的加速键显示。

#### `public void restoreSavedBindings(boolean initialCall)`
- 行号: L200
- 描述: 从偏好存储恢复保存的绑定（菜单栏创建时以 `initialCall=true` 调用），随后更新所有菜单项的加速键显示。

#### `private void updateAccelerator(EMenuItem item)`
- 行号: L214
- 描述: 绑定变化后重算菜单项应显示的加速键：取第一条不含前缀键的有效绑定（菜单不能显示双键组合）。

#### `public KeyMaps getKeyMaps()`
- 行号: L237
- 描述: 返回持有真实 InputMap/ActionMap 的 KeyMaps 对象（用于把全局快捷键挂到组件上）。

#### `public Instance genInstance()`
- 行号: L242
- 描述: 生成一个该菜单栏模板的 Swing 实例（JMenuBar）。

### 内部类 Instance（extends JMenuBar）

#### `public Instance()`
- 行号: L257
- 描述: 构造时遍历模板 `getItems()`，对每个菜单项调用 `genMenu()` 并加入菜单栏。SDI 模式下每个窗口拥有一个 Instance，同组实例共享状态与快捷键。

#### `public EMenuBar getMenuBarGroup()`
- 行号: L262
- 描述: 返回该实例所属的 EMenuBar 模板组。

#### `protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed)`
- 行号: L273
- 描述: **`[重要]`** 重写 JMenuBar 的按键分发：记录 ActivityLogger 日志；`ignoreKeyBindings` 为真则全部忽略；`ignoreTextEditKeys` 为真则只放行带 Ctrl/Meta/Alt 的按键；其余按键交给 `keyBindingManager.processKeyEvent(e)`，不再传给菜单组件。

#### `public void setIgnoreKeyBindings(boolean b)` / `public boolean getIgnoreKeyBindings()`
- 行号: L303 / L305
- 描述: 纯 getter/setter 合并：设置/读取"忽略全部快捷键"标志。

#### `public void setIgnoreTextEditKeys(boolean b)` / `public boolean getIgnoreTextEditKeys()`
- 行号: L307 / L309
- 描述: 纯 getter/setter 合并：设置/读取"忽略文本编辑键"标志。

#### `JMenuItem findMenuItem(int[] path)`
- 行号: L318
- 描述: 包内方法。按整型路径在真实组件树中定位菜单项（getComponent(topIndex) 后逐层 getMenuComponent），path[0]<0 返回 null。

#### `public void finished()`
- 行号: L336
- 描述: 顶层窗口（SDI 模式）销毁时调用：递归移除全部菜单与菜单项的 ActionListener（`disposeofMenu`/`disposeofMenuItem`），解除对模板的引用以便垃圾回收。

---

## EMenu

**文件**: `com/sun/electric/tool/user/menus/EMenu.java`（126 行）

**职责**: 通用 Electric 菜单模板。持有一个不可变的子菜单项列表，负责生成子菜单的 Swing 组件，并支持"动态菜单项"——在静态项之后追加/替换运行时条目。

**继承/实现**: `public class EMenu extends EMenuItem`

**关键字段**:
- `items (final List<EMenuItem>)` — 子菜单项列表（构造时过滤 null 后包装为不可修改列表；`EMenuItem.SEPARATOR` 表示分隔线）

### 方法

#### `public EMenu(String text, EMenuItem... items)`
- 行号: L45
- 描述: 构造菜单模板。text 中 "_" 标记助记符位置；var-arg 中的 null 项被跳过，`MenuCommands.SEPARATOR` 元素成为分隔线。

#### `public EMenu(String text, List<EMenuItem> itemsList)`
- 行号: L61
- 描述: 同上，接受 List 参数（转为数组后委托给上一个构造器）。

#### `public List<EMenuItem> getItems()`
- 行号: L70
- 描述: 返回不可修改的子菜单项列表。

#### `public void setDynamicItems(List<? extends EMenuItem> dynamicItems)`
- 行号: L74
- 描述: **`[重要]`** 动态设置子菜单尾部条目：对所有已生成的菜单栏实例，先删掉静态项（items.size()）之后的多余组件，再用 `genMenuElems` 追加新的动态项。用于"Open Recent Library"、Window 菜单窗口列表、Languages 脚本列表等运行期变化菜单。

#### `void registerTree(EMenuBar menuBar, int[] parentPath, int indexInParent)`
- 行号: L83
- 描述: 包内方法。先按父类注册自身，再递归注册全部子项（子项 path = 自身 path + 子项下标）。

#### `protected void registerItem()`
- 行号: L92
- 描述: 空实现覆盖父类——菜单本身不注册快捷键（只有叶子菜单项注册）。

#### `JMenu genMenu()`
- 行号: L95
- 描述: 包内方法。生成 JMenu 组件，并递归生成全部子项组件加入其中。

#### `protected JMenuItem createMenuItem()`
- 行号: L102
- 描述: 覆盖父类，返回 `new JMenu()`（菜单项模板的工厂方法）。

#### `private void genMenuElems(JMenu menu, List<? extends EMenuItem> items)`
- 行号: L107
- 描述: 把模板列表逐个转为 Swing 组件加入 JMenu；`EMenuItem.SEPARATOR` 转成 `menu.addSeparator()`。

#### `protected void updateMenuItem(JMenuItem item)`
- 行号: L118
- 描述: 覆盖父类：菜单只根据 `isEnabled()` 设置灰化，不设置勾选状态。

#### `public void run()`
- 行号: L123
- 描述: 覆盖父类，抛 `UnsupportedOperationException`——菜单本身不执行命令。

---

## EMenuItem

**文件**: `com/sun/electric/tool/user/menus/EMenuItem.java`（416 行）

**职责**: 通用 Electric 菜单项模板。保存文字/图标/助记符/加速键等元数据，从同一个模板生成菜单按钮与工具条按钮（共享状态），并作为 ActionListener 统一响应激活事件。

**继承/实现**: `public abstract class EMenuItem implements ActionListener`

**关键字段**:
- `NULL_ARRAY (public static final EMenuItem[])` — 空数组常量
- `buckyBit (private static final int)` — 平台菜单快捷键修饰键（Mac 为 Meta，其余为 Ctrl，`Toolkit.getMenuShortcutKeyMaskEx()`）
- `SEPARATOR (public static final EMenuItem)` — 匿名子类常量，代表菜单分隔线（registerTree/genMenu/run 均不可用）
- `lastActivated (private static EMenuItem)` — 最后激活的菜单项，供 Repeat Last Action 重放
- `text (private final String)` — 显示文字（也是快捷键偏好存储的键和工具提示文本）
- `icon (private final Icon)` — 图标，可为 null
- `mnemonicsPos (private final int)` — text 中助记符位置（"_" 标记），-1 表示无
- `accelerators (private KeyStroke[])` — 附加加速键数组
- `menuBar (EMenuBar)` — 所属菜单栏模板（包内可见）
- `path (int[])` — 从菜单栏到本项的整型下标路径（包内可见）

### 方法

#### 构造器族（L95–L188）
- 行号: L95 / L112 / L122 / L132 / L141 / L149 / L158 / L167 / L177 / L186
- 描述: 全部委托到主构造器 `EMenuItem(String, KeyStroke[], boolean, Icon)`：解析 text 中的 "_"（末尾 "_" 或禁用助记符时忽略），删除下划线后保存文字与助记符位置，accelerators 为 null 时置为空数组。public 可见的依次为 `EMenuItem(String text, KeyStroke accelerator)`（L141）、`EMenuItem(String text)`（L149）、`EMenuItem(String text, Icon icon)`（L158）、`EMenuItem(String text, char acceleratorChar)`（L167）；其余为包内可见。

#### `public static KeyStroke[] shortcut(int[] keyCode)`
- 行号: L196
- 描述: 为每个键码生成带平台修饰键（buckyBit）的 KeyStroke 数组。

#### `public static KeyStroke shortcut(int keyCode)`
- 行号: L209
- 描述: 生成单个带平台修饰键的 KeyStroke（即 Ctrl/Cmd+键）。

#### `public static void repeatLastCommand()`
- 行号: L216
- 描述: 重放最后激活的命令（若 lastActivated 非空则调用其 run()）。

#### `public String getText()`
- 行号: L225
- 描述: 纯 getter：返回显示文字（已去除 "_"）。

#### `public String getToolTipText()`
- 行号: L227
- 描述: 生成工具提示：文字后附加速键（若有），格式 `text(accelerator)`。

#### `KeyStroke getAccelerator()` / `KeyStroke[] getAccelerators()`
- 行号: L235 / L240
- 描述: 纯 getter 合并：返回第一个加速键 / 整个加速键数组（包内可见）。

#### `void setAccelerator(KeyStroke accelerator)`
- 行号: L242
- 描述: 包内方法。替换第一个加速键，并同步更新所有窗口菜单实例上该菜单项的 setAccelerator 显示。

#### `public String toString()`
- 行号: L256
- 描述: 返回 text。

#### `public String getDescription()`
- 行号: L264
- 描述: 返回 "Menu | SubMenu | item" 形式的描述串（已注册时），未注册则返回 text。该串是快捷键偏好存储的键。

#### `public int[] getPath()`
- 行号: L270
- 描述: 返回从菜单栏到本项的整型路径。

#### `void registerTree(EMenuBar menuBar, int[] parentPath, int indexInParent)`
- 行号: L278
- 描述: 包内方法。把本项挂到菜单栏模板树：记录 menuBar，构造 path = parentPath + indexInParent，然后调用 registerItem()。同一模板被引用两次会抛 IllegalStateException（防止一处模板多处使用导致状态混乱）。

#### `protected void registerItem()`
- 行号: L291
- 描述: 注册本项：`menuBar.registerKeyBindings(this)`。子类可覆盖以追加注册（如 CheckBox 追加 registerUpdatable）。

#### `protected void registerUpdatable()`
- 行号: L298
- 描述: 把本项注册为"可更新"菜单项（灰化或勾选状态会变化者使用）。

#### `JMenuItem genMenu()`
- 行号: L306
- 描述: 包内方法。由模板生成一个 Swing 菜单项实例：createMenuItem() 后设置文字、助记符（setMnemonic/setDisplayedMnemonicIndex）、加速键，添加自身为 ActionListener，并初始化灰化/勾选状态（updateMenuItem）。

#### `protected JMenuItem createMenuItem()`
- 行号: L329
- 描述: 工厂方法：返回 `new JMenuItem()`（有图标时 `new JMenuItem(icon)`）。子类覆盖以生成其他组件（EMenu 返回 JMenu，CheckBox 返回 JCheckBoxMenuItem）。

#### `protected void updateMenuItem(JMenuItem item)`
- 行号: L337
- 描述: 弹出菜单前刷新实例外观：setEnabled(isEnabled()) + setSelected(isSelected())。

#### `public boolean isEnabled()`
- 行号: L347
- 描述: 返回本模板是否可用（默认 true，子类按窗口类型/工具状态覆盖）。

#### `public boolean isSelected()`
- 行号: L354
- 描述: 返回本模板是否处于选中状态（默认 false，CheckBox 类覆盖）。

#### `public char getMnemonicChar()`
- 行号: L360
- 描述: 返回触发本菜单项的 ALT 字符（无则为 0）。

#### `public void actionPerformed(ActionEvent e)`
- 行号: L370
- 描述: **`[重要]`** 命令统一入口：通知 UserInterfaceMain 用户已发出命令，ActivityLogger 记录激活日志，除 "Repeat Last" 外更新 lastActivated，然后执行 run()，最后 updateButtons() 刷新所有可更新菜单项。菜单按钮、工具条按钮、快捷键三条路径都汇到这里。

#### `public abstract void run()`
- 行号: L382
- 描述: 抽象方法：执行具体命令。所有匿名 EMenuItem 子类都实现它。

#### `protected void updateButtons()`
- 行号: L388
- 描述: 状态变化后刷新 GUI 按钮：默认调用 `menuBar.updateAllButtons()`；动态菜单项（如最近库列表）常覆盖为空以跳过。

#### `void updateJMenuItems()`
- 行号: L395
- 描述: 包内方法。遍历所有菜单栏实例，按 path 找到本项的真实组件并刷新其灰化/勾选状态。

### 内部类 CheckBox（extends EMenuItem）

#### `public CheckBox(String text)` / `public void run()` / `public abstract void setSelected(boolean b)` / `protected JMenuItem createMenuItem()` / `protected void registerItem()`
- 行号: L405 / L406 / L407 / L408 / L411
- 描述: 勾选框菜单项基类（如 Routing 菜单的 Auto-Stitching 开关）。run() 实现为取反选中状态；createMenuItem() 返回 `JCheckBoxMenuItem`；registerItem() 在注册快捷键之外追加 registerUpdatable()，使勾选状态实时同步到所有窗口。

---

## MenuCommands

**文件**: `com/sun/electric/tool/user/menus/MenuCommands.java`（271 行）

**职责**: 菜单系统的组装入口。汇集所有下拉菜单为一个 EMenuBar（含隐藏的快捷键菜单与运行时附加菜单），并提供各菜单类共用的"取当前窗口选中对象"辅助方法。类头注释说明：SDI 模式下 Swing 要求每个窗口有自己的菜单，因此某个窗口菜单的状态变化必须同步到所有其他窗口的菜单。

**继承/实现**: `public final class MenuCommands`（构造器私有，纯静态工具类）

**关键字段**:
- `rtdefinedMenus (private static List<EMenuItem>)` — 启动期由外部注册的附加菜单（addRTDefinedMenu 收集）
- `menuBar (private static EMenuBar)` — 单例菜单栏模板缓存

### 方法

#### `public static void addRTDefinedMenu(EMenuItem menu)`
- 行号: L64
- 描述: 启动时注册附加菜单，menuBar() 构建时统一追加。

#### `public static EMenuBar menuBar()`
- 行号: L71
- 描述: **`[重要]`** 创建（或返回缓存的）完整下拉菜单栏。顺序：FileMenu → EditMenu → CellMenu → ExportMenu → ViewMenu → WindowMenu → ToolMenu → 反射加载的插件菜单 "menus.SunAsyncMenu"（`makeExtraMenu`，L82）→ HelpMenu；`Job.getDebug()` 时再追加 `makeTestMenus()`（TestMenu + PublicDebugMenu + 反射加载 com.sun.electric.plugins.tests.DevelopersMenus）；最后追加 rtdefinedMenus。隐藏菜单由 `wiringShortcuts()` 提供，构造后调用 `restoreSavedBindings(true)` 恢复用户快捷键。

#### `private static List<EMenuItem> makeTestMenus()`
- 行号: L101
- 描述: 构建调试菜单列表（仅 Job.getDebug() 时被调用）。

#### `static EMenu makeExtraMenu(String plugin, boolean printClassError)`
- 行号: L129
- 描述: 包内方法。反射调用 `com.sun.electric.plugins.<plugin>.makeMenu()` 加载插件菜单；类缺失/异常时返回 null（debug 模式打印原因）。WindowMenu（j3d 菜单）与 ToolMenu（pcell 菜单）也用它加载插件。

#### `private static EMenu wiringShortcuts()`
- 行号: L158
- 描述: 构建隐藏菜单 "Shortcuts"：数字 0-9 键切换布线层（`ClickZoomWireListener.theOne.wireTo(0..9)`）、"-"/"=" 上下移一层（wireDownUp）、空格切换布线目标（switchWiringTarget），以及 WindowMenu 的窗口循环项（getHiddenWindowCycleMenuItem）。这些项只通过快捷键触发，不显示在菜单栏。

#### `public static List<Geometric> getSelectedObjects(boolean wantNodes, boolean wantArcs)`
- 行号: L226
- 描述: 返回当前焦点窗口高亮区中选中的对象（可过滤节点/弧）；无窗口或无高亮器时返回空列表。

#### `public static List<NodeInst> getSelectedNodes()`
- 行号: L243
- 描述: 返回当前焦点窗口选中的 NodeInst 列表。

#### `public static List<ArcInst> getSelectedArcs()`
- 行号: L260
- 描述: 返回当前焦点窗口选中的 ArcInst 列表。

---

## FileMenu

**文件**: `com/sun/electric/tool/user/menus/FileMenu.java`（2102 行）

**职责**: 处理 "File" 下拉菜单的全部命令：库的新建/打开/关闭/保存，各种格式的导入/导出，项目管理与 CVS，打印，以及退出。大量逻辑封装在内部 Job 类（NewLibrary、ReadLibrary、ImportLibrary、CloseLibrary、SaveLibrary、QuitJob 等）中以后台线程执行。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L140
- 描述: **`[重要]`** 包内方法，构建 "_File" 菜单。先创建空的动态子菜单 `openRecentLibs`（"Open Recent Library"），随后按顺序组装各项（详见下方清单）。条件项：Dais/Calibre 导入依赖 `IOTool.hasDais()/hasCalibreDRV()`，Skill 导出依赖 `IOTool.hasSkill()`，Fast JELIB 仅 debug 模式，Mac 平台省略 Preferences/Quit（由系统菜单提供）。"C_VS" 子菜单覆盖 `isEnabled()`（CVS.isEnabled()）并注册为可更新项。

### 关键命令处理方法（public）

#### `public static void newLibraryCommand()`
- 行号: L379
- 描述: 弹出输入框询问库名，启动 NewLibrary Job 创建并设为当前库。

#### `public static void openLibraryCommand()` / `public static void openLibraryCommand(URL file)`
- 行号: L417 / L449
- 描述: 打开库（交互式弹文件对话框 / 按 URL 直接打开）。同名库已存在时先走 preventLoss 提示保存，再启动 ReadLibrary Job；无扩展名的 URL 依次尝试 .jelib/.elib/.delib/.readabledump。

#### `public static FileType getLibraryFormat(String fileName, FileType defaultType)`
- 行号: L497
- 描述: 按文件名确定库格式，无有效扩展名时返回 defaultType（委托 FileType.getLibraryFormat）。

#### `public static void updateRecentlyOpenedLibrariesList()`
- 行号: L688
- 描述: **`[重要]`** 重建 "Open Recent Library" 动态子菜单：为 User.getRecentlyOpenedLibraries() 中每个库生成菜单项（run 里 openLibraryCommand 并更新工作目录），空列表时放一个 "<none>" 占位项，最后 `openRecentLibs.setDynamicItems(list)`。

#### `public static void importLibraryCommand(FileType type, boolean wholeDirectory, boolean canMerge, boolean techSpecific, boolean skeletonize)`
- 行号: L883
- 描述: 交互式导入外部库：选文件（wholeDirectory 时选目录），委托 importLibraryCommandNoGUI。

#### `public static void importLibraryCommandNoGUI(String fileName, FileType type, boolean canMerge, boolean techSpecific, boolean skeletonize)`
- 行号: L905
- 描述: 非交互导入（批处理亦可用）。同名库存在时提示保存/合并（canMerge 时提供 Merge 选项）；techSpecific 时弹框选择版图工艺；ELIB/READABLEDUMP 走 ReadLibrary，其余启动 ImportLibrary Job。

#### `public static void closeLibraryCommand(Library lib)`
- 行号: L963
- 描述: 关闭库：检查是否有非剪贴板引用（有则拒绝），提示保存未保存改动，启动 CloseLibrary Job。

#### `public static boolean saveLibraryCommand(Library lib)` / `public static boolean saveLibraryCommand(Library lib, FileType type, boolean compatibleWith6, boolean forceToType, boolean saveAs)`
- 行号: L1060 / L1074
- 描述: 保存库（简版 / 全参数版）。先 saveLibraryRequest 收集保存信息（含 Dummy cell 警告），再启动 SaveLibrary Job。

#### `public static RenameAndSaveLibraryTask saveLibraryRequest(Library lib, FileType type, boolean compatibleWith6, boolean forceToType, boolean saveAs)`
- 行号: L1095
- 描述: 询问保存细节：警告 Dummy cells；非 saveAs 且库来自磁盘时复用原文件名（文件不可写则重新弹框）；最终生成 RenameAndSaveLibraryTask（含重命名、备份方案）。

#### `public static void saveAsLibraryCommand(Library lib)`
- 行号: L1267
- 描述: "另存为"：强制弹文件名保存，完成后刷新窗口标题。

#### `public static void saveAllLibrariesCommand()` / `public static void saveAllLibrariesCommand(FileType type, boolean compatibleWith6, boolean forceToType)`
- 行号: L1281 / L1286
- 描述: 保存所有已改动库；单个保存失败时询问"取消全部 / 跳过此库"。

#### `public static void saveAllLibrariesInFormatCommand()`
- 行号: L1319
- 描述: 弹框选择 JELIB/ELIB/READABLEDUMP/DELIB 格式，把全部库另存为该格式（SaveAllLibrariesInFormatJob 会改写库文件扩展名并重新保存）。

#### `public static void importPrefsCommand()` / `public static void exportPrefsCommand()`
- 行号: L1361 / L1374
- 描述: 导入/导出用户偏好 XML 文件（UserInterfaceMain.importPrefs / Pref.exportPrefs）。

#### `public static void exportCommand(FileType type, boolean isNetlist)`
- 行号: L1415
- 描述: **`[重要]`** 导出当前窗口 cell 的统一入口（File/Export 各格式与 Tools 各网表都汇聚于此）。PostScript 先做 syncAll 并限制窗口类型；SPICE 按引擎转成 .cir 并尊重运行目录/覆盖偏好；TELESIS 选目录；PNG 走 ExportImage Job；其余 Output.exportCellCommand。文件命名按 IOTool 偏好决定（如 GDS 写整库时用库名）。

#### `public static void pageSetupCommand()`
- 行号: L1571
- 描述: 弹出页面设置对话框（缓存 PageFormat）。

#### `public static void printCommand()`
- 行号: L1596
- 描述: 打印当前 EditWindow：选打印机、关双缓冲、计算打印区域，最后 PrintJobAWT 执行打印。

#### `public static boolean quitCommand()`
- 行号: L1727
- 描述: 退出：preventLoss 收集需保存库，启动 QuitJob（保存库、保存展开状态与偏好、System.exit(0)）。

#### `public static Collection<RenameAndSaveLibraryTask> preventLoss(Library desiredLib, int action, MutableBoolean wantToMerge)`
- 行号: L1850
- 描述: **`[重要]`** 退出/关闭/替换前检查未保存库：对每个改动库弹 "Save before quitting/closing/replacing?"（Yes/No/Cancel/No to All，可合并时加 Merge），返回待保存任务列表；用户取消返回 null。

#### `public static int showFileMenuOptionDialog(Component parentComponent, Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue, String toolTipMessage)`
- 行号: L1914
- 描述: 基于 JOptionPane 但支持 ToolTip 文本的选项对话框，返回选项下标（关闭返回 -1）。

#### `public static void forceQuit()`
- 行号: L1963
- 描述: 强制退出：有改动库时提供"强制保存并退出/取消/不保存退出"，经确认后 System.exit(1)。

#### `public static boolean forceSave(boolean confirm)`
- 行号: L2010
- 描述: 强制保存全部库到当前目录下 "panic" 目录（不经 Job，可能产生坏库），confirm 为真时先弹确认框。

### 包含的菜单项清单

顶层 `_File`：
- `New Library...` → `newLibraryCommand()`
- `Open Library`（复用 `ToolBar.openLibraryCommand` 模板项）
- `Open Recent Library`（动态子菜单，内容由 `updateRecentlyOpenedLibrariesList()` 生成）
- 子菜单 `_Import`：
  - `CIF (Caltech Intermediate Format)...` → `importLibraryCommand(FileType.CIF, false,false,true,false)`
  - `GDS II (Stream)...` → `importLibraryCommand(FileType.GDS, false,false,true,false)`
  - `GDS II (Stream) Skeleton...` → `importLibraryCommand(FileType.GDS, false,false,true,true)`
  - `GDS Map File...` → `GDSMap.importMapFile()`
  - `EDIF ...` → `importLibraryCommand(FileType.EDIF, ...)`
  - `LEF ...` → `importLibraryCommand(FileType.LEF, ...)`
  - `DEF ...` → `importLibraryCommand(FileType.DEF, false,true,true,false)`
  - `DXF (AutoCAD)...` → `importLibraryCommand(FileType.DXF, false,false,false,false)`
  - `Spice Deck (Single file)...` → `importLibraryCommand(FileType.SPICE, false,false,false,false)`
  - `Spice Decks (Whole directory)...` → `importLibraryCommand(FileType.SPICE, true,false,false,false)`
  - `Gerber...` → `importLibraryCommand(FileType.GERBER, ...)`
  - `SUE ...` → `importLibraryCommand(FileType.SUE, false,false,true,false)`
  - `Verilog...` / `Applicon 860...` / `Bookshelf...` → 对应 FileType 的 `importLibraryCommand`
  - `Dais (Sun CAD)...`（条件 `IOTool.hasDais()`）→ `importLibraryCommand(FileType.DAIS, true,false,false,false)`
  - `Calibre DESIGNrev...`（条件 `IOTool.hasCalibreDRV()`）→ `importLibraryCommand(FileType.CALIBREDRV, false,false,true,false)`
  - `ELIB...` / `Readable Dump...` → 对应 FileType 的 `importLibraryCommand`
  - `Fast JELIB...`（仅 debug）→ `JELIB2.newJelibReader(true,true,false,true,true)`
  - `Text Cell Contents...` → `TextWindow.readTextCell()`
  - `User Preferences...` → `importPrefsCommand()`
  - `Project Settings...` → `ProjSettings.importSettings()`
  - `XML Error Logger...` → `ErrorLoggerTree.importLogger()`
- `Close Library` → `closeLibraryCommand(Library.getCurrent())`
- `Save Library`（复用 `ToolBar.saveLibraryCommand` 模板项）
- `Save Library As...` → `saveAsLibraryCommand(...)`（先 `checkInvariants()`）
- `Save All Libraries`（加速键 'S'）→ `saveAllLibrariesCommand()`
- `Save All Libraries in Format...` → `saveAllLibrariesInFormatCommand()`
- 子菜单 `_Export`：
  - `CIF ...` / `GDS II (Stream)...` / `EDIF ...` / `LEF ...` / `DEF ...` / `L...` / `Bookshelf...` → `exportCommand(对应 FileType, false)`
  - `GDS Map File...` → `GDSMap.exportMapFile()`
  - `Skill (Cadence Commands)...` / `Skill Exports Only ...`（条件 `IOTool.hasSkill()`）→ `exportCommand(SKILL/SKILLEXPORTSONLY, false)`
  - `Eagle...` / `ECAD...` / `Gerber...` / `Pads...` / `Telesis...` / `DFTM...` / `Flattened Rectangles...` → `exportCommand(对应 FileType, false)`
  - `Text Cell Contents...` → `TextWindow.writeTextCell()`
  - `PostScript...` → `exportCommand(FileType.POSTSCRIPT, false)`
  - `PNG ...` → `exportCommand(FileType.PNG, false)`；`SVG...` / `HPGL...` / `DXF ...` / `STL ...` → `exportCommand(对应 FileType, false)`
  - `ELIB (Version 6)...` → `saveLibraryCommand(cur, FileType.ELIB, true,false,false)`
  - `JELIB (Version 8.03)...` → `saveOldJelib()`
  - `User Preferences...` → `exportPrefsCommand()`
  - `Project Settings...` → `ProjSettings.exportSettings()`
- `Change Current Library...` → `ChangeCurrentLib.showDialog()`
- `List Libraries` → `CircuitChanges.listLibrariesCommand()`
- `Rename Library...` → `CircuitChanges.renameLibrary(...)`
- `Mark All Libraries for Saving` → `CircuitChangeJobs.markAllLibrariesForSavingCommand()`
- 子菜单 `Check Libraries`：`Check` → `checkAndRepairCommand(false)`；`Repair` → `checkAndRepairCommand(true)`；`Find Unused Library Files` → `CircuitChanges.findUnusedLibraryFiles()`
- 子菜单 `Project Management`：`Update` → `UpdateJob.updateProject()`；`Check Out This Cell` → `CheckOutJob.checkOutThisCell()`；`Check In This Cell...` → `CheckInJob.checkInThisCell()`；`Rollback and Release Check-Out` → `CancelCheckOutJob.cancelCheckOutThisCell()`；`Add This Cell` → `AddCellJob.addThisCell()`；`Remove This Cell` → `DeleteCellJob.removeThisCell()`；`Show History of This Cell...` → `HistoryDialog.examineThisHistory()`；`Get Library From Repository...` → `LibraryDialog.getALibrary()`；`Add Current Library To Repository` → `AddLibraryJob.addThisLibrary()`；`Add All Libraries To Repository` → `AddLibraryJob.addAllLibraries()`
- 子菜单 `CVS`（isEnabled=CVS.isEnabled()，可更新项）：`Commit All Open Libraries` → `Commit.commitAllLibraries()`；`Update Open Libraries` → `Update.updateOpenLibraries(UPDATE)`；`Get Status Open Libraries` → `updateOpenLibraries(STATUS)`；`List Editors Open Libraries` → `Edit.listEditorsOpenLibraries()`；`Update Project Libraries` / `Get Status Project Libraries` → `Update.updateProject(UPDATE/STATUS)`；`List Editors Project Libraries` → `Edit.listEditorsProject()`；`Checkout From Repository` → `CVS.checkoutFromRepository()`
- `Page Setup...` → `pageSetupCommand()`
- `Print...` → `printCommand()`
- `Preferences`（复用 `ToolBar.preferencesCommand` 模板项，非 Mac）
- `Quit`（'Q'，非 Mac）→ `quitCommand()`
- `Force Quit (and Save)` → `forceQuit()`

---

## EditMenu

**文件**: `com/sun/electric/tool/user/menus/EditMenu.java`（2127 行）

**职责**: 处理 "Edit" 下拉菜单的命令：剪贴板操作、撤销/重做、旋转/镜像/缩放/移动、擦除、属性与总线参数、弧编辑、文本、清理、工艺相关操作、层次选择与波形窗口联动等。菜单项多数直接委托给 `Clipboard`、`CircuitChanges`、`Highlighter` 等工具类。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L135
- 描述: **`[重要]`** 包内方法，构建 "_Edit" 菜单。计算 ctrl/ctrlShift 修饰键供移动命令使用；大量菜单项是就地匿名子类（run 里调对应命令）；子菜单有 Rotate、Mirror、Size、Move、Erase、Properties、Arc、Text、Cleanup Cell、Technology Specific（含 FPGA）、Technology Editing、Selection；`ToolBar.modesSubMenu`（选择模式）与 `ToolBar.undoCommand/redoCommand` 直接复用工具条模板项。

### 关键命令处理方法（public）

#### `public static void keyBindingsCommand()`
- 行号: L513
- 描述: 打开 EditKeyBindings 对话框编辑当前菜单栏的快捷键。

#### `public static void getInfoCommand(boolean doubleClick)`
- 行号: L524
- 描述: **`[重要]`** 显示高亮对象属性对话框：统计节点/弧/端口/文字/图形数量，单选时打开对应 GetInfo 对话框（节点/弧/Export/文字/Outline），双击还有工艺编辑对象与 SpecialProperties 的特殊处理，多选打开 GetInfoMulti。

#### `public static void seeAllParametersCommand()` / `public static void hideAllParametersCommand()` / `public static void defaultParamVisibilityCommand()`
- 行号: L650 / L658 / L666
- 描述: 显示 / 隐藏 / 恢复默认 选中节点上的参数可见性（各启动一个 ParameterVisibility Job，mode 0/1/2）。

#### `public static void updateParameters(boolean allLibraries)`
- 行号: L741
- 描述: 用原型参数更新选中节点（或全部库中所有 cell 实例）的参数，启动 UpdateParameters Job。

#### `public static void changeGlobalTextSize(double scale)`
- 行号: L802
- 描述: 按比例改变全局文字缩放（Increase/Decrease All Text Size 分别传 1.25/0.8），并重绘全部窗口。

#### `public static void addGDSText(boolean highV)`
- 行号: L818
- 描述: 给选中对象加 GDS 文字属性（highV 为真加 GDS_TEXT_HV_KEY，否则 GDS_TEXT_KEY）。

#### `public static void selectAllCommand()` / `public static void selectEasyCommand()` / `public static void selectHardCommand()`
- 行号: L862 / L879 / L888
- 描述: 全选当前 cell 对象 / 只选"易选"对象 / 只选"难选"对象（MessagesWindow 有焦点时转为其 selectAll）；统一走 `doSelection(mustBeEasy, mustBeHard)`（L893，按层可见性、多页原理图范围、文字显示偏好过滤）。

#### `public static void selectAllLikeThisCommand()`
- 行号: L1021
- 描述: 选中与当前高亮对象同原型/同属性名/同端口特性的全部对象。

#### `public static void selectNextLikeThisCommand(boolean next)`
- 行号: L1127
- 描述: 循环选中下一个/上一个同类型对象（按原型匹配节点/弧，按特性匹配 Export，按变量键匹配文字）。

#### `public static void selectObjectUnderCursor()`
- 行号: L1286
- 描述: 弹出光标下方对象列表菜单供用户选择（selectUnderCursorMenu，选中后高亮该对象）。

#### `public static void selectNothingCommand()`
- 行号: L1326
- 描述: 清空当前窗口高亮。

#### `public static void deselectAllArcsCommand()`
- 行号: L1337
- 描述: 从高亮列表中移除全部弧。

#### `public static void selectMakeEasyCommand()` / `public static void selectMakeHardCommand()`
- 行号: L1360 / L1371
- 描述: 把选中对象标记为易选/难选（SetEasyHardSelect Job）。

#### `public static void selectEnclosedObjectsCommand()`
- 行号: L1429
- 描述: 把矩形高亮区域替换为区域内对象的选择。

#### `public static void addToWaveformNewCommand()` / `public static void addToWaveformCurrentCommand()` / `public static void removeFromWaveformCommand()`
- 行号: L1484 / L1504 / L1523
- 描述: 把选中网络加入波形窗口（新面板/当前面板叠加）/从波形窗口移除。

#### `public static void insertJogInArcCommand()`
- 行号: L1542
- 描述: 在选中弧上交互式插入折点（临时切换 WindowFrame 监听器为 InsertJogInArcListener，松开鼠标后启动 InsertJogPoint Job 把弧一分为三）。

#### `public static void insertInductorInArcCommand()`
- 行号: L1727
- 描述: 在选中弧上交互式插入电感原语（InsertInductorInArcListener + InsertInductorPoint Job，寻找工艺中可连接的 INDUCT 节点并拆弧）。

#### `public static void showUndoListCommand()`
- 行号: L1924
- 描述: 显示撤销历史（Undo.showHistoryList）。

#### `public static void describeTechnologyCommand()`
- 行号: L1929
- 描述: 在消息窗口打印当前工艺描述（名称、比例、弧/引脚/接触/纯层节点清单）。

### 包含的菜单项清单

顶层 `_Edit`：
- `Cut`（'X'）→ `Clipboard.cut()`；`Copy`（'C'）→ `Clipboard.copy()`；`Paste`（'V'）→ `Clipboard.paste()`；`Duplicate`（'M'）→ `Clipboard.duplicate()`
- `Undo`（复用 `ToolBar.undoCommand`）；`Redo`（复用 `ToolBar.redoCommand`）
- `Show Undo List` → `showUndoListCommand()`
- `Repeat Last Action`（F7）→ `EMenuItem.repeatLastCommand()`
- 子菜单 `_Rotate`：`90 Degrees Clockwise` → `CircuitChanges.rotateObjects(2700)`；`90 Degrees Counterclockwise`（'J'）→ `rotateObjects(900)`；`180 Degrees` → `rotateObjects(1800)`；`Other...` → `rotateObjects(0)`
- 子菜单 `_Mirror`：`Up <-> Down` → `CircuitChanges.mirrorObjects(true)`；`Left <-> Right` → `mirrorObjects(false)`
- 子菜单 `Si_ze`：`Interactively`（'B'）→ `SizeListener.sizeObjects()`；`All Selected Nodes...` → `SizeListener.sizeAllNodes()`；`All Selected Arcs...` → `SizeListener.sizeAllArcs()`
- 子菜单 `Mo_ve`：`Spread...` → `Spread.showSpreadDialog()`；`Move Objects By...` → `MoveBy.showMoveByDialog()`；`Align to Grid` → `CircuitChanges.alignToGrid()`；`Move Objects Left/Right/Up/Down`（方向键）→ `ClickZoomWireListener.moveSelected(±1/0/±1, ...)`；`Move Objects More *`（Ctrl+方向键）→ `moveSelected(..., true, false)`；`Move Objects Most *`（Ctrl+Shift+方向键）→ `moveSelected(..., true, true)`；`Align Horizontally to Left/Right/Center` → `CircuitChanges.alignNodes(true, 0/1/2)`；`Align Vertically to Top/Bottom/Center` → `alignNodes(false, 0/1/2)`；`Cell Center to Center of Selection` → `CircuitChanges.cellCenterToCenterOfSelection()`
- 子菜单 `_Erase`：`Selected`（Delete/Backspace）→ `CircuitChanges.deleteSelected()`；`Arcs Connected to Selected Nodes` → `deleteArcsOnSelected(false)`；`Arcs Connected Between Selected Nodes` → `deleteArcsOnSelected(true)`
- `Array...`（F6）→ `Array.showArrayDialog()`
- `Change...`（'C' 无修饰）→ `Change.showChangeDialog()`
- 子菜单 `Propert_ies`：`Object Properties...`（'I'）→ `getInfoCommand(false)`；`Cell Parameters...` → `Attributes.showDialog()`；`Update Parameters on Node` → `updateParameters(false)`；`Update Parameters all Libraries` → `updateParameters(true)`；`Parameterize Bus Name` → `BusParameters.makeBusParameter()`；`Edit Bus Parameters...` → `BusParameters.showBusParametersDialog()`
- 子菜单 `Ar_c`：`Rigid` / `Not Rigid` / `Fixed Angle` / `Not Fixed Angle` → `CircuitChanges.arcRigidCommand()/arcNotRigidCommand()/arcFixedAngleCommand()/arcNotFixedAngleCommand()`；`Toggle Directionality` → `arcDirectionalCommand()`；`Toggle End Extension of Both Head/Tail` → `arcHeadExtendCommand()+arcTailExtendCommand()`；`Toggle End Extension of Head/Tail` → 各自命令；`Insert Jog In Arc` → `insertJogInArcCommand()`；`Insert Inductor In Arc` → `insertInductorInArcCommand()`；`Rip Bus` → `CircuitChanges.ripBus()`；`Connect Arcs that Cross Named Network...` → `CircuitChanges.connectOverlappingNetworks()`；`Curve through Cursor` / `Curve about Cursor` → `CurveListener.setCurvature(true/false)`；`Remove Curvature` → `CurveListener.removeCurvature()`
- `Selection Modes`（复用 `ToolBar.modesSubMenu`）
- 子菜单 `Te_xt`：`Find Text...`（'L'）→ `FindText.findTextDialog()`；`Change Text Size...` → `ChangeText.changeTextDialog()`；`Increase All Text Size`（'='）→ `changeGlobalTextSize(1.25)`；`Decrease All Text Size`（'-'）→ `changeGlobalTextSize(0.8)`；`Add Text Annotation`（'T'）→ `PaletteFrame.placeInstance("ART_message", null, false)`；`Add GDS Text` → `addGDSText(false)`；`Add HV GDS Text` → `addGDSText(true)`；`Edit Text Cell Externally...` → `ExternalEditing.editExternally()`
- 子菜单 `Clea_nup Cell`：`Cleanup Pins` → `CircuitChanges.cleanupPinsCommand(false, ep)`；`Cleanup Pins Everywhere` → `cleanupPinsCommand(true, ep)`；`Show Nonmanhattan` → `showNonmanhattanCommand()`；`Show Pure Layer Nodes` → `showPureLayerCommand()`；`Shorten Selected Arcs` → `shortenArcsCommand()`；`Show Redundant Pure-Layer Nodes` → `showRedundantPureLayerNodes()`
- 子菜单 `Technology Speci_fic`：`Toggle Port Negation`（'T'）→ `CircuitChanges.toggleNegatedCommand()`；`Artwork Color and Pattern...` → `ArtworkLook.showArtworkLookDialog()`；`Describe this Technology` → `describeTechnologyCommand()`；`Document Current Technology` → `Manipulate.describeTechnology(...)`；`Write XML of Current Technology...` → 就地写 XML（`Xml.Technology.writeXml`）；`Write XML of Technology from Old Electric Build...` → `writeXmlTechnologyFromElectricBuildCommand()`；子菜单 `FPGA`：`Read Architecture And Primitives...`/`Read Primitives...` → `FPGA.tech().readArchitectureFile(true/false)`；`Edit Pips...` → `programPips()`；`Show No Wires`/`Show Active Wires`/`Show All Wires` → `setWireDisplay(0/1/2)`；`Show Text`/`Hide Text` → `setTextDisplay(true/false)`
- 子菜单 `Technolo_gy Editing`：`Convert Technology to Library for Editing...` → `TechToLib.makeLibFromTech()`；`Convert Library to Technology...` → `LibToTech.makeTechFromLib()`；`Identify Primitive Layers` / `Identify Ports` → `Manipulate.identifyLayers(false/true)`；`Edit Library Dependencies...` → `Manipulate.editLibraryDependencies()`；`Edit Component Menu...` → `Manipulate.editComponentMenu()`；`Technology Creation Wizard...` → `TechEditWizard.techEditWizardCommand()`
- 子菜单 `_Selection`：`Select All`（'A'）→ `selectAllCommand()`；`Select All Easy` → `selectEasyCommand()`；`Select All Hard` → `selectHardCommand()`；`Select Nothing` → `selectNothingCommand()`；`Select All Like This` → `selectAllLikeThisCommand()`；`Select Next/Previous Like This` → `selectNextLikeThisCommand(true/false)`；`Select Object...` → `SelectObject.selectObjectDialog(null, false)`；`Select Object Under Cursor...`（'U'）→ `selectObjectUnderCursor()`；`Deselect All Arcs` → `deselectAllArcsCommand()`；`Make Selected Easy/Hard` → `selectMakeEasyCommand()/selectMakeHardCommand()`；`Push Selection`/`Pop Selection` → `wnd.getHighlighter().pushHighlight()/popHighlight()`；`Enclosed Objects` → `selectEnclosedObjectsCommand()`；`Show Next/Previous Error`（'>'/'<'）→ `showNextErrorCommand(true)/showPrevErrorCommand(true)`；`Show Next/Previous Error, same Window`（']'/'['）→ 同命令传 false；`Show Single Geometry`（'/'）→ `showSingleGeometryCommand()`；`Show Current Collection of Errors` → `ErrorLoggerTree.showCurrentErrors()`；`Add to Waveform in New Panel`（'A'）→ `addToWaveformNewCommand()`；`Add to Waveform in Current Panel`（'O'）→ `addToWaveformCurrentCommand()`；`Remove from Waveform`（'R'）→ `removeFromWaveformCommand()`

---

## CellMenu

**文件**: `com/sun/electric/tool/user/menus/CellMenu.java`（872 行）

**职责**: 处理 "Cell" 下拉菜单的命令：cell 的新建/编辑/重命名/复制/删除（经由 CellBrowser 对话框）、多页原理图管理、跨库复制、层次上下钻取、cell 版本管理、信息列表、展开/收缩实例、打包与抽取。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L93
- 描述: **`[重要]`** 包内方法，构建 "_Cell" 菜单（清单见下）。`E_xpand Cell Instances`/`Unexpand Cell Ins_tances` 两个子菜单的第一项分别复用 `ToolBar.expandOneLevelCommand`/`ToolBar.unexpandOneLevelCommand` 模板项。

### 关键命令处理方法（public）

#### `public static void cellBrowserCommand(CellBrowser.DoAction action)`
- 行号: L267
- 描述: 打开 CellBrowser 对话框执行指定动作（editCell/newInst/renameCell/duplicateCell/deleteCell）。

#### `public static class SetMultiPageJob` / `public static class DeleteMultiPageJob`
- 行号: L293 / L327
- 描述: 公开内部 Job 类：把 cell 设为多页（必要时打开 A 尺寸边框）/删除多页中的一页（删除该页电路、后续页下移、页码递减并修正各窗口页码）。

#### `public static class NewCellName extends EDialog`
- 行号: L692
- 描述: 公开内部对话框类："复制 cell / 替换实例"时询问新 cell 名（常量 GROUPNAME/CELLNAMEDUPLICATE/CELLNAMEREPLACE），确认后启动 `CellChangeJobs.DuplicateCell`。

### 包含的菜单项清单

顶层 `_Cell`：
- `New Cell...`（'N'）→ `newCellCommand()`（NewCell 对话框）
- `Edit Cell...` → `cellBrowserCommand(editCell)`；`Place Cell Instance...`（'N' 无修饰）→ `cellBrowserCommand(newInst)`；`Rename Cell...` → `cellBrowserCommand(renameCell)`；`Duplicate Cell...` → `cellBrowserCommand(duplicateCell)`；`Delete Cell...` → `cellBrowserCommand(deleteCell)`
- 子菜单 `_Multi-Page Cells`：`Make Cell Multi-Page` → `makeMultiPageCell()`；`Create New Page` → `createNewMultiPage()`；`Delete This Page` → `deleteThisMultiPage()`；`Edit Next Page` → `editNextMultiPage()`；`Convert old-style Multi-Page Schematics` → `ViewChanges.convertMultiPageViews()`
- `Cross-Library Copy...` → `crossLibraryCopyCommand()`（CrossLibCopy 对话框）
- 子菜单 `Merge Li_braries`：`Add Exports from Library...` → `ExportChanges.synchronizeLibrary()`；`Replace Cells from Library...` → `ExportChanges.replaceFromOtherLibrary()`
- 子菜单 `_Down Hierarchy`：`Down Hierarchy`（'D'）→ `downHierCommand(false,false)`；`Down Hierarchy, Keep Focus` → `downHierCommand(true,false)`；`Down Hierarchy, New Window` → `downHierCommand(false,true)`；`Down Hierarchy, Keep Focus, New Window` → `downHierCommand(true,true)`；`Down Hierarchy In Place`（'D' 无修饰）→ `downHierInPlaceCommand()`；`Down Hierarchy In Place To Object`（Shift+'D'）→ `downHierInPlaceToObject()`（几何搜索光标下对象，多个时弹 JPopupMenu 选择，descendToObject 逐层钻入）
- 子菜单 `_Up Hierarchy`：`Up Hierarchy`（'U'）→ `upHierCommand(false)`；`Up Hierarchy, Keep Focus` → `upHierCommand(true)`
- 子菜单 `Cell Viewing Histor_y`：`Go Back a Cell` → `changeCellHistory(true)`；`Go Forward a Cell` → `changeCellHistory(false)`
- `New Version of Current Cell` → `newCellVersionCommand()`（CircuitChanges.newVersionOfCell）
- `Delete Unused Old Versions` → `deleteOldCellVersionsCommand()`
- `Duplicate Current Cell...` → `duplicateCellCommand()`（NewCellName 对话框）
- `Replace Instance with Duplicate Cell...` → `replaceInstanceWithDuplicateCellCommand()`
- 子菜单 `Cell In_fo`：`Describe this Cell` → `CellLists.describeThisCellCommand()`；`General Cell Lists...` → `CellLists.generalCellListsCommand()`；`Summarize Cell Contents` / `Summarize Selected Cell Contents` → `CellLists.designSummaryCommand(false/true)`；`List Nodes/Arcs in this Cell` → `listNodesAndArcsInCellCommand()`；`List Cell Instances` → `listCellInstancesCommand()`；`List Cell Usage` / `List Cell Usage, Hierarchically` → `listCellUsageCommand(false/true)`；`Number of Transistors` → `numberOfTransistorsCommand()`；`Cell Graph, Entire Library` → `CircuitChanges.graphCellsInLibrary()`；`Cell Graph, From Current Cell` → `graphCellsFromCell()`；`Library Graph` → `graphLibraries()`
- `Cell Properties...` → `cellControlCommand()`（CellProperties 对话框）
- 子菜单 `E_xpand Cell Instances`：`Expand Cell Instances One Level`（复用 `ToolBar.expandOneLevelCommand`）；`All the Way` → `CircuitChanges.DoExpandCommands(false, Integer.MAX_VALUE)`；`Specified Amount...` → `DoExpandCommands(false, -1)`
- 子菜单 `Unexpand Cell Ins_tances`：`Unexpand Cell Instances One Level`（复用 `ToolBar.unexpandOneLevelCommand`）；`All the Way` → `DoExpandCommands(true, Integer.MAX_VALUE)`；`Specified Amount...` → `DoExpandCommands(true, -1)`
- `Look Inside Highlighted`（'P'）→ `peekCommand()`（临时放大高亮区域到底层）
- `Package Into Cell...` → `CircuitChanges.packageIntoCell()`
- 子菜单 `Extract Cell _Instance`：`One Level Down` → `CircuitChanges.extractCells(1)`；`All the Way` → `extractCells(Integer.MAX_VALUE)`；`Specified Amount...` → `extractCells(-1)`

---

## ExportMenu

**文件**: `com/sun/electric/tool/user/menus/ExportMenu.java`（120 行）

**职责**: 处理 "Export" 下拉菜单的命令：创建/重新导出/删除/移动/重命名 export 端口，以及端口的查看与批量操作。全部委托给 `ExportChanges` 或对话框类。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L37
- 描述: **`[重要]`** 包内方法，构建 "E_xport" 菜单（清单见下）。除 `Create Export...`（NewExport 对话框）与 `Manipulate Exports...`/`Manipulate Ports on Node...`（对话框）外，全部项直接调用 ExportChanges 静态命令。

### 包含的菜单项清单

顶层 `E_xport`：
- `Create Export...`（'E'）→ `new NewExport(TopLevel.getCurrentJFrame())`
- `Re-Export Everything` → `ExportChanges.reExportAll()`
- 子菜单 `Re-Export _Selected`：`Unwired Ports Only` → `ExportChanges.reExportSelected(false, true)`；`Wired and Unwired Ports` → `reExportSelected(true, true)`；`Wired Ports Only` → `reExportSelected(true, false)`
- `Re-Export Selected Port on All Nodes` → `ExportChanges.reExportSelectedPort()`
- `Re-Export Power and Ground` → `ExportChanges.reExportPowerAndGround()`
- 子菜单 `Re-Export _Highlighted Area`：同上三种模式 → `ExportChanges.reExportHighlighted(false, x, y)`
- 子菜单 `Re-Export D_eep Highlighted Area`：同上三种模式（深层）→ `ExportChanges.reExportHighlighted(true, x, y)`
- `Delete Export` → `ExportChanges.deleteExport()`
- `Delete Exports on Selected` → `ExportChanges.deleteExportsOnSelected()`
- `Delete Exports in Highlighted Area` → `ExportChanges.deleteExportsInArea()`
- `Move Export` → `ExportChanges.moveExport()`
- `Rename Export...` → `ExportChanges.renameExport()`
- `Export Connection Information` → `ExportChanges.exportConnectionList()`
- `Summarize Exports` → `ExportChanges.describeExports(true)`；`List Exports` → `describeExports(false)`
- `Show Exports` → `ExportChanges.showExports(null)`
- `Manipulate Exports...` → `ManipulateExports.showDialog()`
- `Follow Export Up Hierarchy` → `new ExportChanges.FollowExport()`
- `Show Ports on Node` → `ExportChanges.showPorts(null)`
- `Manipulate Ports on Node...` → `ManipulatePorts.showDialog()`

---

## ViewMenu

**文件**: `com/sun/electric/tool/user/menus/ViewMenu.java`（197 行）

**职责**: 处理 "View" 下拉菜单的命令：视图控制对话框、切换/编辑 cell 的各个视图（layout、schematic、icon、VHDL、documentation、skeleton），以及生成 icon/schematic/layout/skeleton/VHDL 视图。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L44
- 描述: 包内方法，构建 "_View" 菜单（清单见下）。各 "Edit X View" 项与 `editView(View)` 对应，Make 系列调用 ViewChanges 或 ToolMenu.makeVHDL。

### 关键命令处理方法（public）

#### `public static void viewControlCommand()`
- 行号: L89
- 描述: 打开 ViewControl 对话框。

#### `public static void changeViewCommand()`
- 行号: L95
- 描述: 弹框把当前 cell 改为另一视图（ICON 特殊不可改，选项里用 "Icon (cannot change into Icon view)" 占位），调用 ViewChanges.changeCellView。

#### `public static void editLayoutViewCommand()` / `editSchematicViewCommand()` / `editIconViewCommand()` / `editVHDLViewCommand()` / `editDocViewCommand()` / `editSkeletonViewCommand()`
- 行号: L126 / L131 / L136 / L141 / L146 / L151
- 描述: 各视图的编辑入口，均委托 `editView(View.X)`。

#### `public static void editOtherViewCommand()`
- 行号: L156
- 描述: 弹框从全部视图列表中选一个来编辑（含其他工艺/关联视图），委托 editView。

#### `private static void editView(View v)`
- 行号: L173
- 描述: 切换到指定视图：已是该视图则提示；有现成 otherView 则按其显示（尊重"在新窗口显示 cell"偏好）；否则询问是否创建空 cell 并启动 `ViewChanges.CreateAndViewCell`。

### 包含的菜单项清单

顶层 `_View`：
- `View Control...` → `viewControlCommand()`
- `Change Cell's View...` → `changeViewCommand()`
- `Edit Layout View` → `editLayoutViewCommand()`；`Edit Schematic View` → `editSchematicViewCommand()`；`Edit Icon View` → `editIconViewCommand()`；`Edit VHDL View` → `editVHDLViewCommand()`；`Edit Documentation View` → `editDocViewCommand()`；`Edit Skeleton View` → `editSkeletonViewCommand()`；`Edit Other View...` → `editOtherViewCommand()`
- `Make Icon View` → `ViewChanges.makeIconViewCommand()`
- `Make Schematic View` → `ViewChanges.makeSchematicView()`
- `Make Alternate Layout View...` → `ViewChanges.makeLayoutView()`
- `Make Skeleton View` → `ViewChanges.makeSkeletonViewCommand()`
- `Make VHDL View` → `ToolMenu.makeVHDL()`

---

## WindowMenu

**文件**: `com/sun/electric/tool/user/menus/WindowMenu.java`（1155 行）

**职责**: 处理 "Window" 下拉菜单的命令：缩放/平移/网格、测量、窗口平铺/级联/关闭、多显示器迁移、配色方案、可见层配置、波形窗口操作、消息窗口操作、侧栏位置；并提供窗口列表与可见层配置两个动态子菜单。

### 构建菜单与动态菜单

#### `public static KeyStroke getCloseWindowAccelerator()`
- 行号: L85
- 描述: 返回 "Close Window" 的加速键（Ctrl/Cmd+W）。

#### `static EMenu makeMenu()`
- 行号: L90
- 描述: **`[重要]`** 包内方法，构建 "_Window" 菜单。先构造数字小键盘的多重 KeyStroke（普通数字键 + NUMPAD 键 + Ctrl+Shift+KP 方向键以兼容 VNC），再构建 `visibleLayersMenu`（"Visible Layers"，每层一个项，10 层以上用 Shift+Ctrl+数字），随后组装清单（见下）。尾部用 SwingUtilities.invokeLater 延迟调用 `setDynamicVisibleLayerMenus()`。MDI/SDI 条件项（Move to Other Display 等）在构建期按 `TopLevel.isMDIMode()` 决定。构建后菜单引用存入 thisWindowMenu 供 setDynamicMenus 使用。

#### `static EMenuItem getHiddenWindowCycleMenuItem()`
- 行号: L426
- 描述: 包内方法。返回隐藏菜单中的 "Window Cycle"（'Q' 无修饰）菜单项：把焦点交给下一个窗口（WindowFrame.getWindows().next()）。

#### `public static void setDynamicMenus()`
- 行号: L437
- 描述: **`[重要]`** 重建 Window 菜单的动态窗口列表：为每个打开的 WindowFrame 生成一个 DynamicEMenuItem（显示窗口标题，点击 requestFocus），末尾追加 "Electric Messages" 项（聚焦消息窗口），按标题排序后 `thisWindowMenu.setDynamicItems(list)`。

#### `public static void setDynamicVisibleLayerMenus()`
- 行号: L457
- 描述: **`[重要]`** 更新 "Visible Layers" 子菜单：跳过硬编码配置，为 InvisibleLayerConfiguration 的每个用户配置生成 DynamicLayerVisibilityMenuItem 追加到所有窗口的菜单实例（先裁掉旧动态项）。

#### `public static void setLayerVisible(int level)`
- 行号: L487
- 描述: 设置当前窗口可见层级别：有保存的硬编码配置则按配置名设置，否则按工艺设置可见度级别（仅版图工艺有效）。

### 关键命令处理方法（public）

#### `public static void fullDisplay()` / `zoomOutDisplay()` / `zoomInDisplay()`
- 行号: L567 / L577 / L587
- 描述: 当前窗口内容填满屏幕 / 缩小 / 放大（委托 WindowContent）。

#### `public static void zoomBoxCommand()`
- 行号: L597
- 描述: 切换到 ClickZoomWireListener 并执行一次性 Zoom Box。

#### `public static void makeGridJustVisibleCommand()`
- 行号: L611
- 描述: 调整缩放使网格恰好可见（按网格间距与显示尺寸计算比例）。

#### `public static void matchOtherWindowCommand(int how)`
- 行号: L629
- 描述: 使当前窗口匹配另一个窗口（要求恰好两个窗口）：0=只匹配比例，1=X 偏移，2=Y 偏移，3=全部。

#### `public static void focusOnHighlighted()`
- 行号: L670
- 描述: 聚焦显示当前高亮对象（WindowContent.focusOnHighlighted）。

#### `public static void toggleGridCommand()`
- 行号: L683
- 描述: 切换网格显示：EditWindow 切换 setGrid；WaveformWindow 切换网格点；其他窗口提示不可用。

#### `public static void tileHorizontallyCommand()` / `tileVerticallyCommand()` / `cascadeWindowsCommand()`
- 行号: L706 / L757 / L808
- 描述: 水平/垂直平铺/级联排列窗口（按显示器区域分组，getArrangementWindowAreas 扣除消息窗口占据区域，支持多屏偏移）。

#### `public static void defaultBackgroundCommand()` / `blackBackgroundCommand()` / `whiteBackgroundCommand()`
- 行号: L977 / L1004 / L1031
- 描述: 恢复默认配色 / 黑底配色 / 白底配色（重置 User 各 ColorPrefType、Artwork 层与 Generic 工艺颜色，最后 repaintAllWindows）。

#### `public static void moveToOtherDisplayCommand()`
- 行号: L1061
- 描述: 把当前窗口移到下一块显示器（仅 SDI 模式，取 GraphicsEnvironment 中当前设备的下一个）。

#### `public static void rememberDisplayLocation()`
- 行号: L1087
- 描述: 记住当前主窗口与消息窗口的位置/尺寸到 User 偏好（仅 SDI 模式）。

#### `public static void goToPreviousSavedFocus()` / `goToNextSavedFocus()`
- 行号: L1112 / L1122
- 描述: 跳转到上一个/下一个保存的视图焦点（EditWindowFocusBrowser 的 goBack/goForward）。

### 包含的菜单项清单

顶层 `_Window`：
- `Fill Window`（numpad9）→ `fullDisplay()`；`Redisplay Window` → `ZoomAndPanListener.redrawDisplay()`；`Zoom Out`（numpad0）→ `zoomOutDisplay()`；`Zoom In`（numpad7）→ `zoomInDisplay()`
- 子菜单 `Special _Zoom`：`Focus on Highlighted`（'F'）→ `focusOnHighlighted()`；`Zoom Box` → `zoomBoxCommand()`；`Make Grid Just Visible` → `makeGridJustVisibleCommand()`；`Match Other Window` → `matchOtherWindowCommand(0)`
- `Pan Left/Right/Up/Down`（numpad 4/6/8/2）→ `ZoomAndPanListener.panXOrY(...)`
- 子菜单 `Special _Pan`：`Center Selection` → `ZoomAndPanListener.centerSelection()`；`Center Cursor`（numpad5）→ `centerCursor(...)`；`Match Other Window in X/Y/X, Y, and Scale` → `matchOtherWindowCommand(1/2/3)`
- `Go To Previous Focus` → `goToPreviousSavedFocus()`；`Go To Next Focus` → `goToNextSavedFocus()`；`Set Focus...` → `SetFocus.showSetFocusDialog()`
- `Toggle Grid`（'G'）→ `toggleGridCommand()`
- 子菜单 `Me_asurements`：`Toggle Measurement Mode` → `ToolBar.setCursorMode(CursorMode.MEASURE)`；`Clear Measurements` → `MeasureListener.theOne.reset()`
- 子菜单 `Ad_just Position`：`Tile Horizontally` → `tileHorizontallyCommand()`；`Tile Vertically`（F9）→ `tileVerticallyCommand()`；`Cascade` → `cascadeWindowsCommand()`
- `Close Window`（Ctrl/Cmd+W）→ `closeWindowCommand()`（WindowFrame.finished）
- （SDI）`Move to Other Display` → `moveToOtherDisplayCommand()`；（SDI）`Remember Location of Display` → `rememberDisplayLocation()`
- `Visible Layers`（动态子菜单，项 → `setLayerVisible(level)`；用户配置项 → `setInvisibleLayerConfiguration(cName)`）
- 子菜单 `_Color Schemes`：`Restore Default Colors` → `defaultBackgroundCommand()`；`Black Background Colors` → `blackBackgroundCommand()`；`White Background Colors` → `whiteBackgroundCommand()`；`Cadence Colors, Layers and Keystrokes` → `importCadencePreferences()`
- 子菜单 `W_aveform Window`：`Save/Restore Waveform Window Configuration ...` → `WaveformWindow.saveConfiguration()/restoreConfiguration()`；`Refresh Simulation Data` → `refreshSimulationData()`；`Clear All Signals in Waveform Window` → `clearSimulationData()`；`Export Simulation Data...` / `... As CSV...` → `exportSimulationData()/exportSimulationDataAsCSV(...)`；`Plot Simulation Data On Screen` / `... as PS...` → `plotSimulationData(...)`；`Fill Only in X/Y` → `fillInX()/fillInY()`；`Create New Waveform Panel` → `makeNewPanel(-1)`；`Toggle Horizontal Panel Lock` → `togglePanelXAxisLock()`；`Show Points and Lines`/`Show Lines`/`Show Points` → `toggleShowPoints()`；`Toggle Grid Points` → `toggleGridPoints()`；`Increase/Decrease Minimum Panel Height` → `growPanels(1.25/0.8)`；VCR 五项（Rewind/Play Backwards/Stop/Play/To End/Faster/Slower）→ `vcrClickXxx()`；`Generate Digital Signal from Analog Signal (0.5v threshold)` → 就地构造 DerivedSignal 并 new WaveSignal
- 子菜单 `_Messages Window`：`Tile with Edit Window` → `MessagesWindow.tileWithEdit()`；`Save Messages...` → `MessagesStream.getMessagesStream().save()`；`Clear` → `MessagesWindow.clearAll()`；`Set Font...` → `MessagesWindow.selectFont()`
- （插件）`MenuCommands.makeExtraMenu("j3d.ui.J3DMenu", true)` — 3D 查看菜单
- 子菜单 `Side _Bar`：`On Left` / `On Right` → `WindowFrame.setSideBarLocation(true/false)`

---

## ToolMenu

**文件**: `com/sun/electric/tool/user/menus/ToolMenu.java`（3063 行）

**职责**: 处理 "Tools" 下拉菜单的命令：DRC/ERC/NCC、各类仿真（内建/SPICE/Verilog/其他）、网络查询、逻辑努力（Logical Effort）、布局规划/布线、电感、生成器、硅编译器、压缩，以及语言脚本菜单。是最大的菜单类，各子菜单的项基本一行一个委托。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L180
- 描述: **`[重要]`** 包内方法，构建 "_Tools" 菜单。先构建 `languageMenu`（"Languages"，之后 invokeLater 调 setDynamicLanguageMenu 追加动态脚本）与 `expRoutingMenu`（"Experimental Routers"，按 RoutingFrame.getRoutingAlgorithms() 生成 DynamicExperimentalRoutingMenuItem）；IRSIM 相关项按 `IRSIM.hasIRSIM()` 条件添加；pcell 插件菜单经 `MenuCommands.makeExtraMenu("pcell.gui.MainMenu", false)` 插入；最后是 "List Tools" 与 languageMenu。

### 关键命令处理方法（public，按声明顺序节选）

#### `public static void optimizeEqualGateDelaysCommand(boolean newAlg)`
- 行号: L900
- 描述: 逻辑努力优化：把当前 cell 置于全局上下文后调用 LETool.optimizeEqualGateDelays（newAlg 选择算法，menu 中两个项分别传 true/false）。

#### `public static void printLEInfoCommand()`
- 行号: L921
- 描述: 打印高亮节点的逻辑努力信息（LETool.printResults）。

#### `public static void backAnnotateCommand()`
- 行号: L950
- 描述: 回标线长：启动 BackAnnotateJob，跑 NCC 后用版图网络几何长度更新原理图线模型属性。

#### `public static void layerCoverageCommand(LayerCoverageTool.LCMode func, GeometryHandler.GHMode mode)`
- 行号: L966
- 描述: 层覆盖命令统一入口（AREA 列表 / IMPLANT 注入生成）。

#### `public static void clearSizesNodableCommand()` / `clearSizesCommand()`
- 行号: L1075 / L1103
- 描述: 清除选中节点 / 全部库中存储的逻辑努力尺寸。

#### `public static void estimateDelaysCommand()`
- 行号: L1114
- 描述: 估算每个网络的延迟比（晶体管宽度加权金属/多晶/扩散半周长），打印结果。

#### `public static void addLEAttribute()`
- 行号: L1194
- 描述: 给选中的 Export 添加 LE 属性（AddLEAttribute Job）。

#### `public static void loadResourceLibraries(String libraryNameWithExtension, FileType type)`
- 行号: L1228
- 描述: 从 jar 资源加载库（LibFile.getLibFile + FileMenu.ReadLibrary）。

#### `public static void showNetworkCommand()`
- 行号: L1272
- 描述: 高亮显示当前窗口 cell 的选中网络（含 3D 高亮）。

#### `public static void listNetworksCommand()` / `listConnectionsOnNetworkCommand()` / `listExportsOnNetworkCommand()` / `listExportsBelowNetworkCommand()` / `listGeometryOnNetworkCommand(GeometryHandler.GHMode mode)`
- 行号: L1292 / L1353 / L1444 / L1477 / L1600
- 描述: 列出网络清单（含弧与 Export）/ 列出所选网络的连接（跨节点端口）/ 沿层次向上向下找 Export / 列出网络上的几何（LayerCoverageTool）。

#### `public static void calculateNetworkQualityCommand()`
- 行号: L1622
- 描述: 用 WireQualityMetric 计算选中网络的布线质量并打印均值。

#### `public static void listGeomsAllNetworksCommand()`
- 行号: L1800
- 描述: 列出全部网络的线长（ListGeomsAllNetworksJob）。

#### `public static void showPowerAndGround()` / `public static void validatePowerAndGround(boolean repair)`
- 行号: L2027 / L2091
- 描述: 高亮电源/地网络；校验（或修复）全部库中 Export 的 PWR/GND 特性。

#### `public static void addMultiplierCommand()`
- 行号: L2135
- 描述: 给选中节点加 "M" 倍乘因子（AddMultiplier Job，变量 SimulationTool.M_FACTOR_KEY）。

#### `public static void makeTemplate(Variable.Key templateKey, ElectricObject eo, String value)` / `public static void makeTemplate(Variable.Key templateKey, boolean selectKey)`
- 行号: L2170 / L2182
- 描述: 为指定对象 / 当前 cell 创建 SPICE/Verilog 输出模板变量（selectKey 时先选文件）。

#### `public static void getUnroutedArcCommand()`
- 行号: L2264
- 描述: 把当前布线弧类型设为 Generic 的 unrouted_arc。

#### `public static void padFrameGeneratorCommand()`
- 行号: L2268
- 描述: 从 PADARR 文件生成 Pad Frame。

#### `public static void listToolsCommand()`
- 行号: L2282
- 描述: 列出 Electric 全部工具及其状态（On/Off、Background、Correcting、Incremental、Analysis、Synthesis）。

#### `public static void javaBshScriptCommand()`
- 行号: L2309
- 描述: 选择并运行 Bean Shell 脚本（EvalJavaBsh.runScript）。

#### `public static void setDynamicLanguageMenu()`
- 行号: L2336
- 描述: **`[重要]`** 重建 Languages 子菜单：保留前 4 个静态项，把 LanguageScripts.getScripts() 中每个预置脚本（.bsh→JAVA、.py/.jy→JYTHON，支持助记符前缀）生成为 DynamicLanguageMenuItem 追加到所有菜单实例。

#### `public static class DynamicLanguageMenuItem extends EMenuItem`
- 行号: L2365
- 描述: 公开动态菜单项类：getDescription() 返回 "Language Scripts"，run() 按类型执行 BeanShell/Jython 脚本，updateButtons() 为空（不参与全局刷新）。

#### `public static void doSiliconCompilation(Cell cell, boolean doItNow, SilComp.SilCompPrefs prefs, EditingPreferences ep)`
- 行号: L2458
- 描述: 硅编译主流程：按视图新旧决定是否转 VHDL / 编译 VHDL / 编译 Verilog，确保标准单元库存在，最后 doSilCompActivityNoJob 执行布局布线并显示。

#### `public static void doPlaceRatsNest(Cell cell, boolean doItNow)`
- 行号: L2516
- 描述: 把 cell 转成 rats-nest 布局（GENERATE_CELL | SHOW_CELL 活动集）。

#### `public static void compileVHDL()` / `compileVerilog()` / `makeVHDL()`
- 行号: L2564 / L2583 / L2602
- 描述: 编译当前 VHDL/Verilog cell 为网表视图 / 把原理图转成 VHDL（DoSilCompActivity Job）。

#### `public static boolean doSilCompActivityNoJob(Cell cell, int activities, SilComp.SilCompPrefs prefs, boolean doItNow)`
- 行号: L2635
- 描述: 按活动位集执行硅编译活动（doItNow 时同步执行，否则启动 DoSilCompActivity Job）。

#### `public static void parasiticCommand()`
- 行号: L2860
- 描述: 对选中网络计算寄生参数（ParasiticTool）。

#### `public static void importAssuraDrcErrors()` / `importCalibreDrcErrors()` / `importCalibreLVSErrors()`
- 行号: L2873 / L2893 / L2926
- 描述: 导入 Assura/Calibre 的 DRC/LVS 错误报告并以 ErrorLogger 显示（先用 GDS.buildUniqueNames 建名字映射）。

#### `public static void showNCCAnnotations()`
- 行号: L2916
- 描述: 打开 NCC 注释对话框。

#### `public static void importDRCDeck()`
- 行号: L2959
- 描述: 从 XML 导入 DRC deck（DRCTemplate.importDRCDeck，成功后 ImportDRCDeckJob 把规则挂到对应 foundry）。

#### `public static void runNccSchematicCrossProbing()`
- 行号: L3026
- 描述: 运行原理图交叉探查 NCC（NccCrossProbing）。

### 包含的菜单项清单

顶层 `_Tools`：
- 子菜单 `_DRC`：`Check Hierarchically`（F5）→ DRC 检查（多线程版图走 MTDRCLayoutTool）；`Check Selection Hierarchically` → `DRC.checkDRCHierarchically(...)`；`Add Skip Annotation to Cell` → `DRC.makeDRCAnnotation()`；`Check Area Coverage` → `LayerCoverageTool.layerCoverageCommand(...)`；`List Layer Coverage on Cell` → `layerCoverageCommand(AREA, ALGO_SWEEP)`；`Import Assura DRC Errors for Current Cell...` → `importAssuraDrcErrors()`；`Import Calibre DRC Errors for Current Cell...` → `importCalibreDrcErrors()`；`Export DRC Deck...` → `exportDRCDeck()`；`Import DRC Deck...` → `importDRCDeck()`
- 子菜单 `Simulation (Built-in)`：`IRSIM: Simulate Current Cell`（条件 hasIRSIM）→ `SimulationTool.startSimulation(IRSIM_ENGINE, null,...)`；`IRSIM: Write Deck...` → `FileMenu.exportCommand(IRSIM, true)`；`IRSIM: Simulate Deck...` → `startSimulation(IRSIM_ENGINE, "", ...)`；`ALS: Simulate Current Cell` → `startSimulation(ALS_ENGINE, ...)`；`Set Signal High/Low/Undefined at Main Time`（'V'/'G'/'X'）→ `SimulationTool.setSignalHigh()/setSignalLow()/setSignalX()`；`Set Clock on Selected Signal...` → `setClock()`；`Update Simulation Window` → `SimulationTool.update()`；`Get Information about Selected Signals` → `showSignalInfo()`；`Clear Selected Stimuli` → `removeSelectedStimuli()`；`Clear All Stimuli on Selected Signals` → `removeStimuliFromSignal()`；`Clear All Stimuli` → `removeAllStimuli()`；`Save Stimuli to Disk...` → `saveStimuli()`；`Restore Stimuli from Disk...` → `restoreStimuli(null)`
- 子菜单 `Simulation (_Spice)`：`Write Spice Deck...` → `FileMenu.exportCommand(SPICE, true)`；`Write CDL Deck...` → `exportCommand(CDL, true)`；`Plot Simulation Output, Choose File...` → `plotChosen()`；`Plot Simulation Output, Guess File` → `SimulationData.plotGuessed(...)`；`Set Spice Model...` → `SimulationTool.setSpiceModel()`；`Add Multiplier` → `addMultiplierCommand()`；`Add Flat Code` → `makeTemplate(SPICE_CODE_FLAT_KEY, false)`；`Set Generic Spice Template` / `Set Spice 2/3 Template` / `Set Ngspice/HSpice/PSpice/GnuCap/SmartSpice/Assura HSpice/Calibre Spice Template` / `Set CDL Template` / `Set Xyce/JoSIM Template` → `makeTemplate(对应 SPICE_*_KEY, false)`；`Set Netlist Cell From File` → `makeTemplate(SPICE_NETLIST_FILE_KEY, true)`
- 子菜单 `Simulation (_Verilog)`：`Write Verilog Deck...` → `exportCommand(VERILOG, true)`；`Write Verilog-A Deck...` → `exportCommand(VERILOGA, true)`；Plot 两项同上；`Set Verilog Template` → `makeTemplate(VERILOG_TEMPLATE_KEY, false)`；`Set Verilog Default Parameter` → `makeTemplate(VERILOG_DEFPARAM_KEY, false)`；子菜单 `Set Verilog _Wire`：`Wire/Trireg/Default` → `SimulationTool.setVerilogWireCommand(0/1/2)`；子菜单 `Transistor _Strength`：`Weak/Normal` → `setTransistorStrengthCommand(true/false)`
- 子菜单 `Simulation (_Others)`：`Write Maxwell/Tegas/SILOS/PAL/DualEval Deck...` → `exportCommand(对应 FileType, true)`；`Write IRSIM Deck...`（仅无 IRSIM 时）→ `exportCommand(IRSIM, true)`；`Write ESIM/RNL/RSIM/COSMOS/MOSSIM Deck...` → `exportCommand(对应 FileType, true)`
- 子菜单 `_ERC`：`Check Wells` → `ERCWellCheck.analyzeCurCell()`；`Antenna Check` → `ERCAntenna.doAntennaCheck()`
- 子菜单 `_NCC`：`Schematic and Layout Views of Cell in Current Window` → `new NccJob(1)`；`Cells from Two Windows` → `new NccJob(2)`；`Copy Schematic User Names to Layout` → `new SchemNamesToLay.RenameJob()`；`Copy All Schematic Names to Layout` → `new AllSchemNamesToLay.RenameJob()`；`Highlight Equivalent` → `HighlightEquivalent.highlight()`；`Run NCC for Schematic Cross-Probing` → `runNccSchematicCrossProbing()`；子菜单 `Add NCC _Annotation to Cell`（12 项）→ `NccCellAnnotations.makeNCCAnnotationMenuCommand("<注释文本>")`（vdd/gnd/ignore/skipNCC/notSubcircuit/flattenInstances/joinGroup/transistorType/resistorType/forcePartMatch/forceWireMatch/blackBox）；`Show NCC Annotations...` → `showNCCAnnotations()`；`Import Calibre LVS Errors for Current Cell...` → `importCalibreLVSErrors()`
- 子菜单 `Net_work`：`Show Network`（'K'）→ `showNetworkCommand()`；`List Networks` → `listNetworksCommand()`；`List Connections on Network` → `listConnectionsOnNetworkCommand()`；`List Exports on Network` → `listExportsOnNetworkCommand()`；`List Exports below Network` → `listExportsBelowNetworkCommand()`；`List Geometry on Network` → `listGeometryOnNetworkCommand(ALGO_SWEEP)`；`Calculate Network Quality` → `calculateNetworkQualityCommand()`；`Show All Networks` → `showAllNetworksCommand()`；`List Total Wire Lengths on All Networks` → `listGeomsAllNetworksCommand()`；`Show Undriven Networks` → `showUndrivenNetworks()`；`Extract Current Cell` / `Extract Current Hierarchy` → `Connectivity.extractCurCell(false/true)`；`Show Power and Ground` → `showPowerAndGround()`；`Validate Power and Ground` → `validatePowerAndGround(false)`；`Repair Power and Ground` → `new RepairPowerAndGround()`
- 子菜单 `_Logical Effort`：`Optimize for Equal Gate Delays` → `optimizeEqualGateDelaysCommand(true)`；`... (no caching)` → `(false)`；`List Info for Selected Node` → `printLEInfoCommand()`；`Back Annotate Wire Lengths for Current Cell` → `backAnnotateCommand()`；`Clear Sizes on Selected Node(s)` → `clearSizesNodableCommand()`；`Clear Sizes in all Libraries` → `clearSizesCommand()`；`Estimate Delays` → `estimateDelaysCommand()`；`Add LE Attribute to Selected Export` → `addLEAttribute()`；`Load Logical Effort Libraries (Purple, Red, and Orange)`（资源存在时）→ `loadResourceLibraries("purpleGeneric180.jelib", JELIB)`；`Loading Resource Library 'latchesL'`（资源存在时）→ `loadResourceLibraries("anopener.jelib", JELIB)`
- 子菜单 `_Placement`：`Floorplan and Place Current Cell` → `Placement.floorplanCurrentCell()`；`Place Current Cell with Preferred Algorithm` → `Placement.placeCurrentCell()`
- 子菜单 `_Routing`：`Enable Auto-Stitching`（CheckBox，isSelected=Routing.isAutoStitchOn()，setSelected=Routing.setAutoStitchOn）→ 勾选即切换；`Auto-Stitch Now` → `AutoStitch.autoStitch(false, true)`；`Auto-Stitch Highlighted Now`（F2）→ `autoStitch(true, true)`；`Enable Mimic-Stitching`（CheckBox，同模式）；`Mimic-Stitch Now`（F1）→ `MimicStitch.mimicStitch(ep, true)`；`Mimic Selected` → `Routing.getRoutingTool().mimicSelected(ep)`；`Maze Route` → `Maze.mazeRoute()`；`River-Route` → `River.riverRoute()`；`Sea-Of-Gates Route this Cell` / `Sea-Of-Gates Route Sub-Cells` → `SeaOfGates.seaOfGatesRoute(false/true)`；`Sea-Of-Gates Cell Properties...` → `SeaOfGatesCell.showSeaOfGatesCellDialog()`；`Clock Routing...` → `ClockRouter.routeHTree()`；`Experimental Routers`（动态子菜单，项 → `DoExperimentalRoutingJob(rf, cell)`）；`Sun Lava Router`（条件 hasSunRouter）→ `Routing.sunRouteCurrentCell()`；`Unroute Network` → `Routing.unrouteCurrent()`；`Unroute Segment` → `unrouteCurrentSegment()`；`Get Unrouted Wire` → `getUnroutedArcCommand()`；`Copy Routing Topology` → `Routing.copyRoutingTopology()`；`Paste Routing Topology` → `pasteRoutingTopology()`
- 子菜单 `_Inductors`：`Inductor Management...` → `ManageInductors.showInductorManagementDialog()`；`Write FastHenry Deck...` → `exportCommand(FASTHENRY, true)`
- 子菜单 `_Generation`：`Coverage Implants Generator` → `layerCoverageCommand(IMPLANT, ALGO_SWEEP)`；`Pad Frame Generator...` → `padFrameGeneratorCommand()`；`ROM Generator...` → `ROMGenerator.generateROM()`；`MOSIS CMOS PLA Generator...` → `PLA.generate()`；`Multi-Finger Transistor Cell...` → `MultiFingerTransistor.showMultiFingerTransistorDialog()`；`Acute Angle Fill` → `acuteAngleFill()`；`Stitch-Based Fill Generator from doc input` → `new StitchFillJob(cell, cell.getLibrary(), false)`；`... from open windows` → `new StitchFillJob(null, null, false)`；`Fill (MoCMOS)...` → `FillGenDialog.openFillGeneratorDialog(MoCMOS)`；`Fill (TSMC180)...`/`Fill (CMOS90)...`（条件工艺存在）→ 同对话框；`Generate gate layouts (MoCMOS)` / `(TSMC180)` / `(CMOS90)` / `(current tech，仅 debug)` → `GateLayoutGenerator.generateFromSchematicsJob(工艺)`
- 子菜单 `Silicon Co_mpiler`：`Convert Current Cell to Layout` → `doSiliconCompilation(...)`；`Convert Current Cell to Rats-Nest Structure` → `doPlaceRatsNest(...)`；`Compile VHDL to Netlist View` → `compileVHDL()`；`Compile Verilog to Netlist View` → `compileVerilog()`
- 子菜单 `_Compaction`：`Do Compaction` → `Compaction.compactNow(...)`
- （插件）`MenuCommands.makeExtraMenu("pcell.gui.MainMenu", false)`
- `List Tools` → `listToolsCommand()`
- `Languages`（动态子菜单）：`Run Java Bean Shell Script...` → `javaBshScriptCommand()`；`Run Jython Script...`（条件 hasJython）→ `jythonScriptCommand()`；`Manage Scripts...` → `new LanguageScripts()`；其后为 `setDynamicLanguageMenu()` 追加的预置脚本项

---

## HelpMenu

**文件**: `com/sun/electric/tool/user/menus/HelpMenu.java`（141 行）

**职责**: 处理 "Help" 下拉菜单的命令：关于对话框、用户手册（英文/俄文）、快捷键列表、3D 展示与示例库加载。

### 构建菜单

#### `static EMenu makeMenu()`
- 行号: L45
- 描述: 包内方法，构建 "_Help" 菜单。条件项：About 与分隔线仅非 Mac 平台显示（Mac 由系统菜单提供）；俄文手册按 `ManualViewer.hasRussianManual()`；3D Showcase 按 `ManualViewer.isJava3DAvailable()`。

### 关键命令处理方法（public）

#### `public static void aboutCommand()`
- 行号: L83
- 描述: 打开 About 对话框。

#### `private static void loadSamplesLibrary(String fileName, String cellName)`
- 行号: L93
- 描述: 从 jar 的 helphtml/ 目录加载示例库（已加载则仅提示），经 `FileMenu.ReadLibrary(url, FileType.JELIB, cellName)` 打开并定位指定 cell。

### 包含的菜单项清单

顶层 `_Help`：
- `About Electric...`（非 Mac）→ `aboutCommand()`
- `User's Manual...` → `ManualViewer.userManualCommand()`
- `User's Manual (Russian)...`（条件 hasRussianManual）→ `ManualViewer.userManualRussianCommand()`
- `Show Key Bindings` → `MenuCommands.menuBar().keyBindingManager.printKeyBindings()`
- 子菜单 `_3D Showcase`（条件 isJava3DAvailable）：`Load Library` → `loadSamplesLibrary("floatingGates", "topCell")`；`3D View of Cage Cell` → `ManualViewer.open3DSample("floatingGates", "topCell", "3D ShowCase")`
- `Load Sample Cells Library` → `loadSamplesLibrary("samples", "tech-MOSISCMOS")`

---

## PublicDebugMenu

**文件**: `com/sun/electric/tool/user/menus/PublicDebugMenu.java`（504 行）

**职责**: 构建 "Debug" 调试菜单（作者 Felix Schmdit）。仅在 `Job.getDebug()` 时经 `MenuCommands.makeTestMenus()` 挂到菜单栏。包含 ACL2/SVEX 处理、Spice 读取、DRC 最小面积测试、Fast JELIB 读取器与 SeaOfGates 路由器等试验性命令。

### 构建菜单

#### `static EMenuItem makeMenu()`
- 行号: L69
- 描述: **`[重要]`** 包内方法，构建 "Debug" 菜单：ACL2 子菜单（14 个就地匿名项）、Spice 子菜单、DRC 子菜单（3 项，MinArea）、Fast JELIB reader 子菜单（6 项，由 `jelibItem` 生成）与 SeaOfGatesRouter 子菜单（由 `sogItem` 与就地匿名项组合）。

#### `private static EMenuItem jelibItem(String text, boolean instantiate, boolean doBackup, boolean getPrimitiveBounds, boolean doSnapshot, boolean doDatabase)`
- 行号: L399
- 描述: 生成一个 Fast JELIB 读取器菜单项，run() 调 `JELIB2.newJelibReader(instantiate, doBackup, getPrimitiveBounds, doSnapshot, doDatabase)`。

#### `private static EMenuItem sogItem(String text, SeaOfGatesHandlers.Save save)` / `sogItem(String text, Job.Type jobType)`
- 行号: L417 / L434
- 描述: 生成 SeaOfGates 试验菜单项：前者 run() 调 `SeaOfGatesHandlers.startInJob(cell, null, defaultVersion, save)`；后者启动 `SeaOfGatesJob(cell, jobType)`。

### 包含的菜单项清单

顶层 `Debug`：
- 子菜单 `ACL2`：
  - `Gen phase FSM for Tutorial` → `GenFsmNew.genFsm(TutorialHints.class, f, designName)`（先选 .sao 文件）
  - `Import SAO...` → `FileMenu.importLibraryCommand(FileType.SAO, false,false,false,false)`
  - `Count objects in SAO file` → ACL2Reader 统计
  - `Generate pkg-imports.dat` → `GenPkgImports.gen(f)`
  - `Dump SVEX design` → `ACL2DesignJobs.dump(DesignHints.Dummy.class, f, outPath)`
  - `Dump SVEX design from Tutorial` → `ACL2DesignJobs.dump(TutorialHints.class, f, outPath)`
  - `Make Trace Svtv for Tutorial` → `TraceSvtvJobs.makeTraceSvtv(...)`；`Read Trace Svtv for Tutorial` → `readTraceSvtv(...)`；`Test Trace Svtv for Tutorial` → `testTraceSvtv(...)`
  - `Dedup SVEX design` → `ACL2DesignJobs.dedup(...)`
  - `Show SVEX assigns` → `ACL2DesignJobs.showAssigns(...)`
  - `Named -> Indexed` → `ACL2DesignJobs.namedToIndexed(...)`
  - `Normalize named assigns` / `Normalize indexed assigns` → `ACL2DesignJobs.normalizeAssigns(f, designName, false/true)`
  - `Gen FSM for ALU` → `ACL2DesignJobs.genAlu(f, outPath)`
  - `Show Tutorial Libs` → `ACL2DesignJobs.showTutorialSvexLibs(f)`
- 子菜单 `Spice`：`Read Spice` → `SpiceNetlistReader.readFile(spicePath, true)` 后写 `/tmp/output.spi`
- 子菜单 `DRC`：`Check Minimum Area...` → `MinArea.checkMinareaLay()`；`Import Minimum Area Test...` → `MinArea.readMinareaLay()`；`Export Minimum Area Test...` → `MinArea.writeMinareaLay()`
- 子菜单 `Fast JELIB reader`：`Database` / `Snapshot` / `primitiveBounds` / `doBackup` / `instantiate` / `only parse` → `JELIB2.newJelibReader(不同布尔组合)`
- 子菜单 `SeaOfGatesRouter`：`Animation` / `Partial-Animation` / `Once` → `SeaOfGatesHandlers.startInJob(..., SAVE_SNAPSHOTS/SAVE_PERIODIC/SAVE_ONCE)`；`Dummy on CHANGE` / `Dummy on SERVER_EXAMINE` / `Dummy on CLIENT_EXAMINE` → `new SeaOfGatesJob(cell, jobType)`；`Dummy on client` → `routeIt(cell, ep, System.out)`；`Dummy in Thread` → `new SeaOfGatesThread(cell, ep).start()`
