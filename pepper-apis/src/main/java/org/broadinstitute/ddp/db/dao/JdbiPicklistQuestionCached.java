package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.core.Handle;

public class JdbiPicklistQuestionCached extends SQLObjectWrapper<JdbiPicklistQuestion> implements JdbiPicklistQuestion {
    private Cache<Long, PicklistQuestionDto> questionIdToPicklistQuestionDto;

    public JdbiPicklistQuestionCached(Handle handle) {
        super(handle, JdbiPicklistQuestion.class);
        initializeCache();
    }

    private void initializeCache() {
        questionIdToPicklistQuestionDto = CacheService.getInstance().getOrCreateCache("questionIdToTextQuestionCache",
                new Duration(),
                ModelChangeType.STUDY,
                this.getClass());
    }

    @Override
    public int insert(long questionId, long selectModeId, long renderModeId, Long picklistLabelTemplateId) {
        return delegate.insert(questionId, selectModeId, renderModeId, picklistLabelTemplateId);
    }

    @Override
    public int insert(long questionId, PicklistSelectMode selectMode, PicklistRenderMode renderMode, Long picklistLabelTemplateId) {
        return delegate.insert(questionId, selectMode, renderMode, picklistLabelTemplateId);
    }

    @Override
    public Optional<PicklistQuestionDto> findDtoByQuestionId(long questionId) {
        return delegate.findDtoByQuestionId(questionId);
    }

    @Override
    public Optional<PicklistQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
        if (isNullCache(questionIdToPicklistQuestionDto)) {
            return delegate.findDtoByQuestionId(questionDto.getId());
        } else {
            PicklistQuestionDto dto = questionIdToPicklistQuestionDto.get(questionDto.getId());
            if (dto == null) {
                cacheDtosForActivity(questionDto.getActivityId());
                dto = questionIdToPicklistQuestionDto.get(questionDto.getId());
            }
            return Optional.ofNullable(dto);
        }
    }

    @Override
    public List<PicklistQuestionDto> findDtosByActivityId(long activityId) {
        return delegate.findDtosByActivityId(activityId);
    }

    private void cacheDtosForActivity(long activityId) {
        findDtosByActivityId(activityId).forEach(dto -> questionIdToPicklistQuestionDto.put(dto.getId(), dto));
    }
}
