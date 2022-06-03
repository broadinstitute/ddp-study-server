package org.broadinstitute.dsm.model.elastic.export.generate;

public class MappingGeneratorFactory implements GeneratorFactory {
    @Override
    public BaseGenerator make(PropertyInfo propertyInfo) {
        return propertyInfo.isCollection()
                ? new CollectionMappingGenerator()
                : new SingleMappingGenerator();
    }
}
