package org.broadinstitute.ddp.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;

public class ActivityDefStore {

    private static ActivityDefStore instance;
    private static volatile Object lockVar = "lock";

    private TreeWalkInterpreter interpreter;
    private Map<String, FormActivityDef> activityDefMap;

    public static ActivityDefStore getInstance() {
        if (instance == null) {
            synchronized (lockVar) {
                if (instance == null) {
                    instance = new ActivityDefStore();
                    instance.interpreter = new TreeWalkInterpreter();
                }
            }
        }
        return instance;
    }

    private ActivityDefStore() {
        activityDefMap = new HashMap<>();
    }

    public void clear() {
        synchronized (lockVar) {
            activityDefMap.clear();
        }
    }

    public FormActivityDef getActivityDef(String studyGuid, String activityCode, String versionTag) {
        synchronized (lockVar) {
            return activityDefMap.get(studyGuid + activityCode + versionTag);
        }
    }

    public void setActivityDef(String studyGuid, String activityCode, String versionTag, FormActivityDef activityDef) {
        synchronized (lockVar) {
            activityDefMap.put(studyGuid + activityCode + versionTag, activityDef);
        }
    }

    public Pair<Integer, Integer> countQuestionsAndAnswers(Handle handle, String userGuid,
                                                           FormActivityDef formActivityDef,
                                                           String instanceGuid) {
        FormResponse formResponse = handle.attach(ActivityInstanceDao.class)
                .findFormResponseWithAnswersByInstanceGuid(instanceGuid)
                .orElse(null);
        if (formResponse != null) {
            formResponse.unwrapComposites();
        }

        int numQuestions = 0;
        int numAnswered = 0;
        for (var section : formActivityDef.getAllSections()) {
            for (var block : section.getBlocks()) {
                Pair<Integer, Integer> counts = countBlock(handle, userGuid, block, formResponse);
                numQuestions += counts.getLeft();
                numAnswered += counts.getRight();
                if (block.getBlockType().isContainerBlock()) {
                    List<FormBlockDef> children;
                    if (block.getBlockType() == BlockType.CONDITIONAL) {
                        children = ((ConditionalBlockDef) block).getNested();
                    } else if (block.getBlockType() == BlockType.GROUP) {
                        children = ((GroupBlockDef) block).getNested();
                    } else {
                        throw new DDPException("Unhandled container block type " + block.getBlockType());
                    }
                    for (var child : children) {
                        counts = countBlock(handle, userGuid, child, formResponse);
                        numQuestions += counts.getLeft();
                        numAnswered += counts.getRight();
                    }
                }
            }
        }

        return new ImmutablePair<>(numQuestions, numAnswered);
    }

    private Pair<Integer, Integer> countBlock(Handle handle, String userGuid, FormBlockDef block, FormResponse formResponse) {
        int numQuestions = 0;
        int numAnswered = 0;
        boolean shown = true;
        String instanceGuid = formResponse != null ? formResponse.getGuid() : null;

        if (block.getShownExpr() != null) {
            try {
                shown = interpreter.eval(block.getShownExpr(), handle, userGuid, instanceGuid);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for formBlockDef def %s: `%s`",
                        block.getBlockGuid(), block.getShownExpr());
                throw new DDPException(msg, e);
            }
        }

        if (shown) {
            QuestionDef questionDef = null;
            if (block.getBlockType() == BlockType.CONDITIONAL) {
                ConditionalBlockDef conditionalBlockDef = (ConditionalBlockDef) block;
                questionDef = conditionalBlockDef.getControl();
            } else if (block.getBlockType() == BlockType.QUESTION) {
                QuestionBlockDef questionBlockDef = (QuestionBlockDef) block;
                questionDef = questionBlockDef.getQuestion();
            }

            if (questionDef != null && !questionDef.isDeprecated()) {
                if (questionDef.getQuestionType() == QuestionType.COMPOSITE
                        && ((CompositeQuestionDef) questionDef).shouldUnwrapChildQuestions()) {
                    // If configured to unwrap, then treat it as multiple individual questions.
                    for (var child : ((CompositeQuestionDef) questionDef).getChildren()) {
                        numQuestions++;
                        var answer = formResponse != null ? formResponse.getAnswer(child.getStableId()) : null;
                        numAnswered += (answer != null && !answer.isEmpty()) ? 1 : 0;
                    }
                } else {
                    numQuestions++;
                    var answer = formResponse != null ? formResponse.getAnswer(questionDef.getStableId()) : null;
                    numAnswered += (answer != null && !answer.isEmpty()) ? 1 : 0;
                }
            }
        }

        return new ImmutablePair<>(numQuestions, numAnswered);
    }
}
