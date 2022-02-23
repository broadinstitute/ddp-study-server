package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.Tissue;

public class SourceGeneratorFactory implements GeneratorFactory {

    @Override
    public BaseGenerator make(BaseGenerator.PropertyInfo propertyInfo) {
        BaseGenerator generator;
        if (propertyInfo.isCollection()) {
            if (Tissue.class.isAssignableFrom(propertyInfo.getPropertyClass())) {
                generator = new TissueSourceGenerator();
            } else {
                generator = new CollectionSourceGenerator();
            }
        } else {
            generator = new SingleSourceGenerator();
        }
        return generator;
    }
}
