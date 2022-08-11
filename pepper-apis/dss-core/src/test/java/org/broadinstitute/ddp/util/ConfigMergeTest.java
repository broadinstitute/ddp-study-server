package org.broadinstitute.ddp.util;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class ConfigMergeTest {
    @Test
    public void testOverride() {
        final var baseConfig = ConfigFactory.empty()
                .withValue("common", ConfigValueFactory.fromAnyRef("a"))
                .withValue("a", ConfigValueFactory.fromAnyRef("a"));

        final var alternativeConfig = ConfigFactory.empty()
                .withValue("common", ConfigValueFactory.fromAnyRef("b"))
                .withValue("b", ConfigValueFactory.fromAnyRef("b"));

        final var mergedConfig = baseConfig.withFallback(alternativeConfig).resolve();

        Assert.assertEquals(ConfigValueFactory.fromAnyRef("a"), mergedConfig.getValue("common"));
        Assert.assertEquals(ConfigValueFactory.fromAnyRef("a"), mergedConfig.getValue("a"));
        Assert.assertEquals(ConfigValueFactory.fromAnyRef("b"), mergedConfig.getValue("b"));
    }
}
