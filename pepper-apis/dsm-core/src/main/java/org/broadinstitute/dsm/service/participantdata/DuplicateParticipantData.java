package org.broadinstitute.dsm.service.participantdata;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Data
public class DuplicateParticipantData {
    private String fieldTypeId;
    private int baseRecordId;
    private int duplicateRecordId;
    private Set<String> commonKeys;
    private Set<String> baseOnlyKeys;
    private Set<String> duplicateOnlyKeys;
    private Map<String, Pair<String, String>> differentValues = new HashMap<>();

    public DuplicateParticipantData(String fieldTypeId, int baseRecordId, int duplicateRecordId) {
        this.fieldTypeId = fieldTypeId;
        this.baseRecordId = baseRecordId;
        this.duplicateRecordId = duplicateRecordId;
    }

    public void setDifferentValues(String key, String baseValue, String duplicateValue) {
        Pair<String, String> pair = new ImmutablePair<>(baseValue, duplicateValue);
        differentValues.put(key, pair);
    }
}
