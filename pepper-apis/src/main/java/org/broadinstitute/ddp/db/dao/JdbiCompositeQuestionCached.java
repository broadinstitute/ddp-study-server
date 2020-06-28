package org.broadinstitute.ddp.db.dao;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.Handle;

public class JdbiCompositeQuestionCached extends SQLObjectWrapper<JdbiCompositeQuestion> implements JdbiCompositeQuestion {
    private Cache<Long, CompositeQuestionDto> idToCompositeQuestionDtoCache;
    private Cache<Long, List<Long>> activityIdToCompositeQuestionIdsCache;
    private Cache<Long, Long> compositeChildQuestionIdToParentIdCache;

    public JdbiCompositeQuestionCached(Handle handle) {
        super(handle, JdbiCompositeQuestion.class);
        initializeCache();
    }

    private void initializeCache() {
        idToCompositeQuestionDtoCache = CacheService.getInstance().getOrCreateCache("idToCompositeQuestionDtoCache",
                new Duration(MINUTES, 15), ModelChangeType.STUDY);
        activityIdToCompositeQuestionIdsCache = CacheService.getInstance().getOrCreateCache("activityIdToCompositeQuestionIdsCache",
                new Duration(MINUTES, 15), ModelChangeType.STUDY);
        compositeChildQuestionIdToParentIdCache = CacheService.getInstance()
                .getOrCreateCache("compositeChildQuestionIdToParentIdCache", new Duration(MINUTES, 15),
                        ModelChangeType.STUDY);
    }

    @Override
    public Optional<CompositeQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
        if (isNullCache(idToCompositeQuestionDtoCache)) {
            return delegate.findDtoByQuestionId(questionDto.getId());
        } else {
            CompositeQuestionDto dto = idToCompositeQuestionDtoCache.get(questionDto.getId());
            if (dto == null) {
                cacheDtosForActivity(questionDto.getActivityId());
                dto = idToCompositeQuestionDtoCache.get(questionDto.getId());
            }
            return Optional.ofNullable(dto);
        }
    }

    @Override
    public Optional<Long> findParentQuestionIdByChildQuestion(QuestionDto questionDto) {
        if (isNullCache(idToCompositeQuestionDtoCache)) {
            return delegate.findParentQuestionIdByChildQuestion(questionDto);
        } else {
            List<Long> activityCompositeDtos = activityIdToCompositeQuestionIdsCache.get(questionDto.getActivityId());
            Long parentId;
            if (activityCompositeDtos != null) {
                parentId = compositeChildQuestionIdToParentIdCache.get(questionDto.getId());
            } else {
                cacheDtosForActivity(questionDto.getActivityId());
                parentId = compositeChildQuestionIdToParentIdCache.get(questionDto.getId());
            }
            return Optional.of(parentId);
        }
    }

    private void cacheDtosForActivity(long activityId) {
        List<CompositeQuestionDto> dtos = delegate.findDtosByActivityId(activityId);

        dtos.forEach(dto -> {
            idToCompositeQuestionDtoCache.put(dto.getId(), dto);
            dto.getChildQuestions().forEach(childDto -> compositeChildQuestionIdToParentIdCache.put(childDto.getId(), dto.getId()));
        });

        activityIdToCompositeQuestionIdsCache.put(activityId, dtos.stream().map(dto -> dto.getId()).collect(toList()));
    }

    @Override
    public Optional<CompositeQuestionDto> findParentDtoByChildQuestionId(long childQuestionId) {
        return delegate.findParentDtoByChildQuestionId(childQuestionId);
    }

    @Override
    public Optional<Long> findParentQuestionIdByChildQuestionId(long childQuestionId) {
        return delegate.findParentQuestionIdByChildQuestionId(childQuestionId);
    }

    @Override
    public Optional<CompositeQuestionDto> findDtoByInstanceGuidAndStableId(String activityInstanceGuid, String questionStableId) {
        return delegate.findDtoByInstanceGuidAndStableId(activityInstanceGuid, questionStableId);
    }

    @Override
    public int insertParent(long compositeQuestionId, boolean allowMultiple, Long addButtonTemplateId, Long itemTemplateId,
                            OrientationType childOrientation, boolean unwrapOnExport) {
        return delegate.insertParent(compositeQuestionId, allowMultiple, addButtonTemplateId, itemTemplateId, childOrientation,
                unwrapOnExport);
    }

    @Override
    public void insertChildren(long compositeQuestionId, List<Long> childQuestionIds) {
        delegate.insertChildren(compositeQuestionId, childQuestionIds);
    }

    @Override
    public void insertChild(long parentQuestionId, List<Long> childQuestionIds, List<Integer> orderIdxs) {
        delegate.insertChild(parentQuestionId, childQuestionIds, orderIdxs);
    }

    @Override
    public Optional<CompositeQuestionDto> findDtoByQuestionId(long questionId) {
        return delegate.findDtoByQuestionId(questionId);
    }

    @Override
    public List<CompositeQuestionDto> findDtosByActivityId(long activityId) {
        if (isNullCache(idToCompositeQuestionDtoCache)) {
            return delegate.findDtosByActivityId(activityId);
        } else {
            List<Long> compDtoIds = activityIdToCompositeQuestionIdsCache.get(activityId);
            if (compDtoIds == null) {
                cacheDtosForActivity(activityId);
                compDtoIds = activityIdToCompositeQuestionIdsCache.get(activityId);
            }
            if (compDtoIds != null) {
                return compDtoIds.stream().map(dtoId ->
                        idToCompositeQuestionDtoCache.get(dtoId)).filter(dto -> dto != null).collect(toList());
            } else {
                return Collections.emptyList();
            }
        }
    }
}
