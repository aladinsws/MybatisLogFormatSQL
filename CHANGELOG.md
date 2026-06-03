# Changelog

All notable changes to **MybatisLogFormatSQL** will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-06-03

### Added

- **MybatisLogFormatSQL** action available in the editor right-click context menu (visible only when text is selected).
- Parses MyBatis log output containing `Preparing:` and `Parameters:` lines and replaces all `?` placeholders with their actual typed values.
- Type-aware SQL literal rendering:
  - Numeric types (`Integer`, `Long`, `Short`, `Double`, `Float`, `BigDecimal`, `BigInteger`) are emitted unquoted.
  - `Boolean` / `boolean` values are converted to `1` / `0`.
  - `null` values (with or without a type wrapper) are rendered as `NULL`.
  - All other types (e.g. `String`, `Date`, `LocalDate`, `LocalDateTime`) are single-quoted with internal single-quotes escaped.
- Comma-safe parameter xsplitting — values containing commas inside parentheses (e.g. complex type descriptors) are handled correctly.
- Result displayed in a resizable, scrollable modal dialog ("MyBatis Log — Formatted SQL") with a **Copy & Close** button that copies the SQL to the system clipboard.
- Compatible with IntelliJ-based IDEs build **251** through **261.\*** (2025.1 and above).

[Unreleased]: https://github.com/aladinsws/MybatisLogFormatSQL/compare/0.1.0...HEAD
[0.1.0]: https://github.com/aladinsws/MybatisLogFormatSQL/commits/0.1.0
