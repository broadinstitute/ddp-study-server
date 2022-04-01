package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.activityinstancecreation.ActivityInstanceCreationEventSyncProcessorDefault;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class OsteoDdp7601 implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/DDP-7601-new-event-for-dsm.conf";

    private Config dataCfg;
    private Config studyCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.studyCfg = studyCfg;
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        var guid = studyCfg.getString("adminUser.guid");
        var adminUser = handle.attach(UserDao.class)
                .findUserByGuid(guid)
                .orElseThrow(() -> new DaoException("Could not find participant user with guid: " + guid));

        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        log.info("Adding new event for DSM notification.");

        Config eventDataCfg = dataCfg.getConfigList("events").get(0);

        eventBuilder.insertEvent(handle, eventDataCfg);

        log.info("Added new event for DSM notification.");

        SqlHelper sqlHelper = handle.attach(SqlHelper.class);
        List<SqlHelper.UserInfo> users = sqlHelper.getUsersInfo();
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        Set<Long> consentIds = new HashSet<>();
        for (var user : users) {
            consentIds.addAll(user.getConsents().keySet());
        }
        sqlHelper.setInstancesToHide(consentIds);
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

        @SqlUpdate("update study_activity set hide_existing_instances_on_creation = true where study_activity_id in (<ids>)")
        int setInstancesToHide(@BindList(value = "ids") Set<Long> ids);

        @SqlQuery("select u.user_id, u.guid, ai.activity_instance_id, ai.study_activity_id from\n"
                + "\tumbrella_study us \n"
                + "\tjoin study_activity sa on us.guid='CMI-OSTEO' and sa.study_id = us.umbrella_study_id\n"
                + "\t\tand sa.study_activity_code in ('CONSENT', 'CONSENT_ASSENT', 'PARENTAL_CONSENT')\n"
                + "    join activity_instance ai on ai.study_activity_id = sa.study_activity_id\n"
                + "    join user u on u.user_id = ai.participant_id")
        @UseRowReducer(UserReducer.class)
        List<UserInfo> getUsersInfo();

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
