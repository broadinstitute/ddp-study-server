package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.jdbi.v3.core.Handle;

public class JdbiQuestionValidationCached extends SQLObjectWrapper<JdbiQuestionValidation> implements JdbiQuestionValidation {
    private static Cache<Long, List<ValidationDto>> questionIdToValidationsCache;

    private void initializeCaching() {
        if (questionIdToValidationsCache == null) {
            questionIdToValidationsCache = CacheService.getInstance().getOrCreateCache("questionIdToValidationsCache",
                    new Duration(),
                    ModelChangeType.STUDY,
                    this.getClass());
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
                cacheActivityValidations(questionDto.getActivityId());
                validations = questionIdToValidationsCache.get(questionDto.getId());
            }
            return validations == null ? new ArrayList<>() : validations;
        }
    }

    private void cacheActivityValidations(Long activityId) {
        questionIdToValidationsCache.putAll(delegate.getAllActiveValidationsForActivity(activityId));
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
