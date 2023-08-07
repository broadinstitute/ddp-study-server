package org.broadinstitute.dsm.db.dao.kit;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.model.kit.ScanError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitStatus;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;

public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Optional<ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId);

    Optional<ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId);

    Optional<ScanError> updateKitReceived(KitRequestShipping kitRequestShipping, String userId);

    Integer insertKit(KitRequestShipping kitRequestShipping);

    Integer insertKitRequest(KitRequestShipping kitRequestShipping);

    Optional<KitRequestShipping> getKitRequest(Long kitRequestId);

    Optional<KitRequestShipping> getKit(Long kitId);

    Integer deleteKitRequest(Long kitRequestId);

    Integer deleteKit(Long kitId);

    Optional<KitRequestShipping> getKitByDdpLabel(String ddpLabel, String kitLabel);

    Optional<List<KitRequestShipping>> getSubkitsByDdpLabel(String ddpLabel, String kitLabel);

    List<KitRequestShipping> getKitsByHruid(String hruid);

    Optional<ScanError> updateKitLabel(KitRequestShipping kitRequestShipping);

    ResultSet getKitsInDatabaseByInstanceId(DDPInstance ddpInstance);

    ResultSet getKitsByJuniperKitId(String juniperKitId);

    ResultSet getKitsByParticipantId(String participantId);

    ArrayList<NonPepperKitStatus> getKitsByKitId(String[] kitIdsArray, NonPepperStatusKitService nonPepperStatusKitService);
}
