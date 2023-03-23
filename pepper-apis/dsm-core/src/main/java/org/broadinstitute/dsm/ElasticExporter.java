package org.broadinstitute.dsm;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.dsm.util.DSMConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.broadinstitute.dsm.pubsub.DSMtasksSubscription.CLEAR_BEFORE_UPDATE;
import static org.broadinstitute.dsm.pubsub.DSMtasksSubscription.doESExport;



public class ElasticExporter {
    private static final Logger logger = LoggerFactory.getLogger(ElasticExporter.class);
    private static final String IS_MIGRATION_OPTION = "isMigration";
    private static final String STUDY_OPTION = "study";
    private static final String gaeDeployDir = "appengine/deploy";
    private static final String vaultConf = "vault.conf";
    public static final String GCP_PATH_TO_SERVICE_ACCOUNT = "portal.googleProjectCredentials";
    private static final AtomicBoolean isReady = new AtomicBoolean(false);

    private static ElasticExporter dsmBackend;

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        Map<String, String> attributesMap = new HashMap<>();

        options.addOption(CLEAR_BEFORE_UPDATE, false, "If set, will clear the Elastic index prior to the udpate.");
        options.addOption(IS_MIGRATION_OPTION, false, "If set, will treat this as a migration.");
        options.addRequiredOption(STUDY_OPTION, "studyName", true, "The name of the study to generate the index for.");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        logger.info("ElasticExporter Started");
        if (cmd.hasOption(CLEAR_BEFORE_UPDATE)) {
            attributesMap.put(CLEAR_BEFORE_UPDATE, "true");
        }

        boolean isMigration = cmd.hasOption(IS_MIGRATION_OPTION);
        String studyName = cmd.getOptionValue(STUDY_OPTION);

        String data = String.format("{\"study\" : \"%s\" , \"isMigration\" : %b}", studyName.trim(), isMigration);

        startDSMCore();

        doESExport(attributesMap, data);

        isReady.set(true);
        logger.info("ElasticExporter Complete");
        System.exit(0);
    }

    private static void startDSMCore() {
        synchronized (isReady) {
            logger.info("Starting up DSM");
            //config without secrets
            Config cfg = ConfigFactory.load();
            //secrets from vault in a config file
            File vaultConfigInCwd = new File(vaultConf);
            File vaultConfigInDeployDir = new File(gaeDeployDir, vaultConf);
            File vaultConfig = vaultConfigInCwd.exists() ? vaultConfigInCwd : vaultConfigInDeployDir;
            logger.info("Reading config values from " + vaultConfig.getAbsolutePath());
            cfg = cfg.withFallback(ConfigFactory.parseFile(vaultConfig));

            if (cfg.hasPath(GCP_PATH_TO_SERVICE_ACCOUNT)) {
                if (StringUtils.isNotBlank(cfg.getString("portal.googleProjectCredentials"))) {
                    System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", cfg.getString("portal.googleProjectCredentials"));
                }
            }

            new DSMConfig(cfg);

            dsmBackend = new ElasticExporter();
            dsmBackend.configureServer(cfg);
            isReady.set(true);
            logger.info("DSM Startup Complete");
        }
    }
    private void configureServer(@NonNull Config config) {
        setupDB(config);
    }

    protected void setupDB(@NonNull Config config) {
        logger.info("Setup the DB...");

        int maxConnections = config.getInt("portal.maxConnections");
        String dbUrl = config.getString("portal.dbUrl");

        //setup the mysql transaction/connection utility
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.DSM, maxConnections, dbUrl));

        logger.info("DB setup complete.");
    }
}
