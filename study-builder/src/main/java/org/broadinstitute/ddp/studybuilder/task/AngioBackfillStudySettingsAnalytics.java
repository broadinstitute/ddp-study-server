package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.Optional;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioBackfillStudySettingsAnalytics implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioBackfillStudySettingsAnalytics.class);
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        StudyDao studyDao = handle.attach(StudyDao.class);
        String analyticsToken = varsCfg.getString("analyticsToken");
        String studyGuid = "ANGIO";

        StudyDto dto = jdbiUmbrellaStudy.findByStudyGuid(studyGuid);
        long studyId = dto.getId();

        //insert into study_settings
        studyDao.addSettings(studyId, null, null, true, analyticsToken);

        Optional<StudySettings> studySettingsOpt = studyDao.findSettings(studyId);
        StudySettings studySettings = studySettingsOpt.get();
        LOG.info("Populated study settings analyticsEnabled={}, analyticsToken={} for studyId: {} ",
                studySettings.isAnalyticsEnabled(), studySettings.getAnalyticsToken(), studySettings.getStudyId());
    }

}
