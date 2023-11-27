package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.util.RedisConnectionValidator;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiQuestionValidationCached extends SQLObjectWrapper<JdbiQuestionValidation> implements JdbiQuestionValidation {
    private static final Logger LOG = LoggerFactory.getLogger(JdbiQuestionValidationCached.class);
    private static RLocalCachedMap<Long, Map<Long, List<RuleDto>>> activityIdToValidationsCache;

    private void initializeCaching() {
        if (activityIdToValidationsCache == null) {
            synchronized (this.getClass()) {
                if (activityIdToValidationsCache == null) {
                    activityIdToValidationsCache = CacheService.getInstance()
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
        return isNullCache(activityIdToValidationsCache);
    }

    @Override
    public long insert(long questionId, long validationId) {
        return delegate.insert(questionId, validationId);
    }

    @Override
    public List<RuleDto> getAllActiveValidations(long questionId) {
        return delegate.getAllActiveValidations(questionId);
    }

    @Override
    public List<RuleDto> getAllActiveValidations(QuestionDto questionDto) {
        if (isNullCache()) {
            return delegate.getAllActiveValidations(questionDto);
        } else {
            Map<Long, List<RuleDto>> questionIdToValidations = null;
            try {
                questionIdToValidations = activityIdToValidationsCache.get(questionDto.getActivityId());
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + activityIdToValidationsCache.getName() + " key lookedup:"
                        + questionDto.getId() + "Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }
            if (questionIdToValidations == null) {
                questionIdToValidations = cacheActivityValidations(questionDto.getActivityId());
            }
            List<RuleDto> validations = questionIdToValidations.get(questionDto.getId());
            return validations == null ? new ArrayList<>() : validations;
        }
    }

    private Map<Long, List<RuleDto>> cacheActivityValidations(Long activityId) {
        Map<Long, List<RuleDto>> dataToCache = delegate.getAllActiveValidationsForActivity(activityId);
        try {
            activityIdToValidationsCache.putAllAsync(Map.of(activityId, dataToCache));
        } catch (RedisException e) {
            LOG.warn("Failed to cache data to Redis: " + activityIdToValidationsCache.getName() + " to key" + activityId, e);
            RedisConnectionValidator.doTest();
        }

        return dataToCache;
    }

    @Override
    public Stream<RuleDto> getAllActiveValidationDtosForActivity(long activityId) {
        return delegate.getAllActiveValidationDtosForActivity(activityId);
    }

    @Override
    public Optional<RuleDto> getRequiredValidationIfActive(long questionId) {
        return delegate.getRequiredValidationIfActive(questionId);
    }

    @Override
    public Stream<RuleDto> findDtosByQuestionIdsAndTimestamp(Iterable<Long> questionIds, long timestamp) {
        return delegate.findDtosByQuestionIdsAndTimestamp(questionIds, timestamp);
    }

    @Override
    public boolean deleteForQuestion(long questionId) {
        throw new NotImplementedException("Not implemented yet in cached version");
    }
}
