package org.broadinstitute.ddp.studybuilder.task;

import static org.broadinstitute.ddp.studybuilder.task.AngioBackfillPdfVersions.addActivityDataSource;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainBackfillPdfVersions implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainBackfillPdfVersions.class);
    private static final String DATA_FILE = "patches/backfill-pdf-versions.conf";
    private static final String GENERIC_TAG = "tag";

    private Config dataCfg;
    private String studyGuid;
    private String consentCode;
    private String releaseCode;
    private String v1Tag;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        studyGuid = dataCfg.getString("studyGuid");
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }

        consentCode = dataCfg.getString("consentActivityCode");
        releaseCode = dataCfg.getString("releaseActivityCode");
        v1Tag = dataCfg.getString("v1Tag");
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        AngioBackfillPdfVersions.SqlHelper helper = handle.attach(AngioBackfillPdfVersions.SqlHelper.class);
        PdfDao pdfDao = handle.attach(PdfDao.class);

        // Backfill consent

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), dataCfg.getString("consentPdfName"))
                .orElseThrow(() -> new DDPException("Could not find consent pdf info"));

        List<PdfVersion> versions = pdfDao.findOrderedConfigVersionsByConfigId(info.getId());
        if (versions.size() != 1) {
            throw new DDPException("Expected one consent pdf version but found " + versions.size());
        }

        PdfVersion version = versions.get(0);
        if (version.getVersionTag().equals(GENERIC_TAG)) {
            LOG.info("Found consent pdf version with generic tag, turning it into v1...");
            String reason = String.format("Create pdf configuration for study=%s with name=%s version=%s",
                    studyDto.getGuid(), info.getConfigName(), v1Tag);
            helper.rewritePdfVersionTag(version.getId(), v1Tag);
            helper.rewriteRevision(version.getRevId(), version.getRevStart(), reason);
            version = pdfDao.findConfigVersion(version.getId()).get();
            addActivityDataSource(handle, pdfDao, info, version, studyDto, consentCode, v1Tag);
        }

        // Backfill release

        PdfConfigInfo releaseInfo = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), dataCfg.getString("releasePdfName"))
                .orElseThrow(() -> new DDPException("Could not find release pdf info"));

        List<PdfVersion> releaseVersions = pdfDao.findOrderedConfigVersionsByConfigId(releaseInfo.getId());
        if (releaseVersions.size() != 1) {
            throw new DDPException("Expected one release pdf version but found " + releaseVersions.size());
        }

        PdfVersion releaseVersion = releaseVersions.get(0);
        if (releaseVersion.getVersionTag().equals(GENERIC_TAG)) {
            LOG.info("Found release pdf version with generic tag, turning it into v1...");
            String reason = String.format("Create pdf configuration for study=%s with name=%s version=%s",
                    studyDto.getGuid(), releaseInfo.getConfigName(), v1Tag);
            helper.rewritePdfVersionTag(releaseVersion.getId(), v1Tag);
            helper.rewriteRevision(releaseVersion.getRevId(), releaseVersion.getRevStart(), reason);
            releaseVersion = pdfDao.findConfigVersion(releaseVersion.getId()).get();

            if (!releaseVersion.hasDataSource(PdfDataSourceType.PARTICIPANT)) {
                pdfDao.insertDataSource(releaseVersion.getId(), new PdfDataSource(PdfDataSourceType.PARTICIPANT));
                LOG.info("Added participant data source to release pdf");
            } else {
                LOG.info("Release pdf already have participant data source");
            }

            addActivityDataSource(handle, pdfDao, releaseInfo, releaseVersion, studyDto, releaseCode, v1Tag);
            addActivityDataSource(handle, pdfDao, releaseInfo, releaseVersion, studyDto, consentCode, v1Tag);
        }
    }
}
