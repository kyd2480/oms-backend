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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentExportService {

    private final OmsAgentToolService toolService;

    public byte[] export(AgentExportRequest request) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            List<Map<String, Object>> toolCalls = request.toolCalls() != null ? request.toolCalls() : List.of();
            XSSFSheet summarySheet = workbook.createSheet("요약");
            int summaryRow = 0;
            summaryRow = writeRow(summarySheet, summaryRow, List.of("제목", valueOr(request.title(), "OMS 조회 결과")));
            summaryRow = writeRow(summarySheet, summaryRow, List.of("내보낸 시각", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            summaryRow = writeRow(summarySheet, summaryRow, List.of("조회 도구 수", String.valueOf(toolCalls.size())));
            summaryRow++;

            int index = 1;
            for (Map<String, Object> toolCall : toolCalls) {
                String name = String.valueOf(toolCall.getOrDefault("name", "unknown"));
                Map<String, Object> args = asMap(toolCall.get("arguments"));
                Map<String, Object> result = toolService.executeTool(name, args);

                summaryRow = writeRow(summarySheet, summaryRow, List.of(index + "번 조회", localizedToolName(name)));
                summaryRow = writeRow(summarySheet, summaryRow, List.of("조회 조건", formatArguments(args)));
                summaryRow = writeSummaryPreview(summarySheet, summaryRow, result);
                summaryRow++;

                XSSFSheet sheet = workbook.createSheet(sheetName(index, name));
                writeResultSheet(sheet, name, args, result);
                index++;
            }

            autosize(summarySheet, 6);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeResultSheet(XSSFSheet sheet, String name, Map<String, Object> args, Map<String, Object> result) {
        int row = 0;
        row = writeRow(sheet, row, List.of("조회 종류", localizedToolName(name)));
        row = writeRow(sheet, row, List.of("조회 조건", formatArguments(args)));
        row++;

        boolean tabularWritten = false;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                row = writeRow(sheet, row, List.of(localizedFieldName(entry.getKey())));
                row = writeTable(sheet, row, castList(list));
                row++;
                tabularWritten = true;
            } else {
                row = writeRow(sheet, row, List.of(localizedFieldName(entry.getKey()), stringify(entry.getValue())));
            }
        }

        if (!tabularWritten && result.isEmpty()) {
            writeRow(sheet, row, List.of("결과", "데이터 없음"));
        }

        autosize(sheet, 12);
    }

    private int writeTable(XSSFSheet sheet, int startRow, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return writeRow(sheet, startRow, List.of("데이터 없음"));
        }
        List<String> headers = rows.stream()
            .flatMap(item -> item.keySet().stream())
            .map(String::valueOf)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
        int rowIndex = writeRow(sheet, startRow, headers);
        for (Map<String, Object> item : rows) {
            List<String> values = headers.stream()
                .map(header -> stringify(item.get(header)))
                .toList();
            rowIndex = writeRow(sheet, rowIndex, values);
        }
        return rowIndex;
    }

    private int writeSummaryPreview(XSSFSheet sheet, int startRow, Map<String, Object> result) {
        int row = startRow;
        List<String> priorityKeys = List.of(
            "period", "startDate", "endDate", "totalOrders", "pendingOrders", "confirmedOrders",
            "shippedOrders", "shippedCount", "totalQuantity", "cancelledOrders", "totalProducts", "totalStock", "availableStock",
            "reservedStock", "outOfStockCount", "count", "latestOrderNo", "latestOrderedAt"
        );

        for (String key : priorityKeys) {
            if (result.containsKey(key) && !(result.get(key) instanceof List<?>)) {
                row = writeRow(sheet, row, List.of(localizedFieldName(key), stringify(result.get(key))));
            }
        }

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) {
                continue;
            }
            row = writeRow(sheet, row, List.of(localizedFieldName(entry.getKey())));
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                row = writeTable(sheet, row, castList(list));
            } else {
                for (Object item : list) {
                    row = writeRow(sheet, row, List.of(stringify(item)));
                }
            }
        }

        if (result.isEmpty()) {
            row = writeRow(sheet, row, List.of("결과", "데이터 없음"));
        }
        return row;
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
        if (value instanceof List<?> list) {
            return list.stream().map(this::stringify).collect(Collectors.joining(", "));
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(localizedFieldName(String.valueOf(k)) + ": " + stringify(v)));
            return String.join(" / ", parts);
        }
        return String.valueOf(value);
    }

    private String valueOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String sheetName(int index, String name) {
        String raw = index + "_" + localizedToolName(name).replaceAll("[\\\\/*?:\\[\\]]", "_");
        return raw.length() > 31 ? raw.substring(0, 31) : raw;
    }

    private String localizedToolName(String name) {
        return switch (name) {
            case "get_order_overview" -> "주문 현황 조회";
            case "search_orders" -> "주문 검색";
            case "get_shipment_stats" -> "출고 현황 조회";
            case "get_claim_overview" -> "반품/교환 현황 조회";
            case "get_inventory_overview" -> "재고 현황 조회";
            case "search_products" -> "상품 검색";
            case "get_top_products_by_channel" -> "판매처 인기 상품 조회";
            default -> name;
        };
    }

    private String localizedFieldName(String key) {
        return switch (key) {
            case "period" -> "조회 기간";
            case "zone" -> "기준 시간대";
            case "startDate" -> "시작일";
            case "endDate" -> "종료일";
            case "totalOrders" -> "총 주문";
            case "pendingOrders" -> "대기 주문";
            case "confirmedOrders" -> "확정 주문";
            case "shippedOrders" -> "발송 완료";
            case "shippedCount" -> "출고 완료 건수";
            case "shippedAt" -> "출고시각";
            case "totalQuantity" -> "상품 수량 합계";
            case "claimType" -> "클레임 유형";
            case "totalClaims" -> "클레임 건수";
            case "requestedClaims" -> "접수 건수";
            case "inspectingClaims" -> "검수중 건수";
            case "completedClaims" -> "완료 건수";
            case "cancelledClaims" -> "취소 건수";
            case "claims" -> "클레임 목록";
            case "returnId" -> "반품 ID";
            case "returnType" -> "반품 유형";
            case "createdAt" -> "접수시각";
            case "cancelledOrders" -> "취소 주문";
            case "topChannels" -> "주요 판매처";
            case "recentDailyCounts" -> "최근 일자별 주문";
            case "latestOrderNo" -> "가장 최근 주문번호";
            case "latestOrderedAt" -> "가장 최근 주문시각";
            case "keyword" -> "검색어";
            case "status" -> "상태";
            case "count" -> "조회 건수";
            case "orders" -> "주문 목록";
            case "orderNo" -> "주문번호";
            case "recipientName" -> "수취인";
            case "customerName" -> "고객명";
            case "channelName" -> "판매처";
            case "orderedAt" -> "주문시각";
            case "productSummary" -> "상품 요약";
            case "invoiceEntered" -> "송장 입력 여부";
            case "totalProducts" -> "전체 상품 수";
            case "totalStock" -> "전체 재고";
            case "availableStock" -> "사용 가능 재고";
            case "reservedStock" -> "예약 재고";
            case "outOfStockCount" -> "품절 상품 수";
            case "riskProducts" -> "위험 상품";
            case "products" -> "상품 목록";
            case "orderCount" -> "주문 건수";
            case "quantity" -> "주문 수량";
            case "channelKeyword" -> "판매처";
            case "productName" -> "상품명";
            case "sku" -> "SKU";
            case "barcode" -> "바코드";
            case "location" -> "위치";
            case "date" -> "일자";
            case "limit" -> "조회 건수";
            default -> key;
        };
    }

    private String formatArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "조건 없음";
        }
        return args.entrySet().stream()
            .map(entry -> localizedFieldName(entry.getKey()) + ": " + stringify(entry.getValue()))
            .collect(Collectors.joining(" / "));
    }
}
