package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.StudyPasswordRequirementsDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiStudyPasswordRequirementsTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private static void insertPasswordRequirements() {
        TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).insert(
                    testData.getTestingStudy().getAuth0TenantId(),
                    TestData.MIN_LENGTH,
                    TestData.IS_UPPERCASE_LETTER_REQUIRED,
                    TestData.IS_LOWERCASE_LETTER_REQUIRED,
                    TestData.IS_SPECIAL_CHARACTER_REQUIRED,
                    TestData.IS_NUMBER_REQUIRED,
                    TestData.MAX_IDENTICAL_CONSECUTIVE_CHARACTERS
                )
        );
    }

    private static void deletePasswordRequirements() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).deleteById(testData.getTestingStudy().getAuth0TenantId())
        );
    }

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsert_givenRequirementsDontExist_whenTheyAreInserted_thenOperationSucceeds() {
        insertPasswordRequirements();
        deletePasswordRequirements();
    }

    @Test
    public void testDeleteById_givenRequirementsExist_whenTheyAreDelete_thenTheyCannotBeFetchedAnymore() {
        insertPasswordRequirements();
        deletePasswordRequirements();
        Optional<StudyPasswordRequirementsDto> reqDto = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).getById(testData.getTestingStudy().getAuth0TenantId())
        );
        Assert.assertFalse(reqDto.isPresent());
    }

    @Test
    public void testFindByGuid_givenRequirementsExist_whenTheyAreFetched_thenValidDataIsReturned() {
        insertPasswordRequirements();
        Optional<StudyPasswordRequirementsDto> reqDto = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).getById(testData.getTestingStudy().getAuth0TenantId())
        );
        Assert.assertTrue(reqDto.isPresent());
        Assert.assertEquals(reqDto.get().getAuth0TenantId(), testData.getTestingStudy().getAuth0TenantId().longValue());
        Assert.assertEquals(reqDto.get().getMinLength(), TestData.MIN_LENGTH);
        Assert.assertEquals(reqDto.get().isUppercaseLetterRequired(), TestData.IS_UPPERCASE_LETTER_REQUIRED);
        Assert.assertEquals(reqDto.get().isLowercaseLetterRequired(), TestData.IS_LOWERCASE_LETTER_REQUIRED);
        Assert.assertEquals(reqDto.get().isSpecialCharacterRequired(), TestData.IS_SPECIAL_CHARACTER_REQUIRED);
        Assert.assertEquals(reqDto.get().getMaxIdenticalConsecutiveCharacters(), TestData.MAX_IDENTICAL_CONSECUTIVE_CHARACTERS);
        deletePasswordRequirements();
    }

    private static class TestData {
        public static final int MIN_LENGTH = 8;
        public static final boolean IS_UPPERCASE_LETTER_REQUIRED = true;
        public static final boolean IS_LOWERCASE_LETTER_REQUIRED = true;
        public static final boolean IS_SPECIAL_CHARACTER_REQUIRED = false;
        public static final boolean IS_NUMBER_REQUIRED = true;
        public static final int MAX_IDENTICAL_CONSECUTIVE_CHARACTERS = 2;

        public static final String NON_EXISTENT_STUDY_GUID = "NON_EXISTENT_STUDY_GUID";
    }

}
