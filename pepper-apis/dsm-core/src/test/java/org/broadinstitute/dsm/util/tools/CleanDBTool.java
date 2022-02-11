//package org.broadinstitute.dsm.util.tools;
//
//import com.typesafe.config.Config;
//import com.typesafe.config.ConfigFactory;
//import com.typesafe.config.ConfigValueFactory;
//import lombok.NonNull;
//import org.broadinstitute.ddp.db.TransactionWrapper;
//import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
//import org.broadinstitute.dsm.util.DBTestUtil;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.util.List;
//
//public class CleanDBTool {
//
//    private static final Logger logger = LoggerFactory.getLogger(CleanDBTool.class);
//
//    public static void main(String[] args) {
//        Config cfg = ConfigFactory.load();
//        //secrets from vault in a config file
//        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/test-config.conf")));
//
//        //overwrite quartz.jobs
//        cfg = cfg.withValue("quartz.enableJobs", ConfigValueFactory.fromAnyRef("false"));
//        cfg = cfg.withValue("portal.port", ConfigValueFactory.fromAnyRef("9999"));
//        cfg = cfg.withValue("errorAlert.recipientAddress", ConfigValueFactory.fromAnyRef(""));
//
//        if (!cfg.getString("portal.environment").startsWith("Local")) {
//            throw new RuntimeException("Not local environment");
//        }
//
//        if (!cfg.getString("portal.dbUrl").contains("local")) {
//            throw new RuntimeException("Not your test db");
//        }
//
//        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
//                cfg.getString("portal.dbSslKeyStorePwd"),
//                cfg.getString("portal.dbSslTrustStore"),
//                cfg.getString("portal.dbSslTrustStorePwd"));
//        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
//                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
//
//        //change realm and decide if you want to keep the last participant in the db or not
//        cleanDB("Angio", false);
//
//    }
//
//    private static void cleanDB(@NonNull String realm, boolean leaveLastPT) {
//        List<String> ddpParticipantIds = DBTestUtil.getStringList("select * from ddp_participant part, ddp_instance inst where part.ddp_instance_id = inst.ddp_instance_id and  inst.instance_name = \"" + realm + "\" order by participant_id desc", "ddp_participant_id");
//        int startIndex = 0;
//        if (leaveLastPT) {
//            startIndex = 1;
//        }
//        for (int i = startIndex; i < ddpParticipantIds.size(); i++) {
//            logger.info("Going to delete " + ddpParticipantIds.get(i));
//            DBTestUtil.deleteAllParticipantData(ddpParticipantIds.get(i), true);
//            DBTestUtil.deleteAllKitData(ddpParticipantIds.get(i));
//            logger.info("Deleted " + ddpParticipantIds.get(i));
//        }
//    }
//}
