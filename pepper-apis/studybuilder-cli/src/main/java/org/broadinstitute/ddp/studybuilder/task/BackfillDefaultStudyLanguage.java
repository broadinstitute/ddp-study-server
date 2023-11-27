package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

@Slf4j
public class BackfillDefaultStudyLanguage implements CustomTask {
    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        //config not really used.. Backfilling all existing studies
        //cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        JdbiLanguageCode jdbiLangCode = handle.attach(JdbiLanguageCode.class);
        String languageName = "English";
        String languageCode = "en";
        StudyLanguageDao dao = handle.attach(StudyLanguageDao.class);
        Long langCodeId = jdbiLangCode.getLanguageCodeId(languageCode);
        if (langCodeId == null) {
            throw new DDPException("Could not find language using code: en");
        }

        List<String> studyList = new ArrayList<>();
        studyList.add("ANGIO");
        studyList.add("cmi-brain");
        studyList.add("CMI-OSTEO");
        studyList.add("cmi-mbc");
        boolean isDefault = true;
        for (String studyGuid : studyList) {
            //get studyId
            StudyDto dto = jdbiUmbrellaStudy.findByStudyGuid(studyGuid);
            if (dto == null) {
                log.error("Study : {} not found. skipping default study language backfill.", studyGuid);
                continue;
            }
            long studyId = dto.getId();
            //insert into study_language
            long studyLanguageId = dao.insert(studyId, langCodeId, languageName);
            //now set as default
            dao.setAsDefaultLanguage(studyId, langCodeId);
            log.info("Populated study language with id={}, languageCode={}, languageName={} isDefault={} study: {} ",
                    studyLanguageId, languageCode, languageName, isDefault, studyGuid);
        }
    }

}
