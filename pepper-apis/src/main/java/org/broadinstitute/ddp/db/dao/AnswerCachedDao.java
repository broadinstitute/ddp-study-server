package org.broadinstitute.ddp.db.dao;

import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;

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
    public Answer createAnswer(long operatorId, long instanceId, Answer answer) {
        return delegate.createAnswer(operatorId, instanceId, answer);
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
            Optional<Answer> optAnswer = Optional.ofNullable(idToAnswerCache.get(answerId));
            if (optAnswer.isEmpty()) {
                optAnswer = delegate.findAnswerById(answerId);
                addToCache(optAnswer);
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
            Long answerId = activityInstanceGuidAndQuestionKeyToAnswerIdCache.get(buildKey(instanceGuid, questionStableId));
            if (answerId != null) {
                answer = idToAnswerCache.get(answerId);
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
            idToAnswerCache.putAsync(answer.getAnswerId(), answer);
            String key = buildKey(answer);
            if (key != null) {
                activityInstanceGuidAndQuestionKeyToAnswerIdCache.putAsync(key, answer.getAnswerId());
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
