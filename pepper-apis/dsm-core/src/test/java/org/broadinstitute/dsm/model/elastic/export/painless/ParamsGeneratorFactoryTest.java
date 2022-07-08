package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.junit.Assert;
import org.junit.Test;

public class ParamsGeneratorFactoryTest {


    @Test
    public void instance() {
        ParamsGeneratorFactory paramsGeneratorFactory = new ParamsGeneratorFactory(new CohortTag(), StringUtils.EMPTY);
        Assert.assertTrue(paramsGeneratorFactory.instance() instanceof ParamsGenerator);

        paramsGeneratorFactory = new ParamsGeneratorFactory(List.of(new CohortTag()), StringUtils.EMPTY);
        Assert.assertTrue(paramsGeneratorFactory.instance() instanceof CollectionParamsGenerator);
    }


}
