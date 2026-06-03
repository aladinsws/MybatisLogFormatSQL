package com.aladinsws.plugins;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        String input = """
                ==> Preparing: SELECT id FROM user WHERE id = ?
                ==> Parameters: 7(Integer)""";
        assertEquals("SELECT id FROM user WHERE id = 7", MybatisLogParser.parse(input));
    }

    @Test
    public void testMultipleEqualsArrowFormat() {
        String input = """
                ===> Preparing: SELECT id FROM t WHERE code = ?
                ===> Parameters: ABC(String)""";
        assertEquals("SELECT id FROM t WHERE code = 'ABC'", MybatisLogParser.parse(input));
    }

    // -----------------------------------------------------------------------
    // Case-insensitive keywords
    // -----------------------------------------------------------------------

    @Test
    public void testUpperCaseKeywords() {
        String input = """
                PREPARING: SELECT 1 FROM dual WHERE x = ?
                PARAMETERS: 5(Integer)""";
        assertEquals("SELECT 1 FROM dual WHERE x = 5", MybatisLogParser.parse(input));
    }

    @Test
    public void testMixedCaseKeywords() {
        String input = """
                Preparing: SELECT 1 WHERE a = ?
                parameters: z(String)""";
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
        String input = """
                Preparing: SELECT * FROM t WHERE id = ?\r
                Parameters: 42(Integer)""";
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

    // -----------------------------------------------------------------------
    // Long log sample console output
    // -----------------------------------------------------------------------

    @Test
    public void testLogLongMybatisSample() {
        String preparing = """
                SELECT o.id, o.order_no, o.status, o.total_amount, o.discount, o.is_deleted, \
                o.created_at, o.updated_at, c.id AS customer_id, c.name AS customer_name, \
                c.email, c.phone, c.vip_level, p.id AS product_id, p.name AS product_name, \
                p.sku, p.unit_price, p.stock_qty, od.quantity, od.unit_price AS ordered_price, \
                od.subtotal, s.id AS shipper_id, s.company_name, s.tracking_no, s.shipped_at, \
                s.estimated_delivery \
                FROM orders o \
                LEFT JOIN customers c ON c.id = o.customer_id \
                LEFT JOIN order_details od ON od.order_id = o.id \
                LEFT JOIN products p ON p.id = od.product_id \
                LEFT JOIN shippers s ON s.order_id = o.id \
                WHERE o.status = ? AND o.is_deleted = ? AND o.total_amount >= ? \
                AND o.discount <= ? AND o.created_at BETWEEN ? AND ? \
                AND c.vip_level IN (?, ?, ?) AND c.email LIKE ? AND p.sku = ? \
                AND p.stock_qty > ? AND od.quantity BETWEEN ? AND ? \
                AND s.shipped_at IS NOT NULL AND o.id != ? \
                ORDER BY o.created_at DESC LIMIT ? OFFSET ?""";

        String parameters = """
                ACTIVE(String), false(Boolean), 99.99(BigDecimal), 0.5(Double), \
                2024-01-01 00:00:00(String), 2024-12-31 23:59:59(String), \
                1(Integer), 2(Integer), 3(Integer), %john%(String), SKU-20240315(String), \
                0(Long), 1(Integer), 100(Integer), 9999(Long), 20(Integer), 0(Integer)""";

        String rawLog =
                "2024-03-15 10:23:45.123 DEBUG 12345 --- [main] c.e.mapper.OrderMapper.findOrders        : ==> " +
                        "Preparing: " + preparing + "\n" +
                        "2024-03-15 10:23:45.124 DEBUG 12345 --- [main] c.e.mapper.OrderMapper.findOrders        : " +
                        "==> Parameters: " + parameters + "\n" +
                        "2024-03-15 10:23:45.456 DEBUG 12345 --- [main] c.e.mapper.OrderMapper.findOrders        : " +
                        "<==      Total: 5";

        System.out.println("=== Raw MyBatis Log ===");
        for (String line : rawLog.split("\n")) {
            System.out.println(line);
        }

        String result = MybatisLogParser.parse(rawLog);

        System.out.println("\n=== Formatted SQL ===");
        System.out.println(result);

        assertNotNull(result);
        assertFalse("Should not return an error", result.startsWith("Error:"));
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

    @Test
    public void testComplexPostgresqlCTEWithWindowFunctions() {
        String preparing = """
                WITH ranked_orders AS \
                (SELECT o.order_id, o.customer_id, o.order_date, o.total_amount, c.customer_name, c.country, \
                ROW_NUMBER() OVER (PARTITION BY o.customer_id ORDER BY o.order_date DESC) as order_rank, \
                DENSE_RANK() OVER (PARTITION BY c.country ORDER BY o.total_amount DESC) as country_rank \
                FROM orders o JOIN customers c ON o.customer_id = c.customer_id \
                WHERE o.order_date >= CURRENT_DATE - INTERVAL '? years'), \
                customer_stats AS \
                (SELECT customer_id, COUNT(*) as total_orders, SUM(total_amount) as lifetime_value, \
                AVG(total_amount) as avg_order_value, MAX(order_date) as last_order_date FROM ranked_orders GROUP BY customer_id), \
                filtered_results AS \
                (SELECT ro.*, cs.total_orders, cs.lifetime_value, cs.avg_order_value, \
                CASE WHEN cs.lifetime_value > ? THEN ? WHEN cs.lifetime_value > ? THEN ? ELSE ? END as customer_tier, \
                LAG(ro.total_amount) OVER (PARTITION BY ro.customer_id ORDER BY ro.order_date) as previous_order_amount, \
                LEAD(ro.total_amount) OVER (PARTITION BY ro.customer_id ORDER BY ro.order_date) as next_order_amount \
                FROM ranked_orders ro JOIN customer_stats cs ON ro.customer_id = cs.customer_id WHERE ro.order_rank <= ?) \
                SELECT customer_id, customer_name, country, total_orders, lifetime_value, \
                ROUND(avg_order_value, ?) as avg_order_value, customer_tier, order_id, order_date, total_amount, \
                previous_order_amount, next_order_amount, \
                ROUND(((total_amount - COALESCE(previous_order_amount, total_amount)) / \
                COALESCE(previous_order_amount, total_amount) * ?)::numeric, ?) as order_growth_percent \
                FROM filtered_results WHERE lifetime_value > ? ORDER BY country, lifetime_value DESC, order_date DESC \
                LIMIT ? OFFSET ?""";

        String parameters = """
                2(Integer), 50000(BigDecimal), VIP(String), 10000(BigDecimal), Premium(String), Standard(String), \
                5(Integer), 2(Integer), 100(Double), 2(Integer), 5000(BigDecimal), 20(Integer), 0(Integer)""";

        String rawLog =
                "2024-03-15 14:32:18.567 DEBUG 54321 --- [scheduler] c.e.mapper.CustomerMapper.analyzeOrders       : " +
                        "==> Preparing: " + preparing + "\n" +
                        "2024-03-15 14:32:18.568 DEBUG 54321 --- [scheduler] c.e.mapper.CustomerMapper.analyzeOrders " +
                        "      : ==> Parameters: " + parameters + "\n" +
                        "2024-03-15 14:32:18.893 DEBUG 54321 --- [scheduler] c.e.mapper.CustomerMapper.analyzeOrders " +
                        "      : <==    Columns: customer_id, customer_name, country, total_orders, lifetime_value, " +
                        "avg_order_value, customer_tier, order_id, order_date, total_amount, previous_order_amount, " +
                        "next_order_amount, order_growth_percent\n" +
                        "2024-03-15 14:32:18.894 DEBUG 54321 --- [scheduler] c.e.mapper.CustomerMapper.analyzeOrders " +
                        "      : <==        Row: 1, Jane Smith, USA, 12, 125000.00, 10416.67, VIP, 1005, 2024-03-15, " +
                        "15000.00, 14500.00, null\n" +
                        "2024-03-15 14:32:18.895 DEBUG 54321 --- [scheduler] c.e.mapper.CustomerMapper.analyzeOrders " +
                        "      : <==      Total: 1";

        System.out.println("=== Complex PostgreSQL CTE Log ===");
        for (String line : rawLog.split("\n")) {
            System.out.println(line);
        }

        String result = MybatisLogParser.parse(rawLog);

        System.out.println("\n=== Formatted SQL ===");
        System.out.println(result);

        assertNotNull(result);
        assertFalse("Should not return an error", result.startsWith("Error:"));
        assertTrue("Should contain WITH clause", result.contains("WITH"));
        assertTrue("Should contain window functions", result.contains("ROW_NUMBER()"));
        assertTrue("Should contain CASE statement", result.contains("CASE"));
    }
}

