package org.broadinstitute.ddp.util;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class RedisConnectionValidator {
    private static final Logger LOG = LoggerFactory.getLogger(RedisConnectionValidator.class);

    public static void doTest() {
        String redisAddress = ConfigManager.getInstance().getConfig().getString(ConfigFile.REDIS_SERVER_ADDRESS);
        String host = StringUtils.substringBetween(redisAddress, "//", ":");
        String portString = StringUtils.substringAfterLast(redisAddress, ":");
        doTest(host, Integer.parseInt(portString));
    }

    public static void doTest(String host, int port) {
        String testHash = "testhash";
        String valToWrite = Math.random() + "";
        try (var jedis = new Jedis(host, port)) {
            jedis.hset(testHash, "hello", valToWrite);

            String valRead = jedis.hget(testHash, "hello");
            if (valToWrite.equals(valRead)) {
                LOG.info("Redis is fine");
            }
        } catch (RuntimeException e) {
            LOG.error("There was a problem reading/writing to Redis", e);
        }
    }

    public static void main(String[] args) {
        doTest("localhost", 6379);
    }
}
