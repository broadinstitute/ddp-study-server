
package org.broadinstitute.dsm.util;

import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

// This class will show the typical usage patterns for Try<T> data structure
public class TryTest {

    @Test
    public void ifThrowsCatchAndThenRun() {

        // will catch ArithmeticException and run the Consumer<ArithmeticException> afterwards
        Try.evaluate(() -> 42 / 0)
                .ifThrowsCatchAndThenRun(ArithmeticException.class, error -> {
                    System.out.println("error");
                    System.out.println(error.getMessage());
                });


        // will catch StringIndexOutOfBoundsException and run the Consumer<StringIndexOutOfBoundsException> afterwards
        Try.evaluate(() -> "String".substring(0, 10000))
                .ifThrowsCatchAndThenRun(StringIndexOutOfBoundsException.class, error -> {
                    System.out.println("error");
                    System.out.println(error.getMessage());
                });

        // will not catch StringIndexOutOfBoundsException and thereby won't run the Consumer<StringIndexOutOfBoundsException> afterwards
        Try.evaluate(() -> "String".substring(0, 3))
                .ifThrowsCatchAndThenRun(StringIndexOutOfBoundsException.class, error -> {
                    System.out.println("error");
                    System.out.println(error.getMessage());
                });
    }

    @Test
    public void ifThrowsThenGet() {

        // will catch ArithmeticException and run the Function<ArithmeticException, Integer> afterwards
        Integer res1 = Try.evaluate(() -> 50 / 0)
                .ifThrowsThenGet(ArithmeticException.class, error -> {
                    System.out.println("Could not divide 50 by 5 so returning just 1");
                    return 1;
                });

        Assert.assertEquals(res1, Integer.valueOf(1));

        // will not catch ArithmeticException and thereby won't run the Function<ArithmeticException, Integer> afterwards
        // will only return the successful division value, which is 20
        Integer res2 = Try.evaluate(() -> 100 / 5)
                .ifThrowsThenGet(ArithmeticException.class, error -> {
                    System.out.println("this won't run here");
                    return 100;
                });

        Assert.assertEquals(res2, Integer.valueOf(20));

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

        // 42 / 1 will be successful so it will print Division was successful
        Try.evaluate(() -> 42 / 1)
                .finalizeWith(() -> System.out.println("Division was successful"), () -> System.out.println("Division was unsuccessful"));

        // 42 / 0 will fail so it will print "Division was unsuccessful"
        Try.evaluate(() -> 42 / 0)
                .finalizeWith(() -> System.out.println("Division was successful"), () -> System.out.println("Division was unsuccessful"));

    }
}
