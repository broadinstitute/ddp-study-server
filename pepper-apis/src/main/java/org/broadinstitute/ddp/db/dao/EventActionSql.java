package org.broadinstitute.ddp.db.dao;

import java.util.Set;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface EventActionSql extends SqlObject {

    @SqlUpdate("insert into create_invitation_event_action"
            + "        (event_action_id, contact_email_question_stable_code_id, mark_existing_as_voided)"
            + " values (:actionId, :qscId, :markExistingAsVoided)")
    int insertCreateInvitationAction(
            @Bind("actionId") long actionId,
            @Bind("qscId") long contactEmailQuestionStableCodeId,
            @Bind("markExistingAsVoided") boolean markExistingAsVoided);

    @GetGeneratedKeys
    @SqlBatch("insert into event_action_target_activity (event_action_id, activity_id) values (:actionId, :activityId)")
    long[] insertTargetActivities(@Bind("actionId") long actionId, @Bind("activityId") Set<Long> targetActivityIds);
}
