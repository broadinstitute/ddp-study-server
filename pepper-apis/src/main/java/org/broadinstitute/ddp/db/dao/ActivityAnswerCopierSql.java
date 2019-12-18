package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ActivityAnswerCopierSql extends SqlObject {

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("baseActivityAnswerCopy")
    long copyBaseAnswer(@Bind("answerGuid") String answerGuid,
            @Bind("sourceStableId") String sourceQuestionStableId,
                         @Bind("destinationStableId") String destinationQuestionStableId,
                         @Bind("sourceActivityInstanceId") long sourceInstanceId,
                         @Bind("destinationActivityInstanceId") long destinationInstanceId,
                         @Bind("createdAt") long createdAtEpochMillis,
                         @Bind("lastUpdatedAt") long lastUpdatedAtEpochMillis);

    // create two activity definitions, one source, one destination, copy and  test
}
