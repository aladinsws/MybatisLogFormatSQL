package com.aladinsws.plugins;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link MybatisLogParser}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Basic SQL + parameters parsing</li>
 *   <li>All supported SQL literal type mappings</li>
 *   <li>NULL handling (typed and bare)</li>
 *   <li>Single-quote escaping inside string values</li>
 *   <li>Arrow-prefixed log format (==&gt; Preparing: …)</li>
 *   <li>Case-insensitive keywords</li>
 *   <li>More placeholders than parameters (remaining ? kept)</li>
 *   <li>No parameters line / empty parameters</li>
 *   <li>Missing Preparing line (error message)</li>
 *   <li>Windows (CRLF) line endings</li>
 *   <li>Extra surrounding log lines are ignored</li>
 *   <li>Boolean → 1 / 0 conversion</li>
 * </ul>
 */
public class MybatisLogParserTest {

    // -----------------------------------------------------------------------
    // Constants for duplicated literals
    // -----------------------------------------------------------------------

    private static final String SQL_WHERE_X_NULL = "SELECT * FROM t WHERE x = NULL";
    private static final String SQL_WHERE_X_PARAM = "SELECT * FROM t WHERE x = ?";
    private static final String SQL_SELECT_NOW = "SELECT NOW()";

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static String log(String preparing, String parameters) {
        return "Preparing: " + preparing + "\nParameters: " + parameters;
    }

    // -----------------------------------------------------------------------
    // Basic happy-path tests
    // -----------------------------------------------------------------------

    @Test
    public void testSimpleStringParameter() {
        String input = log("SELECT * FROM user WHERE name = ?", "Alice(String)");
        assertEquals("SELECT * FROM user WHERE name = 'Alice'", MybatisLogParser.parse(input));
    }

    @Test
    public void testMultipleParameters() {
        String input = log(
                "SELECT * FROM user WHERE id = ? AND name = ?",
                "42(Integer), Bob(String)"
        );
        assertEquals("SELECT * FROM user WHERE id = 42 AND name = 'Bob'",
                MybatisLogParser.parse(input));
    }

    @Test
    public void testNoPlaceholdersReturnsPreparingLineAsIs() {
        String input = log("SELECT 1 FROM dual", "");
        assertEquals("SELECT 1 FROM dual", MybatisLogParser.parse(input));
    }

    // -----------------------------------------------------------------------
    // Type mapping tests
    // -----------------------------------------------------------------------

    @Test
    public void testIntegerTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE id = 10",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE id = ?", "10(Integer)")));
    }

    @Test
    public void testLongTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE id = 9999999999",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE id = ?", "9999999999(Long)")));
    }

    @Test
    public void testShortTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE flag = 1",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE flag = ?", "1(Short)")));
    }

    @Test
    public void testDoubleTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE price = 3.14",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE price = ?", "3.14(Double)")));
    }

    @Test
    public void testFloatTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE ratio = 0.5",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE ratio = ?", "0.5(Float)")));
    }

    @Test
    public void testBigDecimalTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE amount = 12345.6789",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE amount = ?", "12345.6789(BigDecimal)")));
    }

    @Test
    public void testBigIntegerTypeUnquoted() {
        assertEquals("SELECT * FROM t WHERE n = 123456789012345678",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE n = ?", "123456789012345678(BigInteger)")));
    }

    @Test
    public void testBooleanTrueReturns1() {
        assertEquals("UPDATE t SET active = 1 WHERE id = 1",
                MybatisLogParser.parse(log("UPDATE t SET active = ? WHERE id = ?", "true(Boolean), 1(Integer)")));
    }

    @Test
    public void testBooleanFalseReturns0() {
        assertEquals("UPDATE t SET active = 0 WHERE id = 2",
                MybatisLogParser.parse(log("UPDATE t SET active = ? WHERE id = ?", "false(Boolean), 2(Integer)")));
    }

    @Test
    public void testStringTypeQuoted() {
        assertEquals("INSERT INTO t(name) VALUES ('hello')",
                MybatisLogParser.parse(log("INSERT INTO t(name) VALUES (?)", "hello(String)")));
    }

    @Test
    public void testDateTypeQuoted() {
        assertEquals("SELECT * FROM t WHERE created = '2024-01-15'",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE created = ?", "2024-01-15(Date)")));
    }

    @Test
    public void testLocalDateTypeQuoted() {
        assertEquals("SELECT * FROM t WHERE dt = '2024-06-02'",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE dt = ?", "2024-06-02(LocalDate)")));
    }

    @Test
    public void testLocalDateTimeTypeQuoted() {
        assertEquals("SELECT * FROM t WHERE ts = '2024-06-02T10:30:00'",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE ts = ?", "2024-06-02T10:30:00(LocalDateTime)")));
    }

    // -----------------------------------------------------------------------
    // NULL handling
    // -----------------------------------------------------------------------

    @Test
    public void testTypedNullValueReturnsNULL() {
        assertEquals(SQL_WHERE_X_NULL,
                MybatisLogParser.parse(log(SQL_WHERE_X_PARAM, "null(String)")));
    }

    @Test
    public void testTypedNullNumericReturnsNULL() {
        assertEquals(SQL_WHERE_X_NULL,
                MybatisLogParser.parse(log(SQL_WHERE_X_PARAM, "null(Integer)")));
    }

    @Test
    public void testBareNullReturnsNULL() {
        // Some MyBatis versions emit "null" without a type wrapper
        assertEquals(SQL_WHERE_X_NULL,
                MybatisLogParser.parse(log(SQL_WHERE_X_PARAM, "null")));
    }

    @Test
    public void testMixedNullAndValues() {
        String input = log(
                "UPDATE t SET a = ?, b = ?, c = ?",
                "null(String), 99(Integer), hello(String)"
        );
        assertEquals("UPDATE t SET a = NULL, b = 99, c = 'hello'",
                MybatisLogParser.parse(input));
    }

    // -----------------------------------------------------------------------
    // Single-quote escaping
    // -----------------------------------------------------------------------

    @Test
    public void testStringWithSingleQuoteEscaped() {
        assertEquals("SELECT * FROM t WHERE name = 'O''Brien'",
                MybatisLogParser.parse(log("SELECT * FROM t WHERE name = ?", "O'Brien(String)")));
    }

    // -----------------------------------------------------------------------
    // Arrow-prefixed log format  (==> Preparing: … / ==> Parameters: …)
    // -----------------------------------------------------------------------

    @Test
    public void testArrowPrefixedFormat() {
        String input = "==> Preparing: SELECT id FROM user WHERE id = ?\n" +
                       "==> Parameters: 7(Integer)";
        assertEquals("SELECT id FROM user WHERE id = 7", MybatisLogParser.parse(input));
    }

    @Test
    public void testMultipleEqualsArrowFormat() {
        String input = "===> Preparing: SELECT id FROM t WHERE code = ?\n" +
                       "===> Parameters: ABC(String)";
        assertEquals("SELECT id FROM t WHERE code = 'ABC'", MybatisLogParser.parse(input));
    }

    // -----------------------------------------------------------------------
    // Case-insensitive keywords
    // -----------------------------------------------------------------------

    @Test
    public void testUpperCaseKeywords() {
        String input = "PREPARING: SELECT 1 FROM dual WHERE x = ?\nPARAMETERS: 5(Integer)";
        assertEquals("SELECT 1 FROM dual WHERE x = 5", MybatisLogParser.parse(input));
    }

    @Test
    public void testMixedCaseKeywords() {
        String input = "Preparing: SELECT 1 WHERE a = ?\nparameters: z(String)";
        assertEquals("SELECT 1 WHERE a = 'z'", MybatisLogParser.parse(input));
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testMorePlaceholdersThanParametersRemainingKept() {
        // 3 placeholders but only 2 params — last ? stays literally
        String input = log("SELECT ? , ?, ?", "1(Integer), 2(Integer)");
        assertEquals("SELECT 1 , 2, ?", MybatisLogParser.parse(input));
    }

    @Test
    public void testNoParametersLineSqlReturnedAsIs() {
        // No Parameters: line at all
        String input = "Preparing: " + SQL_SELECT_NOW;
        assertEquals(SQL_SELECT_NOW, MybatisLogParser.parse(input));
    }

    @Test
    public void testEmptyParametersLineNoPlaceholdersSqlReturnedAsIs() {
        String input = log(SQL_SELECT_NOW, "");
        assertEquals(SQL_SELECT_NOW, MybatisLogParser.parse(input));
    }

    @Test
    public void testMissingPreparingLineReturnsErrorMessage() {
        String input = "Parameters: 1(Integer)";
        assertTrue("Expected error message",
                MybatisLogParser.parse(input).startsWith("Error:"));
    }

    @Test
    public void testEmptyInputReturnsErrorMessage() {
        assertTrue(MybatisLogParser.parse("").startsWith("Error:"));
    }

    @Test
    public void testWindowsLineEndings() {
        String input = "Preparing: SELECT * FROM t WHERE id = ?\r\nParameters: 42(Integer)";
        assertEquals("SELECT * FROM t WHERE id = 42", MybatisLogParser.parse(input));
    }

    @Test
    public void testExtraLogLinesAreIgnored() {
        String input = """
                [DEBUG] com.example.mapper.UserMapper - ==> Preparing: SELECT * FROM user WHERE id = ?
                [DEBUG] com.example.mapper.UserMapper - ==> Parameters: 1(Integer)
                [DEBUG] com.example.mapper.UserMapper - <==    Columns: id, name
                [DEBUG] com.example.mapper.UserMapper - <==        Row: 1, Alice
                [DEBUG] com.example.mapper.UserMapper - <==      Total: 1""";
        assertEquals("SELECT * FROM user WHERE id = 1", MybatisLogParser.parse(input));
    }

    @Test
    public void testMultipleQuestionsInComplexSql() {
        String input = log(
                "INSERT INTO orders(user_id, product, qty, price, note) VALUES (?, ?, ?, ?, ?)",
                "1001(Long), Widget(String), 3(Integer), 9.99(BigDecimal), null(String)"
        );
        assertEquals(
                "INSERT INTO orders(user_id, product, qty, price, note) VALUES (1001, 'Widget', 3, 9.99, NULL)",
                MybatisLogParser.parse(input)
        );
    }

    @Test
    public void testPrimitiveLowercaseTypes() {
        // Primitive int / long / short / double / float / boolean
        String input = log(
                "SELECT ? , ?, ?, ?, ?, ?",
                "1(int), 2(long), 3(short), 1.1(double), 2.2(float), true(boolean)"
        );
        assertEquals("SELECT 1 , 2, 3, 1.1, 2.2, 1", MybatisLogParser.parse(input));
    }
}

