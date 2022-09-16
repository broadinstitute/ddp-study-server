
package org.broadinstitute.dsm.util;

import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

// This class will show the typical usage patterns for Try<T> data structure
public class TryTest {

    @Test
    public void ifThrowsSingleCatchAndThenRun() {

        // will catch ArithmeticException and run the Consumer<ArithmeticException> afterwards
        AtomicBoolean caught = new AtomicBoolean(false);
        Try.evaluate(() -> 42 / 0)
                .catchAndThenRun(error -> {
                    caught.set(true);
                    System.out.println("error");
                    System.out.println(error.getMessage());
                }, ArithmeticException.class);
        Assert.assertTrue(caught.get());

        caught.set(false);

        // will catch StringIndexOutOfBoundsException and run the Consumer<StringIndexOutOfBoundsException> afterwards
        Try.evaluate(() -> "String".substring(0, 10000))
                .catchAndThenRun(error -> {
                    caught.set(true);
                    System.out.println("error");
                    System.out.println(error.getMessage());
                }, StringIndexOutOfBoundsException.class);
        Assert.assertTrue(caught.get());

        caught.set(false);

        // will not catch StringIndexOutOfBoundsException and thereby won't run the Consumer<StringIndexOutOfBoundsException> afterwards
        Try.evaluate(() -> "String".substring(0, 3))
                .catchAndThenRun(error -> {
                    caught.set(true);
                    System.out.println("error");
                    System.out.println(error.getMessage());
                }, StringIndexOutOfBoundsException.class);

        Assert.assertFalse(caught.get());
    }

    @Test
    public void ifThrowsSingleCatchAndThenGet() {

        // will catch ArithmeticException and run the Function<ArithmeticException, Integer> afterwards
        Integer res1 = Try.evaluate(() -> 50 / 0)
                .catchAndThenGet(error -> {
                    System.out.println("Could not divide 50 by 5 so returning just 1");
                    return 1;
                }, ArithmeticException.class);

        Assert.assertEquals(res1, Integer.valueOf(1));

        // will not catch ArithmeticException and thereby won't run the Function<ArithmeticException, Integer> afterwards
        // will only return the successful division value, which is 20
        Integer res2 = Try.evaluate(() -> 100 / 5)
                .catchAndThenGet(error -> {
                    System.out.println("this won't run here");
                    return 100;
                }, ArithmeticException.class);

        Assert.assertEquals(res2, Integer.valueOf(20));

        // will catch RuntimeException but does not know how to handle it
        // so it throws NoSuchElementException
        try {
            Try.evaluate(() -> {
                throw new RuntimeException();
            }).catchAndThenGet(err -> {
                throw new NoSuchElementException();
            }, RuntimeException.class);
        } catch (NoSuchElementException nse) {
            System.out.println("caught NoSuchElementException");
            Assert.assertTrue(true);
        }

    }

    @Test
    public void fold() {

        // 42 / 1 will be successful so it will run onSuccess function,
        // onSuccess function returns the identity which will be 42.
        Integer r1 = Try.evaluate(() -> 42 / 1)
                .fold(Function.identity(), 100);

        Assert.assertEquals(Integer.valueOf(42), r1);

        // 42 / 0 will fail so it will return default value,
        // default value is 0
        Integer r2 = Try.evaluate(() -> 42 / 0)
                .fold(i -> i + 1, 0);

        Assert.assertEquals(r2, Integer.valueOf(0));

        // "String".substring(0, 10000) will fail so it will return default value,
        // default value will be "String"
        String r3 = Try.evaluate(() -> "String".substring(0, 10000))
                .fold(str -> str, "String");

        Assert.assertEquals(r3, "String");

        // "String".substring(0, 3) will be successful so it will run the onSuccess function,
        // onSuccess function will return the concatenated value which will be "StrStr"
        String r4 = Try.evaluate(() -> "String".substring(0, 3))
                .fold(str -> str.concat(str), "String");

        Assert.assertEquals(r4, "StrStr");

    }

    @Test
    public void finalizeWith() {

        AtomicBoolean finalizerRan = new AtomicBoolean(false);

        // 42 / 1 will be successful so it will print Division was successful
        Try.evaluate(() -> 42 / 1)
                .finalizeWith(() -> {
                    System.out.println("Division was successful");
                    finalizerRan.set(true);
                }, () -> System.out.println("Division was unsuccessful"));

        Assert.assertTrue(finalizerRan.get());

        finalizerRan.set(false);

        // 42 / 0 will fail so it will print "Division was unsuccessful"
        Try.evaluate(() -> 42 / 0)
                .finalizeWith(() -> System.out.println("Division was successful"), () -> {
                    System.out.println("Division was unsuccessful");
                    finalizerRan.set(true);
                });

        Assert.assertTrue(finalizerRan.get());

    }

    // If it throws either ClassCastException or NullPointerException we will return 100
    @Test
    public void ifThrowsAnyCatchAndThenGet() {
        Random rand = new Random();
        for (int i = 0; i < 100; i++) {
            int res = Try.evaluate(() -> {
                int number = rand.nextInt(10);
                if (number > 5) {
                    throw new ClassCastException();
                } else {
                    throw new NullPointerException();
                }
            }).catchAndThenGet(err -> 100, ClassCastException.class, NullPointerException.class);
            Assert.assertEquals(res, 100);
        }
    }

    // Sometime we have no idea how to handle situation, so we throw exception!
    // The below is the one
    @Test
    public void ifThrowsAnyCatchAndThenRun() {
        AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < 100; i++) {
            Try.evaluate(() -> {
                Random rand = new Random();
                int value = rand.nextInt(10);
                if (value > 5) {
                    throw new ClassCastException();
                } else {
                    throw new NullPointerException();
                }
            }).catchAndThenRun(err -> {
                System.out.println("Caught the error");
                count.incrementAndGet();
            }, ClassCastException.class, NullPointerException.class);
        }
        Assert.assertEquals(count.get(), 100);
    }

    @Test
    public void uncaughtException() {
        try {
            Try.evaluate(() -> 42 / 0)
                    .catchAndThenGet(err -> 5, NullPointerException.class);
        } catch (Try.UncaughtException tue) {
            System.out.println("caught");
            Assert.assertTrue(true);
        }
    }

}
