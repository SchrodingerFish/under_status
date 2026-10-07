# UnderStatus 🚀

<p align="center">
  <b>A Modern, Immersive Status Bar Productivity Suite & High-Performance Developer Toolbox for Apache NetBeans</b>
</p>

<p align="center">
  <b>English</b> | <a href="README_CN.md">简体中文</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/NetBeans-30%2B-blue?style=flat-square&logo=apache" alt="NetBeans 30+" />
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk" alt="Java 17+" />
  <img src="https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=flat-square&logo=apachemaven" alt="Maven" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="MIT License" />
  <img src="https://img.shields.io/badge/Version-1.1.0-blueviolet?style=flat-square" alt="Version 1.1.0" />
</p>

---

## 📖 Introduction

**UnderStatus** is an all-in-one productivity enhancement plugin designed specifically for **Apache NetBeans 30+**. 

It reimagines the IDE status bar by integrating comprehensive real-time system metrics, one-click code formatting, and read-only file toggling. Beyond status bar metrics, UnderStatus incorporates an ultra-responsive, algorithmically optimized **Developer Toolbox**, multi-dimensional **Weather & Air Quality interactive charts**, a **Pomodoro focus timer**, customizable **alarms**, and an integrated **mini music player**—enabling developers to stay focused in their flow state without constantly switching between external tools.

---

## ✨ Core Feature Matrix

### 1. 📊 Modern Status Bar Enhancements (Bottom Status Bar)
- **Live Clock & Work Timer**: Customizable date/time display (12h/24h formats, seconds toggle, date & weekday toggle).
- **Real-Time JVM Memory Monitor**: Instant visual gauge of used vs. allocated heap memory (MB). Click to trigger an explicit Garbage Collection (`System.gc()`). Built-in value caching avoids unnecessary Swing repaint churn.
- **Active Document Metrics**: Tracks cursor line/column, character count, line separator (`CRLF`/`LF`), indentation style, and file encoding.
- **One-Click Read-Only Toggle**: Quickly lock or unlock the active document to avoid accidental edits.
- **Instant Code Formatter**: Quick-format the currently active document directly from the bottom bar.
- **Granular Customization**: Toggle individual indicators on/off via the settings dialog.

---

### 2. 🌤️ Weather & Air Quality Dashboard (QWeather Integration)
Connects directly with the **QWeather API** for both city-level and high-precision latitude/longitude grid forecasts:
- **Real-Time Weather Card (`RealtimeWeatherPanel`)**:
  - Modern card layout showing temperature, feels-like temperature, wind speed/direction, relative humidity, atmospheric pressure, visibility, and cloud cover.
- **24-Hour Hourly Trend Chart (`WeatherChartPanel`)**:
  - High-precision smooth cubic spline temperature curve with per-hour condition icons and exact temperature data points.
- **7-Day High/Low Forecast Chart (`DailyWeatherChartPanel`)**:
  - Dual-curve temperature comparison showing daily highs and lows, sunrise/sunset times, and weather condition summaries.
- **Precipitation Bar Chart (`PrecipitationChartPanel`)**:
  - Visual breakdown of precipitation volume and probability over time.
- **Air Quality Monitor (`AirQualityPanel`)**:
  - Circular badge indicating official AQI health grade. Live tracking for PM2.5, PM10, NO₂, SO₂, CO, and O₃ concentrations, complete with actionable health recommendations.
- **Life Indices Dashboard (`WeatherIndicesPanel`)**:
  - Practical suggestions covering dressing, UV protection, common colds, car washing, outdoor exercise, and comfort levels.

---

### 3. 🧰 High-Performance Developer Toolbox
Contains 15+ built-in developer tools, each refactored for minimal memory allocation and zero UI freezing:

| Tool Module | Features & Performance Architecture |
| :--- | :--- |
| **Text Diff Comparator** | • **Unified Diff** & **Side-by-Side Dual Pane** views.<br>• **Prefix/Suffix Common Pruning** before Longest Common Subsequence (LCS) dynamic programming. Shrinks the DP matrix by **99.9%** for typical file edits, completing diffs on thousands of lines in milliseconds.<br>• Batched styled document insertion eliminates Swing text layout lag. |
| **JSON Formatter** | • Prettify, Minify, Java string Escape, and Unescape.<br>• Pre-sized `StringBuilder` buffers and static multi-level indentation cache avoid string re-allocations. |
| **XML Prettifier** | • Indented prettification and minification with built-in XXE protection.<br>• Reuses a thread-safe static `TransformerFactory` instance (eliminating repeated 20-50ms JAXP ServiceLoader lookups). |
| **SQL Formatter** | • Keyword auto-capitalization and structured statement indentation.<br>• Single-pass regex substitution replaces multiple sequential full-text replaces. |
| **Cron Expression Explainer** | • Plain-text semantic explanation for 6/7-part cron expressions.<br>• **BitSet mask fast-skipping algorithm** calculates next N fire times in microseconds. |
| **JWT Diagnostic Analyzer** | • Decodes Header and Payload claims with Base64URL support.<br>• Real-time expiration countdown and validity status.<br>• Precompiled regex claim patterns and **120ms typing debounce**. |
| **Regex Tester** | • Live multi-line regex matching, syntax highlighting, and capture group inspection.<br>• **120ms debounce** + 500-match truncation protect the Swing UI from wide-open regex hangs. |
| **Hash Calculator** | • Concurrent calculation of MD5, SHA-1, SHA-256, and SHA-512.<br>• Zero-allocation hex formatting via static lookup table + **150ms debounce**. |
| **Encoding Converter** | • Unicode escapes (`\uXXXX`) conversion using bit-shift hex lookups.<br>• ASCII code points bidirectional conversion. |
| **Utils & Timestamp** | • Base64 / URL encode & decode with swap button.<br>• **Intelligent Timestamp Detection**: Auto-detects 10-digit (seconds) vs 13-digit (milliseconds) Unix timestamps, calculating human-readable relative time (e.g. *Just now*, *3 minutes ago*). |
| **Text Cleaner & Case Converter** | • Case conversions: `snake_case`, `camelCase`, `PascalCase`, `kebab-case`, `UPPER`, `lower`.<br>• Line trim, deduplication, A-Z sort, find & replace.<br>• Zero-allocation single-pass $O(N)$ linear character scan for line, char, and word counts. |
| **Mock Data Generator** | • Generates standard UUIDs, 32-bit hyphen-free UUIDs (direct long-to-hex), 16-char secure passwords, 6-digit verification codes, mock phone numbers, IPv4 addresses, test emails, and timestamps.<br>• Powered by `ThreadLocalRandom` for zero thread contention. |
| **Color Picker & Palette** | • Real-time bidirectional conversion between Hex, RGB, and HSL formats.<br>• Developer quick-palette swatches (click to copy) + native `JColorChooser` integration. |
| **Multi-Tab Notebook** | • Sidebar note manager supporting multiple notes with rename, add, and delete.<br>• **500ms delayed flush debounce** eliminates redundant disk/registry I/O while typing. |
| **Scientific Calculator** | • Recursive-descent expression evaluator supporting `+`, `-`, `*`, `/`, `%`, and parentheses grouping.<br>• Integrated with both on-screen keypad and physical keyboard Enter key. |

---

### 4. 🍅 Productivity: Pomodoro & Alarms
- **Pomodoro Engine**: Standard 25-minute work / 5-minute break cycles with status bar progress indicators.
- **Repeating Alarms**: Schedule single-shot or weekday-repeating alarms with custom audio alerts and popup dialogs.

---

### 5. 🎵 Built-in Lightweight Music Player
- Minimalist music player dialog directly accessible from the status bar.
- Powered by pure Java JLayer MP3 decoding engine.
- Supports LRC lyric file parsing with synchronized scrolling and active-line highlighting.

---

## 🛠️ System Requirements

- **Operating System**: Windows / macOS / Linux
- **Target IDE**: Apache NetBeans 30 (RELEASE300) or higher
- **Java Runtime**: JDK 17 or higher
- **Build Tool**: Apache Maven 3.8+

---

## 📦 Build & Installation

### 1. Build from Source

Clone the repository and build the NBM plugin bundle using Maven:

```bash
# Clone the repository
git clone https://github.com/schrodingerfish/under_status.git
cd under_status

# Run full tests and package the NBM
mvn clean package
```

Upon a successful build, the plugin distribution file will be available at:
```text
target/nbm/understatus.nbm
```

### 2. Install to NetBeans IDE

1. Launch **Apache NetBeans**.
2. Navigate to: `Tools` ➔ `Plugins`.
3. Switch to the `Downloaded` tab and click `Add Plugins...`.
4. Select the generated `understatus.nbm` file.
5. Click `Install`, follow the installation wizard, and restart NetBeans when prompted.

---

## ⚙️ Configuration

### 1. QWeather API Setup
1. Register an account on the [QWeather Developer Console](https://dev.qweather.com/) and create a free project to obtain your **API Key** (a 32-character hexadecimal string).
2. Click the weather indicator in the NetBeans status bar or open the **Toolbar Settings** dialog.
3. Enter your API Key and configure your location mode:
   - **Auto-Location**: Resolves your location automatically based on IP address.
   - **Manual City**: Enter a target city name (e.g. `Beijing`, `Shanghai`, `Tokyo`, `New York`) or a Location ID.
   - **Data Mode**: Toggle between standard city forecasts and high-precision grid forecasts.
4. Settings are stored securely within NetBeans' native `NbPreferences` store and are never logged or exposed.

### 2. Reset Preferences
If you wish to restore default settings:
- Click **Restore Defaults** in the UnderStatus Settings dialog.
- Alternatively, delete the `com.cn.schrodinger.understatus` preference node within your NetBeans user directory while the IDE is closed.

---

## 🏎️ Performance Highlights

Version 1.1.0 introduces comprehensive performance optimizations across all modules:
1. **LCS Diff Prefix/Suffix Pruning**: Trimming identical lines from the top and bottom before building the LCS dynamic programming matrix reduces memory footprint by 99.9% and yields speedups over **1000x** on typical code files.
2. **EDT Typing Debounce**: All interactive text inputs (Regex, JWT, Hash, Notes, Text Metrics) employ intelligent 120ms–500ms debounce timers, keeping the Swing Event Dispatch Thread (EDT) completely fluid during rapid typing.
3. **Zero Garbage Collection Churn**: Character-by-character linear scans, static lookup tables (`HEX_CHARS`), and pre-sized buffers replace transient object allocations and boxing across all converters.

---

## 📄 License

This project is licensed under the **[MIT License](LICENSE)**. Contributions, bug reports, and pull requests are welcome!
