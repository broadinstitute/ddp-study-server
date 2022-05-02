package org.broadinstitute.ddp.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
public class QuestionsExporter {
    private static final String USAGE = "StudyDataLoaderMain [-h, --help] [OPTIONS]";

    public static void main(String[] args) throws Exception {
        initDbConnection();

        final Options options = new Options();
        options.addOption("h", "help", false, "print help message");
        options.addRequiredOption("o", "output-file", true, "Output File");
        options.addRequiredOption("s", "study-guid", true, "Study GUID");

        final CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp(80, USAGE, "", options, "");
            return;
        }

        final List<QuestionWrapper> questions = getQuestions(cmd.getOptionValue("study-guid"));
        if (questions.isEmpty()) {
            log.info("No questions to export");
            return;
        }

        export(questions, cmd.getOptionValue("output-file"));

        log.info("{} questions successfully exported to {} file", questions.size(), cmd.getOptionValue("output-file"));
    }

    private static void export(final List<QuestionWrapper> questions, final String outputFile) throws IOException {
        final CsvMapper mapper = CsvMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
                .build();

        mapper.writerFor(QuestionWrapper.class)
                .with(CsvSchema.builder().setUseHeader(true)
                        .addColumn("questionId")
                        .addColumn("questionType")
                        .addColumn("validationTypes")
                        .build())
                .writeValues(new File(outputFile))
                .writeAll(questions);
    }

    private static List<QuestionWrapper> getQuestions(final String studyGuid) {
        return TransactionWrapper.withTxn(handle -> {
            final var validationDao = handle.attach(ValidationDao.class).getJdbiQuestionValidation();
            return StreamEx.of(handle.attach(QuestionDao.class).getJdbiQuestion().findByStudyGuid(studyGuid))
                    .map(question -> new QuestionWrapper(question, validationDao.getAllActiveValidations(question)))
                    .toList();
        });
    }

    public static void initDbConnection() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        log.info("Initializing db pool");
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                maxConnections, cfg.getString(ConfigFile.DB_URL)));
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
        DBUtils.loadDaoSqlCommands(sqlConfig);
    }

    @Value
    @AllArgsConstructor
    private static class QuestionWrapper {
        QuestionDto question;
        List<RuleDto> rules;

        public Long getQuestionId() {
            return Optional.ofNullable(question).map(QuestionDto::getId).orElse(null);
        }

        public QuestionType getQuestionType() {
            return Optional.ofNullable(question).map(QuestionDto::getType).orElse(null);
        }

        public String getValidationTypes() {
            return StreamEx.of(rules).map(RuleDto::getRuleType).joining();
        }
    }
}
