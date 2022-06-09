package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

/**
 * Task to make additional edits as part of the "Brain Tumor Project" rename.
 *
 * <p>This should be ran right after the BrainRename task. This assumes that activities will have a new version from
 * the BrainRename task, so it will make edits using that as the latest version.
 */
@Slf4j
public class SingularConsentAssentUpdate implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/consent-assent-add-introduction.conf";
    private static final String ACTIVITY_CODE = "CONSENT_ASSENT";
    private static final String V1_VERSION_TAG = "v1";

    private Config studyCfg;
    private Config dataCfg;
    private Gson gson;

    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        log.info("Editing Singular study, {} activity... ", ACTIVITY_CODE);
        insertIntroduction(handle);
    }

    private void insertIntroduction(Handle handle) {

        log.info("{}: adding introduction section...", ACTIVITY_CODE);

        var sectionBlockDao = handle.attach(SectionBlockDao.class);
        var jdbiVersion = handle.attach(JdbiActivityVersion.class);
        var helper = handle.attach(SqlHelper.class);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), ACTIVITY_CODE);
        FormSectionDef introSection = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("introduction")), FormSectionDef.class);

        ActivityVersionDto versionDto = jdbiVersion.findByActivityCodeAndVersionTag(studyDto.getId(), ACTIVITY_CODE, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException(
                        String.format("Couldn't find activity version by studyId=%s, activityCode=%s and versionTag=%s",
                                studyDto.getId(), ACTIVITY_CODE, V1_VERSION_TAG)));

        long introSectionId = sectionBlockDao.insertSection(activityId, introSection, versionDto.getRevId());
        DBUtils.checkUpdate(1, helper.updateIntroSectionIdById(introSectionId, activityId));

        log.info("{}: Added introduction section with sectionId={}", ACTIVITY_CODE, introSection.getSectionId());
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update form_activity_setting set introduction_section_id = :introSecId where form_activity_id = :activityId")
        int updateIntroSectionIdById(@Bind("introSecId") long introSectionId, @Bind("activityId") long activityId);
    }
}
