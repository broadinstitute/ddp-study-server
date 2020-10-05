package org.broadinstitute.ddp.db.dao;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.core.Handle;

public class JdbiPicklistQuestionCached extends SQLObjectWrapper<JdbiPicklistQuestion> implements JdbiPicklistQuestion {
    private static Cache<Long, PicklistQuestionDto> questionIdToPicklistQuestionDto;

    public JdbiPicklistQuestionCached(Handle handle) {
        super(handle, JdbiPicklistQuestion.class);
        initializeCache();
    }

    private void initializeCache() {
        if (questionIdToPicklistQuestionDto == null) {
            questionIdToPicklistQuestionDto = CacheService.getInstance().getOrCreateCache("questionIdToPicklistQuestionDto",
                    new Duration(),
                    ModelChangeType.STUDY,
                    this.getClass());
        }
    }

    @Override
    public int insert(long questionId, long selectModeId, long renderModeId, Long picklistLabelTemplateId) {
        return delegate.insert(questionId, selectModeId, renderModeId, picklistLabelTemplateId);
    }

    @Override
    public int insert(long questionId, PicklistSelectMode selectMode, PicklistRenderMode renderMode, Long picklistLabelTemplateId) {
        return delegate.insert(questionId, selectMode, renderMode, picklistLabelTemplateId);
    }

    // @Override
    // public Optional<PicklistQuestionDto> findDtoByQuestionId(long questionId) {
    //     if (isNullCache(questionIdToPicklistQuestionDto)) {
    //         return delegate.findDtoByQuestionId(questionId);
    //     } else {
    //         PicklistQuestionDto pickDto = questionIdToPicklistQuestionDto.get(questionId);
    //         if (pickDto == null) {
    //             Optional<PicklistQuestionDto> dtoOpt = delegate.findDtoByQuestionId(questionId);
    //             dtoOpt.ifPresent(dto -> questionIdToPicklistQuestionDto.put(questionId, dto));
    //             return dtoOpt;
    //         } else {
    //             return Optional.of(pickDto);
    //         }
    //
    //     }
    // }

    // @Override
    // public Optional<PicklistQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
    //     if (isNullCache(questionIdToPicklistQuestionDto)) {
    //         return delegate.findDtoByQuestionId(questionDto.getId());
    //     } else {
    //         PicklistQuestionDto dto = questionIdToPicklistQuestionDto.get(questionDto.getId());
    //         if (dto == null) {
    //             cacheDtosForActivity(questionDto.getActivityId());
    //             dto = questionIdToPicklistQuestionDto.get(questionDto.getId());
    //         }
    //         return Optional.ofNullable(dto);
    //     }
    // }

    // @Override
    // public List<PicklistQuestionDto> findDtosByActivityId(long activityId) {
    //     return delegate.findDtosByActivityId(activityId);
    // }

    // private void cacheDtosForActivity(long activityId) {
    //     findDtosByActivityId(activityId).forEach(dto -> questionIdToPicklistQuestionDto.put(dto.getId(), dto));
    // }
}
