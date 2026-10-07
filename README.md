# UnderStatus

An Apache NetBeans status bar plugin with developer tools, notes, weather, timers, alarms, and an MP3 player.

**English** | [简体中文](README_CN.md)

## Requirements and installation

- Apache NetBeans 30 / RELEASE300 APIs; JDK **21 or newer** for building and running the module.
- Maven 3.8+; Windows, Linux, or macOS. CI builds on Windows and Linux with Java 21 and 25.

```bash
git clone https://github.com/schrodingerfish/under_status.git
cd under_status
mvn clean verify
```

Install **`target/under_status.nbm`** using NetBeans **Tools → Plugins → Downloaded → Add Plugins**. Restart if requested. `verify` runs JUnit, Checkstyle, dependency bytecode checks, and generates `target/site/jacoco/index.html`. A module build does not replace installation testing in the target IDE.

## Features

| Area | Behavior |
| --- | --- |
| Status bar | Clock, work timer, JVM memory, editor line/column and encoding, read-only toggle, formatting, and configurable indicators. Clicking memory refreshes the display. |
| Weather | QWeather conditions, hourly/daily forecasts, precipitation, air quality, and life indices, subject to API subscription. Refresh preserves usable cached data on failure and labels stale results. |
| Text tools | Diff, strict JSON formatting, XML, SQL, Cron preview, JWT inspection, Java regex, hashes, encoding, Base64/URL conversion, timestamps, and text transformations. |
| Other tools | Multi-note notebook, calculator, mock data generator, and color picker. Hex/RGB are editable; HSL is a readout. |
| Timers | Configurable work/break periods and single or weekday alarms. Hiding the Pomodoro indicator does not pause it. Delayed callbacks advance to one full next phase; alarms catch up the latest eligible occurrence within 24 hours. |
| Music | Online search/playback through the configured API, favorites, synchronized lyrics, and frame-based MP3 pause/resume. Both entry points share one playback session. Closing the last music view stops playback; pending favorite saves continue. |

## Tool behavior and limits

- **Cron:** Unix five-field and Quartz six/seven-field previews have separate weekday rules. Supports lists, ranges, names, and positive steps; Quartz requires exactly one `?` in the day fields. Years range from 1970 to 2199. Unsupported extensions return an error. Preview uses local calendar time, not a guarantee of scheduler behavior across daylight-saving transitions.
- **Regex:** Java syntax in an isolated JVM, with a 64 MiB heap and a two-second wall-time limit including startup. Timeout/cancellation terminates the child. Expressions/replacements are limited to 2,048 UTF-16 characters, input to 100,000, output to 200,000, and displayed matches to 500. Replacement refuses more than 500 matches. Requires the IDE runtime's `bin/java` executable and a writable temporary directory.
- **Diff:** exact LCS has a budget of 1,000,000 matrix cells. Larger unmatched regions become coarse deletion/addition blocks with a notice; each side is limited to 20,000 lines. The visible view is rendered on demand.
- **Text processing:** shared tools generally accept up to 250,000 UTF-16 characters and return up to 500,000; individual tools can have tighter bounds. Bounded background tasks discard obsolete results.
- **SQL/XML:** SQL preserves literals, quoted identifiers, parameters, and comments; it is a lexical formatter, not a validator for every dialect. XML disables external entities and preserves text and whitespace, including mixed content. It deliberately avoids aggressive whitespace removal.
- **JSON/JWT:** JSON rejects malformed input, duplicate keys, trailing values, and excessive nesting. Decimal scale is limited to ±1,024 to bound exponent conversions; JWT dates must fit an Instant with nanosecond precision. JWT inspection **does not verify signatures or establish authenticity**.
- **Random data:** passwords use `SecureRandom`; codes, phone numbers, addresses, and email addresses are mock data for testing. Hashes are computed sequentially in a background task.

## Configuration and storage

Configure indicators, timers, music host, QWeather host/key, and location from toolbar settings. Weather availability depends on endpoint/account permissions. Requests have bounded bodies and timeouts; default tests use fixtures and local loopback servers instead of public music/weather services.

Ordinary settings use NetBeans Preferences. The QWeather API key uses **NetBeans Keyring**; Preferences is not an encrypted credential store. Keyring protection depends on its platform provider.

Notes and favorites are stored separately under:

```text
<netbeans.user>/config/understatus/content/
```

Outside NetBeans, the fallback is `<user.home>/.understatus/content/`. Each content file has a format version, checksum, atomic replacement, and a previous-version backup. Content is **not encrypted**. The maximum serialized payload is 16 MiB per file. Existing Preferences content migrates when a file is absent, retaining the old preference as backup; a saved empty file remains empty. Back up this directory when moving IDE profiles.

Notes share documents across views and save after a 500 ms debounce on a background queue. Favorites use serialized background persistence. Save failures are visible and retryable; unsaved edits remain in the current IDE session. If primary and backup both cannot be read, editing is blocked until loading succeeds, preventing accidental overwrite. Confirm saving before exiting the IDE.

## Signing and validation

The old root `keystore` was confirmed to be an unused test file and is excluded from version control. Keep future release keys outside the repository and inject paths/passwords through private release configuration. Untracking does not erase Git history; no history rewrite or key rotation is part of this repair.

See the [original audit](docs/project-analysis-2026-10-07.md) and [repair and validation report](docs/project-repairs-2026-10-07.md). Resource limits are safeguards, not throughput benchmarks. Full IDE interaction, audio hardware, assistive technology, and cross-platform visual acceptance require separate validation.

## License

[MIT](LICENSE).
