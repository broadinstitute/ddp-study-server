package org.broadinstitute.dsm.db.dao.settings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class FieldSettingsDaoTest extends DbTxnBaseTest {

    @Test
    public void testGetFieldSettingsByFieldTypeAndColumnName() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        // expecting the test DB to have static data setup via Liquibase
        Optional<FieldSettingsDto> fsDto = fieldSettingsDao.getFieldSettingsByFieldTypeAndColumnName(
                "RGP_MEDICAL_RECORDS_GROUP", DBConstants.REFERRAL_SOURCE_ID);

        // ensure some static data we depend on is present and accessible
        Assert.assertTrue(fsDto.isPresent());
        FieldSettingsDto refSource = fsDto.get();

        // details column holds a map of FIND_OUT answers to REF_SOURCE IDs
        Map<String, String> refMap = ObjectMapperSingleton.readValue(
                refSource.getDetails(), new TypeReference<>() {});
        Assert.assertTrue(refMap.containsKey("SEARCH"));
        Assert.assertEquals("GOOGLE", refMap.get("SEARCH"));

        // possible values holds a list of maps where value = REF_SOURCE ID and name = text
        List<Map<String, String>> refValues = ObjectMapperSingleton.readValue(
                refSource.getPossibleValues(), new TypeReference<>() {});

        Set<String> refIDs = refValues.stream().map(m -> m.get("value")).collect(Collectors.toSet());
        Assert.assertTrue(refIDs.contains("YOUTUBE"));
    }
}
