package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.ActivityFormGroupDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityGroupDao extends SqlObject {
    Logger LOG = LoggerFactory.getLogger(ActivityGroupDao.class);

    @SqlQuery("select * from form_group where activity_id = :activityId and section_id is null")
    @RegisterConstructorMapper(ActivityFormGroupDto.class)
    ActivityFormGroupDto findByOnlyActivityId(@Bind("activityId") long activityId);

    @SqlQuery("select * from form_group where section_id = :sectionId")
    @RegisterConstructorMapper(ActivityFormGroupDto.class)
    ActivityFormGroupDto findBySectionId(@Bind("sectionId") long sectionId);

    @SqlUpdate("insert into form_group (activity_id, section_id, form_code, form_name) values "
            + "(:activityId, :sectionId, :formCode, :formName)")
    @GetGeneratedKeys
    Long insert(@Bind("activityId") Long activityId, @Bind("sectionId") Long sectionId, @Bind("formCode") String formCode,
                @Bind("formName")  String formName);
}
