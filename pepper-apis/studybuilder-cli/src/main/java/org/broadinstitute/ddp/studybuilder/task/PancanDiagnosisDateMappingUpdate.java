package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.study.ActivityMappingType;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;

@Slf4j
public class PancanDiagnosisDateMappingUpdate implements CustomTask {
    private Config studyCfg;
    private Config dataCfg;
    private Path cfgPath;

    private Handle handle;
    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.studyCfg = studyCfg;
        this.cfgPath = cfgPath;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.handle = handle;

        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        long activityId = jdbiActivity.findIdByStudyIdAndCode(studyDto.getId(), "ABOUT_CANCER").get();
        insertActivityMapping(handle, "DATE_OF_DIAGNOSIS", "DIAGNOSIS_DATE", activityId);
    }

    private void insertActivityMapping(Handle handle, String mappingType, String stableId, long activityId) {
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        ActivityMappingType type = ActivityMappingType.valueOf(mappingType);

        activityDao.insertActivityMapping(studyDto.getGuid(), type, activityId, stableId);
        log.info("Added activity mapping for {} with type={}, activityId={}, subStableId={}",
                "ABOUT_CANCER", type, activityId, stableId);
    }

}
