# Changelog

All notable changes to **MybatisLogFormatSQL** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

## [0.1.0-beta] - 2026-06-02

### Added
- Right-click editor action **"Format MyBatis Log SQL"** — visible only when text is selected
- `MybatisLogParser`: pure utility class that parses `Preparing:` and `Parameters:` lines from MyBatis log output and produces a single executable SQL string
- Smart SQL literal rendering based on declared Java type:
  - Numeric types (`Integer`, `Long`, `Short`, `Double`, `Float`, `BigDecimal`, `BigInteger`) — emitted unquoted
  - `Boolean` / `boolean` — converted to `1` (true) or `0` (false)
  - All other types (`String`, `Date`, `LocalDate`, `LocalDateTime`, …) — single-quoted with `'` escaping
  - Bare or typed `null` — rendered as `NULL`
- `MybatisLogDialog`: modal dialog displaying the formatted SQL in a scrollable, editable monospaced text area
- **"Copy & Close"** button — copies the current textarea content to the system clipboard and closes the dialog
- Comma splitting that is safe for values containing nested parentheses (e.g. complex type descriptors)

### Requirements
- IntelliJ-based IDE **2026.1.x** (build `261.*`)
- Java 21

[Unreleased]: https://github.com/aladinsws/MybatisLogFormatSQL/compare/v0.1.0-beta...HEAD
[0.1.0-beta]: https://github.com/aladinsws/MybatisLogFormatSQL/releases/tag/v0.1.0-beta
