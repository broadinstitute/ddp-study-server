package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.nio.file.Path;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoPDFv2 implements CustomTask {
    private static final String V2_VERSION_TAG = "v2";
    public static final String CONSENT = "CONSENT";
    public static final String PARENTAL_CONSENT = "PARENTAL_CONSENT";
    public static final String CONSENT_ASSENT = "CONSENT_ASSENT";
    public static final String RELEASE_SELF = "RELEASE_SELF";
    public static final String RELEASE_MINOR = "RELEASE_MINOR";
    public static final String OSPROJECT_CONSENT = "osproject-consent";
    public static final String OSPROJECT_CONSENT_ASSENT = "osproject-consent-assent";
    public static final String OSPROJECT_RELEASE = "osproject-release";
    public static final String OSPROJECT_RELEASE_PARENTAL = "osproject-release-parental";
    public static final String OSPROJECT_RELEASE_CONSENT_ASSENT = "osproject-release-consent-assent";

    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("Adding new data source versions for consents PDFs");
        long studyId = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid")).getId();
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE, CONSENT);
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE, RELEASE_SELF);
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE_PARENTAL, PARENTAL_CONSENT);
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE_PARENTAL, RELEASE_MINOR);
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE_CONSENT_ASSENT, CONSENT_ASSENT);
        addNewConsentDataSourceToReleasePdf(handle, studyId, OSPROJECT_RELEASE_CONSENT_ASSENT, RELEASE_MINOR);
    }

    private void addNewConsentDataSourceToReleasePdf(Handle handle, long studyId, String pdfName, String activityCode) {
        PdfDao pdfDao = handle.attach(PdfDao.class);
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyId, pdfName)
                .orElseThrow(() -> new DDPException("Could not find pdf with name=" + pdfName));

        PdfVersion version = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(ver -> ver.getAcceptedActivityVersions().containsKey(activityCode))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find pdf version with data source for activityCode=" + activityCode));

        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
        long activityVersionId = jdbiActivityVersion.findByActivityCodeAndVersionTag(studyId, activityCode, V2_VERSION_TAG)
                .map(ActivityVersionDto::getId)
                .orElseThrow(() -> new DDPException(String.format(
                        "Could not find activity version id for activityCode=%s versionTag=%s", activityCode, V2_VERSION_TAG)));

        pdfDao.insertDataSource(version.getId(), new PdfActivityDataSource(activityId, activityVersionId));

        log.info("Added activity data source with activityCode={} versionTag={} to pdf {} version {}",
                activityCode, V2_VERSION_TAG, info.getConfigName(), version.getVersionTag());
    }
    
}
