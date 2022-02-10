package org.broadinstitute.dsm.model.elastic.export;

import java.util.Map;

public interface Exportable {

    void exportData(Map<String, Object> data);

    void exportMapping(Map<String, Object> mapping);
}
