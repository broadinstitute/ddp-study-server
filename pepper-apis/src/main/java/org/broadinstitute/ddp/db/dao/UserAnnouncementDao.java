package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserAnnouncementDao extends SqlObject {

    @SqlUpdate("insert into user_announcement (participant_user_id, study_id, message_template_id, created_at)"
            + " values (:participantUserId, :studyId, :msgTemplateId, :createdAt)")
    @GetGeneratedKeys
    long insert(@Bind("participantUserId") long participantUserId,
                @Bind("studyId") long studyId,
                @Bind("msgTemplateId") long msgTemplateId,
                @Bind("createdAt") long createdAtMillis);

    default long insert(long participantUserId, long studyId, long msgTemplateId) {
        return insert(participantUserId, studyId, msgTemplateId, Instant.now().toEpochMilli());
    }

    @SqlUpdate("delete from user_announcement where participant_user_id = :participantUserId and study_id = :studyId")
    int deleteAllForParticipantAndStudy(@Bind("participantUserId") long participantUserId, @Bind("studyId") long studyId);

    @SqlUpdate("delete from user_announcement where user_announcement_id in (<ids>)")
    int deleteByIds(@BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) Set<Long> ids);

    @SqlQuery("select user_announcement_id as id, participant_user_id, study_id, message_template_id, created_at"
            + "  from user_announcement"
            + " where participant_user_id = :participantUserId and study_id = :studyId"
            + " order by created_at asc")
    @RegisterConstructorMapper(UserAnnouncement.class)
    Stream<UserAnnouncement> findAllForParticipantAndStudy(@Bind("participantUserId") long participantUserId,
                                                           @Bind("studyId") long studyId);
}
