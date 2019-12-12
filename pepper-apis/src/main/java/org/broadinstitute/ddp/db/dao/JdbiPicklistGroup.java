package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiPicklistGroup extends SqlObject {

    @SqlUpdate("insert into picklist_group (picklist_question_id, group_stable_id, name_template_id, display_order, revision_id)"
            + " values (:picklistQuestionId, :groupStableId, :nameTemplateId, :displayOrder, :revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("picklistQuestionId") long questionId,
                @Bind("groupStableId") String stableId,
                @Bind("nameTemplateId") long nameTemplateId,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);
}
