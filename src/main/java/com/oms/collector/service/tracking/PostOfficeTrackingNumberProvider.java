package com.oms.collector.service.tracking;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import kr.re.etri.security.SEED128;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 우체국 택배 OpenAPI 송장번호 발급 구현체
 *
 * 우체국 계약소포 OpenAPI 연동 구현체.
 * 계약승인번호/공급지코드를 조회한 뒤 소포신청 API를 호출하고,
 * 응답 XML 의 regiNo 를 송장번호로 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tracking.provider", havingValue = "post-office")
public class PostOfficeTrackingNumberProvider implements TrackingNumberProvider {

    private static final String DEFAULT_BASE_URL = "http://ship.epost.go.kr";
    private static final String APPROVAL_API_PATH = "/api.GetApprNo.jparcel";
    private static final String OFFICE_INFO_API_PATH = "/api.GetOfficeInfo.jparcel";
    private static final String INSERT_ORDER_API_PATH = "/api.InsertOrder.jparcel";
    private static final String CANCEL_ORDER_API_PATH = "/api.GetResCancelCmd.jparcel";
    private static final String DEFAULT_USER_AGENT = "Apache-HttpClient/4.5.1 (Java/17)";

    private final OrderRepository orderRepository;
    private final SEED128 seed128 = new SEED128();

    @Value("${tracking.post-office.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;

    @Value("${tracking.post-office.auth-key:}")
    private String authKey;

    @Value("${tracking.post-office.seed-key:}")
    private String seedKey;

    @Value("${tracking.post-office.customer-no:}")
    private String customerNo;

    @Value("${tracking.post-office.contract-approval-no:}")
    private String contractApprovalNo;

    @Value("${tracking.post-office.office-ser:}")
    private String officeSer;

    @Value("${tracking.post-office.order-company-name:OMS}")
    private String orderCompanyName;

    @Value("${tracking.post-office.inquiry-tel:}")
    private String inquiryTel;

    @Value("${tracking.post-office.content-code:029}")
    private String contentCode;

    @Value("${tracking.post-office.default-weight:1}")
    private String defaultWeight;

    @Value("${tracking.post-office.default-volume:60}")
    private String defaultVolume;

    @Value("${tracking.post-office.print-yn:N}")
    private String printYn;

    @Value("${tracking.post-office.test-yn:N}")
    private String testYn;

    @Override
    public boolean supports(String carrierCode) {
        return "POST".equals(carrierCode);
    }

    @Override
    public IssueResult issue(String carrierCode, String carrierName, String orderNo) {
        if (!"POST".equals(carrierCode)) {
            throw new UnsupportedOperationException(
                "PostOfficeTrackingNumberProvider는 우체국(POST)만 지원합니다. 요청 택배사: " + carrierCode
            );
        }
        log.info("[우체국 API] 송장번호 발급 요청: orderNo={}", orderNo);
        return issueFromApi(orderNo);
    }

    @Override
    public void cancel(String carrierCode, String carrierName, String orderNo,
                       String trackingNo, String reservationNo, String reqYmd) {
        if (!"POST".equals(carrierCode)) {
            throw new UnsupportedOperationException(
                "PostOfficeTrackingNumberProvider는 우체국(POST)만 지원합니다. 요청 택배사: " + carrierCode
            );
        }
        validateConfiguration();
        requireText(trackingNo, "trackingNo");

        String resolvedApprNo = StringUtils.hasText(contractApprovalNo) ? contractApprovalNo : fetchApprovalNumber();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("custNo", customerNo);
        fields.put("apprNo", resolvedApprNo);
        fields.put("reqType", "1");
        fields.put("reqNo", orderNo);
        if (StringUtils.hasText(reservationNo)) {
            fields.put("resNo", reservationNo);
        }
        if (StringUtils.hasText(reqYmd)) {
            fields.put("reqYmd", reqYmd);
        }
        fields.put("regiNo", trackingNo);

        String xml = invokeApi(CANCEL_ORDER_API_PATH, encrypt(buildQueryString(fields)));
        ensureNoApiError(xml);
        log.info("[우체국 API] 송장번호 취소 완료: orderNo={}, trackingNo={}", orderNo, trackingNo);
    }

    private IssueResult issueFromApi(String orderNo) {
        validateConfiguration();

        Order order = orderRepository.findWithItemsByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderNo));

        String resolvedApprNo = StringUtils.hasText(contractApprovalNo) ? contractApprovalNo : fetchApprovalNumber();
        String resolvedOfficeSer = StringUtils.hasText(officeSer) ? officeSer : fetchOfficeSer();
        String regData = encrypt(buildInsertOrderPayload(order, resolvedApprNo, resolvedOfficeSer));
        String xml = invokeApi(INSERT_ORDER_API_PATH, regData);
        ensureNoApiError(xml);

        String trackingNo = getRequiredTagValue(xml, "regiNo");
        String reservationNo = getOptionalTagValue(xml, "resNo");
        String reqYmd = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        log.info("[우체국 API] 송장번호 발급 완료: orderNo={}, trackingNo={}, resNo={}, reqYmd={}", orderNo, trackingNo, reservationNo, reqYmd);
        return new IssueResult(trackingNo, reservationNo, reqYmd);
    }

    private void validateConfiguration() {
        requireText(authKey, "tracking.post-office.auth-key");
        requireText(seedKey, "tracking.post-office.seed-key");
        requireText(customerNo, "tracking.post-office.customer-no");
        requireText(orderCompanyName, "tracking.post-office.order-company-name");
        requireText(contentCode, "tracking.post-office.content-code");
    }

    private String fetchApprovalNumber() {
        String regData = encrypt(buildQueryString(Map.of("custNo", customerNo)));
        String xml = invokeApi(APPROVAL_API_PATH, regData);
        ensureNoApiError(xml);
        return getRequiredTagValue(xml, "apprNo");
    }

    private String fetchOfficeSer() {
        String regData = encrypt(buildQueryString(Map.of("custNo", customerNo)));
        String xml = invokeApi(OFFICE_INFO_API_PATH, regData);
        ensureNoApiError(xml);
        return getRequiredTagValue(xml, "officeSer");
    }

    private String buildInsertOrderPayload(Order order, String apprNo, String officeSerValue) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("custNo", customerNo);
        fields.put("apprNo", apprNo);
        fields.put("payType", "1");
        fields.put("reqType", "1");
        fields.put("officeSer", officeSerValue);
        fields.put("weight", Objects.toString(defaultWeight, "1"));
        fields.put("volume", Objects.toString(defaultVolume, "60"));
        fields.put("microYn", "N");
        fields.put("orderNo", truncate(order.getOrderNo(), 50));
        fields.put("ordCompNm", truncate(orderCompanyName, 100));
        putIfHasText(fields, "inqTelCn", sanitizePhone(inquiryTel));
        putIfHasText(fields, "ordNm", truncate(order.getCustomerName(), 40));
        putIfHasText(fields, "ordTel", sanitizePhone(order.getCustomerPhone()));
        fields.put("recNm", truncate(order.getRecipientName(), 40));
        fields.put("recZip", requireText(sanitizeZip(order.getPostalCode()), "recipient postal code"));
        fields.put("recAddr1", truncate(requireText(order.getAddress(), "recipient address"), 150));
        fields.put("recAddr2", truncate(StringUtils.hasText(order.getAddressDetail()) ? order.getAddressDetail() : "-", 300));

        String recipientPhone = sanitizePhone(order.getRecipientPhone());
        fields.put("recTel", requireText(recipientPhone, "recipient phone"));
        fields.put("contCd", truncate(contentCode, 3));
        fields.put("goodsNm", truncate(buildGoodsName(order), 400));
        putIfHasText(fields, "goodsCd", truncate(buildGoodsCode(order), 400));
        putIfHasText(fields, "goodsSize", truncate(buildOptionSummary(order), 30));
        fields.put("qty", Integer.toString(order.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum()));
        putIfHasText(fields, "delivMsg", truncate(extractDeliveryMessage(order.getDeliveryMemo()), 200));
        fields.put("printYn", normalizeYn(printYn, "N"));
        fields.put("testYn", normalizeYn(testYn, "N"));
        return buildQueryString(fields);
    }

    private String invokeApi(String apiPath, String regData) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(apiPath)
            .queryParam("key", authKey)
            .queryParam("regData", regData)
            .build(true)
            .toUri();

        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
            connection.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.name());

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = readResponse(stream);
            if (statusCode >= 400) {
                throw new IllegalStateException("우체국 API 호출 실패: HTTP " + statusCode + " " + responseBody);
            }
            return responseBody;
        } catch (Exception e) {
            throw new IllegalStateException("우체국 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private String encrypt(String plainText) {
        return seed128.getEncryptData(seedKey, plainText);
    }

    private void ensureNoApiError(String xml) {
        String errorCode = getOptionalTagValue(xml, "error_code");
        if (StringUtils.hasText(errorCode)) {
            String message = getOptionalTagValue(xml, "message");
            throw new IllegalStateException("우체국 API 오류 [" + errorCode + "] " + Objects.toString(message, ""));
        }
    }

    private String getRequiredTagValue(String xml, String tagName) {
        String value = getOptionalTagValue(xml, tagName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("우체국 API 응답에 필수 항목이 없습니다: " + tagName);
        }
        return value;
    }

    private String getOptionalTagValue(String xml, String tagName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList nodes = document.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                return null;
            }
            String value = nodes.item(0).getTextContent();
            return StringUtils.hasText(value) ? value.trim() : null;
        } catch (Exception e) {
            throw new IllegalStateException("우체국 API XML 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildQueryString(Map<String, String> fields) {
        return fields.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> entry.getKey() + "=" + sanitizeRegDataValue(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private void putIfHasText(Map<String, String> fields, String key, String value) {
        if (StringUtils.hasText(value)) {
            fields.put(key, value);
        }
    }

    private String buildGoodsName(Order order) {
        return order.getItems().stream()
            .map(item -> truncate(item.getProductName(), 100))
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.joining(", "));
    }

    private String buildGoodsCode(Order order) {
        return order.getItems().stream()
            .map(item -> truncate(item.getProductCode(), 100))
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.joining(","));
    }

    private String buildOptionSummary(Order order) {
        return order.getItems().stream()
            .map(item -> truncate(item.getOptionName(), 30))
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.joining(","));
    }

    private String sanitizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : truncate(digits, 12);
    }

    private String sanitizeZip(String zip) {
        if (!StringUtils.hasText(zip)) {
            return null;
        }
        String digits = zip.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : truncate(digits, 5);
    }

    private String normalizeYn(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return "Y".equalsIgnoreCase(value) ? "Y" : "N";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String sanitizeRegDataValue(String value) {
        return value
            .replace("&", " ")
            .replace("=", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("우체국 API 필수 설정/데이터가 없습니다: " + fieldName);
        }
        return value;
    }

    private String extractDeliveryMessage(String memo) {
        if (!StringUtils.hasText(memo) || memo.contains("INVOICE:")) {
            return null;
        }
        return memo;
    }
}
