package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBostonAddSampleCollectMessage implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonAddSampleCollectMessage.class);
    private static final String DATA_FILE = "patches/sample-collect-message.conf";
    private static final String STUDY_GUID = "testboston";

    private Config varsCfg;
    private Config dataCfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        dataCfg = dataCfg.resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);

        Config blockCfg = dataCfg.getConfig("block");
        validateBlockDef(blockCfg);

        String activityCode = varsCfg.getString("id.act.baseline_symptom");
        prependNewBlockInPlace(handle, studyDto.getId(), activityCode, blockCfg);

        activityCode = varsCfg.getString("id.act.longitudinal");
        prependNewBlockInPlace(handle, studyDto.getId(), activityCode, blockCfg);
    }

    private void validateBlockDef(Config blockCfg) {
        var validator = new GsonPojoValidator();
        var def = gson.fromJson(ConfigUtil.toJson(blockCfg), ContentBlockDef.class);
        List<JsonValidationError> errors = validator.validateAsJson(def);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            throw new DDPException("Block definition has validation errors: " + msg);
        }
    }

    private void prependNewBlockInPlace(Handle handle, long studyId, String activityCode, Config blockCfg) {
        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
        LOG.info("Prepending block for activity {}...", activityCode);

        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find latest version for activity " + activityCode));

        long timestamp = versionDto.getRevStart();
        List<Long> sectionIds = handle.attach(JdbiFormActivityFormSection.class)
                .findOrderedSectionIdsByActivityIdAndTimestamp(activityId, timestamp);
        if (sectionIds.isEmpty()) {
            throw new DDPException("Could not find sections for activity " + activityCode);
        }

        long sectionId = sectionIds.get(0);
        long revisionId = versionDto.getRevId();
        int displayOrder = SectionBlockDao.DISPLAY_ORDER_GAP / 2; // Current first block starts at this gap, so use something smaller.
        var block = gson.fromJson(ConfigUtil.toJson(blockCfg), ContentBlockDef.class);

        handle.attach(SectionBlockDao.class).insertBlockForSection(activityId, sectionId, displayOrder, block, revisionId);
        LOG.info("Inserted new {} block with id={}, displayOrder={} for activityCode={}, sectionId={}",
                block.getBlockType(), block.getBlockId(), displayOrder, activityCode, sectionId);
    }
}
