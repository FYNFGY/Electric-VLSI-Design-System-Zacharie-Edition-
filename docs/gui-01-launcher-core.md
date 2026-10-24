# GUI 启动与核心类（UserInterfaceMain / User）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user。本文件由 AI 生成。

## 模块概述

`com.sun.electric.tool.user` 是 Electric 的用户 GUI 模块。`UserInterfaceMain` 是 GUI 版用户界面的启动入口与运行中枢：它继承 `AbstractUserInterface`，在 EDT（事件分发线程）上完成 EventQueue 替换、偏好加载、顶层窗口初始化，并把数据库快照变化（`showSnapshot`）、任务终止（`terminateJob`）、错误报告（`reportLog`）、进度对话框等抽象接口落到具体的 Swing 组件上。`User` 是 user 工具的 Listener 单例，负责在任务批次结束时清理失效窗口并触发重绘，同时集中管理全部 User 级偏好（Pref）：网格、图标生成、颜色、单位、窗口位置、显示算法等，几乎所有 GUI 偏好都通过 `User.getXxx()/setXxx()` 存取。

`GraphicsPreferences` 是图形偏好的不可变快照（字体、端口显示级别、每层颜色/图案/透明度、文本可见性），通过 `withXxx()` 返回新实例，经 `UserInterfaceMain.get/setGraphicsPreferences` 全局生效。`IconParameters` 依据 `User` 中的图标生成偏好实际生成 Cell 图标（黑盒 + 引脚 + 引线）。`ErrorLogger`/`ActivityLogger`/`MessagesStream` 构成消息基础设施：错误与警告带高亮几何存入 ErrorLogger 并在错误树中显示；所有 `System.out` 输出被 `MessagesStream` 重定向到消息窗口；`ActivityLogger` 把菜单激活、任务启动、异常写入 Electric.log。

`CellChangeJobs`、`CircuitChangeJobs`、`CircuitChanges`、`ViewChanges`、`ExportChanges` 是编辑命令层：前两者是 `Job` 子类集合（在后台线程执行实际数据库修改），后三者是用户命令入口（参数收集、对话框、启动对应 Job）。`Exec`/`ExecMulti` 封装外部进程调用，`CantEditException` 统一处理"对象被锁定"时的用户确认。`Resources` 提供图标资源加载。

---

## UserInterfaceMain

**文件**: `com/sun/electric/tool/user/UserInterfaceMain.java`（1314 行）
**职责**: GUI 版用户界面的主类。构造函数在 EDT 上替换系统 EventQueue（捕获所有 Throwable）、设定客户端线程，并按 `Mode`（MDI/SDI）初始化顶层窗口；实现 `AbstractUserInterface` 的全部抽象方法，把数据库快照切换、任务结束、错误高亮显示、确认对话框、进度对话框桥接到 Swing UI；同时维护 undo/redo 状态、高亮存档、全局 EditingPreferences/GraphicsPreferences 和 DatabaseChangeListener 列表。

**关键字段**:
- `Mode` 枚举（MDI/SDI）(L122) — 窗口模式（多文档/单文档界面）
- `undoEnabled` / `redoEnabled` (boolean, L126-127) — undo/redo 按钮可用状态（供工具栏查询）
- `listenerList` (EventListenerList, L128) — DatabaseChangeListener 注册表
- `currentSnapshot` (Snapshot, L129) — 客户端当前数据库快照（初始为 initialSnapshot）
- `currentGraphicsPreferences` (GraphicsPreferences, L130) — 当前图形偏好（仅客户端线程访问）
- `progress` (Progress, L132) — 进度对话框实例（quiet 模式下为 null）
- `sw` (SplashWindow) / `swStarted` (long) (L133-134) — 启动画面及其显示时刻
- `oldUndoSnapshot` (Snapshot, L203) — undo/redo 前的旧快照（用于 Undo.endChanges）
- `printWriter` (PrintWriter, L773) — 保存消息输出的文件流
- `newCommand` / `commandNumber` (boolean/int, L774-775) — 消息窗口按命令分段
- `lastSavedEp` (EditingPreferences, L1106) — 最近一次生效的编辑偏好缓存
- `savedHighlights` / `lastId` (L1143-1144) — 高亮状态存档（undo/redo 时恢复用）

### 方法

#### `public UserInterfaceMain(List<String> argsList, Mode mode, boolean showSplash)`
- 行号: L136
- 描述: **[重要]** 构造函数。先在 EDT 上 `invokeAndWait` 初始化原 EventDispatchThread，然后 push 一个新的 EventQueue（其 `dispatchEvent` 捕获并记录一切 Throwable），再把客户端线程绑定到新 EventQueue 并设置线程环境；若 `mode` 为 null 则按 `StartupPrefs.getDisplayStyle()` 决定 MDI/SDI；最后 `SwingUtilities.invokeLater` 调度 `InitializationRun` 完成其余初始化。

#### `protected void showSnapshot(Snapshot newSnapshot)`
- 行号: L206
- 描述: 覆写父类方法。首次记录 `oldUndoSnapshot = currentSnapshot` 后转发到 `showSnapshot(newSnapshot, true)`（按 undo/redo 处理）。

#### `protected void terminateJob(Job.Key jobKey, String jobName, Tool tool, Job.Type jobType, byte[] serializedJob, boolean doItOk, byte[] serializedResult, Snapshot newSnapshot)`
- 行号: L214
- 描述: **[重要]** 任务终止回调。非 EXAMINE 类任务先 `Undo.endChanges` 结束变更、`showSnapshot` 切换快照并恢复高亮；随后取出或反序列化 EJob，调用 `job.terminateIt(jobException)`；若任务耗时超过阈值且开启 `User.isBeepAfterLongJobs()` 则蜂鸣，并打印任务信息。

#### `protected void showJobQueue(Job.Inform[] jobQueue)`
- 行号: L286
- 描述: 任务队列状态回调（保证在 EDT 执行）。更新 `JobTree` 显示，并在有排队/运行中的变更任务时把顶层窗口光标设为忙等待。

#### `public void addEvent(Client.ServerEvent serverEvent)`
- 行号: L306
- 描述: 把服务器事件投递到 EDT（`SwingUtilities.invokeLater`）。

#### `private class InitializationRun implements Runnable`（`public void run()`）
- 行号: L310（run 在 L320）
- 描述: **[重要]** 初始化主流程：`Pref.setCachedObjsFromPreferences()` 缓存偏好；创建 `TechPool`、`EditingPreferences`、`GraphicsPreferences`；设置系统 Look&Feel；Mac 上注册 About/Preferences/Quit 处理器并启用系统菜单栏；按需显示 SplashWindow；最后 `TopLevel.OSInitialize(mode)` 与 `TopLevel.InitializeMessagesWindow()` 建立主窗口和消息窗口。

#### `public void finishInitialization()`
- 行号: L375
- 描述: **[重要]** 初始化收尾（由框架在初始化完成后调用）。Linux 下把工作目录切到 user.dir；启动画面至少保持 `MINIMUMSPLASHDURATION`（3 秒）后关闭；`TopLevel.InitializeWindows()` 恢复上次窗口布局并打开当前库；debug 模式下打印缺失的可选/私有组件名。

#### `public EDatabase getDatabase()` / `public EditWindow_ getCurrentEditWindow_()` / `public EditWindow_ needCurrentEditWindow_()` / `public Cell getCurrentCell()` / `public Cell needCurrentCell()`
- 行号: L427 / L431 / L435 / L439 / L443
- 描述: 简单委托 getter。分别返回客户端数据库、当前 EditWindow_（无则 null / 无则报错）、当前 Cell（无则 null / 无则报错）。

#### `public void adjustReferencePoint(Cell theCell, double cX, double cY)`
- 行号: L450
- 描述: 遍历所有窗口，把显示 `theCell` 的编辑窗口偏移量减去 (cX, cY)，用于调整参考点。

#### `public void repaintAllWindows()`
- 行号: L469
- 描述: 重绘所有窗口（委托 `WindowFrame.repaintAllWindows()`）。

#### `public int getDefaultTextSize()`
- 行号: L473
- 描述: 返回编辑窗口默认字体大小（`EditWindow.getDefaultFontSize()`）。

#### `public EditWindow_ displayCell(Cell cell)`
- 行号: L477
- 描述: 在窗口中显示指定 Cell：若 `User.isShowCellsInNewWindow()` 或没有当前窗口则新建编辑窗口，否则复用当前窗口；返回其 EditWindow_（不是编辑窗口时返回 null）。

#### `public void termLogging(ErrorLogger log, boolean explain, boolean terminate)`
- 行号: L493
- 描述: 结束错误记录。空且非持久 logger 直接返回，否则交给 `ErrorLoggerTree.addLogger` 在错误树中展示（并可解释/标记终止）。

#### `public void updateNetworkErrors(Cell cell, List<ErrorLogger.MessageLog> errors)`
- 行号: L501
- 描述: 增量更新网络一致性检查错误（委托 `ErrorLoggerTree.updateNetworkErrors`）。

#### `public void updateIncrementalDRCErrors(Cell cell, List<ErrorLogger.MessageLog> newErrors, List<ErrorLogger.MessageLog> delErrors)`
- 行号: L505
- 描述: 增量更新 DRC 错误（委托 `ErrorLoggerTree.updateDrcErrors`）。

#### `public String reportLog(ErrorLogger.MessageLog log, boolean showhigh, boolean separateWindow, int position)`
- 行号: L514
- 描述: **[重要]** 展示一条错误/警告。`showhigh` 为真时先清空所有窗口高亮，再逐条把该 log 的 ErrorHighlight 画到对应 Cell 的窗口（`separateWindow` 控制是否新开窗口）；若开启 `User.isShiftWindowToErrors()` 且高亮区不在视口内则 `focusScreen` 平移窗口，否则画箭头指示；返回错误消息文本。

#### `public void showErrorMessage(String message, String title)`
- 行号: L645
- 描述: 在 EDT 上弹 `JOptionPane.ERROR_MESSAGE` 模态错误对话框；当前无顶层 JFrame 时改打 stderr。

#### `private String breakLongMessage(String message)`
- 行号: L670
- 描述: 把超过 100 字符的消息按空格断行为多行（最多 20 行，超出追加 " and more ..."），供对话框显示。

#### `public void showInformationMessage(String message, String title)` / `public void showInformationMessage(JFrame frame, String message, String title)`
- 行号: L700 / L711
- 描述: 弹信息对话框。前者用当前 JFrame，后者用指定 frame；非 EDT 线程自动 `invokeLater` 转投。

#### `public void printMessage(String message, boolean newLine)`
- 行号: L729
- 描述: 向消息窗口追加文本（非 EDT 时 `invokeLater` 转投 `MessagesWindow.appendString`）。

#### `public void saveMessages(String filePath)`
- 行号: L747
- 描述: 开始/切换消息保存目标文件：`filePath` 为 null 时停止保存并关闭 PrintWriter，否则创建文件流并提示保存位置。

#### `public static void userCommandIssued()`
- 行号: L782
- 描述: 标记用户发起了一条新命令（点击/按键/菜单），使消息窗口用分隔行区分各命令的输出。

#### `public void appendString(String str)`
- 行号: L786
- 描述: 向消息窗口追加字符串；若是新命令开头则插入 `=====N=====` 分隔行；同时写入保存文件的 PrintWriter（若开启）。

#### `public boolean confirmMessage(Object message)`
- 行号: L808
- 描述: 弹确认对话框，返回用户是否选择 Yes。

#### `public int askForChoice(String message, String title, String[] choices, String defaultChoice)`
- 行号: L821
- 描述: 弹多选按钮对话框。先把超长消息按 256 字符/行、最多 10 行截断重组，返回所选按钮下标。

#### `public String askForInput(Object message, String title, String def)`
- 行号: L860
- 描述: 弹单行文本输入对话框，返回输入字符串（取消返回 null）。

#### `public static void importPrefs(URL fileURL)`
- 行号: L869
- 描述: 导入偏好 XML：`Pref.importPrefs` 读入、环境写回偏好、重新缓存全部偏好（`loadPreferences`）、恢复菜单键绑定、`User.technologyChanged()` 并重绘窗口。

#### `public int saveHighlights()`
- 行号: L894
- 描述: **[重要]** 保存当前窗口的高亮列表与偏移（`SavedHighlights`），返回自增 ID；存档数超过 `User.getMaxUndoHistory()` 时淘汰最旧记录。

#### `public void restoreHighlights(int highlightsId)`
- 行号: L912
- 描述: 按 ID 查找并恢复之前保存的高亮状态（undo/redo 后调用）。

#### `public static void freeHighlights(EditWindow_ wnd)`
- 行号: L925
- 描述: 删除指定窗口的全部高亮存档（窗口关闭时调用）。

#### `public void showUndoRedoStatus(boolean newUndoEnabled, boolean newRedoEnabled)`
- 行号: L939
- 描述: **[重要]** 更新 undo/redo 可用标志，并通过 `PropertyChangeRun` 在 EDT 上刷新工具栏按钮。

#### `public void showSnapshot(Snapshot newSnapshot, boolean undoRedo)`
- 行号: L957
- 描述: **[重要]** 数据库快照切换核心。锁数据库并 `database.undo(newSnapshot)`（异常时 recover），随后更新线程环境；若 ToolSettings 变化则 `ToolSettings.attachToGroup`，若 TechPool 变化则保留层可见性、重载偏好并 `User.technologyChanged()`；通知所有 Tool 监听器 `endBatch`，最后 `fireDatabaseChangeEvent`。

#### `public void beep()`
- 行号: L997
- 描述: 蜂鸣（EDT 上调用 `User.playSound()`，否则转投）。

#### `public static boolean getUndoEnabled()` / `public static boolean getRedoEnabled()`
- 行号: L1015 / L1024
- 描述: getter。返回 undo/redo 是否可用（工具栏据此决定按钮是否可用）。

#### `private static void firePropertyChange(PropertyChangeEvent e)`
- 行号: L1028
- 描述: 更新 undo/redo 工具栏按钮；遍历所有 WindowFrame，把显示已不链接 Cell（undo 中被删除）的窗口清空并重绘。

#### `private static class PropertyChangeRun implements Runnable`（`public void run()`）
- 行号: L1049（run 在 L1057）
- 描述: 把属性变化事件投递到 EDT 后调用 `firePropertyChange`。

#### `public static synchronized void addDatabaseChangeListener(DatabaseChangeListener l)` / `public static synchronized void removeDatabaseChangeListener(DatabaseChangeListener l)`
- 行号: L1066 / L1071
- 描述: 注册/注销 DatabaseChangeListener（数据库状态变化时收到通知）。

#### `public static void fireDatabaseChangeEvent(DatabaseChangeEvent e)`
- 行号: L1079
- 描述: 逆序通知所有已注册的 DatabaseChangeListener（`databaseChanged(e)`）。

#### `private static void loadPreferences(TechPool techPool)`
- 行号: L1092
- 描述: 重新缓存偏好：重建 EditingPreferences、GraphicsPreferences、层可见性（`LayerVisibility.loadPreferences`）并刷新 `ClickZoomWireListener` 偏好。

#### `public static EditingPreferences getEditingPreferences()` / `public static void setEditingPreferences(EditingPreferences newEp)`
- 行号: L1101 / L1108
- 描述: 获取当前编辑偏好缓存；设置时若与旧值相等则忽略，否则把差异写入 Pref 树并更新缓存与 ClickZoomWireListener。

#### `public static GraphicsPreferences getGraphicsPreferences()` / `public static void setGraphicsPreferences(GraphicsPreferences gp)`
- 行号: L1121 / L1133
- 描述: 获取当前图形偏好（debug 下若在非客户端线程访问会警告一次）；设置时同样做 diff 写回 Pref 树后替换缓存。

#### `private static class SavedHighlights`
- 行号: L1146
- 描述: 高亮存档。构造时保存窗口的高亮列表与偏移（L1157），`restore()`（L1164）恢复列表、偏移并 `finishedHighlighting()`。

#### `private static class SplashWindow extends JFrame`
- 行号: L1174
- 描述: 无边框启动画面：绿色边框 + SplashImage.gif + 版本号（默认字体 24 号加粗），居中、置顶显示，并挂 `WindowsEvents` 监听。

#### `private static class WindowsEvents implements WindowListener`
- 行号: L1215
- 描述: 启动画面窗口监听器；在 `windowDeactivated`（L1242）时若失去焦点给主窗口则把启动画面重新置顶。

#### `public void startProgressDialog(String msg, String filePath)`
- 行号: L1256
- 描述: 创建并显示进度对话框（先关掉旧的）；filePath 非空时文案为 "Reading msg filePath..."。

#### `public void stopProgressDialog()`
- 行号: L1275
- 描述: 关闭并释放进度对话框。

#### `public void setProgressValue(int pct)` / `public void setProgressNote(String message)` / `public String getProgressNote()`
- 行号: L1286 / L1297 / L1308
- 描述: 更新进度百分比；设置进度文本；获取当前进度文本（无进度对话框时返回空串）。

---

## User

**文件**: `com/sun/electric/tool/user/User.java`（2983 行）
**职责**: User Interface 工具的单例（继承 `Listener`）。`endBatch` 在每次任务批次结束后清理显示已删除 Cell 的窗口并触发渲染任务；维护"当前弧/当前接触"状态与等价端口映射，支持技术面板切换；作为 User 模块全部偏好（Pref）的集中出入口，为图标生成、选择、网格、文本、边框、颜色、单位、窗口布局、显示算法等提供 get/set/工厂默认值三元组访问。

**关键字段**:
- `tool` (User, L92) — User 工具单例
- `FRAME_SIZE`、`FRAME_COMPANY_NAME`、`FRAME_DESIGNER_NAME`、`FRAME_LAST_CHANGED_BY`、`FRAME_PROJECT_NAME` (Variable.Key, L93-97) — 原理图边框变量键
- `currentArcProto` / `currentTech` (L99-100) — 组件菜单当前弧（红框高亮）与当前技术
- `preferredContactPortProtoMap` (Map<String,PrimitivePort>, L101) — "弧名@弧名" → 首选接触端口映射
- `equivalentPortProtoMap` (Map<String,List<PrimitivePort>>, L102) — 弧名组合键 → 等价端口列表（布线工具用）
- `contactNodeProtoMap` (Map<String,Pref>, L310) — 接触节点当前选择偏好缓存
- `changedWindowRects` (Map<EditWindow,ERectangle>, L223) — 各窗口需要重绘的脏区域
- 大量 `static Pref cache...` 字段 — 各类偏好的底层 Pref 对象（见下文各偏好分组）

### 方法

#### `private User()`
- 行号: L115
- 描述: 私有构造，调用 `super("user")` 注册工具名。

#### `public void init()`
- 行号: L123
- 描述: 工具初始化：`setOn()`、`setIncremental()`（user 工具始终开启）。

#### `public static User getUserTool()`
- 行号: L137
- 描述: 返回 User 工具单例。

#### `public void endBatch(Snapshot oldSnapshot, Snapshot newSnapshot, boolean undoRedo)`
- 行号: L146
- 描述: **[重要]** 任务批次结束回调。把显示已不链接 Cell 的编辑窗口置空；若环境相对初始环境有变化则 `EditWindow.invokeRenderJob()` 触发重绘。

#### `public static void fixStaleCellReferences(IdMapper idMapper)`
- 行号: L200
- 描述: **[重要]** 库重载/改名后修复陈旧引用：若当前库被映射到新 LibId 则切换当前库；把所有指向未链接 Cell 的窗口内容换成 idMapper 映射后的新 Cell。

#### `public static ERectangle getChangedInWindow(EditWindow wnd)` / `public static void clearChangedInWindow(EditWindow wnd)`
- 行号: L230 / L241
- 描述: 查询窗口的待重绘区域（数据库坐标）；重绘完成后清除该记录。

#### `public static void technologyChanged()`
- 行号: L250
- 描述: 技术参数变化后的全局刷新：清 VectorCache 与子 Cell 缓存、更新窗口技术列表和层可见性。

#### `public PrimitivePort getPreferredContactPortProto(ArcProto key1, ArcProto key2)`
- 行号: L264
- 描述: 返回一对弧对应的"当前"接触端口；技术变化时先用 `uploadCurrentData` 重建映射，按 "key1@key2" 与反序查找。

#### `public List<PrimitivePort> getPrimitivePortConnectedToArc(ArcProto ap)`
- 行号: L280
- 描述: 扫描 contact 映射，返回所有键中含有指定弧名（按 ", @" 分词精确匹配）的 PrimitivePort 列表。

#### `private Pref getContactNodePref(Technology tech, String mainContactName)`
- 行号: L312
- 描述: 取得（必要时创建）"组件菜单中接触节点的当前选择"偏好对象，键为 `ComponentMenuEntryFor<name>In<tech>`。

#### `public String getCurrentContactNodeProto(Technology tech, String mainContactName)` / `public void setCurrentContactNodeProto(Technology tech, String mainContactName, Object obj)`
- 行号: L330 / L347
- 描述: 读取组件菜单中多选接触条目的当前选择名（并刷新首选接触映射）；设置当前选择（接受 NodeProto/NodeInst/Xml.PrimitiveNode/Xml.MenuNodeInst），存偏好并更新连接映射。

#### `private void updatePrimitiveNodeConnections(PrimitiveNode pn)`
- 行号: L372
- 描述: 对单端口接触节点，把其连接弧名的两两组合（按排序后的弧名表）写入 `preferredContactPortProtoMap`。

#### `public void uploadCurrentData(Technology tech, Xml.MenuPalette menuPalette)`
- 行号: L397
- 描述: **[重要]** 组件面板切换技术时的数据重建：清等价端口映射、设置当前弧为技术第一根弧、重建层间接触映射，并按 XML 面板第一项恢复各接触的当前选择。

#### `public void setEquivalentPortProto(Object obj)`
- 行号: L427
- 描述: 把接触节点（NodeProto/NodeInst）注册进等价端口映射（按排序弧名组合键），供布线工具查询。

#### `private static List<String> getArcNamesSorted(PrimitivePort p)` / `private static String getKeyFromList(List<String> list)`
- 行号: L469 / L486
- 描述: 返回端口连接弧的排序名列表（剔除 Generic 弧）；把弧名列表拼成 "@a@b" 形式的映射键。

#### `public List<PrimitivePort> getEquivalentPorts(PrimitivePort p)`
- 行号: L501
- 描述: 返回与给定端口弧组合相同的等价端口列表。

#### `public ArcProto getCurrentArcProto()` / `public void setCurrentArcProtoTemporarily(ArcProto ap)` / `public void setCurrentArcProto(ArcProto ap)`
- 行号: L512 / L521 / L531
- 描述: 获取当前弧（组件菜单红框高亮）；临时改当前弧（不改面板显示，供布线规划器）；正式改当前弧并通知面板标签 `arcProtoChanged()`。

#### `public static void playSound()`
- 行号: L541
- 描述: 播放一次 Click.wav 点击音（资源缺失或失败则静默禁用）。

#### `public static void setCurrentLibrary(Library lib)`
- 行号: L573
- 描述: 切换当前库（断言 EDT，委托 ExtendedUserInterface）。

#### `public static String getDefaultTechnology()` / `public static Setting getDefaultTechnologySetting()`
- 行号: L586 / L591
- 描述: 获取技术面板默认技术名（默认 "mocmos"）及其 Setting。

#### `public static Technology getSchematicTechnology()` / `public static Setting getSchematicTechnologySetting()` / `public static Technology getFactorySchematicTechnology()`
- 行号: L601 / L615 / L621
- 描述: 获取"发现原理图时使用的版图技术"（查不到时回退 mocmos）及其 Setting / 工厂默认技术。

#### `public static boolean isIncludeDateAndVersionInOutput()` / `public static Setting getIncludeDateAndVersionInOutputSetting()`
- 行号: L634 / L639
- 描述: 是否在输出文件中包含日期与 Electric 版本（默认 true）及其 Setting。

#### `public static Setting getPSubstrateProcessLayoutTechnologySetting()` / `public static boolean isPSubstrateProcessLayoutTechnology()`
- 行号: L646 / L647
- 描述: 是否 P 衬底工艺（true 时忽略 pwell 间距规则）及其 Setting。

#### `public static Setting getSoftTechnologiesSetting()`
- 行号: L653
- 描述: 附加技术（soft technologies）的项目偏好 Setting。

#### 图标生成偏好（ICON GENERATION PREFERENCES，get/set/工厂默认值三元组，均基于 `Pref cacheIconGen...`）
- 行号: L663-947
- 描述: 生成图标时各类端口/文本的摆放参数。端口所在边（0 左/1 右/2 上/3 下）：InputSide(L663/669/675，默认左)、OutputSide(L683/689/695，默认右)、BidirSide(L703/709/715，默认上)、PowerSide(L723/729/735，默认下)、GroundSide(L743/749/755，默认下)、ClockSide(L763/769/775，默认左)；按位置摆放时的文本旋转：TopRot(L784/791/798)、BottomRot(L807/814/821)、LeftRot(L830/837/844)、RightRot(L853/860/867)，均默认 0；按位置摆放时跳过某边：TopSkip(L875/881/887)、BottomSkip(L895/901/907)、LeftSkip(L915/921/927)、RightSkip(L935/941/947)，均默认 false。

#### `public static int getPortDisplayLevel()` / `public static int getExportDisplayLevel()`
- 行号: L958 / L966
- 描述: 端口/输出（export）显示级别（0 全名、1 短名、2 十字），直接读 `GraphicsPreferences` 字段。

#### `public static boolean isMoveNodeWithExport()` / `setMoveNodeWithExport(boolean)` / `isFactoryMoveNodeWithExport()`
- 行号: L974 / L979 / L984
- 描述: 移动 export 名字时是否连带移动节点（默认 false）的当前值/设置/工厂默认。

#### 选择偏好（SELECTION PREFERENCES）
- 行号: L994-1092
- 描述: `isEasySelectionOfCellInstances`(L994)/set(L999)/factory(L1004)：Cell 实例是否易选中（默认 true）；`isDraggingMustEncloseObjects`(L1012)/set(L1017)/factory(L1022)：框选是否必须完全包围对象（默认 false）；`isMouseOverHighlightingEnabled`(L1030)/set(L1035)/factory(L1040)：鼠标悬停高亮（默认 true）；`isHighlightConnectedObjects`(L1047)/set(L1052)/factory(L1057)：高亮与选中对象相连的对象（默认 true）；`isHighlightInvisibleObjects`(L1064)/set(L1069)/factory(L1074)：高亮全不可见层上的对象（默认 false）；`isRoutingMode`(L1082)/set(L1087)/factory(L1092)：click/zoom/wire 是否处于布线模式（禁改实例与拓扑，默认 false）。

#### 网格与对齐偏好（GRID AND ALIGNMENT PREFERENCES）
- 行号: L1102-1241
- 描述: `getDefGridXSpacing`(L1102)/set(L1107)/factory(L1112) 与 `getDefGridYSpacing`(L1120)/set(L1125)/factory(L1130)：X/Y 网格点默认间距（默认 1）；`getDefGridXBoldFrequency`(L1138)/set(L1143)/factory(L1148) 与 Y 方向(L1156/1161/1166)：加粗网格点频率（默认 10）；`isGridAlignMeasurementCursor`(L1174)/set(L1179)/factory(L1184)：测量模式光标是否吸附网格（默认 false）；`getAlignmentToGrid`(L1191)/`getAlignmentToGridIndex`(L1199)/`getAlignmentToGridVector`(L1207)：当前网格对齐（EDimension）、其下标与五个候选值数组（读 EditingPreferences）；`setAlignmentToGridVector`(L1216)：设置候选数组与当前下标（写回 EditingPreferences）；`isGridAxesShown`(L1229)/set(L1235)/factory(L1241)：是否显示过原点的网格轴实线（默认 false）。

#### 文本偏好（TEXT PREFERENCES）
- 行号: L1251-1393
- 描述: `isTextVisibilityOn(TextType)`(L1251)：指定类型文本是否可见（读 GraphicsPreferences）；便捷封装：Node(L1261)、Arc(L1268)、Port(L1275)、Export(L1282)、Annotation(L1290)、Cell(L1303)；`getDefaultFont`(L1310)/`getFactoryDefaultFont`(L1315)：显示默认字体（默认 "SansSerif"）；`getDefaultTextCellFont`(L1323)/set(L1328)/factory(L1333)：文本 Cell 编辑字体；`getDefaultTextCellSize`(L1341)/set(L1346)/factory(L1351)：文本 Cell 字号（默认 12）；`getGlobalTextScale`(L1361)/set(L1368)/factory(L1375)：全局文本缩放因子（默认 1）；`getDefaultTextExternalEditor`(L1383)/set(L1388)/factory(L1393)：外部编辑文本 Cell 的程序（默认空）。

#### 边框偏好（FRAME PREFERENCES）
- 行号: L1404-1458
- 描述: 原理图边框公司名 `getFrameCompanyName`(L1404)/set(L1410)/factory(L1416)、设计者名 `getFrameDesignerName`(L1425)/set(L1431)/factory(L1437)、项目名 `getFrameProjectName`(L1446)/set(L1452)/factory(L1458)，均默认空串。

#### `public enum ColorPrefType`
- 行号: L1462
- 描述: 特殊显示颜色的枚举（背景、网格、测量尺、各类高亮、文本、实例轮廓、波形窗各色、3D 各色），含偏好键 `getPrefKey()`(L1537) 与工厂默认色 `getFactoryDefaultColor()`(L1538)。

#### `public static int getColor(ColorPrefType pref)` / `public static void setColor(ColorPrefType pref, int color)` / `public static void resetFactoryColor(ColorPrefType pref)`
- 行号: L1551 / L1558 / L1567
- 描述: 读取特殊颜色（RGB 取低 24 位）；通过 `GraphicsPreferences.withColor` 设置新颜色；恢复为工厂默认色。

#### 单位偏好（UNITS PREFERENCES）
- 行号: L1581-1766
- 描述: 距离单位 `getDistanceUnits`(L1581)/set(L1591)/factory(L1602)（默认 null=可缩放单位）、电阻单位(L1615/1623/1631，默认欧姆)、电容单位(L1642/1650/1658，默认皮法)、电感单位(L1669/1677/1685，默认纳亨)、电流单位(L1696/1704/1712，默认毫安)、电压单位(L1723/1731/1739，默认伏特)、时间单位(L1750/1758/1766，默认秒)。均为 `TextUtils.UnitScale` 与下标互转。

#### `public static String getWorkingDirectory()` / `public static void setWorkingDirectory(String dir)`
- 行号: L1806 / L1812
- 描述: 当前工作目录（读 EditingPreferences）；设置时经 `withWorkingDirectory` 生成新 EditingPreferences 写回。

#### `public static String getRegressionPath()` / `public static void setRegressionPath(String s)`
- 行号: L1825 / L1834
- 描述: 回归测试目录路径的读写（默认 "<set me up>"）。

#### `public static String[] getRecentlyOpenedLibraries()` / `public static void addRecentlyOpenedLibrary(String s)` / `public static void clearRecentlyOpenedLibraries()`
- 行号: L1847 / L1859 / L1875
- 描述: 读取最近打开库列表（换行分隔，无扩展名）；把新路径去重后插到队首（最多 16 条）；清空列表。

#### `public static boolean isPromptForIndexWhenDescending()` / `setPromptForIndexWhenDescending(boolean)` / `isFactoryPromptForIndexWhenDescending()`
- 行号: L1888 / L1896 / L1904
- 描述: 下钻阵列节点时是否询问索引（默认 false）。

#### `public static boolean isBeepAfterLongJobs()` / `setBeepAfterLongJobs(boolean)` / `isFactoryBeepAfterLongJobs()`
- 行号: L1913 / L1919 / L1925
- 描述: 长任务（>1 分钟）结束是否蜂鸣（默认 false）。

#### `public static boolean isJobVerboseMode()` / `setJobVerboseMode(boolean)` / `isFactoryJobVerboseMode()`
- 行号: L1933 / L1938 / L1943
- 描述: 是否在消息窗口详细描述任务（默认 false）。

#### `public static boolean isRotateLayoutTransistors()` / `setRotateLayoutTransistors(boolean)` / `isFactoryRotateLayoutTransistors()`
- 行号: L1951 / L1956 / L1961
- 描述: 版图晶体管在菜单/初始摆放时是否旋转 90 度（默认 false）。

#### `public static boolean isSideBarOnRight()` / `setSideBarOnRight(boolean)` / `isFactorySideBarOnRight()`
- 行号: L1970 / L1976 / L1982
- 描述: 侧栏（explorer/组件/层）是否默认在右侧（默认 false，左侧）。

#### `public static int getDefaultWindowTab()` / `public static void setDefaultWindowTab(int t)`
- 行号: L1991 / L1997
- 描述: 侧栏默认标签页（0=组件 1=explorer 2=层，默认 0）。

#### `public static Point getDefaultWindowPos()` / `public static void setDefaultWindowPos(Point pt)`
- 行号: L2006 / L2014
- 描述: 顶层窗口默认位置（默认 (0,0)）。

#### `public static Dimension getDefaultWindowSize()` / `public static void setDefaultWindowSize(Dimension sz)`
- 行号: L2027 / L2036
- 描述: 顶层窗口默认尺寸（0 时返回 null，用屏幕默认）。

#### `public static Point getDefaultMessagesPos()` / `public static void setDefaultMessagesPos(Point pt)` / `public static Dimension getDefaultMessagesSize()` / `public static void setDefaultMessagesSize(Dimension sz)`
- 行号: L2049 / L2058 / L2071 / L2080
- 描述: 消息窗口默认位置（-1 表示 null）与默认尺寸（0 表示 null）。

#### `public static boolean isPlayClickSoundsWhenCreatingArcs()` / `setPlayClickSoundsWhenCreatingArcs(boolean)` / `isFactoryPlayClickSoundsWhenCreatingArcs()`
- 行号: L2092 / L2097 / L2102
- 描述: 画弧时是否播放点击音（默认 true，ServerPref）。

#### `public static boolean isShowHierarchicalCursorCoordinates()` / `setShowHierarchicalCursorCoordinates(boolean)` / `isFactoryShowHierarchicalCursorCoordinates()`
- 行号: L2113 / L2121 / L2129
- 描述: 是否显示层次化光标坐标（默认 true）。

#### `public static boolean isDimUpperLevelWhenDownInPlace()` / `setDimUpperLevelWhenDownInPlace(boolean)` / `isFactoryDimUpperLevelWhenDownInPlace()`
- 行号: L2139 / L2146 / L2153
- 描述: 原地下钻编辑时是否淡化上层（默认 true；某些系统上可关以提速）。

#### `public static boolean isShowCellsInNewWindow()` / `setShowCellsInNewWindow(boolean)` / `isFactoryShowCellsInNewWindow()`
- 行号: L2162 / L2168 / L2174
- 描述: 查看 Cell 时是否新开窗口（默认 true）；`UserInterfaceMain.displayCell` 依赖此偏好。

#### `public static boolean isErrorHighlightingPulsate()` / `setErrorHighlightingPulsate(boolean)` / `isFactoryErrorHighlightingPulsate()`
- 行号: L2182 / L2187 / L2192
- 描述: 错误高亮是否闪烁描边（默认 true）。

#### `public static boolean isShiftWindowToErrors()` / `setShiftWindowToErrors(boolean)` / `isFactoryShiftWindowToErrors()`
- 行号: L2201 / L2207 / L2213
- 描述: 是否平移窗口使下一个错误可见（默认 false，画箭头指示）；`reportLog` 依赖此偏好。

#### `public static boolean isKeepModelessDialogsOnTop()` / `setKeepModelessDialogsOnTop(boolean)` / `isFactoryKeepModelessDialogsOnTop()`
- 行号: L2222 / L2228 / L2234
- 描述: 无模态对话框（如属性框）是否保持在编辑窗口之上（默认 false）。

#### `public static boolean isCadenceMeasurementStyle()` / `setCadenceMeasurementStyle(boolean)` / `isFactoryCadenceMeasurementStyle()`
- 行号: L2242 / L2247 / L2252
- 描述: 测量工具是否用 Cadence 风格（默认 false）。

#### `public static boolean isDockMessagesWindow()` / `setDockMessagesWindow(boolean)` / `isFactoryDockMessagesWindow()`
- 行号: L2259 / L2263 / L2267
- 描述: 消息窗口是否停靠到每个编辑窗口（默认 false）。

#### `public static int getDisplayAlgorithm()` / `setDisplayAlgorithm(int)` / `getFactoryDisplayAlgorithm()`
- 行号: L2277 / L2284 / L2291
- 描述: 显示算法选择（0 像素、1 矢量、2 图层，默认 1）。

#### `public static boolean isUseCellGreekingImages()` / `setUseCellGreekingImages(boolean)` / `isFactoryUseCellGreekingImages()`
- 行号: L2300 / L2306 / L2312
- 描述: 细胞"希腊化"（近似绘制）时是否用小图（默认 false，用单一混色）。

#### `public static double getGreekSizeLimit()` / `setGreekSizeLimit(double)` / `getFactoryGreekSizeLimit()`
- 行号: L2324 / L2332 / L2340
- 描述: 可精确绘制的最小对象尺寸（屏幕像素，默认 3，更小则希腊化）。

#### `public static double getGreekCellSizeLimit()` / `setGreekCellSizeLimit(double)` / `getFactoryGreekCellSizeLimit()`
- 行号: L2349 / L2355 / L2361
- 描述: 不希腊化的 Cell/屏幕尺寸比阈值（默认 0.1）。

#### `public static double getPatternedScaleLimit()` / `setPatternedScaleLimit(double)` / `getFactoryPatternedScaleLimit()`
- 行号: L2370 / L2376 / L2382
- 描述: 使用图案绘制（patterned drawing）的缩放阈值（默认 0.5）。

#### `public static boolean isLegacyComposite()` / `setLegacyComposite(boolean)` / `isFactoryLegacyComposite()`
- 行号: L2390 / L2395 / L2400
- 描述: 是否使用旧式图层合成（默认 false，使用 alpha 混合）。

#### `public static double getAlphaBlendingOvercolorLimit()` / `setAlphaBlendingOvercolorLimit(double)` / `getFactoryAlphaBlendingOvercolorLimit()`
- 行号: L2409 / L2415 / L2421
- 描述: alpha 混合使用 overcolor 的缩放阈值（默认 0.6）。

#### `public static boolean isShowFileSelectionForNetlists()` / `setShowFileSelectionForNetlists(boolean)` / `isFactoryShowFileSelectionForNetlists()`
- 行号: L2429 / L2434 / L2439
- 描述: 写网表前是否弹文件选择框（默认 true）。

#### `public static int getPanningDistance()` / `setPanningDistance(int)` / `getFactoryPanningDistance()`
- 行号: L2448 / L2454 / L2460
- 描述: 平移/滚轮滚动距离档位（0 小、1 中、2 大，默认 1）。

#### `public static int getDisplayStyle()` / `setDisplayStyle(int)` / `getFactoryDisplayStyle()`
- 行号: L2469 / L2476 / L2482
- 描述: 初始显示风格（0=OS 默认、1=MDI、2=SDI；重启后生效）。

#### `public static boolean isEnableLog()` / `setEnableLog(boolean)` / `getFactoryEnableLog()` 与 `public static boolean isMultipleLog()` / `setMultipleLog(boolean)` / `getFactoryMultipleLog()`
- 行号: L2490 / L2495 / L2501 / L2509 / L2514 / L2520
- 描述: 是否启用 Electric 日志（默认 true）；是否生成多日志文件 Electric-*.log（默认 false）。

#### `public static int getErrorLimit()` / `setErrorLimit(int)` / `getFactoryErrorLimit()`
- 行号: L2528 / L2534 / L2540
- 描述: 最多记录的错误数（0 = 无限制，默认 0）；ErrorLogger 创建时读取。

#### `public static int getMaxUndoHistory()` / `setMaxUndoHistory(int)` / `getFactoryMaxUndoHistory()`
- 行号: L2546 / L2550 / L2554
- 描述: 内存中保留的最大 undo 步数（`UserInterfaceMain.saveHighlights` 依赖）。

#### `public static int getMemorySize()` / `setMemorySize(int)` / `getFactoryMemorySize()` 与 `public static int getPermSpace()` / `setPermSpace(int)` / `getFactoryPermSpace()`
- 行号: L2562 / L2567 / L2572 / L2580 / L2585 / L2591
- 描述: Electric 最大内存（MB）；二代 GC 永久代上限（MB，0 表示不考虑）。

#### `public static double getScreenScaling()` / `setScreenScaling(double)` / `getFactoryScreenScaling()`
- 行号: L2599 / L2605 / L2611
- 描述: 整个屏幕的缩放因子（0 = 不缩放）。

#### `public static boolean isUseTwoJVMs()` / `setUseTwoJVMs(boolean)` / `isFactoryUseTwoJVMs()` 与 `public static boolean isUseClientServer()` / `setUseClientServer(boolean)` / `isFactoryUseClientServer()`
- 行号: L2620 / L2626 / L2632 / L2640 / L2646 / L2650
- 描述: 是否用两个 JVM（客户端+服务端分离内存）运行（默认 false）；是否使用独立客户端/服务端（默认 false）。

#### `public static boolean isSnapshotLogging()` / `setSnapshotLogging(boolean)` / `isFactorySnapshotLogging()`
- 行号: L2658 / L2663 / L2668
- 描述: 是否在临时文件中记录数据库快照日志（默认 false）。

#### `public static boolean isAutoTechnologySwitch()` / `setAutoTechnologySwitch(boolean)` / `isFactoryAutoTechnologySwitch()`
- 行号: L2677 / L2683 / L2689
- 描述: 切换当前 Cell 时是否自动切换组件菜单技术（默认 true）。

#### `public static boolean isReconstructArcsAndExportsToDeletedCells()` / `setReconstructArcsAndExportsToDeletedCells(boolean)` / `isFactoryReconstructArcsAndExportsToDeletedCells()`
- 行号: L2699 / L2706 / L2713
- 描述: 删除 Cell 实例后是否重建连到该处的弧与 export（以引脚收尾，默认 true）。

#### `public static boolean isConvertSchematicLayoutWhenPasting()` / `setConvertSchematicLayoutWhenPasting(boolean)` / `isFactoryConvertSchematicLayoutWhenPasting()`
- 行号: L2723 / L2730 / L2737
- 描述: 粘贴时是否在原理图/版图视图间自动转换（默认 true）。

#### `public static boolean isCheckCellDates()` / `setCheckCellDates(boolean)` / `isFactoryCheckCellDates()`
- 行号: L2746 / L2752 / L2758
- 描述: 放置实例时是否检查 Cell 日期（默认 false；未实现）。

#### `public static boolean isDisallowModificationLockedPrims()` / `setDisallowModificationLockedPrims(boolean)` / `isFactoryDisallowModificationLockedPrims()` 与 `public static boolean isDisallowModificationComplexNodes()` / `setDisallowModificationComplexNodes(boolean)` / `isFactoryDisallowModificationComplexNodes()`
- 行号: L2767 / L2773 / L2779 / L2788 / L2794 / L2800
- 描述: 是否禁止修改锁定原语（FPGA 等阵列技术）；是否禁止修改复杂节点（Cell 实例、晶体管等）。`CantEditException` 可临时改写这两项。

#### `public static boolean isMoveAfterDuplicate()` / `setMoveAfterDuplicate(boolean)` / `isFactoryMoveAfterDuplicate()`
- 行号: L2808 / L2813 / L2818
- 描述: 复制（Duplicate）后是否移动对象（默认 true）。

#### `public static boolean isDuplicateInPlace()` / `setDuplicateInPlace(boolean)` / `isFactoryDuplicateInPlace()`
- 行号: L2826 / L2831 / L2836
- 描述: 是否原地复制（默认 false）。

#### `public static boolean isDupCopiesExports()` / `setDupCopiesExports(boolean)` / `isFactoryDupCopiesExports()`
- 行号: L2844 / L2849 / L2854
- 描述: Duplicate/Paste/Array 节点时是否复制其 export（默认 false）。

#### `public static boolean isIncrementRightmostIndex()` / `setIncrementRightmostIndex(boolean)` / `isFactoryIncrementRightmostIndex()`
- 行号: L2863 / L2869 / L2875
- 描述: 多维阵列自动递增索引时是否从最右下标开始（默认 true）。

#### `public static boolean isExtractCopiesExports()` / `setExtractCopiesExports(boolean)` / `isFactoryExtractCopiesExports()`
- 行号: L2883 / L2889 / L2893
- 描述: Extract 节点时是否复制其 export（默认 true）。

#### `public static boolean isArcsAutoIncremented()` / `setArcsAutoIncremented(boolean)` / `isFactoryArcsAutoIncremented()`
- 行号: L2901 / L2906 / L2911
- 描述: Duplicate/Paste/Array 弧时是否自动递增弧名（默认 true）。

#### `public static int getNewNodeRotation()` / `public static void setNewNodeRotation(int rot)` 与 `public static boolean isNewNodeMirrorX()` / `public static void setNewNodeMirrorX(boolean on)`
- 行号: L2919 / L2924 / L2932 / L2937
- 描述: 新节点的默认旋转角（默认 0）；新节点是否 X 镜像（默认 false）。

#### `public static int getWaveformDigitalPanelHeight()` / `setWaveformDigitalPanelHeight(int)` 与 `public static int getWaveformAnalogPanelHeight()` / `setWaveformAnalogPanelHeight(int)`
- 行号: L2945 / L2950 / L2958 / L2963
- 描述: 数字/模拟波形面板高度（默认 30 / 75）。

#### `public static String getLayersSorting()` / `setLayersSorting(String)` / `getFactoryLayersSorting()`
- 行号: L2972 / L2977 / L2982
- 描述: 层排序算法名（`LayerSortingType`，默认 ByOrderInTechFile）。

---

## ActivityLogger

**文件**: `com/sun/electric/tool/user/ActivityLogger.java`（252 行）
**职责**: 静态活动日志器，把菜单激活、工具栏点击、任务启动、异常等事件写入 Electric.log（默认在工作目录，可多文件命名）；异常同时弹错误对话框提示用户日志位置。日志开关与行为由静态标志控制。

主要方法：
- `public static synchronized void initialize(String fileName, boolean logMenuActivations, boolean logJobs, boolean useTimeStamps, boolean enableLog, boolean multipleLog)` (L64) — 初始化并打开日志文件（失败则退回 stdout）。
- `public static String getLoggingInformation()` (L105) — 返回日志文件位置说明文本。
- `public static synchronized void finished()` (L115) — 关闭日志流。
- `public static synchronized void logMenuActivated(String menuDescription)` (L123) — 记录菜单激活及当时的高亮列表。
- `public static synchronized void logToolBarButtonActivated(String buttonName)` (L145) — 记录工具栏按钮激活。
- `public static synchronized void logJobStarted(String jobName, Job.Type jobType, Cell cell, List<Highlight> savedHighlights, Point2D savedHighlightsOffset)` (L161) — 记录任务启动、当前 Cell 与起始高亮。
- `public static synchronized void logHighlights(List<Highlight> highlights, Point2D offset)` (L178) — 记录高亮对象描述。
- `public static synchronized void logTime(long time)` (L191) — 记录时间戳。
- `public static synchronized void logException(Throwable e)` (L203) — 记录异常堆栈并弹错误对话框。
- `public static synchronized void logMessage(String msg)` (L225) — 记录一条消息。
- `public static synchronized void logThreadMessage(String msg)` (L232) — 临时调试用线程消息记录。
- `private static synchronized void printDelimeter(boolean printThreadInfo)` (L241) — 打印事件分隔线并递增计数。

---

## ErrorLogger

**文件**: `com/sun/electric/tool/user/ErrorLogger.java`（1387 行）
**职责**: 错误/警告记录器（Serializable）。维护 MessageLog/WarningLog 列表，每条 log 含消息文本、所属 Cell、排序键（sortKey）和 ErrorHighlight 高亮列表；支持按 sortKey 分组（GroupLog）、按 Cell 过滤/清除、增量增删、XML 导出与 XML 解析回读；`termLogging` 结束记录并交给 UI 在错误树中展示。

主要方法：
- `public static void writeXmlHeader(String indent, PrintStream ps)` (L102) — 输出 XML DTD 头。
- 内部类 `MessageLog` (L134) — 单条错误记录：构造 (L141/145)、`getCell`(L153)、`getMessageString`(L175)、`getNumHighlights`(L177)、`getHighlights`(L179)、`getSortKey`(L184)、`compareTo`(L193，大小写不敏感字典序)、`findGeometries`(L198)、`getMessage`(L218，带序号)、`writeXmlDescription`(L223)、`isValid`(L243，Cell 与高亮仍有效)、序列化 `write`/`read`(L254/L269)。
- 内部类 `WarningLog` (L302) — 警告记录，继承 MessageLog。
- `public static ErrorLogger newInst(String system)` / `newInst(String system, boolean persistent)` (L386 / L394) — 工厂：按 `User.getErrorLimit()` 设错误上限。
- `public void addMessages(List<MessageLog>)` / `addMessages(ErrorLogger)` / `deleteMessages(List<MessageLog>)` (L408 / L421 / L429) — 批量增删消息并维护几何哈希。
- `private void hashMessage/unHashMessage` (L442 / L458) — 维护"几何对象 → MessageLog 集合"索引。
- `private synchronized MessageLog logAnError(...)` (L479 / L489) — 内部错误入账（受 errorLimit 限制）。
- `public synchronized void logError(...)` 重载 (L524 / L536 / L548 / L562 / L575 / L590 / L604) — 记录错误：纯文本 / 带 Cell / 带 CellId / 带 Geometric+VarContext / 带 Export / 带 EPoint / 带 PolyBase。
- `public synchronized void logMessage(...)` (L625 / L726) 与 `logMessageWithLines(...)` (L639) — 通用记录（错误或警告），支持 Geometric/ImmutableNodeInst/Export/EPoint/Rectangle2D/PolyBase 集合及线段列表。
- `private synchronized MessageLog logAWarning(...)` (L767) — 内部警告入账。
- `public synchronized void logWarning(...)` 重载 (L804 / L818 / L833) — 记录警告（文本 / Geometric / Export）。
- `public synchronized int getNumMessages(Cell cell, boolean searchInError)` (L840) — 统计某 Cell 的错误或警告数。
- `public synchronized boolean findMessage(Cell, Geometric, Cell, Geometric, boolean)` (L869) — 查重：是否已有涉及这两组几何的消息。
- `public synchronized void clearAllLogs()` (L897) — 清空全部错误与警告。
- `public synchronized List<MessageLog> getAllLogs(Cell cell)` (L909) — 取某 Cell 的全部消息。
- `public synchronized boolean clearLogs(Cell cell)` (L931) — 清除某 Cell 的消息，返回是否真有删除。
- `public void exportErrorLogger(String filePath)` (L961) — 把全部日志写成带 DTD 的 XML 文件。
- `public void setGroupName(int sortKey, String groupName)` / `public String getGroupName(int sortKey)` (L1047 / L1060) — 为 sortKey 设置/读取分组名（错误树子树标题）。
- `public synchronized void termLogging(boolean explain)` (L1071) — **[重要]** 结束记录并交给 `Job.getUserInterface().termLogging` 展示。
- `public synchronized void termLogging_(boolean terminate)` (L1078) — 给所有消息编号并可标记 terminated。
- `public String getInfo()` (L1098) — 概要信息（"N errors, M warnings"）。
- `public synchronized void sortLogs()` (L1107) — 按 sortKey 排序错误与警告。
- `public synchronized int getNumErrors()` / `getNumWarnings()` / `getNumLogs()` (L1117 / L1123 / L1129) — 数量统计。
- `public MessageLog getLog(int i)` / `public int getLogIndex(MessageLog)` (L1131 / L1135) — 按下标取消息 / 求消息下标（错误在前）。
- `public synchronized Iterator<MessageLog> getLogs()` (L1151) — 遍历全部消息（快照副本）。
- `public synchronized void deleteLog(int i)` (L1162) — 按下标删除并解除哈希。
- 内部类 `XMLParser` (L1194) — SAX 解析导出的 XML 重建 ErrorLogger；`process`(L1196) 解析入口，`XMLHandler`（startElement L1271 / endElement L1242 等）处理元素。

---

## MessagesStream

**文件**: `com/sun/electric/tool/user/MessagesStream.java`（130 行）
**职责**: 继承 `PrintStream` 的消息流。构造时 `System.setOut(this)` 把全部标准输出重定向到消息窗口；批处理模式下退回 Dummy 输出。提供 `save` 把消息窗口内容导出到文件。

主要方法：
- `public static MessagesStream getMessagesStream()` (L51) — 获取单例（惰性初始化）。
- `public MessagesStream()` (L57) — 构造：强制行分隔符为 "\n" 并接管 System.out。
- 覆写的 `print/println/write` 系列 (L69-94) — 全部转调私有 `print(String, boolean)`。
- `private void print(String s, boolean newLine)` (L96) — 核心分发：批处理写 stdout，否则经 `UserInterface.printMessage` 进消息窗口，失败时兜底 stderr。
- `public void save()` / `public void save(String filePath)` (L122 / L127) — 弹文件选择框保存 / 保存到指定路径（调 `saveMessages`）。

---

## Resources

**文件**: `com/sun/electric/tool/user/Resources.java`（94 行）
**职责**: 图标/图像资源加载工具。按类路径从 `resources/` 包中取资源 URL 或 ImageIcon；并提供可选插件（Java3D、Jython）类的探测加载，缺失时记录到 `TextUtils.recordMissingComponent`。

主要方法：
- `public static ImageIcon getResource(Class<?> theClass, String iconName)` (L46) — 按类加载图标（失败返回空 ImageIcon）。
- `public static URL getURLResource(Class<?> theClass, String resourceName)` (L61) — 取资源的 URL。
- `public static Class<?> get3DClass(String name)` (L66) — 探测 Java3D 插件并加载 3D 类（插件缺失返回 null）。
- `public static Class<?> getJythonClass(String name)` (L74) — 加载 Jython 插件类。
- `private static Class<?> getClass(String name, String plugin)` (L79) — 经 `Electric.classFromPlugins` 加载，异常时记录缺失组件。

---

## GraphicsPreferences

**文件**: `com/sun/electric/tool/user/GraphicsPreferences.java`（616 行）
**职责**: 图形偏好的不可变快照（继承 `PrefPackage`）。持有全局图形字段（端口/输出显示级别、默认字体、临时名显示）与按技术划分的 `TechData`（每层颜色、图案、轮廓、透明度及透明层颜色），外加特殊颜色表（ColorPrefType）和按文本类型的可见性。所有修改通过 `withXxx()` 返回新实例，`putPrefs` 负责 diff 写回偏好树；由 `UserInterfaceMain` 在客户端线程持有当前实例。

主要方法：
- `public GraphicsPreferences(boolean factory)` / `public GraphicsPreferences(boolean factory, TechPool techPool)` (L371 / L375) — 构造：从偏好树（或工厂偏好）装载全部图形数据。
- `public void putPrefs(Preferences prefRoot, boolean removeDefaults)` / `putPrefs(..., GraphicsPreferences oldGp)` (L408 / L412) — 把当前值（相对旧值的差异）写回偏好树。
- `public GraphicsPreferences withTransparentLayerColors(Technology, Color[])` (L451) — 替换某技术全部透明层颜色。
- `public GraphicsPreferences withTransparentLayerColor(Technology, int, Color)` (L456) — 替换单个透明层颜色。
- `public GraphicsPreferences withGraphics(Layer, EGraphics)` (L461) — 替换某层的图形描述。
- `public GraphicsPreferences withColor(ColorPrefType, Color)` / `withFactoryColor(ColorPrefType)` (L466 / L473) — 改特殊颜色 / 恢复其工厂色。
- `public GraphicsPreferences withTextVisibilityOn(TextType, boolean)` (L475) — 改某文本类型可见性。
- `public GraphicsPreferences withPortDisplayLevel(int)` / `withExportDisplayLevel(int)` / `withDisplayLevelReset()` (L482 / L487 / L492) — 改端口/输出显示级别 / 复位两者。
- `public GraphicsPreferences withDefaultFont(String)` / `withShowTempNames(boolean)` (L496 / L501) — 改默认字体 / 临时名显示。
- `public int getNumTransparentLayers(Technology)` (L515) — 某技术透明层数量。
- `public Color[] getTransparentLayerColors(Technology)` (L522) — 某技术透明层颜色。
- `public Color[] getColorMap(Technology)` (L530) — 透明层颜色映射表（2^n 项）。
- `public EGraphics getGraphics(Layer)` (L537) — 某层的图形描述。
- `public Color getColor(ColorPrefType)` / `public boolean isTextVisibilityOn(TextType)` / `public boolean isShowTempNames()` (L544 / L552 / L560) — 字段读取。
- `public boolean equals(Object)` (L565) — 逐字段/TechData 比较。
- `private GraphicsPreferences withTechData(TechData)` (L584) — 替换单个技术数据后返回新实例。
- `private TechData getTechData(Technology)` (L599) — 取技术数据（断言索引一致）。
- `protected String getKeyIn(String what, LayerId)` (L606) — 生成 "KeyLayerInTech" 偏好键。
- 内部类 `TechData` (L91) — 单技术图形数据：`putPrefs`(L188)、`withTransparentColor`(L270)、`withTransparentColors`(L279)、`withGraphics`(L292)、`getColorMap`(L307)。

---

## IconParameters

**文件**: `com/sun/electric/tool/user/IconParameters.java`（709 行）
**职责**: 自动图标生成参数与执行类（Serializable）。保存各类端口所在边、文本旋转/跳过参数（可用 User 偏好或内部默认初始化）；`makeIconForCell` 按这些参数为一个 Cell 生成图标 Cell（黑盒主体、按位置或按特性分布的 export 引脚与引线）。

主要方法：
- `public static IconParameters makeInstance(boolean userDefaults)` (L54) — 工厂：按 User 图标偏好或硬编码默认值初始化。
- `private IconParameters(boolean userDefaults)` (L59) — 初始化 14 个摆放参数。
- `public Cell makeIconForCell(Cell curCell, EditingPreferences ep)` (L134) — **[重要]** 为 Cell 生成图标：收集/合并 export（ShadowExport 按名与下标归纳）、按位置（`getIconGenExportPlacement()==1`，角度排序+最优旋转分配）或按特性分边、创建黑盒（Artwork 厚多边形 + 名字）、逐个放置引脚与引线。
- `private int iconPosition(Export pp)` (L466) — 按端口特性（IN/OUT/BIDIR/PWR/GND/CLK）返回所在边。
- `public static boolean makeIconExport(Export pp, EditingPreferences ep, ...)` (L497) — 创建图标 export（判断总线）。
- `public static boolean makeIconExportBase(String name, boolean isBus, Export sample, ...)` (L517) — **[重要]** 实际创建引脚节点、export（含文本旋转/位置/偏移，可复制原 export 变量）与引线弧（总线用 bus_arc 且不延伸）。
- 内部类 `ShadowExport` (L94) — 同组 export 的聚合（下标合并、中心、靠边判断、按角度排序 L115）。
- 内部类 `ShadowExportByName` (L697) — 按 export 名字典序排序。

---

## Exec

**文件**: `com/sun/electric/tool/user/Exec.java`（463 行）
**职责**: 包装 `Runtime.exec()` 的外部进程执行器（继承 Thread）。解决输出缓冲死锁（后台读线程吃掉 stdout/stderr 并可重定向）、进程结束监听、向进程写输入；内嵌 `OutputStreamChecker` 可实时匹配输出流中的字符串/正则。

主要方法：
- 内部类 `ExecProcessReader` (L51) — 输出吞噬线程；`run()`(L79) 读流并重定向到指定 OutputStream。
- `public interface FinishedListener` (L113) / 内部类 `FinishedEvent` (L120) — 进程结束监听接口与事件（源、命令行、工作目录、退出码）。
- `public Exec(String[] exec, String[] envVars, File dir, OutputStream outStreamRedir, OutputStream errStreamRedir)` (L191) — 构造：记录命令、环境、目录与重定向流。
- `public void run()` (L206) — **[重要]** 设置线程环境与 UserInterfaceExec 后启动进程、启动两个输出吞噬线程、`waitFor` 退出码、join 重定向线程、通知全部 FinishedListener 并关闭输入。
- `public void writeln(String line)` (L292) — 向进程标准输入写一行。
- `public void addFinishedListener(FinishedListener)` / `removeFinishedListener(FinishedListener)` (L307 / L317) — 注册/注销结束监听。
- `public synchronized void destroyProcess()` (L326) — 强杀进程。
- `public int getExitVal()` (L332) — 退出码。
- 内部类 `OutputStreamChecker` (L340) — 包装输出流并匹配字符串/正则：`write(int)`(L398) 逐字符匹配（环形缓冲/行内正则），命中即 `alertListeners`(L442)；`getFound`/`getFoundLine`(L448/449)。
- `public interface OutputStreamCheckerListener` (L460) — 匹配命中即回调 `matchFound`。

---

## ExecMulti

**文件**: `com/sun/electric/tool/user/ExecMulti.java`（127 行）
**职责**: 按顺序（串行）运行多个 Exec 外部进程。每个进程可附带运行前/后注释和"忽略退出码"标志；非零退出码默认中断队列并通知总监听器。

主要方法：
- `public ExecMulti()` (L23) — 初始化队列与监听器列表。
- `public void addExec(Exec e)` / `addExec(Exec e, boolean ignoreExitValue)` / `addExec(Exec e, String preRunComment, String postRunComment, boolean ignoreExitValue)` (L33 / L37 / L41) — 向队列追加进程。
- `public void start()` (L48) — 启动队列（开始第一个进程）。
- `private void startNext()` (L52) — 取下一个进程注册监听、打印注释并启动；全部完成时 `done()`。
- `public void processFinished(Exec.FinishedEvent e)` (L72) — **[重要]** 单进程结束回调：非零退出且不忽略则提前终止队列，否则继续下一个。
- `private void done()` (L89) — 向全部总监听器派发最后一个 FinishedEvent。
- `public void addFinishedListener(Exec.FinishedListener)` / `removeFinishedListener(Exec.FinishedListener)` (L101 / L111) — 总监听器管理。

---

## CellChangeJobs

**文件**: `com/sun/electric/tool/user/CellChangeJobs.java`（1804 行）
**职责**: "对 Cell 进行修改的 Job 集合"（构造器私有，仅静态嵌套 Job 类）。涵盖删除/改名/分组、画 Cell 层次结构图、打包实例为新 Cell、提取实例、新版本与整库复制等操作；各 Job 在后台线程执行，`terminateOK` 在客户端收尾。

主要方法（嵌套类）：
- `public static class DeleteCell` (L79) — 删除单个 Cell；`doIt`(L90) 再次检查占用后 `cell.kill()`。
- `public static class DeleteManyCells` (L103) — 批量删除；`doIt`(L114) 循环删除未被占用的 Cell（占用者延后），`terminateOK`(L143) 汇报并重绘。
- `public static class RenameCell` (L155) — 重命名 Cell；`doIt`(L171) 调 `cell.rename`，`terminateOK`(L178) 用 `User.fixStaleCellReferences` 修复窗口。
- `public static class DeleteCellGroup` (L187) — 删除 Cell 组；`doIt`(L203)。
- `public static class RenameCellGroup` (L223) — 重命名 Cell 组；`doIt`(L235)，`renameGellGroupFunction`(L240) 处理组内同名/异名情况。
- `public static class GraphCells` (L284) — **[重要]** 在 "CellStructure" Cell 中画出 Cell 层次结构图；`doIt`(L312) 建 GraphNode、算深度并摆放，`terminateOK`(L604) 显示。
- `public static class GraphLibraries` (L650) — 画库结构图；`doIt`(L677)、`terminateOK`(L875)。
- `public static class PackageCell` (L888) — 把选中实例打包进新 Cell；`doIt`(L907)、`terminateOK`(L973)。
- `public static class ExtractCellInstances` (L982) — 按深度提取 Cell 实例；`doIt`(L1013)、`terminateOK`(L1023)。
- `public static void doArbitraryExtraction(Cell, ExpansionState, Set<NodeInst>, ...)` (L1029) — 任意展开状态下提取实例的公共实现。
- `public static class NewCellVersion` (L1262) — 创建 Cell 新版本；`doIt`(L1276)、`terminateOK`(L1286)。
- `public static class DuplicateCell` (L1313) — 复制 Cell；`doIt`(L1343) 递归复制内容，`terminateOK`(L1407)。
- `public static void copyExpandedStatus(Map<CellId,Cell> newCells)` (L1442) — 复制后恢复实例展开状态。
- `public static IdMapper copyRecursively(List<Cell> fromCells, Library toLib, boolean verbose, boolean move, ...)` (L1478) — 跨库递归复制 Cell 的公共入口。

---

## CircuitChangeJobs

**文件**: `com/sun/electric/tool/user/CircuitChangeJobs.java`（3719 行）
**职责**: "对电路进行修改的 Job 集合"（构造器包私有，仅静态嵌套 Job 类与辅助静态方法）。涵盖旋转/镜像、对齐、弧属性与扩展、总线拆分、删除、清理 pin、移动、重连、技术/库改名、检查修复与锁定清除等；`testEditable`/`cantEdit` 是编辑权限与锁定检查的统一入口。

主要方法（嵌套类与静态方法）：
- `public static class RotateSelected` (L108) — 旋转选中对象；`doIt`(L135)。
- `public static class RotateText` (L297) — 仅旋转文本；`doIt`(L325)；辅助 `rotateArcText`(L361)、`rotateNodeText`(L382)。
- `public static class AlignObjects` (L493) — 对齐选中对象（居中/按顺序）；`doIt`(L507)。
- `public static class AlignNodes` (L704) — 按端口对齐节点；`doIt`(L719)。
- `public static class ChangeArcProperties` (L776) — 改弧属性（刚性/定角/端延伸等）；`doIt`(L799)、`terminateOK`(L905)。
- `public static class ToggleNegationJob` (L915) — 切换取反圈；`doIt`(L934)、`terminateOK`(L968)。
- `public static class RipTheBus` (L977) — 拆分总线；`doIt`(L991)。
- `public static class DeleteSelected` (L1138) — 删除选中对象；`doIt`(L1157)、`terminateOK`(L1265)。
- `public static class DeleteSelectedGeometry` (L1283) — 按几何矩形删除；`doIt`(L1297)。
- `public static class DeleteArcs` (L1483) — 删除弧（可只删一端）；`doIt`(L1494)。
- `public static void eraseObjectsInList(Cell, List<Geometric>, boolean reconstructArcsAndExports, ...)` (L1529) — 删除对象列表的公共实现。
- `public static class CleanupChanges` (L1727) — 电路清理（多余 pin 等）；`doIt`(L1756)。
- `public static class ShortenArcs` (L1864) — 缩短重叠弧；`doIt`(L1877)。
- `public static class ManyMove` (L1914) — 大批量移动；`doIt`(L1936)、`terminateOK`(L2279)。
- `public static class CellCenterToCenterOfSelection` (L2345) — Cell 中心移到选中对象中心；`doIt`(L2358)。
- `public static List<Reconnect> getPinsToPassThrough(Cell, EditingPreferences)` (L2399) — 找可穿过的 pin。
- `public static class Reconnect` (L2427) — 重连：`erasePassThru`(L2446)、`reconnectArcs`(L2580)。
- `public static void spreadCircuitry(Cell, NodeInst, char direction, double amount, ...)` (L2622) — 在约束下推开电路。
- `public static NodeInst replaceNodeInst(NodeInst, NodeProto, ...)` (L2896) / `replaceNodeInsts(...)` (L2915) — 节点原型替换（BatchChanges）。
- `public static void inheritAttributes(NodeInst, EditingPreferences)` (L2949) / `inheritCellParameter(...)` (L3045) — 属性/参数继承。
- `public static class RenameTechnology` (L3133) / `RenameLibrary` (L3166) — 技术与库改名 Job（`doIt` L3146/L3180）。
- `public static void markAllLibrariesForSavingCommand()` (L3198) / `markCurrentLibForSavingCommand()` (L3241) — 标记全部/当前库为已修改（嵌套 Job L3203/L3246）。
- `public static class CheckAndRepairJob` (L3280) — 一致性检查与修复；`doIt`(L3291)。
- `public static class ReloadLibraryJob` (L3313) — 重载库；`doIt`(L3324)。
- `public static void testEditable(Cell, NodeInst, boolean giveError, boolean allowInstanceChange)` (L3416) — 检查对象是否可编辑（可能抛 CantEditException）。
- `public static int cantEdit(Cell, NodeInst, boolean giveError, boolean allowInstanceChange, boolean insideJob)` (L3490) — **[重要]** 锁定/权限检查并弹确认，返回是否允许修改。
- `public static class ClearNodeLocked` (L3596) / `ClearCellLocked` (L3617) — 清除节点/Cell 锁定（`doIt` L3607/L3630）。
- `public static class MakeCellAnnotationJob` (L3639) — 添加 Cell 标注；`makeAnnotationMenuCommand`(L3648)、`doIt`(L3668)、`terminateOK`(L3673)、`addAnnotation`(L3679/L3684)。

---

## CircuitChanges

**文件**: `com/sun/electric/tool/user/CircuitChanges.java`（2295 行）
**职责**: "用户级电路修改命令"入口类（构造器私有，全静态方法）。每个方法对应一条菜单命令：收集选中对象/参数、必要时弹对话框（如取网络名）、启动 CircuitChangeJobs/CellChangeJobs 中的相应 Job；另含网络连通、库列表、清理等维护命令。

主要方法：
- `public static void rotateObjects(int amount)` (L123) — 旋转选中对象 90°/180°/270°。
- `public static void mirrorObjects(boolean horizontally)` (L188) — 水平/垂直镜像。
- `public static void alignToGrid()` (L237) — 对齐到网格。
- `public static void alignNodes(boolean horizontal, int direction)` (L293) — 节点对齐。
- `public static void arcRigidCommand()` / `arcNotRigidCommand()` / `arcFixedAngleCommand()` / `arcNotFixedAngleCommand()` / `arcDirectionalCommand()` / `arcHeadExtendCommand()` / `arcTailExtendCommand()` (L401-L461) — 弧属性命令（刚性、定角、方向、端延伸）。
- `public static void toggleNegatedCommand()` (L471) — 切换取反。
- `public static List<Highlight> getHighlighted()` (L482) — 取当前高亮对象列表（供 Job 序列化前保存）。
- `public static void ripBus()` (L494) — 拆分总线。
- `public static void deleteSelected()` (L512) — 删除选中。
- `public static void cellCenterToCenterOfSelection()` (L584) — Cell 中心居中。
- `public static void deleteArcsOnSelected(boolean both)` (L603) — 删除选中对象上的弧。
- `public static boolean deleteCell(Cell cell, boolean confirm, boolean quiet)` (L654) — 删除 Cell（可确认/静默）。
- `public static void cleanCellRef(Cell cell)` (L679) — 清理 Cell 引用（死后窗口/树刷新）。
- `public static void renameCellInJob(Cell, String)` (L708) / `renameCellGroupInJob(Cell.CellGroup, String)` (L744) — 启动改名 Job。
- `public static void graphCellsFromCell()` / `graphCellsInLibrary()` / `graphLibraries()` (L754 / L764 / L772) — 画 Cell/库结构图。
- `public static void packageIntoCell()` (L782) — 打包为新 Cell。
- `public static void extractCells(int depth)` (L828) — 按深度提取实例。
- `public static void cleanupPinsCommand(boolean everywhere, EditingPreferences ep)` (L868) — 清理多余 pin（遍历全部 Cell 或仅当前）。
- `public static void showNonmanhattanCommand()` (L1137) — 高亮非曼哈顿几何。
- `public static void showPureLayerCommand()` (L1247) — 高亮纯层节点。
- `public static void shortenArcsCommand()` (L1280) — 缩短重叠弧。
- `public static void showRedundantPureLayerNodes()` (L1293) — 高亮冗余纯层节点。
- `public static boolean isBox(Point2D[] points)` (L1386) — 判断多边形是否矩形（供打包命令用）。
- `public static void newVersionOfCell(Cell cell)` (L1410) — 创建新版本。
- `public static void connectOverlappingNetworks()` (L1429) — 连接重叠网络（含对话框与 RouteOverlapJob L1508）。
- `public static void manyMove(double dX, double dY)` (L1951) — 大批量移动。
- `public static void DoExpandCommands(boolean unExpand, int amount)` (L1997) — 批量展开/收起 Cell 实例。
- `public static void listLibrariesCommand()` (L2105) — 列出库信息。
- `public static void renameCurrentTechnology()` (L2193) / `renameLibrary(Library lib)` (L2206) — 启动改名 Job。
- `public static void checkAndRepairCommand(boolean repair)` (L2216) — 一致性检查/修复。
- `public static void findUnusedLibraryFiles()` (L2224) — 找磁盘上未载入的库文件。
- `public static void removeUnusedLayers(Library lib)` (L2290) — 移除库中未使用的层。

---

## ViewChanges

**文件**: `com/sun/electric/tool/user/ViewChanges.java`（2268 行）
**职责**: "电路视图相关修改"入口类（构造器包私有，静态命令方法与嵌套 Job）。负责 Cell 视图的创建与转换：新建 Cell 并展示、转换旧多页视图、切换视图（schematic/layout 互转）、生成骨架（skeleton）、图标（icon）视图、原理图视图，以及由原理图自动生成版图视图（含标准单元库匹配与摆放）。

主要方法（嵌套类与静态方法）：
- `public static class CreateAndViewCell` (L113) — 创建 Cell 并显示；`doIt`(L128)、`terminateOK`(L136)。
- `public static void convertMultiPageViews()` (L147) — 转换旧版多页原理图（嵌套 FixOldMultiPageSchematics L173）。
- `public static void changeCellView(Cell cell, View newView)` (L241) — 切换 Cell 视图（嵌套 ChangeCellView L279）。
- `public static void makeSkeletonViewCommand()` (L314) — 生成骨架视图（嵌套 MakeSkeletonView L336）。
- `public static boolean skeletonizeCell(Cell curCell, Cell skeletonCell, EditingPreferences ep)` (L389) — 骨架化的核心实现（抽取实例成端口）。
- `public static void makeIconViewCommand()` (L505) — GUI 入口生成图标视图。
- `public static void makeIconViewNoGUI(Cell curCell, boolean doItNow, EditingPreferences ep, boolean fixedValues)` (L521) — 无 GUI 生成图标视图（嵌套 MakeIconView L535）。
- `public static int iconTextRotation(Export pp, EditingPreferences ep)` (L632) — 按端口特性计算图标文本旋转（IconParameters 也调用）。
- `public static void makeSchematicView()` (L655) — 生成原理图视图（嵌套 MakeSchematicView L662）。
- `public static void makeLayoutView()` (L962) — GUI 入口生成版图视图（弹 MakeNewViewDialog L974 选择布局选项）。
- `public static class MakeLayoutView` (L1135) — **[重要]** 版图视图生成 Job；`doIt`(L1157) 用 `MakeLayoutVisitor`(L1200) 遍历层次、匹配标准单元库（`findStandardCell` L2146、`getEssentialContent` L2197、`computeParameterDistance` L2254）、用 `PlacerGrid`(L1830)/`Leaf`(L1965) 摆放版图节点。

---

## ExportChanges

**文件**: `com/sun/electric/tool/user/ExportChanges.java`（2011 行）
**职责**: "Electric 中所有 Export 修改命令"（final 类，静态方法与嵌套 Job）。涵盖 export 列表描述、沿 export 追踪层次、对节点/端口重新导出（re-export）、删除/移动/改名 export、修改特性、数字重编号、在窗口间跳转显示、跨库同步与替换等。

主要方法（嵌套类与静态方法）：
- `public static void describeExports(boolean summarize)` (L85) — 列出 Cell 的 export（嵌套 DescribeExports L100，`doIt` L113）。
- `public static class FollowExport` (L439) — 沿 export 追踪（嵌套 Job，`doIt` L453）。
- `public static void reExportAll()` (L592) — 重导出全部未连接端口。
- `public static void reExportSelected(boolean wiredPorts, boolean unwiredPorts)` (L611) — 重导出选中节点。
- `public static void reExportSelectedPort()` (L631) — 重导出选中端口。
- `public static void reExportPowerAndGround()` (L680) — 重导出电源/地（嵌套 ReExportNodes L697）。
- `public static int reExportNodes(Cell, List<Geometric>, ...)` (L751) — 节点重导出核心实现。
- `public static class ReExportPorts` (L792) — 端口重导出 Job。
- `public static void reExportHighlighted(boolean deep, boolean wiredPorts, boolean unwiredPorts)` (L843) — 层次重导出（嵌套 ReExportHighlighted L867）。
- `public static int reExportPorts(Cell, List<PortInst>, ...)` (L985) — 端口重导出核心实现。
- `public static PortInst getNewPortFromReferenceExport(NodeInst, Export)` (L1117) — 按参考 export 在新节点上找对应端口。
- `public static void deleteExport()` (L1149) — 删除选中 export（弹对话框）。
- `public static void exportConnectionList()` (L1167) — 打印网络连接表。
- `public static void deleteExportsOnSelected()` (L1217) / `deleteExportsInArea()` (L1247) — 删除选中对象上/区域内 export。
- `public static void deleteExports(Cell, List<Export>)` (L1285) — 删除核心（嵌套 DeleteExports L1306）。
- `public static void moveExport()` (L1332) — 移动 export 名字（嵌套 MoveExport L1374）。
- `public static void renameExport()` (L1399) — 改名（嵌套 RenameExport L1417）。
- `public static class ChangeExportCharacteristic` (L1440) / `ChangeExportBodyOnly` (L1463) — 改 export 特性 / 改"仅主体"标志。
- `public static class RenumberNumericExports` (L1486) — 数字 export 重编号（配合 ExportsByNumber L1528）。
- `public static void showExports(List<Export>)` (L1554) / `showPorts(List<PortInst>)` (L1562) — 在窗口间依次显示 export/端口位置。
- `public static void synchronizeLibrary()` (L1824) — 按参考库同步当前库 export（嵌套 SynchronizeExports L1855）。
- `public static void replaceFromOtherLibrary()` (L1928) — 用另一库同名 Cell 的 export 替换（嵌套 ReplaceFromOtherLibrary L1963）。

---

## CantEditException

**文件**: `com/sun/electric/tool/user/CantEditException.java`（104 行）
**职责**: 继承 `JobException` 的"无法编辑"异常。由编辑权限检查抛出，携带被锁定的节点/原语/复杂节点/Cell 信息；`presentProblem` 弹对话框向用户确认（Yes/No/Always/Cancel），选择 Always 时可能修改数据库（解除锁定）或改写 User 偏好。

主要方法：
- `public int presentProblem()` (L47) — **[重要]** 按锁定类型依次弹 `askForChoice` 对话框；返回 0=允许修改，正数=跳过本次，负数=取消整个操作；"Always" 会解除对应锁定/改 `User.setDisallowModificationLockedPrims` 等偏好。注意：可能改数据库，须在 Change Job 内调用。
- 字段 `lockedNode` / `lockedPrim` / `lockedComplex` / `lockedInstances` / `lockedAll` / `lockedExample` (L32-37) — 各类锁定对象。
