package org.broadinstitute.dsm.model.ddp;

import lombok.Data;

@Data
public class PreferredLanguage {

    private String languageCode;
    private String displayName;
    private boolean isDefault;
}
