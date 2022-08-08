package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;

import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class BrugadaAddKitConfigurationAndUpdateEnrolledEvent implements CustomTask {
    private static final String EVENT_DATA_FILE = "patches/addKitConfigurationAndUpdateEnrolledEvent.conf";
    private static final String STUDY_GUID = "brugada";

    private Config studyCfg;
    private Config eventDataCfg;
    private Config kitDataCfg;

    private UpdateTemplatesInPlace updateTemplatesInPlace;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(EVENT_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Event Data file is missing: " + file);
        }
        this.eventDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg).getConfigList("events").get(0);
        this.kitDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg).getConfigList("kits").get(0);
        updateTemplatesInPlace.init(cfgPath, studyCfg, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        var guid = studyCfg.getString("adminUser.guid");
        var adminUser = handle.attach(UserDao.class)
                .findUserByGuid(guid)
                .orElseThrow(() -> new DaoException("Could not find participant user with guid: " + guid));

        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        log.info("Searching for activity instance creation event copy configuration...");

        List<EventConfiguration> existingEvents = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId()).stream()
                .filter(eventConfiguration -> eventConfiguration.getEventActionType().name()
                        .equals(eventDataCfg.getConfig("action").getString("type")))
                .collect(Collectors.toList());

        if (existingEvents.size() > 0) {
            for (EventConfiguration event: existingEvents) {
                log.info("Updating USER_ENROLLED event configuration with id {}. Setting status is_active=false",
                        event.getEventConfigurationId());
                DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class)
                        .updateIsActiveById(event.getEventConfigurationId(), false));
            }
        }

        log.info("New USER_ENROLLED event configuration has added wit id: {}",
                eventBuilder.insertEvent(handle, eventDataCfg));

        log.info("Inserting kits configuration.");

        KitConfigurationDao kitDao = handle.attach(KitConfigurationDao.class);
        KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);

        List<KitConfigurationDto> kits = kitDao.getKitConfigurationDtosByStudyId(studyDto.getId());
        if (kits.size() != 0) {
            log.info("Kit configuration exists in study {}", STUDY_GUID);
            return;
        }

        String type = kitDataCfg.getString("type");
        int quantity = kitDataCfg.getInt("quantity");
        boolean needsApproval = kitDataCfg.getBoolean("needsApproval");

        KitType kitType = kitTypeDao.getKitTypeByName(type)
                .orElseThrow(() -> new DDPException("Could not find kit type " + type));
        long kitId = kitDao.insertConfiguration(studyDto.getId(), quantity, kitType.getId(), needsApproval);
        log.info("Created kit configuration with id={}, type={}, quantity={}, needsApproval={}",
                kitId, type, quantity, needsApproval);

        for (Config ruleCfg : kitDataCfg.getConfigList("rules")) {
            KitRuleType ruleType = KitRuleType.valueOf(ruleCfg.getString("type"));
            if (ruleType == KitRuleType.PEX) {
                String expr = ruleCfg.getString("expression");
                long ruleId = kitDao.addPexRule(kitId, expr);
                log.info("Added pex rule to kit configuration {} with id={}", kitId, ruleId);
            } else if (ruleType == KitRuleType.COUNTRY || ruleType == KitRuleType.COUNTRY) {
                log.info("This task doesn't support kit rule type {}", ruleType);
            } else {
                throw new DDPException("Unsupported kit rule type " + ruleType);
            }
        }
        log.info("Kit configuration has added in study {}", STUDY_GUID);
        updateTemplatesInPlace.run(handle);
    }

}
