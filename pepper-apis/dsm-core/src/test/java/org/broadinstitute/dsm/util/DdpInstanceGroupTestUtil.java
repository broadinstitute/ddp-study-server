package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.GroupDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;
//todo add comments and java docs

@Slf4j
public class DdpInstanceGroupTestUtil {
    private static final String SQL_INSERT_DDP_INSTANCE_GROUP =
            "INSERT INTO ddp_instance_group SET ddp_instance_id = ?, ddp_group_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE_GROUP =
            "DELETE FROM ddp_instance_group WHERE ddp_instance_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE =
            "DELETE FROM ddp_instance WHERE ddp_instance_id = ?";

    private static final String SQL_DELETE_GROUP =
            "DELETE FROM ddp_group WHERE group_id = ?";

    static DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    static GroupDao groupDao = new GroupDao();


    public static int createInstanceGroup(String instanceName, String studyGroup) {
        int instanceId = getDdpInstanceId(instanceName);
        int studyGroupId = getGroupId(studyGroup);
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DDP_INSTANCE_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, instanceId);
                stmt.setInt(2, studyGroupId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            try {
                deleteInstance(instanceId);
            } catch (Exception e) {
                log.error("Failed to delete DDP instance {}", instanceId);
            }
            throw new DsmInternalError("Error adding DDP instance group " + instanceName, res.resultException);
        }
        return instanceId;
    }

    public static DDPInstanceDto createInstance(String instanceName, String studyGuid, String collaboratorPrefix, String esIndex) {
        if (ddpInstanceDao.getDDPInstanceIdByInstanceName(instanceName) != -1) {
            return ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).get();
        }
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        ddpInstanceDto.setInstanceName(instanceName);
        ddpInstanceDto.setStudyGuid(studyGuid);
        ddpInstanceDto.setEsParticipantIndex(esIndex);
        ddpInstanceDto.setCollaboratorIdPrefix(collaboratorPrefix);
        ddpInstanceDto.setIsActive(true);
        ddpInstanceDto.setAuth0Token(false);
        ddpInstanceDto.setMigratedDdp(false);
        int testCreatedInstanceId = ddpInstanceDao.create(ddpInstanceDto);
        ddpInstanceDto.setDdpInstanceId(testCreatedInstanceId);
        log.info("Created test DDP instance {} with ID={}", instanceName, testCreatedInstanceId);
        return ddpInstanceDto;
    }

    public static void deleteInstanceGroup(int instanceId) {
        inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_INSTANCE_GROUP)) {
                stmt.setInt(1, instanceId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting DDP instance group: ddpInstanceId=%d", instanceId);
                throw new DsmInternalError(msg, ex);
            }
        });

        deleteInstance(instanceId);
    }

    private static void deleteInstance(int instanceId) {
        inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_INSTANCE)) {
                stmt.setInt(1, instanceId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting DDP instance: ddpInstanceId=%d", instanceId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }

    public static int getDdpInstanceId(String instanceName) {
        int id = ddpInstanceDao.getDDPInstanceIdByInstanceName(instanceName);
        if (id != -1) return id;
        return createTestDdpInstance(instanceName, null).getDdpInstanceId();
    }

    public static int deleteStudyGroup(int groupId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_GROUP)) {
                stmt.setInt(1, groupId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting study group: groupId=%d", groupId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }

    public static DDPInstanceDto createTestDdpInstance(String ddpInstanceName) {
        return createTestDdpInstance(ddpInstanceName, null);
    }

    public static DDPInstanceDto createTestDdpInstance(String ddpInstanceName, String esIndex) {
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().build();
        ddpInstanceDto.setInstanceName(ddpInstanceName);
        ddpInstanceDto.setStudyGuid(ddpInstanceName);
        ddpInstanceDto.setEsParticipantIndex(esIndex);
        ddpInstanceDto.setIsActive(true);
        ddpInstanceDto.setAuth0Token(false);
        ddpInstanceDto.setMigratedDdp(false);
        int testCreatedInstanceId = ddpInstanceDao.create(ddpInstanceDto);
        ddpInstanceDto.setDdpInstanceId(testCreatedInstanceId);
        log.info("Created test DDP instance {} with ID={}", ddpInstanceName, testCreatedInstanceId);
        return ddpInstanceDto;
    }

    public static int getGroupId(String studyGroup) {
        return groupDao.getGroupIdByName(studyGroup);
    }


    public static int createGroup(String studyGroup) {
        if (getGroupId(studyGroup) != -1) {
            return getGroupId(studyGroup);
        }
        return groupDao.create(studyGroup);
    }

}
