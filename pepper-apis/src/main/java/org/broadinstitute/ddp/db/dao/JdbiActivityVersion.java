package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityVersion extends SqlObject {

    @SqlUpdate("insert into activity_version (study_activity_id, version_tag, revision_id)"
            + " values (:activityId, :versionTag, :revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("activityId") long activityId, @Bind("versionTag") String versionTag, @Bind("revisionId") long revisionId);

    @SqlQuery("select av.*, rev.start_date, rev.end_date"
            + " from activity_version as av"
            + " join revision as rev on rev.revision_id = av.revision_id"
            + " where av.study_activity_id = :activityId"
            + " order by rev.start_date asc")
    @RegisterRowMapper(ActivityVersionDto.ActivityVersionDtoMapper.class)
    List<ActivityVersionDto> findAllVersionsInAscendingOrder(long activityId);

    @SqlQuery("select av.*, rev.start_date, rev.end_date"
            + " from activity_version as av"
            + " join revision as rev on rev.revision_id = av.revision_id"
            + " where av.study_activity_id = :activityId and rev.end_date is null")
    @RegisterRowMapper(ActivityVersionDto.ActivityVersionDtoMapper.class)
    Optional<ActivityVersionDto> getActiveVersion(long activityId);

    @SqlQuery("select av.*, rev.start_date, rev.end_date"
            + " from activity_version as av"
            + " join revision as rev on rev.revision_id = av.revision_id"
            + " where av.activity_version_id = :versionId")
    @RegisterRowMapper(ActivityVersionDto.ActivityVersionDtoMapper.class)
    Optional<ActivityVersionDto> findById(@Bind("versionId") long versionId);

    // study-builder
    @SqlQuery("select ver.*, rev.start_date, rev.end_date"
            + " from activity_version as ver"
            + " join study_activity as act on act.study_activity_id = ver.study_activity_id"
            + " join revision as rev on rev.revision_id = ver.revision_id"
            + " where act.study_id = :studyId"
            + "   and act.study_activity_code = :activityCode"
            + "   and ver.version_tag = :versionTag")
    @RegisterRowMapper(ActivityVersionDto.ActivityVersionDtoMapper.class)
    Optional<ActivityVersionDto> findByActivityCodeAndVersionTag(@Bind("studyId") long studyId,
                                                                 @Bind("activityCode") String activityCode,
                                                                 @Bind("versionTag") String versionTag);

    @SqlQuery("select av.*, rev.start_date, rev.end_date"
            + "  from activity_version as av"
            + "  join revision as rev on rev.revision_id = av.revision_id"
            + "  join activity_instance as ai on ai.study_activity_id = av.study_activity_id"
            + " where ai.activity_instance_guid = :instanceGuid"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    @RegisterRowMapper(ActivityVersionDto.ActivityVersionDtoMapper.class)
    Optional<ActivityVersionDto> findByInstanceGuid(@Bind("instanceGuid") String instanceGuid);

    @SqlUpdate("update activity_version set revision_id = :revisionId where activity_version_id = :versionId")
    int updateRevisionIdById(long versionId, long revisionId);
}
