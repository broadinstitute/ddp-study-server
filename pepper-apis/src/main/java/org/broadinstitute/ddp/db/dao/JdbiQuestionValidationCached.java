package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiQuestionValidationCached extends SQLObjectWrapper<JdbiQuestionValidation> implements JdbiQuestionValidation {
    private static final Logger LOG = LoggerFactory.getLogger(JdbiQuestionValidationCached.class);
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
            List<ValidationDto> validations = null;
            try {
                validations = questionIdToValidationsCache.get(questionDto.getId());
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + questionIdToValidationsCache.getName() + " key lookedup:"
                        + questionDto.getId() + "Will try to retrieve from database", e);
            }
            if (validations == null) {
                Map<Long, List<ValidationDto>> data = cacheActivityValidations(questionDto.getActivityId());
                validations = data.get(questionDto.getId());
            }
            return validations == null ? new ArrayList<>() : validations;
        }
    }

    private Map<Long, List<ValidationDto>> cacheActivityValidations(Long activityId) {
        Map<Long, List<ValidationDto>> dataToCache = delegate.getAllActiveValidationsForActivity(activityId);
        try {
            questionIdToValidationsCache.putAllAsync(dataToCache);
        } catch (RedisException e) {
            LOG.warn("Failed to cache data to Redis: " + questionIdToValidationsCache.getName() + " to key" + activityId, e);
        }

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

    @Override
    public Stream<ValidationDto> findDtosByQuestionIdsAndTimestamp(Set<Long> questionIds, long timestamp) {
        return delegate.findDtosByQuestionIdsAndTimestamp(questionIds, timestamp);
    }
}
