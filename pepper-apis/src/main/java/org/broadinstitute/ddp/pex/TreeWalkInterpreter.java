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
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.lang.PexBaseVisitor;
import org.broadinstitute.ddp.pex.lang.PexParser;
import org.broadinstitute.ddp.pex.lang.PexParser.AgeAtLeastPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.AnswerQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.DefaultLatestAnswerQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.FormPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.FormQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasAnyOptionPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasDatePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasFalsePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasOptionPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.HasTruePredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.IsStatusPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.PredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.QuestionPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.QuestionQueryContext;
import org.broadinstitute.ddp.pex.lang.PexParser.StudyPredicateContext;
import org.broadinstitute.ddp.pex.lang.PexParser.StudyQueryContext;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tree walking interpreter for pex.
 *
 * <p>Evaluation of pex expressions is done by directly walking the parse tree
 * created by the pex parser and visiting the nodes of interest.
 *
 * <p>This uses our own visitor instead of the tree walker from ANTLR so we can
 * implement optimizations like short-circuiting binary operators.
 *
 * <p>Correspondence between PEX values and Java objects is simply:
 * <ul>
 *     <li>bool -> Boolean</li>
 *     <li>int -> Long</li>
 *     <li>str -> String</li>
 *     <li>date -> LocalDate</li>
 * </ul>
 *
 * <p>Note that support for dates is very minimal. Only full dates with year-month-day is supported.
 */
public class TreeWalkInterpreter implements PexInterpreter {

    private static final Logger LOG = LoggerFactory.getLogger(TreeWalkInterpreter.class);
    private static final PexFetcher fetcher = new PexFetcher();

    @Override
    public boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid) {
        return eval(expression, handle, userGuid, operatorGuid, activityInstanceGuid, null);
    }

    @Override
    public boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid,
                        UserActivityInstanceSummary activityInstanceSummary) {
        return eval(expression, handle, userGuid, operatorGuid, activityInstanceGuid, activityInstanceSummary, null);
    }

    @Override
    public boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid,
                        UserActivityInstanceSummary activityInstanceSummary, EventSignal signal) {
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

        InterpreterContext ictx = new InterpreterContext(handle, userGuid, operatorGuid, activityInstanceGuid,
                activityInstanceSummary, signal);
        PexValueVisitor visitor = new PexValueVisitor(this, ictx);

        Object result = visitor.visit(tree);
        if (!(result instanceof Boolean)) {
            String typeName = result.getClass().getSimpleName();
            throw new PexRuntimeException("Final result of expression needs to be a bool value but got runtime type " + typeName);
        }

        return (boolean) result;
    }

    /**
     * Extract the boolean value represented in a node by parsing a bool.
     *
     * @param node the parse tree terminal node
     * @return boolean value
     */
    private boolean extractBool(TerminalNode node) {
        return Boolean.parseBoolean(node.getText());
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
     * Extract the string value represented in a node by removing the beginning and ending double-quotes. Does not
     * support backslash-escaping, or any kind of string escaping.
     *
     * @param node the parse tree terminal node
     * @return string value
     */
    private String extractString(TerminalNode node) {
        String raw = node.getText();
        return raw.substring(1, raw.length() - 1);
    }

    /**
     * Extract the comparison operator represented by the given node.
     *
     * @param node the parse tree terminal node
     * @return compare operator type
     */
    private CompareOperator extractCompareOperator(TerminalNode node) {
        switch (node.getText()) {
            case "<":
                return CompareOperator.LESS;
            case "<=":
                return CompareOperator.LESS_EQ;
            case ">":
                return CompareOperator.GREATER;
            case ">=":
                return CompareOperator.GREATER_EQ;
            case "==":
                return CompareOperator.EQ;
            case "!=":
                return CompareOperator.NOT_EQ;
            default:
                throw new PexException("Unknown compare operator: " + node.getText());
        }
    }

    /**
     * Extract the unary operator represented by the given node.
     *
     * @param node the parse tree terminal node
     * @return unary operator type
     */
    private UnaryOperator extractUnaryOperator(TerminalNode node) {
        switch (node.getText()) {
            case "!":
                return UnaryOperator.NOT;
            case "-":
                return UnaryOperator.NEG;
            default:
                throw new PexException("Unknown unary operator: " + node.getText());
        }
    }

    private String getUserGuidByUserType(InterpreterContext ictx, TerminalNode node) {
        if (UserType.OPERATOR.equals(node.getText())) {
            return ictx.getOperatorGuid();
        } else {
            return ictx.getUserGuid();
        }
    }

    private boolean evalStudyQuery(InterpreterContext ictx, StudyQueryContext ctx) {
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        String umbrellaStudyGuid = extractString(ctx.study().STR());
        return applyStudyPredicate(ictx, ctx.studyPredicate(), userGuid, umbrellaStudyGuid);
    }

    private boolean applyStudyPredicate(InterpreterContext ictx, StudyPredicateContext predCtx, String userGuid, String studyGuid) {
        if (predCtx instanceof PexParser.HasAgedUpPredicateContext) {
            GovernancePolicy policy = ictx.getHandle().attach(StudyGovernanceDao.class)
                    .findPolicyByStudyGuid(studyGuid)
                    .orElse(null);
            if (policy == null) {
                throw new PexFetchException(new NoSuchElementException("Governance policy for " + studyGuid + " does not exist"));
            }

            UserProfile profile = ictx.getHandle().attach(UserProfileDao.class).findProfileByUserGuid(userGuid).orElse(null);
            if (profile == null || profile.getBirthDate() == null) {
                LOG.warn("User {} in study {} does not have profile or birth date to evaluate age-up policy, defaulting to false",
                        userGuid, studyGuid);
                return false;
            }

            return policy.hasReachedAgeOfMajority(ictx.getHandle(), new TreeWalkInterpreter(), userGuid, profile.getBirthDate());
        } else if (predCtx instanceof PexParser.HasInvitationPredicateContext) {
            String str = extractString(((PexParser.HasInvitationPredicateContext) predCtx).STR());
            InvitationType inviteType;
            try {
                inviteType = InvitationType.valueOf(str.toUpperCase());
            } catch (Exception e) {
                throw new PexUnsupportedException("Invalid invitation type for hasInvitation() predicate: " + str, e);
            }
            return ictx.getHandle()
                    .attach(InvitationDao.class)
                    .findInvitations(studyGuid, userGuid)
                    .stream()
                    .anyMatch(invite -> invite.getInvitationType() == inviteType);
        } else {
            throw new PexUnsupportedException("Unsupported study predicate: " + predCtx.getText());
        }
    }

    private boolean evalFormQuery(InterpreterContext ictx, FormQueryContext ctx) {
        String umbrellaStudyGuid = extractString(ctx.study().STR());
        String studyActivityCode = extractString(ctx.form().STR());
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        return applyFormPredicate(ictx, ctx.formPredicate(), userGuid, umbrellaStudyGuid, studyActivityCode);
    }

    private boolean applyFormPredicate(InterpreterContext ictx, FormPredicateContext predCtx, String userGuid,
                                       String studyGuid, String activityCode) {
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
            return fetcher.findLatestActivityInstanceStatus(ictx, userGuid, studyGuid, activityCode)
                    .map(expectedStatuses::contains)
                    .orElse(false);
        } else if (predCtx instanceof PexParser.HasInstancePredicateContext) {
            return fetcher.findLatestActivityInstanceStatus(ictx, userGuid, studyGuid, activityCode).isPresent();
        } else {
            throw new PexUnsupportedException("Unsupported form predicate: " + predCtx.getText());
        }
    }

    private Object evalFormInstanceQuery(InterpreterContext ictx, PexParser.FormInstanceQueryContext ctx) {
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String instanceType = ctx.instance().INSTANCE_TYPE().getText();

        long activityId = ictx.getHandle().attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .map(ActivityDto::getActivityId)
                .orElseThrow(() -> {
                    String msg = String.format("Could not find activity with study guid %s and activity code %s", studyGuid, activityCode);
                    return new PexFetchException(new NoSuchElementException(msg));
                });

        final long instanceId;
        if (instanceType.equals(LATEST)) {
            instanceId = ictx.getHandle().attach(JdbiActivityInstance.class)
                    .findLatestInstanceIdByUserGuidAndActivityId(userGuid, activityId)
                    .orElseThrow(() -> new PexFetchException("Could not find latest instance for activity " + activityCode));
        } else {
            String instanceGuid = ictx.getActivityInstanceGuid();
            if (StringUtils.isBlank(instanceGuid)) {
                throw new PexFetchException("No instance guid available for specific instance query");
            }
            instanceId = ictx.getHandle().attach(JdbiActivityInstance.class).getActivityInstanceId(instanceGuid);
        }

        return applyFormInstancePredicate(ictx, ctx.formInstancePredicate(), instanceId);
    }

    private Object applyFormInstancePredicate(InterpreterContext ictx, PexParser.FormInstancePredicateContext predCtx, long instanceId) {
        if (predCtx instanceof PexParser.InstanceSnapshotSubstitutionQueryContext) {
            String subName = extractString(((PexParser.InstanceSnapshotSubstitutionQueryContext) predCtx).STR());
            String value = ictx.getHandle().attach(ActivityInstanceDao.class)
                    .findSubstitutions(instanceId)
                    .get(subName);
            if (value == null) {
                throw new PexFetchException("Could not find snapshot substitution " + subName);
            }
            return value;
        } else {
            throw new PexUnsupportedException("Unsupported form instance predicate: " + predCtx.getText());
        }
    }

    private boolean evalQuestionQuery(InterpreterContext ictx, QuestionQueryContext ctx) {
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String stableId = extractString(ctx.question().STR());

        long activityId = ictx.getHandle().attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .map(ActivityDto::getActivityId)
                .orElseThrow(() -> {
                    String msg = String.format("Could not find activity with study guid %s and activity code %s", studyGuid, activityCode);
                    return new PexFetchException(new NoSuchElementException(msg));
                });

        return applyQuestionPredicate(ictx, userGuid, activityId, stableId, ctx.questionPredicate());
    }

    private boolean applyQuestionPredicate(InterpreterContext ictx, String userGuid, long activityId,
                                           String stableId, QuestionPredicateContext predCtx) {
        if (predCtx instanceof PexParser.IsAnsweredPredicateContext) {
            Long instanceId = ictx.getHandle().attach(JdbiActivityInstance.class)
                    .findLatestInstanceIdByUserGuidAndActivityId(userGuid, activityId)
                    .orElse(null);
            if (instanceId == null) {
                return false;
            }

            Answer answer = ictx.getHandle().attach(AnswerDao.class)
                    .findAnswerByInstanceIdAndQuestionStableId(instanceId, stableId)
                    .orElse(null);
            if (answer == null || answer.getValue() == null) {
                return false;
            }

            if (answer.getQuestionType() == QuestionType.PICKLIST) {
                return ((PicklistAnswer) answer).getValue().size() > 0;
            } else if (answer.getQuestionType() == QuestionType.TEXT) {
                return !((TextAnswer) answer).getValue().isBlank();
            } else {
                return true;
            }
        } else {
            throw new PexUnsupportedException("Unsupported question predicate: " + predCtx.getText());
        }
    }

    private Object evalDefaultLatestAnswerQuery(InterpreterContext ictx, DefaultLatestAnswerQueryContext ctx) {
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String stableId = extractString(ctx.question().STR());
        return applyAnswerPredicate(ictx, userGuid, studyGuid, activityCode, stableId, LATEST, ctx.predicate());
    }

    private Object evalAnswerQuery(InterpreterContext ictx, AnswerQueryContext ctx) {
        String userGuid = getUserGuidByUserType(ictx, ctx.USER_TYPE());
        String studyGuid = extractString(ctx.study().STR());
        String activityCode = extractString(ctx.form().STR());
        String type = ctx.instance().INSTANCE_TYPE().getText();
        String stableId = extractString(ctx.question().STR());
        return applyAnswerPredicate(ictx, userGuid, studyGuid, activityCode, stableId, type, ctx.predicate());
    }

    private Object applyAnswerPredicate(InterpreterContext ictx, String userGuid, String studyGuid, String activityCode, String stableId,
                                        String instanceType, PredicateContext predicateCtx) {

        long studyId = new JdbiUmbrellaStudyCached(ictx.getHandle())
                .getIdByGuid(studyGuid)
                .orElseThrow(() -> {
                    String msg = String.format("Study guid '%s' does not refer to a valid study", studyGuid);
                    return new PexFetchException(new NoSuchElementException(msg));
                });

        QuestionType questionType;
        if (ictx.getActivityInstanceSummary() != null) {
            questionType = ictx.getActivityInstanceSummary().getLatestActivityInstance(activityCode)
                    .map(instanceDto -> ActivityDefStore.getInstance().findActivityDef(ictx.getHandle(), studyGuid, instanceDto)
                            .orElseGet(() -> null))
                    .map(activityDef -> activityDef.getQuestionByStableId(stableId))
                    .map(questionDef -> questionDef.getQuestionType())
                    .orElseThrow(() -> {
                        String msg = String.format(
                                "Cannot find question %s in form activity def with activity code %s for user %s in study %s",
                                stableId, activityCode, userGuid, studyGuid);
                        throw new PexFetchException(new NoSuchElementException(msg));
                    });
        } else {
            questionType = fetcher.findQuestionType(ictx, userGuid, studyGuid, activityCode, stableId).orElseThrow(() -> {
                String msg = String.format(
                        "Cannot find question %s in form %s for user %s and study %s",
                        stableId, activityCode, userGuid, studyGuid);
                return new PexFetchException(new NoSuchElementException(msg));
            });
        }

        String instanceGuid = instanceType.equals(LATEST) ? null : ictx.getActivityInstanceGuid();

        switch (questionType) {
            case BOOLEAN:
                return applyBoolAnswerPredicate(ictx, predicateCtx, userGuid, studyId, activityCode, instanceGuid, stableId);
            case TEXT:
                return applyTextAnswerPredicate(ictx, predicateCtx, userGuid, studyId, activityCode, instanceGuid, stableId);
            case PICKLIST:
                return applyPicklistAnswerPredicate(ictx, predicateCtx, userGuid, studyId, activityCode, instanceGuid, stableId);
            case DATE:
                return applyDateAnswerPredicate(ictx, predicateCtx, userGuid, studyId, activityCode, instanceGuid, stableId);
            case NUMERIC:
                return applyNumericAnswerPredicate(ictx, predicateCtx, userGuid, studyId, activityCode, instanceGuid, stableId);
            default:
                throw new PexUnsupportedException("Question " + stableId + " with type "
                        + questionType + " is currently not supported");
        }
    }

    private Object applyBoolAnswerPredicate(InterpreterContext ictx, PredicateContext predicateCtx,
                                            String userGuid, long studyId, String activityCode,
                                            String instanceGuid, String stableId) {
        if (predicateCtx instanceof HasTruePredicateContext || predicateCtx instanceof HasFalsePredicateContext) {
            boolean expected = (predicateCtx instanceof HasTruePredicateContext);
            Boolean value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestBoolAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificBoolAnswer(ictx, activityCode, instanceGuid, stableId);
            if (value == null) {
                return false;
            } else {
                return value == expected;
            }
        } else if (predicateCtx instanceof PexParser.ValueQueryContext) {
            Boolean value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestBoolAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificBoolAnswer(ictx, activityCode, instanceGuid, stableId);
            if (value == null) {
                String msg = String.format("User %s does not have boolean answer for question %s", userGuid, stableId);
                throw new PexFetchException(msg);
            } else {
                return value;
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on boolean answer query: " + predicateCtx.getText());
        }
    }

    /**
     * Returns true if the given date is at least minimumAge old.
     *
     * @param dateValue  the date to check
     * @param timeUnit   the time unit
     * @param minimumAge minimum age, inclusive
     * @return true if dateValue is present and is at least minimumAge. False otherwise, including if dateValue is empty
     */
    private boolean isOldEnough(Optional<DateValue> dateValue, ChronoUnit timeUnit, long minimumAge) {
        boolean isOldEnough = false;
        if (dateValue.isPresent()) {
            long age = dateValue.get().between(timeUnit, LocalDate.now());  // Instant is zoneless; use LocalDate.
            isOldEnough = age >= minimumAge;
        }
        return isOldEnough;
    }

    private Object applyDateAnswerPredicate(InterpreterContext ictx, PredicateContext predicateCtx,
                                            String userGuid, long studyId, String activityCode, String instanceGuid, String stableId) {
        if (predicateCtx instanceof HasDatePredicateContext) {
            Optional<DateValue> dateValue = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestDateAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificDateAnswer(ictx, activityCode, instanceGuid, stableId);
            return dateValue.isPresent();
        } else if (predicateCtx instanceof AgeAtLeastPredicateContext) {
            AgeAtLeastPredicateContext predCtx = (AgeAtLeastPredicateContext) predicateCtx;
            long minimumAge = extractLong(predCtx.INT());
            ChronoUnit timeUnit = ChronoUnit.valueOf(predCtx.TIMEUNIT().getText());

            Optional<DateValue> dateValue = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestDateAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificDateAnswer(ictx, activityCode, instanceGuid, stableId);

            return isOldEnough(dateValue, timeUnit, minimumAge);
        } else if (predicateCtx instanceof PexParser.ValueQueryContext) {
            Optional<DateValue> dateValue = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestDateAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificDateAnswer(ictx, activityCode, instanceGuid, stableId);

            if (dateValue.isEmpty()) {
                String msg = String.format("User %s does not have date answer for question %s", userGuid, stableId);
                throw new PexFetchException(msg);
            }

            LocalDate value = dateValue.flatMap(DateValue::asLocalDate).orElse(null);
            if (value == null) {
                String msg = String.format(
                        "Could not convert date answer to date value for user %s and question %s",
                        userGuid, stableId);
                throw new PexRuntimeException(msg);
            }

            return value;
        } else {
            throw new PexUnsupportedException("Invalid predicate used on date answer query: " + predicateCtx.getText());
        }
    }

    private Object applyTextAnswerPredicate(InterpreterContext ictx, PredicateContext predicateCtx,
                                            String userGuid, long studyId, String activityCode, String instanceGuid, String stableId) {
        if (predicateCtx instanceof PexParser.HasTextPredicateContext) {
            String value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestTextAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificTextAnswer(ictx, activityCode, instanceGuid, stableId);
            return StringUtils.isNotBlank(value);
        } else if (predicateCtx instanceof PexParser.ValueQueryContext) {
            String value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestTextAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificTextAnswer(ictx, activityCode, instanceGuid, stableId);
            if (value == null) {
                String msg = String.format("User %s does not have text answer for question %s", userGuid, stableId);
                throw new PexFetchException(msg);
            } else {
                return value;
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on text answer query: " + predicateCtx.getText());
        }
    }

    private Object applyPicklistAnswerPredicate(InterpreterContext ictx, PredicateContext predicateCtx,
                                                String userGuid, long studyId, String activityCode, String instanceGuid, String stableId) {
        if (predicateCtx instanceof HasOptionPredicateContext) {
            String optionStableId = extractString(((HasOptionPredicateContext) predicateCtx).STR());
            List<String> value;
            if (ictx.getActivityInstanceSummary() != null) {
                if (StringUtils.isBlank(instanceGuid)) {
                    value = ictx.getActivityInstanceSummary().getLatestActivityInstance(activityCode)
                            .map(instanceDto -> fetcher.findPicklistAnswer(ictx, instanceDto, stableId))
                            .orElse(null);
                } else {
                    value = ictx.getActivityInstanceSummary().getActivityInstanceByGuid(instanceGuid)
                            .map(instanceDto -> fetcher.findPicklistAnswer(ictx, instanceDto, stableId))
                            .orElse(null);
                }
            } else {
                value = StringUtils.isBlank(instanceGuid)
                        ? fetcher.findLatestPicklistAnswer(ictx, userGuid, activityCode, stableId, studyId)
                        : fetcher.findSpecificPicklistAnswer(ictx, activityCode, instanceGuid, stableId);
            }
            if (value == null) {
                return false;
            } else {
                return value.contains(optionStableId);
            }
        } else if (predicateCtx instanceof HasAnyOptionPredicateContext) {
            List<String> optionStableIds = ((HasAnyOptionPredicateContext) predicateCtx).STR()
                    .stream()
                    .map(this::extractString)
                    .collect(Collectors.toList());
            List<String> value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestPicklistAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificPicklistAnswer(ictx, activityCode, instanceGuid, stableId);
            if (value == null) {
                return false;
            } else {
                return value.stream().anyMatch(optionStableIds::contains);
            }
        } else if (predicateCtx instanceof PexParser.ValueQueryContext) {
            throw new PexUnsupportedException("Getting picklist answer value is currently not supported");
        } else {
            throw new PexUnsupportedException("Invalid predicate used on picklist answer set query: " + predicateCtx.getText());
        }
    }

    private Object applyNumericAnswerPredicate(InterpreterContext ictx, PredicateContext predicateCtx,
                                               String userGuid, long studyId, String activityCode, String instanceGuid, String stableId) {
        if (predicateCtx instanceof PexParser.ValueQueryContext) {
            Long value = StringUtils.isBlank(instanceGuid)
                    ? fetcher.findLatestNumericIntegerAnswer(ictx, userGuid, activityCode, stableId, studyId)
                    : fetcher.findSpecificNumericIntegerAnswer(ictx, activityCode, instanceGuid, stableId);
            if (value == null) {
                String msg = String.format("User %s does not have numeric answer for question %s", userGuid, stableId);
                throw new PexFetchException(msg);
            } else {
                return value;
            }
        } else {
            throw new PexUnsupportedException("Invalid predicate used on numeric answer set query: " + predicateCtx.getText());
        }
    }

    private Object evalProfileQuery(InterpreterContext ictx, PexParser.ProfileQueryContext ctx) {
        UserProfile profile = ictx.getHandle()
                .attach(UserProfileDao.class)
                .findProfileByUserGuid(ictx.getUserGuid())
                .orElse(null);
        if (profile == null) {
            throw new PexFetchException("Could not find profile for user " + ictx.getUserGuid());
        }

        PexParser.ProfileDataQueryContext queryCtx = ctx.profileDataQuery();
        if (queryCtx instanceof PexParser.ProfileBirthDateQueryContext) {
            LocalDate birthDate = profile.getBirthDate();
            if (birthDate == null) {
                String msg = String.format("User %s does not have birth date in profile", ictx.getUserGuid());
                throw new PexFetchException(msg);
            } else {
                return birthDate;
            }
        } else if (queryCtx instanceof PexParser.ProfileIsGovernedParticipantQueryContext) {
            return !ictx.getUserGuid().equals(ictx.getOperatorGuid());
        } else {
            throw new PexUnsupportedException("Unhandled profile data query: " + queryCtx.getText());
        }
    }

    private Object evalEventTestResultQuery(InterpreterContext ictx, PexParser.EventTestResultQueryContext ctx) {
        EventSignal eventSignal = ictx.getEventSignal();
        if (eventSignal == null) {
            throw new PexRuntimeException("Expected event signal data but none found in evaluation context");
        }
        if (eventSignal.getEventTriggerType() != EventTriggerType.DSM_NOTIFICATION) {
            throw new PexRuntimeException("Expected DSM notification but found in evaluation context event type "
                    + eventSignal.getEventTriggerType());
        }
        TestResult testResult = ((DsmNotificationSignal) eventSignal).getTestResult();
        if (testResult == null) {
            throw new PexRuntimeException("Expected test result event data but none found in evaluation context");
        }

        PexParser.TestResultQueryContext queryCtx = ctx.testResultQuery();
        if (queryCtx instanceof PexParser.IsCorrectedTestResultQueryContext) {
            return testResult.isCorrected();
        } else if (queryCtx instanceof PexParser.IsPositiveTestResultQueryContext) {
            return TestResult.POSITIVE_CODE.equals(testResult.getNormalizedResult());
        } else {
            throw new PexUnsupportedException("Unhandled test result query: " + queryCtx.getText());
        }
    }

    /**
     * A parse tree visitor that returns PEX values, which are just Java objects.
     *
     * <p>This mainly focuses on how to visit the tree nodes and handling some operators, but relies on the tree walk
     * interpreter for actual evaluation of most nodes.
     */
    class PexValueVisitor extends PexBaseVisitor<Object> {

        private TreeWalkInterpreter interpreter;
        private InterpreterContext ictx;

        PexValueVisitor(TreeWalkInterpreter interpreter, InterpreterContext ictx) {
            this.interpreter = interpreter;
            this.ictx = ictx;
        }

        @Override
        public Object visitBoolLiteralExpr(PexParser.BoolLiteralExprContext ctx) {
            return extractBool(ctx.BOOL());
        }

        @Override
        public Object visitIntLiteralExpr(PexParser.IntLiteralExprContext ctx) {
            return extractLong(ctx.INT());
        }

        @Override
        public Object visitStrLiteralExpr(PexParser.StrLiteralExprContext ctx) {
            return extractString(ctx.STR());
        }

        @Override
        public Object visitUnaryExpr(PexParser.UnaryExprContext ctx) {
            UnaryOperator op = extractUnaryOperator(ctx.UNARY_OPERATOR());
            Object value = ctx.expr().accept(this);
            return op.apply(value);
        }

        @Override
        public Object visitCompareExpr(PexParser.CompareExprContext ctx) {
            CompareOperator op = extractCompareOperator(ctx.RELATION_OPERATOR());
            Object lhs = ctx.expr(0).accept(this);
            Object rhs = ctx.expr(1).accept(this);
            return op.apply(lhs, rhs);
        }

        @Override
        public Object visitEqualityExpr(PexParser.EqualityExprContext ctx) {
            CompareOperator op = extractCompareOperator(ctx.EQUALITY_OPERATOR());
            Object lhs = ctx.expr(0).accept(this);
            Object rhs = ctx.expr(1).accept(this);
            return op.apply(lhs, rhs);
        }

        @Override
        public Object visitAndExpr(PexParser.AndExprContext ctx) {
            return LogicalOperator.AND.apply(this, ctx.expr(0), ctx.expr(1));
        }

        @Override
        public Object visitOrExpr(PexParser.OrExprContext ctx) {
            return LogicalOperator.OR.apply(this, ctx.expr(0), ctx.expr(1));
        }

        @Override
        public Object visitGroupExpr(PexParser.GroupExprContext ctx) {
            // Only evaluate the enclosed expression and not the terminal nodes
            return ctx.expr().accept(this);
        }

        @Override
        public Object visitStudyQuery(PexParser.StudyQueryContext ctx) {
            return interpreter.evalStudyQuery(ictx, ctx);
        }

        @Override
        public Object visitFormQuery(PexParser.FormQueryContext ctx) {
            return interpreter.evalFormQuery(ictx, ctx);
        }

        @Override
        public Object visitFormInstanceQuery(PexParser.FormInstanceQueryContext ctx) {
            return interpreter.evalFormInstanceQuery(ictx, ctx);
        }

        @Override
        public Object visitQuestionQuery(PexParser.QuestionQueryContext ctx) {
            return interpreter.evalQuestionQuery(ictx, ctx);
        }

        @Override
        public Object visitAnswerQuery(PexParser.AnswerQueryContext ctx) {
            return interpreter.evalAnswerQuery(ictx, ctx);
        }

        @Override
        public Object visitDefaultLatestAnswerQuery(PexParser.DefaultLatestAnswerQueryContext ctx) {
            return interpreter.evalDefaultLatestAnswerQuery(ictx, ctx);
        }

        @Override
        public Object visitProfileQuery(PexParser.ProfileQueryContext ctx) {
            return interpreter.evalProfileQuery(ictx, ctx);
        }

        @Override
        public Object visitEventTestResultQuery(PexParser.EventTestResultQueryContext ctx) {
            return interpreter.evalEventTestResultQuery(ictx, ctx);
        }
    }
}
