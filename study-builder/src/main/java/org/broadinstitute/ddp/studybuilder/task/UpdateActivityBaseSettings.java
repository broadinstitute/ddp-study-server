package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to update basic activity configuration such as display order, max instances per user, and other flags.
 */
public class UpdateActivityBaseSettings implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateActivityBaseSettings.class);

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            String activityCode = definition.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

            var jdbiActivity = handle.attach(JdbiActivity.class);
            ActivityDto currentDto = jdbiActivity.queryActivityById(activityId);
            ActivityDto latestDto = new ActivityDto(
                    currentDto.getActivityId(),
                    currentDto.getActivityTypeId(),
                    currentDto.getStudyId(),
                    currentDto.getActivityCode(),
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
                    definition.getBoolean("hideExistingInstancesOnCreation"));

            if (!currentDto.equals(latestDto)) {
                LOG.info("{} changed, updating...", activityCode);
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
                        latestDto.isHideExistingInstancesOnCreation());
            } else {
                LOG.info("No changes for {}, skipping...", activityCode);
            }
        }
    }
}
