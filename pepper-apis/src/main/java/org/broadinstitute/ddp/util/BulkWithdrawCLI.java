package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script that will withdraw a list of participant hruids
 * from the given study.
 */
public class BulkWithdrawCLI {

    private static final Logger LOG = LoggerFactory.getLogger(BulkWithdrawCLI.class);

    public static void main(String[] args) {
        String fileOfHruids = args[0];
        String studyGuid = args[1];
        String[] hruidsArray = new String[]{};
        try {
            hruidsArray = IOUtils.toString(new FileReader(new File(fileOfHruids))).split("\n");
        } catch(IOException e) {
            LOG.error("Could not parse hruids", e);
            System.exit(-1);
        }
        final List<String> hruids = Arrays.asList(hruidsArray);
        LogbackConfigurationPrinter.printLoggingConfiguration();
        Config cfg = ConfigManager.getInstance().getConfig();
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);

        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        LOG.info("Using db {}", dbUrl);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
        Instant now = Instant.now();

        try {
            TransactionWrapper.useTxn(handle -> {
                JdbiUserStudyEnrollment enrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);
                UserDao userDao = handle.attach(UserDao.class);

                DataExportDao exportDao = handle.attach(DataExportDao.class);
                PreparedStatement instanceStmt = handle.getConnection().prepareStatement("select ddp_instance_id from dev_dsm_db.ddp_instance where instance_name= ?");
                instanceStmt.setString(1, studyGuid);
                ResultSet resultSet = instanceStmt.executeQuery();
                resultSet.next();
                long instanceId = resultSet.getLong(1);
                if (resultSet.next()) {
                    throw new RuntimeException("Too many row rows for instance " + instanceId);
                }


                PreparedStatement dsmWithdrawStmt = handle.getConnection().prepareStatement("insert into dev_dsm_db.ddp_participant_exit(ddp_instance_id, ddp_participant_id,exit_date, exit_by,in_ddp)\n" +
                        "values(?,?,?,?,?);");


                for (String hruid : hruids) {
                    LOG.info("Withdrawing {}", hruid);
                    User participant = userDao.findUserByHruid(hruid).get();
                    enrollmentDao.changeUserStudyEnrollmentStatus(participant.getGuid(), studyGuid, EnrollmentStatusType.EXITED_AFTER_ENROLLMENT, now.toEpochMilli());
                    dsmWithdrawStmt.setLong(1, instanceId);
                    dsmWithdrawStmt.setString(2, participant.getGuid());
                    dsmWithdrawStmt.setLong(3, now.toEpochMilli());
                    dsmWithdrawStmt.setLong(4, 9); // zim
                    dsmWithdrawStmt.setBoolean(5, true);

                    int numDsmWithdrawn = dsmWithdrawStmt.executeUpdate();
                    if (numDsmWithdrawn != 1) {
                        throw new RuntimeException("Updated "+ numDsmWithdrawn + " dsm rows");
                    }
                    exportDao.queueDataSync(participant.getGuid(), studyGuid);

                    LOG.info("Withdrew {} from {} and queued ES data sync.", hruid, studyGuid);
                }


            });
        } catch(SQLException e) {
            LOG.error("Error running update",e);
            System.exit(-1);
        }

    }
}
