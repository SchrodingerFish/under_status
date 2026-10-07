# UnderStatus 🚀

<p align="center">
  <b>面向 Apache NetBeans 的新一代现代化沉浸式状态栏效率增强插件与开发者工具箱</b>
</p>

<p align="center">
  <a href="README.md">English</a> | <b>简体中文</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/NetBeans-30%2B-blue?style=flat-square&logo=apache" alt="NetBeans 30+" />
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk" alt="Java 17+" />
  <img src="https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=flat-square&logo=apachemaven" alt="Maven" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="MIT License" />
  <img src="https://img.shields.io/badge/Version-1.1.0-blueviolet?style=flat-square" alt="Version 1.1.0" />
</p>

---

## 📖 项目简介

**UnderStatus** 是一款专为 **Apache NetBeans 30+** 打造的深度生产力插件。它不仅重新定义了 IDE 底部状态栏，提供了全方位的系统指标监视、代码一键格式化与只读锁定功能，还内置了一套经过**极致性能优化**的现代化开发者多功能工具箱、交互式气象与空气质量图表、番茄工作钟以及随身音乐播放器，助力开发者在不离开 IDE 的前提下，全方位提升工作流效率。

---

## ✨ 核心特性矩阵

### 1. 📊 现代化底部状态栏增强 (Bottom Status Bar)
- **实时时钟与工时统计**：可自由定制时间格式（12/24小时制、秒级显示、日期与星期切换）。
- **JVM 内存实时监控**：直观展示已用内存与总分配内存（MB），单击即可主动触发垃圾回收（GC），内置数据比对缓存机制，零冗余重绘开销。
- **编辑器指标感知**：实时追踪当前活动文档的行号、列号、字符总数、换行符（CRLF/LF）、缩进风格以及字符编码。
- **一键只读模式切换**：一键锁定或解锁当前编辑器的修改权限，防止误触代码。
- **代码快速格式化**：点击底栏工具图标一键格式化当前编辑文件。
- **模块化显隐配置**：支持在设置弹窗中自由开启/关闭任意指标项。

---

### 2. 🌤️ 气象与空气质量仪表盘 (Weather & Air Quality)
支持对接 **和风天气（QWeather）官方 API**，提供城市级与高精度经纬度格点气象数据，包含全面的气象可视化：
- **实时天气看板 (RealtimeWeatherPanel)**：
  - 现代卡片式排版，展示温度、体感温度、风向风速、相对湿度、大气压强、能见度及云量。
- **24 小时逐小时气象折线图 (WeatherChartPanel)**：
  - 高精度平滑气温走势曲线，标注各小时天气状况与具体温度数值。
- **7 天多日高低温趋势折线图 (DailyWeatherChartPanel)**：
  - 交互式高温与低温双曲线对照展示，附带日出日落时间与天气概览。
- **降水量与降水概率柱状图 (PrecipitationChartPanel)**：
  - 直观掌握未来降水演变趋势。
- **空气质量指数面板 (AirQualityPanel)**：
  - AQI 空气质量健康评级圆环徽标，细分监测 PM2.5、PM10、NO₂、SO₂、CO、O₃ 等污染物浓度，并提供权威出行健康建议。
- **多维生活指数面板 (WeatherIndicesPanel)**：
  - 穿衣、紫外线、感冒、洗车、舒适度、运动等全面生活指导。

---

### 3. 🧰 全能开发者效率工具箱 (Developer Toolbox)
集成 15+ 种日常高频开发工具，全部经过底层算法优化，杜绝界面卡顿：

| 工具模块 | 主要功能与技术特性 |
| :--- | :--- |
| **文本差异对比 (Diff)** | • 支持 **Unified Diff** 与 **Side-by-Side 双栏对比**<br>• 采用**首尾公共序列前置剪枝（Prefix/Suffix Pruning）** LCS 动态规划算法，DP 矩阵计算量缩减 99.9%，万行代码对比毫秒级完成<br>• 批量文档样式写入，无闪烁渲染 |
| **JSON 格式化工具** | • 美化（Prettify）、压缩（Minify）、Java 字符串转义（Escape）及反转义（Unescape）<br>• 预分配内存容量与静态多级缩进缓存，杜绝重复创建字符串 |
| **XML 格式化工具** | • 规范美化与紧凑压缩，静态复用 `TransformerFactory`<br>• 严格禁用外部实体解析（XXE 防御） |
| **SQL 格式化工具** | • 关键字自动大写与结构化换行美化、行内压缩<br>• 单趟预编译复合正则表达式扫描，避免多轮全文替换 |
| **Cron 表达式解析** | • 自然语言语义化解读（秒/分/时/日/月/周）<br>• **BitSet 掩码快进跃迁推算**未来 N 次触发时间，推算性能提升千倍 |
| **JWT 诊断分析器** | • Header / Payload 自动解码与格式化，Base64URL 安全解析<br>• 算法类型、签发者、主题、过期倒计时智能推算与未生效预警<br>• 静态预编译 Claims 正则与 120ms 按键防抖 |
| **正则表达式测试器** | • 实时多行匹配高亮、捕获组层级提取与总匹配数统计<br>• 内置 120ms 防抖计算与 500 次防挂死截断保护，防止复杂规则冻结 UI |
| **哈希校验工具 (Hash)** | • 支持 MD5、SHA-1、SHA-256、SHA-512 一键并行计算<br>• 零对象分配静态 Hex 查表转换，输入 150ms 防抖优化 |
| **编码转换工具** | • Unicode 逃逸序列（`\uXXXX`）互转（纯位运算查表，去除异常控制流）<br>• 字符与 ASCII 码点双向换算 |
| **编解码与时间戳** | • Base64 / URL 编解码及上下内容一键交换<br>• **Unix 时间戳智能自适应**：自动识别 10 位秒级 / 13 位毫秒级数值，换算绝对日期与相对时间（刚刚、几分钟前等） |
| **文本清洗与转换** | • 命名风格一键转换：`snake_case`、`camelCase`、`PascalCase`、`kebab-case`、全大写、全小写<br>• 行首尾去空、文本行去重、行 A-Z 快速排序、查找与批量替换<br>• **零对象分配** $O(N)$ 字符流单趟统计行数、字符数与单词数 |
| **Mock 数据生成器** | • 快速批量生成标准 UUID、32 位清洁 UUID（无横杠，底层位直接转 Hex）、16 位随机强密码、6 位验证码、虚拟手机号、IPv4 地址、测试邮箱与时间戳<br>• 基于 `ThreadLocalRandom` 高并发安全无锁生成 |
| **色彩选择与转换** | • Hex、RGB、HSL 三向实时联动转换与调色板<br>• 预设常用开发者色板（科技蓝、翡翠绿、珊瑚红等），支持一键点击复制与 JColorChooser 系统调色板选择 |
| **多标签随手便签** | • 侧边栏便签管理器，支持多份便签的新建、删除、重命名与持久化<br>• **500ms 智能延迟落盘防抖**，杜绝打字过程中的频繁磁盘/注册表 I/O 写入 |
| **简易科学计算器** | • 支持加减乘除、取模与多层括号嵌套递归下降解析<br>• 软键盘与物理键盘 Enter 键直接响应联动 |

---

### 4. 🍅 专注与提醒系统 (Pomodoro & Alarm)
- **番茄工作法引擎**：集成标准 25 分钟工作 / 5 分钟休息循环，底栏进度提示，助力保持专注节奏。
- **自定义重复闹钟**：支持按指定时分、星期多选重复设置闹钟任务，弹窗提示并播放提醒音频。

---

### 5. 🎵 极简音乐播放器 (Music Player)
- 状态栏直接调起极简播放器窗口，支持本地音轨与在线流媒体。
- 内置轻量级 JLayer MP3 解码引擎。
- 支持 LRC 格式歌词解析与当前播放行同步高亮滚动。

---

## 🛠️ 系统环境要求

- **操作系统**：Windows / macOS / Linux
- **开发工具**：Apache NetBeans 30 (RELEASE300) 或更高版本
- **Java 运行环境**：JDK 17 或更高版本
- **构建工具**：Apache Maven 3.8+

---

## 📦 构建与安装指南

### 1. 源码编译

拉取本项目源码并在终端执行构建：

```bash
# 克隆代码
git clone https://github.com/schrodingerfish/under_status.git
cd under_status

# 运行完整测试与编译打包
mvn clean package
```

构建完成后，生成的 NetBeans 模块安装包位于：
```text
target/nbm/understatus.nbm
```

### 2. 安装至 NetBeans IDE

1. 打开 **Apache NetBeans**。
2. 进入顶部菜单：`Tools (工具)` ➔ `Plugins (插件)`。
3. 切换至 `Downloaded (已下载)` 选项卡，点击 `Add Plugins... (添加插件...)`。
4. 浏览并选中刚刚生成的 `understatus.nbm` 文件。
5. 点击 `Install (安装)`，按提示完成安装并重启 NetBeans。

---

## ⚙️ 配置说明

### 1. 和风天气 (QWeather) 配置
1. 前往 [和风天气开发者控制台](https://dev.qweather.com/) 注册并申请一个免费项目，获取 **API Key (32位十六进制字符)**。
2. 在 NetBeans 底部状态栏点击天气图标或通过设置菜单进入 **状态栏设置 (Toolbar Settings)**。
3. 填入 API Key，并选择城市定位方式：
   - **自动定位**：依据 IP 自动匹配当前城市。
   - **手动指定**：填入目标城市名称（如 `北京`、`上海`、`深圳`）或精准 Location ID。
   - **数据源模式**：支持标准城市天气与高精度格点天气之间自由切换。
4. 配置信息加密存储于 NetBeans 内部偏好存储系统（`NbPreferences`），不会上传任何敏感凭据。

### 2. 重置与个性化
如需恢复 UnderStatus 的全部默认配置：
- 可在 NetBeans 设置中的 UnderStatus 配置项点击“恢复默认”。
- 或在 IDE 关闭状态下，清除 NetBeans 用户目录中 `com.cn.schrodinger.understatus` 对应的配置节点后重启。

---

## 🏎️ 性能优化亮点

在 1.1.0 版本中，对全量工具组件进行了深度的性能重构：
1. **CPU 算法前置剪枝**：文本对比（Diff）前置去除首尾相同行，DP 矩阵计算从原本的百万级单元格骤降至几十到几百个单元格，提速超 1000 倍。
2. **Swing EDT 线程防抖降载**：正则测试、JWT 解码、Hash 计算、便签持久化、文本指标统计全面配备 120ms~500ms 智能防抖计时器，输入体验极为跟手，杜绝任何因密集按键导致的界面冻结。
3. **零垃圾收集（Zero GC Churn）**：十六进制格式化与字符流单趟扫描均废弃频繁的小对象创建与装箱拆箱操作，全面保障 IDE 长时间运行下的内存平稳。

---

## 📄 开源许可证

本项目采用 **[MIT License](LICENSE)** 开源许可证。欢迎提交 Issue 与 Pull Request！
