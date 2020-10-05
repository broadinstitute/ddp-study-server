package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTextQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainConsentVersion2.class);
    private static final String DATA_FILE = "patches/consent-version2.conf";
    private static final String BRAIN = "cmi-brain";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(BRAIN)) {
            throw new DDPException("This task is only for the " + BRAIN + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now(); //parse(dataCfg.getString("timestamp"));
        this.cfgPath = cfgPath;
        gson = GsonUtil.standardGson();

    }

    @Override
    public void run(Handle handle) {
        //creates version: 2 for CONSENT (self/adult consent) activity.

        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        PdfBuilder builder = new PdfBuilder(cfgPath.getParent(), cfg, studyDto, adminUser.getId());

        String activityCode = dataCfg.getString("activityCode");
        String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();
        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);

        SqlHelper helper = handle.attach(SqlHelper.class);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        ActivityDao activityDao = handle.attach(ActivityDao.class);

        //change version
        ActivityVersionDto activityVersionDto = activityDao.changeVersion(activityId, versionTag, meta);

        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDto fullNameDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CONSENT_FULLNAME").get();
        TextQuestionDto fullNameTextDto = handle.attach(JdbiTextQuestion.class).findDtoByQuestionId(fullNameDto.getId()).get();
        long fullNameBlockId = helper.findQuestionBlockId(fullNameDto.getId());
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        SectionBlockMembershipDto fullNameSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(fullNameBlockId).get();
        long fullNameRevId = fullNameDto.getRevisionId();

        QuestionDto dobDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CONSENT_DOB").get();
        long dobBlockId = helper.findQuestionBlockId(dobDto.getId());
        SectionBlockMembershipDto dobSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(dobBlockId).get();
        long newV2RevId = jdbiRevision.insertStart(timestamp.toEpochMilli(), adminUser.getId(), "consent version#2 change");
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        int dobDisplayOrder = dobSectionDto.getDisplayOrder();

        TextQuestionDef firstNameDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("firstNameQuestion")), TextQuestionDef.class);
        QuestionBlockDef blockDef = new QuestionBlockDef(firstNameDef);
        sectionBlockDao.insertBlockForSection(activityId, fullNameSectionDto.getSectionId(), dobDisplayOrder - 5, blockDef, newV2RevId);

        TextQuestionDef lastNameDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("lastNameQuestion")), TextQuestionDef.class);
        QuestionBlockDef lnBlockDef = new QuestionBlockDef(lastNameDef);
        sectionBlockDao.insertBlockForSection(activityId, fullNameSectionDto.getSectionId(), dobDisplayOrder - 4, lnBlockDef, newV2RevId);
        LOG.info("Added first name and last name questions.");

        //change displayOrder of fullname
        long fullnameNewRevId = jdbiRevision.copyAndTerminate(fullNameRevId, meta);
        jdbiFormSectionBlock.updateRevisionIdById(fullNameDto.getId(), fullnameNewRevId);
        helper.updateFormSectionBlockDisplayOrder(fullNameSectionDto.getId(), dobSectionDto.getDisplayOrder() + 5);

        //update template placeholder text
        String newTemplateText = "Your Signature (Full Name)*";
        long tmplVarId = helper.findTemplateVariableId(fullNameTextDto.getPlaceholderTemplateId());
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);
        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, newV2RevId, tmplVarId);

        //pdf version
        LOG.info("Adding new pdf version for consent");
        builder.insertPdfConfig(handle, dataCfg.getConfig("consentPdfV2"));
        addNewConsentDataSourceToReleasePdf(handle, studyDto.getId(), dataCfg.getString("releasePdfName"), activityCode, versionTag);
    }

    private void addNewConsentDataSourceToReleasePdf(Handle handle, long studyId, String pdfName, String activityCode, String versionTag) {
        PdfDao pdfDao = handle.attach(PdfDao.class);
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyId, pdfName)
                .orElseThrow(() -> new DDPException("Could not find pdf with name=" + pdfName));

        PdfVersion version = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(ver -> ver.getAcceptedActivityVersions().containsKey(activityCode))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find pdf version with data source for activityCode=" + activityCode));

        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
        long activityVersionId = jdbiActivityVersion.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag)
                .map(ActivityVersionDto::getId)
                .orElseThrow(() -> new DDPException(String.format(
                        "Could not find activity version id for activityCode=%s versionTag=%s", activityCode, versionTag)));

        pdfDao.insertDataSource(version.getId(), new PdfActivityDataSource(activityId, activityVersionId));

        LOG.info("Added activity data source with activityCode={} versionTag={} to pdf {} version {}",
                activityCode, versionTag, info.getConfigName(), version.getVersionTag());
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long findTemplateVariableId(@Bind("templateId") long templateId);

        @SqlUpdate("update form_section__block set display_order = :displayOrder where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockDisplayOrder(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("displayOrder") int displayOrder);

    }
}
