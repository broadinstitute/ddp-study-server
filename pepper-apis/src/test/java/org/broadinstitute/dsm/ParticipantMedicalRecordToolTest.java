package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.ParticipantMedicalRecordTool;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantMedicalRecordToolTest extends TestHelper {

    @BeforeClass
    public static void first() {
        setupDB();
    }

    @Test
    public void testParticipantMedicalRecordTool() {
        if (!cfg.getString("portal.environment").startsWith("Local")) {
            throw new RuntimeException("Not local environment");
        }

        if (!cfg.getString("portal.dbUrl").contains("local")) {
            throw new RuntimeException("Not your test db");
        }
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
//        TransactionWrapper.reset(TestUtil.UNIT_TEST, DBConstants.EEL_DB_NAME);
        ParticipantMedicalRecordTool.argumentsForTesting("config/test-config.conf", TEST_DDP, "/Users/simone/IdeaProjects/ddp-dsm/src/test/resources/AllFieldsDatStat.txt");
        ParticipantMedicalRecordTool.littleMain();
    }
}
