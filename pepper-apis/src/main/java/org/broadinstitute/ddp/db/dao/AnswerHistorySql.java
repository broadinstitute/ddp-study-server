package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface AnswerHistorySql extends SqlObject {

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("saveBaseAnswer")
    long saveBaseAnswer(@Bind("answerId") long answerId);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("saveAnswerWithSimpleValue")
    long saveAnswerWithSimpleValue(@Bind("answerId") long answerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("saveAnswerPicklistValue")
    int saveAnswerPicklistValue(@Bind("answerId") long answerId, @Bind("answerHistId") long answerHistId);
}
