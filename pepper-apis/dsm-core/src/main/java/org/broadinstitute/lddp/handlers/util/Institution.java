package org.broadinstitute.lddp.handlers.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;

public class Institution {
    private String id;
    private String type;

    public Institution() {
    }

    public Institution(@NonNull String id, @NonNull String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public enum InstitutionType {
        INSTITUTION, PHYSICIAN, INITIAL_BIOPSY
    }

    public static void verify(Institution institution) {
        if (StringUtils.isBlank(institution.getId())) {
            throw new DSMBadRequestException("Missing required id in institution");
        }
        if (StringUtils.isBlank(institution.getType())) {
            throw new DSMBadRequestException("Missing required type in institution");
        }
    }
}
