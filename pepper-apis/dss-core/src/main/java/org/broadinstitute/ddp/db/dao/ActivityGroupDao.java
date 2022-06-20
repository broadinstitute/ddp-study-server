package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityGroupDao extends SqlObject {
    Logger LOG = LoggerFactory.getLogger(ActivityGroupDao.class);

    @SqlUpdate("insert into activity_group (activity_id, section_id, form_code, form_name) values "
            + "(:activityId, :sectionId, :formCode, :formName)")
    @GetGeneratedKeys
    Long insert(@Bind("activityId") Long activityId, @Bind("sectionId") Long sectionId, @Bind("formCode") String formCode,
                @Bind("formName")  String formName);
}
