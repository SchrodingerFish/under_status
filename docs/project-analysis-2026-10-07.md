# UnderStatus 全项目优化分析

分析日期：2026-10-07。基线：`7c9d008`，加上工作区原有的 `pom.xml` 产物名修改（`understatus` → `under_status`）。

总体判断：项目已经有可复用的核心逻辑、天气数据模型、懒加载和测试基础，适合渐进改进。当前最值得投入的是构建兼容性、算法正确性、界面响应和资源生命周期；继续微调字符串拼接的收益明显低于修复这些问题。

## 范围与验证结果

- 盘点全部 129 个 Git 跟踪文件：92 个生产 Java 文件（13,845 行）、23 个测试 Java 文件（1,394 行），以及 14 个资源、配置、文档等文件。
- 覆盖状态栏、工具箱、便签、设置、天气、音乐、番茄钟、闹钟、构建和 CI；采用全仓结构扫描、关键路径逐行检查、现有测试及针对性复现。未声称逐行审阅所有界面布局代码。
- 没有发现提交进仓库的 JAR、CLASS、NBM 或成片嵌入的第三方源码；`target` 已被忽略。主要运行依赖是 NetBeans APIs 和 JLayer 1.0.1。
- Windows / JDK 25.0.3：`mvn --batch-mode --no-transfer-progress verify` 执行 78 个测试，全部通过；随后 Checkstyle 因两处星号导入失败。
- Windows / JDK 17：同一命令执行 78 个测试，出现 5 个错误，根因是 Java 21 字节码不能在 Java 17 加载。此次使用已有编译产物，但运行期错误本身已足以否定当前 Java 17 兼容性。
- 独立、限时、限堆 JVM 复现了 Cron、SQL、XML、Diff 的问题，并测得正则退化样例单次计算约 1.04 秒。这是问题复现数据，不是通用性能基准。
- 未启动完整 NetBeans 做交互回归，未做跨系统视觉验证、长时间内存测量或 CVE 数据库审计。音乐乱序与窗口生命周期结论基于代码路径；便签后端容量和 UI 视觉问题标为待集成验证。
- 分析没有修改生产代码、构建配置或原有测试。临时复现程序位于已忽略的 `target/audit/`。

## 模块评估

| 模块 | 现状与值得保留的设计 | 优化方向 | 处置建议 |
| --- | --- | --- | --- |
| 状态栏、窗口及 UI | 根包 37 个类；已有 EditorMetricsTracker、LatestTask 和按值更新内存条 | 把配置、调度和资源所有权从大窗口类移入控制器；规范挂载/卸载 | 提取与整合 |
| 天气 | `weather` 27 个类；类型化模型、HTTP 接口、TTL、重试、过期回退、独立标签状态 | 请求去重、任务代次、缓存上限、类型化视图结果 | 核心资产，保留 |
| 工具箱 | `toolbox` 11 个类；算法已部分脱离 Swing，16 个页签懒加载 | 局部重做 Cron、SQL 和 Diff 的边界策略；保留 UI 与简单纯函数 | 问题算法局部重建 |
| 音乐 | `music` 5 个类；播放器、曲目和歌词已有独立类 | 共享播放会话、异步结果校验、真正暂停、可注入 HTTP/解码器 | 提取与整合 |
| 设置与便签 | `settings` 6 个类；SettingsStore/SecretStore 可替换，密钥进入 Keyring | 拆分配置与内容存储、迁移版本、错误提示、减少全量读写 | 提取与整合 |
| 番茄钟与闹钟 | 3 个核心类；业务规则已独立 | 时间源注入、计时漂移、休眠恢复策略 | 核心资产，保留 |
| 构建与仓库 | Maven 插件版本固定，有 Enforcer、Checkstyle 和 CI | 统一 Java 基线、产物路径、离线单测与发布凭据管理 | 整合；私钥退出源码管理 |

## P1：优先修复

P1 表示影响构建、结果正确性、IDE 响应或发布凭据的问题；同级内部建议按下面顺序实施。P2 是随后安排的可靠性与维护改进。

### 01. Java 基线与依赖、CI 不一致【实测】

位置：`pom.xml:50`、`pom.xml:53`、`pom.xml:276`、`.github/workflows/verify.yml:14`。

POM 声明 `release=17`、Enforcer 接受 Java 17，CI 也用 Java 17。但当前解析到的 RELEASE300 依赖中，`org.openide.util.NbPreferences` 和 `RequestProcessor` 的 class version 为 65（Java 21）。JDK 17 测试报 `UnsupportedClassVersionError`，并使 SettingsRepository/Keyring 初始化失败。`--release 17` 不会把依赖降级成 Java 17。

建议：既然目标是 NetBeans 30，优先统一采用与该平台匹配的 Java 基线，并至少满足当前依赖所需的 Java 21；同步 compiler、Enforcer、CI、README。若必须支持 Java 17，应选用兼容的平台依赖并验证完整组合。增加依赖字节码版本检查，使用干净环境构建。Java 21 的完整 IDE 运行还需独立验收。

### 02. 完整验证失败，CI 产物路径也不匹配【实测＋静态确认】

位置：`DiffTabPanel.java:4`、`DiffTabPanel.java:8`、`pom.xml:153`、`.github/workflows/verify.yml:21`、`README_CN.md:118`。

JDK 25 下 78 个测试通过，Checkstyle 因 `javax.swing.*` 与 `java.awt.*` 拒绝构建。当前工作区生成 `target/under_status.nbm`（并有 `target/nbm/under_status.nbm`），CI 上传仍指向 `target/understatus.nbm`；README 仍写旧名。构建目录还残留旧名文件，因此不能用“磁盘上存在该文件”判断发布链路正确。

建议：改显式导入；基于选定的 finalName 统一 CI 与 README；上传设置 `if-no-files-found: error`；以干净构建验证安装包内容及路径。此处产物名差异包含用户已有未提交修改，分析未覆盖或撤销它。

### 03. Cron 存在无限循环和错误的执行日期【实测】

位置：`toolbox/core/CronExplainer.java:81`、`:104`、`:133`、`:175`；`CronTabPanel.java:127`。

- `*/0 * * * * ?`：`step=0` 使 `for (v += step)` 永不前进；独立进程 3 秒后被测试程序终止。界面直接同步调用这一算法，会冻结 IDE 的事件线程。
- `0 0 0 1 1 ? 2099`：实际返回 `2027-01-01T00:00`，第七个年份字段未参与计算。
- `0 0 0 ? * MON`：结果包含周一和周二，因为同时接受 Quartz、Linux、Java 的星期数值。

建议：首先校验步长 > 0、范围和字段组合；明确选择 Unix 5 字段与 Quartz 6/7 字段语义，分别处理星期、年份、日/月/周规则。可评估成熟 Cron 库；若维持自研，应独立解析成类型化模型。通过注入 Clock 固定测试日期，加入上述回归样例和不支持语法的明确错误。

### 04. 正则“500 次保护”没有限制实际计算【实测＋静态确认】

位置：`RegexTabPanel.java:74`、`:255`、`:259`、`:281`。

Swing Timer 只延迟调用，`runRegex()` 仍在 EDT。超过 500 次后只是停止输出分组，高亮之外的 `matcher.find()` 循环仍持续。单次 `find()` 的灾难性回溯也发生在计数之前，替换还会再次完整匹配。

独立 JVM 中 `(a+)+$` 对 10,000 个 `a` 加 `!` 单次约 1,036ms；真实界面还有文本获取和渲染成本。

建议：限制输入长度与展示结果，后台计算，携带请求代次后回到 EDT 更新；为用户可输入的 Java 正则提供可真正终止的隔离任务，或评估线性时间引擎并明确语法差异。仅用 Future 超时/线程 interrupt 不能保证 Java Matcher 停止。

### 05. Diff 最坏情况仍需平方级内存，并阻塞 EDT【实测】

位置：`toolbox/core/DiffCalculator.java:64`、`DiffTabPanel.java:169`。

前后缀剪枝对局部小改动有效，但不同文本仍创建 `(n+1)×(m+1)` 的 `int[][]`。两侧 10,000 行基本不同的文本，DP 元素本身约 400MB（约 381MiB），尚未包含数组头和文本；在 `-Xmx64m` 的隔离 JVM 中复现 `OutOfMemoryError`。比较和两个视图的渲染都在按钮事件中同步执行。

建议：优先增加矩阵单元格预算和超限降级，计算移出 EDT；评估 Myers/线性空间算法及差异极大的输入退路；按当前可见视图渲染，避免每次同时填充三份 StyledDocument。验收应覆盖完全不同文本、局部修改、长单行、空文件，并单独观察 UI 延迟和峰值内存。

### 06. SQL / XML 格式化会改变内容语义【实测】

位置：`toolbox/core/SqlFormatter.java:22`、`:53`、`toolbox/core/XmlFormatter.java:60`。

- SQL `select 'from  x' as s from t` 被格式化为字符串内部出现换行、关键字变大写、双空格丢失的结果。
- SQL `select 1 -- keep comment\nfrom t` 压缩后成为 `select 1 -- keep comment from t`，FROM 子句进入注释。
- XML `<p><b>A</b> <b>B</b></p>` 压缩后丢失中间空格，混合内容的文本变为 `AB`。

建议：SQL 使用能区分字面量、带引号标识符、行/块注释的 tokenizer，明确支持的方言；XML 尊重混合内容和 `xml:space`，不要无条件删除节点之间的空白。格式化失败保留原输入并独立展示错误。测试应验证内容语义和解析结果，而不只验证输出中有大写 SELECT。

### 07. 音乐异步结果会覆盖后一次选歌【静态确认】

位置：`MusicTabPanel.java:585`、`:601`、`:609`、`:689`；`music/MusicAudioPlayer.java:93`。

先选 A 再选 B，如果 A 的 URL 后返回，仍会执行 `audioPlayer.play(A, ...)`；封面请求也无当前曲目校验。播放器已有 generation 检查，但 generation 在 `play()` 内递增，不能识别更早发起、稍后才解析完成的 A 请求。歌词比 URL/封面多一层曲目判断，但仍以实际播放器曲目而非最新用户选择为准。

建议：在用户选歌时就创建会话代次，URL、歌词、封面、错误提示统一校验；新选择使旧任务失效，并尽可能取消网络工作。用可控 Future 模拟 A 慢 B 快，验证最终声音、标题、封面、歌词属于同一首歌。

### 08. 音乐窗口没有完整的资源释放和共享会话【静态确认】

位置：`QuickNoteCalcDialog.java:100`、`MusicPlayerDialog.java:34`、`:43`、`MusicTabPanel.java:75`、`music/MusicAudioPlayer.java:295`。

工具箱与独立窗口分别创建 MusicTabPanel，每个面板拥有自己的播放器。`MusicAudioPlayer.close()` 已实现，但没有看到窗口/面板调用；工具箱失焦或 Esc 销毁窗口后，播放、Timer 和监听器仍可保留旧面板。独立窗口 Esc 后再次打开会创建新播放器，可能与仍在播放的旧实例并存。两个面板还各自持有收藏快照，保存时可能互相覆盖。

建议：引入一个具有明确所有者的 MusicSession，由两个界面订阅；如果关闭后允许继续播放，应复用并保留可控制的同一会话；如果关闭即停止，则在 dispose 时关闭播放器和任务。二者都需要模块退出时的统一 close。验收反复打开/关闭、切换两个入口后，播放器/定时器/监听器数量不应增长。

### 09. Git 跟踪了包含私钥的 JKS【文件结构确认】

位置：仓库根目录 `keystore`。

只检查了文件结构：JKS 格式、1 个条目、首条目类型为 private key。未读取或展示密钥内容，未尝试口令，也未判断是否仍用于正式发布。

建议：核实用途；若是仍有效的发布签名私钥且仓库已共享，应轮换并迁入仓库外的安全存储，由发布 CI 注入。停止继续跟踪该文件并配置忽略；简单删除工作区文件不会移除 Git 历史。历史清理和密钥轮换需结合现有安装用户的升级/签名策略制定，此次未操作。

## P2：随后安排的可靠性与维护改进

### 10. 天气请求需要代次校验、缓存边界与刷新语义

位置：`WeatherDetailDialog.java:430`、`:469`、`:532`、`weather/WeatherDataService.java:106`、`weather/WeatherCache.java:13`、`QWeatherService.java:37`。

- 缓存使用 ConcurrentHashMap，但“查询 → 加载 → 写入”没有按键合并在途请求；状态栏、多个详情窗口可能重复请求相同资源。
- 详情窗口依赖 key 隔离状态提示，但同一图表/输出控件仍可被较晚返回的旧范围请求覆盖；后台 lambda 还读取 `range.getSelectedIndex()`。应在 EDT 先捕获不可变参数，再校验请求代次。
- 手动刷新先 `clearLocation` 清掉该地点所有缓存，网络失败时失去本来可用的旧数据；按钮 1.2 秒后恢复，而请求/重试可能尚未结束。
- 缓存只标记过期不淘汰，按日期的新 key 会累积；建议条目数上限和过期数据保留期限。常用天气数据量不大，此项长期内存收益应测量后判断。
- 定位缓存 TTL 为 3650 天；自动 IP 地点在会话内通常不会随网络/城市变化更新。
- 状态栏 `fetchWeather()` 丢弃 `Result.stale()`，过期数据外观与新数据相同。

建议：按 key 合并在途任务，使用请求代次与不可变参数；刷新保留旧值、强制重验证；类型化返回 `value/stale/error/updatedAt`，不要从展示字符串中的“⚠ 数据可能已过期”反推状态；定位使用合理 TTL 或用户可触发的重新定位。

### 11. 番茄钟按回调次数计时，隐藏控件会暂停推进

位置：`pomodoro/PomodoroEngine.java:57`、`BottomToolbarView.java:332`、`alarm/AlarmScheduler.java:23`。

每次 Swing Timer 回调只减一秒，EDT 忙碌/系统休眠时回调不能代表实际时间；`showPomodoro=false` 时不调用 tick，显示偏好影响了计时行为。闹钟只匹配当前分钟，跨过该分钟就没有补偿触发。

建议：分开“显示”和“运行”状态；用可注入的时间源、截止时间与暂停剩余量计算计时；明确休眠后补响/跳过策略。普通流逝时间可用单调时间，日历闹钟需要时区和墙上时钟。加入跳时、休眠、隐藏后恢复和重复闹钟的测试。

### 12. 播放器的“恢复”实际从头重播

位置：`music/MusicAudioPlayer.java:179`、`:190`。

pause 关闭 JLayer/流；resume 调用 play，重新打开 URL 并从头解码，没有保存帧位置或时间位置。

建议：明确暂停恢复的产品语义；需要真正续播时引入可暂停的解码循环或支持 seek 的音频实现，并校准歌词进度。网络读取、关闭连接和解码器的操作应避免在 EDT 持锁执行。需要真实音频/慢网络集成测试验证，不宜只测状态布尔值。

### 13. 配置与用户内容存储应分开，增加保存失败可见性

位置：`NotesTabPanel.java:129`、`:198`、`settings/SettingsRepository.java:80`、`settings/PreferencesSettingsStore.java:17`、`music/MusicApiClient.java:40`。

便签每次编辑仍 `getText()` 复制全部内容，500ms 防抖仅减少后续写入；保存时把全部便签 Base64 后写入一个 Preferences 值，收藏同样如此。没有容量检查、保存失败状态、备份或显式迁移恢复。标准 Java Preferences 单值有 8192 字符限制，实际 NetBeans 提供者是否覆盖限制需在真实 IDE 验证，不能仅据此断言当前 IDE 一定丢数据。

建议：便签/收藏移到用户目录下有版本的文件存储，临时文件写入后原子替换并保留备份；配置继续用 Preferences，密钥继续用 Keyring。通过独立内容仓库处理更新、保存队列、失败提示和迁移测试。拆分设置读取，避免每次音乐 HTTP 请求只为取得 host 就加载全套设置并读取天气密钥。

### 14. JSON/JWT 的解析和安全描述需要一致

位置：`weather/JsonParser.java:14`、`music/MusicApiClient.java:370`、`toolbox/core/JsonFormatter.java:20`、`toolbox/core/JwtDecoder.java:17`、`:57`。

项目同时存在天气 JSON AST、音乐的多套手工字段扫描、JWT 正则取字段、JSON 格式化字符扫描。嵌套对象、转义、aud 数组等语义易不一致；JSON Formatter 本身也不验证输入合法性。JWT 未验签，却把无 exp 描述成“永久有效”，会误导诊断。

建议：将通用 JSON 解析抽到中立包或采用一个成熟库，天气异常留在天气适配层；JWT 只读取顶层标准 claims，并区分“时间尚未过期”与“签名已验证”，无 exp 显示“未提供过期时间”。验证嵌套同名字段、数组、Unicode 转义、深度/输入上限。不要为了复用而让音乐依赖天气领域异常。

### 15. 密码生成使用了非密码学随机数

位置：`GenTabPanel.java:128`、`CommonUtils.java:39`、`README_CN.md:72`。

“随机强密码”实际调用 `Math.random()`。它适合模拟数据，不适合安全凭据。

建议：密码生成改成可复用的 SecureRandom，并明确字符策略；用于 Mock 的手机号/验证码可继续普通随机数，但 UI/文档应说明是测试数据。不要把是否出现重复当作验证随机数安全性的测试。

### 16. 状态栏生命周期和控制器职责不完整

位置：`BottomToolbarView.java:109`、`:406`、`StatusBarController.java:14`、`DocumentUtils.java:79`。

View 构造时启动 Timer/监听器，removeNotify 终止不可恢复的 LatestTask/RequestProcessor，却没有 addNotify 配对重建。组件重新挂载后，时钟/指标不再推进，后续天气提交也可能报错。实际 NetBeans 中哪些操作会重挂载需集成确认。

StatusBarController 只调用 loadSettings，而 View 构造已经调用过，尚未承担资源协调。每秒刷新指标也重复获取编码，当前 FileEncodingQuery 通过反射查找，尽管 POM 已直接声明 queries 依赖。

建议：让控制器管理启动、停止、配置变化，View 聚焦显示和事件；明确暂时移除与永久销毁。编码随文档/文件变化缓存，使用直接 API 或一次性能力探测；全量格式化和文件系统检查的线程选择按 NetBeans 官方锁顺序与后台任务 API 实施。内存条点击直接 System.gc() 也应考虑改为明确操作，后台调用不能消除整个 JVM 的 GC 停顿。

### 17. 测试需要从“有输出”转向边界、并发和集成行为

位置：`src/test/java/.../toolbox/core/ToolboxCoreTest.java:49`、`music/MusicApiClientTest.java:13`、`WeatherChartTest.java:29`。

78 个测试有价值，但现有 Cron 主要验证数量和递增，SQL 主要验证关键字，未覆盖本次错误。音乐测试直接访问公网歌曲接口，容易受 DNS、限流、内容变化影响，且成功也未证明特定 fallback 分支被执行。图表绘制到 BufferedImage 能发现绘制异常，却不能证明暗色主题、缩放、焦点和交互正确。未配置覆盖率报告，所以不能声称已有覆盖率数字。

建议：先补本报告的失败样例；把音乐 HTTP/音频设备变成可注入接口，默认单测完全离线，真实接口测试单独 profile；用可控线程调度测试过期结果与关闭；配置 JaCoCo 观察核心业务分支，再逐步设置有意义的门槛。增加 Windows/Linux 与选定 JDK 的构建矩阵，以及真实 NetBeans 安装、双入口音乐、窗口关闭恢复的冒烟测试。

### 18. 文档、可访问性和视觉体验应跟随实际能力

位置：`README_CN.md:61`、`:67`、`:68`、`:73`、`:96`、`:140`、`ColorTabPanel.java:150`、`HashTabPanel.java:126`、`UiDefaults.java:9`。

文档宣称 500 次防挂死、哈希并行、HSL 三向输入、Java 17、NbPreferences 加密，与代码不同：匹配未截断、四个哈希顺序执行、HSL 只读、密钥实际在 Keyring。1000 倍、99.9%、零分配等没有仓库内可复现基准支持，且 Diff 最坏情况已证明不满足笼统承诺。

建议：按实际功能重写这些说明，性能数字附输入分布、规模、环境和基准程序。UI 文案迁入 Bundle 资源；统一主题颜色/字体，补键盘焦点可见性、图标按钮 accessible name 和图表的文字等价内容。现有 UiDefaults 可复用，不必另建一套；暗色、125%/150%/200% 缩放和屏幕阅读器效果需实际验证。

## 建议实施顺序与验收

| 阶段 | 内容 | 完成标准 |
| --- | --- | --- |
| 1. 恢复可发布基线 | Java/依赖/CI 对齐、Checkstyle、NBM 路径；并行核实签名私钥用途 | 干净环境 verify 成功，CI 确实上传本次 NBM，能安装到目标 NetBeans |
| 2. 正确性与防卡死 | Cron、Regex、Diff、SQL/XML | 本次全部失败输入有回归覆盖；大输入有明确资源预算，UI 可继续交互 |
| 3. 异步与生命周期 | 音乐会话、过期请求、窗口关闭、天气刷新；真实计时 | A 慢 B 快仍显示/播放 B；反复开关不累积资源；失联保留并标注旧天气 |
| 4. 数据与维护 | 内容持久化、JSON 统一、测试隔离、可访问性、文档 | 保存失败可见且可恢复；默认测试离线；文档与产品能力逐项一致 |

不建议此时迁移 UI 技术栈、拆成微服务或为了抽象把每个 JPanel 建成独立模块。先以现有 Swing、Maven、NetBeans API 完成上述修复，再用测量结果决定更大的架构投入。

## 复现摘要

| 输入 / 命令 | 观察结果 |
| --- | --- |
| JDK 25 `mvn verify` | 78 tests passed；Checkstyle 2 errors |
| JDK 17 `mvn verify` | 78 tests；5 errors；class version 65 > 61 |
| Cron `*/0 * * * * ?` | 3 秒超时；源码显示零步长无限循环 |
| Cron `0 0 0 1 1 ? 2099` | 2027-01-01，忽略年份 |
| Cron `0 0 0 ? * MON` | 同时出现 Monday / Tuesday |
| Diff 两侧各 10,000 行不同文本，`-Xmx64m` | DiffCalculator:64 OutOfMemoryError |
| Regex `(a+)+$` / `a×10000 + !` | false，约 1036ms（单次，JDK 25） |
| XML `<p><b>A</b> <b>B</b></p>` minify | `<p><b>A</b><b>B</b></p>` |

可交互报告：[project-analysis-2026-10-07.html](project-analysis-2026-10-07.html)。该页面包含可筛选的问题、模块建议和全文件清单。源码位置均相对于项目根目录；本报告的证据对应本次分析的工作区快照。
