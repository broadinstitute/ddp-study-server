package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to update templates for an activity in-place. Need to provide the `--activity-code` argument.
 */
public class UpdateActivityTemplatesInPlace implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateActivityTemplatesInPlace.class);

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private String activityCode;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void consumeArguments(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(new Options(), args);
        String[] positional = cmd.getArgs();
        if (positional.length < 1) {
            throw new ParseException("Positional argument ACTIVITY_CODE is required.");
        }
        this.activityCode = positional[0];
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class)
                .findUserByGuid(studyCfg.getString("adminUser.guid")).get();

        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());
        findThenTraverseActivity(handle, studyDto.getId(), activityBuilder);
    }

    private void findThenTraverseActivity(Handle handle, long studyId, ActivityBuilder activityBuilder) {
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        boolean found = false;
        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            String activityCode = definition.getString("activityCode");
            String versionTag = definition.getString("versionTag");

            if (this.activityCode.equals(activityCode)) {
                LOG.info("Found activity definition for {}", activityCode);

                ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyId, activityCode).get();
                ActivityVersionDto versionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag).get();
                FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);

                var updateTask = new UpdateTemplatesInPlace();
                updateTask.traverseActivity(handle, activityCode, definition, activity);
                found = true;
                break;
            }
        }

        if (!found) {
            LOG.info("Could not find activity definition for {}", activityCode);
        }
    }
}
