package org.broadinstitute.ddp.util;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {
    public static <T> Stream<T> throwIfEmpty(Stream<T> stream, Supplier<RuntimeException> supplier) {
        Iterator<T> iterator = stream.iterator();
        if (iterator.hasNext()) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
        } else {
            throw supplier.get();
        }
    }

    public static <T> Stream<T> defaultIfEmpty(Stream<T> stream, Supplier<T> supplier) {
        Iterator<T> iterator = stream.iterator();
        if (iterator.hasNext()) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
        } else {
            return Stream.of(supplier.get());
        }
    }
}
