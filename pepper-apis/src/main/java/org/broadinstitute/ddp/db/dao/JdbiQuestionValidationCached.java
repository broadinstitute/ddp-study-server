package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;

public class JdbiQuestionValidationCached extends SQLObjectWrapper<JdbiQuestionValidation> implements JdbiQuestionValidation {
    private static RLocalCachedMap<Long, List<ValidationDto>> questionIdToValidationsCache;

    private void initializeCaching() {
        if (questionIdToValidationsCache == null) {
            synchronized (this.getClass()) {
                if (questionIdToValidationsCache == null) {
                    questionIdToValidationsCache = CacheService.getInstance()
                            .getOrCreateLocalCache("questionIdToValidationsCache", 10000);
                }
            }
        }
    }

    public JdbiQuestionValidationCached(Handle handle) {
        super(handle, JdbiQuestionValidation.class);
        initializeCaching();
    }

    private boolean isNullCache() {
        return isNullCache(questionIdToValidationsCache);
    }

    @Override
    public long insert(long questionId, long validationId) {
        return delegate.insert(questionId, validationId);
    }

    @Override
    public List<ValidationDto> getAllActiveValidations(long questionId) {
        return delegate.getAllActiveValidations(questionId);
    }

    public List<ValidationDto> getAllActiveValidations(QuestionDto questionDto) {
        if (isNullCache()) {
            return delegate.getAllActiveValidations(questionDto);
        } else {
            List<ValidationDto> validations = questionIdToValidationsCache.get(questionDto.getId());
            if (validations == null) {
                Map<Long, List<ValidationDto>> data = cacheActivityValidations(questionDto.getActivityId());
                validations = data.get(questionDto.getId());
            }
            return validations == null ? new ArrayList<>() : validations;
        }
    }

    private Map<Long, List<ValidationDto>> cacheActivityValidations(Long activityId) {
        Map<Long, List<ValidationDto>> dataToCache = delegate.getAllActiveValidationsForActivity(activityId);
        questionIdToValidationsCache.putAllAsync(dataToCache);
        return dataToCache;
    }


    @Override
    public Map<Long, List<ValidationDto>> getAllActiveValidationsForActivity(long activityId) {
        return delegate.getAllActiveValidationsForActivity(activityId);
    }

    @Override
    public Optional<ValidationDto> getRequiredValidationIfActive(long questionId) {
        return delegate.getRequiredValidationIfActive(questionId);
    }

    @Override
    public List<ValidationDto> findDtosByQuestionIdAndTimestamp(long questionId, long timestamp) {
        return delegate.findDtosByQuestionIdAndTimestamp(questionId, timestamp);
    }
}
