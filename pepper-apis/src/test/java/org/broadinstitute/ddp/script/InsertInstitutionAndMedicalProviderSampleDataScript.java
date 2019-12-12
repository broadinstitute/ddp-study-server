package org.broadinstitute.ddp.script;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiCity;
import org.broadinstitute.ddp.db.dao.JdbiInstitution;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.InstitutionDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.MedicalProviderUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not really a test, but a script that inserts the institution-
 * and medical provider-related sample data for DDP-1902
 */
@Ignore
public class InsertInstitutionAndMedicalProviderSampleDataScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(InsertInstitutionAndMedicalProviderSampleDataScript.class);

    @Test
    @Ignore
    public void insertInstitutionRelatedSampleData() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    long cityId = handle.attach(JdbiCity.class).insert(
                            TestInstitutionData.STATE,
                            TestInstitutionData.CITY
                    );
                    long instId = handle.attach(JdbiInstitution.class).insert(
                            new InstitutionDto(
                                    TestInstitutionData.GUID,
                                    cityId,
                                    TestInstitutionData.NAME
                            )
                    );
                    long instAliasId = handle.attach(JdbiInstitution.class).insertAlias(
                            instId,
                            TestInstitutionData.ALIAS
                    );
                }
        );
    }

    @Test
    @Ignore
    public void insertMedicalProviderRelatedSampleData() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiMedicalProvider.class).insert(
                            new MedicalProviderDto(
                                    null,
                                    TestMedicalProviderData.GUID,
                                    MedicalProviderUtil.getTestUserIdByGuid(handle, TestConstants.TEST_USER_GUID),
                                    MedicalProviderUtil.getTestUmbrellaStudyIdByGuid(handle, TestConstants.TEST_STUDY_GUID),
                                    TestMedicalProviderData.INSTITUTION_TYPE,
                                    TestMedicalProviderData.INSTITUTION_NAME,
                                    TestMedicalProviderData.PHYSICIAN_NAME,
                                    TestMedicalProviderData.INSTITUTION_CITY,
                                    TestMedicalProviderData.INSTITUTION_STATE,
                                    null,
                                    null,
                                    null,
                                    null
                            )
                    );
                }
        );
    }

    public static final class TestInstitutionData {
        public static final String GUID = "05AE0BB800";
        public static final String CITY = "Los Angeles";
        public static final String STATE = "California";
        public static final String NAME = "Sacred Heart Hospital";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String ALIAS = "SHH";
    }

    public static final class TestMedicalProviderData {
        public static final String GUID = "BBAB095933";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String INSTITUTION_CITY = "West Windsor Township";
        public static final String INSTITUTION_STATE = "New Jersey";
    }

}
