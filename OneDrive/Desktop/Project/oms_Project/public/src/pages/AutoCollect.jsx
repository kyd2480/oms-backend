import { useState } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

// ⚠️ CSV 재고 데이터 (30,833개 중 상위 5,000개)
// 실제 운영 시 정식 입고 프로세스로 교체
import STOCK_DATA from '../data/stock_data.json';

// ⚠️ 테스트 주문 데이터 (재고 기반 생성)
// 실제 쇼핑몰 API 연결 시 이 데이터와 insertTestOrders 함수 제거
const TEST_ORDERS = [{"productName":"XMK4PP1104 \ub124\uc774\ube44","optionName":"XXL","barcode":"XMK4PP1104322L","quantity":3,"receiverName":"\uc870\ud604\uc6b0","receiverPhone":"010-5678-9012","address":"\uc11c\uc6b8\uc2dc \ub9c8\ud3ec\uad6c \ud569\uc815\ub3d9 456","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":120000},{"productName":"XFL1LL1503 \ube14\ub799","optionName":"XL(77~88)","barcode":"XFL1LL150309XL","quantity":1,"receiverName":"\uac15\ub2e4\uc740","receiverPhone":"010-6789-0123","address":"\ubd80\uc0b0\uc2dc \ud574\uc6b4\ub300\uad6c \uc6b0\ub3d9 101","channel":"11\ubc88\uac00","salePrice":80000},{"productName":"XFK1TL1104 \uba5c\ub780\uc9c0\uadf8\ub808\uc774","optionName":"S(44~55)","barcode":"XFK1TL110405S","quantity":2,"receiverName":"\uc784\uc9c0\uc218","receiverPhone":"010-7890-1234","address":"\ub300\uc804\uc2dc \uc720\uc131\uad6c \uad81\ub3d9 505","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":65000},{"productName":"\uc694\uac00\ub9e4\ud2b8\ud0c8 \ub808\uae45\uc2a4 \ube14\ub799","optionName":"M","barcode":"FAKE001","quantity":1,"receiverName":"\uc624\ud0dc\uc591","receiverPhone":"010-8901-2345","address":"\ub300\uad6c\uc2dc \uc218\uc131\uad6c \ubc94\uc5b4\ub3d9 303","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":45000},{"productName":"XFL5UP202229M \ubc14\ub2e4\ube14\ub8e8","optionName":"M(55\ubc18~66)","barcode":"XFL5UP20222908M","quantity":2,"receiverName":"\ucd5c\uc608\uc740","receiverPhone":"010-2345-6789","address":"\uc11c\uc6b8\uc2dc \uac15\ub0a8\uad6c \uc5ed\uc0bc\ub3d9 123","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":95000},{"productName":"\uc2a4\ud3ec\uce20 \ube0c\ub77c \ud654\uc774\ud2b8","optionName":"S","barcode":"FAKE004","quantity":1,"receiverName":"\ubc15\uc9c0\ud638","receiverPhone":"010-3456-7890","address":"\uacbd\uae30\ub3c4 \uc131\ub0a8\uc2dc \ubd84\ub2f9\uad6c \uc815\uc790\ub3d9 789","channel":"11\ubc88\uac00","salePrice":35000},{"productName":"XFL1TL1003 \ucfe0\uce20\ud551\ud06c","optionName":"L(66\ubc18~77)","barcode":"XFL1TL100321L","quantity":1,"receiverName":"\uc815\ubbfc\uc11c","receiverPhone":"010-9012-3456","address":"\uc778\ucc9c\uc2dc \ub0a8\ub3d9\uad6c \uad6c\uc6d4\ub3d9 202","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":75000},{"productName":"XFL1TL1003 \ube14\ub799","optionName":"M(55\ubc18~66)","barcode":"XFL1TL100309M","quantity":3,"receiverName":"\uae40\ubbfc\uc900","receiverPhone":"010-1234-5678","address":"\uad11\uc8fc\uc2dc \uc11c\uad6c \ud654\uc815\ub3d9 404","channel":"11\ubc88\uac00","salePrice":75000},{"productName":"\uc81c\uc2dc\ubbf9\uc2a4 \ud6c4\ub514 \ub124\uc774\ube44","optionName":"L","barcode":"FAKE002","quantity":2,"receiverName":"\uc774\uc11c\uc5f0","receiverPhone":"010-0123-4567","address":"\uc11c\uc6b8\uc2dc \ub9c8\ud3ec\uad6c \ud569\uc815\ub3d9 456","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":89000},{"productName":"XFL1TL1003 \ubc31\uc544\uc774\ubcf4\ub9ac","optionName":"S(44~55)","barcode":"XFL1TL100302S","quantity":1,"receiverName":"\uc2e0\uc7ac\uc6d0","receiverPhone":"010-4567-8901","address":"\uc11c\uc6b8\uc2dc \uac15\ub0a8\uad6c \uc5ed\uc0bc\ub3d9 123","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":75000},{"productName":"XFL0UB3505 \ube14\ub799","optionName":"M(55\ubc18~66)","barcode":"NOINSTOCK001","quantity":2,"receiverName":"\ud55c\uc18c\ud76c","receiverPhone":"010-5678-9012","address":"\ubd80\uc0b0\uc2dc \ud574\uc6b4\ub300\uad6c \uc6b0\ub3d9 101","channel":"11\ubc88\uac00","salePrice":68000},{"productName":"\ud2b8\ub808\uc774\ub2dd \ud31c\uce20 \uadf8\ub808\uc774","optionName":"XL","barcode":"FAKE003","quantity":1,"receiverName":"\ubc30\uc18c\ud76c","receiverPhone":"010-6789-0123","address":"\ub300\uc804\uc2dc \uc720\uc131\uad6c \uad81\ub3d9 505","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":55000},{"productName":"XFL2TE1016 \uc2a4\ud1a4\uc6cc\uc2dc\uadf8\ub808\uc774","optionName":"XL(77~88)","barcode":"XFL2TE101606XL","quantity":1,"receiverName":"\ubb38\uc900\ud601","receiverPhone":"010-7890-1234","address":"\ub300\uad6c\uc2dc \uc218\uc131\uad6c \ubc94\uc5b4\ub3d9 303","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":82000},{"productName":"NOINSTOCK \uc0c1\ud488","optionName":"L","barcode":"NOINSTOCK002","quantity":3,"receiverName":"\uc870\ud604\uc6b0","receiverPhone":"010-8901-2345","address":"\uc11c\uc6b8\uc2dc \ub9c8\ud3ec\uad6c \ud569\uc815\ub3d9 456","channel":"11\ubc88\uac00","salePrice":42000},{"productName":"XFL1TL1003 \ucfe0\uce20\ud551\ud06c","optionName":"XXL(88\ubc18~99)","barcode":"XFL1TL1003212L","quantity":2,"receiverName":"\ucd5c\uc608\uc740","receiverPhone":"010-9012-3456","address":"\uacbd\uae30\ub3c4 \uc131\ub0a8\uc2dc \ubd84\ub2f9\uad6c \uc815\uc790\ub3d9 789","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":75000},{"productName":"\ub7f0\ub2dd\ud654 \ube14\ub8e8","optionName":"250","barcode":"FAKE005","quantity":1,"receiverName":"\ubc15\uc9c0\ud638","receiverPhone":"010-0123-4567","address":"\uc778\ucc9c\uc2dc \ub0a8\ub3d9\uad6c \uad6c\uc6d4\ub3d9 202","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":120000},{"productName":"XFL1TL1003 \ubc31\uc544\uc774\ubcf4\ub9ac","optionName":"XXL(88\ubc18~99)","barcode":"XFL1TL1003022L","quantity":1,"receiverName":"\uae40\ubbfc\uc900","receiverPhone":"010-1234-5678","address":"\uad11\uc8fc\uc2dc \uc11c\uad6c \ud654\uc815\ub3d9 404","channel":"11\ubc88\uac00","salePrice":75000},{"productName":"ZERO STOCK \uc0c1\ud488A","optionName":"S","barcode":"NOINSTOCK003","quantity":2,"receiverName":"\uc774\uc11c\uc5f0","receiverPhone":"010-2345-6789","address":"\uc11c\uc6b8\uc2dc \uac15\ub0a8\uad6c \uc5ed\uc0bc\ub3d9 123","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":38000},{"productName":"XFL1TL1003 \ube14\ub799","optionName":"XL(77~88)","barcode":"XFL1TL100309XL","quantity":1,"receiverName":"\uc815\ubbfc\uc11c","receiverPhone":"010-3456-7890","address":"\ubd80\uc0b0\uc2dc \ud574\uc6b4\ub300\uad6c \uc6b0\ub3d9 101","channel":"\uc2a4\ub9c8\ud2b8\uc2a4\ud1a0\uc5b4","salePrice":75000},{"productName":"ZERO STOCK \uc0c1\ud488B","optionName":"M","barcode":"NOINSTOCK004","quantity":1,"receiverName":"\uc2e0\uc7ac\uc6d0","receiverPhone":"010-4567-8901","address":"\ub300\uc804\uc2dc \uc720\uc131\uad6c \uad81\ub3d9 505","channel":"11\ubc88\uac00","salePrice":55000}];

const S = {
  page: { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card: { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem',
          boxShadow:'0 1px 4px rgba(0,0,0,0.06)' },
  bBlue:  { padding:'0.5rem 1.5rem', background:'#1976d2', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  bGreen: { padding:'0.5rem 1.5rem', background:'#2e7d32', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  bRed:   { padding:'0.5rem 1.5rem', background:'#c62828', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  bGray:  { padding:'0.5rem 1.5rem', background:'#e0e0e0', color:'#333', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem' },
};

export default function AutoCollect() {
  const [loading,  setLoading]  = useState(false);
  const [msg,      setMsg]      = useState('');
  const [progress, setProgress] = useState({ show: false, current: 0, total: 0, label: '' });
  const [msgType, setMsgType] = useState(''); // success | error

  const showMsg = (text, type = 'success') => { setMsg(text); setMsgType(type); };
  const showProgress = (label, current, total) => setProgress({ show: true, label, current, total });
  const hideProgress = () => setProgress({ show: false, current: 0, total: 0, label: '' });

  // ── 전체 수집 (실제 API) ─────────────────────────────────────
  const collectAll = async () => {
    setLoading(true); setMsg('');
    try {
      const res  = await fetch(`${API_BASE}/api/collection/collect-all`, { method:'POST' });
      const data = await res.json();
      showMsg(data.message || '수집 완료');
    } catch(e) { showMsg('수집 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] 테스트 주문 삽입 ──────────────────────────────────
  // ⚠️ 실제 API 연결 후 아래 함수와 버튼 제거
  const insertTestOrders = async () => {
    if (!window.confirm(`재고 기반 테스트 주문 ${TEST_ORDERS.length}건을 삽입하시겠습니까?\n(재고있음 / 재고없음 / 미매칭 혼합)`)) return;
    setLoading(true); setMsg('');
    try {
      const res  = await fetch(`${API_BASE}/api/test/orders/insert`, {
        method:'POST',
        headers: { 'Content-Type':'application/json' },
        body: JSON.stringify(TEST_ORDERS),
      });
      const data = await res.json();
      showMsg(`✅ ${data.saved}건 삽입 완료${data.skipped > 0 ? ` (${data.skipped}건 스킵)` : ''}`);
    } catch(e) { showMsg('삽입 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] 테스트 주문 삭제 ──────────────────────────────────
  const clearTestOrders = async () => {
    if (!window.confirm('TEST- 로 시작하는 테스트 주문을 전부 삭제하시겠습니까?')) return;
    setLoading(true); setMsg('');
    showProgress('테스트 주문 삭제 중...', 0, 100);
    try {
      const res  = await fetch(`${API_BASE}/api/test/orders/clear`, { method:'DELETE' });
      const data = await res.json();
      hideProgress();
      showMsg(`✅ ${data.deleted}건 삭제 완료`);
    } catch(e) { hideProgress(); showMsg('삭제 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] 전체 주문 삭제 ──────────────────────────────────────
  const clearAllOrders = async () => {
    if (!window.confirm('⚠️ 전체 주문을 삭제합니다.\n이 작업은 복구가 불가능합니다. 계속하시겠습니까?')) return;
    if (!window.confirm('정말로 전체 삭제하시겠습니까?')) return;
    setLoading(true); setMsg('');
    showProgress('전체 주문 삭제 중...', 0, 100);
    try {
      const res  = await fetch(`${API_BASE}/api/test/orders/clear-all`, { method:'DELETE' });
      const data = await res.json();
      hideProgress();
      showMsg(`✅ ${data.deleted}건 전체 삭제 완료`);
    } catch(e) { hideProgress(); showMsg('삭제 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] 잘못된 CONFIRMED 상태 복구 ──────────────────────────
  const resetStatus = async () => {
    if (!window.confirm('송장번호 없이 CONFIRMED된 주문을 PENDING으로 복구합니다.\n계속하시겠습니까?')) return;
    setLoading(true); setMsg('');
    showProgress('주문 상태 복구 중...', 0, 100);
    try {
      const res  = await fetch(`${API_BASE}/api/test/orders/reset-status`, { method:'PATCH' });
      const data = await res.json();
      hideProgress();
      showMsg(`✅ ${data.fixed}건 PENDING으로 복구 완료`);
    } catch(e) { hideProgress(); showMsg('복구 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] 잘못된 product_code 초기화 ────────────────────────
  const fixProductCodes = async () => {
    if (!window.confirm('11ST-PRD-xxx, NAVER-PRD-xxx 등 쇼핑몰 상품코드를 product_code에서 제거합니다.\n상품명 매칭이 정상 동작하게 됩니다. 계속하시겠습니까?')) return;
    setLoading(true); setMsg('');
    showProgress('상품코드 오류 수정 중... (시간이 걸릴 수 있습니다)', 0, 100);
    try {
      const res  = await fetch(`${API_BASE}/api/test/orders/fix-product-codes`, { method:'PATCH' });
      const data = await res.json();
      hideProgress();
      showMsg(`✅ ${data.fixed}건 초기화 완료`);
    } catch(e) { hideProgress(); showMsg('초기화 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  // ── [TEST] CSV 재고 일괄 입고 ────────────────────────────────
  // ⚠️ 실제 운영 시 정식 입고 프로세스로 교체
  const inboundStock = async () => {
    if (!window.confirm(`CSV 재고 데이터 ${STOCK_DATA.length}개를 입고 처리합니다.\n기존 재고에 추가됩니다. 계속하시겠습니까?`)) return;
    setLoading(true); setMsg('');
    try {
      const CHUNK = 500;
      let totalUpdated = 0;
      let totalNotFound = 0;
      for (let i = 0; i < STOCK_DATA.length; i += CHUNK) {
        const chunk = STOCK_DATA.slice(i, i + CHUNK);
        showProgress('재고 입고 중...', Math.min(i + CHUNK, STOCK_DATA.length), STOCK_DATA.length);
        const res  = await fetch(`${API_BASE}/api/test/stock/inbound`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(chunk),
        });
        const data = await res.json();
        totalUpdated  += data.updated  || 0;
        totalNotFound += data.notFound || 0;
      }
      hideProgress();
      showMsg(`✅ ${totalUpdated}개 재고 입고 완료 (미발견: ${totalNotFound}개)`);
    } catch(e) { hideProgress(); showMsg('입고 실패: ' + e.message, 'error'); }
    finally { setLoading(false); }
  };

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>
          주문 수집
        </h1>
        <div style={{ fontSize:'0.875rem', color:'#999' }}>
          판매처별 주문을 수집합니다
        </div>
      </div>

      {/* 알림 */}
      {msg && (
        <div style={{ padding:'0.85rem 1.25rem', borderRadius:'6px', marginBottom:'1rem',
          fontSize:'0.875rem', fontWeight:600,
          background: msgType==='error' ? '#ffebee' : '#e8f5e9',
          border: `1px solid ${msgType==='error' ? '#ffcdd2' : '#a5d6a7'}`,
          color: msgType==='error' ? '#c62828' : '#1b5e20' }}>
          {msg}
          <button onClick={()=>setMsg('')}
            style={{float:'right', background:'none', border:'none', cursor:'pointer', color:'#666'}}>✕</button>
        </div>
      )}

      {/* 실제 수집 */}
      <div style={S.card}>
        <div style={{ fontSize:'0.95rem', fontWeight:700, color:'#1565c0',
          marginBottom:'1rem', display:'flex', alignItems:'center', gap:'0.5rem' }}>
          <span style={{width:9, height:9, borderRadius:'50%', background:'#1565c0', display:'inline-block'}}/>
          판매처 주문 수집
        </div>
        <div style={{ fontSize:'0.875rem', color:'#666', marginBottom:'1.5rem' }}>
          연결된 판매처(스마트스토어, 11번가 등)에서 신규 주문을 수집합니다.
        </div>
        <button onClick={collectAll} disabled={loading} style={S.bBlue}>
          {loading ? '⏳ 수집 중...' : '📥 전체 수집 실행'}
        </button>
      </div>

      {/* ⚠️ 테스트 전용 섹션 - 실제 API 연결 후 이 card 전체 제거 */}
      <div style={{ ...S.card, border:'2px dashed #ff9800' }}>
        <div style={{ display:'flex', alignItems:'center', gap:'0.5rem',
          marginBottom:'1rem' }}>
          <span style={{ background:'#ff9800', color:'#fff', padding:'0.15rem 0.5rem',
            borderRadius:'4px', fontSize:'0.75rem', fontWeight:700 }}>TEST ONLY</span>
          <span style={{ fontSize:'0.95rem', fontWeight:700, color:'#e65100' }}>
            테스트 데이터 관리
          </span>
          <span style={{ fontSize:'0.78rem', color:'#999', marginLeft:'auto' }}>
            ⚠️ 실제 API 연결 후 이 섹션 제거
          </span>
        </div>

        <div style={{ fontSize:'0.875rem', color:'#666', marginBottom:'1.25rem',
          background:'#fff8e1', padding:'0.75rem 1rem', borderRadius:'6px',
          border:'1px solid #ffe082' }}>
          재고 CSV 기반 테스트 주문 <strong>{TEST_ORDERS.length}건</strong>을 삽입합니다.
          <ul style={{ margin:'0.5rem 0 0', paddingLeft:'1.25rem', fontSize:'0.8rem', color:'#777' }}>
            <li>재고 있는 상품 (할당 성공 케이스)</li>
            <li>재고 0인 상품 (재고 부족 케이스)</li>
            <li>재고DB 미등록 상품 (미매칭 케이스)</li>
            <li style={{color:'#e65100'}}><strong>🔧 상품코드 오류 수정</strong>: 자동수집 주문의 product_code에 쇼핑몰 코드가 잘못 저장된 경우 초기화</li>
          </ul>
        </div>

        <div style={{ display:'flex', gap:'0.75rem' }}>
          <button onClick={insertTestOrders} disabled={loading} style={S.bGreen}>
            🧪 테스트 주문 {TEST_ORDERS.length}건 삽입
          </button>
          <button onClick={clearTestOrders} disabled={loading} style={S.bRed}>
            🗑️ 테스트 주문 삭제 (TEST-)
          </button>
          <button onClick={clearAllOrders} disabled={loading}
            style={{...S.bRed, background:'#7b1fa2'}}>
            ⚠️ 전체 주문 삭제
          </button>
          <button onClick={resetStatus} disabled={loading}
            style={{...S.bGray, border:'1px solid #1976d2', color:'#1565c0'}}>
            🔄 주문상태 복구 (CONFIRMED→PENDING)
          </button>
          <button onClick={fixProductCodes} disabled={loading}
            style={{...S.bGray, border:'1px solid #ff9800', color:'#e65100'}}>
            🔧 상품코드 오류 수정
          </button>
          <button onClick={inboundStock} disabled={loading}
            style={{...S.bGray, border:'1px solid #2e7d32', color:'#2e7d32'}}>
            📦 CSV 재고 입고 ({STOCK_DATA.length}개)
          </button>
        </div>
      </div>
    </div>
  );
}
