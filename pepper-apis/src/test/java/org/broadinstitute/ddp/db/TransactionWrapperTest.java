package org.broadinstitute.ddp.db;

import static org.broadinstitute.ddp.util.MySqlTestContainerUtil.MYSQL_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

public class TransactionWrapperTest {

    private static final String TEST_QUERY = "select test_name from test";

    private static final Logger LOG = LoggerFactory.getLogger(TransactionWrapperTest.class);

    // since we are just testing the transaction wrapper, we want a separate db, not the usual pepper db
    private static MySQLContainer dbContainer = new MySQLContainer(MYSQL_VERSION);

    private static String testDbUrl;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUp() throws SQLException {
        dbContainer.start();
        try {
            TimeUnit.MILLISECONDS.sleep(2000);
        } catch (InterruptedException e) {
            LOG.info("Wait interrupted", e);
        }
        testDbUrl = MySqlTestContainerUtil.getFullJdbcTestUrl(dbContainer);
        LiquibaseUtil.runChangeLog(new com.mysql.cj.jdbc.Driver(), testDbUrl, "src/test/resources/db-testscripts/txnwrappertest.xml");
        TransactionWrapper.reset();
    }

    @AfterClass
    public static void tearDown() {
        dbContainer.stop();
        try {
            TimeUnit.MILLISECONDS.sleep(2000);
        } catch (InterruptedException e) {
            LOG.info("Wait interrupted", e);
        }
    }

    @Before
    public void initPool() {
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, testDbUrl));
    }

    @After
    public void resetPool() {
        TransactionWrapper.reset();
    }



    @Test
    public void testPoolExhaustionBehavior() {
        int maxPoolSize = 1;
        synchronized (TransactionWrapper.class) {
            TransactionWrapper.reset();
            TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                    maxPoolSize, testDbUrl));
            final AtomicBoolean gotPoolExhaustedError = new AtomicBoolean(false);

            TransactionWrapper.useTxn((handle) -> {
                try {
                    ResultSet rs = queryTestData(handle.getConnection());
                    int numRows = 0;
                    while (rs.next()) {
                        numRows++;
                    }
                    assertEquals(1, numRows);
                    try {
                        TransactionWrapper.withTxn((handle2) -> {
                            return null;
                        });
                    } catch (Exception e) {
                        gotPoolExhaustedError.set(e.getCause().getMessage().toLowerCase().contains("pool"));
                    }
                } catch (SQLException e) {
                    LOG.error("Trouble making first connection", e);
                    fail("Trouble making first connection");
                }
            });

            assertTrue("Two connections were created, but the pool should have failed because the max size is "
                    + maxPoolSize, gotPoolExhaustedError.get());
        }
    }

    private ResultSet queryTestData(Connection conn) throws SQLException {
        return conn.prepareStatement(TEST_QUERY).executeQuery();
    }

    @Test
    public void testInTransaction() throws SQLException {
        String name = TransactionWrapper.withTxn(handle -> {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(TEST_QUERY)) {
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                return rs.getString(1);
            }
        });
        assertEquals("testing", name);
    }

    @Test
    public void testInTransaction_rethrow() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("test msg");

        TransactionWrapper.withTxn(handle -> {
            throw new RuntimeException("test msg");
        });
    }

    @Test
    public void testUseTransaction() {
        TransactionWrapper.withTxn(handle -> {
            String name;
            try {
                try (PreparedStatement stmt = handle.getConnection().prepareStatement(TEST_QUERY)) {
                    ResultSet rs = stmt.executeQuery();
                    assertTrue(rs.next());
                    name = rs.getString(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error running " + TEST_QUERY, e);
            }
            assertEquals("testing", name);
            return null;
        });
    }

    @Test
    public void testUseTransaction_rethrow() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("test msg");

        TransactionWrapper.useTxn(handle -> {
            throw new RuntimeException("test msg");
        });
    }

    @Test
    public void testJdbi_withTxn_returnsValue() {
        boolean res = TransactionWrapper.withTxn(handle -> {
            String name = handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            return "testing".equals(name);
        });
        assertTrue(res);
    }

    @Test
    public void testJdbi_withTxn_rethrowsException() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("test msg");

        TransactionWrapper.withTxn(handle -> {
            handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            throw new RuntimeException("test msg");
        });
    }

    @Test
    public void testJdbi_withTxn_updatesSameTransaction() {
        TransactionWrapper.withTxn(handle -> {
            int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
            assertEquals(1, res);

            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(2, names.size());
            assertTrue(names.contains("testing"));
            assertTrue(names.contains("jdbi"));

            res = handle.execute("delete from test where test_name = ?", "jdbi");
            assertEquals(1, res);

            return null;
        });
    }

    @Test
    public void testJdbi_withTxn_updatesAcrossTransactions() {
        TransactionWrapper.withTxn(handle -> {
            int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
            assertEquals(1, res);
            return null;
        });

        TransactionWrapper.withTxn(handle -> {
            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(2, names.size());
            assertTrue(names.contains("testing"));
            assertTrue(names.contains("jdbi"));

            int res = handle.execute("delete from test where test_name = ?", "jdbi");
            assertEquals(1, res);

            return null;
        });
    }

    @Test
    public void testJdbi_withTxn_rollback() {
        try {
            TransactionWrapper.withTxn(handle -> {
                int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
                assertEquals(1, res);

                List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
                assertEquals(2, names.size());

                throw new RuntimeException("test rollback");
            });
        } catch (RuntimeException e) {
            assertEquals("test rollback", e.getMessage());
        }

        TransactionWrapper.withTxn(handle -> {
            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(1, names.size());
            assertEquals("testing", names.get(0));
            return null;
        });
    }

    @Test
    public void testJdbi_withTxn_multipleConnections() {
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 2, testDbUrl));
        boolean res = TransactionWrapper.withTxn(handle -> {
            String name = handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            String name2 = TransactionWrapper.withTxn(
                    h -> h.select(TEST_QUERY).mapTo(String.class).findOnly());
            assertEquals(name, name2);
            return "testing".equals(name);
        });
        assertTrue(res);
    }

    @Test
    public void testJdbi_withTxn_outOfConnections() {
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, testDbUrl));
        try {
            TransactionWrapper.withTxn(handle -> {
                handle.select(TEST_QUERY).mapTo(String.class).findOnly();
                TransactionWrapper.withTxn(h -> {
                    fail("this callback should not have ran");
                    return null;
                });
                return null;
            });
        } catch (DDPException e) {
            assertTrue("Got " + e.getCause().getMessage() + " instead of expected regex", e.getCause().getMessage()
                    .matches(".*Connection is not available.*"));
        }
    }

    @Test
    public void testJdbi_useTxn_canUseHandle() {
        TransactionWrapper.useTxn(handle -> {
            String name = handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            assertEquals("testing", name);
        });
    }

    @Test
    public void testJdbi_useTxn_rethrowsException() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("test msg");

        TransactionWrapper.useTxn(handle -> {
            handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            throw new RuntimeException("test msg");
        });
    }

    @Test
    public void testJdbi_useTxn_updatesSameTransaction() {
        TransactionWrapper.useTxn(handle -> {
            int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
            assertEquals(1, res);

            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(2, names.size());
            assertTrue(names.contains("testing"));
            assertTrue(names.contains("jdbi"));

            res = handle.execute("delete from test where test_name = ?", "jdbi");
            assertEquals(1, res);
        });
    }

    @Test
    public void testJdbi_useTxn_updatesAcrossTransactions() {
        TransactionWrapper.useTxn(handle -> {
            int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
            assertEquals(1, res);
        });

        TransactionWrapper.useTxn(handle -> {
            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(2, names.size());
            assertTrue(names.contains("testing"));
            assertTrue(names.contains("jdbi"));

            int res = handle.execute("delete from test where test_name = ?", "jdbi");
            assertEquals(1, res);
        });
    }

    @Test
    public void testJdbi_useTxn_rollback() {
        try {
            TransactionWrapper.useTxn(handle -> {
                int res = handle.execute("insert into test (test_name) values (?)", "jdbi");
                assertEquals(1, res);

                List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
                assertEquals(2, names.size());

                throw new RuntimeException("test rollback");
            });
        } catch (RuntimeException e) {
            assertEquals("test rollback", e.getMessage());
        }

        TransactionWrapper.useTxn(handle -> {
            List<String> names = handle.select(TEST_QUERY).mapTo(String.class).list();
            assertNotNull(names);
            assertEquals(1, names.size());
            assertEquals("testing", names.get(0));
        });
    }

    @Test
    public void testJdbi_useTxn_multipleConnections() {
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 2, testDbUrl));
        TransactionWrapper.useTxn(handle -> {
            String name = handle.select(TEST_QUERY).mapTo(String.class).findOnly();
            TransactionWrapper.useTxn(h -> {
                String name2 = h.select(TEST_QUERY).mapTo(String.class).findOnly();
                assertEquals(name, name2);
            });
            assertEquals("testing", name);
        });
    }

    @Test
    public void testJdbi_useTxn_outOfConnections() {
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, testDbUrl));
        try {
            TransactionWrapper.useTxn(handle -> {
                handle.select(TEST_QUERY).mapTo(String.class).findOnly();
                TransactionWrapper.useTxn(h -> {
                    fail("this callback should not have ran");
                });
            });
        } catch (DDPException e) {
            assertTrue(e.getCause().getMessage().matches(".*Connection is not available.*"));
        }
    }
}
