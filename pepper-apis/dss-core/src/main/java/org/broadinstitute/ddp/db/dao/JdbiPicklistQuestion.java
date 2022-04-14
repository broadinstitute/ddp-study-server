package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiPicklistQuestion extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByModeIds")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectModeId") long selectModeId,
               @Bind("renderModeId") long renderModeId,
               @Bind("picklistLabelTemplateId") Long picklistLabelTemplateId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByModeCodes")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectMode") PicklistSelectMode selectMode,
               @Bind("renderMode") PicklistRenderMode renderMode,
               @Bind("picklistLabelTemplateId") Long picklistLabelTemplateId);

}
