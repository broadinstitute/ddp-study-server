package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class MultiOptionsSourceGeneratorTest {

    @Test
    public void simpleMultiOptionsToMap() {
        var generator = new MultiOptionsSourceGenerator();
        var actual = generator.toMap("Met sites at Mets dx", "[Bones, Cones]");
        var expected = Map.of("metSitesAtMetsDx", Map.of("values", List.of("Bones", "Cones")));
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void complexMultiOptionsToMap() {
        var generator = new MultiOptionsSourceGenerator();
        var actual = generator.toMap("Met sites at Mets dx",
                "{\"other\":\"bones and some other as well\",\"Met sites at Mets dx\":[\" Bone\",\"other\"]}");
        var expected =
                Map.of("metSitesAtMetsDx", Map.of("other", "bones and some other as well", "values", List.of(" Bone", "other")));
        Assert.assertEquals(expected, actual);
    }


}