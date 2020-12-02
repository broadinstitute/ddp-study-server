package org.broadinstitute.ddp.db.dao;

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

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.util.RedisConnectionValidator;
import org.jdbi.v3.core.Handle;
import org.redisson.api.RLocalCachedMap;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiQuestionCached extends SQLObjectWrapper<JdbiQuestion> implements JdbiQuestion {
    private static final Logger LOG = LoggerFactory.getLogger(JdbiQuestionCached.class);
    private static RLocalCachedMap<Long, QuestionDto> questionIdToDtoCache;
    private static RLocalCachedMap<Long, List<String>> questionIdToTextSuggestionsCache;
    private static RLocalCachedMap<Long, Long> compositeChildIdToParentIdCache;
    private static RLocalCachedMap<Long, List<Long>> compositeParentIdToChildIdsCache;
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
    public Optional<Long> findIdByStableIdAndInstanceGuid(String stableId, String instanceGuid) {
        if (isNullCache(stableIdInstanceGuidToQuestionIdCache)) {
            return delegate.findIdByStableIdAndInstanceGuid(stableId, instanceGuid);
        } else {
            String key = stableId + ":" + instanceGuid;
            Long questionId = null;
            try {
                questionId = stableIdInstanceGuidToQuestionIdCache.get(key);
            } catch (RedisException e) {
                LOG.warn("Failed to retrieve value from Redis cache: " + stableIdInstanceGuidToQuestionIdCache.getName()
                        + " key:" + key + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (questionId == null) {
                questionId = delegate.findIdByStableIdAndInstanceGuid(stableId, instanceGuid).orElse(null);
                if (questionId != null) {
                    try {
                        stableIdInstanceGuidToQuestionIdCache.putAsync(key, questionId);
                    } catch (RedisException e) {
                        LOG.warn("Failed to store value to Redis cache: " + stableIdInstanceGuidToQuestionIdCache.getName(), e);
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
                LOG.warn("Failed to retrieve value from Redis cache: " + questionIdToTextSuggestionsCache.getName()
                        + " key:" + questionId + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (suggestions == null) {
                suggestions = delegate.findTextQuestionSuggestions(questionId);
                try {
                    questionIdToTextSuggestionsCache.putAsync(questionId, suggestions);
                } catch (RedisException e) {
                    LOG.warn("Failed to store value to Redis cache: " + questionIdToTextSuggestionsCache.getName(), e);
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
                LOG.warn("Failed to retrieve value from Redis cache: " + compositeChildIdToParentIdCache.getName()
                        + " key:" + childQuestionId + " Will try to retrieve from database", e);
                RedisConnectionValidator.doTest();
            }

            if (parentId == null) {
                parentId = delegate.findCompositeParentIdByChildId(childQuestionId).orElse(null);
                if (parentId != null) {
                    try {
                        compositeChildIdToParentIdCache.put(childQuestionId, parentId);
                    } catch (RedisException e) {
                        LOG.warn("Failed to store value to Redis cache: " + compositeChildIdToParentIdCache.getName(), e);
                    }
                }
            }

            return Optional.ofNullable(parentId);
        }
    }

    @Override
    public Map<Long, List<Long>> collectOrderedCompositeChildIdsByParentIds(Iterable<Long> parentQuestionIds) {
        if (isNullCache(compositeParentIdToChildIdsCache)) {
            return delegate.collectOrderedCompositeChildIdsByParentIds(parentQuestionIds);
        } else {
            Set<Long> missingParentIds = new HashSet<>();
            Map<Long, List<Long>> result = new HashMap<>();

            for (var parentId : parentQuestionIds) {
                try {
                    var childIds = compositeParentIdToChildIdsCache.get(parentId);
                    if (childIds != null) {
                        result.put(parentId, childIds);
                    } else {
                        missingParentIds.add(parentId);
                    }
                } catch (RedisException e) {
                    LOG.warn("Failed to retrieve value from Redis cache: " + compositeParentIdToChildIdsCache.getName()
                            + " key:" + parentId + " Will try to retrieve from database", e);
                    missingParentIds.add(parentId);
                }
            }

            if (!missingParentIds.isEmpty()) {
                var moreChildIds = delegate.collectOrderedCompositeChildIdsByParentIds(missingParentIds);
                try {
                    compositeParentIdToChildIdsCache.putAll(moreChildIds);
                    for (var entry : moreChildIds.entrySet()) {
                        long parentId = entry.getKey();
                        for (var childId : entry.getValue()) {
                            compositeChildIdToParentIdCache.put(childId, parentId);
                        }
                    }
                } catch (RedisException e) {
                    LOG.warn("Failed to store values to Redis cache: " + compositeParentIdToChildIdsCache.getName(), e);
                    RedisConnectionValidator.doTest();
                }
                result.putAll(moreChildIds);
            }

            return result;
        }
    }

    @Override
    public Stream<CompositeIdPair> findOrderedCompositeChildIdsByParentIds(Iterable<Long> parentQuestionIds) {
        return delegate.findOrderedCompositeChildIdsByParentIds(parentQuestionIds);
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
                    LOG.warn("Failed to retrieve value from Redis cache: " + questionIdToDtoCache.getName()
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
                    LOG.warn("Failed to store values to Redis cache: " + questionIdToDtoCache.getName(), e);
                    RedisConnectionValidator.doTest();
                }
                moreQuestionDtos.values().forEach(builder::add);
            }

            return builder.build();
        }
    }
}
