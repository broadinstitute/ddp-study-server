package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessorDefault;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Slf4j
public class OsteoDdp7601 implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Config studyCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);

        SqlHelper sqlHelper = handle.attach(SqlHelper.class);
        List<SqlHelper.UserInfo> users = sqlHelper.getUsersInfo();
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        Set<Long> consentIds = new HashSet<>();
        Set<Long> toHide = new HashSet<>();
        for (var user : users) {
            consentIds.addAll(user.getConsents().keySet());
            for (Long id : user.getConsents().values()) {
                if (sqlHelper.getActivityStatus(id) == 0) {
                    toHide.add(id);
                }
            }
        }
        if (!toHide.isEmpty()) {
            sqlHelper.setInstancesToHide(toHide);
        }
        for (var consentId : consentIds) {
            jdbiActivity.updateMaxInstancesPerUserById(consentId, null);
        }
        for (var user : users) {
            var signal = new EventSignal(
                    user.getId(),
                    user.getId(),
                    user.getGuid(),
                    user.getGuid(),
                    studyDto.getId(),
                    studyDto.getGuid(),
                    EventTriggerType.ACTIVITY_STATUS);
            for (long activityId : user.getConsents().keySet()) {
                ActivityInstanceCreationEventSyncProcessorDefault activityInstanceCreationEventSyncProcessor =
                        new ActivityInstanceCreationEventSyncProcessorDefault(handle, signal, activityId,
                                new ActivityInstanceCreationService(signal));
                activityInstanceCreationEventSyncProcessor.processInstancesCreation();
            }
        }
        for (var consentId : consentIds) {
            jdbiActivity.updateMaxInstancesPerUserById(consentId, 1);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update activity_instance set is_hidden=true where activity_instance_id in (<ids>);")
        int setInstancesToHide(@BindList(value = "ids") Set<Long> ids);

        @SqlQuery("select u.user_id, u.guid, ai.activity_instance_id, ai.study_activity_id from\n"
                + "\tumbrella_study us \n"
                + "\tjoin study_activity sa on us.guid='CMI-OSTEO' and sa.study_id = us.umbrella_study_id\n"
                + "\t\tand sa.study_activity_code in ('CONSENT', 'CONSENT_ASSENT', 'PARENTAL_CONSENT')\n"
                + "    join activity_instance ai on ai.study_activity_id = sa.study_activity_id\n"
                + "    join user u on u.user_id = ai.participant_id")
        @UseRowReducer(UserReducer.class)
        List<UserInfo> getUsersInfo();

        @SqlQuery("select count(*) from activity_instance_status ais \n"
                + "join activity_instance_status_type aist on "
                + "ais.activity_instance_status_type_id = aist.activity_instance_status_type_id\n"
                + "where activity_instance_id=:id and aist.activity_instance_status_type_code='COMPLETE'; ")
        int getActivityStatus(@Bind(value = "id") Long id);

        class UserInfo {
            long id;
            String guid;
            Map<Long, Long> consents;

            UserInfo(long id, String guid) {
                this.id = id;
                this.guid = guid;
                consents = new HashMap<>();
            }

            void addConsent(Long activityId, Long activityInstanceId) {
                consents.put(activityId, activityInstanceId);
            }

            public long getId() {
                return id;
            }

            public String getGuid() {
                return guid;
            }

            public Map<Long, Long> getConsents() {
                return consents;
            }
        }

        class UserReducer implements LinkedHashMapRowReducer<Long, UserInfo> {
            @Override
            public void accumulate(Map<Long, UserInfo> container, RowView row) {
                long userId = row.getColumn("user_id", Long.class);
                String userGuid = row.getColumn("guid", String.class);
                long activityInstanceId = row.getColumn("activity_instance_id", Long.class);
                long activityId = row.getColumn("study_activity_id", Long.class);
                UserInfo userInfo = container.get(userId);
                if (userInfo == null) {
                    userInfo = new UserInfo(userId, userGuid);
                    container.put(userId, userInfo);
                }
                userInfo.addConsent(activityId, activityInstanceId);
            }
        }
    }
}
