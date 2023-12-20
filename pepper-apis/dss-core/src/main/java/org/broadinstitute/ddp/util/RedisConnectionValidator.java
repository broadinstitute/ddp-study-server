package org.broadinstitute.ddp.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import redis.clients.jedis.Jedis;

@Slf4j
public class RedisConnectionValidator {
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
                log.debug("Redis ping ok");
            }
        } catch (RuntimeException e) {
            log.error("There was a problem reading/writing to Redis at {}:{}", host, port, e);
        }
    }

    public static void main(String[] args) {
        doTest("localhost", 6379);
    }
}
