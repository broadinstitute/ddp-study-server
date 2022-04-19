package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class OsteoNewFamilyHistory implements CustomTask {
    private static final String DATA_FILE = "patches/family-history.conf";

    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        Map<String, Long> createdActivities = insertActivities(handle, studyDto, adminUser.getUserId());
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyDto.getGuid(), "FAMILY_HISTORY").orElseThrow();
        SqlHelper helper = handle.attach(SqlHelper.class);
        helper.updateActivityInstanceCreationAction(createdActivities.get("FAMILY_HISTORY_V2"), activityDto.getActivityId());
        helper.deleteWorkflow(studyDto.getId());
        addWorkflowTransitions(handle, studyDto);
    }

    private Map<String, Long> insertActivities(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting activity configuration...");
        Map<String, Long> result = new HashMap<>();

        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);

        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        List<? extends Config> activities = dataCfg.getConfigList("activityFilepath");
        for (Config activity : activities) {
            Config definition = activityBuilder.readDefinitionConfig(activity.getString("name"));
            List<Config> nested = new ArrayList<>();
            for (String nestedFilename : activity.getStringList("nested")) {
                Config nestedDef = activityBuilder.readDefinitionConfig(nestedFilename);
                nested.add(nestedDef);
            }
            var activityVersion = activityBuilder.insertActivity(handle, definition, nested, timestamp);
            result.put(definition.getString("activityCode"), activityVersion.getActivityId());
            log.info("Activity configuration {} has been added in study {}", activity, STUDY_GUID);
        }
        return result;
    }

    private void addWorkflowTransitions(Handle handle, StudyDto studyDto) {
        List<? extends Config> transitions = dataCfg.getConfigList("workflows");

        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        for (Config transitionCfg : transitions) {
            workflowBuilder.insertTransitionSet(handle, transitionCfg);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update activity_instance_creation_action "
                + "set study_activity_id = :newActivityId "
                + "where study_activity_id = :oldActivityId")
        void updateActivityInstanceCreationAction(@Bind("newActivityId") long newActivityId, @Bind("oldActivityId") long oldActivityId);

        @SqlUpdate("delete from workflow_transition where umbrella_study_id = :studyId")
        void deleteWorkflow(@Bind("studyId") long studyId);

    }
}
