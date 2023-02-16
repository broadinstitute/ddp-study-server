package org.broadinstitute.dsm.model.kit;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;

public class KitDaoMock implements KitDao {
    @Override
    public int create(KitRequestShipping kitRequestShipping) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<KitRequestShipping> get(long id) {
        return Optional.empty();
    }

    @Override
    public Boolean isBloodKit(String kitLabel) {
        return false;
    }

    @Override
    public Boolean hasTrackingScan(String kitLabel) {
        return null;
    }

    @Override
    public Optional<ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId) {
        return Optional.empty();
    }

    @Override
    public Optional<ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId) {
        return Optional.empty();
    }

    @Override
    public Optional<ScanError> updateKitReceived(KitRequestShipping kitRequestShipping, String userId) {
        return Optional.empty();
    }

    @Override
    public Integer insertKit(KitRequestShipping kitRequestShipping) {
        return null;
    }

    @Override
    public Integer insertKitRequest(KitRequestShipping kitRequestShipping) {
        return null;
    }

    @Override
    public Optional<KitRequestShipping> getKitRequest(Long kitRequestId) {
        return Optional.empty();
    }

    @Override
    public Optional<KitRequestShipping> getKit(Long kitId) {
        return Optional.empty();
    }

    @Override
    public Integer deleteKitRequest(Long kitRequestId) {
        return null;
    }

    @Override
    public Integer deleteKit(Long kitId) {
        return null;
    }

    @Override
    public Optional<KitRequestShipping> getKitByDdpLabel(String ddpLabel, String kitLabel) {
        return Optional.empty();
    }

    @Override
    public Optional<List<KitRequestShipping>> getSubkitsByDdpLabel(String ddpLabel, String kitLabel) {
        return null;
    }

    @Override
    public List<KitRequestShipping> getKitsByHruid(String hruid) {
        return null;
    }

    @Override
    public Optional<ScanError> updateKitLabel(KitRequestShipping kitRequestShipping) {
        return Optional.empty();
    }
}
