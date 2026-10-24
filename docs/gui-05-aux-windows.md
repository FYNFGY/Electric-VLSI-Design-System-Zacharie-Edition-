# 辅助窗口（MessagesWindow / TextWindow / JobTree / ExplorerTree）

> 基于 Electric 9.08.1 源码，包 com.sun.electric.tool.user.ui。本文件由 AI 生成。

## 模块概述

本组文件实现 Electric 用户 GUI 中的四类辅助窗口及两个支撑类。MessagesWindow 是 Java 侧的消息控制台（用作 System.out 的 PrintStream 后端），以线程安全方式在 JTextArea 中累积输出，支持 MDI 内部窗口、独立 JFrame 和停靠（Dock）三种显示模式，并提供右键剪切/复制/粘贴、保存、清空和字体设置功能。TextWindow 实现 WindowContent 接口，用于显示和编辑文本类 cell（如 doc 视图），支持撤销/重做、剪贴板、文件读写（readTextCell/writeTextCell）、行号跳转和文本搜索替换，并在文本变化时通过 SaveCellText Job 异步保存回 cell。JobTree 与 EJobTreeNode 构成资源管理器树中的 JOBS 分支，以单例节点静态维护展开状态，用差分算法增量同步 Job.Inform 列表并触发 TreeModelEvent。ExplorerTree（重点）是继承 JTree 的层次化单元浏览器，配合 ExplorerTreeModel 按字母序/单元组/层次三种模式构建库树，实现拖放（跨库复制）、按节点类型区分的右键菜单、双击打开 cell、CVS/项目管理和波形信号交互等大量功能；ExplorerTreeModel 是其 DefaultTreeModel 子类；Util 提供 HSV 颜色转换工具函数。

---

## MessagesWindow

**文件**: `com/sun/electric/tool/user/ui/MessagesWindow.java`（645 行）
**职责**: 作为 Java 侧控制台，解决标准 Electric 控制台无法处理多线程打印的问题。该类的实例应被设为 System.out 的 PrintStream 后端（`System.setOut(new PrintStream(new MessagesWindow()))`），正常使用中无需直接调用其方法。
**继承/实现**: `implements MouseListener, ClipboardOwner`
**关键字段**:
- `info (JTextArea)` — 文本显示区（等宽字体，自动滚动）
- `contentFrame (Container)` — 内容面板（JInternalFrame 的 contentPane、JFrame 的 contentPane 或停靠模式下的纯 JPanel）
- `jf (Container)` — 实际窗口容器（MDI 模式为 JInternalFrame，独立模式为 JFrame，停靠模式为 null）
- `initialized (static boolean)` — 是否已完成初始化（保证 init() 幂等）
- `currentFont (static Font)` — 所有消息窗口共享的当前字体
- `messagesWindows (static final HashSet<MessagesWindow>)` — 所有消息窗口实例的集合
- `text (static final StringBuffer)` — 停靠（Dock）模式下窗口不存在时暂存文本的缓冲

### 方法

#### `public static synchronized void init()`
- 行号: L96
- 描述: 初始化消息窗口（幂等）：若未启用停靠则创建新窗口实例；随后把 ActivityLogger 的日志信息追加到窗口。

#### `public static Iterable<MessagesWindow> getMessagesWindows()`
- 行号: L103
- 描述: 纯 getter，返回所有消息窗口实例的集合。

#### `public Container getContent()`
- 行号: L107
- 描述: 纯 getter，返回内容面板。

#### `public MessagesWindow()`
- 行号: L109
- 描述: **`[重要]`** 构造器。根据运行模式创建容器：MDI 模式创建 `JInternalFrame("Electric Messages")` 并加入桌面；非停靠模式创建 `JFrame`（关闭操作为 DO_NOTHING_ON_CLOSE，Mac 上附加菜单栏，可恢复用户默认位置/尺寸）；停靠模式仅创建 JPanel。随后创建 20x110 的等宽 JTextArea 放入 JScrollPane，注册自身为鼠标监听器；停靠模式下把暂存缓冲 `text` 回放进新窗口，并把实例加入 `messagesWindows` 集合。

#### `public boolean isFocusOwner()`
- 行号: L175
- 描述: 判断消息窗口是否当前窗口（MDI 模式看是否被选中，否则看焦点归属）。

#### `public static MessagesWindow getFocusOwner()`
- 行号: L186
- 描述: 遍历所有消息窗口，返回拥有焦点的那个，无则返回 null。

#### `public void requestFocus()`
- 行号: L196
- 描述: 请求窗口焦点；若不在客户端（EDT）线程则通过 `SwingUtilities.invokeLater` 调度，否则直接执行 `requestFocusUnsafe`。

#### `private void requestFocusUnsafe()`
- 行号: L208
- 描述: 在 EDT 上执行：MDI 模式置前并选中内部窗口（忽略 PropertyVetoException），独立模式置前并请求焦点。

#### `public Rectangle getMessagesLocation()`
- 行号: L228
- 描述: 返回窗口 bounds（无窗口容器时返回内容面板 bounds）。

#### `public static int getMinMessagesCharWidth()`
- 行号: L238
- 描述: 返回所有消息窗口中最小列数。

#### `public static void tileWithEdit()`
- 行号: L249
- 描述: **`[重要]`** 把消息窗口吸附到当前编辑窗口正下方：取当前 WindowFrame 的 bounds，将消息窗口的 x/宽度对齐编辑窗口、y 置于其下方，并 `setBounds` 应用。

#### `public static void appendString(String str)`
- 行号: L276
- 描述: **`[重要]`** 向消息窗口追加文本的静态入口：窗口尚不存在且非停靠模式时把错误打到 System.err；停靠模式先追加到静态缓冲 `text`；再遍历所有实例逐个 `appendStr`。

#### `private void appendStr(String str)`
- 行号: L287
- 描述: 实例级追加：`info.append(str)`，并通过 invokeLater 把光标移到文档末尾实现自动滚动。

#### `public void mouseClicked(MouseEvent e)` / `public void mouseEntered(MouseEvent e)` / `public void mouseExited(MouseEvent e)` / `public void lostOwnership(Clipboard parClipboard, Transferable parTransferable)`
- 行号: L303 / L305 / L307 / L321
- 描述: 接口空实现（MouseListener / ClipboardOwner），无实际行为。

#### `public void mousePressed(MouseEvent e)` / `public void mouseReleased(MouseEvent e)`
- 行号: L309 / L315
- 描述: 鼠标按下/释放时若为弹出菜单触发（右键），调用 `doContext` 弹出上下文菜单。

#### `private void doContext(MouseEvent e)`
- 行号: L323
- 描述: **`[重要]`** 构建右键菜单：Cut、Copy、Paste、分隔线、Cut All、Copy All、Save All（仅 `Job.getDebug()` 时显示）、Clear；在鼠标位置显示。

#### `public void pasteText()`
- 行号: L361
- 描述: 把剪贴板文本粘贴到消息窗口（`info.paste()`）。

#### `public void copyText(boolean all, boolean cut)`
- 行号: L371
- 描述: 复制文本：`all=true` 时复制/剪切全部（剪切用 selectAll+cut，复制用 StringSelection 写入系统剪贴板）；否则只操作选中文本。`cut=true` 表示剪切。

#### `public void saveAll()`
- 行号: L394
- 描述: 通过 OpenFile 选择输出 .txt 文件，把消息窗口全部文本写入该文件。

#### `public void clear(boolean all)`
- 行号: L418
- 描述: 清空文本：`all=true` 删除全部，否则仅删除选中部分（replaceSelection("")）。

#### `public static void clearAll()`
- 行号: L429
- 描述: 清空停靠缓冲及所有消息窗口实例的文本。

#### `public void selectAll()`
- 行号: L438
- 描述: 全选消息窗口文本。

#### `public static void selectFont()`
- 行号: L448
- 描述: 弹出 FontSelectDialog 交互式选择消息窗口字体（MDI 模式以当前 JFrame 为父窗口）。

#### 内部类 FontSelectDialog extends EDialog
- `public FontSelectDialog(Frame parent)` — 行号: L469。构造对话框：列出系统所有字体族名与预设字号（8-72），显示当前字体样例，OK/Cancel 按钮，窗口关闭视为取消。
- `private void cancel()` — 行号: L611。关闭对话框（dispose）。
- `private void updateSampleText()` — 行号: L616。根据字体列表与字号列表的当前选择刷新样例文本的字体。
- `private void OK()` — 行号: L626。**`[重要]`** 读取选择的字体名/字号，若与初始值不同则更新静态 `currentFont` 并应用到所有消息窗口实例的 JTextArea，最后关闭对话框。

---

## TextWindow

**文件**: `com/sun/electric/tool/user/ui/TextWindow.java`（806 行）
**职责**: 定义用于显示文本 cell 的窗口。将 cell 的文本视图内容装入 JTextArea，提供撤销/重做、剪贴板操作、磁盘文件读写、行号跳转、搜索替换及打印初始化等；文本修改会通过 SaveCellText Job 写回数据库。
**继承/实现**: `implements WindowContent`
**关键字段**:
- `cell (Cell)` — 窗口中显示的 cell
- `wf (WindowFrame)` — 包含本窗口的 WindowFrame
- `overall (TextWindowPanel)` — 整体面板（含滚动区）
- `finishing (boolean)` — 窗口是否正在关闭
- `reloading (boolean)` — 是否正在程序性重载文本（用于抑制保存触发）
- `textArea (JTextArea)` — 文本编辑区
- `scrollPane (JScrollPane)` — 滚动容器
- `undo (UndoManager)` — 撤销管理器
- `undoListeners / redoListeners (static WeakReferences<PropertyChangeListener>)` — 撤销/重做状态变更监听器（弱引用）
- `searchString / searchCaseSensitive` — 当前搜索串与大小写标志

### 方法

#### `public TextWindow(Cell cell, WindowFrame wf)`
- 行号: L91
- 描述: **`[重要]`** 构造器：创建 JTextArea、JScrollPane 与 TextWindowPanel，调用 `setCell` 装入 cell 文本，注册文档监听器（TextWindowDocumentListener）、撤销监听器（MyUndoableEditListener）和焦点监听器。

#### `public static class TextWindowPanel extends JPanel`
- 行号: L115
- 描述: 空壳类，复制 JPanel 仅用于类型识别——标记某 JPanel 是 TextWindow 的面板。

#### `public void setCursor(Cursor cursor)`
- 行号: L119
- 描述: 空实现（WindowContent 接口），TextWindow 不处理光标。

#### `private void setCellFont(Cell cell)`
- 行号: L124
- 描述: 设置文本区字体：默认取 User 的默认文本 cell 字体/字号，若 cell 定义了 TEXT_CELL_FONT_NAME / TEXT_CELL_FONT_SIZE 变量则覆盖。

#### 内部类 MyUndoableEditListener（`implements UndoableEditListener`）
- `public void undoableEditHappened(UndoableEditEvent e)` — 行号: L139。把每次编辑记入 UndoManager，并调用 `updateUndoRedo` 刷新菜单状态。

#### `public static void addTextUndoListener(PropertyChangeListener l)` / `addTextRedoListener` / `removeTextUndoListener` / `removeTextRedoListener`
- 行号: L150 / L152 / L154 / L156
- 描述: 纯注册/注销，向静态弱引用监听器集合添加/移除撤销与重做状态监听器。

#### `private void updateUndoRedo()`
- 行号: L158
- 描述: 通过 `Job.getExtendedUserInterface().showUndoRedoStatus(undo.canUndo(), undo.canRedo())` 刷新界面撤销/重做可用状态。

#### `public void undo()`
- 行号: L166
- 描述: **`[重要]`** 撤销一次文本修改；无可撤销内容时打印提示；捕获 CannotUndoException。

#### `public void redo()`
- 行号: L186
- 描述: **`[重要]`** 重做一次文本修改；无可重做内容时打印提示；捕获 CannotRedoException。

#### `public void paint(Graphics g)`
- 行号: L206
- 描述: 绘制：若 cell 是当前 cell 则先让 textArea 请求键盘焦点，再绘制文本区。

#### `public void updateFontInformation()`
- 行号: L218
- 描述: 用 User 默认文本 cell 字体/字号重新设置文本区字体（响应全局字体偏好变化）。

#### 内部静态类 TextWindowDocumentListener（`implements DocumentListener, FocusListener`）
- `public void changedUpdate(DocumentEvent e)` / `insertUpdate(DocumentEvent e)` / `removeUpdate(DocumentEvent e)` — 行号: L232 / L234 / L236。任何文档变更都转发到 `tw.textWindowContentChanged()`。
- `public void focusGained(FocusEvent e)` — 行号: L238。获得焦点时让菜单栏忽略文本编辑快捷键（避免与输入冲突）。
- `public void focusLost(FocusEvent e)` — 行号: L244。失去焦点时恢复菜单栏对文本编辑键的处理。

#### `public void copy()`
- 行号: L251
- 描述: 把选中文本写入系统剪贴板（TextUtils.setTextOnClipboard）。

#### `public void cut()`
- 行号: L262
- 描述: 把选中文本写入剪贴板并用空串替换选区（即剪切）。

#### `public void paste()`
- 行号: L274
- 描述: 读取剪贴板文本并替换当前选区。

#### `private void textWindowContentChanged()`
- 行号: L282
- 描述: 文本内容变化回调：非重载状态下创建并启动 SaveCellText Job 把内容保存回 cell。

#### `public List<MutableTreeNode> loadExplorerTrees()`
- 行号: L288
- 描述: 委托 `wf.loadDefaultExplorerTree()` 返回默认资源管理器树。

#### `public void loadTechnologies()`
- 行号: L293
- 描述: 空实现。

#### `public JPanel getPanel()`
- 行号: L300
- 描述: 纯 getter，返回顶层面板 overall。

#### `public void finished()`
- 行号: L306
- 描述: 空实现；WindowFrame 关闭时调用以清理，TextWindow 无需处理。

#### 内部静态类 SaveCellText extends Job
- `private SaveCellText(TextWindow tw)` — 行号: L316。构造 "Save Cell Text" CHANGE 作业，捕获当前 cell 与文本行数组并 `startJob()`。
- `public boolean doIt() throws JobException` — 行号: L324。执行 `cell.setTextViewContents(strings, ...)` 把文本写回 cell，返回 true。

#### `public void setWindowTitle()`
- 行号: L334
- 描述: 用 `wf.composeTitle(cell, "", 0)` 设置窗口标题。

#### `public Cell getCell()`
- 行号: L344
- 描述: 纯 getter，返回窗口显示的 cell。

#### `public Highlighter getHighlighter()`
- 行号: L346
- 描述: 恒返回 null（文本窗口无高亮器）。

#### `public void setCell(Cell cell, VarContext context, WindowFrame.DisplayAttributes displayAttributes)`
- 行号: L351
- 描述: **`[重要]`** 切换窗口显示的 cell：读取 `cell.getTextViewContents()` 拼成单串，设置字体，在 `reloading=true` 保护下替换文本区内容并复位选区，最后刷新窗口标题。

#### `public static void readTextCell()`
- 行号: L368
- 描述: **`[重要]`** 静态入口：校验当前窗口内容是 TextWindow（否则报错），弹出 OpenFile 选择输入文件后调用实例方法读入。

#### `public void readTextCell(String fileName)`
- 行号: L385
- 描述: **`[重要]`** 实例方法：打开 URL 流，按 64KB 缓冲逐块读入字符，清空后把文件内容填进文本区。

#### `public static void writeTextCell()`
- 行号: L436
- 描述: **`[重要]`** 静态入口：校验当前窗口是 TextWindow，默认文件名取 cell 名加 ".txt"，经 OpenFile 选择输出文件后调用实例方法写出。

#### `public boolean writeTextCell(String fileName)`
- 行号: L460
- 描述: 按文档段落（行）逐行把文本写入文件，返回是否成功（IO 异常返回 false）。

#### `public void goToLineNumber(int lineNumber)`
- 行号: L498
- 描述: 选中指定行（1-based）：越界打印提示，否则选中该行的起止偏移。

#### `private void updateText(String [] strings)`
- 行号: L516
- 描述: 用字符串数组（拼成单串）替换文本区内容。

#### `public static void updateText(Cell cell)`
- 行号: L527
- 描述: **`[重要]`** 静态刷新入口：当某 cell 文本被系统其他部分修改后，遍历所有 WindowFrame，对显示该 cell 且未处于关闭中的 TextWindow 重新装载文本。

#### `private static String makeOneString(String [] strings)`
- 行号: L553
- 描述: 把字符串数组拼接为单个字符串（每行后追加 "\n"）。

#### `public int getLineCount()`
- 行号: L569
- 描述: 返回文本行数（文档根元素子元素数）。

#### `public String [] convertToStrings()`
- 行号: L581
- 描述: **`[重要]`** 把当前文档逐行转换为字符串数组（去掉行尾换行符），供保存回 cell 使用。

#### `public void rightScrollChanged(int value)` / `public void bottomScrollChanged(int value)` / `public void repaint()` / `public void fullRepaint()`
- 行号: L600 / L602 / L604 / L606
- 描述: 空实现（WindowContent 接口，TextWindow 不处理滚动与重绘）。

#### `public void fillScreen()` / `public void zoomOutContents()` / `public void zoomInContents()` / `public void focusOnHighlighted()`
- 行号: L621 / L623 / L625 / L627
- 描述: 空实现（缩放/平移类接口，文本窗口不适用）。

#### `public void initTextSearch(String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, boolean highlightedOnly)`
- 行号: L642
- 描述: 初始化文本搜索：保存搜索串与大小写标志；regExp=true 时打印"暂不支持正则匹配"提示。

#### `public boolean findNextText(boolean reverse)`
- 行号: L657
- 描述: **`[重要]`** 从当前选区出发逐行查找下一个匹配（reverse=true 反向），找到则选中该匹配并返回 true，遍历完所有行返回 false。

#### `public void replaceText(String replace)`
- 行号: L709
- 描述: 用新文本替换 findNextText 刚选中的内容。

#### `public void replaceAllText(String replace)`
- 行号: L720
- 描述: **`[重要]`** 全文替换：逐行循环查找 searchString 并替换所有出现，用 replaceRange 写回每一行。

#### `public void writeImage(ElectricPrinter ep, String filePath)`
- 行号: L757
- 描述: 未实现：打印 "TextWindow:writeImage not implemented"。

#### `public boolean initializePrinting(ElectricPrinter ep, PageFormat pageFormat)`
- 行号: L768
- 描述: 按可打印区域与 DPI 设置面板尺寸并 validate/repaint，恒返回 true。

#### `public BufferedImage getPrintImage(ElectricPrinter ep)`
- 行号: L783
- 描述: 恒返回 null。

#### `public void panXOrY(int direction, double[] panningAmounts, int ticks)`
- 行号: L794
- 描述: 空实现。

#### `public void centerCursor()`
- 行号: L802
- 描述: 空实现。

---

## JobTree

**文件**: `com/sun/electric/tool/user/ui/JobTree.java`（301 行）
**职责**: 定义资源管理器树中的 Job 信息节点（JOBS 分支）。使用静态单例节点以便跨次重建保持树的展开/折叠状态，将 Job.Inform 列表映射为 EJobTreeNode 叶子节点，并以增量方式同步更新。
**继承/实现**: `extends DefaultMutableTreeNode`
**关键字段**:
- `jobNodeName (static final String)` — 节点名 "JOBS"
- `jobTree (static final JobTree)` — 单例 JobTree 节点
- `jobPath (static final TreePath)` — 从 ExplorerTreeModel.rootNode 到 jobTree 的固定 TreePath
- `jobNodes (final Vector<TreeNode>)` — 子节点（EJobTreeNode）向量，直接作为 DefaultMutableTreeNode 的 children
- `indices (int[])` — 差分更新中收集的变更索引数组（可扩容）
- `indicesCount (int)` — indices 中有效元素个数

### 方法

#### `private JobTree()`
- 行号: L56
- 描述: 私有构造器（保证单例）：以 "JOBS" 为节点名，并把 `children` 直接指向 jobNodes。

#### `public static DefaultMutableTreeNode getExplorerTree()`
- 行号: L62
- 描述: 返回单例 jobTree（供 ExplorerTreeModel 挂载）。

#### `public static void update(List<Job.Inform> jobs)`
- 行号: L68
- 描述: **`[重要]`** 用最新 Job.Inform 列表更新树（委托 updateJobs）。

#### `public static JPopupMenu getPopupStatus(EJobTreeNode jobNode)`
- 行号: L73
- 描述: 构建 job 节点右键菜单：Get Info、Abort、Delete，绑定 JobMenuActionListener。

#### `public boolean isLeaf()` / `public boolean getAllowsChildren()`
- 行号: L85 / L86
- 描述: TreeNode 覆写：恒返回 false / true（JOBS 节点非叶子、允许子节点）。

#### `public void insert(MutableTreeNode newChild, int childIndex)` / `public void remove(int childIndex)`
- 行号: L87 / L88
- 描述: TreeNode 覆写：子节点由 update() 统一管理，直接操作抛 UnsupportedOperationException。

#### `public int getIndex(TreeNode aChild)`
- 行号: L103
- 描述: 返回 aChild 在 jobNodes 中的索引（线性查找 O(n)）；aChild 为 null 抛 IllegalArgumentException；非 EJobTreeNode 返回 -1。

#### `public List<Job.Inform> getChangeJobs()`
- 行号: L118
- 描述: **`[重要]`** 返回所有处于排队或运行状态（isChangeJobQueuedOrRunning）的 Change 作业列表。

#### `private void updateJobs(List<Job.Inform> newJobs)`
- 行号: L131
- 描述: **`[重要]`** 差分同步（要求 EDT 线程）：先找出旧列表中已消失的 job 并 fireTreeNodesRemoved；再把新出现的 job 插入对应位置并 fireTreeNodesInserted；最后对 toString 变化的节点更新信息并 fireTreeNodesChanged。结束时断言新旧数量一致。

#### `void indicesClear()` / `void indicesAdd(int index)`
- 行号: L197 / L199
- 描述: 变更索引缓冲的清理与追加（容量不足时翻倍扩容）。

#### 内部私有类 JobMenuActionListener（`implements ActionListener`）
- `public void actionPerformed(ActionEvent e)` — 行号: L214。处理菜单命令：Get Info 打印 job 信息；Abort 调用 `jobInfo.abort()`；Delete 调用 `jobInfo.remove()`，运行中的 job 删除失败时打印提示。

#### 内部静态类 EJobTreeNode（`implements TreeNode`）
- `EJobTreeNode(Job.Inform jobInfo)` — 行号: L234。构造叶子节点，持有 Job.Inform。
- `private void setInfo(Job.Inform jobInfo)` — 行号: L239。更新 Job.Inform（断言 key 相同）。
- `public TreeNode getChildAt(int childIndex)` — 行号: L248。无子节点，抛 ArrayIndexOutOfBoundsException。
- `public int getChildCount()` — 行号: L256。恒返回 0。
- `public TreeNode getParent()` — 行号: L263。抛 UnsupportedOperationException。
- `public int getIndex(TreeNode node)` — 行号: L272。node 为 null 抛 IllegalArgumentException，否则返回 -1。
- `public boolean getAllowsChildren()` / `public boolean isLeaf()` — 行号: L281 / L286。恒返回 false / true。
- `public Enumeration children()` — 行号: L291。返回空枚举。
- `public String toString()` — 行号: L295。返回 jobInfo.toString()。
- `public Job.Key getKey()` / `public String getInfo()` — 行号: L297 / L299。纯 getter，返回 job 的 Key 与信息字符串。

---

## ExplorerTree

**文件**: `com/sun/electric/tool/user/ui/ExplorerTree.java`（3445 行）
**职责**: 显示数据库的 cell 资源管理器树视图。以 ExplorerTreeModel 为模型呈现 LIBRARIES（库/cell 组/cell/版本/多页）、ERRORS 与 JOBS 分支；支持单元拖放（跨库复制）、按选中对象类型动态构建右键菜单、双击打开 cell 窗口、CVS 项目管理、波形信号与 sweep 交互，并提供"查找 cell"对话框与展开状态保持机制。
**继承/实现**: `extends JTree implements DragSourceListener`
**关键字段**:
- `NULL_TREE_PATH_ARRAY (final static TreePath[])` — 空路径数组常量
- `handler (TreeHandler)` — 鼠标/选择事件统一处理器
- `rootNode (final String)` — 根节点名（取自 ExplorerTreeModel.rootNode）
- `currentSelectedPaths (TreePath [])` — 当前选中路径缓存
- `theCellSearchDialog (static FindCellDialog)` — FindCellDialog 单例
- `dropTarget (ExplorerTreeDropTarget)` — DnD 放置目标
- `dragOffset (Point)` — 拖动偏移量
- `dragImage (BufferedImage)` — 拖动影像
- `subCells (boolean)` — 是否连同子 cell 拖动
- `iconLibrary / iconLibraryNormal / iconLibraryChecked` 及 `iconGroup / iconJobs / iconLibraries / iconErrors / iconErrorMsg / iconWarnMsg / iconSignals / iconSweeps / iconMeasurements / iconViewMultiPageSchematics / iconSpiderWeb / iconLocked / iconUnlocked / iconAvailable (static ImageIcon)` — 各类型节点图标缓存（惰性加载）

### 方法

#### `ExplorerTree(List<MutableTreeNode> contentNodes)`
- 行号: L187
- 描述: **`[重要]`** 构造器：安装 ExplorerTreeModel，用传入的 contentNodes 构建内容树，初始化拖放（initDND），默认展开 JOBS 分支，选择模式设为 DISCONTIGUOUS_TREE_SELECTION，隐藏根节点、3 次点击切换展开、注册 ToolTipManager，安装自定义渲染器 MyRenderer，并挂接 TreeHandler 作为鼠标与选择监听器。

#### `public Object [] getCurrentlySelectedObject()`
- 行号: L222
- 描述: 返回所有当前选中对象组成的数组（逐个调用带索引的重载）。

#### `public int numCurrentlySelectedObjects()`
- 行号: L234
- 描述: 纯 getter，返回当前选中对象数量（currentSelectedPaths.length）。

#### `public Object getCurrentlySelectedObject(int i)`
- 行号: L241
- 描述: 返回第 i 个选中对象（越界返回 null）；对象取自树路径末端节点存储的 userObject。

#### `private Object getObjectInTreePath(TreePath treePath)`
- 行号: L253
- 描述: 工具方法：提取 TreePath 末端节点对象（DefaultMutableTreeNode 取 getUserObject，否则取节点本身）。

#### `public List<Library> getCurrentlySelectedLibraries()`
- 行号: L266
- 描述: 返回选中对象中所有 Library 组成的列表（无则空列表）。

#### `public List<Cell> getCurrentlySelectedCells()`
- 行号: L281
- 描述: **`[重要]`** 返回选中对象中的所有 Cell：CellAndCount 解包为 Cell，Cell.CellGroup 展开为组内全部 cell。

#### `public void clearCurrentlySelectedObjects()`
- 行号: L302
- 描述: 把当前选中路径缓存清空（设为空数组）。

#### `public ExplorerTreeModel model()`
- 行号: L311
- 描述: 纯 getter，把 treeModel 强转为 ExplorerTreeModel 返回。

#### `void openLibraryInExplorerTree(Library lib, Cell cell, boolean openLib)`
- 行号: L319
- 描述: 入口方法：从根路径开始递归展开指定库（openLib=true）或信号列表（openLib=false），可选展开到指定 cell 并选中对应行。

#### `private int countChildrenAndExpandInPath(Cell cell, TreeModel treeModel, TreePath path, Object node, int count)`
- 行号: L334
- 描述: 在库展开路径上逐层计数子节点并展开经过的 cell 组/版本节点，返回目标 cell 的行号。

#### `private boolean openLibraryInExplorerTree(Library library, Cell cell, TreePath path, boolean openLib, int count)`
- 行号: L373
- 描述: **`[重要]`** 递归实现：遇到目标 Library 即展开并（可选）展开到 cell、`setSelectionRow`；遇到 "libraries"/"signals" 字符串节点按 openLib 展开；随后递归子节点，找到目标返回 true。

#### `void redoContentTrees(List<MutableTreeNode> contentNodes)`
- 行号: L432
- 描述: **`[重要]`** 重建内容树（要求 EDT）：先用 KeepTreeExpansion 缓存展开状态，更新模型内容节点，再恢复展开。

#### 内部静态类 FindCellDialog extends EModelessDialog
- `private FindCellDialog(Frame parent)` — 行号: L459。构造 "Find Cells" 对话框：搜索输入框、Find 按钮（默认按钮）、结果 JList、Edit Selected Cell(s)/Edit All Cells/Done 按钮；双击结果行触发编辑。
- `public void init()` — 行号: L560。清空搜索框与结果列表并显示对话框。
- `protected void escapePressed()` — 行号: L568。按 Esc 关闭对话框。
- `private void findTextActionPerformed(ActionEvent evt)` — 行号: L570。**`[重要]`** 用忽略大小写+Unicode 的正则在所有可见库的 cell 名中搜索，匹配的 cell 描述加入列表，按有无结果启用编辑按钮。
- `private void editCell(boolean all)` — 行号: L599。编辑所有结果（all=true）或仅列表选中项，逐项调用 editACell。
- `private void editACell(String cellName)` — 行号: L615。通过 Cell.findNodeProto 定位 cell；若已在某 EditWindow/TextWindow 中则前置该窗口，否则 WindowFrame.createEditWindow 新建。

#### 内部静态类 KeepTreeExpansion
- `public KeepTreeExpansion(JTree tt, Object rn, TreeModel tm, TreePath tp)` — 行号: L656。构造时从 tp 出发递归缓存所有展开路径。
- `private void recursivelyCacheExpandedPaths(TreePath path)` — 行号: L667。递归收集已展开路径（跳过叶子）。
- `public void restore()` — 行号: L684。重建后逐一恢复缓存的展开路径。
- `private void expandCachedPath(TreePath oldPath)` — 行号: L689。在新树中按 userObject 或原索引重新定位每个节点并逐级 expandPath，找不到对应节点则放弃该路径。
- `private Object findChildByUserObject(Object parent, Object userObject)` — 行号: L713。在 parent 子节点中按 userObject 相等查找对应新节点。

#### `public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)`
- 行号: L727
- 描述: **`[重要]`** JTree 覆写：把节点转换为显示文本——Cell 显示 `cell.noLibDescribe()`（多页主原理图带 "**" 标记）；Library 显示库名（当前库加 " [Current]" 并切换图标）；CellGroup 显示组名；ErrorLogger 节点显示系统名（当前 logger 加 " [Current]"）；MessageLog 显示消息；Signal 显示信号名；null 显示空串。

#### `private void initDND()`
- 行号: L809
- 描述: **`[重要]`** 初始化拖放：安装 MyTransferHandler 包装原 TransferHandler、开启 dragEnabled，并为树注册 ExplorerTreeDropTarget（ACTION_LINK）。注释中保留了旧式 DragGestureRecognizer 方案供参考。

#### `public void setUI(TreeUI ui)`
- 行号: L842
- 描述: 覆写修复 bug：把 BasicDragGestureRecognizer 注册的鼠标/移动监听器从列表末尾移回正确顺序，解决"节点未选中时点击即拖动失败"的问题（Java 1.5 需要）。

#### 内部类 MyTransferHandler extends TransferHandler
- `MyTransferHandler(TransferHandler real, ExplorerTree tree)` — 行号: L879。构造器：包装真实 handler 并保存树引用。
- `protected Transferable createTransferable(JComponent c)` — 行号: L882。**`[重要]`** 创建拖动数据：选中为 Signal 时生成波形面板可识别的文本；cell/组一次只能拖一个（多选报错）；用 EditWindow.NodeProtoTransferable 包装对象，并把渲染后的节点半透明绘制成 dragImage 作为拖动影像。
- `public boolean canImport(JComponent comp, DataFlavor[] transferFlavors)` — 行号: L955。委托 real。
- `public void exportAsDrag(JComponent comp, InputEvent e, int action)` — 行号: L957。委托 real。
- `protected void exportDone(JComponent source, Transferable data, int action)` — 行号: L959。空实现。
- `public void exportToClipboard(JComponent comp, Clipboard clip, int action)` — 行号: L961。委托 real。
- `public int getSourceActions(JComponent c)` — 行号: L963。委托 real。
- `public Icon getVisualRepresentation(Transferable t)` — 行号: L965。委托 real。
- `public boolean importData(JComponent comp, Transferable t)` — 行号: L967。委托 real。

#### DragSourceListener 接口方法 `public void dragEnter(DragSourceDragEvent e)` / `dragOver` / `dragExit(DragSourceEvent e)` / `dragDropEnd(DragSourceDropEvent e)` / `dropActionChanged(DragSourceDragEvent e)`
- 行号: L1052 / L1055 / L1058 / L1061 / L1064
- 描述: 空实现（拖动源事件由 TransferHandler 处理）。

#### 内部私有静态类 ExplorerTreeDropTarget（`implements DropTargetListener`）
- `public void dragEnter(DropTargetDragEvent e)` — 行号: L1075。转发到 dragAction 判断是否接受。
- `public void dragOver(DropTargetDragEvent e)` — 行号: L1081。**`[重要]`** 拖动经过时：擦除旧影像、必要时绘制拖动影像、定位鼠标下 TreePath 的目标库（findLibrary），若为跨库拖动则用红框高亮目标位置并接受动作。
- `public void dropActionChanged(DropTargetDragEvent e)` — 行号: L1130。接受当前拖动动作。
- `public void dragExit(DropTargetEvent e)` — 行号: L1136。擦除绘制影像。
- `public void drop(DropTargetDropEvent dtde)` — 行号: L1142。**`[重要]`** 放下处理：接受 ACTION_LINK，取出被拖 cell，计算目标库，跨库时启动 `CrossLibCopy.CrossLibraryCopyJob`（携带 group/subCells 标志），成功 dropComplete(true)。
- `private Library findLibrary(TreePath cp)` — 行号: L1185。从树路径末端对象推断所属 Library（Library / CellGroup 内 cell / Cell 的库）。
- `private Object getDraggedObject(DataFlavor [] flavors)` — 行号: L1199。从 NodeProtoDataFlavor 中取出被拖动对象。
- `private ExplorerTree getOriginalTree(DataFlavor [] flavors)` — 行号: L1213。从 NodeProtoDataFlavor 中取出拖动来源树。
- `private void dragAction(DropTargetDragEvent e)` — 行号: L1226。存在有效被拖对象时接受拖动动作。
- `private void eraseDragImage(DropTarget dt)` — 行号: L1233。paintImmediately 擦除上次绘制的影像区域。

#### 内部类 MyRenderer extends DefaultTreeCellRenderer
- `public MyRenderer()` — 行号: L1248。构造器：创建 11 号 arial 常规/粗体字体。
- `public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)` — 行号: L1255。**`[重要]`** 渲染逻辑：Library 节点显示图标（改动过用粗体，CVS 启用时按状态着色）；Cell 节点按视图（ICON/LAYOUT/SCHEMATIC/DOC/UNKNOWN）选图标组，再按 Project 状态（NOTMANAGED/CHECKEDIN/CHECKEDOUTTOOTHERS/CHECKEDOUTTOYOU）叠加 old/available/locked/unlocked 图标，旧版本显示蜘蛛网图标，已修改用粗体；MultiPageCell 显示多页图标；CellGroup 组内任一 cell 修改则粗体；字符串节点（jobs/libraries/errors/signals/sweeps/measurements）显示对应图标；MessageLog 按 Warning/Error 显示图标。
- `private IconGroup findIconGroup(View view)` — 行号: L1395。按视图类型查找或构建 IconGroup（regular + old/available/locked/unlocked 四种叠加图标），结果缓存于 iconGroups 映射。
- `private ImageIcon buildIcon(ImageIcon fg, ImageIcon bg)` — 行号: L1424。像素级叠加前景/背景图标（前景非白色像素覆盖背景）生成新 ImageIcon。

#### 内部类 TreeHandler（`implements MouseListener, MouseMotionListener, TreeSelectionListener`）
- `TreeHandler(ExplorerTree t)` — 行号: L1468。构造器：保存树引用。
- `public void mouseClicked(MouseEvent e)` / `mouseEntered(MouseEvent e)` / `mouseExited(MouseEvent e)` / `mouseMoved(MouseEvent e)` — 行号: L1471 / L1474 / L1477 / L1480。空实现。
- `public void mousePressed(MouseEvent e)` — 行号: L1483。**`[重要]`** 鼠标按下：右键弹菜单（先选中点击项）；双击分发——Signal 加入波形窗口、SweepSignal 切换包含状态、Cell/CellAndCount/MultiPageCell 打开 cell 窗口（多页跳转页码）、Library/CellGroup/String/ErrorLoggerTreeNode 折叠/展开、EJobTreeNode 打印信息、MessageLog 定位并报告日志；双击 Cell 且为最新版本时记录原始路径准备拖动。
- `public void mouseReleased(MouseEvent e)` — 行号: L1632。释放时若为弹出菜单触发（右键）则选中并弹菜单。
- `public void mouseDragged(MouseEvent e)` — 行号: L1644。拖动 cell 时保持原始选择与当前选择并重绘（配合 DnD）。
- `public void valueChanged(TreeSelectionEvent e)` — 行号: L1657。**`[重要]`** 选择变化：滚动使首个选中可见、缓存 currentSelectedPaths；若本树属于波形窗口则触发 `crossProbeWaveformToEditWindow` 交叉探测。
- `private void selectTreeElement(int x, int y)` — 行号: L1695。右键时若点击处元素未在选中集合中，则改为单选该元素。
- `private void cacheEvent(MouseEvent e)` — 行号: L1717。缓存当前鼠标事件供右键菜单定位（其余历史代码已注释）。
- `private void addGetInfoMenu(JPopupMenu origMenu)` — 行号: L1755。向菜单追加 "Get Info" 项（绑定 showGetInfoGeneral）；origMenu 为 null 时新建菜单并在鼠标位置直接弹出。
- `private void addOpenCloseMenus(JPopupMenu menu)` — 行号: L1781。向菜单追加 Open / Open All Below Here / Close All Below Here / Close All at This Level 及分隔线。
- `private void doContextMenu()` — 行号: L1814。**`[重要]`** 右键菜单中枢：按选中对象类型构建对应菜单——SweepSignal（Include/Exclude/Highlight）、Signal（加入波形面板）、多选 Cell（删除/改视图/改组/CVS）、单 Cell（Edit、项目管理 Check Out/Check In/History、放置实例、新建/复制/删除/重命名/改视图/CVS 等）、MultiPageCell（编辑/新建页/删页）、Library（设为当前库、更新仓库、新建 cell、重命名/保存/关闭/重载）、CellGroup（新建 cell、删除组、复制组、重命名/复制组内 cell）、字符串节点（sweeps 全含/全排/去高亮、errors 删除全部/导入 logger、libraries 显示模式单选按钮/排序选项/搜索、TECHNOLOGY LAYERS/ARCS/NODES 的新建与重排原语）、ErrorLogger 节点（删除/导出/显示全部/设为当前）、MessageLog（显示全部）、EJobTreeNode（委托 JobTree.getPopupStatus）。
- `private void openAction()` — 行号: L2699。展开所有当前选中路径。
- `private void recursiveOpenAction()` — 行号: L2705。对所有选中路径递归展开。
- `private void recursivelyOpen(TreePath path)` — 行号: L2711。递归展开实现：expandPath 后逐子节点递归。
- `private void recursiveCloseAction()` — 行号: L2724。对所有选中路径递归折叠。
- `private void recursivelyClose(TreePath path)` — 行号: L2730。递归折叠实现：先递归子节点再 collapsePath。
- `private void closeParentTree()` — 行号: L2747。递归折叠最后一个选中路径的父路径（"Close All at This Level"）。
- `private void setCurLibAction()` — 行号: L2752。把选中库设为当前库并刷新相关界面。
- `private void renameLibraryAction()` — 行号: L2759。调用 CircuitChanges.renameLibrary 重命名选中库。
- `private void saveLibraryAction()` — 行号: L2765。调用 FileMenu.saveLibraryCommand 保存选中库。
- `private void saveLibraryAsAction()` — 行号: L2771。调用 FileMenu.saveLibraryCommand（saveAs=true）另存库。
- `private void closeLibraryAction()` — 行号: L2777。调用 FileMenu.closeLibraryCommand 关闭选中库。
- `private void reloadLibraryAction()` — 行号: L2783。启动 CircuitChangeJobs.ReloadLibraryJob 重载选中库。
- `private Cell getCellFromExplorerObject(Object obj)` — 行号: L2789。从 Cell 或 CellAndCount 对象中提取 Cell（否则 null）。
- `private void renameGroupAction()` — 行号: L2797。重命名 cell 组：组内 cell 名字不一时弹警告（新名作为前缀），输入新名后调用 CircuitChanges.renameCellGroupInJob。
- `private void duplicateGroupAction()` — 行号: L2865。复制整个 cell 组：取组中某 cell 弹 NewCellName(GROUPNAME) 对话框。
- `private void editCellAction(boolean newWindow)` — 行号: L2882。打开选中 cell：newWindow=true 时新建编辑窗口，否则用当前窗口；MultiPageCell 记录页码并在打开后 setMultiPageNumber。
- `private void editTextCellExternally()` — 行号: L2914。调用 ExternalEditing.editTextCellExternally 用外部文本编辑器打开选中文本 cell。
- `private void newCellInstanceAction()` — 行号: L2931。调用 PaletteFrame.placeInstance 放置选中 cell 的实例。
- `private void newCellAction()` — 行号: L2938。根据选中对象（Library/CellGroup/Cell/CellAndCount）推断目标库与默认名，弹出 NewCell 对话框。
- `private void addToWaveform(boolean newPanel)` — 行号: L2965。把选中 Signal 加入当前波形窗口（newPanel=true 时开新面板）。
- `private void setSweepAction(boolean include)` — 行号: L2979。设置所有选中 SweepSignal 的包含状态（最后一个触发更新）。
- `private void highlightSweepAction()` — 行号: L2991。高亮所有选中 SweepSignal。
- `private void setAllSweepsAction(boolean include)` — 行号: L3002。收集选中 sweep 容器节点下全部 SweepSignal，经波形窗口 setIncludeInAllSweeps 统一设置。
- `private void removeSweepHighlighting()` — 行号: L3029。清除波形窗口 sweep 高亮并重绘所有面板。
- `private void showGetInfoGeneral()` — 行号: L3046。向控制台打印选中节点的通用信息（子节点数、节点名等）。
- `private void newCellVersionAction()` — 行号: L3072。调用 CircuitChanges.newVersionOfCell 创建选中 cell 的新版本。
- `private void duplicateCellAction()` — 行号: L3078。弹 NewCellName(CELLNAMEDUPLICATE) 对话框复制选中 cell。
- `private void deleteCellGroupAction()` — 行号: L3084。**`[重要]`** 删除选中 cell/cell 组：区分组与 cell 两类目标，弹确认对话框（含目标清单），cell 走 CircuitChanges.deleteCell、组走 CellChangeJobs.DeleteCellGroup。
- `private void renameCellAction()` — 行号: L3128。弹输入框取新名，调用 CircuitChanges.renameCellInJob 重命名选中 cell。
- `private void prepareForCopyAction(JMenu subMenu, Object selectedObject)` — 行号: L3136。为 "Copy Cell/Group" 子菜单列出除自身所在库外的所有非隐藏库作为目标。
- `private void copyCellOrCellGroupAction(ActionEvent e)` — 行号: L3163。从菜单项文本解析目标库，收集当前选中路径上的 cell（组则取首 cell 并置 copyRelated），启动 CrossLibraryCopyJob。
- `private void reViewCellAction(ActionEvent e)` — 行号: L3190。从菜单文本解析目标 View，对所有选中 cell 调用 ViewChanges.changeCellView 切换视图。
- `private void changeCellGroupAction()` — 行号: L3210。收集选中 cell（要求同库），弹 ChangeCellGroup 对话框重新分组。
- `private void makeNewSchematicPage()` — 行号: L3232。对多页 cell 启动 SetMultiPageJob 增加一页并跳到新页（非多页 cell 打印提示）。
- `private void deleteSchematicPage()` — 行号: L3247。删除 MultiPageCell 对应页（DeleteMultiPageJob；仅剩一页时拒绝）。
- `private void searchAction()` — 行号: L3261。惰性创建 FindCellDialog 单例（MDI 模式带父 JFrame），初始化并置前显示。
- `private void addCVSMenu(JMenu cvsMenu)` — 行号: L3293。**`[重要]`** 构建 CVS 子菜单：根据选中库/cell 的 CVS 状态（State 集合）启用/禁用 Commit、Update、Get Status、List Editors、Show Log（单库或单 cell 时）、Rollback、Add to CVS、Remove from CVS（仅库）、Undo CVS Add or Remove、Rollforward（恒禁用）。
- `private void cvsUpdate(Update.UpdateEnum type)` — 行号: L3404。对选中库/cell 执行 CVS Update（UPDATE/STATUS/ROLLBACK/ROLLFORWARD）。
- `private void cvsListEditors()` — 行号: L3410。列出选中库/cell 的 CVS 编辑者。
- `private void showLog(int type)` — 行号: L3416。显示 CVS 日志：type=1 显示库日志，type=0 显示 cell 日志。
- `private void cvsCommit()` — 行号: L3427。提交选中库/cell 到 CVS。
- `private void cvsAddRemove(boolean add)` — 行号: L3433。对选中库/cell 执行 CVS 添加或移除。
- `private void cvsUndoAddRemove()` — 行号: L3439。撤销 CVS 添加/移除操作。

---

## ExplorerTreeModel

**文件**: `com/sun/electric/tool/user/ui/ExplorerTreeModel.java`（739 行）
**职责**: cell 资源管理器树视图的模型（DefaultTreeModel 子类）。维护内容节点列表，并在其后挂接 ERROR 与 JOBS 两个固定分支；提供全局（弱引用）监听器广播机制；支持字母序、按 cell 组、按层次三种库树构建模式及工艺库分类展示。
**继承/实现**: `extends DefaultTreeModel`
**关键字段**:
- `rootNode (public static final String)` — 根节点名 "Explorer"
- `allListeners (static final WeakReferences<TreeModelListener>)` — 所有树的 TreeModelListener 弱引用集合（用于静态广播事件）
- `contentExplorerNodes (final ArrayList<MutableTreeNode>)` — 根下内容节点列表
- `jobExplorerNode / errorExplorerNode (final DefaultMutableTreeNode)` — JOBS / ERRORS 分支节点（来自 JobTree / ErrorLoggerTree 单例）
- `rootPath / jobPath / errorPath (final Object[])` — 相应节点的对象路径
- `SHOWALPHABETICALLY / SHOWBYCELLGROUP / SHOWBYHIERARCHY (static final int)` — 三种库树显示模式常量
- `howToShow (static int)` — 当前显示模式（默认 SHOWBYCELLGROUP）
- `libraryNode (static String)` — 库树根节点名 "LIBRARIES"

### 方法

#### 内部静态类 CellAndCount / MultiPageCell
- `CellAndCount(Cell cell, int count)` — 行号: L76。构造 cell+实例计数包装（层次显示用）。
- `Cell getCell()` / `int getCount()` — 行号: L77 / L78。纯 getter。
- `public String toString()` — 行号: L80。返回 "cell名 (计数)"。
- `Cell getCell()` / `int getPageNo()` — 行号: L87 / L88。MultiPageCell 的 getter。
- `public String toString()` — 行号: L90。返回 "cell名 Page (页码+1)"。

#### `ExplorerTreeModel()`
- 行号: L93
- 描述: 包私有构造器：取 JobTree/ErrorLoggerTree 单例节点，构造 root/job/error 三条对象路径。

#### `public Object getRoot()`
- 行号: L108
- 描述: 覆写：返回根节点字符串 rootNode。

#### `public Object getChild(Object parent, int index)`
- 行号: L122
- 描述: 覆写：parent 为根时，index 末尾两位分别返回 errorExplorerNode 与 jobExplorerNode，其余返回内容节点；其他情况委托父类。

#### `public int getChildCount(Object parent)`
- 行号: L140
- 描述: 覆写：根节点子数为内容节点数 + 2（ERRORS、JOBS）。

#### `public boolean isLeaf(Object node)`
- 行号: L156
- 描述: 覆写：根节点恒非叶子。

#### `public void valueForPathChanged(TreePath path, Object newValue)`
- 行号: L170
- 描述: 委托父类处理节点值编辑。

#### `public int getIndexOfChild(Object parent, Object child)`
- 行号: L184
- 描述: 覆写：根节点下 error/job 节点返回特殊索引，其余在 contentExplorerNodes 中线性查找；child 为 null 抛 IllegalArgumentException。

#### `public void addTreeModelListener(TreeModelListener l)` / `public void removeTreeModelListener(TreeModelListener l)`
- 行号: L206 / L219
- 描述: 注册/注销监听器：除父类集合外同步维护全局弱引用集合 allListeners。

#### `static void fireTreeNodesChanged(Object source, TreePath treePath, int[] childIndices, Object[] children)` / `fireTreeNodesInserted(...)` / `fireTreeNodesRemoved(...)` / `fireTreeStructureChanged(Object source, TreePath treePath)`
- 行号: L232 / L247 / L262 / L275
- 描述: 静态事件广播：构造 TreeModelEvent 并逆序遍历 allListeners 分发给所有模型监听器（changed/inserted/removed/structureChanged）。

#### `public void reload(Object path[])`
- 行号: L288
- 描述: 触发结构变化事件（path 非空时）。

#### `public void nodesWereInserted(Object[] path, int[] childIndices)`
- 行号: L303
- 描述: 插入后通知：按索引取新子节点并广播 fireTreeNodesInserted。

#### `public void nodesWereRemoved(Object path[], int[] childIndices, Object[] removedChildren)`
- 行号: L321
- 描述: 移除后广播 fireTreeNodesRemoved。

#### `public void nodesChanged(Object[] path, int[] childIndices)`
- 行号: L330
- 描述: 节点变化通知：按索引收集子节点广播 fireTreeNodesChanged；childIndices 为 null 且节点为根时广播整根变化。

#### `public TreeNode[] getPathToRoot(TreeNode aNode)`
- 行号: L358
- 描述: 委托父类构建到根路径。

#### `protected TreeNode[] getPathToRoot(TreeNode aNode, int depth)`
- 行号: L375
- 描述: 覆写：递归构建到根节点路径（处理 null 节点与直接挂在根下的节点）。

#### `void updateContentExplorerNodes(List<MutableTreeNode> newContentExplorerNodes)`
- 行号: L404
- 描述: **`[重要]`** 整体替换内容节点：先广播移除全部旧节点，清空后加入新节点并广播插入。

#### `static boolean isShownAlphabetically()` / `static void showAlphabeticallyAction()`
- 行号: L422 / L427
- 描述: 查询/设置显示模式为字母序；设置后调用 WindowFrame.wantToRedoLibraryTree() 请求重建库树。

#### `static boolean isShownByGroup()` / `static void showByGroupAction()`
- 行号: L433 / L438
- 描述: 查询/设置显示模式为按 cell 组。

#### `static boolean isShownByHierarchy()` / `static void showByHierarchyAction()`
- 行号: L444 / L449
- 描述: 查询/设置显示模式为按层次。

#### `static boolean getSortLexically()`
- 行号: L455
- 描述: 返回 User.prefSortLexically 偏好值。

#### `static void setSortLexically(ActionEvent e)`
- 行号: L460
- 描述: 从 JCheckBoxMenuItem 当前状态取反设置 prefSortLexically，并请求重建库树。

#### `public static DefaultMutableTreeNode makeLibraryTree()`
- 行号: L467
- 描述: **`[重要]`** 创建 "LIBRARIES" 根节点并按当前 howToShow 模式调用对应的 rebuild 方法构建整棵树。

#### `private static synchronized void rebuildExplorerTreeByName(DefaultMutableTreeNode libraryExplorerTree)`
- 行号: L487
- 描述: **`[重要]`** 按库→cell 平面列表构建：遍历可见库，工艺库走 addTechnologyLibraryToTree，普通库按名称（可选词法排序）把每个 cell 直接挂为库的子节点。

#### `private static boolean addTechnologyLibraryToTree(Library lib, DefaultMutableTreeNode libTree)`
- 行号: L509
- 描述: **`[重要]`** 若库含工艺库 cell，则挂入 TECHNOLOGY LAYERS / TECHNOLOGY ARCS / TECHNOLOGY NODES / TECHNOLOGY SUPPORT 四个分类节点，cell 按 LayerInfo/ArcInfo/NodeInfo 归类，剩余进 SUPPORT；返回是否工艺库。

#### `private static synchronized void rebuildExplorerTreeByHierarchy(DefaultMutableTreeNode libraryExplorerTree)`
- 行号: L560
- 描述: **`[重要]`** 按层次构建：跳过 icon 与文本视图；对每个版本中没有被实例化（getInstancesOf 为空）的 cell 作为顶层节点，递归调用 createHierarchicalExplorerTree 展开其内容。

#### `private static void createHierarchicalExplorerTree(Cell cell, DefaultMutableTreeNode cellTree)`
- 行号: L601
- 描述: **`[重要]`** 递归构建层次：统计 cell 内各子 cell 实例数（icon 实例映射到 contentsView），按名称排序后为每个子 cell 创建 CellAndCount 节点并继续递归。

#### `private static synchronized void rebuildExplorerTreeByGroups(DefaultMutableTreeNode libraryExplorerTree)`
- 行号: L641
- 描述: **`[重要]`** 按 cell 组构建：遍历可见库，最新版本 cell 按组聚合——组内仅一个新 cell 时直接挂库下，否则创建组节点并把组内 cell（按视图排序）挂入；每个 cell 经 addCellAndAllVersions 附加多页与旧版本子节点。

#### `private static void addCellAndAllVersions(Cell cell, DefaultMutableTreeNode libTree)`
- 行号: L699
- 描述: 添加 cell 节点及其全部内容：多页 cell 附加每个 MultiPageCell 页节点；多版本 cell 附加各旧版本节点（旧版本若多页同样展开页节点）。

---

## Util

**文件**: `com/sun/electric/tool/user/ui/Util.java`（85 行）
**职责**: 用户界面的工具函数。当前仅提供 AWT 缺失的 HSV 与 RGB 颜色空间相互转换。
**继承/实现**: （无，普通 final 类结构）

### 方法

#### `public static Color colorFromHSV(float h, float s, float v)`
- 行号: L36
- 描述: 把 HSV（h 为 0-1 圈数）颜色转换为 java.awt.Color。

#### `public static float hueFromColor(Color c)`
- 行号: L52
- 描述: 从 Color 中提取色相 hue（归一化到 0-1）；无饱和度或亮度为 0 时返回 0。
