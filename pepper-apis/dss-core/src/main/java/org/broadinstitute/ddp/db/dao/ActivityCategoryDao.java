package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.ActivityFormGroupDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ActivityCategoryDao extends SqlObject {

    @SqlUpdate("insert into activity_category (study_id, category_code, category_name) "
            + "values (:studyId, :categoryCode, :categoryName)")
    @GetGeneratedKeys
    Long insertCategory(@Bind("studyId") Long studyId, @Bind("categoryCode") String categoryCode,
                        @Bind("categoryName") String categoryName);

    @SqlUpdate("insert into activity_category_group (category_id, parent_form_id, form_code, form_name) "
            + "values (:categoryId, :parentFormId, :categoryCode, :categoryName)")
    @GetGeneratedKeys
    Long insertCategoryGroup(@Bind("categoryId") Long categoryId, @Bind("parentFormId") Long parentFormId,
                             @Bind("categoryCode") String code, @Bind("categoryName") String name);


    @SqlQuery("select form_id from activity_category_group where form_code=:formCode")
    long findFormIdByCode(String formCode);


    @SqlQuery("select ac.category_code, ac.category_name, cg.form_code as form_code, cg.form_name as form_name,"
            + " cgs.form_code as parent_form_code from activity_group "
            + "         join activity_category_group cg on cg.form_id = activity_group.form_id "
            + "         join activity_category ac on ac.category_id = cg.category_id "
            + "         left outer join activity_category_group cgs on (cg.parent_form_id is not null and cgs.form_id = cg.parent_form_id)"
            + "where activity_id = :activityId")
    @RegisterConstructorMapper(ActivityFormGroupDto.class)
    ActivityFormGroupDto findByActivityId(@Bind("activityId") long activityId);

    @SqlUpdate("insert into activity_group (activity_id, form_id) values (:activityId, :formId)")
    @GetGeneratedKeys
    Long insertActivityGroup(@Bind("activityId") long activityId, @Bind("formId") long formId);
}
