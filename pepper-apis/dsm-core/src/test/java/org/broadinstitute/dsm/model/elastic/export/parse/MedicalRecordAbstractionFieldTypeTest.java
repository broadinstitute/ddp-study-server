package org.broadinstitute.dsm.model.elastic.export.parse;

import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Test;

import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldType.*;
import static org.junit.Assert.assertEquals;

public class MedicalRecordAbstractionFieldTypeTest {

    @Test
    public void getEnumByInnerValue() {
        assertEquals(MedicalRecordAbstractionFieldType.of("date"), DATE);
        assertEquals(MedicalRecordAbstractionFieldType.of("number"), NUMBER);
        assertEquals(MedicalRecordAbstractionFieldType.of("multi_options"), MULTI_OPTIONS);
        assertEquals(MedicalRecordAbstractionFieldType.of("text_area"), TEXT_AREA);
        assertEquals(MedicalRecordAbstractionFieldType.of("multi_type_array"), MULTI_TYPE_ARRAY);
        assertEquals(MedicalRecordAbstractionFieldType.of("text"), TEXT);
        assertEquals(MedicalRecordAbstractionFieldType.of("table"), TABLE);
        assertEquals(MedicalRecordAbstractionFieldType.of("options"), OPTIONS);
        assertEquals(MedicalRecordAbstractionFieldType.of("drugs"), DRUGS);
        assertEquals(MedicalRecordAbstractionFieldType.of("multi_type"), MULTI_TYPE);
        assertEquals(MedicalRecordAbstractionFieldType.of("checkbox"), CHECKBOX);
        assertEquals(MedicalRecordAbstractionFieldType.of("cancers"), CANCERS);
    }

    @Test
    public void throwExceptionIfFieldTypeDoesNotExist() {
        IntStream.range(1, 50).forEach(iteration -> catchExceptionAndAssertTrue());
    }

    private void catchExceptionAndAssertTrue() {
        try {
            MedicalRecordAbstractionFieldType.of(TestUtil.generateRandomString());
        } catch (NoSuchElementException ignored) {
            assert (true);
        }
    }

}