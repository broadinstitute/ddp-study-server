package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

/**
 * Main entry point for command-line tool that helps stand up a study.
 */
public class StudyBuilderCli {

    private static final String USAGE = "StudyBuilder [-h, --help] [OPTIONS] <STUDY_CONFIG_FILE | STUDY_KEY>";
    private static final String FOOTNOTE = "\n"
            + "When passing study key instead of study config filepath, "
            + "the study key should correspond to name of directory for that study."
            + "\n\n"
            + "When using the `only-*` flags, the study is expected to already have been setup. "
            + "Only one of these flags should be given on the command-line. If more than one is present, "
            + "the preference is as follows from highest to lowest: only-activity, only-workflow, only-events.";
    private static final String OPT_RUN_TASK = "run-task";
    private static final String OPT_PATCH = "patch";
    private static final String OPT_CREATE_EMAILS = "create-emails";
    private static final String OPT_UPDATE_EMAILS = "update-emails";
    private static final String OPT_EMAIL_KEYS = "email-keys";
    private static final String DEFAULT_STUDIES_DIR = "studies";
    private static final String DEFAULT_STUDY_CONF_FILENAME = "study.conf";

    public static void main(String[] args) throws Exception {
        var app = new StudyBuilderCli();
        app.run(args);
    }

    private void run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption(null, "vars", true, "study variables file");
        options.addOption(null, "substitutions", true, "study-wide substitutions file");
        options.addOption(null, "dry-run", false, "run study setup or custom task without saving");
        options.addOption(null, "only-activity", true, "only run activity setup for given activity code");
        options.addOption(null, "only-workflow", false, "only run workflow setup");
        options.addOption(null, "only-events", false, "only run events setup");
        options.addOption(null, "only-update-pdfs", false, "only run pdf template updates (deprecated)");
        options.addOption(null, "no-workflow", false, "do not run workflow setup");
        options.addOption(null, "no-events", false, "do not run events setup");
        options.addOption(null, "enable-events", true, "enable or disable all the events for a study, accepts true/false");
        options.addOption(null, "invalidate", false, "invalidates a study by renaming its identifiers and configuration");
        options.addOption(null, OPT_CREATE_EMAILS, false, "create sendgrid emails for study");
        options.addOption(null, OPT_UPDATE_EMAILS, false, "update active version of sendgrid emails for study");
        options.addOption(null, OPT_EMAIL_KEYS, true, "comma-separated email keys, only create/update emails with these keys");
        options.addOption(null, OPT_RUN_TASK, true, "run a custom task");
        options.addOption(null, OPT_PATCH, false, "run patches for a study");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        String[] positional = cmd.getArgs();

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, FOOTNOTE);
            return;
        }

        if (positional == null || positional.length < 1) {
            System.err.println("[builder] requires either study configuration file or study key");
            return;
        }

        Config cfg = ConfigFactory.load();
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper
                .DbConfiguration(TransactionWrapper.DB.APIS, 1, cfg.getString("dbUrl")));
        log("using db url: %s", cfg.getString("dbUrl"));

        Config varsCfg = ConfigFactory.empty();
        if (cmd.hasOption("vars")) {
            String varsFile = cmd.getOptionValue("vars");
            varsCfg = ConfigFactory.parseFile(new File(varsFile));
            log("using study variables file: %s", varsFile);
        }

        Config subsCfg = ConfigFactory.empty();
        if (cmd.hasOption("substitutions")) {
            String subsFile = cmd.getOptionValue("substitutions");
            subsCfg = ConfigFactory.parseFile(new File(subsFile));
            log("using substitutions file: %s", subsFile);
        }

        // Merge the configs. Substitutions have higher priority, and we fallback to vars, i.e. substitutions override keys in vars.
        varsCfg = subsCfg.withFallback(varsCfg);

        Path cfgPath = resolvePathToStudyConfigFile(positional[0]);
        if (cfgPath == null) {
            log("could not resolve study config filepath using argument: " + positional[0]);
            return;
        }

        Config studyCfg = ConfigFactory.parseFile(cfgPath.toFile());
        log("using study configuration file: %s", cfgPath);

        log("resolving study configuration...");
        studyCfg = studyCfg.resolveWith(varsCfg);

        boolean isDryRun = cmd.hasOption("dry-run");
        if (cmd.hasOption(OPT_RUN_TASK)) {
            String taskName = cmd.getOptionValue(OPT_RUN_TASK);
            runCustomTask(taskName, cfgPath, studyCfg, varsCfg, isDryRun);
            return;
        } else if (cmd.hasOption(OPT_PATCH)) {
            log("executing patches...");
            StudyPatcher patcher = new StudyPatcher(cfgPath, studyCfg, varsCfg);
            execute(patcher::run, isDryRun);
            log("done");
            return;
        }

        StudyBuilder builder = new StudyBuilder(cfgPath, studyCfg, varsCfg);
        if (cmd.hasOption("only-activity")) {
            String activityCode = cmd.getOptionValue("only-activity");
            log("executing single activity setup with activityCode=%s ...", activityCode);
            execute(handle -> builder.runActivity(handle, activityCode), isDryRun);
            log("done");
            return;
        } else if (cmd.hasOption("only-workflow")) {
            log("executing workflow setup...");
            execute(builder::runWorkflow, isDryRun);
            log("done");
            return;
        } else if (cmd.hasOption("only-events")) {
            log("executing events setup...");
            execute(builder::runEvents, isDryRun);
            log("done");
            return;
        } else if (cmd.hasOption("only-update-pdfs")) {
            //pdfs are updated only if study and configuration exists. No updates to pdf configuration. No new insert if pdf not found
            // log("executing pdf template updates...");
            // execute(builder::runUpdatePdfTemplates, isDryRun);
            // log("done");
            log("only-update-pdfs feature is deprecated");
            return;
        } else if (cmd.hasOption("enable-events")) {
            String arg = cmd.getOptionValue("enable-events");
            if ("true".equals(arg) || "false".equals(arg)) {
                log("toggling active state of all events for study to: " + arg);
                execute(handle -> builder.runEnableEvents(handle, Boolean.parseBoolean(arg)), isDryRun);
                log("done");
            } else {
                log("must pass either true/false for `enable-events` flag");
            }
            return;
        } else if (cmd.hasOption("invalidate")) {
            runInvalidateStudy(cfg, studyCfg, builder, isDryRun);
            return;
        } else if (cmd.hasOption(OPT_CREATE_EMAILS) || cmd.hasOption(OPT_UPDATE_EMAILS)) {
            if (isDryRun) {
                throw new DDPException("Create/update of sendgrid emails does not support dry-run");
            }
            runEmails(cmd, cfgPath, studyCfg, varsCfg);
            return;
        }

        if (cmd.hasOption("no-workflow")) {
            builder.doWorkflow(false);
        }
        if (cmd.hasOption("no-events")) {
            builder.doEvents(false);
        }

        log("executing setup...");
        execute(builder::run, isDryRun);
        log("done");
    }

    private void log(String fmt, Object... args) {
        System.out.println("[builder] " + String.format(fmt, args));
    }

    private Path resolvePathToStudyConfigFile(String filepathOrKey) {
        // Try as file path.
        File file = Paths.get(filepathOrKey).toFile();
        if (file.isFile() && file.exists()) {
            return file.toPath().toAbsolutePath();
        }

        // Try as study key.
        file = Paths.get(DEFAULT_STUDIES_DIR, filepathOrKey, DEFAULT_STUDY_CONF_FILENAME).toFile();
        if (file.isFile() && file.exists()) {
            return file.toPath().toAbsolutePath();
        }

        return null;
    }

    private void execute(Consumer<Handle> task, boolean isDryRun) {
        TransactionWrapper.useTxn(handle -> {
            task.accept(handle);
            if (isDryRun) {
                log("rolling back execution...");
                handle.rollback();
            }
        });
        try {
            TimeUnit.SECONDS.sleep(1);  // Wait a bit for database writes to persist, needed for hsqldb.
        } catch (InterruptedException e) {
            throw new DDPException("sleep was interrupted", e);
        }
    }

    private void runCustomTask(String taskName, Path cfgPath, Config studyCfg, Config varsCfg, boolean isDryRun) {
        CustomTask task = BuilderUtils.loadTask(taskName);
        task.init(cfgPath, studyCfg, varsCfg);
        log("executing custom task: " + taskName);
        execute(task::run, isDryRun);
        log("done");
    }

    private void runInvalidateStudy(Config appCfg, Config studyCfg, StudyBuilder builder, boolean isDryRun) {
        log("running invalidation...");
        System.out.println();
        System.out.println("Invalidating a study will make it un-usable due to renamed identifiers and configuration.");
        System.out.print("Are you sure? Enter study guid to confirm: ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        if (!studyCfg.getString("study.guid").equals(input)) {
            System.out.println();
            log("input '%s' does not match study guid, invalidation will not be performed", input);
            return;
        }

        System.out.println();
        System.out.println("Double-check the db url: " + appCfg.getString("dbUrl"));
        System.out.print("Does it look right? [yes/no] ");

        input = scanner.nextLine().trim();
        if (!"yes".equals(input)) {
            System.out.println();
            log("adjust the db url in the application config file, or enter 'yes' to continue");
            return;
        }

        System.out.println();
        execute(builder::runInvalidate, isDryRun);
        log("done");
    }

    private void runEmails(CommandLine cmd, Path cfgPath, Config studyCfg, Config varsCfg) {
        var emailBuilder = new EmailBuilder(cfgPath, studyCfg, varsCfg);
        Set<String> keys = null;
        if (cmd.hasOption(OPT_EMAIL_KEYS)) {
            keys = Set.of(cmd.getOptionValue(OPT_EMAIL_KEYS).split(","));
        }

        if (cmd.hasOption(OPT_CREATE_EMAILS)) {
            if (keys == null) {
                log("executing creation of all study sendgrid emails...");
                emailBuilder.createAll();
            } else {
                log("executing creation of sendgrid emails with keys: " + Arrays.toString(keys.toArray()));
                emailBuilder.createForEmailKeys(keys);
            }
        } else {
            if (keys == null) {
                log("executing update of all study sendgrid emails...");
                emailBuilder.updateAll();
            } else {
                log("executing update of sendgrid emails with keys: " + Arrays.toString(keys.toArray()));
                emailBuilder.updateForEmailKeys(keys);
            }
        }

        log("done");
    }
}
