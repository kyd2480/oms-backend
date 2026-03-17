package com.oms.collector.collector.impl;

import com.oms.collector.collector.OrderCollector;
import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.CollectedOrderItem;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 쿠팡 Mock 주문 수집기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockCoupangCollector implements OrderCollector {

    private static final String[][] REAL_PRODUCTS = {
        {"XTFTP04H468S", "XTFTP04H4 레인민트", "S(44~55)"},
        {"XFK3BC150106S", "XFK3BC1501 락스톤그레이", "S(44~55)"},
        {"XFL1BL1508092L", "XFL1BL1508 블랙", "XXL(88반~99)"},
        {"HGTA3004G61S", "GT3004G 슈가민트", "S(44~55)"},
        {"XFK2JL100241S", "XFK2JL1002 민트듀", "S(44~55)"},
        {"XFK1RK120333S", "XFK1RK1203 다크네이비", "S(44~55)"},
        {"XFK2WB140709S", "XFK2WB1407 블랙", "S(44~55)"},
        {"XFK4PS120116M", "XFK4PS1201 베이지", "M(55반~66)"},
        {"10756020001", "XE5102G 손목보호대 차콜그레이", "차콜그레이"},
        {"XFK4JP100115XL", "XFK4JP1001 페블베이지", "XL(77~88)"},
        {"HGTC2246H01M", "GT2246H 블랙", "M"},
        {"FXPA9208G413L", "XP9208G 러스트오렌지", "XXXL(99반~100)"},
        {"XTFSH03J242XL", "XTFSH03J2 프릴퍼플", "XL(77~88)"},
        {"XMK3PT100408M", "XMK3PT1004 차콜", "M"},
        {"XMMSP04J262XL", "XMMSP04J2 더스틴그린", "XL"},
        {"XTFSH12J306M", "XTFSH12J3 멜란지그레이", "M(55반~66)"},
        {"XFK5UB150126M", "XFK5UB1501 소프트라벤더", "M(55반~66)"},
        {"XFK5UP150416L", "XFK5UP1504 토스티드버터", "L(66반~77)"},
        {"XFK3QL110109L", "XFK3QL1101 블랙", "L(66반~77)"},
        {"XFK2WP1402093L", "XFK2WP1402 블랙", "XXXL(99반~100)"},
        {"XFK1JL1101022L", "XFK1JL1101 백아이보리", "XXL(88반~99)"},
        {"XSFWB10J202M", "XSFWB10J2 아이보리", "M(55반~66)"},
        {"XMMPT01H408L", "XMMPT01H4 다크그레이", "L"},
        {"XFL1BL150209M", "XFL1BL1502 블랙", "M(55반~66)"},
        {"XWFBR05J1512L", "XWFBR05J1 티파니블루", "XXL(88반~99)"},
        {"XFK2LL150429L", "XFK2LL1504 클라우드딥블루", "L(66반~77)"},
        {"10832420001", "XEB210H 더블라인오버니삭스 더스트브라운", "더스트브라운"},
        {"XWFSL02H209M", "XWFSL02H2 제트인디고", "M(55반~66)"},
        {"HXAK1012G0012", "KA1012G 블랙", "120"},
        {"XTFST06J2022L", "XTFST06J2 아이보리", "XXL(88반~99)"},
        {"XWFLT06J302M", "XWFLT06J3 백아이보리", "M(55반~66)"},
        {"XFK2WB140309S", "XFK2WB1403 블랙", "S(44~55)"},
        {"XWFLT02J3102L", "XWFLT02J3 블랙", "XXL(88반~99)"},
        {"HGAB221I62SM", "GAB221I 그린", "S/M"},
        {"XFK1QL1103412L", "XFK1QL1103 썸머민트", "XXL(88반~99)"},
        {"XFL2PT100221XL", "XFL2PT1002 머드핑크", "XL(77~88)"},
        {"FXTC2142F222L", "XT2142F 젠틀베이지", "XXL"},
        {"10143020001", "XT4108E 아이스페더 다크그레이", "S(44~55)"},
        {"XMK3TS110109M", "XMK3TS1101 블랙", "M"},
        {"FXTC2218G61XL", "XT2218G 로드카키", "XL"},
        {"XTFSH16J310XL", "XTFSH16J3 블랙", "XL(77~88)"},
        {"XFK1RK110521M", "XFK1RK1105 핑크퍼센트", "M(55반~66)"},
        {"XUK2AB160221F", "XUK2AB1602 핑크코스모스", "핑크코스모스"},
        {"XUK3TL130227150", "XUK3TL1302 플럼퍼플", "150"},
        {"XMK1HC110129XL", "XMK1HC1101 스카이블루", "XL"},
        {"10567620003", "XT4108E 달리아와인", "L(66반~77)"},
        {"XFK1BL150109M", "XFK1BL1501 블랙", "M(55반~66)"},
        {"XFK4PT1104032L", "XFK4PT1104 폴링크림", "XXL(88반~99)"},
        {"XFK3TC111715S", "XFK3TC1117 바닐라베이지", "S(44~55)"},
        {"XGFJK10J310M", "XGFJK10J3 블랙", "M(55반~66)"},
        {"XFK3BL1506092L", "XFK3BL1506 블랙", "XXL(88반~99)"},
        {"XFK1BC1501292L", "XFK1BC1501 파우더스카이", "XXL(88반~99)"},
        {"XTFTP08J2372L", "XTFTP08J2 카밍카키", "XXL(88반~99)"},
        {"XWFLG01H336L", "XWFLG01H3 레이싱레드", "L(66반~77)"},
        {"XFK2PS1103142L", "XFK2PS1103 스위트오렌지", "XXL(88반~99)"},
        {"XFK4JD120302S", "XFK4JD1203 아이보리", "S(44~55)"},
        {"10774020001", "XP9167F 아쿠아리움", "S(44~55)"},
        {"XTFTP08J221M", "XTFTP08J2 피넛베이지", "M(55반~66)"},
        {"XWFSL05J1102L", "XWFSL05J1 블랙", "XXL(88반~99)"},
        {"XFK3TL100921S", "XFK3TL1009 드라이로즈", "S(44~55)"},
        {"XWFTP02J305S", "XWFTP02J3 리버그레이", "S(44~55)"},
        {"XMK5UP200141L", "XMK5UP2001 더스티민트", "L"},
        {"XWFCT02J121S", "XWFCT02J1 클라우드베이지", "S(44~55)"},
        {"XMK2WR130330130", "XMK2WR1303 마린스트라이프", "130"},
        {"XTFTP03H432L", "XTFTP03H4 핑크샌드", "L(66반~77)"},
        {"XTFJK08H410XL", "XTFJK08H4 블랙", "XL(77~88)"},
        {"XFK1PT1002092L", "XFK1PT1002 블랙", "XXL(88반~99)"},
        {"HXAK4811H1614", "KA4811H 써니오렌지", "140"},
        {"XFK2JL1005093L", "XFK2JL1005 블랙", "XXXL(99반~100)"},
        {"HXAK1013G0016", "KA1013G 블랙", "160"},
        {"XFK3JL100430S", "XFK3JL1004 화이트블루", "S(44~55)"},
        {"XFK3TU120529L", "XFK3TU1205 라이트블루", "L(66반~77)"},
        {"XMMST01H222L", "XMMST01H2 베이지", "L"},
        {"XFK2JL100129XL", "XFK2JL1001 펄블루", "XL(77~88)"},
        {"10480420006", "XP9172F 시안블루", "XXXL(99반~100)"},
        {"XFK1PT110309M", "XFK1PT1103 블랙", "M(55반~66)"},
        {"XMK4PP120209M", "XMK4PP1202 블랙", "M"},
        {"XFK1JL110109XL", "XFK1JL1101 블랙", "XL(77~88)"},
        {"XMK4TL1101163L", "XMK4TL1101 베이지", "XXXL"},
        {"XGFJK10J322S", "XGFJK10J3 베이지", "S(44~55)"},
        {"XUK2AB160521F", "XUK2AB1605 네온바비핑크", "네온바비핑크"},
        {"XWFLG10J308XL", "XWFLG10J3 포레스트그레이", "XL(77~88)"},
        {"FXAA5353F22F", "XA5353F 뉴트럴베이지", "뉴트럴베이지"},
        {"XFK2OP110233M", "XFK2OP1102 브로큰네이비", "M(55반~66)"},
        {"XMK3TE100132XL", "XMK3TE1001 블루인디고", "XL"},
        {"HXTA4354G52S", "XT4354G 플러피블루", "S(44~55)"},
        {"XFK2JL110209L", "XFK2JL1102 블랙", "L(66반~77)"},
        {"XFK2TS1104122L", "XFK2TS1104 라임티", "XXL(88반~99)"},
        {"FWPA9230H01L", "WP9230H 블랙", "L(66반~77)"},
        {"XMK2TS1201292L", "XMK2TS1201 라이트블루", "XXL"},
        {"10881120003", "XA5370G 썬더그레이", "L(66반~77)"},
        {"FXTC2220H21XL", "XT2220H 아미베이지", "XL"},
        {"FXPA9193F522L", "XP9193F 아틀란틱블루", "XXL(88반~99)"},
        {"XFK3LL150430S", "XFK3LL1504 벨에어블루", "S(44~55)"},
        {"XGFUY01H310M", "XGFUY01H3 블랙", "M(55반~66)"},
        {"XKFLG02H30914", "XKFLG02H3 제트차콜", "140"},
        {"XUK3SH180120245", "XUK3SH1801 베이비핑크", "245"},
        {"XTFJK02J111M", "XTFJK02J1 멜로우레몬", "M(55반~66)"},
        {"FWPA9214G32S", "WP9214G 토마토쥬스", "S(44~55)"},
        {"XFK1RK120501XS", "XFK1RK1205 화이트", "XS(44)"},
        {"XUK4PT130329160", "XUK4PT1303 올블루", "160"},
        {"XKURT52J20916", "XKURT52J2 차콜그레이", "160"},
        {"XWFTP04J308XL", "XWFTP04J3 스토니차콜", "XL(77~88)"},
        {"XFK2PS150132S", "XFK2PS1501 보이지네이비", "S(44~55)"},
        {"XWFLG01H358L", "XWFLG01H3 모던네이비", "L(66반~77)"},
        {"10964320002", "XS0302H X-레디폼 슬라이드 크림베이지", "240"},
        {"XTFBR01H2312L", "XTFBR01H2 바비핑크", "XXL(88반~99)"},
        {"XFK2BC150409L", "XFK2BC1504 블랙", "L(66반~77)"},
        {"FWAA5458H71M", "WA5458H 서프블루", "M(55반~66)"},
        {"XFK2RK110109S", "XFK2RK1101 블랙", "S(44~55)"},
        {"10495020003", "XA5203T 노티컬블루", "L(66반~77)"},
        {"10466320004", "XP9170F 블랙", "XL(77~88)"},
        {"XFK4JP1007092L", "XFK4JP1007 블랙", "XXL(88반~99)"},
        {"XFK2TS100102S", "XFK2TS1001 백아이보리", "S(44~55)"},
        {"XFK5UB150101L", "XFK5UB1501 뉴트럴아이보리", "L(66반~77)"},
        {"10155020002", "XT8102E 블랙", "M(55반~66)"},
        {"XMK2PP110116M", "XMK2PP1101 베이지", "M"},
        {"XTMKT01J307M", "XTMKT01J3 그레이", "M"},
        {"XMK1PP110409XL", "XMK1PP1104 블랙", "XL"},
        {"XFK4TL110203S", "XFK4TL1102 러스트베이지", "S(44~55)"},
        {"XFK1PT1102042L", "XFK1PT1102 애시베이지", "XXL(88반~99)"},
        {"XTFSH20J103M", "XTFSH20J1 아이보리", "M(55반~66)"},
        {"FXTC2192G113L", "XT2192G 윈드다크그레이", "XXXL"},
        {"XFK1PP120109XL", "XFK1PP1201 블랙", "XL(77~88)"},
        {"XFK1HC110120S", "XFK1HC1101 핑크팝콘", "S(44~55)"},
        {"XWFSG10H253XL", "XWFSG10H2 그레이블루", "XL(77~88)"},
        {"XSFWO10J252S", "XSFWO10J2 빅토리아블루", "S(44~55)"},
        {"XGMLT03J3102L", "XGMLT03J3 미드나잇블랙", "XXL"},
        {"FXAA5375G02L", "XA5375G 백아이보리", "L(66반~77)"},
        {"XSMRT52J202M", "XSMRT52J2 화이트", "M"},
        {"XFK3TS100238S", "XFK3TS1002 데저트토프", "S(44~55)"},
        {"XFK2PS110315L", "XFK2PS1103 롤링베이지", "L(66반~77)"},
        {"XFK4RK120109L", "XFK4RK1201 블랙", "L(66반~77)"},
        {"XFK5UB1501022L", "XFK5UB1501 파우더크림", "XXL(88반~99)"},
        {"FXTC2171G11L", "XT2171G 시티그레이", "L"},
        {"XWFBR05J1102L", "XWFBR05J1 블랙", "XXL(88반~99)"},
        {"XMMGJ02J109XL", "XMMGJ02J1 차콜", "XL"},
        {"XFK2TE100502S", "XFK2TE1005 백아이보리", "S(44~55)"},
        {"FXTC1001T63XL", "XT1001T 언더민트", "XL"},
        {"XWFSH01J137M", "XWFSH01J1 블러쉬레드", "M(55반~66)"},
        {"XTMLT01J3582L", "XTMLT01J3 네이비", "XXL"},
        {"XKUST03J23215", "XKUST03J2 다이아몬드핑크", "150"},
        {"XTFJK05J3222L", "XTFJK05J3 피넛버터", "XXL(88반~99)"},
        {"XFK1TS1004063L", "XFK1TS1004 아테네그레이", "XXXL(99반~100)"},
        {"XFK2BC150434S", "XFK2BC1504 밀키그린", "S(44~55)"},
        {"XFK3RK120432XL", "XFK3RK1204 네이비", "XL(77~88)"},
        {"XWFTP03J1673L", "XWFTP03J1 고딕그린", "XXXL(99반~100)"},
        {"XWFST02J2123L", "XWFST02J2 프리지아옐로우", "XXXL(99반~100)"},
        {"XFK4PP110509S", "XFK4PP1105 블랙", "S(44~55)"},
        {"10621320001", "XEB210C 심볼 크루삭스 페퍼그린", "S-M"},
        {"XFK4JP120109XL", "XFK4JP1201 블랙", "XL(77~88)"},
        {"XFK3LL150309M", "XFK3LL1503 블랙", "M(55반~66)"},
        {"XMK4PP1105092L", "XMK4PP1105 블랙", "XXL"},
        {"XTFJK09H321XL", "XTFJK09H3 마쉬멜로우", "XL(77~88)"},
        {"XKULG01J34116", "XKULG01J3 베르트민트", "160"},
        {"FXTA4190T21XL", "XT4190T 백아이보리", "XL(77~88)"},
        {"FXTK4551H4716", "KT4551H 멜로우오렌지", "160"},
        {"XTFSH12J310L", "XTFSH12J3 블랙", "L(66반~77)"},
        {"XWFLG04J101XL", "XWFLG04J1 블랙", "XL(77~88)"},
        {"XFK3PS112215S", "XFK3PS1122 아이스바닐라", "S(44~55)"},
        {"XGMDJ02J310M", "XGMDJ02J3 블랙", "M"},
        {"XWMJK01J302L", "XWMJK01J3 화이트", "L"},
        {"XWFTP01J158M", "XWFTP01J1 콜드네이비", "M(55반~66)"},
        {"XAUZZ07J232F", "XAUZZ07J2 솔티핑크", "솔티핑크"},
        {"XTFJK10J168M", "XTFJK10J1 내추럴민트", "M(55반~66)"},
        {"XFK2PS120211L", "XFK2PS1202 옐로우", "L(66반~77)"},
        {"XTFST03J2632L", "XTFST03J2 파우더그린", "XXL(88반~99)"},
        {"XFL1JL100302S", "XFL1JL1003 백아이보리", "S(44~55)"},
        {"HWPK5103H0013", "KP5103H 블랙", "130"},
        {"HXJA0114F33XL", "XJ0114F 핑키베이지", "XL(77~88)"},
        {"XMK4PT1101172L", "XMK4PT1101 다크베이지", "XXL"},
        {"FXPA9170F74XL", "XP9170F 오션블루", "XL(77~88)"},
        {"XTFST07J203M", "XTFST07J2 푸딩크림", "M(55반~66)"},
        {"10671320003", "XP9198G 로즈힙", "L(66반~77)"},
        {"XMK2TS120109L", "XMK2TS1201 블랙", "L"},
        {"10490020001", "XT4316F 퓨어블루", "S(44~55)"},
        {"XWFHJ02H363M", "XWFHJ02H3 어텐션그린", "M(55반~66)"},
        {"XFK1RK1105212L", "XFK1RK1105 핑크퍼센트", "XXL(88반~99)"},
        {"XFK3BL150404XL", "XFK3BL1504 미스트그레이", "XL(77~88)"},
        {"XMK1JL1001093L", "XMK1JL1001 블랙", "XXXL"},
        {"XTFLT02J321XL", "XTFLT02J3 글로시베이지", "XL(77~88)"},
        {"XFK3TL110709L", "XFK3TL1107 블랙", "L(66반~77)"},
        {"XMMJK11J2512L", "XMMJK11J2 스카이블루", "XXL"},
        {"XFL2TE1013212L", "XFL2TE1013 팝핑크", "XXL(88반~99)"},
        {"FXTC2188G21XL", "XT2188G 콜드화이트", "XL"},
        {"FXTC2196G212L", "XT2196G 화이트", "XXL"},
        {"XMK2QS1101093L", "XMK2QS1101 블랙", "XXXL"},
        {"XTFTP06J110S", "XTFTP06J1 블랙", "S(44~55)"},
        {"FXPC2140F61M", "XP2140F 이지딥카키", "M"},
        {"XMK2PS1105313L", "XMK2PS1105 딥블루", "XXXL"},
        {"XSFRT01J210L", "XSFRT01J2 블랙", "L(66반~77)"},
        {"XFK3JL100121S", "XFK3JL1001 피치퓨레", "S(44~55)"},
        {"XMMST03H310XL", "XMMST03H3 블랙", "XL"},
        {"XFK2JL1101262L", "XFK2JL1101 아이스오키드", "XXL(88반~99)"},
        {"XWFLG03H307XL", "XWFLG03H3 미드그레이", "XL(77~88)"},
        {"XGFUY01H303XL", "XGFUY01H3 아이보리", "XL(77~88)"},
        {"XFK2LL150429S", "XFK2LL1504 클라우드딥블루", "S(44~55)"},
        {"XGFLG01J310M", "XGFLG01J3 블랙", "M(55반~66)"},
        {"10568720001", "XT4316F 다크나이트", "S(44~55)"},
        {"XFK3RK120920L", "XFK3RK1209 라이트핑크", "L(66반~77)"},
    };

    
    private static final String CHANNEL_CODE = "COUPANG";
    private final SalesChannelRepository salesChannelRepository;
    private final Random random = new Random();
    
    @Override
    public String getChannelCode() {
        return CHANNEL_CODE;
    }
    
    @Override
    public List<CollectedOrder> collectOrders(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("🔹 [Mock] 쿠팡 주문 수집 시작: {} ~ {}", startDate, endDate);
        
        try {
            int orderCount = 1 + random.nextInt(3);
            List<CollectedOrder> orders = new ArrayList<>();
            
            SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
                .orElseThrow(() -> new RuntimeException("쿠팡 판매처를 찾을 수 없습니다"));
            
            for (int i = 0; i < orderCount; i++) {
                CollectedOrder order = generateMockOrder(channel);
                orders.add(order);
            }
            
            log.info("✅ [Mock] 쿠팡 주문 {} 건 수집 완료", orders.size());
            return orders;
            
        } catch (Exception e) {
            log.error("❌ [Mock] 쿠팡 주문 수집 실패", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public CollectedOrder getOrder(String channelOrderNo) {
        log.info("🔹 [Mock] 쿠팡 단일 주문 조회: {}", channelOrderNo);
        
        SalesChannel channel = salesChannelRepository.findByChannelCode(CHANNEL_CODE)
            .orElseThrow(() -> new RuntimeException("쿠팡 판매처를 찾을 수 없습니다"));
        
        return generateMockOrder(channel);
    }
    
    @Override
    public boolean testConnection() {
        log.info("🔹 [Mock] 쿠팡 연결 테스트");
        return true;
    }
    
    private CollectedOrder generateMockOrder(SalesChannel channel) {
        String orderNo = "CP-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
        
        CollectedOrder order = CollectedOrder.builder()
            .channelId(channel.getChannelId())
            .channelCode(CHANNEL_CODE)
            .channelOrderNo(orderNo)
            .customerName("쿠팡고객" + random.nextInt(100))
            .customerPhone(String.format("010-%04d-%04d", random.nextInt(10000), random.nextInt(10000)))
            .customerEmail("coupang" + random.nextInt(1000) + "@test.com")
            .recipientName("쿠팡수령인" + random.nextInt(100))
            .recipientPhone(String.format("010-%04d-%04d", random.nextInt(10000), random.nextInt(10000)))
            .postalCode(String.format("%05d", random.nextInt(100000)))
            .address("경기도 성남시 분당구 쿠팡로 " + (random.nextInt(500) + 1))
            .addressDetail(random.nextInt(10) + "동 " + random.nextInt(1000) + "호")
            .deliveryMemo("로켓배송 부탁드립니다")
            .status("PAYED")
            .paymentStatus("PAID")
            .paymentMethod("CARD")
            .orderedAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .paidAt(LocalDateTime.now().minusHours(random.nextInt(24)))
            .build();
        
        // 주문 상품 - 실제 재고 바코드 기반
        String[] prod = REAL_PRODUCTS[random.nextInt(REAL_PRODUCTS.length)];
        CollectedOrderItem item = CollectedOrderItem.builder()
            .channelProductCode(prod[0])
            .productName(prod[1])
            .optionName(prod[2])
            .quantity(1 + random.nextInt(2))
            .unitPrice(new BigDecimal(35000 + random.nextInt(30000)))
            .barcode(prod[0])
            .sku(prod[0])
            .build();
        
        item.calculateTotalPrice();
        order.addItem(item);
        
        // 금액 계산
        order.setTotalAmount(item.getTotalPrice());
        order.setShippingFee(BigDecimal.ZERO); // 쿠팡은 무료배송
        order.setDiscountAmount(new BigDecimal(random.nextInt(3000)));
        order.setPaymentAmount(order.getTotalAmount().subtract(order.getDiscountAmount()));
        
        // 원본 JSON은 null로 설정 → RawOrderService가 전체 객체를 JSON으로 변환
        order.setRawJson(null);
        
        return order;
    }
}
