package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.nio.file.Path;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoPdfFixes implements CustomTask {
    private static final String V2_VERSION_TAG = "v2";

    private Config studyCfg;

    private StudyDto studyDto;
    private PdfDao pdfDao;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.pdfDao = handle.attach(PdfDao.class);

        removePdfVersion("osproject-release");
        removePdfVersion("osproject-release-parental");
        removePdfVersion("osproject-release-consent-assent");
    }

    private void removePdfVersion(String pdfConfigName) {
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
    }
}
