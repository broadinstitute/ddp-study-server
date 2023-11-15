package org.broadinstitute.ddp.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.RedisConnectionException;

@Slf4j
public class CacheServiceTest {

    private static final String AARCH64 = "aarch64";

    @Test
    public void testGetInstanceDoesNotThrowNoSuchMethodError() {
        try {
            CacheService.getInstance();
        } catch (RedisConnectionException e) {
            log.info("As expected, cannot connect to local instance that does not exist.");
        } catch (NoSuchFieldError | NoSuchMethodError e) {
            e.printStackTrace();
            Assert.fail("Could not get CacheService instance: " + e.getMessage());
        } catch (Throwable e) {
            e.printStackTrace();
            Assert.fail("Unexpected error when getting CacheService instance.");
        }
    }
}
