package org.broadinstitute.ddp.filter;

import java.util.Locale;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiStudyLanguage;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class StudyLanguageResolutionFilterTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    private static final String LANG_EN = "en";
    private static final String LANG_RU = "ru";
    private static final String LANG_HEADER_RU = "Accept-Language: ru-RU, ru";

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void test_whenStudySupportsLanguageInUserProfile_andNoLanguageHeaderIsSpecified_thenWeChooseOneFromProfile() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiStudyLanguage.class).insert(testData.getStudyGuid(), LANG_RU);
                    String acceptLanguageHeader = null;
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_RU, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesntSupportLanguageInUserProfile_andNoLanguageHeaderIsSpecified_thenWeFallBackToDefaultOne() {
        TransactionWrapper.useTxn(
                handle -> {
                    long ruLanguageId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(LANG_RU);
                    handle.attach(JdbiProfile.class).updatePreferredLangId(testData.getTestingUser().getUserId(), ruLanguageId);
                    String acceptLanguageHeader = null;
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesntSupportLanguageInLanguageHeader_thenWeFallBackToDefaultOne() {
        TransactionWrapper.useTxn(
                handle -> {
                    String acceptLanguageHeader = "Accept-Language: fr-FR, fr";
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenBothLanguageInUserProfile_andLanguageHeaderAreSpecfied_thenLanguageInHeaderTakesPrecedence() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiStudyLanguage.class).insert(testData.getStudyGuid(), LANG_RU);
                    String acceptLanguageHeader = LANG_HEADER_RU;
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_EN), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_RU, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesntSupportLanguageInLanguageHeader_thenWeResortToLanguageInUserProfile() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiStudyLanguage.class).insert(testData.getStudyGuid(), LANG_EN);
                    String acceptLanguageHeader = LANG_HEADER_RU;
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_EN), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }
}
