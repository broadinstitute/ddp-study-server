package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.ActivityCategoryDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityCategoryDao extends SqlObject {
    Logger LOG = LoggerFactory.getLogger(ActivityCategoryDao.class);

    @SqlQuery("select * from activity_category where activity_id = :activityId")
    @RegisterConstructorMapper(ActivityCategoryDto.class)
    ActivityCategoryDto findByActivityId(@Bind("activityId") long activityId);

    @SqlUpdate("insert into activity_category (activity_id, activity_category_code, activity_category_name) "
            + "values (:activityId, :categoryCode, :categoryName)")
    @GetGeneratedKeys
    Long insert(@Bind("activityId") Long activityId, @Bind("categoryCode") String categoryCode, @Bind("categoryName") String categoryName);
}
