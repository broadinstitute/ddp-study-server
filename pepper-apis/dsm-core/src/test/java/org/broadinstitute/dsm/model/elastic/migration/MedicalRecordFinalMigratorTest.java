
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.abstraction.MedicalRecordFinalDaoLive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MedicalRecordFinalMigratorTest {

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    public void getDataByRealm() {
        var migrator = new MedicalRecordFinalMigrator("participants_structured.cmi.cmi-mbc", "Pepper-MBC", new MedicalRecordFinalDaoLive());
        Map<String, Object> dataByRealm = migrator.getDataByRealm();
        migrator.export();
        Assert.assertFalse(false);
    }
}
