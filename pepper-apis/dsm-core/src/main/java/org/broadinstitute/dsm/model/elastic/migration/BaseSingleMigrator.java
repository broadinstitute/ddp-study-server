package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public abstract class BaseSingleMigrator extends BaseMigrator {

    private Map<String, Object> transformedObject = new HashMap<>();

    protected BaseSingleMigrator(String index, String realm, String object) {
        super(index, realm, object);
    }

    @Override
    public Map<String, Object> generate() {
        return new HashMap<>(Map.of(ESObjectConstants.DSM, new HashMap<>(Map.of(entity, transformedObject))));
    }

    @Override
    protected void transformObject(Object object) {
        transformedObject = getObjectTransformer().transformObjectToMap(object);
    }

    @Override
    protected Object mergeObjects(Object object1, Object object2) {
        throw new DsmInternalError("mergeObjects not supported for single migrators");
    }

    /**
     * Compare a DB record for a given entity with the corresponding ES record, and return the results as a
     * verification log.
     *
     * @param ddpParticipantId the participant id
     * @param esDataMap map ES DSM fields to their values
     * @param verifyFields true if differences in record fields should be logged, false to just log missing entity records
     * @return verification log (as a list to comply with the interface)
     */
    @Override
    protected List<VerificationLog> verifyElasticData(String ddpParticipantId, Map<String, Object> esDataMap,
                                                      boolean verifyFields) {
        VerificationLog verificationLog = new VerificationLog(ddpParticipantId, entity);
        Optional<Map<String, Object>> esEntityMap = getEsEntityMap(esDataMap);
        if (esEntityMap.isEmpty()) {
            if (!transformedObject.isEmpty()) {
                if (verifyFields) {
                    verificationLog.setDbOnlyFields(transformedObject.keySet());
                } else {
                    verificationLog.setDbOnlyEntityIds(Set.of(ddpParticipantId));
                }
            }
        } else if (transformedObject.isEmpty()) {
            if (verifyFields) {
                verificationLog.setEsOnlyFields(esEntityMap.get().keySet());
            } else {
                verificationLog.setEsOnlyEntityIds(Set.of(ddpParticipantId));
            }
        } else if (verifyFields) {
            compareElasticData(esEntityMap.get(), transformedObject, verificationLog);
        }

        return List.of(verificationLog);
    }

    private Optional<Map<String, Object>> getEsEntityMap(Map<String, Object> esDataMap) {
        if (esDataMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(((Map)esDataMap.get(entity)));
    }
}
