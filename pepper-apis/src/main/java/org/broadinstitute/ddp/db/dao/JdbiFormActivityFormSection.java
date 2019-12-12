package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiFormActivityFormSection extends SqlObject {

    @SqlUpdate("insert into form_activity__form_section"
            + " (form_activity_id,form_section_id,revision_id,display_order) values(?,?,?,?)")
    @GetGeneratedKeys()
    long insert(long activityId, long formSectionId, long revisionId, long sectionOrder);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedBodySectionIdsByInstanceGuid")
    List<Long> getOrderedBodySectionIds(@Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select fa_fs.form_section_id"
            + "  from form_activity__form_section as fa_fs"
            + "  join revision as rev on fa_fs.revision_id = rev.revision_id"
            + " where fa_fs.form_activity_id = :activityId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)"
            + " order by fa_fs.display_order asc")
    List<Long> findOrderedSectionIdsByActivityIdAndTimestamp(@Bind("activityId") long activityId,
                                                             @Bind("timestamp") long timestamp);
}
