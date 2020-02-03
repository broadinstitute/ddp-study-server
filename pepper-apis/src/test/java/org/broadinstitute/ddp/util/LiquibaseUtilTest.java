package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.MySqlTestContainerUtil.MYSQL_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import liquibase.exception.MigrationFailedException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.MySQLContainer;

public class LiquibaseUtilTest {

    private static final String DB_SCRIPTS_DIR = "src/test/resources/db-testscripts";

    private String testDbUrl;

    // since we are testing migration-related actions, we want a separate db here, not the usual pepper dbs
    private final MySQLContainer dbContainer = new MySQLContainer(MYSQL_VERSION);

    @Before
    public void setup() {
        dbContainer.start();
        testDbUrl = MySqlTestContainerUtil.getFullJdbcTestUrl(dbContainer);
        TransactionWrapper.reset();
        TransactionWrapper.init("UTC", new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, testDbUrl));
    }

    @After
    public void cleanup() {
        TransactionWrapper.reset();
        dbContainer.stop();
    }

    @Test
    public void testSuccessfulMigration() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(testDbUrl, script);
        TransactionWrapper.useTxn(handle -> {
            List<String> names = handle.select("select name from test_table").mapTo(String.class).list();
            assertEquals(1, names.size());
            assertEquals("liquibase", names.get(0));
        });
    }

    @Test
    public void testSuccessfulRollbackWhenChangeSetFails() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(testDbUrl, script);
        try {
            script = DB_SCRIPTS_DIR + "/liquibase-migrate-rollback-success.xml";
            LiquibaseUtil.runChangeLog(testDbUrl, script);
            fail("Expected migrations to fail and trigger rollback");
        } catch (DDPException e) {
            assertTrue(e.getMessage().contains("migrations"));
            assertTrue(e.getCause() instanceof MigrationFailedException);
            assertTrue(e.getCause().getMessage().contains(script + "::faulty-sql"));
            TransactionWrapper.useTxn(handle -> {
                // Check insertion is rolled back.
                List<String> names = handle.select("select name from test_table").mapTo(String.class).list();
                assertEquals(1, names.size());
                assertFalse(names.contains("another"));
            });
        }
    }

    @Test
    public void testFailedRollbackWhenChangeSetFails() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(testDbUrl, script);
        try {
            script = DB_SCRIPTS_DIR + "/liquibase-migrate-rollback-faulty.xml";
            LiquibaseUtil.runChangeLog(testDbUrl, script);
            fail("Expected migrations to fail and trigger rollback");
        } catch (DDPException e) {
            assertTrue(e.getMessage().contains("migrations"));
            assertTrue(e.getCause() instanceof MigrationFailedException);
            assertTrue(e.getCause().getMessage().contains(script + "::faulty-sql"));
            TransactionWrapper.useTxn(handle -> {
                // Check that test data is not rolled back since rollback instructions are not provided.
                List<String> names = handle.select("select name from test_table").mapTo(String.class).list();
                assertEquals(2, names.size());
                assertTrue(names.contains("another"));
            });
        }
    }
}
