package org.broadinstitute.ddp.filter;

import java.util.Locale;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class StudyLanguageResolutionFilterTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    private static final String LANG_EN = "en";
    private static final String LANG_RU = "ru";
    private static final String LANG_FR = "fr";
    private static final String LANG_HE = "he";
    private static final String LANG_HEADER_RU = "Accept-Language: ru-RU, ru";
    private static final String LANG_HEADER_FR = "Accept-Language: fr-FR, fr";
    private static final String LANG_HEADER_HE = "Accept-Language: he-HE, he";

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void test_whenStudySupportsLanguageInUserProfile_andNoLanguageHeaderIsSpecified_thenWeChooseOneFromProfile() {
        TransactionWrapper.useTxn(
                handle -> {
                    enableLanguageSupportForStudy(handle, testData.getStudyGuid(), LANG_RU);
                    String acceptLanguageHeader = null;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_RU, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesNotSupportLanguageInUserProfile_andNoLanguageHeaderIsSpecified_thenWeFallBackToDefaultOne() {
        TransactionWrapper.useTxn(
                handle -> {
                    String acceptLanguageHeader = null;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesNotSupportLanguageInLanguageHeader_thenWeFallBackToDefaultOne() {
        TransactionWrapper.useTxn(
                handle -> {
                    String acceptLanguageHeader = LANG_HEADER_FR;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_RU), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenBothLangInUserProfile_andLangHeaderAreSpecfiedAndSupported_thenLanguageInHeaderTakesPrecedence() {
        TransactionWrapper.useTxn(
                handle -> {
                    enableLanguageSupportForStudy(handle, testData.getStudyGuid(), LANG_RU);
                    enableLanguageSupportForStudy(handle, testData.getStudyGuid(), LANG_FR);
                    String acceptLanguageHeader = LANG_HEADER_RU;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_FR), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_RU, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyDoesNotSupportLanguageInLanguageHeader_thenWeResortToLanguageInUserProfile() {
        TransactionWrapper.useTxn(
                handle -> {
                    enableLanguageSupportForStudy(handle, testData.getStudyGuid(), LANG_FR);
                    String acceptLanguageHeader = LANG_HEADER_RU;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_FR), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_FR, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenStudyUsesNewerLanguageCodeThanJavaLocale_thenTryNewerLanguageCode() {
        TransactionWrapper.useTxn(
                handle -> {
                    enableLanguageSupportForStudy(handle, testData.getStudyGuid(), LANG_HE);
                    String acceptLanguageHeader = LANG_HEADER_HE;
                    LanguageDto languageDto = StudyLanguageResolutionFilter.getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_HE), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_HE, languageDto.getIsoCode());
                }
        );
    }

    private void enableLanguageSupportForStudy(Handle handle, String studyGuid, String isoLanguageCode) {
        handle.attach(StudyLanguageDao.class).insert(studyGuid, isoLanguageCode, null);
    }
}
