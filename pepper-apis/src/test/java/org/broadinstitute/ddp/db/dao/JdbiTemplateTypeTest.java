package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JdbiTemplateTypeTest extends TxnAwareBaseTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetTypeId() {
        TransactionWrapper.useTxn(handle -> {
            JdbiTemplateType dao = handle.attach(JdbiTemplateType.class);
            assertTrue(dao.getTypeId(TemplateType.HTML) >= 0);
            assertTrue(dao.getTypeId(TemplateType.TEXT) >= 0);
        });
    }

    @Test
    public void testGetTypeId_notFound() {
        thrown.expect(IllegalStateException.class);
        TransactionWrapper.useTxn(handle ->
                handle.attach(JdbiTemplateType.class).getTypeId(null));
    }
}
