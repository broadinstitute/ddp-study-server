package org.broadinstitute.ddp.db.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.util.RedisConnectionValidator;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class JdbiQuestionCached extends SQLObjectWrapper<JdbiQuestion> implements JdbiQuestion {
    private static RLocalCachedMap<Long, QuestionDto> questionIdToDtoCache;
    private static RLocalCachedMap<Long, List<String>> questionIdToTextSuggestionsCache;
    private static RLocalCachedMap<Long, Long> compositeChildIdToParentIdCache;
    private static RLocalCachedMap<String, List<Long>> compositeParentIdToChildIdsCache;
    private static RLocalCachedMap<String, Long> stableIdInstanceGuidToQuestionIdCache;

    public JdbiQuestionCached(Handle handle) {
        super(handle, JdbiQuestion.class);
        initializeCaching();
    }

    private void initializeCaching() {
        if (questionIdToDtoCache == null) {
            synchronized (getClass()) {
                CacheService cacheService = CacheService.getInstance();
                if (questionIdToDtoCache == null) {
                    questionIdToDtoCache = cacheService
                            .getOrCreateLocalCache("questionIdToDtoCache", 1000);
                }
                if (questionIdToTextSuggestionsCache == null) {
                    questionIdToTextSuggestionsCache = cacheService
                            .getOrCreateLocalCache("questionIdToTextSuggestions", 100);
                }
                if (compositeChildIdToParentIdCache == null) {
                    compositeChildIdToParentIdCache = cacheService
                            .getOrCreateLocalCache("compositeChildIdToParentIdCache", 1000);
                }
                if (compositeParentIdToChildIdsCache == null) {
                    compositeParentIdToChildIdsCache = cacheService
                            .getOrCreateLocalCache("compositeParentIdToChildIdsCache", 1000);
                }
                if (stableIdInstanceGuidToQuestionIdCache == null) {
                    stableIdInstanceGuidToQuestionIdCache = cacheService
                            .getOrCreateLocalCache("stableIdInstanceGuidToQuestionIdCache", 10_000);
                }
            }
        }
    }

    @Override
    public long insert(long questionTypeId, boolean isRestricted, long stableCodeId, long promptTemplateId, Long tooltipTemplateId,
                       Long infoHeaderTemplateId, Long infoFooterTemplateId, long revisionId, long activityId, boolean hideNumber,
                       boolean isDeprecated, boolean isWriteOnce) {
        return delegate.insert(questionTypeId, isRestricted, stableCodeId, promptTemplateId, tooltipTemplateId,
                infoHeaderTemplateId, infoFooterTemplateId, revisionId, activityId, hideNumber, isDeprecated, isWriteOnce);
    }

    @Override
    public Optional<QuestionDto> findLatestDtoByStudyIdAndQuestionStableId(long studyId, String questionStableId) {
        return delegate.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId);
    }

    @Override
    public Optional<QuestionDto> findLatestDtoByStudyGuidAndQuestionStableId(String studyGuid, String questionStableId) {
        return delegate.findLatestDtoByStudyGuidAndQuestionStableId(studyGuid, questionStableId);
    }

    @Override
    public List<QuestionDto> findByStudyGuid(String studyGuid) {
        return delegate.findByStudyGuid(studyGuid);
    }

    @Override
    public Optional<QuestionDto> findDtoByActivityIdAndQuestionStableId(long activityId, String questionStableId) {
        return delegate.findDtoByActivityIdAndQuestionStableId(activityId, questionStableId);
    }

    @Override
    public List<String> getActivityCodesByActivityInstanceSelectQuestionId(Long questionId) {
        return delegate.getActivityCodesByActivityInstanceSelectQuestionId(questionId);
    }

    @Override
    public Stream<QuestionDto> findLatestDtosByStudyIdAndQuestionStableIds(long studyId, Set<String> questionStableId) {
        return delegate.findLatestDtosByStudyIdAndQuestionStableIds(studyId, questionStableId);
    }

    @Override
    public int updateRevisionIdById(long questionId, long revisionId) {
        return delegate.updateRevisionIdById(questionId, revisionId);
    }

    @Override
    public int updateIsDeprecatedById(long questionId, boolean isDeprecated) {
        return delegate.updateIsDeprecatedById(questionId, isDeprecated);
    }

    @Override
    public int insertFileQuestion(long questionId, long maxFileSize) {
        return delegate.insertFileQuestion(questionId, maxFileSize);
    }

    @Override
    public long insertMimeType(String mimeTypeCode) {
        return delegate.insertMimeType(mimeTypeCode);
    }

    @Override
    public int insertFileQuestionMimeType(long fileQuestionId, long mimeTypeId) {
        return delegate.insertFileQuestionMimeType(fileQuestionId, mimeTypeId);
    }

    @Override
    public Optional<Long> findMimeTypeIdByMimeType(String mimeTypeCode) {
        return delegate.findMimeTypeIdByMimeType(mimeTypeCode);
    }

    @Override
    public Optional<Long> findIdByStableIdAndInstanceGuid(String stableId, String instanceGuid) {
        if (isNullCache(stableIdInstanceGuidToQuestionIdCache)) {
            return delegate.findIdByStableIdAndInstanceGuid(stableId, instanceGuid);
        } else {
            String key = stableId + ":" + instanceGuid;
            Long questionId = null;
            try {
                questionId = stableIdInstanceGuidToQuestionIdCache.get(key);
            } catch (RedisException e) {
                log.warn("Failed to retrieve value from Redis cache: " + stableIdInstanceGuidToQuestionIdCache.getName()
                        + " key:" + key + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (questionId == null) {
                questionId = delegate.findIdByStableIdAndInstanceGuid(stableId, instanceGuid).orElse(null);
                if (questionId != null) {
                    try {
                        stableIdInstanceGuidToQuestionIdCache.putAsync(key, questionId);
                    } catch (RedisException e) {
                        log.warn("Failed to store value to Redis cache: " + stableIdInstanceGuidToQuestionIdCache.getName(), e);
                    }
                }
            }

            return Optional.ofNullable(questionId);
        }
    }

    @Override
    public List<String> findTextQuestionSuggestions(long questionId) {
        if (isNullCache(questionIdToTextSuggestionsCache)) {
            return delegate.findTextQuestionSuggestions(questionId);
        } else {
            List<String> suggestions = null;
            try {
                suggestions = questionIdToTextSuggestionsCache.get(questionId);
            } catch (RedisException e) {
                log.warn("Failed to retrieve value from Redis cache: " + questionIdToTextSuggestionsCache.getName()
                        + " key:" + questionId + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (suggestions == null) {
                suggestions = delegate.findTextQuestionSuggestions(questionId);
                try {
                    questionIdToTextSuggestionsCache.putAsync(questionId, suggestions);
                } catch (RedisException e) {
                    log.warn("Failed to store value to Redis cache: " + questionIdToTextSuggestionsCache.getName(), e);
                }
            }

            return suggestions == null ? new ArrayList<>() : suggestions;
        }
    }

    @Override
    public Optional<Long> findCompositeParentIdByChildId(long childQuestionId) {
        if (isNullCache(compositeChildIdToParentIdCache)) {
            return delegate.findCompositeParentIdByChildId(childQuestionId);
        } else {
            Long parentId = null;
            try {
                parentId = compositeChildIdToParentIdCache.get(childQuestionId);
            } catch (RedisException e) {
                log.warn("Failed to retrieve value from Redis cache: " + compositeChildIdToParentIdCache.getName()
                        + " key:" + childQuestionId + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (parentId == null) {
                parentId = delegate.findCompositeParentIdByChildId(childQuestionId).orElse(null);
                if (parentId != null) {
                    try {
                        compositeChildIdToParentIdCache.put(childQuestionId, parentId);
                    } catch (RedisException e) {
                        log.warn("Failed to store value to Redis cache: " + compositeChildIdToParentIdCache.getName(), e);
                    }
                }
            }

            return Optional.ofNullable(parentId);
        }
    }

    @Override
    public Map<Long, List<Long>> collectOrderedCompositeChildIdsByParentIdsAndInstanceGuid(Iterable<Long> parentQuestionIds,
                                                                                           String instanceGuid) {
        if (isNullCache(compositeParentIdToChildIdsCache)) {
            return delegate.collectOrderedCompositeChildIdsByParentIdsAndInstanceGuid(parentQuestionIds, instanceGuid);
        } else {
            Set<Long> missingParentIds = new HashSet<>();
            Map<Long, List<Long>> result = new HashMap<>();

            for (var parentId : parentQuestionIds) {
                String key = parentId + ":" + instanceGuid;
                try {
                    var childIds = compositeParentIdToChildIdsCache.get(key);
                    if (childIds != null) {
                        result.put(parentId, childIds);
                    } else {
                        missingParentIds.add(parentId);
                    }
                } catch (RedisException e) {
                    log.warn("Failed to retrieve value from Redis cache: " + compositeParentIdToChildIdsCache.getName()
                            + " key:" + key + " Will try to retrieve from database", e);
                    missingParentIds.add(parentId);
                }
            }

            if (!missingParentIds.isEmpty()) {
                var moreChildIds = delegate.collectOrderedCompositeChildIdsByParentIdsAndInstanceGuid(missingParentIds, instanceGuid);
                try {
                    for (var entry : moreChildIds.entrySet()) {
                        long parentId = entry.getKey();
                        for (var childId : entry.getValue()) {
                            compositeChildIdToParentIdCache.put(childId, parentId);
                        }
                        compositeParentIdToChildIdsCache.put(parentId + ":" + instanceGuid, entry.getValue());
                    }
                } catch (RedisException e) {
                    log.warn("Failed to store values to Redis cache: " + compositeParentIdToChildIdsCache.getName(), e);
                    RedisConnectionValidator.doTest();
                }
                result.putAll(moreChildIds);
            }

            return result;
        }
    }

    @Override
    public Stream<CompositeIdPair> findOrderedCompositeChildIdsByParentIdsAndInstanceGuid(Iterable<Long> parentQuestionIds,
                                                                                          String instanceGuid) {
        return delegate.findOrderedCompositeChildIdsByParentIdsAndInstanceGuid(parentQuestionIds, instanceGuid);
    }

    @Override
    public Map<Long, List<Long>> collectOrderedCompositeChildIdsByParentIdsAndTimestamp(Iterable<Long> parentQuestionIds, long timestamp) {
        if (isNullCache(compositeParentIdToChildIdsCache)) {
            return delegate.collectOrderedCompositeChildIdsByParentIdsAndTimestamp(parentQuestionIds, timestamp);
        } else {
            Set<Long> missingParentIds = new HashSet<>();
            Map<Long, List<Long>> result = new HashMap<>();

            for (var parentId : parentQuestionIds) {
                String key = parentId + ":" + timestamp;
                try {
                    var childIds = compositeParentIdToChildIdsCache.get(key);
                    if (childIds != null) {
                        result.put(parentId, childIds);
                    } else {
                        missingParentIds.add(parentId);
                    }
                } catch (RedisException e) {
                    log.warn("Failed to retrieve value from Redis cache: " + compositeParentIdToChildIdsCache.getName()
                            + " key:" + key + " Will try to retrieve from database", e);
                    missingParentIds.add(parentId);
                }
            }

            if (!missingParentIds.isEmpty()) {
                var moreChildIds = delegate.collectOrderedCompositeChildIdsByParentIdsAndTimestamp(missingParentIds, timestamp);
                try {
                    for (var entry : moreChildIds.entrySet()) {
                        long parentId = entry.getKey();
                        for (var childId : entry.getValue()) {
                            compositeChildIdToParentIdCache.put(childId, parentId);
                        }
                        compositeParentIdToChildIdsCache.put(parentId + ":" + timestamp, entry.getValue());
                    }
                } catch (RedisException e) {
                    log.warn("Failed to store values to Redis cache: " + compositeParentIdToChildIdsCache.getName(), e);
                    RedisConnectionValidator.doTest();
                }
                result.putAll(moreChildIds);
            }

            return result;
        }
    }

    @Override
    public boolean deleteBaseQuestion(long questionId) {
        throw new NotImplementedException("Not implemented for cached version");
    }

    @Override
    public Stream<CompositeIdPair> findOrderedCompositeChildIdsByParentIdsAndTimestamp(Iterable<Long> parentQuestionIds, long timestamp) {
        return delegate.findOrderedCompositeChildIdsByParentIdsAndTimestamp(parentQuestionIds, timestamp);
    }

    @Override
    public Stream<QuestionDto> findQuestionDtosByIds(Iterable<Long> questionIds) {
        if (isNullCache(questionIdToDtoCache)) {
            return delegate.findQuestionDtosByIds(questionIds);
        } else {
            Set<Long> missingQuestionIds = new HashSet<>();
            Stream.Builder<QuestionDto> builder = Stream.builder();

            for (var questionId : questionIds) {
                try {
                    var questionDto = questionIdToDtoCache.get(questionId);
                    if (questionDto != null) {
                        builder.add(questionDto);
                    } else {
                        missingQuestionIds.add(questionId);
                    }
                } catch (RedisException e) {
                    log.warn("Failed to retrieve value from Redis cache: " + questionIdToDtoCache.getName()
                            + " key:" + questionId + " Will try to retrieve from database", e);
                    missingQuestionIds.add(questionId);
                }
            }

            if (!missingQuestionIds.isEmpty()) {
                Map<Long, QuestionDto> moreQuestionDtos;
                try (var stream = delegate.findQuestionDtosByIds(missingQuestionIds)) {
                    moreQuestionDtos = stream.collect(Collectors.toMap(QuestionDto::getId, Function.identity()));
                }
                try {
                    questionIdToDtoCache.putAllAsync(moreQuestionDtos);
                } catch (RedisException e) {
                    log.warn("Failed to store values to Redis cache: " + questionIdToDtoCache.getName(), e);
                    RedisConnectionValidator.doTest();
                }
                moreQuestionDtos.values().forEach(builder::add);
            }

            return builder.build();
        }
    }

    @Override
    public Optional<QuestionDto> findBasicQuestionDtoById(long questionId) {
        return delegate.findBasicQuestionDtoById(questionId);
    }
}
