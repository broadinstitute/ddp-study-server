package org.broadinstitute.ddp.db.dao.copyanswer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *  Maps the source child stable id to the destination/target child stable id
 */
public class CompositeAnswerCopyConfiguration {

    private final Map<String, String> sourceToDestination = new HashMap<>();

    public void addChildCopyConfiguration(String sourceStableId, String destinationStableId) {
        sourceToDestination.put(sourceStableId, destinationStableId);
    }

    public Map<String, String> getChildCopyConfiguration() {
        return Collections.unmodifiableMap(sourceToDestination);
    }

}
