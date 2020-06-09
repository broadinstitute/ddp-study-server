package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.study.StudyLanguage;
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

            //First make sure we don't run into errors when languages haven't been specified
            List<StudyLanguage> studyLanguageList = dao.findLanguages(testData.getStudyId());
            Assert.assertEquals(0, studyLanguageList.size());

            Long englishLangCodeId = LanguageStore.getOrComputeDefault(handle).getId();
            dao.insert(testData.getStudyId(), englishLangCodeId, "english");

            StudyLanguageSql studyLanguageSql = handle.attach(StudyLanguageSql.class);
            List<Long> defaultLangs = studyLanguageSql.selectDefaultLanguageCodeId(testData.getStudyId());
            assertNotNull(defaultLangs);
            Assert.assertTrue(defaultLangs.isEmpty());

            Long frenchLangCodeId = LanguageStore.getOrCompute(handle, "fr").getId();
            dao.insert(testData.getStudyId(), frenchLangCodeId, "french");

            //now set as default
            dao.setAsDefaultLanguage(testData.getStudyId(), englishLangCodeId);
            defaultLangs = studyLanguageSql.selectDefaultLanguageCodeId(testData.getStudyId());
            assertNotNull(defaultLangs);
            Assert.assertTrue(defaultLangs.size() == 1);
            Assert.assertEquals(englishLangCodeId, defaultLangs.get(0));

            //Test getting full language information
            studyLanguageList = dao.findLanguages(testData.getStudyId());
            Assert.assertNotNull(studyLanguageList);
            Assert.assertEquals(studyLanguageList.size(), 2);
            StudyLanguage toCheck = studyLanguageList.get(0);
            Assert.assertEquals(toCheck.getDisplayName(), "english");
            Assert.assertTrue(toCheck.isDefault());
            Assert.assertEquals(toCheck.getLanguageCode(), "en");
            toCheck = studyLanguageList.get(1);
            Assert.assertEquals(toCheck.getDisplayName(), "french");
            Assert.assertFalse(toCheck.isDefault());
            Assert.assertEquals(toCheck.getLanguageCode(), "fr");

            handle.rollback();
        });
    }

}
