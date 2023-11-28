package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;

/**
 * CLI for bulk creation of activity instances for a list of participants.
 */
@Slf4j
public class CreateActivityInstanceCLI {
    private static final String USAGE = CreateActivityInstanceCLI.class.getSimpleName() + " [OPTIONS]";

    public static final String STUDY_OPTION = "s";
    public static final String ACTIVITY_CODE_OPTION = "a";
    public static final String FILE_OF_PARTICIPANT_GUIDS_OPTION = "f";
    public static boolean isDryRun;

    public static void main(String[] args) {
        Options options = new Options();

        options.addRequiredOption(STUDY_OPTION, "study", true, "study guid (note caps sensitivity)");
        options.addRequiredOption(ACTIVITY_CODE_OPTION, "code", true, "activity code");
        options.addRequiredOption(FILE_OF_PARTICIPANT_GUIDS_OPTION, "file", true,
                "absolute path to file of participant guids, one per line");
        options.addOption(null, "dry-run", false, "runs Activity Instance creation without saving");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
            isDryRun = cmd.hasOption("dry-run");
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "Also requires a -Dconfig.file");
            System.exit(-1);
        }

        String studyGuid = cmd.getOptionValue(STUDY_OPTION);
        String activityCode = cmd.getOptionValue(ACTIVITY_CODE_OPTION);
        File fileOfParticipantGuids = new File(cmd.getOptionValue(FILE_OF_PARTICIPANT_GUIDS_OPTION));

        List<String> ptpGuids = null;

        try {
            ptpGuids = IOUtils.readLines(new FileReader(fileOfParticipantGuids));
        } catch (IOException e) {
            log.error("Could not read " + fileOfParticipantGuids.getAbsolutePath(), e);
            System.exit(-1);
        }

        final List<String> participantGuids = ptpGuids;
        Config cfg = ConfigFactory.load();
        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        StringBuilder errors = new StringBuilder();
        StringBuilder summary = new StringBuilder();

        log.info("Will attempt to create " + participantGuids.size() + " " + activityCode + " activity instances for " + studyGuid);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, dbUrl));
        TransactionWrapper.useTxn(handle -> {
            // get the activity id
            long studyActivityId;

            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyGuidAndCode(studyGuid, activityCode);

            if (activityDto.isEmpty()) {
                log.error("Could not find activity " + activityCode + " in study " + studyGuid);
                System.exit(-1);
            }

            studyActivityId = activityDto.get().getActivityId();
            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
            UserDao userDao = handle.attach(UserDao.class);
            int instancesCreated = 0;

            for (String participantGuid: participantGuids) {
                if (userDao.findUserByGuid(participantGuid).isEmpty()) {
                    errors.append("Could not find user ")
                            .append(participantGuid)
                            .append(".  No activity instance created.\n");
                    continue;
                }
                ActivityInstanceDto createdInstance = activityInstanceDao
                        .insertInstance(studyActivityId, participantGuid, participantGuid, null);
                summary.append("Created ")
                        .append(activityCode)
                        .append(" instance ")
                        .append(createdInstance.getGuid())
                        .append(" for participant ")
                        .append(participantGuid).append("\n");
                instancesCreated++;
            }

            String errorMessage = errors.toString();
            String summaryMessage = summary.toString();

            log.info("Created " + instancesCreated + " " + activityCode + " activity instances");
            if (StringUtils.isNotBlank(summaryMessage)) {
                log.info(summaryMessage);
            }

            if (StringUtils.isNotBlank(errorMessage)) {
                log.error(errorMessage);
                // put this high level indicator of errors last, so it's right at the bottom of
                // the terminal window
                log.error("There were errors creating activity instances!");
            }
            if (isDryRun) {
                log.info("In dry-run mode: rolling back execution...");
                handle.rollback();
            }

        });

    }
}
