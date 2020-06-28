package org.broadinstitute.ddp.db.dao;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.List;
import java.util.Optional;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusChangeDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

//@todo: not used or completed yet.
public class JdbiActivityInstanceCached extends SQLObjectWrapper<JdbiActivityInstance> implements JdbiActivityInstance {
    private static Cache<Long, ActivityInstanceDto> idToActivityInstanceDtoCache;
    private static Cache<String, Long> activityInstanceGuidToIdCache;

    public JdbiActivityInstanceCached(Handle handle) {
        super(handle, JdbiActivityInstance.class);
        initializeCache();
    }

    private void initializeCache() {
        idToActivityInstanceDtoCache = CacheService.getInstance().getOrCreateCache("idToActivityInstanceDtoCache",
                new Duration(MINUTES, 15), ModelChangeType.STUDY);
        activityInstanceGuidToIdCache = CacheService.getInstance().getOrCreateCache("activityInstanceGuidToIdCache",
                new Duration(MINUTES, 15), ModelChangeType.STUDY);
    }

    private void addToCache(ActivityInstanceDto dto) {
        idToActivityInstanceDtoCache.put(dto.getId(), dto);
        activityInstanceGuidToIdCache.put(dto.getGuid(), dto.getId());
    }

    private void removeFromCache(ActivityInstanceDto dto) {
        if (dto != null) {
            idToActivityInstanceDtoCache.remove(dto.getId());
            activityInstanceGuidToIdCache.remove(dto.getGuid());
        }
    }

    private void removeFromCache(long activityInstanceId) {
        idToActivityInstanceDtoCache.get(activityInstanceId);
    }

    private void removeFromCache(String activityInstanceGuid) {
        Long id = activityInstanceGuidToIdCache.getAndRemove(activityInstanceGuid);
        if (id != null) {
            idToActivityInstanceDtoCache.remove(id);
        }
    }


    @Override
    public List<ActivityInstanceStatusChangeDto> getActivityInstanceStatusChanges(long activityId, long participantId,
                                                                                  InstanceStatusType... statusType) {
        return delegate.getActivityInstanceStatusChanges(activityId, participantId, statusType);
    }

    @Override
    public long getActivityInstanceId(String activityInstanceGuid) {
        return getByActivityInstanceGuid(activityInstanceGuid).map(dto -> dto.getId()).orElse(0L);
    }

    @Override
    public String getActivityInstanceGuid(long activityInstanceId) {
        return getByActivityInstanceId(activityInstanceId).map(dto -> dto.getGuid()).orElse(null);
    }

    private String buildKey(String studyGuid, String activityCode, String userGuid) {
        return studyGuid + ":" + activityCode + ":" + userGuid;
    }

    @Override
    public long insert(long activityId, long participantId, String instanceGuid, Boolean isReadOnly, long createdAtMillis,
                       Long onDemandTriggerId) {
        return delegate.insert(activityId, participantId, instanceGuid, isReadOnly, createdAtMillis, onDemandTriggerId);
    }

    @Override
    public long insertLegacyInstance(long activityId, long participantId, String activityInstanceGuid, Boolean isReadOnly,
                                     long createdAtMillis, Long submissionId, String sessionId, String legacyVersion) {
        return delegate.insertLegacyInstance(activityId, participantId, activityInstanceGuid, isReadOnly, createdAtMillis, submissionId,
                sessionId, legacyVersion);
    }

    @Override
    public int delete(long id) {
        int val = delegate.delete(id);
        removeFromCache(idToActivityInstanceDtoCache.get(id));
        return val;
    }

    @Override
    public Optional<ActivityInstanceDto> getByActivityInstanceId(long activityInstanceId) {
        ActivityInstanceDto dto = idToActivityInstanceDtoCache.get(activityInstanceId);
        if (dto == null) {
            Optional<ActivityInstanceDto> optionalDto = delegate.getByActivityInstanceId(activityInstanceId);
            if (optionalDto.isPresent()) {
                addToCache(dto);
            }
            return optionalDto;
        } else {
            return Optional.ofNullable(dto);
        }
    }

    @Override
    public Optional<ActivityInstanceDto> getByActivityInstanceGuid(String activityInstanceGuid) {
        Long activityInstanceId = activityInstanceGuidToIdCache.get(activityInstanceGuid);
        ActivityInstanceDto dto = activityInstanceId == null ? null : idToActivityInstanceDtoCache.get(activityInstanceId);
        if (dto == null) {
            Optional<ActivityInstanceDto> optionalDto = delegate.getByActivityInstanceGuid(activityInstanceGuid);
            if (optionalDto.isPresent()) {
                addToCache(dto);
            }
            return optionalDto;
        } else {
            return Optional.ofNullable(dto);
        }
    }

    @Override
    public long getActivityIdByGuid(String guid) {
        return getByActivityInstanceGuid(guid).map(dto -> dto.getActivityId()).orElse(0L);
    }


    @Override
    public Optional<ActivityInstanceDto> getByUserAndInstanceGuids(String userGuid, String instanceGuid) {
        var optionalVal = delegate.getByUserAndInstanceGuids(userGuid, instanceGuid);
        optionalVal.ifPresent(dto -> addToCache(dto));
        return optionalVal;
    }


    @Override
    public int updateIsReadonlyByGuid(Boolean isReadonly, String activityInstanceGuid) {
        var val = delegate.updateIsReadonlyByGuid(isReadonly, activityInstanceGuid);
        removeFromCache(activityInstanceGuid);
        return val;

    }

    @Override
    public int updateFirstCompletedAtIfNotSet(long instanceId, long firstCompletedAtMillis) {
        var val = updateFirstCompletedAtIfNotSet(instanceId, firstCompletedAtMillis);
        removeFromCache(idToActivityInstanceDtoCache.get(instanceId));
        return val;
    }

    @Override
    public int deleteByInstanceGuid(String instanceGuid) {
        var val = delegate.deleteByInstanceGuid(instanceGuid);
        removeFromCache(activityInstanceGuidToIdCache.get(instanceGuid));
        return val;
    }

    @Override
    public int getNumActivitiesForParticipant(long studyActivityId, long participantId) {
        return delegate.getNumActivitiesForParticipant(studyActivityId, participantId);
    }

    @Override
    public int getCount() {
        return delegate.getCount();
    }

    @Override
    public List<ActivityInstanceDto> findAllByUserGuidAndActivityCode(String userGuid, String activityCode, long studyId) {
        var val = delegate.findAllByUserGuidAndActivityCode(userGuid, activityCode, studyId);
        val.forEach(dto -> addToCache(dto));
        return val;
    }

    @Override
    public List<ActivityInstanceDto> findAllByUserIdAndStudyId(long userId, long studyId) {
        var val = delegate.findAllByUserIdAndStudyId(userId, studyId);
        val.forEach(dto -> addToCache(dto));
        return val;
    }

    @Override
    public Optional<String> findLatestInstanceGuidFromUserGuidAndQuestionId(String userGuid, long questionId) {
        return delegate.findLatestInstanceGuidFromUserGuidAndQuestionId(userGuid, questionId);
    }

    @Override
    public List<Long> findsIdByUserGuidAndStudyGuid(String userGuid, String studyGuid) {
        return delegate.findsIdByUserGuidAndStudyGuid(userGuid, studyGuid);
    }

    @Override
    public List<Boolean> findIsReadonlyByUserGuidAndStudyGuid(String userGuid, String studyGuid) {
        return delegate.findIsReadonlyByUserGuidAndStudyGuid(userGuid, studyGuid);
    }

    @Override
    public Optional<Boolean> findIsReadonlyById(long id) {
        return delegate.findIsReadonlyById(id);
    }

    @Override
    public int makeActivityInstancesReadonly(List<Long> activityInstanceIds) {
        var val = delegate.makeActivityInstancesReadonly(activityInstanceIds);
        activityInstanceIds.forEach(activityInstanceId -> removeFromCache(activityInstanceId));
        return val;
    }

    @Override
    public int updateOndemandTriggerId(long userId, long activityInstanceId, long triggerId) {
        var val = delegate.updateOndemandTriggerId(userId, activityInstanceId, triggerId);
        removeFromCache(activityInstanceId);
        return val;
    }

    @Override
    public Optional<String> findLatestInstanceGuidByUserGuidAndCodesOfActivities(String userGuid, List<String> activityCodes,
                                                                                 long studyId) {
        return delegate.findLatestInstanceGuidByUserGuidAndCodesOfActivities(userGuid, activityCodes, studyId);
    }

    @Override
    public Optional<String> findLatestInstanceGuidByUserIdAndActivityId(long userId, long activityId) {
        return delegate.findLatestInstanceGuidByUserIdAndActivityId(userId, activityId);
    }

    @Override
    public Optional<Long> findLatestInstanceIdByUserGuidAndActivityId(String userGuid, long activityId) {
        return delegate.findLatestInstanceIdByUserGuidAndActivityId(userGuid, activityId);
    }

    @Override
    public Optional<String> findLatestInstanceGuidByUserGuidAndActivityId(String userGuid, long activityId) {
        return delegate.findLatestInstanceGuidByUserGuidAndActivityId(userGuid, activityId);
    }
}
