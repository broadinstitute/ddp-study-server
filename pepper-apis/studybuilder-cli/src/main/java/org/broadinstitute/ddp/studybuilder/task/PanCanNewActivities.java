package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PanCanNewActivities implements CustomTask {
    private static final String DATA_FILE = "patches/new-activities.conf";

    private static final String STUDY_GUID = "cmi-pancan";
    private static final int NUM_EVENTS = 16;

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

        insertActivities(handle, studyDto, adminUser.getUserId());
        insertBloodConsentPdf(handle, studyDto, adminUser.getUserId());
        addEvents(handle, studyDto, adminUser.getUserId());
        addBloodConsentKits(handle, studyDto);
    }

    private void insertActivities(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting activity configuration...");

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
            activityBuilder.insertActivity(handle, definition, nested, timestamp);
            log.info("Activity configuration {} has been added in study {}", activity, STUDY_GUID);
        }
    }

    private void insertBloodConsentPdf(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting pdf configuration...");

        if (!dataCfg.hasPath("pdf")) {
            throw new DDPException("There is no 'pdf' configuration.");
        }
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), cfg, studyDto, adminUserId);
        pdfBuilder.insertPdfConfig(handle, dataCfg.getConfig("pdf"));

        log.info("PDF configuration has added in study {}", STUDY_GUID);
    }

    private void addEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting events configuration...");

        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        List<? extends Config> events = dataCfg.getConfigList("events");
        if (events.size() != NUM_EVENTS) {
            throw new DDPException("Expected " + NUM_EVENTS + " events but got " + events.size());
        }

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }

        log.info("Events configuration has added in study {}", STUDY_GUID);
    }

    private void addBloodConsentKits(Handle handle, StudyDto studyDto) {
        log.info("Inserting kits configuration...");

        if (!dataCfg.hasPath("kit")) {
            throw new DDPException("There is no 'kit' configuration.");
        }
        String type = dataCfg.getConfig("kit").getString("type");
        int quantity = dataCfg.getConfig("kit").getInt("quantity");
        boolean needsApproval = dataCfg.getConfig("kit").getBoolean("needsApproval");

        KitType kitType = handle.attach(KitTypeDao.class).getKitTypeByName(type)
                .orElseThrow(() -> new DDPException("Could not find kit type " + type));
        long kitId = handle.attach(KitConfigurationDao.class)
                .insertConfiguration(studyDto.getId(), quantity, kitType.getId(), needsApproval);
        log.info("Created kit configuration with id={}, type={}, quantity={}, needsApproval={}",
                kitId, type, quantity, needsApproval);

        if (dataCfg.getConfig("kit").getConfigList("rules").size() != 1) {
            throw new DDPException("Found " + dataCfg.getConfig("kit").getConfigList("rules").size()
                    + ", but expected 1 rule in 'kit' configuration.");
        }

        Config ruleCfg = dataCfg.getConfig("kit").getConfigList("rules").get(0);
        KitRuleType ruleType = KitRuleType.valueOf(ruleCfg.getString("type"));

        if (ruleType != KitRuleType.PEX) {
            throw new DDPException("This task doesn't support kit rule type " + ruleType);
        }

        long ruleId = handle.attach(KitConfigurationDao.class).addPexRule(kitId, ruleCfg.getString("expression"));
        log.info("Added pex rule to kit configuration {} with id={}", kitId, ruleId);
        log.info("Kit configuration has added in study {}", STUDY_GUID);
    }
}
