
package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.CANCERS;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.CHECKBOX;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.DATE;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.DRUGS;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.MULTI_OPTIONS;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.MULTI_TYPE;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.MULTI_TYPE_ARRAY;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.NUMBER;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.OPTIONS;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.TABLE;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.TEXT;
import static org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType.TEXT_AREA;
import static org.junit.Assert.assertEquals;

import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Test;

public class MedicalRecordAbstractionFieldTypeTest {

    @Test
    public void getEnumByInnerValue() {
        assertEquals(MedicalRecordAbstractionFieldType.of("date"), DATE);
        assertEquals(MedicalRecordAbstractionFieldType.of("number"), NUMBER);
        assertEquals(MedicalRecordAbstractionFieldType.of("multi_options"), MULTI_OPTIONS);
        assertEquals(MedicalRecordAbstractionFieldType.of("textarea"), TEXT_AREA);
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