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

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
    }


    // When the study supports the language in the user profile, we choose it
    @Test
    public void test_givenStudySupportsRU_andPreferredLangIsRU_andNoAcceptLangHeader_whenGetPreferredLangIsCalled_thenItReturnsRU() {
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

    // When study doesn't suport the language, we fall back to the default one regardless of the user preferences
    @Test
    public void test_givenStudySupportsNoLang_andPreferredLangIsRU_andNoLanguageHeader_whenGetPreferredLangIsCalled_thenItReturnsEN() {
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

    // When study doesn't suport the language, we fall back to the default one regardless of the Accept-Language header
    @Test
    public void test_givenStudySupportsNoLang_andPrefLangIsFR_andLangHeaderContainsRU_whenGetPreferredLangIsCalled_thenItReturnsEN() {
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

    // Language header always overrides the language in the user profile
    @Test
    public void test_givenStudySupportsRU_andPreferredLangIsEN_andLanguageHeaderContainsRU_whenGetPreferredLangIsCalled_thenItReturnsRU() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiStudyLanguage.class).insert(testData.getStudyGuid(), LANG_RU);
                    String acceptLanguageHeader = "Accept-Language: ru-RU, ru";
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_EN), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_RU, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }

    // If the study doesn't support the language in the Accept-Language header, we resort to the one in the user profile
    @Test
    public void test_givenStudySupportsEN_andPreferredLangIsEN_andLanguageHeaderContainsRU_whenGetPreferredLangIsCalled_thenItReturnsEN() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiStudyLanguage.class).insert(testData.getStudyGuid(), LANG_EN);
                    String acceptLanguageHeader = "Accept-Language: ru-RU, ru";
                    LanguageDto languageDto = new StudyLanguageResolutionFilter().getPreferredLanguage(
                            handle, acceptLanguageHeader, Locale.forLanguageTag(LANG_EN), testData.getStudyGuid()
                    );
                    Assert.assertEquals(LANG_EN, languageDto.getIsoCode());
                    handle.rollback();
                }
        );
    }
}
