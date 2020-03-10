package org.broadinstitute.ddp.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.export.DataExporter;

/**
 * Utility to run data export via command-line. Note that a config file is required, and the main thing we look for in the configuration
 * file is the database connection details and auth0 encryption key.
 *
 * <pre>
 * $ mvn -DskipTests clean package
 * $ java -Dconfig.file=/path/to/cli.conf -cp /path/to/DataDonationPlatform.jar org.broadinstitute.ddp.util.DataExporterCli --help
 * </pre>
 * An example below exports a structured document to the Elasticsearch. Hint: you can replace values in the config file to point
 * to a local ES instance.
 * <pre>
 * $ java -Dconfig.file=./application.conf -cp ./DataDonationPlatform.jar org.broadinstitute.ddp.util.DataExporterCli -e -s 'study-AABBCC'
 * </pre>
 */
public class DataExporterCli {

    private static final String USAGE = "DataExporterCli [-h, --help] [OPTIONS] STUDY_GUID";

    private Config cfg;
    private Config sqlConfig;
    private DataExporter exporter;

    public static void main(String[] args) throws Exception {
        DataExporterCli app = new DataExporterCli();
        app.run(args);
    }

    private void run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("c", "csv", false, "export as CSV");
        options.addOption("o", "output", true, "output file path");
        options.addOption("e", "elastic", false, "export to elasticsearch");
        options.addOption("m", "mappings", false, "export mappings file instead of dataset");
        options.addOption("g", "guids", true, "specific user guids to export");
        options.addOption("s", "structured", false, "export a structured document");
        options.addOption("u", "users", false, "export study users document");
        options.addOption("ad", "activitydefinitions", false, "export study activity definitions to elasticsearch");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        String[] positional = cmd.getArgs();

        HelpFormatter formatter = new HelpFormatter();
        if (cmd.hasOption("help")) {
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }
        if (positional == null || positional.length < 1) {
            System.out.println("[export] study guid is required");
            return;
        }

        boolean csvExport = cmd.hasOption("c");
        boolean elasticExport = cmd.hasOption("e");
        boolean mappingFileOnly = cmd.hasOption("m");
        boolean hasSpecificGuids = cmd.hasOption("g");
        boolean exportStructuredDocument = cmd.hasOption("s");
        boolean exportActivityDefinitions = cmd.hasOption("ad");
        boolean exportUsersDocument = cmd.hasOption("u");

        int choicesMade = (csvExport ? 1 : 0) + (elasticExport ? 1 : 0) + (mappingFileOnly ? 1 : 0)
                + (exportActivityDefinitions ? 1 : 0) + (exportUsersDocument ? 1 : 0);
        if (choicesMade != 1) {
            formatter.printHelp(80, USAGE, "You must select only one of: [c, e, u, ad or m]", options, "");
            return;
        }

        String studyGuid = positional[0];
        initDbConnection();
        initExporter();

        Optional<String> filename = Optional.ofNullable(cmd.getOptionValue("output"));
        if (mappingFileOnly) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
            runMappingsExport(studyGuid,
                    filename.orElse(String.format("%s_mappings_%s.json", studyGuid, fmt.format(Instant.now()))));

        } else if (csvExport) {
            if (hasSpecificGuids) {
                throw new Exception("Specific GUIDs option is only available for the ES export. We're deprecating CSV shortly");
            }
            runExport(studyGuid,
                    filename.orElse(DataExporter.makeExportCSVFilename(studyGuid, Instant.now())));
        } else if (elasticExport) {
            Set<String> specificGuids = null;
            if (hasSpecificGuids) {
                specificGuids = new HashSet<>(Arrays.asList(cmd.getOptionValue("g").split(",")));
            }
            runEsExport(studyGuid, specificGuids, exportStructuredDocument);
        } else if (exportActivityDefinitions) {
            runActivityDefinitionExportToES(studyGuid);
        } else if (exportUsersDocument) {
            runUsersExportToElasticsearch(studyGuid);
        }
    }

    private void initDbConnection() {
        cfg = ConfigManager.getInstance().getConfig();
        sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        System.out.println("[export] using database connection: " + dbUrl);

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        TransactionWrapper.reset();
        String defaultTimeZoneName = cfg.getString(ConfigFile.DEFAULT_TIMEZONE);
        TransactionWrapper.init(new TransactionWrapper
                .DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));

        DBUtils.loadDaoSqlCommands(sqlConfig);
    }

    private void initExporter() {
        exporter = new DataExporter(cfg);
    }

    private void runEsExport(String studyGuid, Set<String> participantGuids, boolean exportStructuredDocument) {
        TransactionWrapper.useTxn(handle -> {
            System.out.println("[export] warming up caches...");
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            // Warm up the activity cache
            exporter.extractActivities(handle, studyDto);

            // Warm up the emails cache
            DataExporter.fetchAndCacheAuth0Emails(handle, studyGuid,
                    handle.select("select auth0_user_id from user").mapTo(String.class).stream().collect(Collectors.toSet()));

            System.out.println(String.format("[export] starting %s json elasticsearch export...",
                    exportStructuredDocument ? "structured" : "flat"));

            long start = System.currentTimeMillis();
            exporter.exportParticipantsToElasticsearchByGuids(handle, studyDto, participantGuids, exportStructuredDocument);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(String.format("[export] took %d ms (%.2f s)", elapsed, elapsed / 1000.0));
        });
    }

    private void runUsersExportToElasticsearch(String studyGuid) {
        TransactionWrapper.useTxn(handle -> {
            System.out.println("[user export] warming up caches...");
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            // Warm up the emails cache
            DataExporter.fetchAndCacheAuth0Emails(handle, studyGuid,
                    handle.select("select auth0_user_id from user").mapTo(String.class).stream().collect(Collectors.toSet()));

            System.out.println("[export] starting elasticsearch user export...");

            long start = System.currentTimeMillis();
            exporter.exportUsersToElasticsearch(handle, studyDto, null);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(String.format("[users export] took %d ms (%.2f s)", elapsed, elapsed / 1000.0));
        });
    }

    private void runExport(String studyGuid, String filename) throws IOException {
        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            System.out.println("[export] generating csv export to file: " + filename);
            exporter.exportCsvToFile(handle, studyDto, Paths.get(filename));
            System.out.println("[export] finished export to file: " + filename);
        });
    }

    private void runActivityDefinitionExportToES(String studyGuid) throws Exception {
        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            exporter.exportActivityDefinitionsToElasticsearch(handle, studyDto, cfg);
        });
    }

    private void runMappingsExport(String studyGuid, String filename) throws IOException {
        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            List<ActivityExtract> activities = exporter.extractActivities(handle, studyDto);

            System.out.println("[export] generating mappings to file: " + filename);

            Map<String, Object> props = exporter.exportStudyDataMappings(activities);
            Map<String, Object> doc = new HashMap<>();
            doc.put("properties", props);
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("_doc", doc);
            Map<String, Object> root = new HashMap<>();
            root.put("mappings", mappings);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename));
            writer.write(gson.toJson(root));
            writer.close();

            System.out.println("[export] finished mappings export to file: " + filename);
        });
    }
}
