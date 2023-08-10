package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

/**
 * General task to update templates for an activity in-place. Need to provide the `--activity-code` argument.
 */
@Slf4j
public class UpdateActivityTemplatesInPlace implements CustomTask {
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private String activityCode;
    private static final String FILE_OF_VARS_TO_SKIP = "sv";
    private String variablesToSkipFile;
    private List<String> variableNamesToSkip = null;

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
        //if (cmd.hasOption(FILE_OF_VARS_TO_SKIP)) {
        if (positional.length == 2) {
            File fileOfSkipVariables = new File(positional[1]);
            //cmd.getOptionValue(FILE_OF_VARS_TO_SKIP));
            log.info("using skip variables file: {}", fileOfSkipVariables.getName());

            try {
                variableNamesToSkip = IOUtils.readLines(new FileReader(fileOfSkipVariables));
            } catch (IOException e) {
                log.error("Could not read " + fileOfSkipVariables.getAbsolutePath(), e);
                System.exit(-1);
            }
        }
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
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"), true);
            String activityCode = definition.getString("activityCode");
            String versionTag = definition.getString("versionTag");

            if (this.activityCode.equals(activityCode)) {
                log.info("Found activity definition for {}", activityCode);
                var updateTask = new UpdateTemplatesInPlace(variableNamesToSkip);
                updateActivityTemplates(handle, studyId, activityDao, jdbiActivity, jdbiActVersion, versionTag,
                        definition, updateTask, activityCode);

                //load nestedActivities
                List<ActivityDef> nestedDefs = activityBuilder.loadNestedActivities(activityCfg);
                Map<String, Config> nestedActivityConf = new HashMap<>();
                if (!nestedDefs.isEmpty()) {
                    log.info("Working on nested activities templates for {}", activityCode);
                    List<String> nestedPaths = activityCfg.hasPath("nestedActivities")
                            ? activityCfg.getStringList("nestedActivities")
                            : Collections.emptyList();
                    for (var nestedPath : nestedPaths) {
                        Config nestedConf = activityBuilder.readDefinitionConfig(nestedPath);
                        nestedActivityConf.put(nestedConf.getString("activityCode"), nestedConf);
                    }
                }
                for (ActivityDef nestedDef : nestedDefs) {
                    updateActivityTemplates(handle, studyId, activityDao, jdbiActivity, jdbiActVersion, versionTag,
                            nestedActivityConf.get(nestedDef.getActivityCode()), updateTask, nestedDef.getActivityCode());
                }
                found = true;
                break;
            }
        }

        if (!found) {
            log.info("Could not find activity definition for {}", activityCode);
        }
    }

    private void updateActivityTemplates(Handle handle, long studyId, ActivityDao activityDao, JdbiActivity jdbiActivity,
                                         JdbiActivityVersion jdbiActVersion, String versionTag, Config activityCfg,
                                         UpdateTemplatesInPlace updateTask, String activityCode) {
        log.info("Working on activity definition/templates for {}", activityCode);
        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyId, activityCode).get();
        ActivityVersionDto versionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag).get();
        FormActivityDef activityDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
        updateTask.traverseActivity(handle, activityCode, activityCfg, activityDef, versionDto.getRevStart());
    }

}
