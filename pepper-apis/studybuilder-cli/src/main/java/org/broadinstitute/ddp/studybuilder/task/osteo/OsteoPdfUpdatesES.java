package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoPdfUpdatesES implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "study-pdfs-en-es.conf";

    private Config studyCfg;
    private Config dataCfg;
    private Path cfgPath;
    private Handle handle;
    private StudyDto studyDto;
    private PdfDao pdfDao;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.studyCfg = studyCfg;
        this.cfgPath = cfgPath;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.handle = handle;
        this.pdfDao = handle.attach(PdfDao.class);

        for (var activityCfg : dataCfg.getConfigList("pdfs")) {
            updateActivity(activityCfg);
        }
    }

    private void updateActivity(Config pdfCfg) {
        String pdfConfigName = pdfCfg.getString("name");
        var versionCfg = pdfCfg.getConfigList("versions");
        String tag = versionCfg.get(0).getString("tag");
        log.info("Deleting current {} pdf config {}  ", tag, pdfConfigName);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find pdf info"));

        PdfVersion pdfVersion = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(v -> v.getVersionTag().equals(tag))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected pdf version " + tag + " but not found"));
        PdfConfiguration fullConfig = pdfDao.findFullConfig(pdfVersion);
        pdfDao.deleteSpecificConfigVersion(fullConfig);

        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        var pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());
        pdfBuilder.insertPdfConfig(handle, pdfCfg);
        log.info("Pdf config {} successfully recreated", pdfConfigName);
    }

}
