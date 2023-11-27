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
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.service.I18nTranslationService;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class QuestionsExporter {
    private static final String USAGE = "QuestionsExporter [-h, --help] [OPTIONS]";

    private static Map<Long, String> picklistPossibleAnswers;
    private static CommandLine cmd;

    public static void main(String[] args) throws Exception {
        initDbConnection();

        final Options options = new Options();
        options.addOption("h", "help", false, "print help message");
        options.addOption("l", "picklist-limit", true, "Picklist possible answers limit");
        options.addRequiredOption("o", "output-file", true, "Output File");
        options.addRequiredOption("s", "study-guid", true, "Study GUID");

        cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp(USAGE, options);
            return;
        }

        if (!cmd.hasOption("picklist-limit")) {
            log.warn("The picklist-limit option wasn't specified. Using default limit: 100");
        }

        log.info("Loading questions...");
        final List<QuestionWrapper> questions = getQuestions(cmd.getOptionValue("study-guid"));
        log.info("{} questions found", questions.size());

        if (questions.isEmpty()) {
            log.info("No questions to export");
            return;
        }

        log.info("Loading possible answers for picklist questions...");
        picklistPossibleAnswers = getPicklistPossibleAnswers(StreamEx.of(questions)
                .map(QuestionWrapper::getId)
                .toList());
        log.info("{} picklist questions found", picklistPossibleAnswers.size());

        log.info("Exporting questions...");
        export(questions, cmd.getOptionValue("output-file"));
        log.info("{} questions successfully exported to {} file", questions.size(), cmd.getOptionValue("output-file"));

        System.exit(0);
    }

    private static void export(final List<QuestionWrapper> questions, final String outputFile) throws IOException {
        CsvMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
                .build().writerFor(QuestionWrapper.class)
                .with(CsvSchema.builder()
                        .setUseHeader(true)
                        .addColumn("id")
                        .addColumn("stableId")
                        .addColumn("type")
                        .addColumn("text")
                        .addColumn("validationTypes")
                        .addColumn("possibleAnswers")
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

    private static String translate(final Long templateId) {
        return TransactionWrapper.withTxn(handle -> handle.attach(TemplateDao.class).getJdbiTemplate().fetch(templateId)
                .map(Template::getTemplateText)
                .map(text -> text.replace("$", ""))
                .map(QuestionsExporter::translate)
                .orElse(null));
    }

    private static String translate(final String templateText) {
        return new I18nTranslationService().getTranslation(templateText, cmd.getOptionValue("study-guid"), "en");
    }

    private static String translate(final List<PicklistOptionDto> picklistOptions) {
        log.info("Loading translations for {} possible answers...", picklistOptions.size());
        if (picklistOptions.size() > Optional.ofNullable(cmd.getOptionValue("picklist-limit"))
                .map(Integer::parseInt)
                .orElse(100)) {
            log.info("The limit of possible options is reached. Question skipped");
            return "";
        }

        return StreamEx.of(picklistOptions)
                .map(PicklistOptionDto::getOptionLabelTemplateId)
                .map(QuestionsExporter::translate)
                .filter(Objects::nonNull)
                .joining(";");
    }

    private static Map<Long, String> getPicklistPossibleAnswers(final List<Long> questionIds) {
        final var picklistOptions = TransactionWrapper.withTxn(handle -> handle.attach(PicklistQuestionDao.class)
                .findOrderedGroupAndOptionDtos(questionIds, System.currentTimeMillis()));
        log.info("{} picklist questions found. Loading translations for each of them...", picklistOptions.size());

        return EntryStream.of(picklistOptions)
                .mapValues(PicklistQuestionDao.GroupAndOptionDtos::getUngroupedOptions)
                .mapValues(QuestionsExporter::translate)
                .toMap();
    }

    @Value
    @AllArgsConstructor
    @SuppressWarnings("unused")
    private static class QuestionWrapper {
        QuestionDto question;
        List<RuleDto> rules;

        private <R> R getSafely(final Function<QuestionDto, R> methodReference) {
            return Optional.ofNullable(question).map(methodReference).orElse(null);
        }

        public Long getId() {
            return getSafely(QuestionDto::getId);
        }

        public String getStableId() {
            return getSafely(QuestionDto::getStableId);
        }

        public QuestionType getType() {
            return getSafely(QuestionDto::getType);
        }

        public String getText() {
            return translate(getSafely(QuestionDto::getPromptTemplateId));
        }

        public String getValidationTypes() {
            return StreamEx.of(rules).map(RuleDto::toString).joining(";");
        }

        public String getPossibleAnswers() {
            if (QuestionType.PICKLIST != getType()) {
                return "";
            }

            return picklistPossibleAnswers.getOrDefault(getId(), null);
        }
    }
}
