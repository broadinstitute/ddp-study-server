package org.broadinstitute.dsm.model.elastic.export.process;

import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;

public interface ProcessorFactory {
    BaseProcessor make(PropertyInfo propertyInfo);
}
