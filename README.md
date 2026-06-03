# MybatisLogFormatSQL

![Build](https://github.com/aladinsws/MybatisLogFormatSQL/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/32073-mybatislogformatsql.svg)](https://plugins.jetbrains.com/plugin/32073-mybatislogformatsql)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32073-mybatislogformatsql.svg)](https://plugins.jetbrains.com/plugin/32073-mybatislogformatsql)

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "MybatisLogFormatSQL"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32073-mybatislogformatsql) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/32073-mybatislogformatsql/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/aladinsws/MybatisLogFormatSQL/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## How to Use

### Basic Usage

1. Select the entire log output or the relevant SQL portion that contains SQL with parameter placeholders (`?`) text into your editor or console
2. Right-click and choose **MybatisLogFormatSQL** from the context menu
3. dialog will open displaying the formatted SQL with all `?` placeholders replaced with their actual parameter values

### Examples

**MyBatis Log:**
```
==> Preparing: INSERT INTO orders(user_id, product, qty, price, note) VALUES (?, ?, ?, ?, ?)
==> Parameters: 1001(Long), Widget(String), 3(Integer), 9.99(BigDecimal), null(String)
```

**Formatted SQL:**
```sql
INSERT INTO orders(user_id, product, qty, price, note) VALUES (1001, 'Widget', 3, 9.99, NULL)
```