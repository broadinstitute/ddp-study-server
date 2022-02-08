package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task to update Google Buckets used for Angio/Brain study icons.
 */
public class ConsolidateBuckets implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(ConsolidateBuckets.class);
    private static final String DATA_FILE = "patches/consolidate-buckets.conf";

    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;
    private String studyGuid;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
        studyGuid = studyCfg.getString("study.guid");

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        SqlHelper helper = handle.attach(SqlHelper.class);
        for (var bucketsCfg : dataCfg.getConfigList("buckets")) {
            String oldName = bucketsCfg.getString("old");
            String newName = bucketsCfg.getString("new");
            int numUpdated = helper.replaceIconUrlByStudyId(studyDto.getId(), oldName, newName);
            LOG.info("Updated {} icon url from old={} to new={}", numUpdated, oldName, newName);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update form_section_icon_source as src"
                + "   join form_section_icon as icon on icon.form_section_icon_id = src.form_section_icon_id"
                + "    set url = replace(url, :old, :new)"
                + "  where locate(:old, url) > 0"
                + "    and icon.form_section_id in ("
                + "        select fas.introduction_section_id from form_activity_setting as fas"
                + "          join study_activity as act on act.study_activity_id = fas.form_activity_id"
                + "         where act.study_id = :studyId and fas.introduction_section_id is not null"
                + "         union"
                + "        select fas.closing_section_id from form_activity_setting as fas"
                + "          join study_activity as act on act.study_activity_id = fas.form_activity_id"
                + "         where act.study_id = :studyId and fas.closing_section_id is not null"
                + "         union"
                + "        select fafs.form_section_id from form_activity__form_section as fafs"
                + "          join study_activity as act on act.study_activity_id = fafs.form_activity_id"
                + "         where act.study_id = :studyId)")
        int replaceIconUrlByStudyId(@Bind("studyId") long studyId,
                                    @Bind("old") String oldSubstring,
                                    @Bind("new") String newSubstring);
    }
}
