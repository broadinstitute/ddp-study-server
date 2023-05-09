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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
public class SimpleTextRevisionTask implements CustomTask {
    private static final String VARIABLES_UPD = "variables-update";

    private static final String STUDY_UPDATES = "activity-updates";
    private static final String VARIABLES_UPD_QS = "question-variables-update";

    private static final Gson gson = GsonUtil.standardGson();

    private String dataFile;

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private Config cfg;
    private Config studyCfg;

    private ActivityDao activityDao;
    private SimpleTextRevisionTask.SqlHelper sqlHelper;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    private StudyDto studyDto;

    private User adminUser;

    @Override
    public void consumeArguments(String[] args) throws ParseException {
        log.info(String.join(", ", args));
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(new Options(), args);
        String[] positional = cmd.getArgs();
        if (positional.length < 1) {
            throw new ParseException("Patch File is required to run.");
        }
        log.info(positional[0]);
        this.dataFile = positional[0];

        File file = cfgPath.getParent().resolve(this.dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        cfg = studyCfg;
        timestamp = Instant.now();
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        this.adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));

        List<? extends Config> activityUpdateConfigs = dataCfg.getConfigList(STUDY_UPDATES);

        this.sqlHelper = handle.attach(SimpleTextRevisionTask.SqlHelper.class);
        this.activityDao = handle.attach(ActivityDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        activitiesUpdate(handle, activityUpdateConfigs);
    }

    private ActivityVersionDto getVersion(Handle handle, RevisionMetadata meta, String activityCode, String versionTag) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, versionTag, meta);
    }

    private void activitiesUpdate(Handle handle, List<? extends Config> activityConfigList) {
        for (Config activityConfig: activityConfigList) {
            String activityCode = activityConfig.getString("activityCode");
            String versionTag = activityConfig.getString("versionTag");

            log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
            long ts = this.timestamp.toEpochMilli();

            String reason = String.format(
                    "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                    studyDto.getGuid(), activityCode, versionTag);
            RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reason);

            ActivityVersionDto activityVersion = getVersion(handle, metaConsent, activityCode, versionTag);
            activityUpdate(metaConsent, activityVersion);
        }
    }

    private void activityUpdate(RevisionMetadata meta, ActivityVersionDto version) {
        updateTemplateVariables(meta, version, dataCfg); //revision only variables in the Template
        updateQuestionTemplateVariables(meta, version, dataCfg); //revision only variables in the Question Template
    }

    private void updateTemplateVariables(RevisionMetadata meta,
                                         ActivityVersionDto version, Config dataCfg) {
        log.info("UPDATE Template variables");
        List<? extends Config> configList = null;

        try {
            configList = dataCfg.getConfigList(VARIABLES_UPD);
        } catch (Exception e) {
            return;
        }

        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            revisionVariableTranslation(templateVariable, meta, version);
        }
    }

    private void updateQuestionTemplateVariables(RevisionMetadata meta,
                                                 ActivityVersionDto version, Config dataCfg) {
        log.info("UPDATE QUESTION Template variables");
        List<? extends Config> configList = null;

        try {
            configList = dataCfg.getConfigList(VARIABLES_UPD_QS);
        } catch (Exception e) {
            return;
        }

        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);

            Long variableId = sqlHelper.findVariableIdByQuestion(templateVariable.getName(), config.getString("questionStableId"),
                    dataCfg.getString("activityCode"), studyDto.getGuid());
            revisionVariable(templateVariable, meta, version, variableId);
        }

        log.info("Updated Question Template Variables");
    }

    private void revisionVariableTranslation(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version) {
        log.info("revisioning and updating template variable: {}", templateVariable.getName());
        Long templateVariableId = sqlHelper.findVariableIdByNameAndActivityId(templateVariable.getName(), version.getActivityId());
        log.info("Tmpl variableId  {} ", templateVariableId);
        revisionVariable(templateVariable, meta, version, templateVariableId);
    }

    private void revisionVariable(TemplateVariable templateVariable, RevisionMetadata meta,
                                  ActivityVersionDto version, Long templateVariableId) {
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(templateVariableId);

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
                    version.getRevId(), templateVariableId);
        }
        int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
        if (ids.length != revIds.length) {
            throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
        }
        log.info("revision and updated template variable: {}", templateVariableId);
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
