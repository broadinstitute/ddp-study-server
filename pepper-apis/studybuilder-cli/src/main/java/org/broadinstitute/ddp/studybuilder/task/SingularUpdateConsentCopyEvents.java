package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.CopyAnswerEventAction;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Task to fix for Singular Consent copy events to handle parent/self name overriding.
 */
@Slf4j
public class SingularUpdateConsentCopyEvents implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/singular-consent-copy-events.conf";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config activityDataCfg;

    private Handle handle;
    private StudyDto studyDto;
    private UserDto adminUser;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        this.handle = handle;

        fixConsentCopyEvent();
    }

    private void fixConsentCopyEvent() {
        String activityCode = "CONSENT_PARENTAL";
        log.info("Working on copy event for {}...", activityCode);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        long copyConfigId = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventActionType().equals(EventActionType.COPY_ANSWER))
                .filter(event -> {
                    if (event.getEventTriggerType().equals(EventTriggerType.ACTIVITY_STATUS) && event.getPreconditionExpression() == null) {
                        var trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                        return trigger.getStudyActivityId() == activityId
                                && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE;
                    }
                    return false;
                })
                .map(event -> ((CopyAnswerEventAction) event.getEventAction()).getCopyConfigurationId())
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find copy event for activity " + activityCode));
        log.info("Found copy event with copy configuration id " + copyConfigId);

        var copyConfigDao = handle.attach(CopyConfigurationDao.class);
        CopyConfiguration currentConfig = copyConfigDao
                .findCopyConfigById(copyConfigId)
                .orElseThrow(() -> new DDPException("Could not find copy configuration with id " + copyConfigId));
        Set<Long> pairsToDelete = currentConfig.getPairs().stream().filter(pair -> {
            String stableId = ((CopyAnswerLocation) pair.getSource()).getQuestionStableId();
            if ("CONSENT_PARENTAL_FIRST_NAME".equals(stableId) || "CONSENT_PARENTAL_LAST_NAME".equals(stableId)) {
                return true;
            }
            return false;
        }).map(pair -> pair.getId()).collect(Collectors.toSet());
        if (pairsToDelete.size() != 2) {
            throw new DDPException("Expected 2 copy config pairs to delete; found : " + pairsToDelete.size());
        }
        //delete these pairs
        SqlHelper helper = handle.attach(SingularUpdateConsentCopyEvents.SqlHelper.class);
        int rowCount = helper.deleteCopyConfigPairById(pairsToDelete);
        DBUtils.checkUpdate(2, rowCount);
        log.info("Deleted 2 CopyConfigPairs : {} ", pairsToDelete);

        //insert new CopyConfigEvent
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getUserId());
        Config eventCfg = activityDataCfg.getConfig("copy-event");
        long eventId = eventBuilder.insertEvent(handle, eventCfg);
        log.info("Inserting copy event : {}  for {}", eventId, activityCode);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("delete from copy_configuration_pair where copy_configuration_pair_id in (<copyConfigPairIds>)")
        int deleteCopyConfigPairById(@BindList(value = "copyConfigPairIds") Set<Long> copyConfigPairIds);
    }
}
