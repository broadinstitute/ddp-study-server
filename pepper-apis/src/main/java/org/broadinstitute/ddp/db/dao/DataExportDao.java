package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.export.DataSyncRequest;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface DataExportDao extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into data_sync_request (user_id, study_id, refresh_user_email) values (:userId, :studyId, false)")
    long queueDataSync(@Bind("userId") long userId, @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into data_sync_request (user_id, study_id, refresh_user_email)"
            + " values ((select user_id from user where guid = :userGuid),"
            + " (select umbrella_study_id from umbrella_study where guid = :studyGuid), false)")
    long queueDataSync(@Bind("userGuid") String userGuid, @Bind("studyGuid") String studyGuid);

    @GetGeneratedKeys
    @SqlUpdate("insert into data_sync_request (user_id, study_id, refresh_user_email) values (:userId, null, false)")
    long queueDataSync(@Bind("userId") long userId);

    @GetGeneratedKeys
    @SqlUpdate("insert into data_sync_request (user_id, study_id, refresh_user_email)"
            + " values ((select user_id from user where guid = :userGuid), null, false)")
    long queueDataSync(@Bind("userGuid") String userGuid);

    @GetGeneratedKeys
    @SqlUpdate("insert into data_sync_request (user_id, study_id, refresh_user_email) values (:userId, null, :refreshUserEmail)")
    long queueDataSync(@Bind("userId") long userId, @Bind("refreshUserEmail") boolean refreshUserEmail);

    @SqlQuery("select * from data_sync_request order by data_sync_request_id desc")
    @RegisterConstructorMapper(DataSyncRequest.class)
    List<DataSyncRequest> findLatestDataSyncRequests();

    @SqlUpdate("delete from data_sync_request where data_sync_request_id <= :requestId")
    int deleteDataSyncRequestsAtOrOlderThan(@Bind("requestId") long requestId);

    @SqlUpdate("delete from data_sync_request where user_id = :userId")
    int deleteDataSyncRequestsForUser(@Bind("userId") long userId);

    @SqlUpdate("delete from data_sync_request where user_id in (<userIds>)")
    int deleteDataSyncRequestsForUsers(@BindList(value = "userIds", onEmpty = EmptyHandling.NULL) Set<Long> userIds);
}
