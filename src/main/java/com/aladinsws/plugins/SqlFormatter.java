package com.aladinsws.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlFormatter {

    private static final int INDENT = 2;

    /**
     * Clause keywords that force a new line (at SQL level, not inside function calls).
     */
    private static final Set<String> CLAUSE_STARTERS = Set.of(
            "WITH", "SELECT", "FROM",
            "FULL OUTER JOIN", "LEFT OUTER JOIN", "RIGHT OUTER JOIN",
            "INNER JOIN", "CROSS JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "JOIN",
            "WHERE", "HAVING", "GROUP BY", "ORDER BY",
            "LIMIT", "OFFSET",
            "UNION ALL", "UNION", "INTERSECT ALL", "INTERSECT", "EXCEPT ALL", "EXCEPT",
            "SET"
    );

    /**
     * Only these keywords before "(" open a SQL block (subquery / CTE body).
     * Everything else (including function-like keywords such as COALESCE, CASE
     * clauses, WHERE grouping, etc.) is treated as an inline paren.
     */
    private static final Set<String> BLOCK_OPENING_KEYWORDS = Set.of("AS", "FROM", "EXISTS");

    /**
     * JOIN clause keywords — when ON follows one of these the AND/OR conditions
     * should be aligned under the first condition rather than at the generic indent.
     */
    private static final Set<String> JOIN_KEYWORDS = Set.of(
            "JOIN", "INNER JOIN",
            "LEFT JOIN", "RIGHT JOIN", "FULL JOIN",
            "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN",
            "CROSS JOIN"
    );

    /**
     * Inline-paren keywords that should still have a space before "(" for
     * readability: {@code OVER (…)}, {@code IN (…)}, etc.
     */
    private static final Set<String> SPACE_BEFORE_INLINE_PAREN = Set.of(
            "OVER", "IN", "NOT IN", "ANY", "ALL", "SOME"
    );

    /**
     * All single-word SQL keywords (used to distinguish keywords from identifiers).
     */
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "JOIN", "ON", "AND", "OR", "NOT",
            "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS",
            "GROUP", "ORDER", "HAVING", "LIMIT", "OFFSET", "BY",
            "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT", "TOP",
            "WITH", "AS", "SET",
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "IN", "IS", "NULL", "BETWEEN", "LIKE", "EXISTS",
            "OVER", "PARTITION", "ROWS", "RANGE", "PRECEDING", "FOLLOWING",
            "CURRENT", "ROW", "UNBOUNDED",
            "INSERT", "INTO", "VALUES", "UPDATE", "DELETE",
            "CREATE", "TABLE", "INDEX", "VIEW", "DROP", "ALTER",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT",
            "BEGIN", "COMMIT", "ROLLBACK", "ASC", "DESC", "NULLS",
            "FIRST", "LAST", "INTERVAL", "EXTRACT", "CAST", "COALESCE",
            "NULLIF", "GREATEST", "LEAST", "FILTER", "WITHIN", "LATERAL",
            "TRUE", "FALSE", "UNKNOWN", "DEFAULT", "USING"
    );

    /**
     * Multi-word keywords to merge (longest first).
     */
    private static final String[][] MULTI_WORD_KW = {
            {"LEFT", "OUTER", "JOIN"},
            {"RIGHT", "OUTER", "JOIN"},
            {"FULL", "OUTER", "JOIN"},
            {"UNION", "ALL"},
            {"INTERSECT", "ALL"},
            {"EXCEPT", "ALL"},
            {"ORDER", "BY"},
            {"GROUP", "BY"},
            {"PARTITION", "BY"},
            {"NOT", "IN"},
            {"NOT", "BETWEEN"},
            {"NOT", "LIKE"},
            {"NOT", "EXISTS"},
            {"NOT", "NULL"},
            {"IS", "NOT"},
            {"IS", "NULL"},
            {"LEFT", "JOIN"},
            {"RIGHT", "JOIN"},
            {"FULL", "JOIN"},
            {"CROSS", "JOIN"},
            {"INNER", "JOIN"},
    };

    // ── Token types & record ──────────────────────────────────────────────────

    private enum TType {KEYWORD, IDENT, STRING, NUMBER, COMMA, SEMI, LPAREN, RPAREN, OP}

    private record Token(TType type, String val) {
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────

    private static final Pattern LEX = Pattern.compile(
            "'(?:[^']|'')*'" +                  // single-quoted string literal
                    "|--[^\\n]*" +                      // line comment → skip
                    "|/\\*[\\s\\S]*?\\*/" +             // block comment → skip
                    "|\\d+(?:\\.\\d*)?" +               // number
                    "|[A-Za-z_][A-Za-z0-9_$#]*" +      // identifier / keyword
                    "|::|\\|\\||<>|!=|<=|>=" +          // 2-char operators
                    "|[=<>+\\-*/%.!|&^~@#]" +           // 1-char operators
                    "|[(),;]" +                         // punctuation
                    "|\\s+"                             // whitespace (discarded)
    );

    @NotNull
    private static List<Token> tokenize(@NotNull String sql) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = LEX.matcher(sql);
        while (m.find()) {
            String v = m.group();
            if (shouldSkipToken(v)) {
                continue;
            }
            TType t = determineTokenType(v);
            tokens.add(new Token(t, v));
        }
        return tokens;
    }

    private static boolean shouldSkipToken(@NotNull String token) {
        char first = token.charAt(0);
        return Character.isWhitespace(first)
                || (first == '-' && token.startsWith("--"))
                || (first == '/' && token.startsWith("/*"));
    }

    @NotNull
    private static TType determineTokenType(@NotNull String token) {
        char first = token.charAt(0);
        if (first == '\'') {
            return TType.STRING;
        } else if (Character.isDigit(first)) {
            return TType.NUMBER;
        } else if (token.equals(",")) {
            return TType.COMMA;
        } else if (token.equals(";")) {
            return TType.SEMI;
        } else if (token.equals("(")) {
            return TType.LPAREN;
        } else if (token.equals(")")) {
            return TType.RPAREN;
        } else if (Character.isLetter(first) || first == '_') {
            return KEYWORDS.contains(token.toUpperCase()) ? TType.KEYWORD : TType.IDENT;
        } else {
            return TType.OP;
        }
    }

    // ── Multi-word keyword merging ─────────────────────────────────────────────

    @NotNull
    private static List<Token> mergeKeywords(@NotNull List<Token> tokens) {
        List<Token> list = new ArrayList<>(tokens);
        // Try each pattern (sorted longest-first above)
        for (String[] kw : MULTI_WORD_KW) {
            int i = 0;
            while (i <= list.size() - kw.length) {
                if (matchesKeywordPattern(list, i, kw)) {
                    mergeTokensAt(list, i, kw);
                    // don't advance i — check again from same position
                } else {
                    i++;
                }
            }
        }
        return list;
    }

    private static boolean matchesKeywordPattern(@NotNull List<Token> list, int startIndex, @NotNull String[] pattern) {
        for (int j = 0; j < pattern.length; j++) {
            Token tok = list.get(startIndex + j);
            boolean isKeywordOrIdent = tok.type() == TType.KEYWORD || tok.type() == TType.IDENT;
            if (!isKeywordOrIdent || !tok.val().equalsIgnoreCase(pattern[j])) {
                return false;
            }
        }
        return true;
    }

    private static void mergeTokensAt(@NotNull List<Token> list, int index, @NotNull String[] kwPattern) {
        String merged = String.join(" ", kwPattern);
        list.subList(index, index + kwPattern.length).clear();
        list.add(index, new Token(TType.KEYWORD, merged.toUpperCase()));
    }

    // ── Block context ─────────────────────────────────────────────────────────

    /**
     * Tracks state for each SQL block (top-level query or CTE body).
     */
    private static class BlockCtx {
        final int indent;       // indentation of clause keywords in this block
        String clause;          // last clause keyword seen (SELECT, FROM, WHERE, …)
        int inlineDepth;        // depth of INLINE parens nested inside this block
        boolean betweenPending; // true after BETWEEN — the next AND belongs to BETWEEN … AND, not a logical AND
        int joinOnConditionIndent; // >=0 when inside a JOIN's ON clause: column where first condition starts

        BlockCtx(int indent) {
            this.indent = indent;
            this.clause = null;
            this.inlineDepth = 0;
            this.betweenPending = false;
            this.joinOnConditionIndent = -1;
        }

        boolean atSqlLevel() {
            return inlineDepth == 0;
        }
    }

    // ── Renderer ──────────────────────────────────────────────────────────────

    private SqlFormatter() {
    }

    @NotNull
    public static String format(@NotNull String sql) {
        String s = sql.trim();
        if (s.isEmpty()) return s;
        List<Token> tokens = mergeKeywords(tokenize(s));
        return render(tokens);
    }

    @NotNull
    private static String render(@NotNull List<Token> tokens) {
        StringBuilder sb = new StringBuilder(tokens.size() * 6);
        Deque<Boolean> parenKindStack = new ArrayDeque<>();
        Deque<BlockCtx> blockStack = new ArrayDeque<>();
        blockStack.push(new BlockCtx(0));
        Deque<Integer> caseWhenIndentStack = new ArrayDeque<>();
        boolean[] needSpace = {false};

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            BlockCtx cur = blockStack.peek();

            switch (t.type()) {
                case KEYWORD:
                    handleKeywordToken(sb, t, cur, caseWhenIndentStack, needSpace);
                    break;
                case IDENT, NUMBER, STRING:
                    emit(sb, t.val(), needSpace[0]);
                    needSpace[0] = true;
                    break;
                case OP:
                    needSpace[0] = handleOperatorToken(sb, t, needSpace[0]);
                    break;
                case COMMA:
                    handleCommaToken(sb, cur, needSpace);
                    break;
                case LPAREN:
                    needSpace[0] = handleLeftParen(sb, tokens, i, cur, parenKindStack, blockStack, needSpace[0]);
                    break;
                case RPAREN:
                    needSpace[0] = handleRightParen(sb, cur, parenKindStack, blockStack);
                    break;
                case SEMI:
                    sb.append(';');
                    needSpace[0] = false;
                    break;
            }
        }

        return sb.toString().trim();
    }

    private static void handleKeywordToken(StringBuilder sb, Token t, BlockCtx cur,
                                           Deque<Integer> caseWhenIndentStack,
                                           boolean[] needSpace) {
        String upper = t.val().toUpperCase();
        if (cur.atSqlLevel()) {
            handleSqlLevelKeyword(sb, upper, cur, caseWhenIndentStack, needSpace);
        } else {
            handleInlineKeyword(sb, upper, caseWhenIndentStack, needSpace);
        }
    }

    private static void handleSqlLevelKeyword(StringBuilder sb, String upper, BlockCtx cur,
                                              Deque<Integer> caseWhenIndentStack,
                                              boolean[] needSpace) {
        if (CLAUSE_STARTERS.contains(upper)) {
            cur.betweenPending = false;
            handleClauseKeyword(sb, cur, upper);
            needSpace[0] = !upper.equals("SELECT");
        } else if (upper.equals("CASE")) {
            caseWhenIndentStack.push(cur.indent + 2 * INDENT);
            emit(sb, "CASE", needSpace[0]);
            needSpace[0] = true;
        } else if (upper.equals("WHEN") || upper.equals("ELSE")) {
            handleWhenElseKeyword(sb, upper, caseWhenIndentStack, needSpace);
        } else if (upper.equals("THEN")) {
            handleThenKeyword(sb, upper, caseWhenIndentStack, needSpace);
        } else if (upper.equals("END")) {
            handleEndKeyword(sb, upper, caseWhenIndentStack, needSpace);
        } else if (upper.equals("AND") || upper.equals("OR")) {
            handleAndOrKeyword(sb, upper, cur, needSpace);
        } else if (upper.equals("BETWEEN") || upper.equals("NOT BETWEEN")) {
            cur.betweenPending = true;
            emit(sb, upper, needSpace[0]);
            needSpace[0] = true;
        } else if (upper.equals("ON") && JOIN_KEYWORDS.contains(cur.clause)) {
            // ON after a JOIN: emit it then record where the first condition starts
            // so subsequent AND/OR can be aligned to that column.
            emit(sb, "ON", needSpace[0]);
            // +1 for the space that will precede the next token
            cur.joinOnConditionIndent = getCurrentLineLength(sb) + 1;
            needSpace[0] = true;
        } else {
            emit(sb, upper, needSpace[0]);
            needSpace[0] = true;
        }
    }

    private static void handleWhenElseKeyword(StringBuilder sb, String upper,
                                              Deque<Integer> caseWhenIndentStack,
                                              boolean[] needSpace) {
        if (hasCaseWhenContext(caseWhenIndentStack)) {
            Integer peekValue = caseWhenIndentStack.peek();
            if (peekValue != null) {
                emitNewlineIndent(sb, peekValue);
                sb.append(upper);
            }
        } else {
            emit(sb, upper, needSpace[0]);
        }
        needSpace[0] = true;
    }

    private static void handleThenKeyword(StringBuilder sb, String upper,
                                          Deque<Integer> caseWhenIndentStack,
                                          boolean[] needSpace) {
        if (!hasCaseWhenContext(caseWhenIndentStack)) {
            emit(sb, upper, needSpace[0]);
        } else {
            emit(sb, "THEN", needSpace[0]);
        }
        needSpace[0] = true;
    }

    private static void handleEndKeyword(StringBuilder sb, String upper,
                                         Deque<Integer> caseWhenIndentStack,
                                         boolean[] needSpace) {
        if (hasCaseWhenContext(caseWhenIndentStack)) {
            Integer popValue = caseWhenIndentStack.pop();
            if (popValue != null) {
                emitNewlineIndent(sb, popValue - INDENT);
                sb.append("END");
            }
        } else {
            emit(sb, upper, needSpace[0]);
        }
        needSpace[0] = true;
    }

    /**
     * Emits AND / OR on a new indented line so each WHERE/HAVING/ON condition
     * is easy to read at a glance.
     *
     * <ul>
     *   <li>The AND that belongs to {@code BETWEEN x AND y} stays inline.</li>
     *   <li>Inside a JOIN's ON clause, AND/OR are aligned to the column where
     *       the first condition started (right after {@code ON }).</li>
     *   <li>Everywhere else, the standard {@code cur.indent + INDENT} is used.</li>
     * </ul>
     */
    private static void handleAndOrKeyword(StringBuilder sb, String upper, BlockCtx cur,
                                           boolean[] needSpace) {
        if (cur.betweenPending && upper.equals("AND")) {
            // This AND closes a BETWEEN expression — keep it inline
            cur.betweenPending = false;
            emit(sb, upper, needSpace[0]);
        } else if (cur.joinOnConditionIndent >= 0) {
            // Inside a JOIN ON clause — align with the first condition
            emitNewlineIndent(sb, cur.joinOnConditionIndent);
            sb.append(upper);
        } else {
            // Logical AND / OR — put it on its own indented line
            emitNewlineIndent(sb, cur.indent + INDENT);
            sb.append(upper);
        }
        needSpace[0] = true;
    }

    private static boolean hasCaseWhenContext(Deque<Integer> caseWhenIndentStack) {
        if (caseWhenIndentStack.isEmpty()) {
            return false;
        }
        Integer value = caseWhenIndentStack.peek();
        return value != null && value >= 0;
    }

    private static void handleInlineKeyword(StringBuilder sb, String upper,
                                            Deque<Integer> caseWhenIndentStack,
                                            boolean[] needSpace) {
        if (upper.equals("CASE")) {
            caseWhenIndentStack.push(-1);
        } else if (upper.equals("END") && !caseWhenIndentStack.isEmpty()) {
            Integer peekValue = caseWhenIndentStack.peek();
            if (peekValue != null && peekValue == -1) {
                caseWhenIndentStack.pop();
            }
        }
        emit(sb, upper, needSpace[0]);
        needSpace[0] = true;
    }

    private static boolean handleOperatorToken(StringBuilder sb, Token t, boolean needSpace) {
        String v = t.val();
        if (v.equals(".") || v.equals("::")) {
            sb.append(v);
            return false;
        } else {
            emit(sb, v, needSpace);
            return true;
        }
    }

    private static void handleCommaToken(StringBuilder sb, BlockCtx cur, boolean[] needSpace) {
        if (cur.atSqlLevel()) {
            if ("SELECT".equals(cur.clause)) {
                sb.append(',');
                emitNewlineIndent(sb, cur.indent + INDENT);
                needSpace[0] = false;
            } else if ("WITH".equals(cur.clause)) {
                sb.append(',');
                sb.append('\n');
                needSpace[0] = false;
            } else {
                sb.append(", ");
                needSpace[0] = false;
            }
        } else {
            sb.append(", ");
            needSpace[0] = false;
        }
    }

    private static boolean handleLeftParen(StringBuilder sb, List<Token> tokens, int i,
                                           BlockCtx cur, Deque<Boolean> parenKindStack,
                                           Deque<BlockCtx> blockStack, boolean needSpace) {
        Token prev = i > 0 ? tokens.get(i - 1) : null;
        boolean isBlock = isBlockParen(prev);
        parenKindStack.push(isBlock);
        if (isBlock) {
            blockStack.push(new BlockCtx(cur.indent + INDENT));
            emit(sb, "(", needSpace);
        } else {
            cur.inlineDepth++;
            boolean spaceBeforeParen = needSpace && prev != null
                    && prev.type() == TType.KEYWORD
                    && SPACE_BEFORE_INLINE_PAREN.contains(prev.val().toUpperCase());
            if (spaceBeforeParen) {
                emit(sb, "(", true);
            } else {
                sb.append('(');
            }
        }
        return false;
    }

    private static boolean handleRightParen(StringBuilder sb, BlockCtx cur,
                                            Deque<Boolean> parenKindStack,
                                            Deque<BlockCtx> blockStack) {
        if (parenKindStack.isEmpty()) {
            sb.append(')');
        } else {
            boolean wasBlock = parenKindStack.pop();
            if (wasBlock) {
                blockStack.pop();
                BlockCtx parent = blockStack.peek();
                if (parent != null) {
                    emitNewlineIndent(sb, parent.indent);
                }
            } else {
                cur.inlineDepth--;
            }
            sb.append(')');
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Emits a clause keyword on a new line (or at position 0 if sb is empty).
     * For SELECT, also emits a trailing newline+indent so columns start indented.
     */
    private static void handleClauseKeyword(StringBuilder sb, BlockCtx cur, String upper) {
        if (!sb.isEmpty()) {
            emitNewlineIndent(sb, cur.indent);
        }
        cur.clause = upper;
        cur.joinOnConditionIndent = -1; // reset whenever a new clause starts
        sb.append(upper);
        if (upper.equals("SELECT")) {
            // Column list follows — each on its own line
            emitNewlineIndent(sb, cur.indent + INDENT);
        }
    }

    /**
     * Returns the number of characters on the current (last) line of {@code sb},
     * i.e. characters since the most-recent {@code '\n'} (or from the start if
     * there is no newline yet).  Used to compute column-aligned indentation for
     * JOIN ON conditions.
     */
    private static int getCurrentLineLength(StringBuilder sb) {
        int lastNewline = sb.lastIndexOf("\n");
        return lastNewline < 0 ? sb.length() : sb.length() - lastNewline - 1;
    }

    /**
     * Appends a newline followed by {@code indent} spaces.
     */
    private static void emitNewlineIndent(StringBuilder sb, int indent) {
        sb.append('\n');
        sb.repeat(" ", Math.max(0, indent));
    }

    /**
     * Appends {@code val}, prefixed by a space when {@code needSpace} is true
     * and the last character already in {@code sb} is not a space, newline, or '('.
     */
    private static void emit(StringBuilder sb, String val, boolean needSpace) {
        if (needSpace && !sb.isEmpty()) {
            char last = sb.charAt(sb.length() - 1);
            if (last != ' ' && last != '\n' && last != '(') {
                sb.append(' ');
            }
        }
        sb.append(val);
    }

    /**
     * Returns {@code true} when the paren following {@code prev} opens a SQL
     * block (subquery / CTE body), or {@code false} when it is an inline paren
     * (function call, window OVER, IN list, arithmetic grouping).
     */
    private static boolean isBlockParen(@Nullable Token prev) {
        if (prev == null) return true;
        return switch (prev.type()) {
            case IDENT -> false;  // function call: name(
            case RPAREN -> false;  // chained call: func()()
            case LPAREN -> false;  // grouping: ((expr))
            case NUMBER -> false;  // unusual, treat inline
            case KEYWORD -> {
                String kw = prev.val().toUpperCase();
                // Explicitly inline keywords
                if (SPACE_BEFORE_INLINE_PAREN.contains(kw)) yield false;
                // Only a small set of keywords actually open subquery blocks
                yield BLOCK_OPENING_KEYWORDS.contains(kw);
            }
            default -> true;
        };
    }
}

