package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Slf4j
public class OsteoSomaticResultsActivity implements CustomTask {

    private static final String DATA_FILE = "activities/somatic-results.conf";
    private static final String WORKFLOW_DATA_FILE = "patches/somatic-results-workflow.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;
    private Config workflowDataCfg;

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

        File workflowFile = cfgPath.getParent().resolve(WORKFLOW_DATA_FILE).toFile();
        if (!workflowFile.exists()) {
            throw new DDPException("Workflow transitions data file is missing: " + file);
        }
        this.workflowDataCfg = ConfigFactory.parseFile(workflowFile);
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        insertActivities(handle, studyDto, adminUser.getUserId());
        addWorkflows(handle, studyDto);
    }

    private void insertActivities(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting activities");
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);
        Config definition = dataCfg;
        ActivityVersionDto dto = activityBuilder.insertActivity(handle, definition, Collections.emptyList(), null);
        log.info("inserted new activity : {}  ", dto.getActivityId());

    }

    private void addWorkflows(Handle handle, StudyDto studyDto) {
        List<? extends Config> workflows = workflowDataCfg.getConfigList("workflowTransitions");
        log.info("adding workflow");
        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        for (Config workflowCfg : workflows) {
            workflowBuilder.insertTransitionSet(handle, workflowCfg);
        }
    }

}
