package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class RarexVersion3 implements CustomTask {
    private static final String STUDY_GUID = "rarex";
    private static final String DATA_FILE = "patches/patch_0903.conf";

    private Config studyCfg;
    private Config dataCfg;

    private SqlHelper helper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        LanguageStore.init(handle);
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        long studyId = studyDto.getId();
        helper = handle.attach(SqlHelper.class);

        long consent = ActivityBuilder.findActivityId(handle, studyId, "CONSENT");
        long consentParental = ActivityBuilder.findActivityId(handle, studyId, "PARENTAL_CONSENT");
        long consentAssent = ActivityBuilder.findActivityId(handle, studyId, "CONSENT_ASSENT");
        long consentLar = ActivityBuilder.findActivityId(handle, studyId, "LAR_CONSENT");
        long consentAssentLar = ActivityBuilder.findActivityId(handle, studyId, "LAR_CONSENT_ASSENT");
        long dataSharing = ActivityBuilder.findActivityId(handle, studyId, "DATA_SHARING");

        log.info("DDP-6773");

        updateVariableByText(consent,
                "Who can I choose to share my <i>de-identified</i> data with, and what will they do with it?",
                "Who can I choose to share my <i>de-Identified</i> data with, and what will they do with it?");
        updateVariableByText(consentParental,
                "Who can I choose to share my child’s <i>de-identified</i> data with, and what will they do with it?",
                "Who can I choose to share my child’s <i>de-Identified</i> data with, and what will they do with it?");
        updateVariableByText(consentAssent,
                "Who can I choose to share my child’s <i>de-identified</i> data with, and what will they do with it?",
                "Who can I choose to share my child’s <i>de-Identified</i> data with, and what will they do with it?");
        updateVariableByText(consentLar,
                "Who can I choose to share <i>de-identified</i> data with, and what will they do with it?",
                "Who can I choose to share <i>de-Identified</i> data with, and what will they do with it?");
        updateVariableByText(consentAssentLar,
                "Who can I choose to share <i>de-identified</i> data with, and what will they do with it?",
                "Who can I choose to share <i>de-Identified</i> data with, and what will they do with it?");

        String advarraBefore = "\n" 
                + "              Advarra IRB reviewed this study. Advarra is a group of people who review research studies to\n" 
                + "              protect research participants' rights and welfare. You can ask Advarra general questions\n" 
                + "              about what it means to be in a research program. Review by Advarra does not mean that the\n" 
                + "              DCP is without risks.\n" 
                + "            ";
        String advarraAfter = "\n" 
                + "              Advarra IRB reviewed this study. Advarra is a group of people who review research studies to\n" 
                + "              protect research participants' rights and welfare. You can ask Advarra general questions\n" 
                + "              about what it means to be in a research program. A review by Advarra does not mean that the\n" 
                + "              DCP is without risks.\n" 
                + "            ";
        updateVariableByText(consent, advarraBefore, advarraAfter);
        updateVariableByText(consentParental, advarraBefore, advarraAfter);
        updateVariableByText(consentAssent, advarraBefore, advarraAfter);
        updateVariableByText(consentLar, advarraBefore, advarraAfter);
        updateVariableByText(consentAssentLar, advarraBefore, advarraAfter);

        updateVariableByText(consent, "\n"
                + "              <p>Yes, you can stop taking part in the DCP at any time for any reason.</p>\n"
                + "              <p>If you decide to quit, you will be offered some choices about whether RARE-X keeps or "
                + "deletes your data.</p>\n"
                + "              <p>To stop participating, you must do so in writing by contacting the Principal Investigator "
                + "listed on the\n"
                + "              top of this form.</p>\n"
                + "            ", "\n"
                + "              <p>Yes, you can stop taking part in the DCP at any time for any reason.</p>\n"
                + "              <p>If you decide to quit, you will be offered some choices about whether RARE-X keeps "
                + "or deletes your data.</p>\n"
                + "              <p>To stop participating, you must do so by contacting the Principal Investigator listed on the\n"
                + "              top of this form.</p>\n"
                + "            ");

        updateVariableByText(dataSharing, "Your Choices for Sharing Your DeIdentified Data for Research",
                "Your Choices for Sharing Your De-identified Data for Research");
        updateVariableByText(dataSharing, "Your Choices for Sharing Patient DeIdentified Data for Research",
                "Your Choices for Sharing Patient De-identified Data for Research");

        updateExpressionTextByQuestionStableCode(consentAssentLar, "LAR_CONSENT_ASSENT_RELATIONSHIP",
                "true");
        updateExpressionTextByQuestionStableCode(consentLar, "LAR_CONSENT_RELATIONSHIP",
                "true");
        updateExpressionTextByQuestionStableCode(consentAssent, "CONSENT_ASSENT_RELATIONSHIP",
                "true");
        updateExpressionTextByQuestionStableCode(consentParental, "PARENTAL_CONSENT_RELATIONSHIP",
                "true");

        String quitBefore = "\n" 
                + "              <p>Yes, you can tell us that you no longer give permission for you/your child to participate in the\n" 
                + "              DCP at any time for any reason.<p>\n" 
                + "              <p>If you or your child decide to quit the DCP, you will be offered some choices about whether\n" 
                + "              RARE-X keeps or deletes your child’s data.</p>\n" 
                + "              <p>To stop participating, you must do so by contacting the Principal Investigator listed on the top "
                + "of this form.</p>\n"
                + "            ";
        String quitAfter = "\n" 
                + "              <p>Yes, you tell us that you no longer give permission for you/your child to participate in the\n" 
                + "              DCP at any time for any reason.<p>\n" 
                + "              <p>If you or your child decide to quit the DCP, you will be offered some choices about whether\n" 
                + "              RARE-X keeps or deletes your child’s data.</p>\n" 
                + "              <p>To stop participating, you must do so by contacting the Principal Investigator listed on the top "
                + "of this form.</p>\n"
                + "            ";
        updateVariableByText(consentParental, quitBefore, quitAfter);
        updateVariableByText(consentAssent, quitBefore, quitAfter);

        String eligibleBefore = "\n" 
                + "              <p>Participants and families who may take part include:</p>\n" 
                + "              <ul><li>Any person who has been diagnosed with a rare disease, or who is looking for a diagnosis.</li>\n" 
                + "              <li>A parent or legal guardian of a child with a rare disease may register a child who is a minor (a "
                + "“minor” is a child under the age of 18, in most states).</li>\n"
                + "              <li>The legally authorized representative of an adult with a rare disease who cannot physically or "
                + "mentally answer the surveys may enroll the affected participant.</li></ul>\n"
                + "            ";
        String eligibleAfter = "\n" 
                + "              <p>Participants and families who may take part include:</p>\n" 
                + "              <ul><li>Any person who has been diagnosed with a rare disease, or who is looking for a diagnosis.</li>\n" 
                + "              <li>A parent or legal guardian of a child with a rare disease may register a child who is a minor"
                + " (a “minor” is a child under the age of 18, in most states).</li>\n"
                + "              <li>The legally authorized representative of an adult with a rare disease who cannot physically "
                + "or mentally answer the surveys, may enroll the affected participant.</li></ul>\n"
                + "            ";
        updateVariableByText(consent, eligibleBefore, eligibleAfter);
        updateVariableByText(consentParental, eligibleBefore, eligibleAfter);
        updateVariableByText(consentAssent, eligibleBefore, eligibleAfter);
        updateVariableByText(consentLar, eligibleBefore, eligibleAfter);
        updateVariableByText(consentAssentLar, eligibleBefore, eligibleAfter);

        Config option = dataCfg.getConfig("parentOfAMinor");
        Set<String> stableIds = new HashSet<>(Set.of(
                "LAR_CONSENT_ASSENT_RELATIONSHIP",
                "LAR_CONSENT_RELATIONSHIP",
                "PARENTAL_CONSENT_RELATIONSHIP",
                "CONSENT_ASSENT_RELATIONSHIP"
        ));
        addPicklistOption(handle, studyId, stableIds, option, 7);
    }

    private void addPicklistOption(Handle handle, long studyId, Set<String> questionStableIds, Config option, int displayOrder) {
        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Stream<QuestionDto> questionDtos = jdbiQuestion.findLatestDtosByStudyIdAndQuestionStableIds(studyId, questionStableIds);
        questionDtos.forEach(questionDto -> {
            PicklistOptionDef pickListOptionDef = GsonUtil.standardGson().fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class);
            plQuestionDao.insertOption(questionDto.getId(), pickListOptionDef, displayOrder, questionDto.getRevisionId());
            log.info("Added new picklistOption " + pickListOptionDef.getStableId() + " with id "
                    + pickListOptionDef.getOptionId() + " into question " + questionDto.getStableId());
        });
    }

    private void updateExpressionTextByQuestionStableCode(long activityId, String questionStableCode, String expr) {
        List<Long> ids = helper.findBlockExpressionIdsByQuestionStableCode(activityId, questionStableCode);
        helper.updateExpressionText(expr, ids);
    }

    private void updateVariableByText(long activityId, String before, String after) {
        List<Long> varIds = helper.findVariableIdsByText(activityId, before);
        if (CollectionUtils.isEmpty(varIds)) {
            throw new DDPException("Could not find any variable with text " + before);
        }
        varIds.forEach(varId -> {
            helper.updateVarValueByTemplateVarId(varId, after);
            log.info("Template variable {} text was updated from \"{}\" to \"{}\"", varId, before, after);
        });
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join block_content as bt on tmpl.template_id = bt.body_template_id or tmpl.template_id = bt.title_template_id"
                + " where ts.substitution_value = :text"
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

        @SqlQuery("select e.expression_id from block__question as bt "
                + "left join block__expression be on be.block_id = bt.block_id "
                + "join expression e on e.expression_id = be.expression_id "
                + "join question q on q.question_id = bt.question_id "
                + "join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id "
                + "  where qsc.stable_id = :stableId and "
                + "  bt.block_id in "
                + "  (select fsb.block_id "
                + "  from form_activity__form_section as fafs "
                + "  join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id "
                + "  where fafs.form_activity_id = :activityId)")
        List<Long> findBlockExpressionIdsByQuestionStableCode(@Bind("activityId") long activityId,
                                                              @Bind("stableId") String stableId);

        // For single language only
        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);

        @SqlUpdate("update expression set expression_text = :text where expression_id in (<ids>)")
        int updateExpressionText(@Bind("text") String text,
                                 @BindList(value = "ids", onEmpty = BindList.EmptyHandling.THROW) List<Long> ids);

    }
}
