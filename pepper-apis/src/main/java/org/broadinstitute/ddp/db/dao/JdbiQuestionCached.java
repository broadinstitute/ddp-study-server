package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;

public class JdbiQuestionCached extends SQLObjectWrapper<JdbiQuestion> implements JdbiQuestion {
    private static RLocalCachedMap<String, List<QuestionDto>> questionKeyToQuestionDtosCache;

    public JdbiQuestionCached(Handle handle) {
        super(handle, JdbiQuestion.class);
        initializeCaching();
    }

    private void initializeCaching() {
        if (questionKeyToQuestionDtosCache == null) {
            synchronized (getClass()) {
                if (questionKeyToQuestionDtosCache == null) {
                    questionKeyToQuestionDtosCache = CacheService.getInstance().getOrCreateLocalCache("questionKeyToQuestionDtoCache",
                            10000);
                }
            }
        }
    }

    @Override
    public long insert(long questionTypeId, boolean isRestricted, long stableCodeId, long promptTemplateId, Long tooltipTemplateId,
                       Long infoHeaderTemplateId, Long infoFooterTemplateId, long revisionId, long activityId, boolean hideNumber,
                       boolean isDeprecated) {
        return delegate.insert(questionTypeId, isRestricted, stableCodeId, promptTemplateId, tooltipTemplateId,
                infoHeaderTemplateId, infoFooterTemplateId, revisionId, activityId, hideNumber, isDeprecated);
    }

    public Optional<QuestionDto> findDtoByStableIdAndInstanceGuid(String stableId, String instanceGuid) {
        return delegate.findDtoByStableIdAndInstanceGuid(stableId, instanceGuid);
    }

    @Override
    public Optional<QuestionDto> findDtoByStableIdAndInstance(String stableId, ActivityInstanceDto activityInstance) {
        if (isNullCache(questionKeyToQuestionDtosCache)) {
            return delegate.findDtoByStableIdAndInstance(stableId, activityInstance);
        } else {
            List<QuestionDto> cachedQuestionDtos = questionKeyToQuestionDtosCache.get(buildQuestionKey(activityInstance.getActivityId(),
                    stableId));
            if (cachedQuestionDtos == null) {
                Map<String, List<QuestionDto>> mapOfDtos = cacheQuestionDtosForStudyActivity(activityInstance.getActivityId());
                cachedQuestionDtos = mapOfDtos.getOrDefault(buildQuestionKey(activityInstance.getActivityId(), stableId),
                        Collections.emptyList());
            }
            List<QuestionDto> filteredDtos = cachedQuestionDtos.stream()
                    .filter(dto -> (dto.getRevisionStart() <= activityInstance.getCreatedAtMillis())
                            && (dto.getRevisionEnd() == null || activityInstance.getCreatedAtMillis() < dto.getRevisionEnd()))
                    .collect(toList());
            return filteredDtos.stream().findFirst();
        }
    }

    @Override
    public List<QuestionDto> findDtosByActivityId(Long activityId) {
        return delegate.findDtosByActivityId(activityId);
    }

    private String buildQuestionKey(Long activityId, String questionStableId) {
        return activityId + ":" + questionStableId;
    }

    public Map<String, List<QuestionDto>> cacheQuestionDtosForStudyActivity(Long activityId) {
        List<QuestionDto> dtos = delegate.findDtosByActivityId(activityId);
        Map<String, List<QuestionDto>> dtoMap = dtos.stream().collect(
                groupingBy(dto -> buildQuestionKey(activityId, dto.getStableId())));
        questionKeyToQuestionDtosCache.putAllAsync(dtoMap);
        return dtoMap;
    }

    @Override
    public Optional<QuestionDto> findDtoByStudyIdStableIdAndUserGuid(long studyId, String stableId, String userGuid) {
        return delegate.findDtoByStudyIdStableIdAndUserGuid(studyId, stableId, userGuid);
    }

    @Override
    public Optional<QuestionDto> getQuestionDtoIfActive(long questionId) {
        return delegate.getQuestionDtoIfActive(questionId);
    }

    @Override
    public Optional<QuestionDto> getQuestionDtoById(long questionId) {
        return delegate.getQuestionDtoById(questionId);
    }

    @Override
    public Optional<QuestionDto> findLatestDtoByStudyIdAndQuestionStableId(long studyId, String questionStableId) {
        return delegate.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId);
    }

    @Override
    public Stream<QuestionDto> findLatestDtosByStudyIdAndQuestionStableIds(long studyId, Set<String> questionStableId) {
        return delegate.findLatestDtosByStudyIdAndQuestionStableIds(studyId, questionStableId);
    }

    @Override
    public int updateRevisionIdById(long questionId, long revisionId) {
        return delegate.updateRevisionIdById(questionId, revisionId);
    }

    @Override
    public int updateIsDeprecatedById(long questionId, boolean isDeprecated) {
        return delegate.updateIsDeprecatedById(questionId, isDeprecated);
    }
}
