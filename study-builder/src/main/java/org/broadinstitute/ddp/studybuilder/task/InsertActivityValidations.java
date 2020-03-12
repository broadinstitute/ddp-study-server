package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

/**
 * A general task that can help insert activity validations.
 */
abstract class InsertActivityValidations implements CustomTask {

    protected String studyGuid;
    protected String dataFile;
    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    InsertActivityValidations(String studyGuid, String dataFile) {
        this.studyGuid = studyGuid;
        this.dataFile = dataFile;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
        File file = cfgPath.getParent().resolve(dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        var builder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getUserId());
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        for (Config item : dataCfg.getConfigList("items")) {
            String activityCode = item.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

            List<ActivityVersionDto> versions = jdbiActVersion.findAllVersionsInAscendingOrder(activityId);
            if (versions.isEmpty()) {
                throw new DDPException("Could not find versions for activity " + activityCode);
            }

            // Since activity validations wasn't designed with revisions in mind, we use the oldest version here to be safe.
            long revId = versions.get(0).getRevId();

            builder.insertValidations(handle, activityId, activityCode, revId, List.copyOf(item.getConfigList("validations")));
        }
    }
}
