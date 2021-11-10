package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiMatrixQuestion extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertMatrixByModeIds")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectModeId") long selectModeId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertMatrixByModeCodes")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectMode") MatrixSelectMode selectMode);

}
