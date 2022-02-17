package org.broadinstitute.dsm.model.elastic.export.generate;

public interface GeneratorFactory {
    BaseGenerator make(BaseGenerator.PropertyInfo propertyInfo);
}
