package org.broadinstitute.dsm.export;

import org.broadinstitute.dsm.db.DDPInstance;

public interface Exporter {
    void export(DDPInstance instance);
}
