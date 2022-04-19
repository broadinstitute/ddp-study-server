package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;


@Slf4j
public class PrionWorkflowUpdate implements CustomTask {
    private static final String FILE_PATH = "patches/workflow-update.conf";
    private static final String STUDY_GUID = "PRION";

    private Config dataCfg;
    private Config cfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfg = studyCfg;

        File file = cfgPath.getParent().resolve(FILE_PATH).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        insertWorkflows(handle, studyDto);
        insertEvents(handle, studyDto, adminUser);
    }

    private void insertWorkflows(Handle handle, StudyDto studyDto) {
        JdbiWorkflowTransition jdbiWorkflowTransition = handle.attach(JdbiWorkflowTransition.class);
        int number = jdbiWorkflowTransition.deleteAllForStudy(studyDto.getId());
        log.info("Deleted {} number of workflows", number);
        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        List<? extends Config> workflows = dataCfg.getConfigList("workflow");
        for (Config workflow : workflows) {
            workflowBuilder.insertTransitionSet(handle, workflow);
        }
    }

    private void insertEvents(Handle handle, StudyDto studyDto, UserDto adminUser) {
        List<QueuedEventDto> allQueuedEvents = handle.attach(EventDao.class).findAllQueuedEvents();
        JdbiEventConfiguration jdbiEventConfiguration = handle.attach(JdbiEventConfiguration.class);
        for (QueuedEventDto allQueuedEvent : allQueuedEvents) {
            jdbiEventConfiguration.deleteById(allQueuedEvent.getEventConfigurationId());
        }
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUser.getUserId());
        List<? extends Config> events = dataCfg.getConfigList("events");
        events.forEach(event -> {
            long id = eventBuilder.insertEvent(handle, event);
            log.info("Inserted an event with id {}", id);
        });
    }
}
