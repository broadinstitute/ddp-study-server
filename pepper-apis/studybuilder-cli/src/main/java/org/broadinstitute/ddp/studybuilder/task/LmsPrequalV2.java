package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class LmsPrequalV2 implements CustomTask {
    private static final String STUDY_GUID = "cmi-lms";
    private static final String DATA_FILE = "patches/prequal_update.conf";

    private String activityCode;
    private final Gson gson = GsonUtil.standardGson();
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.activityCode = dataCfg.getString("activityCode");
    }

    @Override
    public void run(Handle handle) {
        addBlockToActivity(handle);
    }

    private void addBlockToActivity(Handle handle) {
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, activityCode).get();
        ActivityVersionDto ver = handle.attach(JdbiActivityVersion.class).getActiveVersion(activityDto.getActivityId()).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, ver);
        List<FormSectionDef> sections = currentDef.getSections();
        FormSectionDef currentSectionDef = sections.get(sections.size()-1);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("empty_block")), FormBlockDef.class);
        int displayOrder = currentSectionDef.getBlocks().size() * 10 + 10;
        sectionBlockDao.insertBlockForSection(activityDto.getActivityId(), currentSectionDef.getSectionId(),
                displayOrder, raceDef, ver.getRevId());
        log.info("New empty block was added to activity {} into section #{} with display order {}",
                "PREQUAL", 1, displayOrder);
    }

}
