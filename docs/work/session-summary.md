# 会话工作总结（2026-10-24）

> 本文件记录本次会话在 Electric VLSI Design System 9.08.1 项目上完成的全部工作，供后续会话/开发者快速了解上下文。

## 一、项目搭建

- 从 `electric-9.08.1.jar` 解压全部源码（1610 个 .java）到项目根：`com/`（源码+资源）、`org/`（slf4j 类）、`META-INF/MANIFEST.MF`、`build.xml`（官方 Ant 构建脚本，默认 target `jar`，javac release=17）
- 配置 IDEA：JDK 17、源码根、运行配置（主类 `com.sun.electric.Electric`，程序参数 `-NOMINMEM`，VM 参数 `--add-opens java.desktop/sun.awt=ALL-UNNAMED -Xmx2g`）
- 补充唯一缺失依赖：JOGL Java3D（`lib/java3d-core-1.7.2.jar`、`java3d-utils-1.7.2.jar`、`vecmath-1.7.2.jar`，来自 Maven Central）
- **构建流程**（Ant 未安装，用等价命令）：
  ```
  javac -J-Duser.language=en -g -encoding UTF-8 --release 17 -cp ".;lib/*" -d target/classes @/tmp/sources.txt
  find com -type f \(资源扩展名清单 \) -exec cp --parents '{}' target/classes/ \;
  cp -r org target/classes/org && cp README.txt COPYING.txt ChangeLog.txt ElectricIcon64x64.png target/classes/
  jar cfm target/electric-9.08.1.jar META-INF/MANIFEST.MF -C target/classes .
  ```
  注意：**打包前必须关闭正在运行的 Electric 实例**（Windows 文件锁）；增量编译单文件不可靠，必须全量编译。
- **git 版本控制**：已 `git init`，`.gitignore` 排除 `target/`、`*.log`、原 jar、编辑器状态。共 12 个提交，最新 `8dd88af`。
- 生成 GUI 模块文档：`docs/`（README 总索引 + gui-01~gui-11 分节，含方法级描述与行号）

## 二、功能开发（用户 GUI 需求）

### 1. Explorer 树功能
- 右键单个 cell → **"Rename All Views of Cell..."**：整组视图（{sch}/{lay}/{ic}）一起重命名（复用 `CellChangeJobs.RenameCellGroup`，并补上 `fixStaleCellReferences` 修复既有缺口）
- **New Cell 对话框多视图创建**：view 列表带复选框，一次创建多个同名视图（自动归入同一 CellGroup）

### 2. JLCEDA 式原理图布线（核心需求）
- **端子标记**：未连接端子=黄点（8px），已连接=红点；覆盖单元实例 + 原始元件（CN 晶体管等）+ 带名引脚（接地/电源标签）+ 悬空线端；排除布线基础设施（PIN/CONNECT/CONTACT）与 ART 节点（**Facet-Center**——曾导致中央幽灵黄点）
- **点击黄点起线**：点击-预览-点击定线（弧线预览跟随鼠标 + H/V 十字虚线），ESC/右键/再点起点=取消；仅在黄点上触发（元件主体点击=正常选择）
- **W 快捷键连线模式**：W 进入/退出（通过菜单 KeyStroke 加速键注册），十字光标 + 定位虚线；点击端子/导线（分叉）/空白（临时 pin）起线
- **DEL 键**：连线模式下删除与起点相连的导线段并退出
- **双击 GetInfo 兼容**：双击端子先取消连线再走原生 GetInfo 弹窗
- **Create Export**：右键快速点击悬空线端弹出菜单（右键按住拖动仍是原生连线）

### 3. 自动连接（拖拽/放置/粘贴）
- 移动元件使端子与端子/导线接触 → 自动连接；接触点绿色十字提示
- 端子-导线中部接触 → **拆线**生成原生 Wire_Pin 连接（**pin 必须用默认尺寸创建，宽高 0 会导致圆盘半径 0 不可见** —— 关键坑）
- 放置（palette）/粘贴（复制）到导线上同样自动连接
- **安全规则**：已连接端子永不自动连接；移动导线（arc）不触发自动连接；wire pin/连接节点不参与自动连接
- 端口-端口跨工艺（CN 端口→原理图导线）：`SimpleWirer.planRoute` + `Router.createRouteNoJob` 带接触链路由

### 4. 渲染
- 原理图导线加粗 5px（`PixelDrawing.drawThickLine5`，仅渲染不改数据库）
- Ctrl+G 网格改为**半透明线网格**（所有窗口；网格先于内容绘制，导线在上层）
- 高亮统一**绿色**（启动时强制设置）+ 高亮位置**绿色十字**标记（`Highlight.getHighlightCenter()` 新 API）
- 放置/粘贴幽灵显示元件轮廓 + 黄色端子（dummy 实例预览机制）
- 蓝点已按要求全部移除；交叉点显示 Electric 原生圆盘（导线同色、随缩放变大）

## 三、关键技术坑（后续修改务必注意）

1. **Job 序列化**：Electric 的 Job 会序列化传给工作线程——Job 字段不能含 `EditWindow` 等 Swing 对象（曾导致 `EJob.deserializeToServer` NPE）；窗口用 `EditWindow.getCurrent()` 在 terminateOK 获取
2. **mouseReleased 会重置 modeLeft**：连线模式是"按下驱动"的，释放事件必须提前 return，否则模式被普通释放流程末尾的 `modeLeft = Mode.none` 清掉
3. **连接点 pin 尺寸**：`NodeInst.makeInstance(pinProto, pt, 0, 0, cell)` 的宽高 0 会让原生圆盘半径归零不可见，必须传 `getDefWidth/Height(ep)`
4. **faceless 原理图坐标**：覆盖层统一用 `wnd.databaseToScreen` / `ScreenPoint`；`getTrueCenter()` 返回 Point2D 而非 EPoint
5. **CN 工艺**：晶体管是 PrimitiveNode 不是 CellInstance；`isSchematic()` 按视图判定（跨工艺安全）；原理图导线/引脚始终是 Schematics 工艺原型
6. **Perl/sed 修改 Java**：`s{}{}` 替换串含花括号需换分隔符；替换串需 `/e` 修饰符才按表达式求值；行号锚定要警惕已有编辑的偏移

## 四、产物与运行

- 运行：双击 `target/run-electric.bat`（`java -jar target/electric-9.08.1.jar`）
- 冒烟测试模式：`timeout 20 java -jar target/electric-9.08.1.jar -NOMINMEM`（exit 124 = 健康运行到超时）
- 用户日志位置：`target/electric.log`（bat 从 target 启动时）或启动目录

## 五、Git 提交历史（12 个）

```
8dd88af Restore cursor when wiring mode exits through any path
f931e75 Fix invisible auto-connect junction pins (zero-size pin creation)
12b4daa Remove blue pin dots, fix phantom yellow dot at cell center
6c3baba Fix W wiring mode state loss, temp pin cleanup, junction dot sizing
62d2940 Wire pins render as blue dots, W binding fixed, DEL deletes wire end
c8828b2 Fix auto-connect safety rules and paste preview, W key, wire pins
5e57ca5 Add placement/paste auto-connect, W wire mode, placement preview, and polish
594c790 Support primitive components (e.g. mocmos-cn transistors) in wiring features
4a1bcbc Complete terminal-marking and connection features per user requirements
01f8ae8 Rework click-click wiring to start only from yellow dots
69cd438 Fix click-click wiring: job serialization NPE and degenerate-route freeze
e38a8f6 Initial commit: Electric VLSI 9.08.1 sources with GUI enhancements
```

## 六、涉及的主要文件

| 文件 | 改动 |
|---|---|
| `com/sun/electric/tool/user/ui/ClickZoomWireListener.java` | 连线状态机、W/DEL 键、右键弹窗、拖拽接触检测（改动最大） |
| `com/sun/electric/tool/user/redisplay/PixelDrawing.java` | 覆盖层（黄/红点、十字虚线、接触点、放置预览）、线宽、线网格、绿色十字 |
| `com/sun/electric/tool/user/redisplay/LayerDrawing.java` | 线网格 + 半透明合成 |
| `com/sun/electric/tool/user/CircuitChangeJobs.java` | 自动连接（拆线、跨工艺路由）、安全规则 |
| `com/sun/electric/tool/user/ui/PaletteFrame.java` / `Clipboard.java` | 放置/粘贴自动连接与预览 |
| `com/sun/electric/tool/user/Highlight.java` | getHighlightCenter API |
| `com/sun/electric/tool/user/UserInterfaceMain.java` | 启动强制绿色高亮 |
| `com/sun/electric/tool/user/dialogs/NewCell.java` | 多视图复选框创建 |
| `com/sun/electric/tool/user/ui/ExplorerTree.java` | 批量重命名菜单 |
| `com/sun/electric/tool/user/ui/WindowFrame.java` / `EditWindow.java` / `ToolBar.java` / `menus/WindowMenu.java` | W 模式入口、光标恢复 |
| `com/sun/electric/tool/drc/Schematic.java` | isDanglingPin 抽取 |
