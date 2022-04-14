package org.broadinstitute.dsm.files;

import java.io.InputStream;
import java.util.Map;

public interface BasicProcessor {

    InputStream generateStream(Map<String, Object> valueMap);

    String getFileName();
}
