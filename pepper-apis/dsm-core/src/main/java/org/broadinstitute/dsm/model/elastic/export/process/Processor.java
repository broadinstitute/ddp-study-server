package org.broadinstitute.dsm.model.elastic.export.process;

import java.util.List;
import java.util.Map;

public interface Processor {
    List<Map<String, Object>> process();
}
