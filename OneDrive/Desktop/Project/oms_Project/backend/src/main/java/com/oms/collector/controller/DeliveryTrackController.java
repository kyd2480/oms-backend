package com.oms.collector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 배송 흐름 조회 컨트롤러
 *
 * GET /api/delivery/track?trackingNo=123456789&carrierCode=POST
 *
 * 우체국 공공 API (통합배송조회) 연동
 * - 국내: getLongitudinalDomesticList
 * - 통합(국내+국제): getLongitudinalCombinedList
 *
 * application.yml 설정 필요:
 *   delivery:
 *     post-office:
 *       api-key: YOUR_SERVICE_KEY  ← data.go.kr 발급 인증키
 */
@Slf4j
@RestController
@RequestMapping("/api/delivery")
@CrossOrigin(origins = "*")
public class DeliveryTrackController {

    // data.go.kr에서 발급받은 서비스 인증키 (URL 인코딩된 키)
    @Value("${delivery.post-office.api-key:}")
    private String postOfficeApiKey;

    // 우체국 통합 종적조회 API (국내 + 국제) — https
    private static final String POST_COMBINED_URL =
        "https://openapi.epost.go.kr/trace/retrieveLongitudinalCombinedService" +
        "/retrieveLongitudinalCombinedService/getLongitudinalCombinedList";

    // 우체국 국내 종적조회 API — https
    private static final String POST_DOMESTIC_URL =
        "https://openapi.epost.go.kr/trace/retrieveLongitudinalService" +
        "/retrieveLongitudinalService/getLongitudinalDomesticList";

    /* ── 응답 DTO ─────────────────────────────────────────── */
    public static class TrackStep {
        public String dateTime;   // 처리일시 + 처리시간
        public String location;   // 처리장소
        public String status;     // 처리상태
        public String detail;     // 상세설명
    }

    public static class TrackResult {
        public boolean success;
        public String  message;
        public String  trackingNo;
        public String  carrierCode;
        public String  carrierName;
        public String  sender;       // 보낸사람
        public String  receiver;     // 받는사람
        public String  sentDate;     // 보낸날짜
        public String  deliveryDate; // 받은날짜
        public String  currentStatus; // 현재 배달상태
        public List<TrackStep> steps = new ArrayList<>();
    }

    /**
     * 배송 흐름 조회
     * GET /api/delivery/track?trackingNo=1234567890&carrierCode=POST
     */
    @GetMapping("/track")
    public ResponseEntity<TrackResult> track(
        @RequestParam String trackingNo,
        @RequestParam(defaultValue = "POST") String carrierCode
    ) {
        log.info("배송 조회: trackingNo={}, carrier={}", trackingNo, carrierCode);

        // 우체국이 아닌 택배사는 현재 미지원
        if (!"POST".equalsIgnoreCase(carrierCode)) {
            TrackResult r = new TrackResult();
            r.success  = false;
            r.message  = carrierCode + " 택배사는 현재 배송조회가 지원되지 않습니다. (우체국만 지원)";
            r.trackingNo  = trackingNo;
            r.carrierCode = carrierCode;
            return ResponseEntity.ok(r);
        }

        // API 키 미설정
        if (postOfficeApiKey == null || postOfficeApiKey.isBlank()) {
            TrackResult r = new TrackResult();
            r.success  = false;
            r.message  = "우체국 API 인증키가 설정되지 않았습니다. (application.yml: delivery.post-office.api-key)";
            r.trackingNo  = trackingNo;
            r.carrierCode = carrierCode;
            return ResponseEntity.ok(r);
        }

        return ResponseEntity.ok(callPostOfficeApi(trackingNo));
    }

    /* ── 우체국 API 호출 ──────────────────────────────────── */
    private TrackResult callPostOfficeApi(String trackingNo) {
        TrackResult result = new TrackResult();
        result.trackingNo  = trackingNo;
        result.carrierCode = "POST";
        result.carrierName = "우체국택배";

        try {
            // 통합 API 먼저 시도
            String xmlResponse = httpGet(POST_COMBINED_URL
                + "?serviceKey=" + postOfficeApiKey
                + "&rgist=" + URLEncoder.encode(trackingNo, StandardCharsets.UTF_8));

            parseCombinedXml(xmlResponse, result);

            // 통합 API 결과 없으면 국내 API 재시도
            if (!result.success || result.steps.isEmpty()) {
                String xmlDomestic = httpGet(POST_DOMESTIC_URL
                    + "?serviceKey=" + postOfficeApiKey
                    + "&rgist=" + URLEncoder.encode(trackingNo, StandardCharsets.UTF_8));
                parseDomesticXml(xmlDomestic, result);
            }

        } catch (Exception e) {
            log.error("우체국 배송조회 실패: {}", e.getMessage());
            result.success = false;
            result.message = "배송조회 중 오류가 발생했습니다: " + e.getMessage();
        }

        return result;
    }

    /* ── 통합 API XML 파싱 ────────────────────────────────── */
    private void parseCombinedXml(String xml, TrackResult result) throws Exception {
        Document doc = parseXml(xml);
        if (doc == null) { result.success=false; result.message="응답 파싱 실패"; return; }

        // 에러 코드 확인
        String errCode = getText(doc, "cmmMsgHeader>returnReasonCode");
        if (errCode != null && !"00".equals(errCode)) {
            result.success = false;
            result.message = "우체국 API 오류: " + getText(doc, "cmmMsgHeader>returnAuthMsg");
            return;
        }

        // 기본 정보
        result.sender        = getText(doc, "retrieveLongitudinalCombinedListResponse>sndr");
        result.receiver      = getText(doc, "retrieveLongitudinalCombinedListResponse>rcvr");
        result.sentDate      = getText(doc, "retrieveLongitudinalCombinedListResponse>sndrDt");
        result.deliveryDate  = getText(doc, "retrieveLongitudinalCombinedListResponse>rcvDt");
        result.currentStatus = getText(doc, "retrieveLongitudinalCombinedListResponse>dlvSt");

        // 종적 목록
        NodeList items = doc.getElementsByTagName("longitudinalDomesticList");
        for (int i = 0; i < items.getLength(); i++) {
            Element el = (Element) items.item(i);
            TrackStep step = new TrackStep();
            step.dateTime = getChildText(el,"chgDt") + " " + getChildText(el,"chgTm");
            step.location = getChildText(el, "nowLc");
            step.status   = getChildText(el, "crgSt");
            step.detail   = getChildText(el, "detailDsc");
            result.steps.add(step);
        }

        result.success = true;
        if (result.steps.isEmpty()) {
            result.message = "조회된 배송 정보가 없습니다. 송장번호를 확인해주세요.";
        }
    }

    /* ── 국내 API XML 파싱 ────────────────────────────────── */
    private void parseDomesticXml(String xml, TrackResult result) throws Exception {
        Document doc = parseXml(xml);
        if (doc == null) { result.success=false; result.message="응답 파싱 실패"; return; }

        String errCode = getText(doc, "cmmMsgHeader>returnReasonCode");
        if (errCode != null && !"00".equals(errCode)) {
            result.success = false;
            result.message = "우체국 API 오류: " + getText(doc, "cmmMsgHeader>returnAuthMsg");
            return;
        }

        if (result.sender == null)
            result.sender   = getText(doc, "retrieveLongitudinalListResponse>sndr");
        if (result.receiver == null)
            result.receiver  = getText(doc, "retrieveLongitudinalListResponse>rcvr");
        result.currentStatus = getText(doc, "retrieveLongitudinalListResponse>dlvSt");

        NodeList items = doc.getElementsByTagName("longitudinalDomesticList");
        result.steps.clear();
        for (int i = 0; i < items.getLength(); i++) {
            Element el = (Element) items.item(i);
            TrackStep step = new TrackStep();
            step.dateTime = getChildText(el,"chgDt") + " " + getChildText(el,"chgTm");
            step.location = getChildText(el, "nowLc");
            step.status   = getChildText(el, "crgSt");
            step.detail   = getChildText(el, "detailDsc");
            result.steps.add(step);
        }

        result.success = true;
        if (result.steps.isEmpty())
            result.message = "조회된 배송 정보가 없습니다. 송장번호를 확인해주세요.";
    }

    /* ── 유틸 ─────────────────────────────────────────────── */
    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/xml");

        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();

        log.debug("우체국 API 응답 ({}): {}", code, body.length() > 200 ? body.substring(0,200)+"..." : body);
        return body;
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("XML 파싱 오류: {}", e.getMessage());
            return null;
        }
    }

    private String getText(Document doc, String path) {
        try {
            String[] parts = path.split(">");
            String tag = parts[parts.length - 1];
            NodeList nl = doc.getElementsByTagName(tag);
            if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
        } catch (Exception ignored) {}
        return null;
    }

    private String getChildText(Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
        return "";
    }
}
