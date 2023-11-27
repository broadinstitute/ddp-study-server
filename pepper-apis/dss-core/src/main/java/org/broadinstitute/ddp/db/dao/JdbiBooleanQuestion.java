package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.BooleanRenderMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBooleanQuestion extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByRenderModeId")
    int insert(@Bind("questionId") long questionId, @Bind("trueTemplateId") long trueTemplateId,
               @Bind("falseTemplateId") long falseTemplateId, @Bind("renderMode") long renderModeId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByRenderModeCode")
    int insert(@Bind("questionId") long questionId, @Bind("trueTemplateId") long trueTemplateId,
            @Bind("falseTemplateId") long falseTemplateId, @Bind("renderMode") BooleanRenderMode renderMode);

}
