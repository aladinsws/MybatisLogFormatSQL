package com.aladinsws.plugins;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SqlFormatterTest {

    // -----------------------------------------------------------------------
    // Helper: trim every line so indentation differences don't matter
    //         for simple assertions, but we DO assert exact multi-line layout
    //         for the structured tests below.
    // -----------------------------------------------------------------------

    private static String fmt(String sql) {
        return SqlFormatter.format(sql);
    }

    // -----------------------------------------------------------------------
    // Edge cases: empty / blank input
    // -----------------------------------------------------------------------

    @Test
    public void testEmptyInputReturnsEmpty() {
        assertEquals("", fmt(""));
    }

    @Test
    public void testBlankInputReturnsEmpty() {
        assertEquals("", fmt("   "));
    }

    @Test
    public void testAlreadyTrimmedInput() {
        // Single token — no newlines should be added
        assertEquals("1", fmt("1"));
    }

    // -----------------------------------------------------------------------
    // Simple SELECT
    // -----------------------------------------------------------------------

    @Test
    public void testSelectStar() {
        String expected = """
                SELECT
                  *
                FROM users""";
        assertEquals(expected, fmt("SELECT * FROM users"));
    }

    @Test
    public void testSelectWithWhere() {
        String expected = """
                SELECT
                  *
                FROM users
                WHERE id = 1""";
        assertEquals(expected, fmt("SELECT * FROM users WHERE id = 1"));
    }

    @Test
    public void testSelectMultipleColumns() {
        String expected = """
                SELECT
                  id,
                  name,
                  email
                FROM users""";
        assertEquals(expected, fmt("SELECT id, name, email FROM users"));
    }

    @Test
    public void testSelectWithAlias() {
        String expected = """
                SELECT
                  u.id,
                  u.name
                FROM users u""";
        assertEquals(expected, fmt("SELECT u.id, u.name FROM users u"));
    }

    // -----------------------------------------------------------------------
    // Dot notation and cast operator
    // -----------------------------------------------------------------------

    @Test
    public void testDotNotationNoSpaces() {
        // table.column must never have spaces around the dot
        String result = fmt("SELECT t.col FROM t");
        assertTrue("dot notation should have no spaces", result.contains("t.col"));
        assertFalse(result.contains("t .col"));
        assertFalse(result.contains("t. col"));
    }

    @Test
    public void testPostgresCastNoSpaces() {
        // value::type must not have spaces around ::
        String result = fmt("SELECT x::numeric FROM t");
        assertTrue(result.contains("x::numeric"));
        assertFalse(result.contains("x ::"));
        assertFalse(result.contains(":: numeric"));
    }

    // -----------------------------------------------------------------------
    // Clause keywords on separate lines
    // -----------------------------------------------------------------------

    @Test
    public void testGroupByOnNewLine() {
        String result = fmt("SELECT name, COUNT(*) FROM orders GROUP BY name");
        assertTrue(result.contains("\nGROUP BY"));
    }

    @Test
    public void testOrderByOnNewLine() {
        String result = fmt("SELECT id FROM t ORDER BY id DESC");
        assertTrue(result.contains("\nORDER BY"));
    }

    @Test
    public void testHavingOnNewLine() {
        String result = fmt("SELECT name, COUNT(*) FROM t GROUP BY name HAVING COUNT(*) > 1");
        assertTrue(result.contains("\nHAVING"));
    }

    @Test
    public void testOrderByColumnsInline() {
        // ORDER BY columns should stay on the same line, not each on its own
        String result = fmt("SELECT id FROM t ORDER BY a, b DESC, c ASC");
        assertTrue("order by columns should be inline",
                result.contains("ORDER BY a, b DESC, c ASC"));
    }

    @Test
    public void testGroupByColumnsInline() {
        String result = fmt("SELECT a, b FROM t GROUP BY a, b");
        assertTrue(result.contains("GROUP BY a, b"));
    }

    // -----------------------------------------------------------------------
    // JOIN variants
    // -----------------------------------------------------------------------

    @Test
    public void testInnerJoin() {
        String result = fmt("SELECT * FROM a INNER JOIN b ON a.id = b.id");
        assertTrue(result.contains("\nINNER JOIN b ON a.id = b.id"));
    }

    @Test
    public void testLeftJoin() {
        String result = fmt("SELECT * FROM a LEFT JOIN b ON a.id = b.id");
        assertTrue(result.contains("\nLEFT JOIN b ON a.id = b.id"));
    }

    @Test
    public void testRightJoin() {
        String result = fmt("SELECT * FROM a RIGHT JOIN b ON a.id = b.id");
        assertTrue(result.contains("\nRIGHT JOIN b ON a.id = b.id"));
    }

    @Test
    public void testFullOuterJoin() {
        String result = fmt("SELECT * FROM a FULL OUTER JOIN b ON a.id = b.id");
        assertTrue(result.contains("\nFULL OUTER JOIN b ON a.id = b.id"));
    }

    @Test
    public void testCrossJoin() {
        String result = fmt("SELECT * FROM a CROSS JOIN b");
        assertTrue(result.contains("\nCROSS JOIN b"));
    }

    @Test
    public void testMultipleJoins() {
        String result = fmt("""
                SELECT * FROM a \
                JOIN b ON a.id = b.aid \
                JOIN c ON b.id = c.bid""");
        // Both JOINs must start on their own line
        long joinLines = result.lines().filter(l -> l.trim().startsWith("JOIN")).count();
        assertEquals(2, joinLines);
    }

    // -----------------------------------------------------------------------
    // CASE expression
    // -----------------------------------------------------------------------

    @Test
    public void testCaseWhenFormatted() {
        String result = fmt(
                "SELECT CASE WHEN x > 0 THEN 'pos' WHEN x < 0 THEN 'neg' ELSE 'zero' END FROM t");
        assertTrue("CASE should be in output", result.contains("CASE"));
        assertTrue("WHEN should be on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("WHEN x > 0")));
        assertTrue("ELSE should be on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("ELSE")));
        assertTrue("END should be on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("END")));
    }

    @Test
    public void testCaseEndIndentLessThanWhen() {
        String result = fmt("SELECT CASE WHEN a = 1 THEN 'one' ELSE 'other' END AS label FROM t");
        // Find WHEN and END lines; END must be indented less than WHEN
        int whenIndent = result.lines()
                .filter(l -> l.stripLeading().startsWith("WHEN"))
                .mapToInt(l -> l.indexOf(l.stripLeading()))
                .findFirst().orElse(-1);
        int endIndent = result.lines()
                .filter(l -> l.stripLeading().startsWith("END"))
                .mapToInt(l -> l.indexOf(l.stripLeading()))
                .findFirst().orElse(-1);
        assertTrue("END indent (" + endIndent + ") must be less than WHEN indent (" + whenIndent + ")",
                endIndent < whenIndent);
    }

    @Test
    public void testCaseInsideFunctionCallStaysInline() {
        // CASE inside a function call must NOT trigger newlines
        String result = fmt("SELECT COALESCE(CASE WHEN x IS NULL THEN 0 ELSE x END, 0) FROM t");
        // The whole COALESCE(...) should be on a single line within SELECT
        long caseLines = result.lines().filter(l -> l.trim().startsWith("WHEN")).count();
        // CASE is inside COALESCE (inline paren) — no WHEN lines
        assertEquals("CASE inside function call should be inline", 0, caseLines);
    }

    // -----------------------------------------------------------------------
    // Window functions
    // -----------------------------------------------------------------------

    @Test
    public void testWindowFunctionOverInline() {
        String result = fmt("""
                SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn FROM emp""");
        // The OVER clause must not produce its own line
        assertFalse("OVER should not start its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("OVER")));
        assertTrue("OVER (...) should appear on the ROW_NUMBER line",
                result.lines().anyMatch(l -> l.contains("ROW_NUMBER()") && l.contains("OVER")));
    }

    @Test
    public void testPartitionByInline() {
        String result = fmt("""
                SELECT RANK() OVER (PARTITION BY dept ORDER BY salary) AS rnk FROM emp""");
        // PARTITION BY inside OVER should stay inline
        assertFalse(result.lines().anyMatch(l -> l.trim().startsWith("PARTITION BY")));
    }

    // -----------------------------------------------------------------------
    // Function calls kept inline
    // -----------------------------------------------------------------------

    @Test
    public void testNestedFunctionCallsInline() {
        String result = fmt("SELECT ROUND(AVG(price), 2) FROM products");
        assertTrue("nested functions should be inline",
                result.lines().anyMatch(l -> l.contains("ROUND(AVG(price), 2)")));
    }

    @Test
    public void testCoalesceInline() {
        String result = fmt("SELECT COALESCE(a, b, 0) FROM t");
        assertTrue(result.lines().anyMatch(l -> l.contains("COALESCE(a, b, 0)")));
    }

    @Test
    public void testCountStarInline() {
        String result = fmt("SELECT COUNT(*) FROM t");
        assertTrue(result.contains("COUNT(*)"));
    }

    // -----------------------------------------------------------------------
    // String literals
    // -----------------------------------------------------------------------

    @Test
    public void testStringLiteralPreserved() {
        String result = fmt("SELECT * FROM t WHERE name = 'Alice'");
        assertTrue(result.contains("'Alice'"));
    }

    @Test
    public void testStringLiteralWithEmbeddedSpaces() {
        String result = fmt("SELECT * FROM t WHERE note = 'hello world'");
        assertTrue(result.contains("'hello world'"));
    }

    @Test
    public void testStringLiteralWithEscapedQuote() {
        String result = fmt("SELECT * FROM t WHERE s = 'it''s fine'");
        assertTrue(result.contains("'it''s fine'"));
    }

    // -----------------------------------------------------------------------
    // Semicolon
    // -----------------------------------------------------------------------

    @Test
    public void testSemicolonPreserved() {
        String result = fmt("SELECT 1;");
        assertTrue(result.endsWith(";"));
    }

    // -----------------------------------------------------------------------
    // Keyword normalisation
    // -----------------------------------------------------------------------

    @Test
    public void testKeywordsUppercased() {
        String result = fmt("select id from users where id = 1");
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("WHERE"));
    }

    @Test
    public void testMixedCaseKeywords() {
        String result = fmt("Select Id From Users Where Id = 1");
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("WHERE"));
    }

    // -----------------------------------------------------------------------
    // UNION / set operators
    // -----------------------------------------------------------------------

    @Test
    public void testUnionOnNewLine() {
        String result = fmt("SELECT id FROM a UNION SELECT id FROM b");
        assertTrue(result.contains("\nUNION\n"));
    }

    @Test
    public void testUnionAllOnNewLine() {
        String result = fmt("SELECT id FROM a UNION ALL SELECT id FROM b");
        assertTrue(result.contains("\nUNION ALL\n"));
    }

    // -----------------------------------------------------------------------
    // CTEs
    // -----------------------------------------------------------------------

    @Test
    public void testSingleCteIndented() {
        String result = fmt("WITH cte AS (SELECT id FROM t) SELECT * FROM cte");
        // The SELECT inside the CTE must be indented
        assertTrue("CTE body SELECT should be indented",
                result.lines().anyMatch(l -> l.startsWith("  SELECT")));
        // The outer SELECT must not be indented
        assertTrue("outer SELECT should be at column 0",
                result.lines().anyMatch(l -> l.startsWith("SELECT")));
    }

    @Test
    public void testMultipleCtesCommaSeparated() {
        String result = fmt(
                "WITH a AS (SELECT 1 AS n), b AS (SELECT 2 AS n) SELECT * FROM a, b");
        // The comma between CTEs must be on the same line as the closing )
        assertTrue("comma after CTE closing paren",
                result.contains("),\n"));
        // Second CTE should start on its own line
        assertTrue(result.lines().anyMatch(l -> l.trim().startsWith("b AS")));
    }

    @Test
    public void testCteBodyColumnsIndentedDeeper() {
        String result = fmt("WITH cte AS (SELECT id, name FROM t) SELECT * FROM cte");
        // Columns inside the CTE SELECT should be at 4 spaces
        assertTrue("CTE columns at 4-space indent",
                result.lines().anyMatch(l -> l.startsWith("    id,")));
    }

    // -----------------------------------------------------------------------
    // Derived table (subquery in FROM)
    // -----------------------------------------------------------------------

    @Test
    public void testDerivedTableFormatted() {
        String result = fmt("SELECT * FROM (SELECT id, name FROM users WHERE active = 1) sub");
        // The inner SELECT should be indented
        assertTrue("subquery SELECT should be indented",
                result.lines().anyMatch(l -> l.startsWith("  SELECT")));
    }

    // -----------------------------------------------------------------------
    // INSERT / UPDATE / DELETE
    // -----------------------------------------------------------------------

    @Test
    public void testUpdateSetOnNewLine() {
        String result = fmt("UPDATE t SET col1 = 1, col2 = 'x' WHERE id = 99");
        assertTrue(result.contains("\nSET"));
        assertTrue(result.contains("\nWHERE"));
    }

    // -----------------------------------------------------------------------
    // Full complex query (mirrors the user's example)
    // -----------------------------------------------------------------------

    @Test
    public void testComplexCteQuery() {
        String sql = """
                WITH ranked AS (SELECT o.id, o.amount, \
                ROW_NUMBER() OVER (PARTITION BY o.customer_id ORDER BY o.created_at DESC) AS rn \
                FROM orders o WHERE o.amount > 0) \
                SELECT id, amount FROM ranked WHERE rn = 1 ORDER BY amount DESC""";

        String result = fmt(sql);

        // CTE structure
        assertTrue("WITH on first line", result.startsWith("WITH"));
        assertTrue("CTE body SELECT indented", result.lines().anyMatch(l -> l.startsWith("  SELECT")));
        assertTrue("CTE closing paren on own line", result.contains("\n)"));
        // OVER inline
        assertFalse("OVER must not start its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("OVER")));
        // Outer SELECT at column 0
        assertTrue("outer SELECT at column 0", result.lines().anyMatch(l -> l.equals("SELECT")));
        // ORDER BY
        assertTrue(result.contains("\nORDER BY amount DESC"));
    }

    @Test
    public void testCaseExpressionInCte() {
        String sql = """
                WITH tiers AS (SELECT id, \
                CASE WHEN amount > 1000 THEN 'high' WHEN amount > 100 THEN 'mid' ELSE 'low' END AS tier \
                FROM orders) SELECT * FROM tiers""";

        String result = fmt(sql);

        // CASE must be multi-line inside the CTE
        assertTrue("WHEN should be on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("WHEN amount > 1000")));
        assertTrue("ELSE on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("ELSE")));
        assertTrue("END on its own line",
                result.lines().anyMatch(l -> l.trim().startsWith("END")));
    }

}

