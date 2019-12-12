package org.broadinstitute.ddp.script.angio;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.AngioPdfConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class AngioPdfConfigurationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioPdfConfigurationScript.class);

    private static final String RELEASE_CONFIG_NAME = "ascproject-release";
    private static final String RELEASE_FILE_NAME = "ascproject-release";

    @Test
    public void runInsertReleasePdfConfig() throws IOException {
        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                    .findByStudyGuid(AngioStudyCreationScript.ANGIO_STUDY_GUID);
            long pdfConfigId = insertReleasePdfConfig(handle, studyDto);
            insertReleasePdfMapping(handle, studyDto, pdfConfigId);
        });
    }

    private long insertReleasePdfConfig(Handle handle, StudyDto studyDto) throws IOException {
        JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

        UserDto userDto = handle.attach(JdbiUser.class)
                .findByUserGuid(AngioStudyCreationScript.ANGIO_USER_GUID);

        String configName = RELEASE_CONFIG_NAME;
        String fileName = RELEASE_FILE_NAME;

        long consentActivityId = handle.attach(JdbiActivity.class)
                .findIdByStudyIdAndCode(studyDto.getId(), AngioConsentActivityCreationScript.ACTIVITY_CODE)
                .orElseThrow(() -> new DDPException("Could not find Angio activity " + AngioConsentActivityCreationScript.ACTIVITY_CODE));
        long consentVersionId = handle.attach(JdbiActivityVersion.class).getActiveVersion(consentActivityId).get().getId();
        long revId = jdbiRev.insertStart(AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR,
                userDto.getUserId(), "Create angio release pdf configuration");

        MailingAddressTemplate addressTemplate = new MailingAddressTemplate(
                IOUtils.toByteArray(new FileInputStream(AngioPdfConstants.PdfFileLocations.RELEASE_FIRST_PAGE)),
                AngioPdfConstants.ReleaseFirstPageFields.FIRST_NAME,
                AngioPdfConstants.ReleaseFirstPageFields.LAST_NAME,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_STREET,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_CITY,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_STATE,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_ZIP,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_COUNTRY,
                AngioPdfConstants.ReleaseFirstPageFields.MAILING_ADDRESS_PHONE);

        PhysicianInstitutionTemplate physicianTemplate = new PhysicianInstitutionTemplate(
                IOUtils.toByteArray(new FileInputStream(AngioPdfConstants.PdfFileLocations.RELEASE_PHYSICIAN)),
                InstitutionType.PHYSICIAN,
                AngioPdfConstants.ReleasePhysicianPageFields.NAME,
                AngioPdfConstants.ReleasePhysicianPageFields.INSTITUTION,
                AngioPdfConstants.ReleasePhysicianPageFields.CITY,
                AngioPdfConstants.ReleasePhysicianPageFields.STATE);

        PhysicianInstitutionTemplate biopsyTemplate = new PhysicianInstitutionTemplate(
                IOUtils.toByteArray(new FileInputStream(AngioPdfConstants.PdfFileLocations.RELEASE_BIOPSY)),
                InstitutionType.INITIAL_BIOPSY,
                null,
                AngioPdfConstants.ReleaseBiopsyPageFields.INSTITUTION,
                AngioPdfConstants.ReleaseBiopsyPageFields.CITY,
                AngioPdfConstants.ReleaseBiopsyPageFields.STATE);

        PhysicianInstitutionTemplate institutionTemplate = new PhysicianInstitutionTemplate(
                IOUtils.toByteArray(new FileInputStream(AngioPdfConstants.PdfFileLocations.RELEASE_INSTITUTION)),
                InstitutionType.INSTITUTION,
                null,
                AngioPdfConstants.ReleaseInstitutionPageFields.INSTITUTION,
                AngioPdfConstants.ReleaseInstitutionPageFields.CITY,
                AngioPdfConstants.ReleaseInstitutionPageFields.STATE);

        CustomTemplate lastPage = new CustomTemplate(
                IOUtils.toByteArray(new FileInputStream(AngioPdfConstants.PdfFileLocations.RELEASE_LAST_PAGE)));
        lastPage.addSubstitution(new AnswerSubstitution(AngioPdfConstants.ReleaseLastPageFields.FULL_NAME,
                consentActivityId, QuestionType.TEXT, AngioConsentActivityCreationScript.CONSENT_SIGNATURE_STABLE_ID));
        lastPage.addSubstitution(new AnswerSubstitution(AngioPdfConstants.ReleaseLastPageFields.DOB,
                consentActivityId, QuestionType.DATE, AngioConsentActivityCreationScript.CONSENT_BIRTHDATE_STABLE_ID));
        lastPage.addSubstitution(new ActivityDateSubstitution(AngioPdfConstants.ReleaseLastPageFields.DATE, consentActivityId));

        PdfConfigInfo info = new PdfConfigInfo(studyDto.getId(), configName, fileName);
        PdfVersion version = new PdfVersion("v1", revId);
        version.addDataSource(new PdfDataSource(PdfDataSourceType.PARTICIPANT));
        version.addDataSource(new PdfActivityDataSource(consentActivityId, consentVersionId));

        PdfConfiguration config = new PdfConfiguration(info, version);
        config.addTemplate(addressTemplate);
        config.addTemplate(physicianTemplate);
        config.addTemplate(biopsyTemplate);
        config.addTemplate(institutionTemplate);
        config.addTemplate(lastPage);

        long pdfConfigId = handle.attach(PdfDao.class).insertNewConfig(config);

        LOG.info("Created angio medical-release pdf configuration with id={}, configName={}, fileName={}",
                pdfConfigId, configName, fileName);

        return pdfConfigId;
    }

    private void insertReleasePdfMapping(Handle handle, StudyDto studyDto, long pdfConfigId) {
        long mappingId = handle.attach(JdbiStudyPdfMapping.class)
                .insert(studyDto.getId(), PdfMappingType.RELEASE, pdfConfigId);
        LOG.info("Created pdf mapping for releasepdf with mapping id={}", mappingId);
    }
}
