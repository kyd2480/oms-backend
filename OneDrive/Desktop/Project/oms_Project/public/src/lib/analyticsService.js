import { fetchWithRetry } from './http';

const API_BASE = 'https://oms-backend-production-8a38.up.railway.app/api/processing';

export async function fetchAnalyticsOverview(range = '7d') {
    try {
        // OMS 통계 API 호출
        const res = await fetchWithRetry(`${API_BASE}/stats`, {
            retries: 2,
            timeoutMs: 6000,
            headers: {
                'Content-Type': 'application/json',
            },
            onRetry: (info) => {
                console.debug('[Analytics Retry]', info);
            },
        });

        if (!res.ok) throw new Error(`API error: ${res.status}`);

        const data = await res.json();

        // OMS 응답을 대시보드 형식으로 변환
        const transformed = transformOmsData(data, range);
        return { data: transformed, isSample: false };
    } catch (err) {
        console.warn('[Analytics] API 실패, 샘플 데이터 사용:', err.message);
        return { data: sampleData(), isSample: true };
    }
}

/**
 * OMS API 응답을 대시보드 형식으로 변환
 */
function transformOmsData(omsData, range) {
    // OMS stats 응답 예시:
    // {
    //   totalOrders: 15,
    //   todayOrders: 3,
    //   unprocessedOrders: 0,
    //   channelStats: [...]
    // }

    return {
        totalOrders: omsData.totalOrders || 0,
        totalAmount: 0, // OMS에 금액 합계 없으면 0
        shipped: Math.floor((omsData.totalOrders || 0) * 0.8), // 추정치
        canceled: 0,
        byMall: transformChannelStats(omsData.channelStats),
        byStatus: [
            { status: '결제완료', count: omsData.todayOrders || 0 },
            { status: '출고대기', count: omsData.unprocessedOrders || 0 },
            { status: '출고완료', count: (omsData.totalOrders || 0) - (omsData.unprocessedOrders || 0) },
            { status: '취소/반품', count: 0 },
        ],
        range,
    };
}

/**
 * 판매처 통계 변환
 */
function transformChannelStats(channelStats) {
    if (!channelStats || !Array.isArray(channelStats)) {
        return [];
    }

    return channelStats.map(ch => ({
        mall: ch.channelName || ch.channelCode || '알 수 없음',
        orders: ch.orderCount || 0,
        amount: 0, // 금액 정보 없음
    }));
}

/**
 * 샘플 데이터 (API 실패 시)
 */
function sampleData() {
    return {
        totalOrders: 128,
        totalAmount: 3520000,
        shipped: 102,
        canceled: 6,
        byMall: [
            { mall: '네이버', orders: 54, amount: 1450000 },
            { mall: '쿠팡', orders: 39, amount: 980000 },
            { mall: '11번가', orders: 21, amount: 720000 },
            { mall: 'G마켓', orders: 14, amount: 370000 },
        ],
        byStatus: [
            { status: '결제완료', count: 18 },
            { status: '출고대기', count: 22 },
            { status: '출고완료', count: 102 },
            { status: '취소/반품', count: 6 },
        ],
    };
}
