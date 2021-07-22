package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestionValidation;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.*;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class RarexVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(RarexVersion2.class);
    private static final String RAREX = "rarex";

    private Config studyCfg;
    private Config varsCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        if (!studyCfg.getString("study.guid").equals(RAREX)) {
            throw new DDPException("This task is only for the " + RAREX + " study!");
        }

        this.studyCfg = studyCfg;
        timestamp = Instant.now();
        this.varsCfg = varsCfg;
        gson = GsonUtil.standardGson();

    }

    @Override
    public void run(Handle handle) {

        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();
        SqlHelper helper = handle.attach(SqlHelper.class);

        //first update styling (applies to both versions: v1 and v2)
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);



        long generalInformationId = ActivityBuilder.findActivityId(handle, studyId, "GENERAL_INFORMATION");
        long healthAndDevelopmentId = ActivityBuilder.findActivityId(handle, studyId, "HEALTH_AND_DEVELOPMENT");
        long prequalId = ActivityBuilder.findActivityId(handle, studyId, "PREQUAL");

        // REFID 27
        replaceContentBlockVariableText(helper, generalInformationId,
                "general_information_health_insurance_coverage_exp",
                "This is just to look at insurance coverage in rare disease in general, NOT to track insurance details.");

        // REFID 71
        replaceContentBlockVariableText(helper, healthAndDevelopmentId,
                "h_d_teeth_issues_exp",
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function. You may have seen a dentist for these issues.");
        replaceContentBlockVariableText(helper, healthAndDevelopmentId,
                "h_d_patient_teeth_issues_exp",
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function. The patient may have seen a dentist for these issues.");

        // REFID 83
        replaceQuestionBlockVariableText(helper, prequalId, "SELF_COUNTRY",
                "COUNTRY_picklist_label",
                "Choose Country or Territory...");

        // RFID 98
        List<Long> varIds = helper.findVariableIdsByText(healthAndDevelopmentId, "Have you ever been diagnosed with cancer, colon polyps or a non-cancerous tumor?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId, "Have you ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId, "Has the patient ever been diagnosed with cancer, colon polyps or a non-cancerous tumor? ");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId, "Has the patient ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId, "Have you had issues with your teeth or mouth?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId, "Have you had issues with your TEETH or MOUTH?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId, "Has the patient had issues with their teeth or mouth?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId, "Has the patient had issues with their TEETH or MOUTH?"));

        // RFID 105
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId, "For the conditions you listed above do you have reports or summaries to upload as support?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId, "Do you have genetic reports or summaries to upload?"));


        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDto dobDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CONSENT_DOB").get();


        //disable AgeTooYoung Dob Validation
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        JdbiQuestionValidation validation = handle.attach(JdbiQuestionValidation.class);
        List<RuleDto> consentDobValidations = validation.getAllActiveValidations(dobDto.getId());
        RuleDto ageRangeValidation = consentDobValidations.stream().filter(
                validationDto -> validationDto.getRuleType().equals(RuleType.AGE_RANGE)).findFirst().get();
        LOG.info("Disabled age range validation for consent dob QID: {} validationID: {} old ver: {} rule: {}",
                dobDto.getId(), ageRangeValidation.getId(), ageRangeValidation.getRevisionId(), ageRangeValidation.getRuleType());

        UpdateTemplatesInPlace updateTemplatesTask = new UpdateTemplatesInPlace();
        LOG.info("updated variables for styling changes");

        //update brain_consent_s3_election_agree listHintStyle to NONE
        long tmplId = helper.findTemplateIdByTemplateText("$brain_consent_s3_election_agree");
        helper.updateGroupHeaderStyleHintByTemplateId(tmplId, ListStyleHint.NONE.name());





        //consent ver: 2 stuff
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);


        QuestionDto fullNameDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CONSENT_FULLNAME").get();
        TextQuestionDto fullNameTextDto = (TextQuestionDto) handle.attach(JdbiQuestion.class)
                .findQuestionDtoById(fullNameDto.getId()).get();
        long fullNameBlockId = helper.findQuestionBlockId(fullNameDto.getId());
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        SectionBlockMembershipDto fullNameSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(fullNameBlockId).get();

        long dobBlockId = helper.findQuestionBlockId(dobDto.getId());
        SectionBlockMembershipDto dobSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(dobBlockId).get();
        long newV2RevId = jdbiRevision.insertStart(timestamp.toEpochMilli(), adminUser.getId(), "consent version#2 change");
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        int dobDisplayOrder = dobSectionDto.getDisplayOrder();

        /*TextQuestionDef firstNameDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("firstNameQuestion")), TextQuestionDef.class);
        QuestionBlockDef blockDef = new QuestionBlockDef(firstNameDef);
        sectionBlockDao.insertBlockForSection(activityId, fullNameSectionDto.getSectionId(), dobDisplayOrder - 5, blockDef, newV2RevId);*/
        LOG.info("Added first name and last name questions.");


        //update template placeholder text
        /*String newTemplateText = "Your Signature (Full Name)*";
        long tmplVarId = helper.findTemplateVariableId(fullNameTextDto.getPlaceholderTemplateId());
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);
        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, newV2RevId, tmplVarId);*/

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

    private void replaceContentBlockVariableText(SqlHelper helper, long activityId, String variableName, String text) {
        List<Long> variableIds = helper.findContentBlockVariableIdsByVarName(activityId, variableName);
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
        }
    }

    private void replaceQuestionBlockVariableText(SqlHelper helper, long activityId, String stableId, String variableName, String text) {
        List<Long> variableIds = helper.findQuestionBlockVariableIdsByVarNameAndStableId(activityId, variableName, stableId);
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long findTemplateVariableId(@Bind("templateId") long templateId);

        //WATCH OUT: might cause issue if same variable name/text across multiple studies.. ok for patch though.
        //used max to handle multiple study versions in lower regions
        @SqlQuery("select max(template_id) from template where template_text = :text")
        long findTemplateIdByTemplateText(@Bind("text") String text);

        @SqlUpdate("update block_group_header set list_style_hint_id = "
                + " (select list_style_hint_id from list_style_hint where list_style_hint_code = :listStyle)"
                + " where title_template_id = :templateId")
        int updateGroupHeaderStyleHintByTemplateId(@Bind("templateId") long templateId, @Bind("listStyle") String listStyle);

        @SqlUpdate("update form_section__block set display_order = :displayOrder where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockDisplayOrder(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("displayOrder") int displayOrder);

        @SqlUpdate("update i18n_activity_detail set subtitle = :text where study_activity_id = :studyActivityId")
        int update18nActivitySubtitle(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlQuery("select fsi.form_section_icon_id from form_section_icon fsi, form_activity__form_section fafs "
                + " where fafs.form_section_id = fsi.form_section_id "
                + " and fafs.form_activity_id = :studyActivityId")
        List<Long> findSectionIconIdByActivity(@Bind("studyActivityId") long studyActivityId);

        @SqlUpdate("delete from form_section_icon_source where form_section_icon_id in (<ids>)")
        int _deleteActivityIconSources(@BindList("ids") Set<Long> ids);

        @SqlUpdate("delete from form_section_icon where form_section_icon_id in (<ids>)")
        int _deleteActivityIcons(@BindList("ids") Set<Long> ids);

        default void deleteActivityIcons(Set<Long> ids) {
            int numUpdated = _deleteActivityIconSources(ids);
            if (numUpdated != 6) {
                throw new DDPException("Expected to delete 6 rows from icon sources ="
                        + " but deleted " + numUpdated);
            }

            numUpdated = _deleteActivityIcons(ids);
            if (numUpdated != 6) {
                throw new DDPException("Expected to delete 6 rows from form section icons ="
                        + " but deleted " + numUpdated);
            }
        }

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join block_content as bt on tmpl.template_id = bt.body_template_id"
                + " where tv.variable_name = :varName"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        List<Long> findContentBlockVariableIdsByVarName(@Bind("activityId") long activityId, @Bind("varName") String variableName);

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join picklist_question pk on tmpl.template_id = pk.picklist_label_template_id"
                + " join block__question bt on bt.question_id = pk.question_id"
                + " join question q on q.question_id = bt.question_id"
                + " join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + " where tv.variable_name = :varName"
                + "   and qsc.stable_id = :stableId"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        List<Long> findQuestionBlockVariableIdsByVarNameAndStableId(@Bind("activityId") long activityId,
                                                                    @Bind("varName") String variableName,
                                                                    @Bind("stableId") String stableId);

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join block_content as bt on tmpl.template_id = bt.body_template_id"
                + " where ts.substitution_value= = :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        List<Long> findVariableIdsByText(@Bind("activityId") long activityId,
                                         @Bind("text") String text);

        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);


    }
}
