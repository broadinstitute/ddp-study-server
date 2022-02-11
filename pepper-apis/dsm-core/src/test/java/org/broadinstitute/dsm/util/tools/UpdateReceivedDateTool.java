package org.broadinstitute.dsm.util.tools;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.tools.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class UpdateReceivedDateTool {

    private static final Logger logger = LoggerFactory.getLogger(UpdateReceivedDateTool.class);

    public static final String SELECT_KIT_RECEIVED_QUERY = "select receive_date from ddp_kit where kit_label = ?";
    private static final String SET_KIT_RECEIVED_QUERY = "update ddp_kit set receive_date = ? where kit_label = ?";

    private static Config cfg;

    private static boolean testScenario = false;

    private static String propFile;
    private static String testJson;

    public static void main(String[] args) {
        littleMain();
    }

    public static void argumentsForTesting(String propFileTesting, String json) {
        testScenario = true;
        propFile = propFileTesting;
        testJson = json;
    }

    public static void littleMain() {
        try {
            if (!testScenario) {
                String confFile = "config/test-config.conf";
                setup(confFile);

                String migrationFile = "receivedUpdate.txt";
                update(migrationFile);
            }
            else {
                setup(propFile);
                update(testJson);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to migrate data ", ex);
            System.exit(-1);
        }
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(config)));

        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
    }

    private static void update(@NonNull String file) throws Exception {
        // Request KitRequests
        String fileContent = TestUtil.readFile(file);
        List<Map<String, String>> content = FileUtil.readFileContent(fileContent);
        inTransaction((conn) -> {
            try {
                for (Map<String, String> line : content) {
                    String smId = line.get(DBUtil.SM_ID);
                    String receivedDateString = line.get(DBUtil.RECEIVED);
                    String receiveDate = DBUtil.checkNotReceived(conn, SELECT_KIT_RECEIVED_QUERY, smId, DBConstants.DSM_RECEIVE_DATE);
                    if (StringUtils.isNotBlank(smId) && StringUtils.isNotBlank(receivedDateString)) {
                        if (StringUtils.isBlank(receiveDate)) {
                            DBUtil.setToReceived(conn, SET_KIT_RECEIVED_QUERY, smId, DBUtil.getLong(receivedDateString));
                        }
                        else {
                            logger.warn("Kit w/ SM-ID " + smId + " had already a receive_date");
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException(" insertIntoDB ", e);
            }
            return null;
        });
    }
}
