package com.oms.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.CollectedOrderItem;
import com.oms.collector.entity.SabangnetIntegration;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SabangnetIntegrationRepository;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SabangnetOrderCollectionService {

    private final SabangnetIntegrationRepository integrationRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final RawOrderService rawOrderService;
    private final OrderProcessingService processingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SabangnetCollectResult collect(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime end = endDate == null ? LocalDateTime.now() : endDate;
        LocalDateTime start = startDate == null ? end.minusDays(1) : startDate;

        List<SabangnetIntegration> integrations = integrationRepository.findByEnabledTrueOrderByCreatedAtDesc();
        if (integrations.isEmpty()) {
            return SabangnetCollectResult.builder()
                .success(false)
                .message("사용 중인 사방넷 연동 설정이 없습니다")
                .startDate(start)
                .endDate(end)
                .build();
        }

        int collected = 0;
        int saved = 0;
        int processed = 0;
        List<String> errors = new ArrayList<>();

        for (SabangnetIntegration integration : integrations) {
            SabangnetCollectResult result = collectIntegration(integration, start, end);
            collected += result.collectedCount();
            saved += result.savedCount();
            processed += result.processedCount();
            if (result.errors() != null) errors.addAll(result.errors());
        }

        boolean success = errors.isEmpty();
        String message = success
            ? "사방넷 주문 수집 완료"
            : "사방넷 주문 수집 일부 실패";

        return SabangnetCollectResult.builder()
            .success(success)
            .message(message)
            .startDate(start)
            .endDate(end)
            .integrationCount(integrations.size())
            .collectedCount(collected)
            .savedCount(saved)
            .processedCount(processed)
            .errors(errors)
            .build();
    }

    @Transactional
    public SabangnetCollectResult collect(UUID integrationId, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime end = endDate == null ? LocalDateTime.now() : endDate;
        LocalDateTime start = startDate == null ? end.minusDays(1) : startDate;
        SabangnetIntegration integration = integrationRepository.findByIntegrationIdAndEnabledTrue(integrationId)
            .orElseThrow(() -> new IllegalArgumentException("사용 중인 사방넷 쇼핑몰 설정을 찾을 수 없습니다"));
        return collectIntegration(integration, start, end);
    }

    private SabangnetCollectResult collectIntegration(SabangnetIntegration integration, LocalDateTime start, LocalDateTime end) {
        String channelCode = channelCode(integration);
        ensureSabangnetChannel(integration, channelCode);

        int collected = 0;
        int saved = 0;
        int processed = 0;
        List<String> errors = new ArrayList<>();

        try {
            String rawResponse = requestOrders(integration, start, end);
            List<CollectedOrder> orders = parseOrders(rawResponse);
            collected += orders.size();

            for (CollectedOrder order : orders) {
                order.setChannelCode(channelCode);
                if (order.getRawJson() == null || order.getRawJson().isBlank()) {
                    order.setRawJson(objectMapper.writeValueAsString(order));
                }
                rawOrderService.saveRawOrder(order);
                saved++;
            }

            integration.setLastCollectedAt(end);
            integrationRepository.save(integration);
            processed = processingService.processUnprocessedOrdersByChannel(channelCode);
            log.info("사방넷 주문 수집 완료: mall={}, channel={}, collected={}", mallLabel(integration), channelCode, orders.size());
        } catch (Exception e) {
            String message = mallLabel(integration) + ": " + e.getMessage();
            errors.add(message);
            log.error("사방넷 주문 수집 실패: {}", mallLabel(integration), e);
        }

        return SabangnetCollectResult.builder()
            .success(errors.isEmpty())
            .message(errors.isEmpty() ? "사방넷 주문 수집 완료" : "사방넷 주문 수집 실패")
            .startDate(start)
            .endDate(end)
            .integrationCount(1)
            .mallCode(integration.getMallCode())
            .mallName(mallLabel(integration))
            .channelCode(channelCode)
            .collectedCount(collected)
            .savedCount(saved)
            .processedCount(processed)
            .errors(errors)
            .build();
    }

    private String requestOrders(SabangnetIntegration integration, LocalDateTime start, LocalDateTime end) {
        String startText = start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endText = end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return WebClient.create()
            .post()
            .uri(integration.getApiBaseUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN)
            .body(BodyInserters.fromFormData("sabangnetId", integration.getSabangnetId())
                .with("apiKey", integration.getApiKey())
                .with("mallCode", integration.getMallCode() == null ? "" : integration.getMallCode())
                .with("shopCode", integration.getMallCode() == null ? "" : integration.getMallCode())
                .with("startDate", startText)
                .with("endDate", endText)
                .with("logisticsPlaceId", integration.getLogisticsPlaceId() == null ? "" : integration.getLogisticsPlaceId()))
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }

    private List<CollectedOrder> parseOrders(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        String text = rawResponse.trim();
        if (text.startsWith("<")) {
            return parseXmlOrders(text);
        }
        return parseJsonOrders(text);
    }

    private List<CollectedOrder> parseJsonOrders(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        List<JsonNode> nodes = new ArrayList<>();
        collectOrderNodes(root, nodes);

        List<CollectedOrder> orders = new ArrayList<>();
        for (JsonNode node : nodes) {
            CollectedOrder order = mapJsonOrder(node);
            if (order != null && order.isValid()) {
                order.setRawJson(node.toString());
                orders.add(order);
            }
        }
        return orders;
    }

    private void collectOrderNodes(JsonNode node, List<JsonNode> nodes) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectOrderNodes(child, nodes));
            return;
        }
        if (!node.isObject()) return;

        JsonNode array = firstArray(node, "orders", "orderList", "list", "data", "items", "result");
        if (array != null) {
            array.forEach(child -> collectOrderNodes(child, nodes));
            return;
        }
        if (text(node, "orderNo", "order_no", "ordNo", "mallOrderNo", "channelOrderNo", "order_id") != null) {
            nodes.add(node);
        }
    }

    private CollectedOrder mapJsonOrder(JsonNode node) {
        String orderNo = text(node, "channelOrderNo", "orderNo", "order_no", "ordNo", "mallOrderNo", "order_id");
        if (orderNo == null) return null;

        List<CollectedOrderItem> items = new ArrayList<>();
        JsonNode itemArray = firstArray(node, "orderItems", "items", "products", "productList", "goods");
        if (itemArray != null) {
            itemArray.forEach(item -> items.add(mapJsonItem(item, node)));
        } else {
            items.add(mapJsonItem(node, node));
        }

        return CollectedOrder.builder()
            .channelCode("SABANGNET")
            .channelOrderNo(orderNo)
            .customerName(text(node, "customerName", "buyerName", "orderName", "ordName"))
            .customerPhone(text(node, "customerPhone", "buyerPhone", "orderTel", "ordTel"))
            .recipientName(text(node, "recipientName", "receiverName", "recvName", "rcvName", "name"))
            .recipientPhone(text(node, "recipientPhone", "receiverPhone", "recvTel", "rcvTel", "phone"))
            .postalCode(text(node, "postalCode", "zip", "zipcode", "recvZip"))
            .address(text(node, "address", "receiverAddress", "recvAddr", "addr"))
            .addressDetail(text(node, "addressDetail", "receiverAddressDetail", "recvAddrDetail", "addrDetail"))
            .deliveryMemo(text(node, "deliveryMemo", "memo", "deliveryMessage", "shipMemo"))
            .totalAmount(decimal(node, "totalAmount", "orderAmount", "payAmount", "amount"))
            .paymentAmount(decimal(node, "paymentAmount", "payAmount", "settleAmount"))
            .status(text(node, "status", "orderStatus", "ordStatus"))
            .paymentStatus(text(node, "paymentStatus", "payStatus"))
            .orderedAt(dateTime(node, "orderedAt", "orderDate", "ordDate", "regDate"))
            .paidAt(dateTime(node, "paidAt", "payDate"))
            .items(items)
            .build();
    }

    private CollectedOrderItem mapJsonItem(JsonNode item, JsonNode order) {
        BigDecimal unitPrice = decimal(item, "unitPrice", "price", "salePrice", "goodsPrice");
        Integer quantity = integer(item, "quantity", "qty", "orderQty", "count");
        return CollectedOrderItem.builder()
            .channelProductCode(text(item, "channelProductCode", "productCode", "goodsNo", "goodsCode", "sku"))
            .productName(text(item, "productName", "goodsName", "itemName", "name"))
            .optionName(text(item, "optionName", "option", "optionValue"))
            .quantity(quantity == null ? 1 : quantity)
            .unitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice)
            .totalPrice(decimal(item, "totalPrice", "amount"))
            .barcode(text(item, "barcode", "barCode"))
            .sku(text(item, "sku", "optionCode"))
            .build();
    }

    private List<CollectedOrder> parseXmlOrders(String rawResponse) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(rawResponse)));
        document.getDocumentElement().normalize();

        List<Element> orderElements = elementsByName(document, "order");
        if (orderElements.isEmpty()) {
            orderElements = elementsByName(document, "Order");
        }
        if (orderElements.isEmpty()) {
            orderElements = List.of(document.getDocumentElement());
        }

        List<CollectedOrder> orders = new ArrayList<>();
        for (Element element : orderElements) {
            CollectedOrder order = mapXmlOrder(element);
            if (order != null && order.isValid()) {
                order.setRawJson(rawResponse);
                orders.add(order);
            }
        }
        return orders;
    }

    private CollectedOrder mapXmlOrder(Element element) {
        String orderNo = xmlText(element, "channelOrderNo", "orderNo", "ordNo", "mallOrderNo", "order_id");
        if (orderNo == null) return null;

        List<CollectedOrderItem> items = new ArrayList<>();
        List<Element> itemElements = childElementsByName(element, "item");
        if (itemElements.isEmpty()) itemElements = childElementsByName(element, "goods");
        if (itemElements.isEmpty()) {
            items.add(mapXmlItem(element));
        } else {
            itemElements.forEach(item -> items.add(mapXmlItem(item)));
        }

        return CollectedOrder.builder()
            .channelCode("SABANGNET")
            .channelOrderNo(orderNo)
            .customerName(xmlText(element, "customerName", "buyerName", "orderName", "ordName"))
            .customerPhone(xmlText(element, "customerPhone", "buyerPhone", "orderTel", "ordTel"))
            .recipientName(xmlText(element, "recipientName", "receiverName", "recvName", "rcvName", "name"))
            .recipientPhone(xmlText(element, "recipientPhone", "receiverPhone", "recvTel", "rcvTel", "phone"))
            .postalCode(xmlText(element, "postalCode", "zip", "zipcode", "recvZip"))
            .address(xmlText(element, "address", "receiverAddress", "recvAddr", "addr"))
            .addressDetail(xmlText(element, "addressDetail", "receiverAddressDetail", "recvAddrDetail", "addrDetail"))
            .deliveryMemo(xmlText(element, "deliveryMemo", "memo", "deliveryMessage", "shipMemo"))
            .status(xmlText(element, "status", "orderStatus", "ordStatus"))
            .paymentStatus(xmlText(element, "paymentStatus", "payStatus"))
            .orderedAt(parseDateTime(xmlText(element, "orderedAt", "orderDate", "ordDate", "regDate")))
            .paidAt(parseDateTime(xmlText(element, "paidAt", "payDate")))
            .items(items)
            .build();
    }

    private CollectedOrderItem mapXmlItem(Element element) {
        Integer quantity = parseInteger(xmlText(element, "quantity", "qty", "orderQty", "count"));
        BigDecimal unitPrice = parseDecimal(xmlText(element, "unitPrice", "price", "salePrice", "goodsPrice"));
        return CollectedOrderItem.builder()
            .channelProductCode(xmlText(element, "channelProductCode", "productCode", "goodsNo", "goodsCode", "sku"))
            .productName(xmlText(element, "productName", "goodsName", "itemName", "name"))
            .optionName(xmlText(element, "optionName", "option", "optionValue"))
            .quantity(quantity == null ? 1 : quantity)
            .unitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice)
            .barcode(xmlText(element, "barcode", "barCode"))
            .sku(xmlText(element, "sku", "optionCode"))
            .build();
    }

    private SalesChannel ensureSabangnetChannel(SabangnetIntegration integration, String channelCode) {
        return salesChannelRepository.findByChannelCode(channelCode).orElseGet(() ->
            salesChannelRepository.save(SalesChannel.builder()
                .channelCode(channelCode)
                .channelName(mallLabel(integration))
                .apiType("REST")
                .isActive(true)
                .collectionInterval(10)
                .build())
        );
    }

    private String channelCode(SabangnetIntegration integration) {
        String source = integration.getMallCode();
        if (source == null || source.isBlank()) {
            source = integration.getIntegrationName();
        }
        String suffix = source.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_");
        if (suffix.isBlank()) suffix = "DEFAULT";
        return "SABANGNET_" + suffix.toUpperCase();
    }

    private String mallLabel(SabangnetIntegration integration) {
        if (integration.getMallName() != null && !integration.getMallName().isBlank()) {
            return integration.getMallName().trim();
        }
        if (integration.getIntegrationName() != null && !integration.getIntegrationName().isBlank()) {
            return integration.getIntegrationName().trim();
        }
        return "사방넷";
    }

    private JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode found = findField(node, name);
            if (found != null && found.isArray()) return found;
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        JsonNode found = null;
        for (String name : names) {
            found = findField(node, name);
            if (found != null && !found.isNull() && !found.asText().isBlank()) {
                return found.asText().trim();
            }
        }
        return null;
    }

    private JsonNode findField(JsonNode node, String name) {
        if (node == null || !node.isObject()) return null;
        String target = normalizeKey(name);
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String current = names.next();
            if (normalizeKey(current).equals(target)) {
                return node.get(current);
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... names) {
        return parseDecimal(text(node, names));
    }

    private Integer integer(JsonNode node, String... names) {
        return parseInteger(text(node, names));
    }

    private LocalDateTime dateTime(JsonNode node, String... names) {
        return parseDateTime(text(node, names));
    }

    private String xmlText(Element element, String... names) {
        for (String name : names) {
            String target = normalizeKey(name);
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element childElement && normalizeKey(childElement.getTagName()).equals(target)) {
                    String value = childElement.getTextContent();
                    if (value != null && !value.isBlank()) return value.trim();
                }
            }
        }
        return null;
    }

    private List<Element> elementsByName(Document document, String name) {
        NodeList nodes = document.getElementsByTagName(name);
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) elements.add(element);
        }
        return elements;
    }

    private List<Element> childElementsByName(Element element, String name) {
        String target = normalizeKey(name);
        List<Element> elements = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && normalizeKey(childElement.getTagName()).equals(target)) {
                elements.add(childElement);
            }
        }
        return elements;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Builder
    public record SabangnetCollectResult(
        boolean success,
        String message,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int integrationCount,
        String mallCode,
        String mallName,
        String channelCode,
        int collectedCount,
        int savedCount,
        int processedCount,
        List<String> errors
    ) {}
}
