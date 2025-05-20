package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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

public class StudyActivityPdfVersionSupport implements CustomTask {

    private String dataFile;
    private String studyGuid;
    private Path cfgPath;
    private Config studyCfg;
    private Config dataCfg;

    public StudyActivityPdfVersionSupport(String studyGuid, String dataFilePath) {
        this.studyGuid = studyGuid;
        this.dataFile = dataFilePath;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only meant for the " + studyGuid + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

    }


    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        List<? extends Config> pdfs = dataCfg.getConfigList("pdfs");

        //insert the new version
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());
        pdfs.forEach(pdf -> pdfBuilder.insertPdfConfig(handle, pdf));

    }

}
