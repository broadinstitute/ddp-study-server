package org.broadinstitute.dsm.model.elastic.export.process;

import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;

public interface ProcessorFactory {
    BaseProcessor make(BaseGenerator.PropertyInfo propertyInfo);
}
