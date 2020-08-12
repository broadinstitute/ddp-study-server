package org.broadinstitute.ddp.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
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
    private static final Object lockVar = "lock";

    private TreeWalkInterpreter interpreter;
    private Map<String, FormActivityDef> activityDefMap;
    private Map<Long, ActivityDto> activityDtoMap;
    private Map<Long, List<ActivityVersionDto>> versionDtoListMap;
    private Map<Long, List<ActivityValidationDto>> validationDtoListMap;

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
        activityDtoMap = new HashMap<>();
        versionDtoListMap = new HashMap<>();
        validationDtoListMap = new HashMap<>();
    }

    public void clear() {
        synchronized (lockVar) {
            activityDefMap.clear();
            activityDtoMap.clear();
            versionDtoListMap.clear();
            validationDtoListMap.clear();
        }
    }

    public List<ActivityValidationDto> findUntranslatedActivityValidationDtos(Handle handle, long activityId) {
        synchronized (lockVar) {
            return validationDtoListMap.computeIfAbsent(activityId, id ->
                    handle.attach(JdbiActivityValidation.class)._findByActivityId(activityId));
        }
    }

    public boolean clearCachedActivityValidationDtos(long activityId) {
        return validationDtoListMap.remove(activityId) != null;
    }

    public Optional<ActivityDto> findActivityDto(Handle handle, long activityId) {
        synchronized (lockVar) {
            return Optional.ofNullable(activityDtoMap.computeIfAbsent(activityId, id ->
                    handle.attach(JdbiActivity.class).queryActivityById(activityId)));
        }
    }

    public Optional<ActivityVersionDto> findVersionDto(Handle handle, long activityId, long createdAtMillis) {
        List<ActivityVersionDto> versionDtos;
        synchronized (lockVar) {
            versionDtos = versionDtoListMap.computeIfAbsent(activityId, id ->
                    handle.attach(JdbiActivityVersion.class).findAllVersionsInAscendingOrder(activityId));
        }
        ActivityVersionDto matched = null;
        for (var versionDto : versionDtos) {
            if (versionDto.getRevStart() <= createdAtMillis) {
                if (versionDto.getRevEnd() == null || createdAtMillis < versionDto.getRevEnd()) {
                    matched = versionDto;
                    break;
                }
            }
        }
        return Optional.ofNullable(matched);
    }

    public Optional<FormActivityDef> findActivityDef(Handle handle, String studyGuid,
                                                     ActivityDto activityDto, ActivityVersionDto versionDto) {
        synchronized (lockVar) {
            String key = studyGuid + activityDto.getActivityCode() + versionDto.getVersionTag();
            FormActivityDef def = activityDefMap.get(key);
            if (def == null) {
                def = handle.attach(FormActivityDao.class).findDefByDtoAndVersion(activityDto, versionDto);
                activityDefMap.put(key, def);
            }
            return Optional.ofNullable(def);
        }
    }

    public Optional<FormActivityDef> findActivityDef(Handle handle, String studyGuid, ActivityInstanceDto instanceDto) {
        return findVersionDto(handle, instanceDto.getActivityId(), instanceDto.getCreatedAtMillis())
                .map(version -> getActivityDef(studyGuid, instanceDto.getActivityCode(), version.getVersionTag()));
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
