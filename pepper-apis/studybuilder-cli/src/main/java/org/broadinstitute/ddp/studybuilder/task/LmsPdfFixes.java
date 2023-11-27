package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.dao.JdbiPdfTemplates;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
public class LmsPdfFixes implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/pdf-fixes.conf";
    private static final String V1_VERSION_TAG = "v1";

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

        for (var activityCfg : dataCfg.getConfigList("pdfTemplates")) {
            updateActivity(activityCfg);
        }
    }

    private void updateActivity(Config pdfCfg) {
        String pdfConfigName = pdfCfg.getString("name");
        log.info("Editing pdf {}", pdfConfigName);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find release pdf info"));

        PdfVersion pdfVersion = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(v -> v.getVersionTag().equals(V1_VERSION_TAG))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected release pdf version 1 but not found"));

        CustomTemplate customTemplate = (CustomTemplate) pdfDao.findFullTemplatesByVersionId(pdfVersion.getId())
                .stream()
                .filter(pdfTemplate -> pdfTemplate.getType().equals(PdfTemplateType.CUSTOM))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected custom template but not found"));

        if (pdfCfg.hasPath("filepath")) {
            File filepath = cfgPath.getParent().resolve(pdfCfg.getString("filepath")).toFile();
            if (!filepath.exists()) {
                throw new DDPException("Pdf file is missing: " + filepath);
            }

            byte[] rawBytes;
            try (FileInputStream input = new FileInputStream(filepath)) {
                rawBytes = IOUtils.toByteArray(input);
            } catch (IOException e) {
                throw new DDPException(e);
            }

            handle.attach(JdbiPdfTemplates.class).updatePdfBaseTemplate(customTemplate.getId(), rawBytes);
            log.info("Updated pdf bytes for pdfBaseTemplateId: {} filepath: {}", customTemplate.getId(), pdfCfg.getString("filepath"));
        }

        log.info("PDF {} was successfully updated", pdfConfigName);
    }
}
