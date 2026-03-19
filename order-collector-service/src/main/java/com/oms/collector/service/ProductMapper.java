package com.oms.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 상품 코드 매핑 서비스
 * 
 * 판매처 상품 코드 → 자사 상품 코드로 변환
 * 
 * TODO: 실제로는 데이터베이스에서 관리해야 함
 */
@Slf4j
@Service
public class ProductMapper {
    
    // 임시 매핑 테이블 (실제로는 DB에서 관리)
    private static final Map<String, String> PRODUCT_MAPPING = new HashMap<>();
    
    static {
        // 예시 매핑 (실제로는 DB 테이블로 관리)
        PRODUCT_MAPPING.put("NAVER-PRD-1001", "XEXYMIX-LEG-001");
        PRODUCT_MAPPING.put("NAVER-PRD-1002", "XEXYMIX-BRA-001");
        PRODUCT_MAPPING.put("CP-PRD-2001", "XEXYMIX-LEG-001");
        PRODUCT_MAPPING.put("CP-PRD-2002", "XEXYMIX-TOP-001");
    }
    
    /**
     * 판매처 상품코드 → 자사 상품코드 변환
     * 
     * @param channelProductCode 판매처 상품코드
     * @return 자사 상품코드 (매핑 없으면 원본 반환)
     */
    public String mapToProductCode(String channelProductCode) {
        if (channelProductCode == null || channelProductCode.isEmpty()) {
            return null;
        }

        // 바코드 패턴 (영문+숫자 조합, 쇼핑몰 코드 아님) → 그대로 사용
        if (!channelProductCode.matches("(?i)(11ST|NAVER|CP|GS|COUPANG|KAKAO)-.*")) {
            log.debug("✅ 바코드 직접 사용: {}", channelProductCode);
            return channelProductCode;
        }
        
        String productCode = PRODUCT_MAPPING.get(channelProductCode);
        
        if (productCode == null) {
            // 쇼핑몰 코드인데 매핑 없음 → null 반환 (상품명 매칭 위임)
            log.debug("⚠️ 상품 매핑 없음: {} (null 반환 → 상품명 매칭 위임)", channelProductCode);
            return null;
        }
        
        log.debug("✅ 상품 매핑: {} → {}", channelProductCode, productCode);
        return productCode;
    }
    
    /**
     * 상품 매핑 추가 (동적)
     */
    public void addMapping(String channelProductCode, String productCode) {
        PRODUCT_MAPPING.put(channelProductCode, productCode);
        log.info("📝 상품 매핑 추가: {} → {}", channelProductCode, productCode);
    }
    
    /**
     * 매핑 존재 여부 확인
     */
    public boolean hasMapping(String channelProductCode) {
        return PRODUCT_MAPPING.containsKey(channelProductCode);
    }
    
    /**
     * 전체 매핑 조회
     */
    public Map<String, String> getAllMappings() {
        return new HashMap<>(PRODUCT_MAPPING);
    }
}
