package org.broadinstitute.dsm.files.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.FileWrongSeparator;

public abstract class AbstractRecordsParser<T> {

    protected final String fileContent;
    protected final String regexSeparator;

    protected List<String> fileHeaders;

    protected AbstractRecordsParser(String fileContent, String regexSeparator) {
        this.fileContent = fileContent;
        this.regexSeparator = regexSeparator;
    }

    public String getRegexSeparator() {
        return regexSeparator;
    }

    public List<T> parseToObjects() {
        if (fileContent == null) {
            throw new RuntimeException("File is empty");
        }
        String[] rows = fileContent.split(System.lineSeparator());
        if (rows.length < 2) {
            throw new RuntimeException("Text file does not contain any values");
        }
        String headerRow = rows[0];
        if (!headerRow.contains(regexSeparator)) {
            throw new FileWrongSeparator(String.format("Headers are not separated by %s", regexSeparator));
        }
        fileHeaders = Arrays.asList(headerRow.trim().split(regexSeparator));
        Optional<String> maybeMissingHeader = findMissingHeaderIfAny(fileHeaders);
        if (maybeMissingHeader.isPresent()) {
            throw new FileColumnMissing("File is missing the column: " + maybeMissingHeader.get());
        } else {
            String[] records = Arrays.copyOfRange(rows, 1, rows.length);
            return transformRecordsToList(records);
        }
    }

    public Optional<String> findMissingHeaderIfAny(List<String> headers) {
        List<String> expectedHeaders = getExpectedHeaders();
        return expectedHeaders.equals(headers)
                ? Optional.empty()
                : expectedHeaders.stream()
                .filter(header -> !headers.contains(header))
                .findFirst();
    }

    public abstract List<String> getExpectedHeaders();

    List<T> transformRecordsToList(String[] records) {
        return Arrays.stream(records)
                .map(record -> transformMapToObject(transformRecordToMap(record)))
                .collect(Collectors.toList());
    }

    Map<String, String> transformRecordToMap(String record) {
        List<String> records = Arrays.asList(record.trim().split(regexSeparator));
        return IntStream.range(0, fileHeaders.size()).boxed()
                .collect(Collectors.toMap(fileHeaders::get, records::get));
    }

    public abstract T transformMapToObject(Map<String, String> map);
}
