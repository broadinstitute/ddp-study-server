package org.broadinstitute.ddp.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.service.OLCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OLCBackfillScript {
    private static final Logger LOG = LoggerFactory.getLogger(OLCBackfillScript.class);

    private static String outputFileName;

    public static void main(String[] args) throws Exception {
        initDbConnection();
        Config cfg = ConfigManager.getInstance().getConfig();

        Options options = new Options();
        options.addRequiredOption("o", "output_file", true, "Output/Report file");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        outputFileName = cmd.getOptionValue('o');

        BufferedWriter outputFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(outputFileName))));

        outputFile.write("Modified Addresses:");
        outputFile.newLine();
        String geocodingApiKey = cfg.getString(ConfigFile.GEOCODING_API_KEY);

        processRecords(outputFile, geocodingApiKey);

        outputFile.close();
    }

    public static void processRecords(Writer outputFile, String geocoding) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            Map<String, String> addressIdToAddressString = handle.createQuery("select ma.*, cai.name as country_name"
                    + " from mailing_address ma "
                    + " left join country_address_info as cai on ma.country_id = cai.country_address_info_id"
                    + " where ma.pluscode is null")
                    .mapToMap()
                    .list()
                    .stream()
                    .filter(row -> StringUtils.isEmpty((String) row.get("pluscode")))
                    .collect(Collectors.toMap(row -> (String) row.get("address_guid"),
                            row -> String.join(", ",
                                    (String) row.get("street1"),
                                    (String) row.get("city"),
                                    (String) row.get("state"),
                                    (String) row.get("postal_code"),
                                    (String) row.get("country_name"))));

            OLCService olcService = new OLCService(geocoding);
            Map<String, String> addressIdToPlusCode = addressIdToAddressString.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> olcService.calculatePlusCodeWithPrecision(entry.getValue(), OLCPrecision.MOST)));

            addressIdToPlusCode
                    .entrySet()
                    .forEach(entry -> {
                        int linesWritten = handle.createUpdate("update mailing_address set pluscode = :pluscode where address_guid = :guid")
                                .bind("pluscode", entry.getValue())
                                .bind("guid", entry.getKey()).execute();
                        if (linesWritten != 1) {
                            throw new RuntimeException("Expected to write 1 line updating " + entry.getKey()
                                    + " but wrote " + linesWritten);
                        }
                        try {
                            outputFile.write(entry.getKey() + ",");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }

    public static void initDbConnection() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());

        LOG.info("Initializing db pool for " + dbUrl);
        LiquibaseUtil.runLiquibase(dbUrl, TransactionWrapper.DB.APIS);

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        TransactionWrapper.reset();
        TransactionWrapper.init(cfg.getString(ConfigFile.DEFAULT_TIMEZONE),
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections,
                dbUrl));

        DBUtils.loadDaoSqlCommands(sqlConfig);
    }
}
