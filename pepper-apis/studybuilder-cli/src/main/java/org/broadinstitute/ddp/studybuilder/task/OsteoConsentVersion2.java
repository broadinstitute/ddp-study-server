package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;


public class OsteoConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoConsentVersion2.class);
    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String DATA_FILE_SOMATIC_CONSENT_ADDENDUM = "patches/somatic-consent-addendum-val.conf";
    private static final String DATA_FILE_SOMATIC_ASSENT_ADDENDUM = "patches/parent-consent-assent.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private Config dataCfg;
    private Config somaticAddendumConsentCfg;
    private Config assentAddendumCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;

        File fileSomaticAddendum = cfgPath.getParent().resolve(DATA_FILE_SOMATIC_CONSENT_ADDENDUM).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + fileSomaticAddendum);
        }
        somaticAddendumConsentCfg = ConfigFactory.parseFile(fileSomaticAddendum);
        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        File fileAssentAddendum = cfgPath.getParent().resolve(DATA_FILE_SOMATIC_ASSENT_ADDENDUM).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + fileAssentAddendum);
        }
        assentAddendumCfg = ConfigFactory.parseFile(fileAssentAddendum);

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCodeConsentAssent = "CONSENT_ASSENT";
        String activityCodeConsent = "CONSENT";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));

        LOG.info("Changing version of {} to {} with timestamp={}", activityCodeConsent, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsentAssent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsentAssent, versionTag);
        RevisionMetadata metaConsentAssent = new RevisionMetadata(ts, adminUser.getId(), reasonConsentAssent);

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsentAssent, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);

        ActivityVersionDto version2ForConsentAssent = getVersion2(handle, studyDto, metaConsentAssent, activityCodeConsentAssent);
        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCodeConsent);

        updateVariables(handle, metaConsentAssent, version2ForConsentAssent);
        runSomaticConsentAddendum(handle, adminUser, studyDto, version2ForConsent, version2ForConsentAssent);
        runSomaticAssentAddendum(handle, adminUser, studyDto, version2ForConsentAssent);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, "v2", meta);
        return version2;
    }

    private void updateVariables(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList("translation-updates");
        System.out.println(configList);
        for (Config config : configList) {
            revisionVariableTranslation(config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW), handle, meta, version2);
        }
    }

    private static final String TRANSLATION_KEY = "varName";
    private static final String TRANSLATION_NEW = "newValue";

    private void revisionVariableTranslation(String varName, String newTemplateText, Handle handle,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        long tmplVarId = handle.attach(SqlHelper.class).findTemplateVariableIdByVariableName(varName);
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newVarSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newVarSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
    }

    public void runSomaticConsentAddendum(Handle handle, User adminUser, StudyDto studyDto,
                                          ActivityVersionDto version2Consent, ActivityVersionDto version2ConsentAssent) {
        LanguageStore.init(handle);

        String filePath = somaticAddendumConsentCfg.getConfigList("updates").get(0).getString("activityFilePath");
        Config consentAssent = activityBuild(studyDto, adminUser, filePath);

        String sectionfilepath = somaticAddendumConsentCfg.getConfigList("updates").get(0).getString("sectionFilePath");
        Config consentAddendumPediatric = activityBuild(studyDto, adminUser, sectionfilepath);

        insertSection(studyDto, handle, consentAddendumPediatric, consentAssent, version2ConsentAssent);

        String filePath1 = somaticAddendumConsentCfg.getConfigList("updates").get(1).getString("activityFilePath");
        Config consentSelf = activityBuild(studyDto, adminUser, filePath1);

        String sectionfilepath1 = somaticAddendumConsentCfg.getConfigList("updates").get(1).getString("sectionFilePath");
        Config consentAddendumSelf = activityBuild(studyDto, adminUser, sectionfilepath1);

        insertSection(studyDto, handle, consentAddendumSelf, consentSelf, version2Consent);

    }


    public void runSomaticAssentAddendum(Handle handle, User adminUser,
                                         StudyDto studyDto, ActivityVersionDto version2ConsentAssent) {
        LanguageStore.init(handle);

        String consentAssent = assentAddendumCfg.getString("activityFilepath");
        Config consentAssentCfg = activityBuild(studyDto, adminUser, consentAssent);

        String assentAddendum = assentAddendumCfg.getString("sectionFilePath");
        Config assentAddendumCfg = activityBuild(studyDto, adminUser, assentAddendum);

        versionTag = consentAssentCfg.getString("versionTag");
        String activityCode = consentAssentCfg.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        long revisionId = version2ConsentAssent.getRevId();

        var sectionDef = gson.fromJson(ConfigUtil.toJson(assentAddendumCfg), FormSectionDef.class);

        var sectionId = handle.attach(SectionBlockDao.class)
                .insertSection(activityId, sectionDef, revisionId);

        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);

        jdbiActSection.insert(activityId, sectionId, revisionId, 60);
    }

    private void insertSection(StudyDto studyDto, Handle handle,
                               Config section, Config activity, ActivityVersionDto version2) {

        String activityCode = activity.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        long revisionId = version2.getRevId();

        var sectionDef = gson.fromJson(ConfigUtil.toJson(section), FormSectionDef.class);

        var sectionId = handle.attach(SectionBlockDao.class)
                .insertSection(activityId, sectionDef, revisionId);

        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);

        jdbiActSection.insert(activityId, sectionId, revisionId, 50);
    }

    private Config activityBuild(StudyDto studyDto, User adminUser, String activityCodeConf) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getId());
        Config config = activityBuilder.readDefinitionConfig(activityCodeConf);
        return config;
    }


    private interface SqlHelper extends SqlObject {
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);
    }
}

