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
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoV2ReleasePdfUpdates implements CustomTask {

    private static final String DATA_FILE = "patches/osteo-v2-release-pdfs.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config studyCfg;
    private Config dataCfg;
    private OsteoV2ReleasePdfUpdates.SqlHelper sqlHelper;

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
        this.sqlHelper = handle.attach(OsteoV2ReleasePdfUpdates.SqlHelper.class);
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));

        List<? extends Config> pdfs = dataCfg.getConfigList("pdfs");
        pdfs.forEach(pdf -> cleanupV2Version(handle, pdf.getString("name"), studyDto.getId(), "v2"));

        //re-insert new v2 version
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());
        pdfs.forEach(pdf -> pdfBuilder.insertPdfConfig(handle, pdf));

    }

    public void cleanupV2Version(Handle handle, String pdfConfigName, long studyId, String versionTag) {

        PdfDao pdfDao = handle.attach(PdfDao.class);
        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyId, pdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find pdf with name=" + pdfConfigName));

        PdfVersion version = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(ver -> ver.getVersionTag().equals(versionTag))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find pdf version with data source for versionTag=" + versionTag));

        //delete this version to do clean insert
        sqlHelper.deletePdfActivitySourcesByVersionId(version.getId());
        sqlHelper.deletePdfSourcesByVersionId(version.getId());
        sqlHelper.deletePdfVersionTemplateVersionId(version.getId());
        sqlHelper.deletePdfVersionByVersionId(version.getId());

    }


    private interface SqlHelper extends SqlObject {

        @SqlUpdate("delete from pdf_activity_data_source where pdf_data_source_id IN "
                + "(select pdf_data_source_id from pdf_data_source where pdf_document_version_id = :pdfDocVersionId)")
        int deletePdfActivitySourcesByVersionId(@Bind("pdfDocVersionId") long pdfDocVersionId);

        @SqlUpdate("delete from pdf_data_source where pdf_document_version_id = :pdfDocVersionId")
        int deletePdfSourcesByVersionId(@Bind("pdfDocVersionId") long pdfDocVersionId);

        @SqlUpdate("delete from pdf_version_template where pdf_document_version_id = :pdfDocVersionId")
        int deletePdfVersionTemplateVersionId(@Bind("pdfDocVersionId") long pdfDocVersionId);

        @SqlUpdate("delete from pdf_document_version where pdf_document_version_id = :pdfDocVersionId")
        int deletePdfVersionByVersionId(@Bind("pdfDocVersionId") long pdfDocVersionId);

    }

}
