package org.broadinstitute.dsm.model.participant;

import lombok.Getter;
import lombok.Setter;
import spark.QueryParamsMap;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class DownloadParticipantListParams {
    public static final List<String> allowedFileFormats = Arrays.asList("tsv", "xlsx");
    private boolean humanReadable = true;
    private boolean onlyMostRecent = false;
    private String fileFormat = "tsv";

    public DownloadParticipantListParams(QueryParamsMap paramMap) {
        if (paramMap.hasKey("fileFormat")) {
            String fileFormatParam = paramMap.get("fileFormat").value();
            if (allowedFileFormats.contains(fileFormatParam)) {
                fileFormat = fileFormatParam;
            }
        }
        if (paramMap.hasKey("humanReadable")) {
            humanReadable = Boolean.valueOf(paramMap.get("humanReadable").value());
        }
        if (paramMap.hasKey("onlyMostRecent")) {
            onlyMostRecent = Boolean.valueOf(paramMap.get("onlyMostRecent").value());
        }
    }
}
