package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;

import liquibase.exception.MigrationFailedException;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LiquibaseUtilTest extends TxnAwareBaseTest {

    private static final String DB_SCRIPTS_DIR = "src/test/resources/db-testscripts";
    private static final String CHANGELOG_TABLE = "DATABASECHANGELOG";
    private static final String[] TEST_TABLES = new String[] {
            "liquibase_test_table", "liquibase_non_existing_table"
    };

    private static Driver driver;
    private static String testDbUrl;

    @BeforeClass
    public static void setup() throws SQLException {
        // We're using existing pepper db to test migration. We can have better isolation by spinning up a new db,
        // but it's a bit too costly in terms of test speed.
        driver = new com.mysql.jdbc.Driver();
        testDbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
    }

    @AfterClass
    public static void cleanup() {
        // Since we're using the usual pepper db, let's clean up any mess we left.
        TransactionWrapper.useTxn(handle -> {
            for (var table : TEST_TABLES) {
                handle.execute("drop table if exists " + table);
            }
            handle.createUpdate("delete from <table> where ID like :pattern")
                    .define("table", CHANGELOG_TABLE)
                    .bind("pattern", "liquibase%")
                    .execute();
        });
    }

    @Test
    public void testSuccessfulMigration() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(driver, testDbUrl, script);
        TransactionWrapper.useTxn(handle -> {
            List<String> names = handle.select("select name from liquibase_test_table").mapTo(String.class).list();
            assertEquals(1, names.size());
            assertEquals("liquibase", names.get(0));
        });
    }

    @Test
    public void testSuccessfulRollbackWhenChangeSetFails() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(driver, testDbUrl, script);
        try {
            script = DB_SCRIPTS_DIR + "/liquibase-migrate-rollback-success.xml";
            LiquibaseUtil.runChangeLog(driver, testDbUrl, script);
            fail("Expected migrations to fail and trigger rollback");
        } catch (DDPException e) {
            assertTrue(e.getMessage().contains("migrations"));
            assertTrue(e.getCause() instanceof MigrationFailedException);
            assertTrue(e.getCause().getMessage().contains(script + "::liquibase-rollback-success-faulty-sql"));
            TransactionWrapper.useTxn(handle -> {
                // Check insertion is rolled back.
                List<String> names = handle.select("select name from liquibase_test_table").mapTo(String.class).list();
                assertEquals(1, names.size());
                assertFalse(names.contains("another"));
            });
        }
    }

    @Test
    public void testFailedRollbackWhenChangeSetFails() {
        String script = DB_SCRIPTS_DIR + "/liquibase-migrate-success.xml";
        LiquibaseUtil.runChangeLog(driver, testDbUrl, script);
        try {
            script = DB_SCRIPTS_DIR + "/liquibase-migrate-rollback-fail.xml";
            LiquibaseUtil.runChangeLog(driver, testDbUrl, script);
            fail("Expected migrations to fail and trigger rollback");
        } catch (DDPException e) {
            assertTrue(e.getMessage().contains("migrations"));
            assertTrue(e.getCause() instanceof MigrationFailedException);
            assertTrue(e.getCause().getMessage().contains(script + "::liquibase-rollback-fail-faulty-sql"));
            TransactionWrapper.useTxn(handle -> {
                // Check that test data is not rolled back since rollback instructions are not provided.
                List<String> names = handle.select("select name from liquibase_test_table").mapTo(String.class).list();
                assertEquals(2, names.size());
                assertTrue(names.contains("another"));
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.execute("delete from liquibase_test_table where name = ?", "another");
            });
        }
    }
}
