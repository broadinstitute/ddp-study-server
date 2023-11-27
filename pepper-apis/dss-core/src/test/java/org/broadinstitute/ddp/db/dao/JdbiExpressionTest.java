package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.util.GuidUtils;
import org.junit.Test;

public class JdbiExpressionTest extends TxnAwareBaseTest {

    @Test
    public void testInsertExpression() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            Expression expr = dao.insertExpression("abc");

            assertTrue(expr.getId() >= 0);
            assertNotNull(expr.getGuid());
            assertEquals(expr.getGuid().length(), GuidUtils.STANDARD_GUID_LENGTH);
            assertEquals(expr.getText(), "abc");

            handle.rollback();
        });
    }

    @Test
    public void testUpdateByGuid() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            Expression expr = dao.insertExpression("abc");

            assertEquals(1, dao.updateByGuid(expr.getGuid(), "xyz"));
            Expression updated = dao.getById(expr.getId()).get();
            assertEquals(updated.getId(), expr.getId());
            assertEquals(updated.getGuid(), expr.getGuid());
            assertEquals(updated.getText(), "xyz");

            handle.rollback();
        });
    }

    @Test
    public void testUpdateByGuid_notFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            assertEquals(0, dao.updateByGuid("abc123", "xyz"));
        });
    }

    @Test
    public void testDeleteById() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            Expression expr = dao.insertExpression("abc");

            assertEquals(1, dao.deleteById(expr.getId()));

            Optional<Expression> dto = dao.getById(expr.getId());
            assertFalse(dto.isPresent());
        });
    }

    @Test
    public void testDeleteById_notFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            assertEquals(0, dao.deleteById(123456L));
        });
    }

    @Test
    public void getById() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            Expression expr = dao.insertExpression("abc");
            Optional<Expression> dto = dao.getById(expr.getId());

            assertTrue(dto.isPresent());
            assertEquals(dto.get().getId(), expr.getId());
            assertEquals(dto.get().getGuid(), expr.getGuid());
            assertEquals(dto.get().getText(), "abc");

            handle.rollback();
        });
    }

    @Test
    public void getById_notFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiExpression dao = handle.attach(JdbiExpression.class);
            Optional<Expression> dto = dao.getById(123456L);
            assertFalse(dto.isPresent());
        });
    }
}
