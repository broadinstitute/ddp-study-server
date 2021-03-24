package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DatabaseTest extends TestHelper{

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTest.class);

    private static final String SELECT_KITREQUEST_QUERY = "SELECT * FROM ddp_instance where instance_name = ?";

    @BeforeClass
    public static void first() {
        setupDB();
    }


    @Test
    public void testDatabaseConnection() {
        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        String instance = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "ddp_instance_id");
        Assert.assertNotNull(instance);
    }

    @Test
    public void isDatabaseDifferent() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        TransactionWrapper.init(10, cfg.getString("portal.dbUrl"), cfg, false);
        TransactionWrapper.init(10, cfg.getString("dev.url"), "dev", cfg, false);
        List<String> usersLocal = getUser(null);
        List<String> usersDev = getUser("dev");
        Assert.assertNotEquals(usersLocal, usersDev);
    }

    @Test
    public void testMBCConnection() throws Exception {
        Connection con = null;
        try {
            Class.forName("org.postgresql.Driver");
            con = DriverManager.getConnection(cfg.getString("mbc.url"));
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = con.prepareStatement("select * from physicians")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Assert.assertNotNull(rs.getString("encrypted_name"));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
        }
        catch (ClassNotFoundException e) {
            logger.warn(e.toString());
        }
        catch (SQLException e) {
            logger.warn(e.toString());
        }
        finally {
            con.close();
        }
    }

    private List<String> getUser(String sourceName) {
        List<String> strings = new ArrayList<>();
        SimpleResult results = null;
        if (sourceName != null) {
            results = inTransaction((conn) -> {
                return getValues(conn, strings);
            }, sourceName);
        }
        else {
            results = inTransaction((conn) -> {
                return getValues(conn, strings);
            });
        }

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of assignees ", results.resultException);
        }
        return strings;
    }

    private SimpleResult getValues(Connection conn, List<String> strings){
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement("select * from access_user")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    strings.add(rs.getString("name"));
                }
            }
        } catch (SQLException ex) {
            dbVals.resultException = ex;
        }
        return dbVals;
    }
}
