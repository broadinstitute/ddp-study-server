package org.broadinstitute.ddp.studybuilder.task;

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
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoConsentPdfFixes implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/consent-pdf-fixes.conf";
    private static final String V2_VERSION_TAG = "v2";

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
            throw new DDPException("Data file is missing: " + file);
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
        String pdfConfigName = pdfCfg.getString("oldName");
        log.info("Deleting old v2 pdf {}", pdfConfigName);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find release pdf info"));

        PdfVersion pdfVersion = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(v -> v.getVersionTag().equals(V2_VERSION_TAG))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected release pdf version 2 but not found"));
        PdfConfiguration fullConfig = pdfDao.findFullConfig(pdfVersion);
        pdfDao.deleteSpecificConfigVersion(fullConfig);

        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));

        var pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());

        pdfBuilder.insertPdfConfig(handle, pdfCfg);

        log.info("PDF {} was successfully recreated", pdfConfigName);
    }

}
