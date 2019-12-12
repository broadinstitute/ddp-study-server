package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Blob;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.IconBlobDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiFormTypeActivityInstanceStatusTypeTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetIconBlobs() throws SQLException {
        TransactionWrapper.useTxn(handle -> {
            StudyDto study1 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            StudyDto study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);

            JdbiFormTypeActivityInstanceStatusType dao = handle.attach(JdbiFormTypeActivityInstanceStatusType.class);

            List<IconBlobDto> icons = dao.getIconBlobs(study1.getGuid());
            assertNotNull(icons);
            assertTrue(icons.isEmpty());

            byte[] blobData = "testing".getBytes();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), testData.getUserId(), "test");
            dao.insert(study2.getId(), FormType.GENERAL, InstanceStatusType.CREATED, blobData, revId);

            icons = dao.getIconBlobs(study2.getGuid());
            assertNotNull(icons);
            assertEquals(1, icons.size());
            Blob iconBlob = icons.iterator().next().getIconBlob();
            assertEquals(blobData.length, iconBlob.length());

            byte[] readBlob = iconBlob.getBytes(1, blobData.length);
            for (int blobPos = 0; blobPos < blobData.length; blobPos++) {
                Assert.assertEquals(blobData[blobPos], readBlob[blobPos]);
            }
            handle.rollback();
        });
    }
}
