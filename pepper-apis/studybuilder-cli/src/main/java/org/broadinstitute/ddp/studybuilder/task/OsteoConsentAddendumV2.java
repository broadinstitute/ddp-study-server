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
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OsteoConsentAddendumV2 implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/addendum-new-activity.conf";

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

        insertActivity(handle, studyDto, adminUser.getUserId());
        insertEvents(handle, studyDto, adminUser.getUserId());
        insertKit(handle, studyDto);
    }

    private void insertActivity(Handle handle, StudyDto studyDto, long adminUserId) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);
        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        List<? extends Config> activities = dataCfg.getConfigList("activityFilepath");

        for (Config activity : activities) {
            Config definition = activityBuilder.readDefinitionConfig(activity.getString("name"));
            List<Config> nestedcfg = new ArrayList<>();
            activityBuilder.insertActivity(handle, definition, nestedcfg, timestamp);
            log.info("Activity configuration {} has been added in study {}", activity, STUDY_GUID);
        }
    }

    private void insertEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        log.info("Inserting events configuration...");
        List<? extends Config> events = dataCfg.getConfigList("events");
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
        log.info("Events configuration has added in study {}", STUDY_GUID);
    }

    private void insertKit(Handle handle, StudyDto studyDto) {
        if (!dataCfg.hasPath("kits")) {
            throw new DDPException(("there is no 'kits' configuration"));
        }
        log.info("Inserting Kits...");
        List<? extends Config> kits = dataCfg.getConfigList("kits");
        for (Config kit : kits) {
            String type = kit.getString("type");
            int quantity = kit.getInt("quantity");
            boolean needsApproval = kit.getBoolean("needsApproval");
            KitType kitType = handle.attach(KitTypeDao.class).getKitTypeByName(type)
                    .orElseThrow(() -> new DDPException("Could not find kit type " + type));
            long kitId = handle.attach(KitConfigurationDao.class)
                    .insertConfiguration(studyDto.getId(), quantity, kitType.getId(), needsApproval);
            log.info("Created kit configuration with id={}, type={}, quantity={}, needsApproval={}",
                    kitId, type, quantity, needsApproval);

            for (Config rules : kit.getConfigList("rules")) {
                KitRuleType ruleType = KitRuleType.valueOf(rules.getString("type"));
                if (ruleType != KitRuleType.PEX) {
                    throw new DDPException("This task doesn't support kit rule type " + ruleType);
                }
                long ruleId = handle.attach(KitConfigurationDao.class).addPexRule(kitId, rules.getString("expression"));
                log.info("Added pex rule to kit configuration {} with id={}", kitId, ruleId);
                log.info("Kit configuration has added in study {}", STUDY_GUID);
            }
        }
    }
}
