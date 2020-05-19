package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class StudyLanguageDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testInsertStudyLanguage() {
        TransactionWrapper.useTxn(handle -> {
            StudyLanguageDao dao = handle.attach(StudyLanguageDao.class);
            Long englishLangCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("en");
            dao.insert(testData.getStudyId(), englishLangCodeId, "english");

            StudyLanguageSql studyLanguageSql = handle.attach(StudyLanguageSql.class);
            List<Long> defaultLangs = studyLanguageSql.selectDefaultLanguageCodeId(testData.getStudyId());
            assertNotNull(defaultLangs);
            Assert.assertTrue(defaultLangs.isEmpty());

            Long frenchLangCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("fr");
            dao.insert(testData.getStudyId(), frenchLangCodeId, "french");

            //now set as default
            dao.setAsDefaultLanguage(testData.getStudyId(), englishLangCodeId);
            defaultLangs = studyLanguageSql.selectDefaultLanguageCodeId(testData.getStudyId());
            assertNotNull(defaultLangs);
            Assert.assertTrue(defaultLangs.size() == 1);
            Assert.assertEquals(englishLangCodeId, defaultLangs.get(0));

            handle.rollback();
        });
    }

}
