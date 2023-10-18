package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.FIELD_SETTINGS_ALIAS;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.SystemUtil;

@Slf4j
public class OncHistoryTemplateService {

    private final String realm;
    private final StudyColumnsProvider studyColumnsProvider;
    protected static final String FULL_DATE_DESC = "Full: 2022-12-5, 12-5-2022, 12/5/2022, 2022/12/5.";
    protected static final String PARTIAL_DATE_DESC = "Partial: 2021-10, 10/2021, 2021.";
    // width is expressed as 1/256 of a character
    private static final int DEFAULT_COL_WIDTH = 256 * 15;
    private static final int HEADER_COL_WIDTH = 256 * 15;
    private static final int WIDE_COL_WIDTH = 256 * 30;
    private static final short LARGE_FONT = 20 * 13;
    private final String dateDescription;
    private final Map<String, String> recordIdColumn;
    private boolean initialized;
    private Map<String, List<String>> columnOptions;
    private Map<String, String> columnDescriptions;
    private Map<String, OncHistoryUploadColumn> studyColumns;
    private String realmDisplayName;
    private CellStyle wrapStyle;
    private CellStyle boldStyle;
    private CellStyle largeBoldStyle;


    public OncHistoryTemplateService(String realm, StudyColumnsProvider studyColumnsProvider) {
        this.realm = realm;
        this.studyColumnsProvider = studyColumnsProvider;
        this.dateDescription = String.format("%s%n%n%s", FULL_DATE_DESC, PARTIAL_DATE_DESC);
        this.recordIdColumn = createRecordIdColumn();
        this.initialized = false;
    }

    protected static Map<String, String> createRecordIdColumn() {
        Map<String, String> col = new HashMap<>();
        col.put("header", OncHistoryUploadService.ID_COLUMN);
        col.put("description", "Short ID");
        col.put("type", "Text");
        col.put("notes", "Participant short ID");
        return col;
    }

    protected void initialize() {
        if (initialized) {
            return;
        }
        int ddpInstanceId;
        try {
            DDPInstanceDto ddpInstance = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm).orElseThrow();
            ddpInstanceId = ddpInstance.getDdpInstanceId();
            realmDisplayName = ddpInstance.getDisplayName();
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid realm: " + realm);
        }

        studyColumns = studyColumnsProvider.getColumnsForStudy(realm);
        columnOptions = OncHistoryUploadService.getPicklists(ddpInstanceId);
        columnDescriptions = getColumnDescriptions(ddpInstanceId);

        initialized = true;
    }

    /**
     * Write onc history data dictionary to output stream
     */
    public void writeDictionary(OutputStream os) {
        initialize();

        log.info("Creating onc history upload data dictionary");
        List<OncHistoryUploadColumn> uploadColumns = new ArrayList<>(studyColumns.values());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10)) {
            createSheet(workbook, uploadColumns);
            workbook.write(os);
            workbook.dispose();
        } catch (Exception e) {
            throw new DsmInternalError("Error creating Excel file", e);
        }
    }

    /**
     * Write onc history upload template (header row) to output stream
     */
    public void writeTemplate(OutputStream os) {
        initialize();

        log.info("Creating onc history upload template");
        List<String> headers = new ArrayList<>();
        // RECORD_ID is not part of studyColumns
        headers.add(OncHistoryUploadService.ID_COLUMN);
        headers.addAll(studyColumns.keySet());

        PrintWriter printWriter = new PrintWriter(os);
        printWriter.println(String.join(SystemUtil.TAB_SEPARATOR, headers));
        printWriter.flush();
    }

    public String getDictionaryFileName() {
        initialize();
        return String.format("OncHistoryUploadDictionary - %s.xlsx", realmDisplayName);
    }

    public String getTemplateFileName() {
        initialize();
        return String.format("OncHistoryUploadTemplate - %s.txt", realmDisplayName);
    }

    protected void createSheet(SXSSFWorkbook workbook, List<OncHistoryUploadColumn> uploadColumns) {
        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        boldStyle = createBoldStyle(workbook);
        largeBoldStyle = createLargeBoldStyle(workbook);

        SXSSFSheet sheet = workbook.createSheet("Onc History Upload Template");

        sheet.setColumnWidth(0, HEADER_COL_WIDTH);
        sheet.setColumnWidth(1, DEFAULT_COL_WIDTH);
        int ind = 2;
        for (var col: uploadColumns) {
            switch (col.getParseType()) {
                case "o":
                case "d":
                    sheet.setColumnWidth(ind, WIDE_COL_WIDTH);
                    break;
                default:
                    sheet.setColumnWidth(ind, DEFAULT_COL_WIDTH);
                    break;
            }
            ind++;
        }

        int rowNum = 0;
        SXSSFRow row = sheet.createRow(rowNum++);
        writeRow(createHeaderRow(uploadColumns, "Column header"), row, true);

        writeRow(createDescRow(uploadColumns, "Description"), sheet.createRow(rowNum++), false);
        writeRow(createTypeRow(uploadColumns, "Column type"), sheet.createRow(rowNum++), false);
        writeRow(createNotesRow(uploadColumns, "Notes"), sheet.createRow(rowNum), false);

        sheet.createFreezePane(1, 0);
    }

    private CellStyle createBoldStyle(SXSSFWorkbook workbook) {
        CellStyle cs = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        cs.setFont(font);
        return cs;
    }

    private CellStyle createLargeBoldStyle(SXSSFWorkbook workbook) {
        CellStyle cs = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeight(LARGE_FONT);
        cs.setFont(font);
        return cs;
    }

    protected List<String> createHeaderRow(List<OncHistoryUploadColumn> uploadColumns, String rowHeader) {
        List<String> header = new ArrayList<>();
        header.add(rowHeader);
        header.add(recordIdColumn.get("header"));
        header.addAll(uploadColumns.stream().map(OncHistoryUploadColumn::getColumnAlias).collect(Collectors.toList()));
        return header;
    }


    protected List<String> createDescRow(List<OncHistoryUploadColumn> uploadColumns, String rowHeader) {
        List<String> row = new ArrayList<>();
        row.add(rowHeader);
        row.add(recordIdColumn.get("description"));
        for (var col: uploadColumns) {
            if (col.getTableAlias().equals(DDP_ONC_HISTORY_DETAIL_ALIAS)) {
                row.add(col.getDescription());
            } else {
                if (!col.getTableAlias().equals(FIELD_SETTINGS_ALIAS)) {
                    throw new DsmInternalError("Invalid table alias for OncHistoryUploadColumn. Column=" + col.getColumnName());
                }
                String desc = columnDescriptions.get(col.getColumnName());
                if (desc == null) {
                    throw new DsmInternalError("Null description for OncHistoryUploadColumn. Column=" + col.getColumnName());
                }
                row.add(desc);
            }
        }
        return row;
    }

    List<String> createTypeRow(List<OncHistoryUploadColumn> uploadColumns, String rowHeader) {
        List<String> row = new ArrayList<>();
        row.add(rowHeader);
        row.add(recordIdColumn.get("type"));
        for (var col: uploadColumns) {
            String type;
            switch (col.getParseType()) {
                case "s":
                    type = "Text";
                    break;
                case "d":
                    type = "Date";
                    break;
                case "o":
                    type = "Select";
                    break;
                case "n":
                    type = "Number";
                    break;
                default:
                    throw new DsmInternalError("Invalid parse type for OncHistoryUploadColumn. Column=" + col.getColumnName());
            }
            row.add(type);
        }
        return row;
    }

    List<String> createNotesRow(List<OncHistoryUploadColumn> uploadColumns, String rowHeader) {
        List<String> row = new ArrayList<>();
        row.add(rowHeader);
        row.add(recordIdColumn.get("notes"));
        for (var col: uploadColumns) {
            row.add(createNote(col));
        }
        return row;
    }


    protected String createNote(OncHistoryUploadColumn uploadColumn) {
        String type = uploadColumn.getParseType();
        if (type.equals("d")) {
            return dateDescription;
        }
        if (type.equals("o")) {
            String colName = uploadColumn.getColumnAlias();
            List<String> options = columnOptions.get(colName);
            if (CollectionUtils.isEmpty(options)) {
                String msg = String.format("No options found for column %s in realm %s", colName, realm);
                throw new DsmInternalError(msg);
            }
            return formatOptions(options);
        }
        return "";
    }

    protected static String formatOptions(List<String> options) {
        StringBuilder sb = new StringBuilder("Valid values:");
        for (String option: options) {
            sb.append("\n").append(option);
        }
        return sb.toString();
    }

    protected void writeRow(List<String> values, Row row, boolean boldRow) {
        IntStream.range(0, values.size()).forEachOrdered(i -> {
            Cell c = row.createCell(i);
            c.setCellValue(values.get(i));
            c.setCellStyle(wrapStyle);
            if (i == 0) {
                c.setCellStyle(largeBoldStyle);
            } else if (boldRow) {
                c.setCellStyle(boldStyle);
            }
        });
    }

    protected static Map<String, String> getColumnDescriptions(int ddpInstanceId) {
        FieldSettingsDao dao = FieldSettingsDao.of();
        List<FieldSettingsDto> settings = dao.getFieldSettingsByInstanceId(ddpInstanceId);
        return settings.stream().filter(s -> s.getFieldType().equals(DDP_ONC_HISTORY_DETAIL_ALIAS))
                .collect(Collectors.toMap(FieldSettingsDto::getColumnName, FieldSettingsDto::getColumnDisplay));
    }

    protected Map<String, List<String>> getColumnOptions() {
        return columnOptions;
    }
}
