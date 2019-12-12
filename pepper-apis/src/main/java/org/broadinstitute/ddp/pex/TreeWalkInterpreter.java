package org.broadinstitute.ddp.pex;

import static org.broadinstitute.ddp.pex.RetrievedActivityInstanceType.LATEST;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.pex.lang.PexBaseVisitor;
import org.broadinstitute.ddp.pex.lang.PexParser;
import org.broadinstitute.ddp.pex.lang.PexParser.AndExprContext;
import org.broadinstitute.ddp.pex.lang.PexParser.AnswerQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.BoolLiteralExprContext;
import org.broadinstitute.ddp.pex.lang.PexParser.DefaultLatestAnswerQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.FormPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.FormQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.GroupExprContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasAnyOptionPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasDatePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasFalsePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasInstancePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasOptionPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasTextPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasTruePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.IsStatusPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.OrExprContext;
import org.broadinstitute.ddp.pex.lang.PexParser.PredicateContext;
import org.jdbi.v3.core.Handle;

/**
 * A tree walking interpreter for pex.
 *
 * <p>Evaluation of pex expressions is done by directly walking the parse tree
 * created by the pex parser and visiting the nodes of interest.
 *
 * <p>This uses our own visitor instead of the tree walker from ANTLR so we can
 * implement optimizations like short-circuiting binary operators.
 */
public class TreeWalkInterpreter implements PexInterpreter {

    private static final PexFetcher fetcher = new PexFetcher();

    @Override
    public boolean eval(String expression, Handle handle, String userGuid, String activityInstanceGuid) {
        CharStream chars = CharStreams.fromString(expression);
        FailFastLexer lexer = new FailFastLexer(chars);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        PexParser parser = new PexParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());

        ParseTree tree;
        try {
            tree = parser.pex();
        } catch (ParseCancellationException e) {
            throw new PexParseException(e.getCause());
        }

        InterpreterContext ictx = new InterpreterContext(handle, userGuid, activityInstanceGuid);
        BooleanVisitor visitor = new BooleanVisitor(this, ictx);
        return visitor.visit(tree);
    }

    /**
     * Extract the string value represented in a node by removing the beginning and ending double-quotes.
     * Does not support backslash-escaping, or any kind of string escaping.
     *
     * @param node the parse tree terminal node
     * @return string value
     */
    private String extractString(TerminalNode node) {
        String raw = node.getText();
        return raw.substring(1, raw.length() - 1);
    }

    /**
     * Extract the numeric value represented in a node by parsing an int.
     *
     * @param node the parse tree terminal node
     * @return int value
     */
    private int extractInt(TerminalNode node) {
        return Integer.parseInt(node.getText());
    }

    /**
     * Extract the numeric value represented in a node by parsing a long.
     *
     * @param node the parse tree terminal node
     * @return long value
     */
    private long extractLong(TerminalNode node) {
        return Long.parseLong(node.getText());
    }

    /**
     * Extract the comparison operator represented by the given node.
     *
     * @param node the parse tree terminal node
     * @return compare operator type
     */
    private CompareOperator extractCompareOperator(TerminalNode node) {
        switch (node.getText()) {
            case "<": return CompareOperator.LESS;
            case "<=": return CompareOperator.LESS_EQ;
            case ">": return CompareOperator.GREATER;
            case ">=": return CompareOperator.GREATER_EQ;
            case "==": return CompareOperator.EQ;
            case "!=": return CompareOperator.NOT_EQ;
            default:
                throw new PexException("Unknown compare operator: " + node.getText());
        }
    }

    private boolean evalDefaultLatestAnswerQuery(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx) {
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String stableId = extractString(ctx.question().STR());
        long studyId = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
            return handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseThrow(() -> {
                        String msg = String.format("Study guid '%s' does not refer to a valid study", studyGuid);
                        return new PexFetchException(new NoSuchElementException(msg));
                    });
        });

        QuestionType questionType = fetcher.findQuestionType(ictx, studyGuid, activityCode, stableId).orElseThrow(() -> {
            String msg = String.format(
                    "Cannot find question %s in form %s for user %s and study %s",
                    stableId, activityCode, ictx.getUserGuid(), studyGuid);
            return new PexFetchException(new NoSuchElementException(msg));
        });

        switch (questionType) {
            case BOOLEAN:
                return applyBoolAnswerSetPredicate(ictx, ctx, activityCode, stableId, studyId);
            case TEXT:
                return applyTextAnswerSetPredicate(ictx, ctx, activityCode, stableId, studyId);
            case PICKLIST:
                return applyPicklistAnswerSetPredicate(ictx, ctx, activityCode, stableId, studyId);
            case DATE:
                return applyDateAnswerSetPredicate(ictx, ctx, activityCode, stableId, studyId);
            case NUMERIC:
                return applyNumericAnswerSetPredicate(ictx, ctx, activityCode, stableId, studyId);
            default:
                throw new PexUnsupportedException("Question " + stableId + " with type "
                        + questionType + " is currently not supported");
        }
    }

    private boolean evalFormQuery(InterpreterContext ictx, FormQueryContext ctx) {
        String umbrellaStudyGuid = extractString(ctx.study().STR());
        String studyActivityCode = extractString(ctx.form().STR());
        return applyFormPredicate(ictx, ctx.formPredicate(), umbrellaStudyGuid, studyActivityCode);
    }

    private boolean applyFormPredicate(InterpreterContext ictx, FormPredicateContext predCtx, String studyGuid, String activityCode) {
        if (predCtx instanceof IsStatusPredicateContext) {
            List<InstanceStatusType> expectedStatuses = ((IsStatusPredicateContext) predCtx).STR().stream()
                    .map(node -> {
                        String str = extractString(node).toUpperCase();
                        try {
                            return InstanceStatusType.valueOf(str);
                        } catch (Exception e) {
                            throw new PexUnsupportedException("Invalid status used for status predicate: " + str, e);
                        }
                    })
                    .collect(Collectors.toList());
            return fetcher.findLatestActivityInstanceStatus(ictx, studyGuid, activityCode)
                    .map(latestStatus -> expectedStatuses.contains(latestStatus))
                    .orElseThrow(() -> {
                        String msg = String.format("No activity instance of form %s found for user %s and study %s",
                                activityCode, ictx.getUserGuid(), studyGuid);
                        return new PexFetchException(new NoSuchElementException(msg));
                    });
        } else if (predCtx instanceof HasInstancePredicateContext) {
            return fetcher.findLatestActivityInstanceStatus(ictx, studyGuid, activityCode).isPresent();
        } else {
            throw new PexUnsupportedException("Unsupported form predicate: " + predCtx.getText());
        }
    }

    /**
     * Returns true if the given date is at least minimumAge old.
     * @param dateValue the date to check
     * @param timeUnit the time unit
     * @param minimumAge minimum age, inclusive
     * @return true if dateValue is present and is at least minimumAge.  False otherwise,
     *              including if dateValue is empty
     */
    private boolean isOldEnough(Optional<DateValue> dateValue, ChronoUnit timeUnit, int minimumAge) {
        boolean isOldEnough = false;
        if (dateValue.isPresent()) {
            long age = dateValue.get().between(timeUnit, LocalDate.now());  // Instant is zoneless; use LocalDate.
            isOldEnough = age >= minimumAge;
        }
        return isOldEnough;
    }

    private boolean applyDateAnswerSetPredicate(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx,
                                                String activityCode, String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof HasDatePredicateContext) {
            return fetcher.findLatestDateAnswer(ictx, activityCode, stableId, studyId).isPresent();
        } else if (setPredCtx instanceof PexParser.AgeAtLeastPredicateContext) {
            int minimumAge = extractInt(((PexParser.AgeAtLeastPredicateContext) setPredCtx).INT());

            ChronoUnit timeUnit = ChronoUnit.valueOf(((PexParser.AgeAtLeastPredicateContext) setPredCtx).TIMEUNIT().getText());

            Optional<DateValue> dateValue;
            dateValue = fetcher.findLatestDateAnswer(ictx, activityCode, stableId, studyId);

            return isOldEnough(dateValue, timeUnit, minimumAge);

        } else {
            throw new PexUnsupportedException("Invalid predicate used on date answer set query: " + setPredCtx.getText());
        }
    }

    private boolean applyBoolAnswerSetPredicate(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx,
                                                String activityCode, String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof HasTruePredicateContext) {
            return matchBoolAnswer(ictx, activityCode, null, stableId, true, studyId);
        } else if (setPredCtx instanceof HasFalsePredicateContext) {
            return matchBoolAnswer(ictx, activityCode, null, stableId, false, studyId);
        } else {
            throw new PexUnsupportedException("Invalid predicate used on boolean answer set query: " + setPredCtx.getText());
        }
    }

    private boolean applyTextAnswerSetPredicate(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx,
                                                String activityCode, String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof HasTextPredicateContext) {
            return !StringUtils.isBlank(fetcher.findLatestTextAnswer(ictx, activityCode, stableId, studyId));
        } else {
            throw new PexUnsupportedException("Invalid predicate used on text answer set query: " + setPredCtx.getText());
        }
    }

    private boolean applyPicklistAnswerSetPredicate(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx,
                                                    String activityCode, String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof HasOptionPredicateContext) {
            String optionStableId = extractString(((HasOptionPredicateContext) setPredCtx).STR());
            List<String> result = fetcher.findLatestPicklistAnswer(ictx, activityCode, stableId, studyId);
            if (result == null) {
                return false;
            } else {
                return result.contains(optionStableId);
            }
        } else if (setPredCtx instanceof HasAnyOptionPredicateContext) {
            List<String> optionStableIds = ((HasAnyOptionPredicateContext) setPredCtx).STR()
                    .stream()
                    .map(this::extractString)
                    .collect(Collectors.toList());
            List<String> result = fetcher.findLatestPicklistAnswer(ictx, activityCode, stableId, studyId);
            if (result == null) {
                return false;
            } else {
                return result.stream().anyMatch(optionStableIds::contains);
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on picklist answer set query: " + setPredCtx.getText());
        }
    }

    private boolean applyNumericAnswerSetPredicate(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx,
                                                   String activityCode, String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof PexParser.NumComparePredicateContext) {
            PexParser.NumComparePredicateContext numCmpPred = (PexParser.NumComparePredicateContext) setPredCtx;
            CompareOperator op = extractCompareOperator(numCmpPred.COMPARE_OPERATOR());
            long target = extractLong(numCmpPred.INT());
            Long value = fetcher.findLatestNumericIntegerAnswer(ictx, activityCode, stableId, studyId);
            if (value == null) {
                return false;
            } else {
                return op.compare(value, target);
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on numeric answer set query: " + setPredCtx.getText());
        }
    }

    private boolean evalAnswerQuery(InterpreterContext ictx, AnswerQueryContext ctx) {
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String type = ctx.instance().INSTANCE_TYPE().getText();
        String stableId = extractString(ctx.question().STR());
        long studyId = TransactionWrapper.withTxn(handle -> {
            return handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseThrow(() -> {
                        String msg = String.format("Study guid '%s' does not refer to a valid study", studyGuid);
                        return new PexFetchException(new NoSuchElementException(msg));
                    });
        });

        QuestionType questionType = fetcher.findQuestionType(ictx, studyGuid, activityCode, stableId).orElseThrow(() -> {
            String msg = String.format(
                    "Cannot find question %s in form %s for user %s and study %s",
                    stableId, activityCode, ictx.getUserGuid(), studyGuid);
            return new PexFetchException(new NoSuchElementException(msg));
        });

        String activityInstanceGuid = (type.equals(LATEST)) ? null : ictx.getActivityInstanceGuid();

        switch (questionType) {
            case BOOLEAN:
                return applyBoolAnswerPredicate(ictx, ctx, activityCode, activityInstanceGuid, stableId, studyId);
            case TEXT:
                return applyTextAnswerPredicate(ictx, ctx, activityCode, activityInstanceGuid, stableId, studyId);
            case PICKLIST:
                return applyPicklistAnswerPredicate(ictx, ctx, activityCode, activityInstanceGuid, stableId, studyId);
            case DATE:
                return applyDateAnswerPredicate(ictx, ctx, activityCode, activityInstanceGuid, stableId, studyId);
            default:
                throw new PexUnsupportedException("Question " + stableId + " with type "
                        + questionType + " is currently not supported");
        }
    }

    private boolean matchBoolAnswer(InterpreterContext ictx, String activityCode,
                                    String activityInstanceGuid, String stableId,
                                    boolean expected, long studyId) {
        Boolean result;
        if (StringUtils.isBlank(activityInstanceGuid)) {
            result = fetcher.findLatestBoolAnswer(ictx, activityCode, stableId, studyId);
        } else {
            result = fetcher.findSpecificBoolAnswer(ictx, activityCode, activityInstanceGuid, stableId);
        }
        if (result == null) {
            return false;
        } else {
            return result == expected;
        }
    }

    private boolean applyBoolAnswerPredicate(InterpreterContext ictx, AnswerQueryContext ctx,
                                             String activityCode, String activityInstanceGuid,
                                             String stableId, long studyId) {
        PredicateContext predCtx = ctx.predicate();
        if (!(predCtx instanceof HasFalsePredicateContext) && !(predCtx instanceof HasTruePredicateContext)) {
            throw new PexUnsupportedException("Invalid predicate used on boolean answer query: " + predCtx.getText());
        }
        boolean expectedValue = predCtx instanceof HasTruePredicateContext;
        return matchBoolAnswer(ictx, activityCode, activityInstanceGuid, stableId, expectedValue, studyId);
    }

    private boolean applyTextAnswerPredicate(InterpreterContext ictx, AnswerQueryContext ctx,
                                             String activityCode, String activityInstanceGuid,
                                             String stableId, long studyId) {
        PredicateContext predCtx = ctx.predicate();
        if (predCtx instanceof HasTextPredicateContext) {
            if (activityInstanceGuid == null) {
                return !StringUtils.isBlank(fetcher.findLatestTextAnswer(ictx, activityCode, stableId, studyId));
            } else {
                return !StringUtils.isBlank(fetcher.findSpecificTextAnswer(ictx, activityCode, activityInstanceGuid, stableId));
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on text answer query: " + predCtx.getText());
        }
    }

    private boolean applyDateAnswerPredicate(InterpreterContext ictx, AnswerQueryContext ctx,
                                             String activityCode, String activityInstanceGuid,
                                             String stableId, long studyId) {
        PredicateContext predCtx = ctx.predicate();
        if (predCtx instanceof HasDatePredicateContext) {
            if (activityInstanceGuid == null) {
                return fetcher.findLatestDateAnswer(ictx, activityCode, stableId, studyId).isPresent();
            } else {
                return fetcher.findSpecificDateAnswer(ictx, activityCode, activityInstanceGuid, stableId).isPresent();
            }
        } else if (predCtx instanceof PexParser.AgeAtLeastPredicateContext) {
            int minimumAge = extractInt(((PexParser.AgeAtLeastPredicateContext) predCtx).INT());

            ChronoUnit timeUnit = ChronoUnit.valueOf(((PexParser.AgeAtLeastPredicateContext) predCtx).TIMEUNIT().getText());

            Optional<DateValue> dateValue;
            if (activityInstanceGuid == null) {
                dateValue = fetcher.findLatestDateAnswer(ictx, activityCode, stableId, studyId);
            } else {
                dateValue = fetcher.findSpecificDateAnswer(ictx, activityCode, activityInstanceGuid, stableId);
            }

            return isOldEnough(dateValue, timeUnit, minimumAge);

        } else {
            throw new PexUnsupportedException("Invalid predicate used on date answer query: " + predCtx.getText());
        }
    }

    private boolean applyPicklistAnswerPredicate(InterpreterContext ictx, AnswerQueryContext ctx,
                                                 String activityCode, String activityInstanceGuid,
                                                 String stableId, long studyId) {
        PredicateContext setPredCtx = ctx.predicate();
        if (setPredCtx instanceof HasOptionPredicateContext) {
            String optionStableId = extractString(((HasOptionPredicateContext) setPredCtx).STR());
            List<String> result;
            if (activityInstanceGuid == null) {
                result = fetcher.findLatestPicklistAnswer(ictx, activityCode, stableId, studyId);
            } else {
                result = fetcher.findSpecificPicklistAnswer(ictx, activityCode, activityInstanceGuid, stableId);
            }

            if (result == null) {
                return false;
            } else {
                return result.contains(optionStableId);
            }

        } else {
            throw new PexUnsupportedException("Invalid predicate used on picklist answer set query: " + setPredCtx.getText());
        }
    }

    /**
     * A parse tree visitor that returns boolean values.
     *
     * <p>This focuses on how to visit the tree nodes, and relies on the tree walk interpreter for
     * actual evaluation of nodes.
     */
    private class BooleanVisitor extends PexBaseVisitor<Boolean> {

        private TreeWalkInterpreter interpreter;
        private InterpreterContext ictx;

        BooleanVisitor(TreeWalkInterpreter interpreter, InterpreterContext ictx) {
            this.interpreter = interpreter;
            this.ictx = ictx;
        }

        @Override
        public Boolean visitNotExpr(PexParser.NotExprContext ctx) {
            return !ctx.expr().accept(this);
        }

        @Override
        public Boolean visitAndExpr(AndExprContext ctx) {
            // Let Java handle short circuiting
            return ctx.expr(0).accept(this) && ctx.expr(1).accept(this);
        }

        @Override
        public Boolean visitOrExpr(OrExprContext ctx) {
            // Let Java handle short circuiting
            return ctx.expr(0).accept(this) || ctx.expr(1).accept(this);
        }

        @Override
        public Boolean visitGroupExpr(GroupExprContext ctx) {
            // Only evaluate the enclosed expression and not the terminal nodes
            return ctx.expr().accept(this);
        }

        @Override
        public Boolean visitBoolLiteralExpr(BoolLiteralExprContext ctx) {
            return Boolean.valueOf(ctx.BOOL().getText());
        }

        @Override
        public Boolean visitDefaultLatestAnswerQuery(DefaultLatestAnswerQueryContext ctx) {
            return interpreter.evalDefaultLatestAnswerQuery(ictx, ctx);
        }

        @Override
        public Boolean visitAnswerQuery(AnswerQueryContext ctx) {
            return interpreter.evalAnswerQuery(ictx, ctx);
        }

        @Override
        public Boolean visitFormQuery(FormQueryContext ctx) {
            return interpreter.evalFormQuery(ictx, ctx);
        }
    }
}
