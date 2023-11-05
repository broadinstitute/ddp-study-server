package org.broadinstitute.ddp.studybuilder.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateUpdateInfo {

    String searchString;
    String replaceString;
    TemplateActionType action;

}
