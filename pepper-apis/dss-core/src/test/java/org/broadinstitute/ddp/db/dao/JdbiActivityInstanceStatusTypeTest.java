package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JdbiActivityInstanceStatusTypeTest extends TxnAwareBaseTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetStatusTypeId() {
        TransactionWrapper.useTxn(handle -> {
            JdbiActivityInstanceStatusType jdbiStatusType = handle.attach(JdbiActivityInstanceStatusType.class);
            long statusTypeId = jdbiStatusType.getStatusTypeId(InstanceStatusType.CREATED);
            assertTrue(statusTypeId >= 0L);
        });
    }

    @Test
    public void testGetStatusTypeId_notKnown() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("no results");
        TransactionWrapper.useTxn(handle -> {
            JdbiActivityInstanceStatusType jdbiStatusType = handle.attach(JdbiActivityInstanceStatusType.class);
            jdbiStatusType.getStatusTypeId(null);
        });
    }
}
