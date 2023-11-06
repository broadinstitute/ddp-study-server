package org.broadinstitute.ddp.cache;

import org.junit.Assert;
import org.junit.Test;

public class CacheServiceTest {

    @Test
    public void testCacheStartup() {
        CacheService cacheService = null; {
            try {
                CacheService.getInstance();
            } catch (NoSuchMethodError e) {
                e.printStackTrace();
                Assert.fail("Transitive dependency error with redisson");
            }
        }
        Assert.assertNotNull(cacheService);
    }

    /**
     * java.lang.NoSuchMethodError: 'com.fasterxml.jackson.core.io.ContentReference com.fasterxml.jackson.dataformat.yaml.YAMLFactory._createContentReference(java.lang.Object)'
     *
     * 	at com.fasterxml.jackson.dataformat.yaml.YAMLFactory.createParser(YAMLFactory.java:413)
     * 	at com.fasterxml.jackson.dataformat.yaml.YAMLFactory.createParser(YAMLFactory.java:387)
     * 	at com.fasterxml.jackson.dataformat.yaml.YAMLFactory.createParser(YAMLFactory.java:15)
     * 	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3548)
     * 	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3516)
     * 	at org.redisson.config.ConfigSupport.fromYAML(ConfigSupport.java:176)
     * 	at org.redisson.config.Config.fromYAML(Config.java:694)
     * 	at org.redisson.jcache.JCachingProvider.loadConfig(JCachingProvider.java:102)
     * 	at org.redisson.jcache.JCachingProvider.getCacheManager(JCachingProvider.java:75)
     * 	at org.redisson.jcache.JCachingProvider.getCacheManager(JCachingProvider.java:143)
     * 	at org.broadinstitute.ddp.cache.CacheService.buildCacheManager(CacheService.java:88)
     * 	at org.broadinstitute.ddp.cache.CacheService.<init>(CacheService.java:70)
     * 	at org.broadinstitute.ddp.cache.CacheService.getInstance(CacheService.java:52)
     * 	at org.broadinstitute.ddp.cache.CacheServiceTest.testCacheStartup(CacheServiceTest.java:10)
     * 	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
     * 	at java.base/java.lang.reflect.Method.invoke(Method.java:578)
     * 	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
     * 	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
     * 	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
     * 	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
     * 	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
     * 	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
     * 	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
     * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
     * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
     * 	at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
     * 	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
     * 	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
     * 	at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
     * 	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
     * 	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
     * 	at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
     * 	at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
     * 	at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:69)
     * 	at com.intellij.rt.junit.IdeaTestRunner$Repeater$1.execute(IdeaTestRunner.java:38)
     * 	at com.intellij.rt.execution.junit.TestsRepeater.repeat(TestsRepeater.java:11)
     * 	at com.intellij.rt.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:35)
     * 	at com.intellij.rt.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:235)
     * 	at com.intellij.rt.junit.JUnitStarter.main(JUnitStarter.java:54)
     */
}
