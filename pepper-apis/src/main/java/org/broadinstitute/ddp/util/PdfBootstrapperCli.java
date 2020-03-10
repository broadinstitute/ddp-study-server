package org.broadinstitute.ddp.util;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.opencsv.CSVWriter;
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
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.dsm.StudyPdfMapping;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To run:
 *
 * <pre>
 * $ GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json java -Dconfig.file=/path/to/app.conf \
 *   -cp ./target/DataDonationPlatform.jar org.broadinstitute.ddp.util.PdfBootstrapperCli -h
 * </pre>
 *
 * <p>Important properties to have in config file:
 * dbUrl, maxConnections, requireDefaultGcpCredentials, googleProjectId, pdfArchiveBucket
 */
public class PdfBootstrapperCli {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBootstrapperCli.class);
    private static final String USAGE = "PdfBootstrapperCli [-h, --help] [OPTIONS]";
    private static final int DISPLAY_WIDTH = 80;

    private Config cfg;

    public static void main(String[] args) throws Exception {
        PdfBootstrapperCli app = new PdfBootstrapperCli();
        app.run(args);
    }

    private void run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("g", "guids", true, "user guids to generate pdf for");
        options.addOption("a", "all", false, "generate pdfs for all users");
        options.addOption("c", "count", false, "count up pdfs to be generated for users");
        options.addOption("s", "study", true, "study guid, required");
        options.addOption(null, "consent", true, "consent activity code, required");
        options.addOption(null, "release", true, "release activity code, required");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        HelpFormatter formatter = new HelpFormatter();
        if (cmd.hasOption("help")) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "", options, "");
            return;
        }

        if (!cmd.hasOption("s")) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "You must provide the study [s]", options, "");
            return;
        }
        String studyGuid = cmd.getOptionValue("s");

        if (!cmd.hasOption("consent")) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "You must provide the consent activity code", options, "");
            return;
        }
        String consentActCode = cmd.getOptionValue("consent");

        if (!cmd.hasOption("release")) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "You must provide the release activity code", options, "");
            return;
        }
        String releaseActCode = cmd.getOptionValue("release");

        boolean hasGuids = cmd.hasOption("g");
        boolean hasAll = cmd.hasOption("a");
        int choicesMade = (hasGuids ? 1 : 0) + (hasAll ? 1 : 0);
        if (choicesMade != 1) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "You must select only one of: [g, a]", options, "");
            return;
        }

        initDbConnection();

        TransactionWrapper.useTxn(apisHandle -> {
            StudyDto studyDto = apisHandle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            JdbiActivity jdbiActivity = apisHandle.attach(JdbiActivity.class);
            long consentActId = jdbiActivity.findIdByStudyIdAndCode(studyDto.getId(), consentActCode)
                    .orElseThrow(() -> new DDPException("Could not find consent activity " + consentActCode));
            long releaseActId = jdbiActivity.findIdByStudyIdAndCode(studyDto.getId(), releaseActCode)
                    .orElseThrow(() -> new DDPException("Could not find release activity " + releaseActCode));

            JdbiStudyPdfMapping jdbiPdfMapping = apisHandle.attach(JdbiStudyPdfMapping.class);
            long consentPdfId = jdbiPdfMapping
                    .findByStudyIdAndMappingType(studyDto.getId(), PdfMappingType.CONSENT)
                    .map(StudyPdfMapping::getPdfConfigurationId)
                    .orElseThrow(() -> new DDPException("Could not find CONSENT pdf mapping"));
            long releasePdfId = jdbiPdfMapping
                    .findByStudyIdAndMappingType(studyDto.getId(), PdfMappingType.RELEASE)
                    .map(StudyPdfMapping::getPdfConfigurationId)
                    .orElseThrow(() -> new DDPException("Could not find RELEASE pdf mapping"));

            Stream<UserPdfInfo> stream = apisHandle.attach(SqlHelper.class)
                    .findPdfInfosForAllUsersInStudy(studyDto.getId(), consentActId, releaseActId);

            if (hasGuids) {
                List<String> guidsForPdfGeneration = Arrays.asList(cmd.getOptionValue('g').split(","));
                stream = stream.filter(info -> guidsForPdfGeneration.contains(info.userGuid));
            }

            if (cmd.hasOption("count")) {
                processCounts(studyDto, stream);
            } else {
                processGeneration(apisHandle, studyDto, consentPdfId, releasePdfId, stream);
            }
        });
    }

    private void log(String fmt, Object... args) {
        System.out.println("[pdf] " + String.format(fmt, args));
    }

    private void initDbConnection() {
        cfg = ConfigManager.getInstance().getConfig();
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        log("using database connection: %s", dbUrl);

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper
                .DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));

        DBUtils.loadDaoSqlCommands(sqlConfig);
    }

    private void processCounts(StudyDto studyDto, Stream<UserPdfInfo> stream) {
        String filename = String.format("%s_pdf_counts_%s.csv", studyDto.getGuid(), Instant.now().toString());
        AtomicLong userCount = new AtomicLong();
        AtomicLong consentCount = new AtomicLong();
        AtomicLong releaseCount = new AtomicLong();

        try (BufferedWriter output = Files.newBufferedWriter(Paths.get(filename))) {
            CSVWriter writer = new CSVWriter(output);

            String[] headers = new String[] {
                    "participant_guid", "has_completed_consent", "has_completed_release", "has_address"
            };
            writer.writeNext(headers, false);

            stream.forEach(info -> {
                userCount.addAndGet(1);
                consentCount.addAndGet(info.hasCompletedConsent ? 1 : 0);
                releaseCount.addAndGet(info.hasCompletedRelease ? 1 : 0);
                String[] row = new String[] {
                        info.userGuid,
                        info.hasCompletedConsent ? "1" : "0",
                        info.hasCompletedRelease ? "1" : "0",
                        info.hasAddress ? "1" : "0"
                };
                writer.writeNext(row, false);
            });

            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new DDPException("Error while writing counts to csv file " + filename, e);
        }

        log("Totals: users=%d consentPdfs=%d releasePdfs=%d totalPdfs=%d", userCount.get(),
                consentCount.get(), releaseCount.get(), consentCount.get() + releaseCount.get());
        log("Counts written to file: %s", filename);
    }

    private void processGeneration(Handle handle, StudyDto studyDto,
                                   long consentPdfId, long releasePdfId,
                                   Stream<UserPdfInfo> stream) {
        PdfService pdfService = new PdfService();
        PdfBucketService pdfBucketService = new PdfBucketService(cfg);
        PdfGenerationService pdfGenerationService = new PdfGenerationService();
        log("Uploads of pdf will be made to bucket=%s", pdfBucketService.getBucketName());

        AtomicLong userCount = new AtomicLong();
        AtomicLong consentCount = new AtomicLong();
        AtomicLong releaseCount = new AtomicLong();

        stream.forEach(info -> {
            if (info.hasCompletedConsent) {
                log("Generating consent pdf for %s", info.userGuid);
                PdfConfiguration pdfConfiguration = pdfService.findFullConfigForUser(
                        handle, consentPdfId, info.userGuid, studyDto.getGuid());
                try {
                    String blobName = pdfService.generateAndUpload(handle, pdfGenerationService, pdfBucketService,
                            pdfConfiguration, info.userGuid, studyDto.getGuid());
                    log("Uploaded consent pdf file: %s", blobName);
                    consentCount.incrementAndGet();
                } catch (Exception e) {
                    LOG.error("Could not generate pdf={} for participantGuid={}",
                            pdfConfiguration.getConfigName(), info.userGuid, e);
                }
            }

            if (info.hasCompletedRelease) {
                log("Generating release pdf for %s", info.userGuid);
                PdfConfiguration pdfConfiguration = pdfService.findFullConfigForUser(
                        handle, releasePdfId, info.userGuid, studyDto.getGuid());
                try {
                    String blobName = pdfService.generateAndUpload(handle, pdfGenerationService, pdfBucketService,
                            pdfConfiguration, info.userGuid, studyDto.getGuid());
                    log("Uploaded release pdf file: %s", blobName);
                    releaseCount.incrementAndGet();
                } catch (Exception e) {
                    LOG.error("Could not generate pdf={} for participantGuid={}",
                            pdfConfiguration.getConfigName(), info.userGuid, e);
                }
            }

            userCount.incrementAndGet();
        });

        log("Processed: users=%d consentPdfs=%d releasePdfs=%d totalPdfs=%d", userCount.get(),
                consentCount.get(), releaseCount.get(), consentCount.get() + releaseCount.get());
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select u.user_id,"
                + "       u.guid as user_guid,"
                + "       (select 1 from activity_instance as ai"
                + "          join activity_instance_status as stat on stat.activity_instance_id = ai.activity_instance_id"
                + "          join activity_instance_status_type as stype"
                + "               on stype.activity_instance_status_type_id = stat.activity_instance_status_type_id"
                + "          join study_activity as act on act.study_activity_id = ai.study_activity_id"
                + "         where act.study_activity_id = :consentActivityId"
                + "           and act.study_id = :studyId"
                + "           and ai.participant_id = u.user_id"
                + "           and stype.activity_instance_status_type_code = 'COMPLETE'"
                + "         limit 1"
                + "       ) has_completed_consent,"
                + "       (select 1 from activity_instance as ai"
                + "          join activity_instance_status as stat on stat.activity_instance_id = ai.activity_instance_id"
                + "          join activity_instance_status_type as stype"
                + "               on stype.activity_instance_status_type_id = stat.activity_instance_status_type_id"
                + "          join study_activity as act on act.study_activity_id = ai.study_activity_id"
                + "         where act.study_activity_id = :releaseActivityId"
                + "           and act.study_id = :studyId"
                + "           and ai.participant_id = u.user_id"
                + "           and stype.activity_instance_status_type_code = 'COMPLETE'"
                + "         limit 1"
                + "       ) has_completed_release,"
                + "       (select 1 from default_mailing_address"
                + "         where participant_user_id = u.user_id"
                + "       ) as has_address"
                + "  from user_study_enrollment as usen"
                + "  join user as u on u.user_id = usen.user_id"
                + " where usen.valid_to is null"
                + "   and usen.study_id = :studyId"
                + "  order by u.guid asc")
        @RegisterConstructorMapper(UserPdfInfo.class)
        Stream<UserPdfInfo> findPdfInfosForAllUsersInStudy(@Bind("studyId") long studyId,
                                                           @Bind("consentActivityId") long consentActivityId,
                                                           @Bind("releaseActivityId") long releaseActivityId);
    }

    public static class UserPdfInfo {

        final long userId;
        final String userGuid;
        final boolean hasCompletedConsent;
        final boolean hasCompletedRelease;
        final boolean hasAddress;

        @JdbiConstructor
        public UserPdfInfo(@ColumnName("user_id") long userId,
                           @ColumnName("user_guid") String userGuid,
                           @ColumnName("has_completed_consent") boolean hasCompletedConsent,
                           @ColumnName("has_completed_release") boolean hasCompletedRelease,
                           @ColumnName("has_address") boolean hasAddress) {
            this.userId = userId;
            this.userGuid = userGuid;
            this.hasCompletedConsent = hasCompletedConsent;
            this.hasCompletedRelease = hasCompletedRelease;
            this.hasAddress = hasAddress;
        }
    }
}
