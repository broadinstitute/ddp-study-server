package org.broadinstitute.ddp.db.dao;

import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;

public class AnswerCachedDao extends SQLObjectWrapper<AnswerDao> implements AnswerDao {
    private static RLocalCachedMap<String, Long> activityInstanceGuidAndQuestionKeyToAnswerIdCache;
    private static RLocalCachedMap<Long, Answer> idToAnswerCache;

    private void initializeCache() {
        if (activityInstanceGuidAndQuestionKeyToAnswerIdCache == null) {
            synchronized (this.getClass()) {
                if (activityInstanceGuidAndQuestionKeyToAnswerIdCache == null) {
                    idToAnswerCache = CacheService.getInstance().getOrCreateLocalCache("idToAnswerCache", 10000);
                    activityInstanceGuidAndQuestionKeyToAnswerIdCache = CacheService.getInstance()
                            .getOrCreateLocalCache("activityInstanceGuidAndQuestionKeyToAnswerIdCache", 10000);
                }
            }
        }
    }

    public AnswerCachedDao(Handle handle) {
        super(handle, AnswerDao.class);
        initializeCache();
    }

    @Override
    public PicklistAnswerDao getPicklistAnswerDao() {
        return delegate.getPicklistAnswerDao();
    }

    @Override
    public JdbiCompositeAnswer getJdbiCompositeAnswer() {
        return delegate.getJdbiCompositeAnswer();
    }

    @Override
    public AnswerSql getAnswerSql() {
        return delegate.getAnswerSql();
    }

    @Override
    public Answer createAnswer(String operatorGuid, String instanceGuid, Answer answer) {
        return delegate.createAnswer(operatorGuid, instanceGuid, answer);
    }


    @Override
    public Answer createAnswer(long operatorId, long instanceId, Answer answer, QuestionDef questionDef) {
        return delegate.createAnswer(operatorId, instanceId, answer, questionDef);
    }

    @Override
    public Answer createAnswer(long operatorId, long instanceId, long questionId, Answer answer) {
        return delegate.createAnswer(operatorId, instanceId, questionId, answer);
    }

    @Override
    public void updateAnswer(long operatorId, long answerId, Answer newAnswer) {
        delegate.updateAnswer(operatorId, answerId, newAnswer);
        removeFromCache(answerId);
    }

    @Override
    public void updateAnswer(long operatorId, long answerId, Answer newAnswer, QuestionDef questionDef) {
        delegate.updateAnswer(operatorId, answerId, newAnswer, questionDef);
        removeFromCache(answerId);
    }

    @Override
    public void deleteAnswer(long answerId) {
        delegate.deleteAnswer(answerId);
        removeFromCache(answerId);
    }

    @Override
    public void deleteAnswers(Set<Long> answerIds) {
        delegate.deleteAnswers(answerIds);
        answerIds.forEach(this::removeFromCache);
    }

    @Override
    public void deleteAllByInstanceIds(Set<Long> instanceIds) {
        delegate.deleteAnswers(instanceIds);
        clearCaches();
    }

    @Override
    public Optional<Answer> findAnswerById(long answerId) {
        if (isNullCache(idToAnswerCache)) {
            return delegate.findAnswerById(answerId);
        } else {
            Answer answer = null;
            try {
                answer = idToAnswerCache.get(answerId);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + idToAnswerCache.getName() + " key lookedup:"
                        + answerId + "Will try to retrieve from database", e);
            }
            Optional<Answer> optAnswer;
            if (answer == null) {
                optAnswer = delegate.findAnswerById(answerId);
                addToCache(optAnswer);
            } else {
                optAnswer = Optional.of(answer);
            }
            return optAnswer;
        }
    }

    @Override
    public Optional<Answer> findAnswerByGuid(String answerGuid) {
        return delegate.findAnswerByGuid(answerGuid);
    }

    @Override
    public Optional<Answer> findAnswerByInstanceIdAndQuestionStableId(long instanceId, String questionStableId) {
        Optional<Answer> answerOpt = delegate.findAnswerByInstanceIdAndQuestionStableId(instanceId, questionStableId);
        addToCache(answerOpt);
        return answerOpt;
    }

    @Override
    public Optional<Answer> findAnswerByInstanceGuidAndQuestionStableId(String instanceGuid, String questionStableId) {
        if (isNullCache(idToAnswerCache)) {
            return delegate.findAnswerByInstanceGuidAndQuestionStableId(instanceGuid, questionStableId);
        } else {
            Answer answer = null;
            Long answerId = null;
            String key = buildKey(instanceGuid, questionStableId);
            try {
                answerId = activityInstanceGuidAndQuestionKeyToAnswerIdCache.get(key);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + activityInstanceGuidAndQuestionKeyToAnswerIdCache.getName()
                        + " key lookedup:" + key + "Will try to retrieve from database", e);
            }
            if (answerId != null) {
                try {
                    answer = idToAnswerCache.get(answerId);
                } catch (RedisException e) {
                    LOG.warn("Failed to retrieve value from Redis cache: " + idToAnswerCache.getName()
                            + " key lookedup:" + answerId + "Will try to retrieve from database", e);
                }
            }
            if (answer == null) {
                Optional<Answer> answerOpt = delegate.findAnswerByInstanceGuidAndQuestionStableId(instanceGuid, questionStableId);
                addToCache(answerOpt);
                return answerOpt;
            } else {
                return Optional.ofNullable(answer);
            }
        }
    }

    @Override
    public Optional<Answer> findAnswerByLatestInstanceAndQuestionStableId(long userId, long studyId, String questionStableId) {
        return delegate.findAnswerByLatestInstanceAndQuestionStableId(userId, studyId, questionStableId);
    }

    private void addToCache(Answer answer) {
        if (!isNullCache(idToAnswerCache)) {
            try {
                idToAnswerCache.putAsync(answer.getAnswerId(), answer);
            } catch (RedisException e) {
                LOG.warn("Failed to save to Redis cache: " + idToAnswerCache.getName() + " with key:" + answer.getAnswerId(), e);
            }

            String key = buildKey(answer);
            if (key != null) {
                try {
                    activityInstanceGuidAndQuestionKeyToAnswerIdCache.putAsync(key, answer.getAnswerId());
                } catch (RedisException e) {
                    LOG.warn("Failed to save to Redis cache: " + idToAnswerCache.getName() + " with key:" + key, e);
                }
            }
        }
    }

    private void addToCache(Optional<Answer> optAnswer) {
        if (optAnswer.isPresent()) {
            Answer answer = optAnswer.get();
            addToCache(answer);
        }
    }

    private void removeFromCache(Long id) {
        if (!isNullCache(idToAnswerCache)) {
            Answer answerFromCache = idToAnswerCache.remove(id);
            String cacheKey = buildKey(answerFromCache);
            if (cacheKey != null) {
                activityInstanceGuidAndQuestionKeyToAnswerIdCache.remove(cacheKey);
            }
            if (answerFromCache instanceof CompositeAnswer) {
                ((CompositeAnswer)answerFromCache).getValue().forEach(
                        row -> row.getValues().forEach(val -> removeFromCache(val.getAnswerId())));
            }
        }
    }

    private void clearCaches() {
        if (!isNullCache(idToAnswerCache)) {
            idToAnswerCache.clear();
            activityInstanceGuidAndQuestionKeyToAnswerIdCache.clear();
        }
    }

    private String buildKey(Answer answer) {
        return answer == null ? null : buildKey(answer.getActivityInstanceGuid(), answer.getQuestionStableId());
    }

    private String buildKey(String activityInstanceGuid, String questionStableId) {
        return activityInstanceGuid + ":" + questionStableId;
    }

}
