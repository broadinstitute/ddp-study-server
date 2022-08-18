
package org.broadinstitute.dsm.model.patch.process;

import lombok.Data;

@Data
public class PatchPreProcessorPayload {

    private final String tableAlias;
    private final String parent;

    public static PatchPreProcessorPayload of(String tableAlias, String parent) {
        return new PatchPreProcessorPayload(tableAlias, parent);
    }
}