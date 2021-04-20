package org.broadinstitute.ddp.migration;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLoaderCli {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderCli.class);
    private static final String USAGE = "DataLoaderCli [-h, --help] [OPTIONS]";

    public static void main(String[] args) throws Exception {
        var options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("c", "config", true, "path to loader config file (required)");
        options.addOption("o", "output", true, "path for output migration report, will use a generated name if not provided");
        options.addOption(null, "bucket", false, "use bucket for source files");
        options.addOption(null, "mailing-list", false, "load mailing list contacts");
        options.addOption(null, "participants", false, "load participant files");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        String configFilePath = cmd.getOptionValue("config");
        if (StringUtils.isBlank(configFilePath)) {
            LOG.error("Loader config file is required");
            return;
        }
        Config cfg = ConfigFactory.parseFile(new File(configFilePath));

        boolean useBucket = cmd.hasOption("bucket");
        boolean loadMailingList = cmd.hasOption("mailing-list");
        boolean loadParticipants = cmd.hasOption("participants");
        if (!loadMailingList && !loadParticipants) {
            LOG.info("Nothing to do, exiting...");
            return;
        }

        initDbConnection(cfg);
        initResources(cfg);

        String studyGuid = cfg.getString(LoaderConfigFile.STUDY_GUID);
        String outputFilename = null;
        if (cmd.hasOption("output")) {
            outputFilename = cmd.getOptionValue("output");
        }

        LOG.info("Running data migration for study: {}", studyGuid);
        var fileReader = new FileReader(cfg, useBucket);
        var loader = new DataLoader(cfg, fileReader);

        Instant start = Instant.now();
        if (loadMailingList) {
            loader.processMailingListFiles();
        }
        if (loadParticipants) {
            var report = loader.processParticipantFiles();
            writeReport(studyGuid, outputFilename, report);
        }

        Duration elapsed = Duration.between(start, Instant.now());
        String minutes = String.format("%.2f", elapsed.getSeconds() / 60.0);
        LOG.info("Total time elapsed: {} minutes ({})", minutes, elapsed.toString());
        LOG.info("Done");
    }

    private static void initDbConnection(Config cfg) {
        String dbUrl = cfg.getString(LoaderConfigFile.DB_URL);
        int maxConnections = cfg.getInt(LoaderConfigFile.MAX_CONNECTIONS);
        TimeZone.setDefault(TimeZone.getTimeZone(cfg.getString(LoaderConfigFile.DEFAULT_TIMEZONE)));

        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);
        DBUtils.loadDaoSqlCommands(sqlConfig);

        LOG.info("Initialized db pool: {}", dbUrl);
    }

    private static void initResources(Config cfg) {
        CacheService.getInstance(); // Make get instance call to "prime" the service.
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
        EncryptionKey.setEncryptionKey(cfg.getString(LoaderConfigFile.AUTH0_ENCRYPTION_SECRET));
    }

    private static void writeReport(String studyGuid, String filename, Report report) {
        if (filename == null || filename.isBlank()) {
            filename = Report.defaultFilename(studyGuid);
        }
        report.write(filename);
        LOG.info("Saved migration report to: {}", filename);
    }
}
