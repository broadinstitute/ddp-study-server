package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@Slf4j
public class OsteoSomaticAssentV3 implements CustomTask {

    private static final String DATA_FILE = "patches/somatic-assent-v3-updates-pepper-468.conf";
    private static final String STUDY = "cmi-osteo";
    private static final String VARIABLES_UPD = "variables-update";
    private static final String VARIABLES_UPD_QS = "question-variables-update";

    private static final Gson gson = GsonUtil.standardGson();

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private ActivityDao activityDao;
    private OsteoSomaticAssentV3.SqlHelper sqlHelper;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCode = dataCfg.getString("activityCode");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reason);
        this.sqlHelper = handle.attach(OsteoSomaticAssentV3.SqlHelper.class);
        this.activityDao = handle.attach(ActivityDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityVersionDto activityVer3 = getVersion3(handle, studyDto, metaConsent, activityCode);
        activityUpdate(handle, metaConsent, activityVer3);
    }

    private ActivityVersionDto getVersion3(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, versionTag, meta);
    }

    private void activityUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto version3) {
        updateTemplateVariables(meta, version3, dataCfg); //revision only variables in the Template
        updateQuestionTemplateVariables(meta, version3, dataCfg); //revision only variables in the Question Template
    }

    private void updateTemplateVariables(RevisionMetadata meta,
                                         ActivityVersionDto version3, Config dataCfg) {
        log.info("UPDATE Template variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_UPD);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            revisionVariableTranslation(templateVariable, meta, version3);
        }
    }

    private void updateQuestionTemplateVariables(RevisionMetadata meta,
                                                 ActivityVersionDto version2, Config dataCfg) {
        log.info("UPDATE QUESTION Template variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_UPD_QS);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            //code specific to handle somatic-assent-addendum question prompt variable
            Long variableId = sqlHelper.findVariableIdByQuestion(templateVariable.getName(), "SOMATIC_ASSENT_ADDENDUM",
                    "CONSENT_ADDENDUM_PEDIATRIC", STUDY);
            revisionVariable(templateVariable, meta, version2, variableId);
        }
    }

    private void revisionVariableTranslation(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version3) {
        log.info("revisioning and updating template variable: {}", templateVariable.getName());
        Long templateVariableId = sqlHelper.findVariableIdByNameAndActivityId(templateVariable.getName(), version3.getActivityId());
        log.info("Tmpl variableId  {} ", templateVariableId);
        revisionVariable(templateVariable, meta, version3, templateVariableId);
    }

    private void revisionVariable(TemplateVariable templateVariable, RevisionMetadata meta,
                                  ActivityVersionDto version3, Long templateVariableId) {
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(templateVariableId);
        //all translations have same revision.. hence using first.. lms has only en any way.
        long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
        long[] revIds = new long[transList.size()];
        Arrays.fill(revIds, revId);
        List<Long> substitutionIds = new ArrayList<>();
        log.info("subs/translations count: {} for varId : {} ", transList.size(), templateVariableId);
        for (var currTranslation : transList) {
            String language = currTranslation.getLanguageCode();
            String currentText = currTranslation.getText();

            String newTxt = templateVariable.getTranslation(language).get().getText();
            log.info("terminated: language: {} revId: {} new Text: {} currText: {}", language, currTranslation.getRevisionId(),
                    newTxt, currentText);

            substitutionIds.add(currTranslation.getId().get());
            jdbiVarSubst.insert(currTranslation.getLanguageCode(), templateVariable.getTranslation(language).get().getText(),
                    version3.getRevId(), templateVariableId);
        }
        int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
        if (ids.length != revIds.length) {
            throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
        }
        log.info("revisioned and updated template variable: {}", templateVariableId);
    }

    private interface SqlHelper extends SqlObject {


        /* Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */
        @SqlQuery("select tv.template_variable_id from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + "  join template_variable tv on tv.template_id = tmpl.template_id "
                + " where tv.variable_name = :variableName"
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
        Long findVariableIdByNameAndActivityId(@Bind("variableName") String variableName, @Bind("activityId") Long activityId);

        @SqlQuery(" select tv.template_variable_id from template as tmpl join question as q "
                + " on tmpl.template_id = q.question_prompt_template_id   "
                + " join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id  "
                + " join study_activity sa on sa.study_activity_id = q.study_activity_id  "
                + " join umbrella_study s on s.umbrella_study_id = sa.study_id  "
                + " join template_variable tv on tv.template_id = tmpl.template_id   "
                + " where tv.variable_name = :variableName  "
                + " and qsc.stable_id = :questionStableId  "
                + " and sa.study_activity_code = :activityCode  "
                + " and s.guid = :studyGuid")
        Long findVariableIdByQuestion(@Bind("variableName") String variableName, @Bind("questionStableId") String questionStableId,
                                      @Bind("activityCode") String activityCode, @Bind("studyGuid") String studyGuid);

    }
}
