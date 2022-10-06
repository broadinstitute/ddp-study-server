package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class AtcpLanguageFix implements CustomTask {
    private static final String DATA_FILE = "patches/language.conf";
    private static final String STUDY = "atcp";

    private static final String OLD_VALUE = "oldValue";

    private static final String NEW_VALUE = "newValue";

    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }

        cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        SqlHelper helper = handle.attach(SqlHelper.class);
        dataCfg.getConfigList("languages").forEach(langCfg -> {
            String oldValue = langCfg.getString(OLD_VALUE);
            String newValue = langCfg.getString(NEW_VALUE);
            int updated = helper.updateLanguage(studyDto.getId(), oldValue, newValue);
            if (updated != 1) {
                throw new DDPException(String.format("Language with displayName %s in study %s not found", oldValue, STUDY));
            }
            log.info("Successfully updated language displayName {}->{} in {}", oldValue, newValue, STUDY);
        });
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update study_language set name=:newValue where name=:oldValue and umbrella_study_id=:studyId")
        int updateLanguage(@Bind("studyId") long studyId, @Bind("oldValue") String oldValue, @Bind("newValue") String newValue);
    }
}
