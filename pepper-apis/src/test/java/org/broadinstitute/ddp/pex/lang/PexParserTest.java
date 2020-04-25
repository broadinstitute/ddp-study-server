package org.broadinstitute.ddp.pex.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

public class PexParserTest {

    private static String EXPR_A = "user.studies[\"A1\"].forms[\"A2\"].questions[\"A3\"].answers.hasTrue()";
    private static String EXPR_B = "user.studies[\"B1\"].forms[\"B2\"].questions[\"B3\"].answers.hasTrue()";
    private static String EXPR_C = "user.studies[\"C1\"].forms[\"C2\"].questions[\"C3\"].answers.hasTrue()";

    private static String TREE_A = "(expr (query user . (study studies [ \"A1\" ]) . (form forms [ \"A2\" ]) . "
            + "(question questions [ \"A3\" ]) . answers . (predicate hasTrue ( ))))";
    private static String TREE_B = "(expr (query user . (study studies [ \"B1\" ]) . (form forms [ \"B2\" ]) . "
            + "(question questions [ \"B3\" ]) . answers . (predicate hasTrue ( ))))";
    private static String TREE_C = "(expr (query user . (study studies [ \"C1\" ]) . (form forms [ \"C2\" ]) . "
            + "(question questions [ \"C3\" ]) . answers . (predicate hasTrue ( ))))";

    @Test
    public void testParsing_tree() {
        PexParser parser = buildParser(EXPR_A);
        ParseTree tree = parser.pex();
        assertNotNull(tree);
    }

    @Test
    public void testWhitespace_ignore() {
        String expr = String.format("   ( \t  %s \n || \n %s    ) \t\r\n  &&   \r\n   %s", EXPR_A, EXPR_B, EXPR_C);
        String expected = String.format("(pex (expr (expr ( (expr %s || %s) )) && %s))", TREE_A, TREE_B, TREE_C);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testPrecedence_unaryHigherThanRelation() {
        String expr = "-1 < -2";
        String expected = "(pex (expr (expr - (expr 1)) < (expr - (expr 2))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testPrecedence_relationHigherThanEquality() {
        String expr = "1 < 2 == 4 >= 3";
        String expected = "(pex (expr (expr (expr 1) < (expr 2)) == (expr (expr 4) >= (expr 3))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testPrecedence_equalityHigherThanLogical() {
        String expr = "1 == 2 || 4 != 3";
        String expected = "(pex (expr (expr (expr 1) == (expr 2)) || (expr (expr 4) != (expr 3))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testPrecedence_ANDHigherThanOR() {
        String expr = String.format("%s || %s && %s", EXPR_A, EXPR_B, EXPR_C);
        String expected = String.format("(pex (expr %s || (expr %s && %s)))", TREE_A, TREE_B, TREE_C);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testPrecedence_logicalNotHigherThanOtherLogicalOperators() {
        String expr = "!true && !false || !true";
        String expected = "(pex (expr (expr (expr ! (expr true)) && (expr ! (expr false))) || (expr ! (expr true))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testAssociativity_unaryRightToLeft() {
        String expr = "!!!true";
        String expected = "(pex (expr ! (expr ! (expr ! (expr true)))))";
        testParserTreeOutput(expr, expected);

        expr = "---25";
        expected = "(pex (expr - (expr - (expr - (expr 25)))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testAssociativity_relationLeftToRight() {
        String expr = "1 < 2 < 3";
        String expected = "(pex (expr (expr (expr 1) < (expr 2)) < (expr 3)))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testAssociativity_equalityLeftToRight() {
        String expr = "1 == 2 == 3";
        String expected = "(pex (expr (expr (expr 1) == (expr 2)) == (expr 3)))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testAssociativity_ANDLeftToRight() {
        String expr = String.format("%s && %s && %s", EXPR_A, EXPR_B, EXPR_C);
        String expected = String.format("(pex (expr (expr %s && %s) && %s))", TREE_A, TREE_B, TREE_C);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testAssociativity_ORLeftToRight() {
        String expr = String.format("%s || %s || %s", EXPR_A, EXPR_B, EXPR_C);
        String expected = String.format("(pex (expr (expr %s || %s) || %s))", TREE_A, TREE_B, TREE_C);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testNot_simpleExpr() {
        String expr = "!true";
        String expected = "(pex (expr ! (expr true)))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testNot_whitespaceNotSignificant() {
        String expr = "! true && !\t\r\n    false";
        String expected = "(pex (expr (expr ! (expr true)) && (expr ! (expr false))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testGrouping() {
        String expr = String.format("(%s)", EXPR_A);
        String expected = String.format("(pex (expr ( %s )))", TREE_A);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testGrouping_changesAssociativity() {
        String expr = String.format("(%s || %s) && %s", EXPR_A, EXPR_B, EXPR_C);
        String expected = String.format("(pex (expr (expr ( (expr %s || %s) )) && %s))", TREE_A, TREE_B, TREE_C);
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testLiteral() {
        String expr = "true";
        String expected = "(pex (expr true))";
        testParserTreeOutput(expr, expected);

        expr = "false";
        expected = "(pex (expr false))";
        testParserTreeOutput(expr, expected);

        expr = "125";
        expected = "(pex (expr 125))";
        testParserTreeOutput(expr, expected);

        expr = "\"abc\"";
        expected = "(pex (expr \"abc\"))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testQuery_defaultLatestAnswerQuery() {
        String expr = "user.studies[\"A1\"].forms[\"A2\"].questions[\"A3\"].answers.hasFalse()";
        String expected = "(pex (expr (query user . (study studies [ \"A1\" ]) . (form forms [ \"A2\" ]) . "
                + "(question questions [ \"A3\" ]) . answers . (predicate hasFalse ( )))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testQuery_testQuery_defaultLatestAnswerQuery_predicateThatTakesSingleArgument() {
        String expr = "user.studies[\"A1\"].forms[\"A2\"].questions[\"A3\"].answers.hasOption(\"OPT_YES\")";
        String expected = "(pex (expr (query user . (study studies [ \"A1\" ]) . (form forms [ \"A2\" ]) . "
                + "(question questions [ \"A3\" ]) . answers . (predicate hasOption ( \"OPT_YES\" )))))";
        testParserTreeOutput(expr, expected);
    }

    @Test
    public void testQuery_answerQuery() {
        String expr = "user.studies[\"A1\"].forms[\"A2\"].instances[latest].questions[\"A3\"].answers.hasFalse()";
        String expected = "(pex (expr (query user . (study studies [ \"A1\" ]) . (form forms [ \"A2\" ]) . "
                + "(instance instances [ latest ]) . (question questions [ \"A3\" ]) . answers . (predicate hasFalse ( )))))";
        testParserTreeOutput(expr, expected);
    }

    private void testParserTreeOutput(String expression, String expected) {
        PexParser parser = buildParser(expression);
        ParseTree tree = parser.pex();
        assertEquals(expected, tree.toStringTree(parser));
    }

    private PexLexer buildLexer(String expression) {
        CharStream chars = CharStreams.fromString(expression);
        return new PexLexer(chars);
    }

    private PexParser buildParser(String expression) {
        PexLexer lexer = buildLexer(expression);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new PexParser(tokens);
    }
}
