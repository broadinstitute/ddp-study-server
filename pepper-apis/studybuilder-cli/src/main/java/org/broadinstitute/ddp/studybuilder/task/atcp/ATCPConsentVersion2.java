package org.broadinstitute.ddp.studybuilder.task.atcp;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTemplateVariable;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class ATCPConsentVersion2 implements CustomTask {
    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String ATCP_STUDY = "atcp";
    private static final String VARIABLES_ADD = "variables-add";
    private static final String VARIABLES_UPD = "variables-update";
    private static final String VARIABLES_DEL = "variables-delete";
    private static final String ADULT_TRANSLATION_KEY = "name";
    private static final String NEW_TEMPL_VAR_LOOKUP = "consent_participation_detail_p4";
    private static final Gson gson = GsonUtil.standardGson();

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private ActivityDao activityDao;
    private SqlHelper sqlHelper;
    private SectionBlockDao sectionBlockDao;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(ATCP_STUDY)) {
            throw new DDPException("This task is only for the " + ATCP_STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCodeConsent = "CONSENT";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        log.info("Changing version of {} to {} with timestamp={}", activityCodeConsent, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsent, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCodeConsent);
        runConsentUpdate(handle, metaConsent, version2ForConsent);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, "v2", meta);
    }

    private void runConsentUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        terminateTemplateVariables(handle, meta, version2, dataCfg);
        addTemplateVariables(handle, meta, version2, dataCfg);
        updateTemplateVariables(handle, meta, version2, dataCfg);

    }

    private void addTemplateVariables(Handle handle, RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_ADD);
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(NEW_TEMPL_VAR_LOOKUP);
        //get first ID
        Long templateId = sqlHelper.findTemplateIdByVariableId(templateVariableIdByVariableNames.get(0));

        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            addVariable(handle, null, templateId, version2.getRevId(), templateVariable);
        }
    }

    private void updateTemplateVariables(Handle handle, RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_UPD);
        for (Config config : configList) {
            //String varName = config.getString(ADULT_TRANSLATION_KEY);
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            //List<Translation> translations = templateVariable.getTranslations();
            revisionVariableTranslation(templateVariable, meta, version2);
        }
    }

    private void terminateTemplateVariables(Handle handle, RevisionMetadata meta,
                                      ActivityVersionDto version2, Config dataCfg) {

        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_DEL);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            terminateTemplateVariable(templateVariable, meta, version2);
        }
    }

    private void revisionVariableTranslation(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and updating template variable: {}", templateVariable.getName());
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(templateVariable.getName());
        //todo if 0/variable not found.. insert new one 3/24
        log.info("Tmpl variableId count: {} ", templateVariableIdByVariableNames.size());
        for (Long tmplVarId : templateVariableIdByVariableNames) {
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            //all translations has same revision.. hence using first
            long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
            long[] revIds = new long[transList.size()];
            Arrays.fill(revIds, revId);
            List<Long> substitutionIds = new ArrayList<>();
            log.info("subs/translations count: {} for varId : {} ", transList.size(), tmplVarId);
            for (var currTranslation : transList) {
                String language = currTranslation.getLanguageCode();
                String currentText = currTranslation.getText();

                //todo .. iterate and update all languages
                String newTxt = templateVariable.getTranslation(language).get().getText();
                log.info(" language: {} revId: {} new Text: {} currText: {}", language, currTranslation.getRevisionId(),
                        newTxt, currentText);

                substitutionIds.add(currTranslation.getId().get());
                //jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
                jdbiVarSubst.insert(currTranslation.getLanguageCode(), templateVariable.getTranslation(language).get().getText(),
                        version2.getRevId(), tmplVarId);
            }
            int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
            if (ids.length != revIds.length) {
                throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
            }
            log.info("ids: {}", ids);
            log.info("revisioned and updated template variable: {}", tmplVarId);
        }
    }

    private void terminateTemplateVariable(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and terminating template variable: {}", templateVariable.getName());
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(templateVariable.getName());
        //todo if 0/variable not found.. insert new one 3/24
        log.info("Tmpl variableId count: {} ", templateVariableIdByVariableNames.size());
        for (Long tmplVarId : templateVariableIdByVariableNames) {
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            //all translations has same revision.. hence using first
            long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
            long[] revIds = new long[transList.size()];
            Arrays.fill(revIds, revId);
            List<Long> substitutionIds = new ArrayList<>();
            log.info("subs/translations count: {} for varId : {} ", transList.size(), tmplVarId);
            for (var currTranslation : transList) {
                //String language = currTranslation.getLanguageCode();
                //String currentText = currTranslation.getText();

                //todo .. iterate and update all languages
                //String newTxt = templateVariable.getTranslation(language).get().getText();
                //log.info(" language: {} revId: {} new Text: {} currText: {}", language, currTranslation.getRevisionId(),
                //        newTxt, currentText);

                substitutionIds.add(currTranslation.getId().get());
                //jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
                //jdbiVarSubst.insert(currTranslation.getLanguageCode(), templateVariable.getTranslation(language).get().getText(),
                //        version2.getRevId(), tmplVarId);
            }
            int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
            if (ids.length != revIds.length) {
                throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
            }
            log.info("ids: {}", ids);
            log.info("revisioned and terminated template variable: {}", tmplVarId);
        }
    }

    private void deleteVariable(Handle handle, String tag, TemplateVariable variable) {
        //just revision and terminate current revision

        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = variable.getName();
        long variableId = variable.getId().get();

        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            boolean deleted = jdbiVariableSubstitution.delete(translation.getId().get());
            if (deleted) {
                log.info("[{}] variable {} language {}: deleted substitution", tag, variableName, language);
            } else {
                throw new DDPException(String.format(
                        "Could not delete substitution for %s variable %s language %s",
                        tag, variableName, language));
            }
        }

        boolean deleted = jdbiTemplateVariable.delete(variableId);
        if (deleted) {
            log.info("[{}] deleted variable {}", tag, variableName);
        } else {
            throw new DDPException(String.format("Could not delete %s variable %s", tag, variableName));
        }
    }

    private void addVariable(Handle handle, String tag, long templateId, long revisionId, TemplateVariable variable) {
        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = variable.getName();

        if (I18nTemplateConstants.DDP.equals(variableName) || I18nTemplateConstants.LAST_UPDATED.equals(variableName)) {
            throw new DaoException("Variable name '" + variableName + "' is not allowed");
        }

        long variableId = jdbiTemplateVariable.insertVariable(templateId, variableName);
        log.info("[{}] inserted variable {}", tag, variableName);

        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            jdbiVariableSubstitution.insert(language, translation.getText(), revisionId, variableId);
            log.info("[{}] variable {} language {}: inserted substitution",
                    tag, variableName, language);
        }
        log.info("inserted template variable for all translations");
    }

    private interface SqlHelper extends SqlObject {

        //todo filter by studyId so that we query only latest study variables
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variableName "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variableName") String variableName);

        @SqlQuery("select template_id from template_variable where template_variable_id = :variableId "
                + "order by template_id desc")
        Long findTemplateIdByVariableId(@Bind("variableId") long variableId);

    }

}

