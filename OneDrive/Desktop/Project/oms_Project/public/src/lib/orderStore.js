/**
 * orderStore.js
 *
 * 주문 데이터 중앙 관리 레이어.
 *
 * ─── 데이터 소스 ────────────────────────────────────────────────
 * [자동수집] GET /api/processing/orders  ← 백엔드 수집 주문 (11번가, 스마트스토어 등)
 * [수동입력] sessionStorage 'oms_manual_orders' ← 직접입력/CSV 업로드 주문
 *
 * getOrders() 호출 시 두 소스를 합쳐서 반환.
 *
 * ─── API 전환 시 수정 위치 ───────────────────────────────────────
 * 이 파일만 수정하면 OrderInput, DuplicateCheck 등 전체 페이지 자동 적용.
 * ──────────────────────────────────────────────────────────────────
 */

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

const API_PROCESSING = `${API_BASE}/api/processing`;
const MANUAL_KEY     = 'oms_manual_orders';

// ─── 수동 입력 주문 캐시 ──────────────────────────────────────────
let _manualCache = null;

function _loadManual() {
  if (_manualCache !== null) return _manualCache;
  try {
    const raw = sessionStorage.getItem(MANUAL_KEY);
    _manualCache = raw ? JSON.parse(raw) : [];
  } catch { _manualCache = []; }
  return _manualCache;
}

function _saveManual(list) {
  _manualCache = list;
  try { sessionStorage.setItem(MANUAL_KEY, JSON.stringify(list)); } catch {}
  _notifyChange();
}

function _notifyChange() {
  window.dispatchEvent(new CustomEvent('oms_orders_changed'));
}

// ─── 백엔드 주문 → 공통 포맷 변환 ───────────────────────────────
function _normalizeApiOrder(o) {
  // ProcessingController Order 엔티티 구조에 맞게 매핑
  const item = o.items?.[0] || {};
  return {
    id:            `api_${o.orderId || o.orderNo}`,
    orderNo:       o.orderNo       || '',
    channel:       o.channel?.channelName || o.channelId || '',
    channelCode:   o.channel?.channelCode || '',
    receiverName:  o.recipientName  || o.customerName || '',
    receiverPhone: o.recipientPhone || o.customerPhone || '',
    address:       o.address        || o.addressDetail || '',
    productName:   item.productName || o.productName   || '',
    sku:           item.productCode || item.channelProductCode || '',
    barcode:       item.barcode     || '',
    optionName:    item.optionName  || '',
    quantity:      item.quantity    || o.quantity || 1,
    salePrice:     o.totalAmount    || o.paymentAmount || 0,
    memo:          o.deliveryMemo   || '',
    status:        _mapStatus(o.orderStatus),
    orderedAt:     o.orderedAt      || o.createdAt || '',
    createdAt:     o.createdAt      || '',
    source:        'api',           // 출처 표시
    rawOrderNo:    o.rawOrder?.channelOrderNo || '',
    duplicate:     o.duplicate      || false,
    matchedProductId: o.matchedProductId || null,
  };
}

function _mapStatus(s) {
  const map = {
    'PENDING':   '신규',
    'NEW':       '신규',
    'CONFIRMED': '확인',
    'SHIPPED':   '발송완료',
    'DELIVERED': '배송완료',
    'CANCELLED': '취소',
    'RETURNED':  '반품',
  };
  return map[s] || s || '신규';
}

// ═══════════════════════════════════════════════════════════════════
// PUBLIC API
// ═══════════════════════════════════════════════════════════════════

/**
 * 백엔드 수집 주문 조회 (페이지네이션)
 * GET /api/processing/orders?page=0&size=50
 * @returns { orders, totalElements, totalPages, currentPage }
 */
/**
 * 전체 주문 모두 가져오기 (페이지 반복 호출)
 * 중복검사 등 전체 데이터가 필요한 경우 사용
 * @param {function} onProgress (loaded, total) 진행 콜백
 */
export async function fetchAllApiOrders(onProgress) {
  const PAGE_SIZE = 200; // 한번에 많이 받아서 호출 수 최소화
  let allOrders = [];
  let page = 0;

  try {
    // 첫 페이지로 전체 건수 파악
    const first = await fetchApiOrders({ page: 0, size: PAGE_SIZE });
    allOrders = [...first.orders];
    if (onProgress) onProgress(allOrders.length, first.totalElements);

    const totalPages = first.totalPages;
    // 나머지 페이지 병렬 호출 (5개씩 묶어서)
    for (let p = 1; p < totalPages; p += 5) {
      const batch = Array.from({ length: Math.min(5, totalPages - p) }, (_, i) =>
        fetchApiOrders({ page: p + i, size: PAGE_SIZE })
      );
      const results = await Promise.all(batch);
      results.forEach(r => { allOrders = [...allOrders, ...r.orders]; });
      if (onProgress) onProgress(allOrders.length, first.totalElements);
    }
    return allOrders;
  } catch (e) {
    console.error('전체 주문 조회 실패:', e);
    return allOrders; // 부분 결과라도 반환
  }
}

export async function fetchApiOrders({ page = 0, size = 50 } = {}) {
  try {
    const res  = await fetch(`${API_PROCESSING}/orders?page=${page}&size=${size}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    // Spring Page 응답 처리
    if (data?.content) {
      return {
        orders:        data.content.map(_normalizeApiOrder),
        totalElements: data.totalElements || 0,
        totalPages:    data.totalPages    || 1,
        currentPage:   data.number       || 0,
      };
    }
    // 배열 응답 fallback
    const list = Array.isArray(data) ? data : [];
    return { orders: list.map(_normalizeApiOrder), totalElements: list.length, totalPages: 1, currentPage: 0 };
  } catch (e) {
    console.error('백엔드 주문 조회 실패:', e);
    return { orders: [], totalElements: 0, totalPages: 1, currentPage: 0 };
  }
}

/**
 * 수동 입력 주문 조회
 */
export function getManualOrders() {
  return [..._loadManual()];
}

/**
 * 전체 주문 조회 (백엔드 + 수동입력 합산)
 * 동기 버전 - 수동 주문만 반환 (이미 로드된 api 주문과 합산은 컴포넌트에서)
 */
export function getOrders() {
  return [..._loadManual()];
}

/**
 * 전체 주문 조회 (비동기 - 백엔드 포함)
 * 컴포넌트에서 useEffect로 호출
 */
export async function getAllOrders() {
  const [apiResult, manualOrders] = await Promise.all([
    fetchApiOrders(),
    Promise.resolve(_loadManual()),
  ]);
  // fetchApiOrders는 { orders, totalElements, ... } 객체 반환
  const apiOrders = Array.isArray(apiResult) ? apiResult : (apiResult?.orders || []);
  // 중복 orderNo 제거 (수동입력이 api보다 우선)
  const manualNos = new Set(manualOrders.map(o => o.orderNo));
  const filtered  = apiOrders.filter(o => !manualNos.has(o.orderNo));
  return [...manualOrders, ...filtered];
}

/**
 * 수동 주문 추가 - 백엔드에 저장 (비동기)
 * sessionStorage는 fallback용으로만 유지
 */
export async function addOrders(orders) {
  try {
    const res = await fetch(`${API_PROCESSING}/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orders),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    // 백엔드 저장 성공
    return data;
  } catch (e) {
    console.error('백엔드 주문 저장 실패 - sessionStorage fallback:', e);
    // fallback: 로컬 저장
    const current = _loadManual();
    _saveManual([...current, ...orders]);
    return { success: false, saved: 0, message: '로컬 저장됨 (백엔드 연결 실패)' };
  }
}

/**
 * 주문 업데이트 (id 기준)
 */
export function updateOrder(id, fields) {
  const next = _loadManual().map(o => o.id === id ? { ...o, ...fields } : o);
  _saveManual(next);
}

/**
 * 여러 주문 일괄 업데이트
 */
export function updateOrders(updates) {
  const map  = Object.fromEntries(updates.map(u => [u.id, u]));
  const next = _loadManual().map(o => map[o.id] ? { ...o, ...map[o.id] } : o);
  _saveManual(next);
}

/**
 * 주문 삭제
 */
export function deleteOrder(id) {
  _saveManual(_loadManual().filter(o => o.id !== id));
}

/**
 * 전체 수동 주문 삭제
 */
export function clearOrders() {
  _saveManual([]);
}

/**
 * 통계 (수동 주문 기준 동기)
 */
export function getOrderStats() {
  const orders = _loadManual();
  return {
    total:     orders.length,
    new:       orders.filter(o => o.status === '신규').length,
    cancelled: orders.filter(o => o.status === '취소').length,
    duplicate: orders.filter(o => o.duplicate).length,
    matched:   orders.filter(o => o.matchedProductId).length,
  };
}

/**
 * orderStore 변경 구독
 */
export function subscribeOrders(callback) {
  const handler = () => callback();
  window.addEventListener('oms_orders_changed', handler);
  return () => window.removeEventListener('oms_orders_changed', handler);
}

// ─── 상수 ────────────────────────────────────────────────────────
export const ORDER_STATUS = {
  NEW:       '신규',
  CONFIRMED: '확인',
  MATCHED:   '매칭완료',
  BUNDLED:   '묶음완료',
  ALLOCATED: '재고할당',
  SHIPPED:   '발송완료',
  CANCELLED: '취소',
};
