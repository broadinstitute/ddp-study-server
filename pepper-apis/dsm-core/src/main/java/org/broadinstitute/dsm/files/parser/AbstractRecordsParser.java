package org.broadinstitute.dsm.files.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.FileWrongSeparator;
import org.broadinstitute.dsm.model.Filter;

public abstract class AbstractRecordsParser<T> {

    private static final int HEADER_INDEX = 0;
    private static final int RECORDS_START_INDEX = 1;

    protected final String fileContent;
    protected final String regexSeparator;
    protected final List<String> expectedHeaders;

    protected List<String> actualHeaders;

    protected AbstractRecordsParser(String fileContent, String regexSeparator, HeadersProvider headersProvider) {
        this.fileContent = fileContent;
        this.regexSeparator = regexSeparator;
        this.expectedHeaders = headersProvider.provideHeaders();
    }

    public String getRegexSeparator() {
        return regexSeparator;
    }

    public List<T> parseToObjects() {
        String[] rows = fileContent.split(System.lineSeparator());
        if (rows.length < 2) {
            throw new RuntimeException("File does not contain any records");
        }
        String headerRow = rows[HEADER_INDEX];
        actualHeaders = Arrays.asList(headerRow.trim().split(regexSeparator));
        if (isFileSeparatedByWrongSeparator()) {
            throw new FileWrongSeparator(String.format("File headers are not separated by %s",
                    RegexSeparatorDictionary.describe(regexSeparator)));
        }
        Optional<String> maybeMissingHeader = findMissingHeaderIfAny(actualHeaders);
        if (maybeMissingHeader.isPresent()) {
            throw new FileColumnMissing("File is missing the column: " + maybeMissingHeader.get());
        } else {
            String[] records = Arrays.copyOfRange(rows, RECORDS_START_INDEX, rows.length);
            return transformRecordsToList(records);
        }
    }

    private boolean isFileSeparatedByWrongSeparator() {
        return actualHeaders.stream().anyMatch(header -> header.contains(Filter.SPACE));
    }

    public Optional<String> findMissingHeaderIfAny(List<String> extractedHeaders) {
        return expectedHeaders.equals(extractedHeaders)
                ? Optional.empty()
                : expectedHeaders.stream()
                .filter(header -> !extractedHeaders.contains(header))
                .findFirst();
    }

    List<T> transformRecordsToList(String[] records) {
        return Arrays.stream(records)
                .map(this::transformRecordToMap)
                .map(this::transformMapToObject)
                .collect(Collectors.toList());
    }

    Map<String, String> transformRecordToMap(String record) {
        List<String> records = Arrays.asList(record.trim().split(regexSeparator));
        return IntStream.range(0, actualHeaders.size())
                .boxed()
                .collect(Collectors.toMap(actualHeaders::get, records::get));
    }

    public abstract T transformMapToObject(Map<String, String> recordAsMap);

}
