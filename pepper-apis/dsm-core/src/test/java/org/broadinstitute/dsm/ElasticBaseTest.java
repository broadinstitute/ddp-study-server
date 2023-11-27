package org.broadinstitute.dsm;

import org.broadinstitute.dsm.util.ElasticTestContainer;
import org.junit.BeforeClass;

public abstract class ElasticBaseTest {

    @BeforeClass
    public static void initElasticContainer() {
        ElasticTestContainer.initialize();
    }
}
