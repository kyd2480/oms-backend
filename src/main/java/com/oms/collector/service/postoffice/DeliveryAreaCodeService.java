package com.oms.collector.service.postoffice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DeliveryAreaCodeService {

    private static final String DEFAULT_BASE_URL = "http://biz.epost.go.kr/KpostPortal/openapi";
    private static final String DEFAULT_USER_AGENT = "Apache-HttpClient/4.5.1 (Java/17)";

    private final Map<String, DeliveryAreaInfo> cache = new ConcurrentHashMap<>();

    @Value("${tracking.post-office.delivery-area.enabled:true}")
    private boolean enabled;

    @Value("${tracking.post-office.delivery-area.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;

    @Value("${tracking.post-office.delivery-area.regkey:}")
    private String regkey;

    @Value("${tracking.post-office.delivery-area.mdiv:1}")
    private String mdiv;

    public DeliveryAreaInfo lookup(String postalCode, String fullAddress) {
        String zip = sanitizeZip(postalCode);
        String address = normalizeAddress(fullAddress);
        if (!enabled || !StringUtils.hasText(regkey) || !StringUtils.hasText(zip) || !StringUtils.hasText(address)) {
            return DeliveryAreaInfo.empty();
        }

        String cacheKey = zip + "|" + address;
        return cache.computeIfAbsent(cacheKey, key -> request(zip, address));
    }

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(regkey);
    }

    private DeliveryAreaInfo request(String zip, String address) {
        HttpURLConnection connection = null;
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("regkey", regkey)
                .queryParam("target", "delivArea")
                .queryParam("zip", zip)
                .queryParam("addr", address)
                .queryParam("mdiv", StringUtils.hasText(mdiv) ? mdiv : "1")
                .build(true)
                .toUri();

            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
            connection.setRequestProperty("Accept", "application/xml,text/xml,*/*");

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readResponse(stream, detectCharset(connection.getContentType()));
            if (statusCode >= 400) {
                throw new IllegalStateException("HTTP " + statusCode + " " + body);
            }

            DeliveryAreaInfo info = parse(body);
            if (!info.hasValue()) {
                log.warn("집배코드 조회 결과 없음: zip={}, address={}", zip, address);
            }
            return info;
        } catch (Exception e) {
            log.warn("집배코드 조회 실패: zip={}, address={}, reason={}", zip, address, e.getMessage());
            return DeliveryAreaInfo.empty();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private DeliveryAreaInfo parse(String xml) throws Exception {
        if (!StringUtils.hasText(xml)) {
            return DeliveryAreaInfo.empty();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));

        String deliveryAreaCode = getTagValue(document, "delivAreaCd");
        String arrivalCenterName = getTagValue(document, "arrCnpoNm");
        String deliveryPostOfficeName = getTagValue(document, "delivPoNm");

        return new DeliveryAreaInfo(deliveryAreaCode, arrivalCenterName, deliveryPostOfficeName);
    }

    private String getTagValue(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String value = nodes.item(0).getTextContent();
        return value == null ? "" : value.trim();
    }

    private String readResponse(InputStream stream, Charset charset) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private Charset detectCharset(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase();
        if (lower.contains("euc-kr")) {
            return Charset.forName("EUC-KR");
        }
        if (lower.contains("utf-8")) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }

    private String sanitizeZip(String postalCode) {
        if (!StringUtils.hasText(postalCode)) {
            return "";
        }
        String digits = postalCode.replaceAll("[^0-9]", "");
        if (digits.length() > 5) {
            return digits.substring(0, 5);
        }
        return digits;
    }

    private String normalizeAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return "";
        }
        return address.replaceAll("\\s+", " ").trim();
    }

    public record DeliveryAreaInfo(String deliveryAreaCode, String arrivalCenterName, String deliveryPostOfficeName) {

        public static DeliveryAreaInfo empty() {
            return new DeliveryAreaInfo("", "", "");
        }

        public boolean hasValue() {
            return StringUtils.hasText(deliveryAreaCode)
                || StringUtils.hasText(arrivalCenterName)
                || StringUtils.hasText(deliveryPostOfficeName);
        }

        public String toPrimaryLine() {
            StringBuilder builder = new StringBuilder();
            appendPart(builder, deliveryAreaCode);
            appendPart(builder, arrivalCenterName);
            appendPart(builder, deliveryPostOfficeName);
            return builder.toString().trim();
        }

        public String toSecondaryLine() {
            if (StringUtils.hasText(deliveryAreaCode)) {
                return "-" + deliveryAreaCode + "-";
            }
            return "";
        }

        private void appendPart(StringBuilder builder, String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value.trim());
        }
    }
}
