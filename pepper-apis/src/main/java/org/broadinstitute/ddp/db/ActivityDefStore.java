package org.broadinstitute.ddp.db;

import static org.broadinstitute.ddp.content.I18nContentRenderer.DEFAULT_LANGUAGE_CODE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;

public class ActivityDefStore {

    private static ActivityDefStore instance;
    private static volatile Object lockVar = "lock";
    private TreeWalkInterpreter interpreter;
    private Map<String, FormActivityDef> activityDefMap;

    private ActivityDefStore() {
        activityDefMap = new HashMap<>();
    }

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

    public Pair<Integer, Integer> countQuestionsAndAnswersForActivity(Handle handle, String studyId, String activityCode,
                                                                      String versionTag, String userGuid, String instanceGuid,
                                                                      String isoLanguageCode) {
        FormActivityDef formActivityDef = getActivityDef(studyId, activityCode, versionTag);

        long langCodeId = Optional.ofNullable(handle.attach(JdbiLanguageCode.class).getLanguageCodeId(isoLanguageCode))
                .orElseGet(() -> handle.attach(JdbiLanguageCode.class).getLanguageCodeId(DEFAULT_LANGUAGE_CODE));

        int numQuestions = 0;
        int numQuestionsAnswered = 0;
        for (FormSectionDef section : formActivityDef.getAllSections()) {
            for (FormBlockDef block : section.getBlocks()) {
                Pair<Integer, Integer> counts = countQuestionsAndAnswersForBlock(handle, interpreter, block, userGuid, instanceGuid,
                        langCodeId);
                numQuestions += counts.getLeft();
                numQuestionsAnswered += counts.getRight();
                if (block.getBlockType().isContainerBlock()) {
                    List<FormBlockDef> children;
                    if (block.getBlockType() == BlockType.CONDITIONAL) {
                        children = ((ConditionalBlockDef) block).getNested();
                    } else if (block.getBlockType() == BlockType.GROUP) {
                        children = ((GroupBlockDef) block).getNested();
                    } else {
                        throw new DDPException("Unhandled container block type " + block.getBlockType());
                    }
                    for (FormBlockDef child : children) {
                        counts = countQuestionsAndAnswersForBlock(handle, interpreter, child, userGuid, instanceGuid, langCodeId);
                        numQuestions += counts.getLeft();
                        numQuestionsAnswered += counts.getRight();
                    }
                }
            }
        }
        return new ImmutablePair<>(numQuestions, numQuestionsAnswered);
    }

    private Pair<Integer, Integer> countQuestionsAndAnswersForBlock(Handle handle, PexInterpreter interpreter,
                                                                    FormBlockDef formBlockDef, String userGuid, String instanceGuid,
                                                                    long isoLanguageCode) {
        int isQuestion = 0;
        int isQuestionAnswered = 0;
        boolean shown = true;

        if (formBlockDef.getShownExpr() != null) {
            try {
                shown = interpreter.eval(formBlockDef.getShownExpr(), handle, userGuid, instanceGuid);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for formBlockDef def %s: `%s`",
                        formBlockDef.getBlockGuid(), formBlockDef.getShownExpr());
                throw new DDPException(msg, e);
            }
        }

        if (shown) {
            if (formBlockDef.getBlockType() == BlockType.CONDITIONAL) {
                ConditionalBlockDef conditionalBlockDef = (ConditionalBlockDef) formBlockDef;
                if (!conditionalBlockDef.getControl().isDeprecated()) {
                    isQuestion++;
                    isQuestionAnswered += handle.attach(QuestionDao.class).getControlQuestionByBlockId(conditionalBlockDef.getBlockId(),
                            instanceGuid,
                            false,
                            isoLanguageCode)
                            .map(question -> question.isAnswered() ? 1 : 0)
                            .orElse(0);
                }
            } else if (formBlockDef.getBlockType() == BlockType.QUESTION) {
                QuestionBlockDef questionBlockDef = (QuestionBlockDef) formBlockDef;
                if (!questionBlockDef.getQuestion().isDeprecated()) {
                    isQuestion++;
                    isQuestionAnswered += handle.attach(QuestionDao.class).getQuestionByBlockId(formBlockDef.getBlockId(), instanceGuid,
                            false, isoLanguageCode)
                            .map(question -> question.isAnswered() ? 1 : 0)
                            .orElse(0);
                }
            }
        }
        return new ImmutablePair<>(isQuestion, isQuestionAnswered);
    }
}
