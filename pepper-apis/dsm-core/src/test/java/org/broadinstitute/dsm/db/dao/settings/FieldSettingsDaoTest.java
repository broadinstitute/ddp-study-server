package org.broadinstitute.dsm.db.dao.settings;

import java.util.Optional;

import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.Test;

public class FieldSettingsDaoTest extends DbTxnBaseTest {

    @Test
    public void testGetFieldSettingsByFieldTypeAndColumnName() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> refSource = fieldSettingsDao.getFieldSettingsByFieldTypeAndColumnName(
                "RGP_MEDICAL_RECORDS_GROUP", DBConstants.REFERRAL_SOURCE_ID);

    }
}
