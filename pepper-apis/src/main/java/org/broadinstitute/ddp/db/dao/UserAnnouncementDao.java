package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserAnnouncementDao extends SqlObject {

    String TABLE_NAME = "user_announcement";
    String GUID_COLUMN = "guid";

    @SqlUpdate("insert into user_announcement (guid, participant_user_id, study_id, message_template_id, is_permanent, created_at)"
            + " values (:guid, :participantUserId, :studyId, :msgTemplateId, :permanent, :createdAt)")
    @GetGeneratedKeys
    long insert(@Bind("guid") String guid,
                @Bind("participantUserId") long participantUserId,
                @Bind("studyId") long studyId,
                @Bind("msgTemplateId") long msgTemplateId,
                @Bind("permanent") boolean isPermanent,
                @Bind("createdAt") long createdAtMillis);

    default long insert(long participantUserId, long studyId, long msgTemplateId, boolean isPermanent) {
        String guid = DBUtils.uniqueUUID4(getHandle(), TABLE_NAME, GUID_COLUMN);
        return insert(guid, participantUserId, studyId, msgTemplateId, isPermanent, Instant.now().toEpochMilli());
    }

    @SqlUpdate("delete from user_announcement where participant_user_id = :participantUserId and study_id = :studyId")
    int deleteAllForParticipantAndStudy(@Bind("participantUserId") long participantUserId, @Bind("studyId") long studyId);

    @SqlUpdate("delete from user_announcement where user_announcement_id in (<ids>)")
    int deleteByIds(@BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) Set<Long> ids);

    @SqlQuery("select * from user_announcement"
            + " where participant_user_id = :participantUserId and study_id = :studyId"
            + " order by created_at asc")
    @RegisterConstructorMapper(UserAnnouncement.class)
    Stream<UserAnnouncement> findAllForParticipantAndStudy(@Bind("participantUserId") long participantUserId,
                                                           @Bind("studyId") long studyId);
}
