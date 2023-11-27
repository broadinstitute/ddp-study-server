package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.jdbi.v3.core.Handle;
import org.redisson.client.RedisException;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatrixQuestionCachedDao extends SQLObjectWrapper<MatrixQuestionDao> implements MatrixQuestionDao {
    private static Cache<String, GroupOptionRowDtos> questionVersionKeyToGroupOptionRowCache;

    public MatrixQuestionCachedDao(Handle handle) {
        super(handle, MatrixQuestionDao.class);
        initializeCache();
    }

    private void initializeCache() {
        if (questionVersionKeyToGroupOptionRowCache == null) {
            questionVersionKeyToGroupOptionRowCache = CacheService.getInstance().getOrCreateCache(
                    "questionVersionKeyToGroupOptionRowCache",
                    new Duration(),
                    ModelChangeType.STUDY,
                    this.getClass());
        }
    }

    @Override
    public JdbiMatrixGroup getJdbiMatrixGroup() {
        return delegate.getJdbiMatrixGroup();
    }

    @Override
    public JdbiMatrixOption getJdbiMatrixOption() {
        return delegate.getJdbiMatrixOption();
    }

    @Override
    public JdbiMatrixRow getJdbiMatrixQuestion() {
        return delegate.getJdbiMatrixQuestion();
    }

    @Override
    public JdbiRevision getJdbiRevision() {
        return delegate.getJdbiRevision();
    }

    @Override
    public TemplateDao getTemplateDao() {
        return delegate.getTemplateDao();
    }

    @Override
    public Map<Long, GroupOptionRowDtos> findOrderedGroupOptionRowDtos(Iterable<Long> questionIds, long timestamp) {
        if (isNullCache(questionVersionKeyToGroupOptionRowCache)) {
            return delegate.findOrderedGroupOptionRowDtos(questionIds, timestamp);
        } else {
            Set<Long> missingQuestionIds = new HashSet<>();
            Map<Long, GroupOptionRowDtos> result = new HashMap<>();

            for (var questionId : questionIds) {
                String key = questionId + ":" + timestamp;
                try {
                    GroupOptionRowDtos cachedDto = questionVersionKeyToGroupOptionRowCache.get(key);
                    if (cachedDto != null) {
                        result.put(questionId, cachedDto);
                    } else {
                        missingQuestionIds.add(questionId);
                    }
                } catch (RedisException e) {
                    LOG.warn("Failed to retrieve value from Redis cache: " + questionVersionKeyToGroupOptionRowCache.getName()
                            + " key:" + key + " Will try to retrieve from database", e);
                    missingQuestionIds.add(questionId);
                }
            }

            if (!missingQuestionIds.isEmpty()) {
                var found = delegate.findOrderedGroupOptionRowDtos(missingQuestionIds, timestamp);
                try {
                    found.forEach((questionId, dto) -> {
                        String key = questionId + ":" + timestamp;
                        questionVersionKeyToGroupOptionRowCache.put(key, dto);
                    });
                } catch (RedisException e) {
                    LOG.warn("Failed to store values to Redis cache: " + questionVersionKeyToGroupOptionRowCache.getName(), e);
                }
                result.putAll(found);
            }

            return result;
        }
    }
}
