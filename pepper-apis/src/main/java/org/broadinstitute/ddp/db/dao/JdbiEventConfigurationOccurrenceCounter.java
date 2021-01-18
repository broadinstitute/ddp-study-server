package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEventConfigurationOccurrenceCounter extends SqlObject {

    @SqlUpdate(
            "insert into event_configuration_occurrence_counter"
            + " (event_configuration_id, participant_user_id, num_occurrences)"
            + " values (:eventConfigurationId, :participantUserId, 0)"
    )
    int insert(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    @SqlQuery(
            "select num_occurrences from event_configuration_occurrence_counter"
            + " where event_configuration_id = :eventConfigurationId and participant_user_id = :participantUserId"
    )
    Optional<Integer> getNumOccurrences(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    default Integer getOrCreateNumOccurrences(long eventConfigurationId, long participantId) {
        return getNumOccurrences(eventConfigurationId, participantId).orElseGet(
                () -> {
                    insert(eventConfigurationId, participantId);
                    return 0;
                }
        );
    }

    @SqlUpdate(
            "update event_configuration_occurrence_counter set num_occurrences = num_occurrences + 1"
            + " where event_configuration_id = :eventConfigurationId and participant_user_id = :participantUserId"
    )
    void incNumOccurrences(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    @SqlUpdate(
            "delete from event_configuration_occurrence_counter"
            + " where event_configuration_id = :eventConfigurationId"
            + " and participant_user_id = :participantUserId"
    )
    int deleteById(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    @SqlUpdate(
            "delete from event_configuration_occurrence_counter"
                    + " where participant_user_id = :participantId")
    int deleteAllByParticipantId(
            @Bind("participantId") long participantId
    );

    @SqlUpdate("delete from event_configuration_occurrence_counter")
    int deleteAll();

}
