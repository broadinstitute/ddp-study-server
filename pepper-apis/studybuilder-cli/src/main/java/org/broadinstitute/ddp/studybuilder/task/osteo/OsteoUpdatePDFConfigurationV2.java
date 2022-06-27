package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.*;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoUpdatePDFConfigurationV2 implements CustomTask {

    private static final String V2_VERSION_TAG = "v2";
    private static final String DATA_FILE = "patches/study-pdf-configuration.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        log.info("Adding new data source versions for consents PDFs");
        StudyBuilder builder = new StudyBuilder(cfgPath, studyCfg, varsCfg);
        Path dirPath = cfgPath.getParent();
        Auth0TenantDto tenantDto = builder.getTenantOrInsert(handle);
        UmbrellaDto umbrellaDto = builder.getUmbrellaOrInsert(handle);
        StudyDto studyDto = builder.getStudyOrInsert(handle, tenantDto.getId(), umbrellaDto.getId());
        List<ClientDto> clientDtos = builder.getClientsOrInsert(handle, tenantDto);
        builder.grantClientsAccessToStudy(handle, clientDtos, studyDto);
        ClientDto webClient = clientDtos.stream()
                .filter(client -> client.getWebPasswordRedirectUrl() != null)
                .findFirst()
                .orElse(clientDtos.get(0));
        UserDto adminDto = builder.getAdminUserOrInsert(handle, webClient.getId());
        new PdfBuilder(dirPath, dataCfg, studyDto, adminDto.getUserId()).run(handle);

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
