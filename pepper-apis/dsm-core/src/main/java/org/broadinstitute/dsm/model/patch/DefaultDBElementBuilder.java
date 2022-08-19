package org.broadinstitute.dsm.model.patch;

import java.util.Objects;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.util.PatchUtil;

public class DefaultDBElementBuilder implements DBElementBuilder {

    @Override
    public DBElement fromName(String name) {
        DBElement dbElement = PatchUtil.getColumnNameMap().get(name);
        if (Objects.isNull(dbElement)) {
            throw new RuntimeException(String.format("could not build DBElement from %s", name));
        }
        return dbElement;
    }
}
