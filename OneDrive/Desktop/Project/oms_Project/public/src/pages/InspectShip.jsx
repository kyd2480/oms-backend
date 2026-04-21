import { useState, useEffect, useRef, useCallback } from 'react';
import { formatOrderSummary, formatProductLabel } from '../lib/orderDisplay';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
                 'https://oms-backend-production-8a38.up.railway.app';

/* ─── 스타일 토큰 ──────────────────────────────────────────── */
const C = {
  border:  '1px solid #d0d0d0',
  borderB: '1px solid #ebebeb',
  th: { padding:'5px 8px', background:'#efefef', border:'1px solid #d0d0d0',
        fontSize:'0.76rem', fontWeight:700, color:'#333', textAlign:'center', whiteSpace:'nowrap' },
  td: { padding:'4px 8px', border:'1px solid #ebebeb',
        fontSize:'0.78rem', color:'#222', textAlign:'center', whiteSpace:'nowrap' },
  label: { fontSize:'0.8rem', fontWeight:700, color:'#333', background:'#f5f5f5',
           border:'1px solid #d0d0d0', padding:'5px 10px', whiteSpace:'nowrap',
           display:'flex', alignItems:'center', minWidth:120 },
  inp: { padding:'4px 7px', border:'1px solid #bbb', borderRadius:2,
         fontSize:'0.82rem', color:'#111', outline:'none', background:'#fff' },
  btn: (bg, color='#fff') => ({
    padding:'4px 13px', background:bg, color,
    border:'1px solid ' + dk(bg), borderRadius:2,
    cursor:'pointer', fontSize:'0.8rem', fontWeight:600, whiteSpace:'nowrap',
  }),
};

function dk(hex) {
  if (!hex?.startsWith('#')) return '#999';
  const n=parseInt(hex.slice(1),16);
  const r=Math.max(0,((n>>16)&255)-28), g=Math.max(0,((n>>8)&255)-28), b=Math.max(0,(n&255)-28);
  return `#${((r<<16)|(g<<8)|b).toString(16).padStart(6,'0')}`;
}

/* ─── 날짜 포맷 ─────────────────────────────────────────────── */
function fmtDate(str) {
  if (!str) return '-';
  try {
    const d = new Date(str);
    const mm = String(d.getMonth()+1).padStart(2,'0');
    const dd = String(d.getDate()).padStart(2,'0');
    const hh = String(d.getHours()).padStart(2,'0');
    const mi = String(d.getMinutes()).padStart(2,'0');
    return `${d.getFullYear()}-${mm}-${dd} ${hh}:${mi}`;
  } catch { return str; }
}

function Badge({ text, bg='#eee', color='#444' }) {
  return (
    <span style={{ padding:'1px 8px', borderRadius:3, fontSize:'0.71rem',
      fontWeight:700, background:bg, color, border:`1px solid ${dk(bg)}` }}>
      {text}
    </span>
  );
}

/* ─── 토스트 ────────────────────────────────────────────────── */
function Toast({ msg, type='success', onClose }) {
  useEffect(() => {
    if (!msg) return;
    const t = setTimeout(onClose, 3500);
    return () => clearTimeout(t);
  }, [msg]);
  if (!msg) return null;
  const colors = {
    success: ['#e8f5e9','#1b5e20','#a5d6a7'],
    error:   ['#ffebee','#c62828','#ffcdd2'],
    warn:    ['#fff8e1','#e65100','#ffe082'],
    info:    ['#e3f2fd','#1565c0','#90caf9'],
  };
  const [bg, fg, bd] = colors[type] || colors.info;
  return (
    <div style={{ position:'fixed', top:16, left:'50%', transform:'translateX(-50%)', zIndex:9999,
      background:bg, color:fg, border:`1px solid ${bd}`, borderRadius:4,
      padding:'8px 20px', fontWeight:700, fontSize:'0.85rem',
      boxShadow:'0 3px 12px rgba(0,0,0,0.15)', display:'flex', alignItems:'center', gap:10 }}>
      {msg}
      <button onClick={onClose} style={{ background:'none', border:'none', cursor:'pointer', color:fg }}>✕</button>
    </div>
  );
}

/* ─── 출고 확인 모달 ────────────────────────────────────────── */
function CompleteModal({ order, onConfirm, onCancel }) {
  if (!order) return null;
  return (
    <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.45)', zIndex:9998,
      display:'flex', alignItems:'center', justifyContent:'center' }}>
      <div style={{ background:'#fff', border:'2px solid #1a7f37', borderRadius:8,
        padding:'1.5rem 2rem', minWidth:360, boxShadow:'0 6px 24px rgba(0,0,0,0.2)' }}>
        <div style={{ fontSize:'1.2rem', fontWeight:700, color:'#1a7f37', marginBottom:'0.5rem' }}>
          ✅ 검수 완료
        </div>
        <div style={{ fontSize:'0.85rem', color:'#555', marginBottom:'1rem' }}>
          모든 상품 스캔이 완료되었습니다. 출고 처리하시겠습니까?
        </div>
        <table style={{ width:'100%', fontSize:'0.82rem', borderCollapse:'collapse', marginBottom:'1rem' }}>
          {[
            ['수취인', order.recipientName],
            ['송장번호', order.trackingNo],
            ['택배사', order.carrierName],
          ].map(([k,v]) => (
            <tr key={k}>
              <td style={{ padding:'3px 8px', color:'#888', fontWeight:600, width:80 }}>{k}</td>
              <td style={{ padding:'3px 8px', color:'#111', fontWeight:600 }}>{v||'-'}</td>
            </tr>
          ))}
        </table>
        <div style={{ display:'flex', gap:8, justifyContent:'flex-end' }}>
          <button onClick={onCancel} style={C.btn('#757575')}>취소</button>
          <button onClick={onConfirm} style={C.btn('#1a7f37')}>🚚 출고 처리</button>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
 * 스캔 단계(phase)
 *   idle    : 송장번호 스캔 대기
 *   invoice : 송장 확인됨 → 상품 바코드 스캔 진행 중
 *   done    : 모든 상품 스캔 완료 → 출고 확인 대기
 * ══════════════════════════════════════════════════════════ */
export default function InspectShip() {
  const [mainTab,     setMainTab]     = useState('ship');
  const [completedOrders, setCompletedOrders] = useState([]);   // 송장삭제 탭 전용 (lazy)
  const [completedLoaded, setCompletedLoaded] = useState(false);
  const [shipped,     setShipped]     = useState([]);
  const [warehouses,        setWarehouses]        = useState([]);
  const [selectedWarehouse, setSelectedWarehouse] = useState(null);
  const [loading,     setLoading]     = useState(false);

  const [phase,        setPhase]        = useState('idle');
  const [activeOrder,  setActiveOrder]  = useState(null);
  const [itemRows,     setItemRows]     = useState([]);
  /*
   * itemRow: {
   *   key: string,
   *   productName: string,
   *   option: string,
   *   barcode: string,
   *   requiredQty: number,
   *   scannedQty: number,
   *   done: boolean,
   * }
   */

  const [scanInput,    setScanInput]    = useState('');
  const [scanLoading,  setScanLoading]  = useState(false);
  const [toast,        setToast]        = useState({ msg:'', type:'success' });
  const [completeModal,setCompleteModal]= useState(null);
  const [progress,     setProgress]     = useState({ show:false, current:0, total:0 });
  const [cancelSearch, setCancelSearch] = useState('');
  const [cancelSel,    setCancelSel]    = useState(new Set());
  const [delSearch,    setDelSearch]    = useState('');
  const [delSel,       setDelSel]       = useState(new Set());

  // 출고내역 탭
  const todayStr = new Date().toISOString().slice(0, 10);
  const [obStart,   setObStart]   = useState(todayStr);
  const [obEnd,     setObEnd]     = useState(todayStr);
  const [obList,    setObList]    = useState([]);
  const [obSearch,  setObSearch]  = useState('');
  const [obLoading, setObLoading] = useState(false);

  const fetchOutboundHistory = useCallback(async (start, end) => {
    setObLoading(true);
    try {
      const res  = await fetch(`${API_BASE}/api/invoice/shipped?startDate=${start}&endDate=${end}`);
      const data = await res.json();
      setObList(Array.isArray(data) ? data : []);
    } catch(e) { console.error(e); }
    finally { setObLoading(false); }
  }, []);

  const scanRef = useRef(null);

  const showToast = (msg, type='success') => setToast({ msg, type });

  /* ── 창고 목록 로드 (창고관리 연동) ───────────────────── */
  const loadWarehouses = useCallback(async () => {
    try {
      const res  = await fetch(`${API_BASE}/api/warehouses`);
      const data = await res.json();
      const list = Array.isArray(data) ? data : (data.content ?? []);
      // WarehouseDto.Response 실제 필드: warehouseId, name, code, isActive
      setWarehouses(list);

      // 1순위: localStorage 저장값 복원
      const saved = localStorage.getItem('inspectShip_warehouseId');
      if (saved) {
        const found = list.find(w => String(w.warehouseId) === saved);
        if (found) { setSelectedWarehouse(found); return; }
      }

      // 2순위: 기본값 — 본사(안양) 코드 ANYANG 또는 이름에 "안양" 포함된 창고
      const defaultWh = list.find(w =>
        w.code?.toUpperCase() === 'ANYANG' ||
        w.name?.includes('안양')
      );
      if (defaultWh) {
        setSelectedWarehouse(defaultWh);
        localStorage.setItem('inspectShip_warehouseId', String(defaultWh.warehouseId));
      }
    } catch(e) { console.error('창고 목록 로드 실패', e); }
  }, []);

  /* ── 당일 출고 목록 로드 (발송취소·출고목록 패널용) ─── */
  const reloadShipped = useCallback(async () => {
    setLoading(true);
    try {
      const res  = await fetch(`${API_BASE}/api/invoice/shipped`);
      const data = await res.json();
      setShipped(Array.isArray(data) ? data : []);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  }, []);

  /* ── 완료 목록 lazy 로드 (송장삭제 탭 전용) ─────────── */
  const loadCompleted = useCallback(async () => {
    setLoading(true);
    try {
      const res  = await fetch(`${API_BASE}/api/invoice/completed`);
      const data = await res.json();
      setCompletedOrders(Array.isArray(data) ? data : []);
      setCompletedLoaded(true);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  }, []);

  const reloadAll = useCallback(async () => {
    await Promise.all([reloadShipped(), loadCompleted()]);
  }, [reloadShipped, loadCompleted]);

  useEffect(() => {
    loadWarehouses();
    reloadShipped();
  }, []);

  /* ── 주문 → 상품 행 빌드 ──────────────────────────────── */
  // 서버 items 배열 사용 (상품명·옵션·바코드·수량 모두 포함)
  // items가 없는 구버전 응답은 productName 문자열 분리로 fallback
  const buildItemRows = (order) => {
    if (order.items && order.items.length > 0) {
      return order.items.map((item, i) => ({
        key:          `${order.orderNo}_${i}`,
        productName:  item.productName || '상품',
        option:       item.option      || '',
        barcode:      item.barcode     || '',
        requiredQty:  item.quantity    || 1,
        scannedQty:   0,
        done:         false,
      }));
    }
    // fallback: 구버전 DTO (items 없음)
    const names = (order.productName || '')
      .split(',').map(s => s.trim()).filter(Boolean);
    if (!names.length) names.push('상품');
    const total   = order.quantity || names.length;
    const perItem = Math.max(1, Math.round(total / names.length));
    return names.map((name, i) => ({
      key:         `${order.orderNo}_${i}`,
      productName: name,
      option:      '',
      barcode:     '',
      requiredQty: i === names.length - 1
        ? total - perItem * (names.length - 1)
        : perItem,
      scannedQty:  0,
      done:        false,
    }));
  };

  /* ── 검수 초기화 ──────────────────────────────────────── */
  const resetScan = useCallback(() => {
    setPhase('idle');
    setActiveOrder(null);
    setItemRows([]);
    setScanInput('');
    setCompleteModal(null);
    setTimeout(() => scanRef.current?.focus(), 50);
  }, []);

  /* ─────────────────────────────────────────────────────────
   * 핵심 스캔 핸들러
   * ─────────────────────────────────────────────────────── */
  const handleScan = useCallback(async () => {
    const val = scanInput.trim();
    if (!val) return;
    setScanInput('');

    /* ① idle → invoice : 송장/주문번호 스캔 → API 단건 조회 */
    if (phase === 'idle') {
      setScanLoading(true);
      try {
        const res = await fetch(`${API_BASE}/api/invoice/find?q=${encodeURIComponent(val)}`);
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          showToast(body.message || `'${val}' — 해당 송장/주문을 찾을 수 없습니다`, 'error');
          return;
        }
        const found = await res.json();
        const rows = buildItemRows(found);
        setActiveOrder(found);
        setItemRows(rows);
        setPhase('invoice');
        showToast(
          `송장 확인 ✅  ${found.trackingNo} | ${found.recipientName} — 상품 바코드를 스캔하세요`,
          'info'
        );
      } catch {
        showToast('주문 조회 중 오류가 발생했습니다', 'error');
      } finally {
        setScanLoading(false);
      }
      return;
    }

    /* ② invoice : 상품 바코드 스캔 */
    if (phase === 'invoice') {
      setItemRows(prev => {
        // 1순위: 바코드 정확 일치
        let targetIdx = prev.findIndex(r =>
          !r.done && r.barcode && r.barcode === val
        );
        // 2순위: 상품명 포함 매칭 (바코드 미설정 환경 대비)
        if (targetIdx < 0) {
          targetIdx = prev.findIndex(r =>
            !r.done && (
              r.productName === val ||
              r.productName.includes(val) ||
              val.includes(r.productName)
            )
          );
        }
        // 매칭 실패 → 오류 토스트, 상태 변경 없음
        if (targetIdx < 0) {
          const allDone = prev.every(r => r.done);
          if (allDone) {
            showToast('모든 상품이 이미 스캔 완료되었습니다', 'warn');
          } else {
            showToast(`'${val}' — 목록에 없는 바코드입니다`, 'error');
          }
          return prev;
        }

        const next = prev.map((r, i) => {
          if (i !== targetIdx) return r;
          const nextQty = r.scannedQty + 1;
          const done    = nextQty >= r.requiredQty;
          return { ...r, scannedQty: nextQty, done };
        });

        const row = next[targetIdx];
        const rowLabel = formatProductLabel(row.productName, row.option);
        if (row.done) {
          showToast(`✅ [${rowLabel}] 완료 (${row.requiredQty}/${row.requiredQty})`, 'success');
        } else {
          showToast(`📦 [${rowLabel}] ${row.scannedQty}/${row.requiredQty}`, 'info');
        }

        // 전체 완료 확인
        if (next.every(r => r.done)) {
          setTimeout(() => {
            setPhase('done');
            setCompleteModal(activeOrder);
          }, 350);
        }

        return next;
      });
      return;
    }

    /* ③ done : 완료 후 추가 스캔 방지 */
    showToast('검수 완료 상태입니다. 출고 처리 후 다음 송장을 스캔하세요', 'warn');
  }, [scanInput, phase, activeOrder]);

  /* ── 출고 처리 ─────────────────────────────────────────── */
  const doShip = async (order) => {
    if (!selectedWarehouse) { showToast('출고 창고를 먼저 선택해주세요', 'error'); return; }
    setCompleteModal(null);
    setLoading(true);
    setProgress({ show:true, current:0, total:1 });
    try {
      // 창고 먼저 서버에 등록 (서버 재시작 후 static 변수 초기화 대비)
      await fetch(`${API_BASE}/api/allocation/set-warehouse`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          warehouseCode: selectedWarehouse.code,
          warehouseName: selectedWarehouse.name,
        }),
      });

      const res  = await fetch(`${API_BASE}/api/allocation/confirm/${order.orderNo}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          warehouseCode: selectedWarehouse.code,
          warehouseName: selectedWarehouse.name,
        }),
      });
      setProgress({ show:true, current:1, total:1 });
      const data = await res.json().catch(() => ({}));
      await reloadShipped();
      if (res.ok && data.success !== false) {
        const syncMessage = data.marketSyncMessage ? ` / 판매처: ${data.marketSyncMessage}` : '';
        showToast(
          `🚚 출고 완료: ${order.trackingNo} (${order.recipientName})${syncMessage}`,
          data.marketSyncSuccess === false ? 'warn' : 'success'
        );
      } else if (!res.ok) {
        // 서버 오류여도 실제 처리됐을 수 있으므로 목록 확인 안내
        showToast(`출고 처리됨 — 출고 목록(당일)에서 확인해주세요`, 'warn');
      } else {
        showToast(`출고 실패: ${data.message || '알 수 없는 오류'}`, 'error');
      }
    } catch {
      showToast('출고 처리 중 오류가 발생했습니다', 'error');
    } finally {
      setProgress({ show:false, current:0, total:0 });
      setLoading(false);
      resetScan();
    }
  };

  /* ── 발송취소 (SHIPPED → CONFIRMED 롤백) ─────────────── */
  const doCancel = async (orderNos) => {
    if (!orderNos.length) return;
    if (!window.confirm(`${orderNos.length}건을 발송취소 처리합니다.\n상태가 CONFIRMED(검수대기)로 돌아갑니다.\n\n계속하시겠습니까?`)) return;
    setLoading(true);
    let ok = 0, fail = 0;
    for (const no of orderNos) {
      try {
        const res  = await fetch(`${API_BASE}/api/invoice/cancel/${no}`, { method:'POST' });
        const data = await res.json();
        data.success ? ok++ : fail++;
      } catch { fail++; }
    }
    showToast(`발송취소 완료: ${ok}건${fail ? ` (실패 ${fail}건)` : ''}`, ok ? 'success' : 'error');
    setCancelSel(new Set());
    await reloadShipped();
    setLoading(false);
  };

  /* ── 송장삭제 (deliveryMemo 초기화) ──────────────────── */
  const doDeleteInvoice = async (orderNos) => {
    if (!orderNos.length) return;
    if (!window.confirm(`${orderNos.length}건의 송장번호를 삭제합니다.\n\n계속하시겠습니까?`)) return;
    setLoading(true);
    let ok = 0, fail = 0;
    for (const no of orderNos) {
      try {
        const res  = await fetch(`${API_BASE}/api/invoice/delete/${no}`, { method:'POST' });
        const data = await res.json();
        data.success ? ok++ : fail++;
      } catch { fail++; }
    }
    showToast(`송장삭제 완료: ${ok}건${fail ? ` (실패 ${fail}건)` : ''}`, ok ? 'success' : 'error');
    setDelSel(new Set());
    await loadCompleted();
    setLoading(false);
  };

  /* ── 파생값 ────────────────────────────────────────────── */
  const doneCount    = itemRows.filter(r => r.done).length;
  const totalCount   = itemRows.length;
  const scanProgress = totalCount === 0 ? 0 : Math.round(doneCount / totalCount * 100);

  /* ══════════════════════════════════════════════════════════
   *  RENDER
   * ═════════════════════════════════════════════════════════ */
  return (
    <div style={{ background:'#f0f0f0', minHeight:'100vh',
      fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif", fontSize:'0.83rem', color:'#111' }}>

      <Toast msg={toast.msg} type={toast.type} onClose={() => setToast({ msg:'', type:'success' })} />

      <CompleteModal
        order={completeModal}
        onConfirm={() => doShip(completeModal)}
        onCancel={() => { setCompleteModal(null); setPhase('invoice'); }}
      />

      {/* ── 상단 탭 바 ─────────────────────────────────────── */}
      <div style={{ display:'flex', borderBottom:'2px solid #1565c0',
        background:'#fff', padding:'0 10px', gap:2, alignItems:'flex-end' }}>
        {[
          { key:'ship',           label:'발송및출고(바코드)' },
          { key:'cancel',         label:'발송취소' },
          { key:'deleteInv',      label:'송장삭제' },
          { key:'outboundHistory',label:'출고내역' },
        ].map(t => (
          <button key={t.key} onClick={() => setMainTab(t.key)} style={{
            padding:'7px 16px', border:'1px solid',
            borderColor:  mainTab===t.key ? '#1565c0' : '#d0d0d0',
            borderBottom: mainTab===t.key ? '2px solid #fff' : 'none',
            background:   mainTab===t.key ? '#fff' : '#ebebeb',
            color:        mainTab===t.key ? '#1565c0' : '#555',
            fontWeight:   mainTab===t.key ? 700 : 400,
            cursor:'pointer', fontSize:'0.82rem',
            borderRadius:'3px 3px 0 0',
            marginBottom: mainTab===t.key ? -2 : 0,
          }}>{t.label}</button>
        ))}
        <div style={{ marginLeft:'auto', display:'flex', gap:4, alignItems:'center', paddingBottom:4 }}>
          {[['#1565c0','발송및출고 프로그램 ▼'],['#555','[엑셀]발송및출고'],
            ['#444','≡ 바코드인쇄 환경설정'],['#444','명령바코드 관리'],
            ['#444','명령바코드 인쇄'],['#666','⚙ 설정'],['#1976d2','ℹ 도움말'],
          ].map(([bg, label]) => (
            <button key={label} style={C.btn(bg)}>{label}</button>
          ))}
        </div>
      </div>

      {/* ── 발송취소 탭 ────────────────────────────────────── */}
      {mainTab === 'cancel' && (() => {
        const list = shipped.filter(o => {
          if (!cancelSearch) return true;
          const kw = cancelSearch.toLowerCase();
          return [o.orderNo, o.recipientName, o.productName, o.trackingNo]
            .some(v => v?.toLowerCase().includes(kw));
        });
        const allChecked = list.length > 0 && list.every(o => cancelSel.has(o.orderNo));
        return (
          <div style={{ padding:'8px 10px' }}>
            <div style={{ background:'#fff', border:C.border, borderRadius:3 }}>
              <div style={{ padding:'5px 10px', background:'#fff3e0', borderBottom:C.border,
                display:'flex', alignItems:'center', gap:6 }}>
                <span style={{ width:8, height:8, borderRadius:'50%', background:'#e65100', display:'inline-block' }}/>
                <span style={{ fontWeight:700, fontSize:'0.8rem', color:'#bf360c' }}>
                  발송취소 — SHIPPED 목록 ({shipped.length}건)
                </span>
                <div style={{ flex:1 }}/>
                <input value={cancelSearch} onChange={e => setCancelSearch(e.target.value)}
                  placeholder="주문번호, 수취인, 송장번호..."
                  style={{ ...C.inp, minWidth:220 }} />
                <button style={C.btn('#757575','#fff')}
                  onClick={() => setCancelSel(new Set(list.map(o => o.orderNo)))}>전체선택</button>
                <button style={C.btn('#757575','#fff')} onClick={() => setCancelSel(new Set())}>선택해제</button>
                <button style={{ ...C.btn('#e65100'), opacity: cancelSel.size ? 1 : 0.4 }}
                  onClick={() => doCancel([...cancelSel])} disabled={!cancelSel.size || loading}>
                  ↩ {cancelSel.size}건 발송취소
                </button>
                <button style={C.btn('#757575','#fff')} onClick={reloadShipped} disabled={loading}>🔄</button>
              </div>
              <div style={{ overflowY:'auto', maxHeight:'calc(100vh - 220px)' }}>
                <table style={{ width:'100%', borderCollapse:'collapse' }}>
                  <thead style={{ position:'sticky', top:0 }}>
                    <tr>
                      <th style={C.th}>
                        <input type="checkbox" checked={allChecked}
                          onChange={e => e.target.checked
                            ? setCancelSel(new Set(list.map(o => o.orderNo)))
                            : setCancelSel(new Set())} />
                      </th>
                      {['순번','출고일시','쇼핑몰','수취인','상품명','택배사','송장번호'].map(h => (
                        <th key={h} style={C.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {list.length === 0 ? (
                      <tr><td colSpan={8} style={{ ...C.td, padding:'2.5rem', color:'#bbb' }}>
                        발송 완료된 주문이 없습니다
                      </td></tr>
                    ) : list.map((o, idx) => (
                      <tr key={o.orderNo}
                        onClick={() => {
                          setCancelSel(prev => {
                            const n = new Set(prev);
                            n.has(o.orderNo) ? n.delete(o.orderNo) : n.add(o.orderNo);
                            return n;
                          });
                        }}
                        style={{ background: cancelSel.has(o.orderNo) ? '#fff3e0' : idx%2===0?'#fff':'#fafafa',
                          cursor:'pointer' }}>
                        <td style={C.td} onClick={e => e.stopPropagation()}>
                          <input type="checkbox" checked={cancelSel.has(o.orderNo)}
                            onChange={() => {
                              setCancelSel(prev => {
                                const n = new Set(prev);
                                n.has(o.orderNo) ? n.delete(o.orderNo) : n.add(o.orderNo);
                                return n;
                              });
                            }} />
                        </td>
                        <td style={{ ...C.td, color:'#aaa' }}>{idx+1}</td>
                        <td style={{ ...C.td, fontSize:'0.72rem', color:'#555' }}>
                          {o.shippedAt ? fmtDate(o.shippedAt) : '-'}
                        </td>
                        <td style={C.td}>
                          {o.channelName ? <Badge text={o.channelName} bg="#e3f2fd" color="#1565c0"/> : '-'}
                        </td>
                        <td style={{ ...C.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                        <td style={{ ...C.td, textAlign:'left', maxWidth:180,
                          overflow:'hidden', textOverflow:'ellipsis' }}>{formatOrderSummary(o)}</td>
                        <td style={C.td}>
                          <Badge text={o.carrierName||'-'} bg="#e8f5e9" color="#2e7d32"/>
                        </td>
                        <td style={{ ...C.td, color:'#1565c0', fontWeight:600, fontSize:'0.74rem' }}>
                          {o.trackingNo||'-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ── 송장삭제 탭 ────────────────────────────────────── */}
      {mainTab === 'deleteInv' && (() => {
        if (!completedLoaded) loadCompleted();
        const list = completedOrders.filter(o => {
          if (!o.hasInvoice) return false;
          if (!delSearch) return true;
          const kw = delSearch.toLowerCase();
          return [o.orderNo, o.recipientName, o.productName, o.trackingNo]
            .some(v => v?.toLowerCase().includes(kw));
        });
        const allChecked = list.length > 0 && list.every(o => delSel.has(o.orderNo));
        return (
          <div style={{ padding:'8px 10px' }}>
            <div style={{ padding:'5px 10px', background:'#ffebee', border:'1px solid #ffcdd2',
              borderRadius:3, marginBottom:6, fontSize:'0.8rem', color:'#c62828', fontWeight:600 }}>
              ⚠ 송장번호를 삭제하면 해당 주문은 송장입력 대기 상태로 돌아갑니다.
            </div>
            <div style={{ background:'#fff', border:C.border, borderRadius:3 }}>
              <div style={{ padding:'5px 10px', background:'#ffebee', borderBottom:C.border,
                display:'flex', alignItems:'center', gap:6 }}>
                <span style={{ width:8, height:8, borderRadius:'50%', background:'#c62828', display:'inline-block' }}/>
                <span style={{ fontWeight:700, fontSize:'0.8rem', color:'#b71c1c' }}>
                  송장삭제 — 송장입력 완료 목록 ({list.length}건)
                </span>
                <div style={{ flex:1 }}/>
                <input value={delSearch} onChange={e => setDelSearch(e.target.value)}
                  placeholder="주문번호, 수취인, 송장번호..."
                  style={{ ...C.inp, minWidth:220 }} />
                <button style={C.btn('#757575','#fff')}
                  onClick={() => setDelSel(new Set(list.map(o => o.orderNo)))}>전체선택</button>
                <button style={C.btn('#757575','#fff')} onClick={() => setDelSel(new Set())}>선택해제</button>
                <button style={{ ...C.btn('#c62828'), opacity: delSel.size ? 1 : 0.4 }}
                  onClick={() => doDeleteInvoice([...delSel])} disabled={!delSel.size || loading}>
                  🗑 {delSel.size}건 송장삭제
                </button>
                <button style={C.btn('#757575','#fff')} onClick={loadCompleted} disabled={loading}>🔄</button>
              </div>
              <div style={{ overflowY:'auto', maxHeight:'calc(100vh - 220px)' }}>
                <table style={{ width:'100%', borderCollapse:'collapse' }}>
                  <thead style={{ position:'sticky', top:0 }}>
                    <tr>
                      <th style={C.th}>
                        <input type="checkbox" checked={allChecked}
                          onChange={e => e.target.checked
                            ? setDelSel(new Set(list.map(o => o.orderNo)))
                            : setDelSel(new Set())} />
                      </th>
                      {['순번','쇼핑몰','수취인','상품명','수량','택배사','송장번호'].map(h => (
                        <th key={h} style={C.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {list.length === 0 ? (
                      <tr><td colSpan={8} style={{ ...C.td, padding:'2.5rem', color:'#bbb' }}>
                        송장이 입력된 주문이 없습니다
                      </td></tr>
                    ) : list.map((o, idx) => (
                      <tr key={o.orderNo}
                        onClick={() => {
                          setDelSel(prev => {
                            const n = new Set(prev);
                            n.has(o.orderNo) ? n.delete(o.orderNo) : n.add(o.orderNo);
                            return n;
                          });
                        }}
                        style={{ background: delSel.has(o.orderNo) ? '#ffebee' : idx%2===0?'#fff':'#fafafa',
                          cursor:'pointer' }}>
                        <td style={C.td} onClick={e => e.stopPropagation()}>
                          <input type="checkbox" checked={delSel.has(o.orderNo)}
                            onChange={() => {
                              setDelSel(prev => {
                                const n = new Set(prev);
                                n.has(o.orderNo) ? n.delete(o.orderNo) : n.add(o.orderNo);
                                return n;
                              });
                            }} />
                        </td>
                        <td style={{ ...C.td, color:'#aaa' }}>{idx+1}</td>
                        <td style={C.td}>
                          {o.channelName ? <Badge text={o.channelName} bg="#e3f2fd" color="#1565c0"/> : '-'}
                        </td>
                        <td style={{ ...C.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                        <td style={{ ...C.td, textAlign:'left', maxWidth:180,
                          overflow:'hidden', textOverflow:'ellipsis' }}>{formatOrderSummary(o)}</td>
                        <td style={C.td}>{o.quantity||0}</td>
                        <td style={C.td}>
                          <Badge text={o.carrierName||'-'} bg="#e8f5e9" color="#2e7d32"/>
                        </td>
                        <td style={{ ...C.td, color:'#1565c0', fontWeight:600, fontSize:'0.74rem' }}>
                          {o.trackingNo||'-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ══════════════════════════════════════════════════════
       *  발송및출고(바코드) 메인
       * ═════════════════════════════════════════════════════ */}
      {mainTab === 'ship' && (
        <div style={{ padding:'8px 10px' }}>

          {/* 출고 진행바 */}
          {progress.show && (
            <div style={{ background:'#fff', border:C.border, borderRadius:3,
              padding:'6px 12px', marginBottom:6 }}>
              <div style={{ display:'flex', justifyContent:'space-between',
                fontSize:'0.78rem', color:'#555', marginBottom:3 }}>
                <span>⏳ 출고 처리 중...</span>
                <span>{Math.round(progress.current / progress.total * 100)}%</span>
              </div>
              <div style={{ height:5, background:'#e0e0e0', borderRadius:3, overflow:'hidden' }}>
                <div style={{ height:'100%', background:'#2e7d32', borderRadius:3,
                  width:`${Math.round(progress.current / progress.total * 100)}%`,
                  transition:'width 0.3s' }}/>
              </div>
            </div>
          )}

          {/* ── 검색(스캔 입력) 섹션 ─────────────────────── */}
          <div style={{ background:'#fff', border:C.border, borderRadius:3, marginBottom:6 }}>
            <div style={{ padding:'5px 10px', background:'#dde4f0', borderBottom:C.border,
              fontWeight:700, fontSize:'0.8rem', color:'#1a3a6b',
              display:'flex', alignItems:'center', gap:6 }}>
              <span style={{ width:8, height:8, borderRadius:'50%',
                background:'#1565c0', display:'inline-block' }}/>
              검색
            </div>

            {/* 출고 창고 선택 행 */}
            <div style={{ display:'flex', borderBottom:C.borderB }}>
              <div style={{ ...C.label, background:'#e8f0fe', color:'#1a3a6b', fontWeight:700 }}>
                🏭 출고 창고
              </div>
              <div style={{ flex:1, padding:'5px 8px', display:'flex', gap:8, alignItems:'center' }}>
                <select
                  value={selectedWarehouse ? String(selectedWarehouse.warehouseId) : ''}
                  onChange={e => {
                    const found = warehouses.find(w => String(w.warehouseId) === e.target.value) || null;
                    setSelectedWarehouse(found);
                    if (found) localStorage.setItem('inspectShip_warehouseId', String(found.warehouseId));
                    else localStorage.removeItem('inspectShip_warehouseId');
                  }}
                  style={{
                    ...C.inp, minWidth:200,
                    background: selectedWarehouse ? '#e8f5e9' : '#fff3e0',
                    border: `1px solid ${selectedWarehouse ? '#66bb6a' : '#ffb74d'}`,
                    fontWeight:600,
                    color: selectedWarehouse ? '#1b5e20' : '#e65100',
                  }}>
                  <option value="">-- 창고 선택 --</option>
                  {warehouses.filter(w => w.isActive !== false).map(w => (
                    <option key={w.warehouseId} value={String(w.warehouseId)}>
                      {w.name}{w.code ? ` (${w.code})` : ''}
                    </option>
                  ))}
                </select>
                {selectedWarehouse && (
                  <span style={{ fontSize:'0.78rem', color:'#1b5e20', fontWeight:600 }}>
                    ✅ 이 창고 재고에서 출고됩니다
                  </span>
                )}
                <button style={{ ...C.btn('#1565c0'), marginLeft:'auto', fontSize:'0.75rem', padding:'3px 10px' }}
                  onClick={loadWarehouses}>
                  🔄 창고 새로고침
                </button>
              </div>
            </div>

            {/* 송장 작업 메모 */}
            <div style={{ display:'flex', borderBottom:C.borderB }}>
              <div style={C.label}>
                송장 작업 메모
                <span style={{ marginLeft:5, padding:'1px 5px', background:'#1976d2', color:'#fff',
                  borderRadius:2, fontSize:'0.67rem', fontWeight:700 }}>Tip</span>
              </div>
              <div style={{ flex:1, padding:'5px 8px', display:'flex', gap:6, alignItems:'center' }}>
                <input style={{ ...C.inp, flex:1, maxWidth:480 }} placeholder="작업 메모 입력" />
                <select style={{ ...C.inp, minWidth:140 }}>
                  <option>작업메모처리여부</option>
                  <option>메모에 추가</option>
                  <option>메모 교체</option>
                </select>
              </div>
            </div>

            {/* 바코드/송장 스캔 */}
            <div style={{ display:'flex', alignItems:'center' }}>
              <div style={C.label}>바코드 / 송장 스캔</div>
              <div style={{ flex:1, padding:'5px 8px', display:'flex', gap:6, alignItems:'center', flexWrap:'wrap' }}>                {/* ★ 단계별 안내 + 색상 변경 입력창 */}
                <input
                  ref={scanRef}
                  value={scanInput}
                  onChange={e => setScanInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') handleScan(); }}
                  autoFocus
                  disabled={phase === 'done'}
                  placeholder={
                    phase === 'idle'    ? '① 송장번호 또는 주문번호 스캔/입력 후 Enter' :
                    phase === 'invoice' ? '② 상품 바코드 스캔 후 Enter' :
                                         '✅ 검수 완료 — 출고 처리 후 다음 송장을 스캔하세요'
                  }
                  style={{
                    ...C.inp, minWidth:300, fontWeight:600,
                    background: phase === 'idle'    ? '#fffde7' :
                                phase === 'invoice' ? '#e8f5e9' : '#f3e5f5',
                    border: `1px solid ${
                      phase === 'idle'    ? '#f9a825' :
                      phase === 'invoice' ? '#66bb6a' : '#ab47bc'
                    }`,
                  }}
                />

                <button style={C.btn('#1565c0')} onClick={handleScan} disabled={phase === 'done'}>
                  확인
                </button>
                <button style={C.btn('#e53935')} onClick={resetScan}>
                  ■ 초기화
                </button>
                <span style={{ fontSize:'0.77rem', color:'#777' }}>굿스플로 추가 송장 출력</span>
                <label style={{ display:'flex', alignItems:'center', gap:4,
                  fontSize:'0.77rem', color:'#555', cursor:'pointer' }}>
                  <input type="checkbox"/> 자동강제출고
                </label>

                {/* 단계 인디케이터 */}
                <div style={{ display:'flex', gap:5, alignItems:'center', marginLeft:6 }}>
                  {[
                    { label:'① 송장 스캔', key:'idle' },
                    { label:'② 상품 스캔', key:'invoice' },
                    { label:'③ 출고',      key:'done' },
                  ].map(s => (
                    <span key={s.key} style={{
                      padding:'2px 10px', borderRadius:10, fontSize:'0.71rem', fontWeight:700,
                      background: phase === s.key ? '#1565c0' : '#eee',
                      color:      phase === s.key ? '#fff'    : '#aaa',
                      border:    `1px solid ${phase === s.key ? '#0d47a1' : '#ddd'}`,
                    }}>{s.label}</span>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* 확인 버튼 */}
          <div style={{ textAlign:'center', marginBottom:6 }}>
            <button
              style={{ ...C.btn('#1565c0'), padding:'6px 40px', fontSize:'0.88rem' }}
              onClick={handleScan}
              disabled={phase === 'done'}>
              확인
            </button>
          </div>

          {/* ── 검색 결과 패널 ─────────────────────────── */}
          <div style={{ background:'#fff', border:C.border, borderRadius:3, marginBottom:6 }}>
            <div style={{ padding:'5px 10px', background:'#dde4f0', borderBottom:C.border,
              fontWeight:700, fontSize:'0.8rem', color:'#1a3a6b',
              display:'flex', alignItems:'center', gap:6 }}>
              <span style={{ width:8, height:8, borderRadius:'50%',
                background:'#1565c0', display:'inline-block' }}/>
              검색 결과
              {activeOrder && (
                <span style={{ fontWeight:400, color:'#555', fontSize:'0.77rem', marginLeft:6 }}>
                  — 송장&nbsp;
                  <strong style={{ color:'#1565c0' }}>{activeOrder.trackingNo}</strong>
                  &nbsp;/&nbsp;수취인&nbsp;
                  <strong>{activeOrder.recipientName}</strong>
                  &nbsp;/&nbsp;{activeOrder.carrierName}
                </span>
              )}
              {activeOrder && (
                <span style={{ marginLeft:'auto', fontSize:'0.77rem',
                  color:'#2e7d32', fontWeight:700 }}>
                  {doneCount}/{totalCount} ({scanProgress}%)
                </span>
              )}
            </div>

            <div style={{ display:'grid', gridTemplateColumns:'120px 1fr 120px 1fr' }}>
              <div style={C.label}>상태</div>
              <div style={{ padding:'5px 10px', display:'flex', gap:10, alignItems:'center' }}>
                {phase === 'idle'    && <Badge text="대기중..." bg="#f5f5f5" color="#999"/>}
                {phase === 'invoice' && <Badge text="검수 진행중" bg="#fff8e1" color="#e65100"/>}
                {phase === 'done'    && <Badge text="검수 완료" bg="#e8f5e9" color="#1b5e20"/>}
                {activeOrder && phase === 'invoice' && (
                  <div style={{ flex:1, maxWidth:180, height:6, background:'#e0e0e0',
                    borderRadius:3, overflow:'hidden' }}>
                    <div style={{ height:'100%', background:'#66bb6a', borderRadius:3,
                      width:`${scanProgress}%`, transition:'width 0.4s' }}/>
                  </div>
                )}
              </div>
              <div style={C.label}>배송 정보</div>
              <div style={{ padding:'5px 10px', fontSize:'0.8rem', color:'#555' }}>
                {activeOrder
                  ? `${activeOrder.carrierName || ''} / ${activeOrder.recipientName || ''} / ${activeOrder.address || ''}`
                  : ''}
              </div>
            </div>
          </div>

          {/* ── 하단 좌우 분할 ──────────────────────────── */}
          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:8 }}>

            {/* ┌─────────────────────────────────────────┐
                │  좌: 대기 목록                           │
                └─────────────────────────────────────────┘ */}
            <div style={{ background:'#fff', border:C.border, borderRadius:3 }}>
              {/* 헤더 */}
              <div style={{ padding:'5px 10px', background:'#dde4f0', borderBottom:C.border,
                display:'flex', alignItems:'center', gap:6, flexWrap:'wrap' }}>
                <span style={{ width:8, height:8, borderRadius:'50%',
                  background:'#1565c0', display:'inline-block' }}/>
                <span style={{ fontWeight:700, fontSize:'0.8rem', color:'#1a3a6b' }}>
                  대기 목록 (송장의 재고매칭내역)
                </span>
                <div style={{ flex:1 }}/>
                {phase !== 'idle' && (
                  <span style={{ fontSize:'0.75rem', color:'#777' }}>
                    총스캔수: {doneCount}건
                  </span>
                )}
                <button style={{ ...C.btn('#757575'), fontSize:'0.75rem', padding:'2px 8px' }}
                  onClick={reloadShipped} disabled={loading}>🔄</button>
              </div>

              {/* ── PHASE idle: 스캔 대기 안내 ── */}
              {phase === 'idle' && (
                <div style={{ display:'flex', flexDirection:'column', alignItems:'center',
                  justifyContent:'center', minHeight:240, color:'#bbb', gap:10 }}>
                  {scanLoading ? (
                    <>
                      <div style={{ fontSize:'2rem' }}>🔍</div>
                      <div style={{ fontSize:'0.85rem', color:'#999' }}>주문 조회 중...</div>
                    </>
                  ) : (
                    <>
                      <div style={{ fontSize:'2.5rem' }}>📦</div>
                      <div style={{ fontSize:'0.9rem', fontWeight:600, color:'#aaa' }}>
                        송장번호를 스캔하면 상품 목록이 표시됩니다
                      </div>
                      <div style={{ fontSize:'0.78rem', color:'#ccc' }}>
                        위 입력창에 송장번호 또는 주문번호 입력 후 Enter
                      </div>
                    </>
                  )}
                </div>
              )}

              {/* ── PHASE invoice / done: 해당 송장 상품 스캔 ── */}
              {phase !== 'idle' && (
                <>
                  {/* 송장 요약 바 */}
                  <div style={{ padding:'5px 10px', background:'#f0f7ff',
                    borderBottom:C.border, display:'flex', gap:14, alignItems:'center',
                    fontSize:'0.8rem', flexWrap:'wrap' }}>
                    <span>📦 <strong>{activeOrder?.carrierName}</strong></span>
                    <span>🏷&nbsp;<strong style={{ color:'#1565c0' }}>{activeOrder?.trackingNo}</strong></span>
                    <span>👤 <strong>{activeOrder?.recipientName}</strong></span>
                    {selectedWarehouse && (
                      <span style={{ color:'#1b5e20', fontWeight:600 }}>
                        🏭 {selectedWarehouse.name}
                      </span>
                    )}
                    <div style={{ flex:1 }}/>
                    <button style={{ ...C.btn('#757575'), fontSize:'0.74rem', padding:'2px 10px' }}
                      onClick={resetScan}>
                      ✕ 다른 송장 스캔
                    </button>
                  </div>

                  {/* 스캔 진행 바 */}
                  <div style={{ padding:'6px 10px', borderBottom:C.border }}>
                    <div style={{ display:'flex', justifyContent:'space-between',
                      fontSize:'0.75rem', color:'#555', marginBottom:3 }}>
                      <span>검수 진행률</span>
                      <span style={{ fontWeight:700,
                        color: scanProgress===100 ? '#1b5e20' : '#e65100' }}>
                        {doneCount} / {totalCount} 완료&nbsp;({scanProgress}%)
                      </span>
                    </div>
                    <div style={{ height:8, background:'#e0e0e0', borderRadius:4, overflow:'hidden' }}>
                      <div style={{ height:'100%', borderRadius:4,
                        background: scanProgress===100 ? '#2e7d32' : '#1976d2',
                        width:`${scanProgress}%`, transition:'width 0.4s' }}/>
                    </div>
                  </div>

                  {/* 상품 행 테이블 */}
                  <div style={{ overflowY:'auto', maxHeight:340 }}>
                    <table style={{ width:'100%', borderCollapse:'collapse' }}>
                      <thead style={{ position:'sticky', top:0 }}>
                        <tr>
                          {['순번','상품명','옵션','바코드','주문','스캔','상태'].map(h => (
                            <th key={h} style={C.th}>{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {itemRows.map((row, idx) => (
                          <tr key={row.key} style={{
                            background: row.done           ? '#e8f5e9' :
                                        row.scannedQty > 0 ? '#fff8e1' :
                                        idx%2===0          ? '#fff'    : '#fafafa',
                          }}>
                            <td style={{ ...C.td, color:'#aaa' }}>{idx+1}</td>
                            <td style={{ ...C.td, textAlign:'left', maxWidth:140,
                              overflow:'hidden', textOverflow:'ellipsis', fontWeight:500 }}>
                              {row.productName}
                            </td>
                            <td style={{ ...C.td, textAlign:'left', maxWidth:100,
                              overflow:'hidden', textOverflow:'ellipsis',
                              color: row.option ? '#444' : '#ccc', fontSize:'0.75rem' }}>
                              {row.option || '-'}
                            </td>
                            <td style={{ ...C.td, color:'#888', fontSize:'0.73rem' }}>
                              {row.barcode
                                ? <span style={{ fontFamily:'monospace', color:'#555' }}>{row.barcode}</span>
                                : <span style={{ color:'#ccc' }}>-</span>}
                            </td>
                            <td style={C.td}>{row.requiredQty}</td>
                            <td style={{ ...C.td, fontWeight:700,
                              color: row.done           ? '#1b5e20' :
                                     row.scannedQty > 0 ? '#e65100' : '#bbb' }}>
                              {row.scannedQty}
                              {row.scannedQty > 0 && !row.done && (
                                <span style={{ color:'#bbb', fontWeight:400 }}>/{row.requiredQty}</span>
                              )}
                            </td>
                            <td style={C.td}>
                              {row.done
                                ? <Badge text="✅ 완료" bg="#e8f5e9" color="#1b5e20"/>
                                : row.scannedQty > 0
                                ? <Badge text={`${row.scannedQty}/${row.requiredQty} 스캔`} bg="#fff8e1" color="#e65100"/>
                                : <Badge text="대기" bg="#f5f5f5" color="#aaa"/>}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {/* 완료 시 출고 버튼 풋터 */}
                  {phase === 'done' && (
                    <div style={{ padding:'8px 10px', borderTop:C.border, background:'#e8f5e9',
                      display:'flex', gap:8, alignItems:'center' }}>
                      <span style={{ flex:1, fontWeight:700, color:'#1b5e20', fontSize:'0.82rem' }}>
                        ✅ 모든 상품 검수 완료
                      </span>
                      <button style={C.btn('#757575')} onClick={resetScan}>취소</button>
                      <button style={C.btn('#1a7f37')}
                        onClick={() => setCompleteModal(activeOrder)}>
                        🚚 출고 처리
                      </button>
                    </div>
                  )}
                </>
              )}

              {/* 화살표 */}
              <div style={{ textAlign:'center', padding:'6px', fontSize:'1.3rem',
                color:'#888', borderTop:C.border }}>→</div>
            </div>

            {/* ┌─────────────────────────────────────────┐
                │  우: 출고 목록 (당일)                    │
                └─────────────────────────────────────────┘ */}
            <div style={{ background:'#fff', border:C.border, borderRadius:3 }}>
              <div style={{ padding:'5px 10px', background:'#fce4e4', borderBottom:C.border,
                display:'flex', alignItems:'center', gap:6 }}>
                <span style={{ width:8, height:8, borderRadius:'50%',
                  background:'#c62828', display:'inline-block' }}/>
                <span style={{ fontWeight:700, fontSize:'0.8rem', color:'#b71c1c' }}>
                  출고 목록&nbsp;
                  <span style={{ fontWeight:400, fontSize:'0.72rem', color:'#888' }}>(당일)</span>
                </span>
                <span style={{ padding:'0 7px', background:'#c62828', color:'#fff',
                  borderRadius:10, fontSize:'0.71rem', fontWeight:700 }}>
                  {shipped.filter(o => o.shippedAt?.startsWith(todayStr)).length}
                </span>
                <div style={{ flex:1 }}/>
                <button style={{ ...C.btn('#757575'), fontSize:'0.74rem', padding:'2px 8px' }}
                  onClick={reloadAll} disabled={loading}>
                  🔄 새로고침
                </button>
              </div>

              <div style={{ overflowY:'auto', maxHeight:460 }}>
                <table style={{ width:'100%', borderCollapse:'collapse' }}>
                  <thead style={{ position:'sticky', top:0 }}>
                    <tr>
                      {['순번','출고일시','수취인','상품명','택배사','송장번호'].map(h => (
                        <th key={h} style={C.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {loading ? (
                      <tr><td colSpan={6} style={{ ...C.td, padding:'2rem', color:'#aaa' }}>
                        ⏳ 로딩 중...
                      </td></tr>
                    ) : (() => {
                      const todayShipped = shipped.filter(o => o.shippedAt?.startsWith(todayStr));
                      return todayShipped.length === 0 ? (
                        <tr><td colSpan={6} style={{ ...C.td, padding:'2.5rem', color:'#ccc' }}>
                          <div style={{ fontSize:'1.5rem', marginBottom:6 }}>📤</div>
                          당일 출고된 항목이 없습니다
                        </td></tr>
                      ) : todayShipped.map((o, idx) => (
                        <tr key={o.orderNo} style={{ background:idx%2===0?'#fff':'#fff5f5' }}>
                          <td style={{ ...C.td, color:'#aaa' }}>{idx+1}</td>
                          <td style={{ ...C.td, fontSize:'0.72rem', color:'#555', whiteSpace:'nowrap' }}>
                            {o.shippedAt ? fmtDate(o.shippedAt) : '-'}
                          </td>
                          <td style={{ ...C.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                          <td style={{ ...C.td, textAlign:'left', maxWidth:150,
                            overflow:'hidden', textOverflow:'ellipsis' }}>
                            {formatOrderSummary(o)}
                          </td>
                          <td style={C.td}>
                            <Badge text={o.carrierName||'-'} bg="#e8f5e9" color="#2e7d32"/>
                          </td>
                          <td style={{ ...C.td, color:'#1565c0', fontWeight:600, fontSize:'0.74rem' }}>
                            {o.trackingNo||'-'}
                          </td>
                        </tr>
                      ));
                    })()}
                  </tbody>
                </table>
              </div>
            </div>
            {/* end 우측 */}

          </div>
          {/* end 하단 좌우 */}

        </div>
      )}

      {/* ── 출고내역 탭 ──────────────────────────────────── */}
      {mainTab === 'outboundHistory' && (
        <div style={{ padding:'8px 10px' }}>
          <div style={{ background:'#fff', border:C.border, borderRadius:3 }}>
            <div style={{ padding:'6px 10px', background:'#e8f5e9', borderBottom:C.border,
              display:'flex', alignItems:'center', gap:8, flexWrap:'wrap' }}>
              <span style={{ width:8, height:8, borderRadius:'50%', background:'#2e7d32', display:'inline-block' }}/>
              <span style={{ fontWeight:700, fontSize:'0.8rem', color:'#1b5e20' }}>출고내역</span>
              <input type="date" value={obStart} onChange={e => setObStart(e.target.value)} style={C.inp}/>
              <span style={{ color:'#aaa' }}>~</span>
              <input type="date" value={obEnd} onChange={e => setObEnd(e.target.value)} style={C.inp}/>
              <button style={C.btn('#2e7d32')} onClick={() => fetchOutboundHistory(obStart, obEnd)} disabled={obLoading}>
                {obLoading ? '⏳' : '🔍 조회'}
              </button>
              <button style={C.btn('#1565c0')} onClick={() => {
                const t = todayStr; setObStart(t); setObEnd(t);
                fetchOutboundHistory(t, t);
              }}>당일</button>
              <input value={obSearch} onChange={e => setObSearch(e.target.value)}
                placeholder="주문번호, 수취인, 송장번호..."
                style={{ ...C.inp, minWidth:220, marginLeft:8 }}/>
              <span style={{ fontSize:'0.78rem', color:'#555', marginLeft:'auto' }}>
                {obList.length}건
              </span>
            </div>
            <div style={{ overflowY:'auto', maxHeight:'calc(100vh - 200px)' }}>
              <table style={{ width:'100%', borderCollapse:'collapse' }}>
                <thead style={{ position:'sticky', top:0 }}>
                  <tr>
                    {['순번','출고일시','쇼핑몰','수취인','상품명','수량','택배사','송장번호'].map(h => (
                      <th key={h} style={C.th}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {obLoading ? (
                    <tr><td colSpan={8} style={{ ...C.td, padding:'2.5rem', color:'#aaa' }}>⏳ 조회 중...</td></tr>
                  ) : obList.length === 0 ? (
                    <tr><td colSpan={8} style={{ ...C.td, padding:'2.5rem', color:'#bbb' }}>
                      기간을 선택하고 조회하세요
                    </td></tr>
                  ) : obList.filter(o => {
                    if (!obSearch) return true;
                    const kw = obSearch.toLowerCase();
                    return [o.orderNo, o.recipientName, o.productName, o.trackingNo]
                      .some(v => v?.toLowerCase().includes(kw));
                  }).map((o, idx) => (
                    <tr key={o.orderNo} style={{ background:idx%2===0?'#fff':'#f9fffe' }}>
                      <td style={{ ...C.td, color:'#aaa' }}>{idx+1}</td>
                      <td style={{ ...C.td, fontSize:'0.72rem', color:'#555' }}>{fmtDate(o.shippedAt)}</td>
                      <td style={C.td}>
                        {o.channelName ? <Badge text={o.channelName} bg="#e3f2fd" color="#1565c0"/> : '-'}
                      </td>
                      <td style={{ ...C.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                      <td style={{ ...C.td, textAlign:'left', maxWidth:200,
                        overflow:'hidden', textOverflow:'ellipsis' }}>{formatOrderSummary(o)}</td>
                      <td style={C.td}>{o.quantity||0}</td>
                      <td style={C.td}><Badge text={o.carrierName||'-'} bg="#e8f5e9" color="#2e7d32"/></td>
                      <td style={{ ...C.td, color:'#1565c0', fontWeight:600, fontSize:'0.74rem' }}>{o.trackingNo||'-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
