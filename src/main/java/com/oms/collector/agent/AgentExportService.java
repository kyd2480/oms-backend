package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentExportRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentExportService {

    private final OmsAgentToolService toolService;

    public byte[] export(AgentExportRequest request) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            List<Map<String, Object>> toolCalls = request.toolCalls() != null ? request.toolCalls() : List.of();
            XSSFSheet summarySheet = workbook.createSheet("Summary");
            int summaryRow = 0;
            summaryRow = writeRow(summarySheet, summaryRow, List.of("Title", valueOr(request.title(), "OMS Agent Export")));
            summaryRow = writeRow(summarySheet, summaryRow, List.of("ExportedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            summaryRow = writeRow(summarySheet, summaryRow, List.of("ToolCallCount", String.valueOf(toolCalls.size())));
            summaryRow++;

            int index = 1;
            for (Map<String, Object> toolCall : toolCalls) {
                String name = String.valueOf(toolCall.getOrDefault("name", "unknown"));
                Map<String, Object> args = asMap(toolCall.get("arguments"));
                Map<String, Object> result = toolService.executeTool(name, args);

                summaryRow = writeRow(summarySheet, summaryRow, List.of("Tool " + index, name));
                XSSFSheet sheet = workbook.createSheet(sheetName(index, name));
                writeResultSheet(sheet, name, args, result);
                index++;
            }

            autosize(summarySheet, 2);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeResultSheet(XSSFSheet sheet, String name, Map<String, Object> args, Map<String, Object> result) {
        int row = 0;
        row = writeRow(sheet, row, List.of("Tool", name));
        row = writeRow(sheet, row, List.of("Arguments", args.toString()));
        row++;

        boolean tabularWritten = false;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                row = writeRow(sheet, row, List.of(entry.getKey()));
                row = writeTable(sheet, row, castList(list));
                row++;
                tabularWritten = true;
            } else {
                row = writeRow(sheet, row, List.of(entry.getKey(), stringify(entry.getValue())));
            }
        }

        if (!tabularWritten && result.isEmpty()) {
            writeRow(sheet, row, List.of("Result", "No data"));
        }

        autosize(sheet, 8);
    }

    private int writeTable(XSSFSheet sheet, int startRow, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return writeRow(sheet, startRow, List.of("No data"));
        }
        List<String> headers = rows.get(0).keySet().stream().map(String::valueOf).toList();
        int rowIndex = writeRow(sheet, startRow, headers);
        for (Map<String, Object> item : rows) {
            List<String> values = headers.stream()
                .map(header -> stringify(item.get(header)))
                .toList();
            rowIndex = writeRow(sheet, rowIndex, values);
        }
        return rowIndex;
    }

    private int writeRow(XSSFSheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values.get(i));
        }
        return rowIndex;
    }

    private void autosize(XSSFSheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(List<?> list) {
        return (List<Map<String, Object>>) list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> casted = new LinkedHashMap<>();
            map.forEach((k, v) -> casted.put(String.valueOf(k), v));
            return casted;
        }
        return Map.of();
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) return list.toString();
        if (value instanceof Map<?, ?> map) return map.toString();
        return String.valueOf(value);
    }

    private String valueOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String sheetName(int index, String name) {
        String raw = index + "_" + name.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return raw.length() > 31 ? raw.substring(0, 31) : raw;
    }
}
