package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class StudyI18nDto {
    String languageCode;
    String name;
    String summary;
}
