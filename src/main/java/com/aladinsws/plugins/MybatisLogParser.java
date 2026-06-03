package com.aladinsws.plugins;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MybatisLogParser {

    private static final String ERROR_NO_PREPARING =
            "Error: No 'Preparing:' line found in the selected text.";

    private static final Pattern PREPARING_PATTERN =
            Pattern.compile("(?:=+>\\s*)?Preparing:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARAMETERS_PATTERN =
            Pattern.compile("(?:=+>\\s*)?Parameters:\\s*(.*)", Pattern.CASE_INSENSITIVE);

    /** Matches a single typed parameter entry: {@code value(Type)} */
    private static final Pattern PARAM_ENTRY =
            Pattern.compile("(.+)\\(([^)]+)\\)");

    private MybatisLogParser() {}

    /** Holds the two relevant lines extracted from a raw log snippet. */
    private record ParsedLines(Optional<String> preparingSql, Optional<String> parametersLine) {}

    /**
     * Parses the raw multi-line MyBatis log text and returns a single
     * executable SQL string.
     *
     * @param rawLog the selected log text (may contain multiple lines)
     * @return formatted SQL, or an error message string if parsing fails
     */
    @NotNull
    public static String parse(@NotNull String rawLog) {
        var lines = scanLines(rawLog);
        return lines.preparingSql()
                .map(sql -> buildSql(sql, lines.parametersLine().orElse("")))
                .orElse(ERROR_NO_PREPARING);
    }

    /** Combines the prepared SQL template with the resolved parameter list. */
    @NotNull
    private static String buildSql(@NotNull String sql, @NotNull String params) {
        if (params.isBlank() || !sql.contains("?")) {
            return sql;
        }
        return replacePlaceholders(sql, parseParameters(params));
    }

    /**
     * Scans each log line and extracts the first {@code Preparing:} and
     * {@code Parameters:} values found.
     */
    @NotNull
    private static ParsedLines scanLines(@NotNull String rawLog) {
        Optional<String> preparingSql = Optional.empty();
        Optional<String> parametersLine = Optional.empty();

        for (String line : rawLog.split("\\r?\\n")) {
            var trimmed = line.trim();

            if (preparingSql.isEmpty()) {
                Matcher matcher = PREPARING_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    preparingSql = Optional.of(matcher.group(1).trim());
                }
            }

            if (parametersLine.isEmpty()) {
                Matcher matcher = PARAMETERS_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    parametersLine = Optional.of(matcher.group(1).trim());
                }
            }

            if (preparingSql.isPresent() && parametersLine.isPresent()) {
                break;
            }
        }

        return new ParsedLines(preparingSql, parametersLine);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    @NotNull
    private static List<String> parseParameters(@NotNull String parametersLine) {
        List<String> result = new ArrayList<>();

        for (String token : splitByTopLevelComma(parametersLine)) {
            var trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher matcher = PARAM_ENTRY.matcher(trimmed);
            if (matcher.matches()) {
                result.add(toSqlLiteral(matcher.group(1).trim(), matcher.group(2).trim()));
            } else if ("null".equalsIgnoreCase(trimmed)) {
                // Bare null without a type wrapper
                result.add("NULL");
            }
            // Malformed token — skip (keeps ? in place)
        }
        return result;
    }

    /**
     * Splits a parameter string on commas that are NOT inside parentheses,
     * allowing values such as {@code foo(a, b)} to be handled safely.
     */
    @NotNull
    private static List<String> splitByTopLevelComma(@NotNull String s) {
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                tokens.add(s.substring(start, i));
                start = i + 1;
            }
        }
        tokens.add(s.substring(start));
        return tokens;
    }

    @NotNull
    private static String toSqlLiteral(@NotNull String value, @NotNull String type) {
        // Handle null value regardless of declared type
        if ("null".equalsIgnoreCase(value)) {
            return "NULL";
        }

        return switch (type) {
            case "Integer", "int",
                 "Long", "long",
                 "Short", "short",
                 "Double", "double",
                 "Float", "float",
                 "BigDecimal", "BigInteger" -> value;

            case "Boolean", "boolean" ->
                    "true".equalsIgnoreCase(value) ? "1" : "0";

            default -> "'" + value.replace("'", "''") + "'";
        };
    }

    @NotNull
    private static String replacePlaceholders(@NotNull String sql, @NotNull List<String> params) {
        var result = new StringBuilder(sql.length() + params.size() * 8);
        int paramIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?' && paramIndex < params.size()) {
                result.append(params.get(paramIndex++));
            } else {
                result.append(sql.charAt(i));
            }
        }
        return result.toString();
    }
}

