package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TimestampRevisioned;
import org.jdbi.v3.core.Handle;

public class PickListQuestionCachedDao extends SQLObjectWrapper<PicklistQuestionDao> implements PicklistQuestionDao {
    private static Cache<Long, GroupAndOptionDtos> questionIdToGroupAndOptionsCache;

    public PickListQuestionCachedDao(Handle handle) {
        super(handle, PicklistQuestionDao.class);
        initializeCache();
    }

    private void initializeCache() {
        questionIdToGroupAndOptionsCache = CacheService.getInstance().getOrCreateCache("questionIdToGroupAndOptionsCache",
                new Duration(),
                ModelChangeType.STUDY,
                this.getClass());
    }

    @Override
    public JdbiPicklistGroup getJdbiPicklistGroup() {
        return delegate.getJdbiPicklistGroup();
    }

    @Override
    public JdbiPicklistOption getJdbiPicklistOption() {
        return delegate.getJdbiPicklistOption();
    }

    @Override
    public JdbiPicklistGroupedOption getJdbiPicklistGroupedOption() {
        return delegate.getJdbiPicklistGroupedOption();
    }

    @Override
    public JdbiRevision getJdbiRevision() {
        return delegate.getJdbiRevision();
    }

    @Override
    public TemplateDao getTemplateDao() {
        return delegate.getTemplateDao();
    }

    private void cacheGroupAndOptionDtosForActivity(long activityId) {
        questionIdToGroupAndOptionsCache.putAll(findAllOrderedGroupAndOptionDtosByQuestion(activityId));
    }

    public GroupAndOptionDtos findOrderedGroupAndOptionDtos(QuestionDto questionDto, long timestamp) {
        if (isNullCache(questionIdToGroupAndOptionsCache)) {
            var map = delegate.findAllOrderedGroupAndOptionDtosByQuestion(questionDto.getActivityId());
            GroupAndOptionDtos unfilteredDtos = map.get(questionDto.getId());
            return buildFilteredGroupAndOptions(unfilteredDtos, timestamp);
        } else {
            GroupAndOptionDtos cachedDto = questionIdToGroupAndOptionsCache.get(questionDto.getId());
            if (cachedDto == null) {
                cacheGroupAndOptionDtosForActivity(questionDto.getActivityId());
                cachedDto = questionIdToGroupAndOptionsCache.get(questionDto.getId());
            }
            return cachedDto == null ? null : buildFilteredGroupAndOptions(cachedDto, timestamp);
        }
    }

    private GroupAndOptionDtos buildFilteredGroupAndOptions(GroupAndOptionDtos groupsAndOptions, long timestamp) {
        List<PicklistGroupDto> filteredGroups = filterByTimestamp(groupsAndOptions.getGroups(), timestamp);
        List<PicklistOptionDto> filteredUngroupedOptions = filterByTimestamp(groupsAndOptions.getUngroupedOptions(), timestamp);

        Map<Long, List<PicklistOptionDto>> filteredGroupIdToOptions = new HashMap<>();
        filteredGroups.forEach(group -> {
                    List<PicklistOptionDto> unfilteredOptions = groupsAndOptions.getGroupIdToOptions().get(group.getId());
                    List<PicklistOptionDto> filteredOptions = filterByTimestamp(unfilteredOptions, timestamp);
                    filteredGroupIdToOptions.put(group.getId(), filteredOptions);
                }
        );
        return new GroupAndOptionDtos(filteredGroups, filteredUngroupedOptions, filteredGroupIdToOptions);
    }

    private <T extends TimestampRevisioned> List<T> filterByTimestamp(Collection<T> coll, long timestamp) {
        Predicate<TimestampRevisioned> timestampFilter = (obj) -> obj.getRevisionStartTimestamp() <= timestamp
                && (obj.getRevisionEndTimestamp() == null || obj.getRevisionEndTimestamp() > timestamp);
        return coll.stream().filter(timestampFilter).collect(toList());
    }
}
