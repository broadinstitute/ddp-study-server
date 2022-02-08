package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioBackfillPdfVersions implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioBackfillPdfVersions.class);
    private static final String DATA_FILE = "patches/backfill-pdf-versions.conf";
    private static final String GENERIC_TAG = "tag";

    private Path cfgPath;
    private Config studyCfg;
    private Config dataCfg;
    private String studyGuid;
    private String consentCode;
    private String releaseCode;
    private Instant v1StartTime;
    private Instant v2StartTime;
    private String v1Tag;
    private String v2Tag;
    private String releaseV1Tag;

    static void addActivityDataSource(Handle handle, PdfDao pdfDao, PdfConfigInfo info, PdfVersion version,
                                      StudyDto studyDto, String activityCode, String versionTag) {
        Map<String, Set<String>> currentTags = version.getAcceptedActivityVersions();
        if (currentTags.containsKey(activityCode) && currentTags.get(activityCode).contains(versionTag)) {
            LOG.info("Pdf {} {} already have data source for activityCode={} versionTag={}",
                    info.getConfigName(), version.getVersionTag(), activityCode, versionTag);
        } else {
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            long activityVersionId = handle.attach(JdbiActivityVersion.class)
                    .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                    .map(ActivityVersionDto::getId)
                    .orElseThrow(() -> new DDPException("Could not find activity version with code=" + activityCode + "tag=" + versionTag));
            pdfDao.insertDataSource(version.getId(), new PdfActivityDataSource(activityId, activityVersionId));
            LOG.info("Added data source to pdf {} {} for activityCode={} versionTag={}",
                    info.getConfigName(), version.getVersionTag(), activityCode, versionTag);
        }
    }

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
        v1StartTime = Instant.parse(dataCfg.getString("v1StartTime"));
        v2StartTime = Instant.parse(dataCfg.getString("v2StartTime"));
        v1Tag = dataCfg.getString("v1Tag");
        v2Tag = dataCfg.getString("v2Tag");
        releaseV1Tag = dataCfg.getString("releaseV1Tag");
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        PdfBuilder builder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, admin.getId());

        SqlHelper helper = handle.attach(SqlHelper.class);
        PdfDao pdfDao = handle.attach(PdfDao.class);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), dataCfg.getString("consentPdfName"))
                .orElseThrow(() -> new DDPException("Could not find consent pdf info"));

        // 0. There should be one existing pdf version for consent
        List<PdfVersion> versions = pdfDao.findOrderedConfigVersionsByConfigId(info.getId());
        if (versions.size() != 1) {
            throw new DDPException("Expected one consent pdf version but found " + versions.size());
        }

        // 1. Massage generic tag from database migration into v2, if applicable
        PdfVersion version = versions.get(0);
        if (version.getVersionTag().equals(GENERIC_TAG)) {
            LOG.info("Found consent pdf version with generic tag, turning it into v2...");
            String reason = String.format("Create pdf configuration for study=%s with name=%s version=%s",
                    studyDto.getGuid(), info.getConfigName(), v2Tag);
            helper.rewritePdfVersionTag(version.getId(), v2Tag);
            helper.rewriteRevision(version.getRevId(), v2StartTime.toEpochMilli(), reason);
            version = pdfDao.findConfigVersion(version.getId()).get();
            addActivityDataSource(handle, pdfDao, info, version, studyDto, consentCode, v2Tag);
        }

        // 2. Fill in v1 or v2 if it's missing
        if (version.getVersionTag().equals(v1Tag)) {
            LOG.info("Missing v2, backfilling...");
            builder.insertPdfConfig(handle, dataCfg.getConfig("consentPdfV2"));
        } else if (version.getVersionTag().equals(v2Tag)) {
            LOG.info("Missing v1, backfilling...");
            builder.insertPdfConfig(handle, dataCfg.getConfig("consentPdfV1"));
        } else {
            throw new DDPException("Unexpected version tag for consent pdf: " + version.getVersionTag());
        }

        // 3. Backfill release pdf data sources
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
                    studyDto.getGuid(), releaseInfo.getConfigName(), releaseV1Tag);
            helper.rewritePdfVersionTag(releaseVersion.getId(), releaseV1Tag);
            helper.rewriteRevision(releaseVersion.getRevId(), v1StartTime.toEpochMilli(), reason);
            releaseVersion = pdfDao.findConfigVersion(releaseVersion.getId()).get();
        }

        if (!releaseVersion.hasDataSource(PdfDataSourceType.PARTICIPANT)) {
            pdfDao.insertDataSource(releaseVersion.getId(), new PdfDataSource(PdfDataSourceType.PARTICIPANT));
            LOG.info("Added participant data source to release pdf");
        } else {
            LOG.info("Release pdf already have participant data source");
        }

        addActivityDataSource(handle, pdfDao, releaseInfo, releaseVersion, studyDto, releaseCode, releaseV1Tag);
        addActivityDataSource(handle, pdfDao, releaseInfo, releaseVersion, studyDto, consentCode, v1Tag);
        addActivityDataSource(handle, pdfDao, releaseInfo, releaseVersion, studyDto, consentCode, v2Tag);
    }

    interface SqlHelper extends SqlObject {

        @SqlUpdate("update pdf_document_version set version_tag = :tag where pdf_document_version_id = :versionId")
        int _updatePdfVersionTag(@Bind("versionId") long versionId, @Bind("tag") String newTag);

        default void rewritePdfVersionTag(long versionId, String newTag) {
            int numUpdated = _updatePdfVersionTag(versionId, newTag);
            if (numUpdated != 1) {
                throw new DDPException("Expect to update 1 pdf version tag but did " + numUpdated);
            }
        }

        @SqlUpdate("update revision set start_date = :start, change_reason = :reason where revision_id = :revId")
        int _updateRevision(@Bind("revId") long revisionId, @Bind("start") long newStart, @Bind("reason") String newReason);

        default void rewriteRevision(long revisionId, long newStart, String newReason) {
            int numUpdated = _updateRevision(revisionId, newStart, newReason);
            if (numUpdated != 1) {
                throw new DDPException("Expect to update 1 revision but did " + numUpdated);
            }
        }
    }
}
