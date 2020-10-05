package org.broadinstitute.ddp.db.dao;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.jdbi.v3.core.Handle;

public class JdbiBooleanQuestionCached extends SQLObjectWrapper<JdbiBooleanQuestion> implements JdbiBooleanQuestion {
    private static Cache<Long, BooleanQuestionDto> questionIdToBooleanQuestionCache;

    public JdbiBooleanQuestionCached(Handle handle) {
        super(handle, JdbiBooleanQuestion.class);
        initializeCache();
    }

    private void initializeCache() {
        if (questionIdToBooleanQuestionCache == null) {
            questionIdToBooleanQuestionCache = CacheService.getInstance().getOrCreateCache("questionIdToBooleanQuestionCache",
                    new Duration(),
                    ModelChangeType.STUDY,
                    this.getClass());
        }
    }

    // private void cacheQuestions(long activityId) {
    //     delegate.findDtoByActivityId(activityId).forEach(dto -> questionIdToBooleanQuestionCache.put(dto.getId(), dto));
    // }


    @Override
    public int insert(long questionId, long trueTemplateId, long falseTemplateId) {
        return delegate.insert(questionId, trueTemplateId, falseTemplateId);
    }

    // @Override
    // public Optional<BooleanQuestionDto> findDtoByQuestionId(long questionId) {
    //     return delegate.findDtoByQuestionId(questionId);
    // }

    // @Override
    // public Optional<BooleanQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
    //     if (isNullCache(questionIdToBooleanQuestionCache)) {
    //         return delegate.findDtoByQuestionId(questionDto.getId());
    //     } else {
    //         BooleanQuestionDto boolDto = questionIdToBooleanQuestionCache.get(questionDto.getId());
    //         if (boolDto == null) {
    //             cacheQuestions(questionDto.getActivityId());
    //             boolDto = questionIdToBooleanQuestionCache.get(questionDto.getId());
    //         }
    //         return Optional.ofNullable(boolDto);
    //     }
    // }

    // @Override
    // public List<BooleanQuestionDto> findDtoByActivityId(long activityId) {
    //     return delegate.findDtoByActivityId(activityId);
    // }
}
