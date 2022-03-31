package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ColumnValue {
    private Object object;
    private boolean isCollection;
    private final Alias alias;
    public ColumnValue(Alias alias, Object object) {
        this.object = object;
        this.alias = alias;
        if (object instanceof Collection) {
            isCollection = true;
        }
    }

    public <T> T getObject() {
        return (T) object;
    }

    public int getColumnsSize() {
        if (isCollection) {
            return Math.max(1, ((Collection<?>)object).size());
        } else {
            return 1;
        }
    }

    public boolean isCollection() {
        return isCollection;
    }

    public Alias getAlias() {
        return alias;
    }

    public <T> Iterator<T> iterator () {
        if (!isCollection) {
            throw new RuntimeException("Value not iterable");
        }
        return ((Collection<T>) object).iterator();
    }

    public void appendEmptyStrings(long size) {
        if (!isCollection) {
            throw new RuntimeException("Value not iterable");
        }
        Collection<String> collection = (Collection<String>) object;
        this.object = Stream.concat(collection.stream(), LongStream.range(0, size).mapToObj(s -> StringUtils.EMPTY))
                .collect(Collectors.toList());
    }

}
