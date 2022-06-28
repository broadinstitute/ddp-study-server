package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.Tissue;
import org.junit.Assert;
import org.junit.Test;

public class SourceGeneratorFactoryTest {

    @Test
    public void processMake() {
        SourceGeneratorFactory sourceGeneratorFactory = new SourceGeneratorFactory();
        PropertyInfo propertyInfo = new PropertyInfo(Tissue.class, true);
        BaseGenerator generator = sourceGeneratorFactory.make(propertyInfo);
        Assert.assertEquals(TissueSourceGenerator.class, generator.getClass());
    }

}
