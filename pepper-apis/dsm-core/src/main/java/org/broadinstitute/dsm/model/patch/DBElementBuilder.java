package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.structure.DBElement;

public interface DBElementBuilder {
    DBElement fromName(String name);
}
