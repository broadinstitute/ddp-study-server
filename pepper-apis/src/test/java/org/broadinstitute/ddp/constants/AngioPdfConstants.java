package org.broadinstitute.ddp.constants;

public class AngioPdfConstants {
    public static final class PdfFileLocations {
        public static final String CONSENT_PDF_LOCATION = "src/main/resources/ConsentForm.pdf";
        public static final String RELEASE_FIRST_PAGE = "src/test/resources/angio/releasepdf/ReleaseForm_firstPage.pdf";
        public static final String RELEASE_PHYSICIAN = "src/test/resources/angio/releasepdf/ReleaseForm_physicians.pdf";
        public static final String RELEASE_BIOPSY = "src/test/resources/angio/releasepdf/ReleaseForm_biopsyInstitution.pdf";
        public static final String RELEASE_INSTITUTION = "src/test/resources/angio/releasepdf/ReleaseForm_institution.pdf";
        public static final String RELEASE_LAST_PAGE = "src/test/resources/angio/releasepdf/ReleaseForm_lastPage.pdf";
    }

    public static final class ConsentFields {
        public static final String DRAW_BLOOD_YES = "drawBlood_YES";
        public static final String DRAW_BLOOD_NO = "drawBlood_NO";
        public static final String TISSUE_SAMPLE_YES = "tissueSample_YES";
        public static final String TISSUE_SAMPLE_NO = "tissueSample_NO";
        public static final String FULL_NAME = "fullName";
        public static final String DATE_OF_BIRTH = "dateOfBirth";
        public static final String TODAY_DATE = "date";
    }

    public static final class ReleaseFirstPageFields {
        public static final String FIRST_NAME = "firstName";
        public static final String LAST_NAME = "lastName";
        public static final String PROXY_FIRST_NAME = "proxyFirstName";
        public static final String PROXY_LAST_NAME = "proxyLastName";
        public static final String MAILING_ADDRESS_STREET = "street";
        public static final String MAILING_ADDRESS_CITY = "city";
        public static final String MAILING_ADDRESS_STATE = "state";
        public static final String MAILING_ADDRESS_PHONE = "phone";
        public static final String MAILING_ADDRESS_ZIP = "zip";
        public static final String MAILING_ADDRESS_COUNTRY = "country";
    }

    public static final class ReleasePhysicianPageFields {
        public static final String NAME = "physicianName";
        public static final String INSTITUTION = "physicianInstitution";
        public static final String CITY = "physicianCity";
        public static final String STATE = "physicianState";
    }

    public static final class ReleaseBiopsyPageFields {
        public static final String INSTITUTION = "biopsyInstitution";
        public static final String CITY = "biopsyCity";
        public static final String STATE = "biopsyState";
    }

    public static final class ReleaseInstitutionPageFields {
        public static final String INSTITUTION = "institutionName";
        public static final String CITY = "institutionCity";
        public static final String STATE = "institutionState";
    }

    public static final class ReleaseLastPageFields {
        public static final String FULL_NAME = "fullName";
        public static final String DOB = "dateOfBirth";
        public static final String DATE = "date";
    }
}
