package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEventConfigurationOccurrenceCounter extends SqlObject {
    String NUM_OCCURRENCES_SELECT_SQL = "select num_occurrences from event_configuration_occurrence_counter"
            + " where event_configuration_id = :eventConfigurationId and participant_user_id = :participantUserId";

    @SqlUpdate(
            "insert into event_configuration_occurrence_counter"
                    + " (event_configuration_id, participant_user_id, num_occurrences)"
                    + " values (:eventConfigurationId, :participantUserId, 0)"
    )
    int insert(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    @SqlQuery(NUM_OCCURRENCES_SELECT_SQL)
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

    default void incNumOccurrences(long eventConfigurationId, long participantUserId) {
        _getNumOccurrencesForUpdate(eventConfigurationId, participantUserId)
                .ifPresent(previousCounterValue ->
                        _updateNumOccurrences(eventConfigurationId, participantUserId, previousCounterValue + 1));
    }

    // private
    @SqlQuery(NUM_OCCURRENCES_SELECT_SQL + " FOR UPDATE")
    Optional<Integer> _getNumOccurrencesForUpdate(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId
    );

    // private
    @SqlUpdate(
            "update event_configuration_occurrence_counter set num_occurrences = :newCounterValue"
                    + " where event_configuration_id = :eventConfigurationId and participant_user_id = :participantUserId"
    )
    void _updateNumOccurrences(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("participantUserId") long participantUserId,
            @Bind("newCounterValue") Integer newCounterValue
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
