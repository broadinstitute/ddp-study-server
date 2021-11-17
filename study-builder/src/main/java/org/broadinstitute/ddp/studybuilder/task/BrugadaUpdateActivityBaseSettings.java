package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

public class BrugadaUpdateActivityBaseSettings implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainPrequalV2.class);
    private static final String DATA_FILE = "patches/updateActivityBaseSettings.conf";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("studyGuid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            String activityCode = definition.getString("activityCode");
            String versionTag = definition.getString("versionTag");

            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            ActivityVersionDto versionDto = jdbiActVersion.findByActivityIdAndVersionTag(activityId, versionTag).orElseThrow();

            if (activityCode.equals(dataCfg.getString("activityCode"))) {
                LOG.info("Working on activity {} version {} (revisionId={})...", activityCode, versionTag, versionDto.getRevId());
                compareBasicSettings(handle, definition, activityId);
            }
        }
    }

    private void compareBasicSettings(Handle handle, Config definition, long activityId) {
        var jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto currentDto = jdbiActivity.queryActivityById(activityId);
        ActivityDto latestDto = new ActivityDto(
                currentDto.getActivityId(),
                currentDto.getActivityTypeId(),
                currentDto.getStudyId(),
                currentDto.getActivityCode(),
                currentDto.getParentActivityId(),
                currentDto.getParentActivityCode(),
                definition.getInt("displayOrder"),
                definition.getBoolean("writeOnce"),
                false,  // instantiate_upon_registration not supported anymore!
                ConfigUtil.getIntIfPresent(definition, "maxInstancesPerUser"),
                ConfigUtil.getLongIfPresent(definition, "editTimeoutSec"),
                definition.getBoolean("allowOndemandTrigger"),
                definition.getBoolean("excludeFromDisplay"),
                definition.getBoolean("allowUnauthenticated"),
                definition.getBoolean("isFollowup"),
                definition.getBoolean("excludeStatusIconFromDisplay"),
                definition.getBoolean("hideExistingInstancesOnCreation"),
                ConfigUtil.getBoolOrElse(definition, "createOnParentCreation", false),
                ConfigUtil.getBoolOrElse(definition, "canDeleteInstances", false),
                ConfigUtil.getBoolIfPresent(definition, "canDeleteFirstInstance"));
        if (!currentDto.equals(latestDto)) {
            if (currentDto.canDeleteInstances() != latestDto.canDeleteInstances()) {
                throw new UnsupportedOperationException("Updating `canDeleteInstances` setting is currently not supported"
                        + " to prevent accidental updates of this property and allowing undesired deletion of data");
            }
            jdbiActivity.updateActivity(
                    latestDto.getActivityId(),
                    latestDto.getDisplayOrder(),
                    latestDto.isWriteOnce(),
                    latestDto.isInstantiateUponRegistration(),
                    latestDto.getMaxInstancesPerUser(),
                    latestDto.getEditTimeoutSec(),
                    latestDto.isOndemandTriggerAllowed(),
                    latestDto.shouldExcludeFromDisplay(),
                    latestDto.isUnauthenticatedAllowed(),
                    latestDto.isFollowup(),
                    latestDto.shouldExcludeStatusIconFromDisplay(),
                    latestDto.isHideExistingInstancesOnCreation(),
                    latestDto.isCreateOnParentCreation(),
                    latestDto.canDeleteInstances(),
                    latestDto.getCanDeleteFirstInstance());
            LOG.info("Updated basic settings");
        } else {
            LOG.info("No changes to basic settings");
        }
    }

}
