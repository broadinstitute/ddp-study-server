package org.broadinstitute.ddp.migration;

import java.io.File;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLoaderCli {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderCli.class);
    private static final String USAGE = "DataLoaderCli [-h, --help] [OPTIONS]";

    public static void main(String[] args) throws Exception {
        var options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("c", "config", true, "path to loader config file (required)");
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
        initCachedData();
        var fileReader = new SourceFileReader(cfg, useBucket);
        var loader = new DataLoader(cfg, fileReader);

        LOG.info("Running data migration for study: {}", loader.getStudyGuid());
        if (loadMailingList) {
            loader.processMailingListFiles();
        }
        if (loadParticipants) {
            loader.processParticipantFiles();
        }
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

    private static void initCachedData() {
        CacheService.getInstance(); // Make get instance call to "prime" the service.
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
    }
}
