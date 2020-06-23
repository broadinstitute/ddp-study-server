package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.core.Handle;

public class JdbiQuestionCached extends SQLObjectWrapper<JdbiQuestion> implements JdbiQuestion {
    private static Cache<String, QuestionDto> questionKeyToQuestionDtoCache;

    public JdbiQuestionCached(Handle handle) {
        super(handle, JdbiQuestion.class);
        initializeCaching();
    }

    private void initializeCaching() {
        questionKeyToQuestionDtoCache = CacheService.getInstance().getOrCreateCache("questionKeyToQuestionDtoCache",
                new Duration(TimeUnit.MINUTES, 15),
                ModelChangeType.UMBRELLA,
                this.getClass());
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
        if (isNullCache(questionKeyToQuestionDtoCache)) {
            return delegate.findDtoByStableIdAndInstance(stableId, activityInstance);
        } else {
            QuestionDto cachedQuestionDto = questionKeyToQuestionDtoCache.get(buildQuestionKey(activityInstance.getActivityId(), stableId));
            if (cachedQuestionDto == null) {
                cacheQuestionDtosForStudyActivity(activityInstance.getActivityId());
                cachedQuestionDto = questionKeyToQuestionDtoCache.get(buildQuestionKey(activityInstance.getActivityId(), stableId));
            }
            return Optional.ofNullable(cachedQuestionDto);
        }
    }

    @Override
    public List<QuestionDto> findDtosByActvityId(Long activityId) {
        return delegate.findDtosByActvityId(activityId);
    }

    private String buildQuestionKey(Long activityId, String questionStableId) {
        return activityId + ":" + questionStableId;
    }

    public void cacheQuestionDtosForStudyActivity(Long activityId) {
        delegate.findDtosByActvityId(activityId).forEach(dto ->
                questionKeyToQuestionDtoCache.put(buildQuestionKey(activityId, dto.getStableId()), dto));
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
