package org.broadinstitute.ddp.migration;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;
import java.util.TimeZone;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.util.ConfigUtil;

@Slf4j
public class DataLoaderCli {
    private static final String USAGE = "DataLoaderCli [-h, --help] [OPTIONS]";
    private static final String PROD_MARKER = "prod";
    private static final int DB_MAX_CONNECTIONS = 1;

    public static void main(String[] args) throws Exception {
        var options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("c", "config", true, "path to loader config file (required)");
        options.addOption("o", "output", true, "path for output migration report, will use a generated name if not provided");
        options.addOption(null, "mailing-list", false, "load mailing list contacts");
        options.addOption(null, "participants", false, "load participant files");
        options.addOption(null, "dsm-data", false, "load data into dsm");
        options.addOption(null, "fix-family-notes", false, "fix up family notes fields so they're from the proband");
        options.addOption(null, "prod-run", false, "must set this flag for production migration run");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        String configFilePath = cmd.getOptionValue("config");
        if (StringUtils.isBlank(configFilePath)) {
            log.error("Loader config file is required");
            return;
        }
        Config cfg = ConfigFactory.parseFile(new File(configFilePath));

        boolean loadMailingList = cmd.hasOption("mailing-list");
        boolean loadParticipants = cmd.hasOption("participants");
        boolean loadDsmData = cmd.hasOption("dsm-data");
        boolean fixFamilyNotes = cmd.hasOption("fix-family-notes");
        if (!loadMailingList && !loadParticipants && !loadDsmData && !fixFamilyNotes) {
            log.info("Nothing to do, exiting...");
            return;
        }

        boolean isProdRun = cmd.hasOption("prod-run");
        if (cfg.getString(LoaderConfigFile.DB_URL).contains(PROD_MARKER)) {
            if (!isProdRun) {
                log.warn("Looks like we're connecting to prod. Must use the `--prod-run` flag!");
                return;
            }
            if (!cfg.getBoolean(LoaderConfigFile.SOURCE_USE_BUCKET)) {
                log.warn("Looks like we're connecting to prod. Must use bucket for source files!");
                return;
            }
            if (StringUtils.isNotBlank(ConfigUtil.getStrIfPresent(cfg, LoaderConfigFile.DUMMY_EMAIL))) {
                log.warn("Looks like we're connecting to prod. Cannot use dummy emails for prod run!");
                return;
            }
            if (cfg.getBoolean(LoaderConfigFile.CREATE_AUTH0_ACCOUNTS)) {
                log.warn("Looks like we're connecting to prod. Cannot do auth0 account creation for prod run!");
                return;
            }
            System.out.print("Looks like we're connecting to prod. Continue? [y/N] ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();
            if (!"y".equals(input) && !"yes".equalsIgnoreCase(input)) {
                return;
            }
        }

        initDbConnection(cfg);
        initResources(cfg);

        String studyGuid = cfg.getString(LoaderConfigFile.STUDY_GUID);
        String outputFilename = null;
        if (cmd.hasOption("output")) {
            outputFilename = cmd.getOptionValue("output");
        }

        log.info("Running data migration for study: {}", studyGuid);
        var fileReader = new FileReader(cfg);
        var loader = new DataLoader(cfg, fileReader, isProdRun);

        Instant start = Instant.now();
        if (loadMailingList) {
            loader.processMailingListFiles();
        }
        if (loadParticipants) {
            var report = new Report();
            try {
                loader.processParticipantFiles(report);
            } catch (Exception e) {
                log.info("Error while processing participant files", e);
                writeReport(studyGuid, outputFilename, report);
                return;
            }
            writeReport(studyGuid, outputFilename, report);
        }
        if (loadDsmData) {
            loader.processDsmFiles();
        }
        if (fixFamilyNotes) {
            loader.fixFamilyNotes();
        }

        Duration elapsed = Duration.between(start, Instant.now());
        String minutes = String.format("%.2f", elapsed.getSeconds() / 60.0);
        log.info("Total time elapsed: {} minutes ({})", minutes, elapsed);
        log.info("Done");
    }

    private static void initDbConnection(Config cfg) {
        String dbUrl = cfg.getString(LoaderConfigFile.DB_URL);
        String dsmDbUrl = cfg.getString(LoaderConfigFile.DSM_DB_URL);
        TimeZone.setDefault(TimeZone.getTimeZone(cfg.getString(LoaderConfigFile.DEFAULT_TIMEZONE)));

        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(
                TransactionWrapper.DB.APIS, DB_MAX_CONNECTIONS, dbUrl));
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);
        DBUtils.loadDaoSqlCommands(sqlConfig);
        log.info("Initialized db pool: {}", dbUrl);

        DsmDataLoader.initDatabasePool(dsmDbUrl, DB_MAX_CONNECTIONS);
        log.info("Initialized dsm db pool: {}", dsmDbUrl);
    }

    private static void initResources(Config cfg) {
        CacheService.getInstance(); // Make getInstance() call to "prime" the service.
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
        EncryptionKey.setEncryptionKey(cfg.getString(LoaderConfigFile.AUTH0_ENCRYPTION_SECRET));
    }

    private static void writeReport(String studyGuid, String filename, Report report) {
        if (filename == null || filename.isBlank()) {
            filename = Report.defaultFilename(studyGuid);
        }
        report.write(filename);
        log.info("Saved{}migration report to: {}", report.isPartial() ? " partial " : " ", filename);
    }
}
