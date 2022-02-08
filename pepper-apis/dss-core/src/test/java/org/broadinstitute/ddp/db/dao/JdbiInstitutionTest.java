package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.InstitutionDto;
import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.Handle;
import org.junit.Test;

public class JdbiInstitutionTest extends TxnAwareBaseTest {

    private long cityId;
    private long institutionId;
    private long institutionAliasId;

    private long insertTestInstitution(Handle handle) {
        return handle.attach(JdbiInstitution.class).insert(
                new InstitutionDto(
                        TestInstitutionData.GUID,
                        cityId,
                        TestInstitutionData.NAME
                )
        );
    }

    private long insertTestInstitutionAlias(Handle handle) {
        return handle.attach(JdbiInstitution.class).insertAlias(
                institutionId,
                TestInstitutionData.ALIAS
        );
    }

    private long insertTestCity(Handle handle) {
        return handle.attach(JdbiCity.class).insert(
                TestInstitutionData.STATE,
                TestInstitutionData.CITY
        );
    }

    private void insertTestData(Handle handle) {
        cityId = insertTestCity(handle);
        institutionId = insertTestInstitution(handle);
        institutionAliasId = insertTestInstitutionAlias(handle);
    }

    private void deleteTestData(Handle handle) {
        deleteTestInstitutionAlias(handle);
        deleteTestInstitution(handle);
        deleteTestCity(handle);
    }

    private void deleteTestInstitutionAlias(Handle handle) {
        handle.attach(JdbiInstitution.class).deleteAlias(institutionId, institutionAliasId);
    }

    private void deleteTestInstitution(Handle handle) {
        handle.attach(JdbiInstitution.class).deleteByGuid(TestInstitutionData.GUID);
    }

    private void deleteTestCity(Handle handle) {
        handle.attach(JdbiCity.class).deleteById(cityId);
    }

    private Long getTestInstitutionTypeIdByCode(Handle handle, InstitutionType type) {
        return handle.attach(JdbiInstitutionType.class).getIdByType(type).get();
    }

    private Optional<InstitutionDto> getTestInstitution(Handle handle) {
        return handle.attach(JdbiInstitution.class).getByGuid(TestInstitutionData.GUID);
    }

    @Test
    public void testInsert() {
        TransactionWrapper.useTxn(handle -> {
            insertTestData(handle);
            assertFalse(institutionId == 0);
            assertFalse(institutionAliasId == 0);
            deleteTestData(handle);
        });
    }

    @Test
    public void testGetSuggestions() {
        TransactionWrapper.useTxn(handle -> {
            insertTestData(handle);
            List<InstitutionSuggestionDto> suggestions = handle.attach(JdbiInstitution.class)
                    .getSuggestionsByNamePattern("%Hea%");
            InstitutionSuggestionDto suggestion = suggestions.get(0);
            assertEquals(suggestion.getName(), TestInstitutionData.NAME);
            assertEquals(suggestion.getCity(), TestInstitutionData.CITY);
            assertEquals(suggestion.getState(), TestInstitutionData.STATE);
            deleteTestData(handle);
        });
    }

    @Test
    public void testDeleteByGuid() {
        TransactionWrapper.useTxn(handle -> {
            insertTestData(handle);
            deleteTestData(handle);
            Optional<InstitutionDto> inst = getTestInstitution(handle);
            assertFalse(inst.isPresent());
        });
    }

    private static final class TestInstitutionData {
        public static final String GUID = "FEFE0BB8080";
        public static final long CITY_ID = 1;
        public static final String CITY = "Los Angeles";
        public static final String STATE = "California";
        public static final String NAME = "Sacred Heart Hospital";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String ALIAS = "SHH";
    }

}
