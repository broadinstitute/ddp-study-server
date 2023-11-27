package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class BrugadaPdfs implements CustomTask {

    private static final String DATA_FILE = "study-pdfs.conf";
    private static final String STUDY_GUID = "brugada";

    private Path cfgPath;
    private Config studyCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

    }


    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));

        List<? extends Config> pdfs = dataCfg.getConfigList("pdfs");

        //insert pdf configs and templates
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());
        pdfs.forEach(pdf -> pdfBuilder.insertPdfConfig(handle, pdf));
    }

}
