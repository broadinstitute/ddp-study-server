package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ColumnValue {
    private Object object;
    private final Alias alias;
    public ColumnValue(Alias alias, Object object) {
        this.object = object;
        this.alias = alias;
    }

    public <T> T getObject() {
        return (T) object;
    }

    public int getColumnsSize() {
        if (isCollection()) {
            return Math.max(1, ((Collection<?>)object).size());
        } else {
            return 1;
        }
    }

    public boolean isCollection() {
        return object instanceof Collection;
    }

    public Alias getAlias() {
        return alias;
    }

    public <T> Iterator<T> iterator () {
        if (isCollection()) {
            return ((Collection<T>) object).iterator();
        }
        throw new RuntimeException("Value not iterable");
    }

    public void appendEmptyStrings(int size) {
        if (object instanceof Collection) {
            Collection<String> collection = (Collection<String>) object;
            this.object = Stream.concat(collection.stream(), IntStream.range(0, size).mapToObj(s -> StringUtils.EMPTY))
                    .collect(Collectors.toList());
        }
    }

}
