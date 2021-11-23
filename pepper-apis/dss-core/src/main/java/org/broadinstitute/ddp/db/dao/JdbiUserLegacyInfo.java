package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserLegacyInfo extends SqlObject {


    @GetGeneratedKeys
    @SqlUpdate("insert into user_legacy_info(participant_user_id, release_survey_address) "
            + " values(:participantUserId, :releaseSurveyAddress)"
    )
    long insertUserLegacyInfo(
            @Bind("participantUserId") long participantUserId,
            @Bind("releaseSurveyAddress") String releaseSurveyAddress
    );


}
