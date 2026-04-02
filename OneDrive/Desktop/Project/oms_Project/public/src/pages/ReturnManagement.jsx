import { useState, useCallback, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

/* ── 상수 ─────────────────────────────────────────────── */
const RETURN_TYPES    = { CANCEL:'취소', REFUND:'환불', EXCHANGE:'교환' };
const RETURN_STATUSES = {
  REQUESTED:  { label:'접수',   color:'#1565c0', bg:'#e3f2fd' },
  INSPECTING: { label:'검수중', color:'#e65100', bg:'#fff3e0' },
  COMPLETED:  { label:'완료',   color:'#2e7d32', bg:'#e8f5e9' },
  CANCELLED:  { label:'취소됨', color:'#757575', bg:'#f5f5f5' },
};
const INSPECT_RESULTS = { NORMAL:'정상', DEFECTIVE:'불량' };

// 접수 시: 정상=국내온라인반품, 불량=RETURN_POOR
const RECEIVE_NORMAL_WH  = 'ANYANG_KO_RETURN'; // 4.안양(국내온라인 반품)
const RECEIVE_DEFECT_WH  = 'RETURN_POOR';          // 4.반품(불량)
// 검수 후: 정상=본사(안양), 불량=반품(불량)
const INSPECT_NORMAL_WH  = 'ANYANG';               // 본사(안양)
const INSPECT_DEFECT_WH  = 'RETURN_POOR';          // 4.반품(불량)
const WAREHOUSES = [
  { code:'ANYANG',  name:'본사(안양)' },
  { code:'ICHEON',  name:'고백창고(이천)' },
  { code:'BUCHEON', name:'부천검수창고' },
];
const CHANNELS = ['쿠팡','11번가','스마트스토어','MakeShop','기타'];

/* ── 스타일 ────────────────────────────────────────────── */
const S = {
  page:  { padding:'1.25rem', background:'#f0f2f5', minHeight:'100vh',
            fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif", fontSize:'0.84rem' },
  card:  { background:'#fff', borderRadius:6, padding:'1rem 1.25rem',
            boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'1rem' },
  btn:   (bg, c='#fff') => ({
    padding:'5px 14px', background:bg, color:c, border:'none',
    borderRadius:4, cursor:'pointer', fontSize:'0.81rem', fontWeight:600
  }),
  inp:   { padding:'5px 9px', border:'1px solid #ccc', borderRadius:4,
            fontSize:'0.82rem', outline:'none', color:'#111' },
  th:    { padding:'7px 10px', background:'#f5f6fa', borderBottom:'1px solid #e5e7eb',
            fontWeight:600, color:'#555', fontSize:'0.78rem', textAlign:'center',
            whiteSpace:'nowrap' },
  td:    { padding:'7px 10px', borderBottom:'1px solid #f0f0f0',
            textAlign:'center', fontSize:'0.8rem', color:'#333' },
};

function StatusBadge({ status }) {
  const s = RETURN_STATUSES[status] || { label:status, color:'#888', bg:'#eee' };
  return (
    <span style={{ padding:'2px 9px', borderRadius:10, fontSize:'0.72rem',
      fontWeight:700, color:s.color, background:s.bg }}>
      {s.label}
    </span>
  );
}

function fmtDate(d) {
  if (!d) return '-';
  return d.replace('T', ' ').substring(0, 16);
}

/* ── 반품 접수 모달 ──────────────────────────────────────── */
function CreateModal({ onClose, onCreated, init }) {
  const [form, setForm] = useState({
    orderNo:         init?.orderNo        || '',
    channelName:     init?.channelName    || '쿠팡',
    recipientName:   init?.recipientName  || '',
    recipientPhone:  init?.recipientPhone || '',
    productName:     init?.productName    || '',
    quantity:        init?.quantity       || 1,
    returnType:'REFUND', returnReason:'',
    returnTrackingNo:'', carrierName:'우체국택배',
    receiveMemo: '',
  });
  // 상품별 판정 items
  const [items, setItems] = useState(() =>
    (init?.items?.length > 0)
      ? init.items.map(it => ({ productName: it.productName||'', optionName: it.optionName||'', productCode: it.productCode||'', quantity: it.quantity||1, result: null }))
      : [{ productName: init?.productName||'', quantity: init?.quantity||1, result: null }]
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));
  const setItemResult = (idx, result) =>
    setItems(prev => prev.map((it, i) => i === idx ? {
      ...it,
      result,
      warehouseCode: result === 'NORMAL' ? RECEIVE_NORMAL_WH : RECEIVE_DEFECT_WH,
    } : it));

  const allJudged    = items.every(it => it.result !== null);
  const hasDefective = items.some(it => it.result === 'DEFECTIVE');
  const overallReceiveResult = allJudged ? (hasDefective ? 'DEFECTIVE' : 'NORMAL') : null;
  const receiveWarehouseCode = overallReceiveResult === 'NORMAL'    ? RECEIVE_NORMAL_WH
                             : overallReceiveResult === 'DEFECTIVE' ? RECEIVE_DEFECT_WH : null;
  const receiveWarehouseLabel = overallReceiveResult === 'NORMAL'    ? '4.안양(국내온라인 반품)'
                              : overallReceiveResult === 'DEFECTIVE' ? '4.반품(불량)' : '';

  const submit = async () => {
    if (!form.orderNo || !form.recipientName || !form.productName) {
      setError('주문번호, 수령자, 상품명은 필수입니다'); return;
    }
    if (!allJudged) {
      setError('모든 상품의 정상/불량을 선택해주세요'); return;
    }
    setLoading(true); setError('');
    try {
      const r = await fetch(`${API_BASE}/api/returns`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({
          ...form,
          quantity: Number(form.quantity),
          items,
          receiveResult: overallReceiveResult,
          receiveWarehouseCode,
          receiveMemo: form.receiveMemo,
        })
      });
      if (!r.ok) throw new Error('서버 오류');
      onCreated(await r.json());
      onClose();
    } catch(e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const row = (label, node) => (
    <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:8 }}>
      <div style={{ width:90, fontSize:'0.8rem', color:'#555', flexShrink:0 }}>{label}</div>
      {node}
    </div>
  );

  return (
    <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.4)',
      zIndex:1000, display:'flex', alignItems:'center', justifyContent:'center' }}>
      <div style={{ background:'#fff', borderRadius:8, padding:'1.5rem',
        width:480, maxHeight:'90vh', overflowY:'auto',
        boxShadow:'0 8px 32px rgba(0,0,0,0.2)' }}>
        <div style={{ fontWeight:700, fontSize:'1rem', marginBottom:'1rem', color:'#1a1a1a' }}>
          📦 반품 접수
        </div>

        {row('판매처', (
          <select value={form.channelName} onChange={e=>set('channelName',e.target.value)}
            style={{ ...S.inp, flex:1 }}>
            {CHANNELS.map(c=><option key={c}>{c}</option>)}
          </select>
        ))}
        {row('주문번호 *', <input value={form.orderNo} onChange={e=>set('orderNo',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="주문번호"/>)}
        {row('수령자 *', <input value={form.recipientName} onChange={e=>set('recipientName',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="수령자명"/>)}
        {row('연락처', <input value={form.recipientPhone} onChange={e=>set('recipientPhone',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="010-0000-0000"/>)}
        {row('상품명 *', <input value={form.productName} onChange={e=>set('productName',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="상품명"/>)}
        {row('수량', <input type="number" min={1} value={form.quantity}
          onChange={e=>set('quantity',e.target.value)} style={{ ...S.inp, width:70 }}/>)}
        {row('반품유형', (
          <select value={form.returnType} onChange={e=>set('returnType',e.target.value)}
            style={{ ...S.inp, flex:1 }}>
            {Object.entries(RETURN_TYPES).map(([k,v])=><option key={k} value={k}>{v}</option>)}
          </select>
        ))}
        {row('반품사유', <textarea value={form.returnReason}
          onChange={e=>set('returnReason',e.target.value)}
          style={{ ...S.inp, flex:1, height:60, resize:'none' }} placeholder="반품 사유"/>)}
        {row('반품 운송장', <input value={form.returnTrackingNo}
          onChange={e=>set('returnTrackingNo',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="운송장번호"/>)}
        {row('택배사', <input value={form.carrierName}
          onChange={e=>set('carrierName',e.target.value)}
          style={{ ...S.inp, flex:1 }} placeholder="택배사"/>)}

        {/* 상품별 정상/불량 판단 */}
        <div style={{ margin:'12px 0 8px', padding:'12px', borderRadius:6,
          background:'#f8f9ff', border:'1px solid #e3f2fd' }}>
          <div style={{ fontSize:'0.8rem', fontWeight:700, color:'#1a1a1a', marginBottom:10 }}>
            📋 접수 판정 *
          </div>
          {items.map((it, idx) => (
            <div key={idx} style={{ padding:'10px 12px', marginBottom:8, borderRadius:6,
              border:`2px solid ${it.result==='NORMAL' ? '#2e7d32' : it.result==='DEFECTIVE' ? '#c62828' : '#ddd'}`,
              background: it.result==='NORMAL' ? '#f1f8e9' : it.result==='DEFECTIVE' ? '#ffebee' : '#fafafa' }}>
              <div style={{ fontSize:'0.82rem', fontWeight:600, color:'#1a1a1a', marginBottom:8 }}>
                {it.productName || `상품 ${idx+1}`}
                {it.optionName && <span style={{ fontSize:'0.72rem', color:'#888', fontWeight:400, marginLeft:6 }}>{it.optionName}</span>}
                <span style={{ fontSize:'0.72rem', color:'#888', fontWeight:400, marginLeft:6 }}>({it.quantity}개)</span>
              </div>
              <div style={{ display:'flex', gap:8 }}>
                <button onClick={() => setItemResult(idx, 'NORMAL')}
                  style={{ padding:'5px 14px', borderRadius:5, cursor:'pointer', fontWeight:700, fontSize:'0.8rem',
                    border:`2px solid ${it.result==='NORMAL' ? '#2e7d32' : '#ddd'}`,
                    background: it.result==='NORMAL' ? '#2e7d32' : '#fff',
                    color: it.result==='NORMAL' ? '#fff' : '#555' }}>
                  ✅ 정상
                </button>
                <button onClick={() => setItemResult(idx, 'DEFECTIVE')}
                  style={{ padding:'5px 14px', borderRadius:5, cursor:'pointer', fontWeight:700, fontSize:'0.8rem',
                    border:`2px solid ${it.result==='DEFECTIVE' ? '#c62828' : '#ddd'}`,
                    background: it.result==='DEFECTIVE' ? '#c62828' : '#fff',
                    color: it.result==='DEFECTIVE' ? '#fff' : '#555' }}>
                  ❌ 불량
                </button>
              </div>
            </div>
          ))}
          {receiveWarehouseCode && (
            <div style={{ fontSize:'0.77rem', color:'#555', padding:'6px 10px',
              background:'#fff', borderRadius:4, border:'1px solid #e0e0e0', marginTop:4 }}>
              📦 이동 창고: <strong style={{ color: overallReceiveResult==='NORMAL' ? '#2e7d32' : '#c62828' }}>
                {receiveWarehouseLabel}
              </strong>
            </div>
          )}
        </div>

        {/* 작업메모 */}
        <div style={{ marginBottom:8 }}>
          <div style={{ fontSize:'0.78rem', color:'#555', fontWeight:600, marginBottom:4 }}>작업메모</div>
          <textarea value={form.receiveMemo} onChange={e=>set('receiveMemo',e.target.value)}
            style={{ ...S.inp, width:'100%', height:52, resize:'none', boxSizing:'border-box' }}
            placeholder="접수 시 특이사항, 상태 메모 등"/>
        </div>

        {error && <div style={{ color:'#c62828', fontSize:'0.8rem', marginBottom:8 }}>⚠ {error}</div>}

        <div style={{ display:'flex', gap:8, justifyContent:'flex-end', marginTop:'1rem' }}>
          <button onClick={onClose} style={S.btn('#757575')}>취소</button>
          <button onClick={submit} disabled={loading}
            style={S.btn('#1565c0')}>{loading ? '처리중...' : '접수'}</button>
        </div>
      </div>
    </div>
  );
}

/* ── 검수 모달 ────────────────────────────────────────────── */
function InspectModal({ ret, onClose, onUpdated }) {
  const [warehouseCode, setWarehouseCode] = useState('');
  const [inspectMemo,   setInspectMemo]   = useState('');
  const [warehouses,      setWarehouses]      = useState([]);
  const [defectWarehouse, setDefectWarehouse] = useState(null);
  const [loading,         setLoading]         = useState(false);
  const [error,           setError]           = useState('');

  // 상품별 판정 — 접수 시 정상 판정된 상품만 (불량은 이미 접수 시 처리됨)
  const [items, setItems] = useState(() =>
    (ret.items||[]).length > 0
      ? ret.items
          .filter(it => it.result !== 'DEFECTIVE')  // 불량은 검수 대상 제외
          .map(it => ({
          productName: it.productName || '',
          optionName:  it.optionName  || '',
          productCode: it.productCode || '',  // 바코드/자사코드 — 매칭 핵심
          quantity:    it.quantity    || 1,
          result:      null,
          restockQty:  it.quantity    || 1,
        }))
      : [{
          productName: ret.productName || '',
          optionName:  '',
          productCode: '',
          quantity:    ret.quantity || 1,
          result:      null,
          restockQty:  ret.quantity || 1,
        }]
  );

  // 불량 상품이 하나라도 있으면 INSPECTING, 전체 정상이면 COMPLETED
  const hasDefective = items.some(it => it.result === 'DEFECTIVE');
  const allJudged    = items.every(it => it.result !== null);

  // 창고 목록 로드
  useEffect(() => {
    fetch(`${API_BASE}/api/warehouses/active`)
      .then(r => r.ok ? r.json() : [])
      .then(data => {
        const list = Array.isArray(data) ? data : [];
        setWarehouses(list);
        if (list.length > 0) setWarehouseCode(list[0].code || list[0].warehouseCode || '');
        const defect = list.find(w => {
          const name = (w.name || w.warehouseName || '').toLowerCase();
          // "국내온라인"과 "반품" 둘 다 포함하는 창고 = 4.안양(국내온라인 반품)
          return (name.includes('국내온라인') && name.includes('반품'))
              || (name.includes('불량') && name.includes('반품'));
        }) || list.find(w => {
          // fallback: 반품 단독
          const name = (w.name || w.warehouseName || '').toLowerCase();
          return name.includes('반품');
        });
        setDefectWarehouse(defect || null);
      })
      .catch(() => setWarehouses([]));
  }, []);

  const setItemResult = (idx, result) =>
    setItems(prev => prev.map((it, i) => i===idx ? { ...it, result } : it));

  const updateQty = (idx, qty) =>
    setItems(prev => prev.map((it, i) => i===idx ? { ...it, restockQty: Number(qty) } : it));

  // 검수 후: 정상=본사(안양), 불량=반품(불량) — 고정
  const inspectNormalLabel = '본사(안양)';
  const inspectDefectLabel = '4.반품(불량)';

  const submit = async () => {
    if (!allJudged) { setError('모든 상품의 정상/불량을 선택해주세요'); return; }
    setLoading(true); setError('');
    try {
      const restockItems = items.map(it => ({
        productCode:   it.productCode,
        productName:   it.productName,
        optionName:    it.optionName,
        quantity:      it.restockQty || 1,
        itemResult:    it.result,
        warehouseCode: it.result === 'DEFECTIVE' ? INSPECT_DEFECT_WH : INSPECT_NORMAL_WH,
      }));

      const overallResult = hasDefective ? 'DEFECTIVE' : 'NORMAL';

      const body = {
        inspectResult: overallResult,
        warehouseCode:      INSPECT_NORMAL_WH,
        defectWarehouseCode: INSPECT_DEFECT_WH,
        inspectMemo,
        restockItems,
      };

      const r = await fetch(`${API_BASE}/api/returns/${ret.returnId}/inspect`, {
        method:'PUT', headers:{'Content-Type':'application/json'},
        body: JSON.stringify(body)
      });
      const data = await r.json();
      if (!data.success) throw new Error(data.stockMessage || '처리 실패');
      onUpdated(data.return, data.stockMessage);
      onClose();
    } catch(e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.45)',
      zIndex:1000, display:'flex', alignItems:'center', justifyContent:'center' }}>
      <div style={{ background:'#fff', borderRadius:8, padding:'1.5rem',
        width:580, maxHeight:'90vh', overflowY:'auto',
        boxShadow:'0 8px 32px rgba(0,0,0,0.25)' }}>

        <div style={{ fontWeight:700, fontSize:'1rem', marginBottom:4, color:'#1a1a1a' }}>
          🔍 검수 처리
        </div>
        <div style={{ fontSize:'0.8rem', color:'#888', marginBottom:'1rem' }}>
          {ret.orderNo} · {ret.recipientName}
        </div>

        {/* 창고 안내 (고정) */}
        <div style={{ marginBottom:14, padding:'10px 14px', borderRadius:6,
          background:'#f8f9ff', border:'1px solid #e3f2fd', fontSize:'0.8rem' }}>
          <div style={{ fontWeight:700, color:'#1a1a1a', marginBottom:6 }}>📦 검수 후 이동 창고 (자동)</div>
          <div style={{ display:'flex', gap:16 }}>
            <span>✅ 정상 → <strong style={{ color:'#2e7d32' }}>{inspectNormalLabel}</strong></span>
            <span>❌ 불량 → <strong style={{ color:'#c62828' }}>{inspectDefectLabel}</strong></span>
          </div>
        </div>

        {/* 상품별 정상/불량 판정 */}
        <div style={{ marginBottom:14 }}>
          <div style={{ fontSize:'0.78rem', color:'#555', fontWeight:600, marginBottom:8 }}>
            상품별 검수 판정
          </div>
          {items.map((it, idx) => (
            <div key={idx} style={{ padding:'10px 12px', marginBottom:8, borderRadius:6,
              border:`2px solid ${
                it.result === 'NORMAL'   ? '#2e7d32' :
                it.result === 'DEFECTIVE'? '#c62828' : '#ddd'}`,
              background: it.result === 'NORMAL' ? '#f1f8e9' :
                          it.result === 'DEFECTIVE' ? '#ffebee' : '#fafafa' }}>

              {/* 상품명 */}
              <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:8 }}>
                <div style={{ flex:1 }}>
                  <div style={{ fontWeight:600, fontSize:'0.84rem', color:'#1a1a1a' }}>
                    {it.productName || `상품 ${idx+1}`}
                  </div>
                  <div style={{ fontSize:'0.72rem', color:'#888', marginTop:1, display:'flex', gap:8 }}>
                    {it.optionName && <span>{it.optionName}</span>}
                    {it.productCode
                      ? <span style={{ background:'#e8f5e9', color:'#2e7d32', padding:'1px 5px',
                          borderRadius:3, fontWeight:600 }}>📦 {it.productCode}</span>
                      : <span style={{ color:'#ffb300', fontWeight:600 }}>⚠ 바코드 없음 (상품명으로 매칭)</span>
                    }
                  </div>
                </div>
                <div style={{ fontSize:'0.75rem', color:'#888' }}>주문 {it.quantity}개</div>
              </div>

              {/* 판정 버튼 + 수량 */}
              <div style={{ display:'flex', gap:8, alignItems:'center' }}>
                <button onClick={() => setItemResult(idx, 'NORMAL')}
                  style={{ padding:'5px 14px', borderRadius:5, cursor:'pointer', fontWeight:700,
                    fontSize:'0.8rem',
                    border:`2px solid ${it.result==='NORMAL' ? '#2e7d32' : '#ddd'}`,
                    background: it.result==='NORMAL' ? '#2e7d32' : '#fff',
                    color: it.result==='NORMAL' ? '#fff' : '#555' }}>
                  ✅ 정상
                </button>
                <button onClick={() => setItemResult(idx, 'DEFECTIVE')}
                  style={{ padding:'5px 14px', borderRadius:5, cursor:'pointer', fontWeight:700,
                    fontSize:'0.8rem',
                    border:`2px solid ${it.result==='DEFECTIVE' ? '#c62828' : '#ddd'}`,
                    background: it.result==='DEFECTIVE' ? '#c62828' : '#fff',
                    color: it.result==='DEFECTIVE' ? '#fff' : '#555' }}>
                  ❌ 불량
                </button>

                {/* 정상이면 입고 수량 표시 */}
                {it.result === 'NORMAL' && (
                  <div style={{ display:'flex', alignItems:'center', gap:6, marginLeft:'auto' }}>
                    <span style={{ fontSize:'0.75rem', color:'#2e7d32' }}>입고 수량</span>
                    <input type="number" min={1} value={it.restockQty}
                      onChange={e => updateQty(idx, e.target.value)}
                      style={{ ...S.inp, width:56, textAlign:'center', fontSize:'0.82rem' }}/>
                    <span style={{ fontSize:'0.75rem', color:'#555' }}>개</span>
                  </div>
                )}
                {it.result === 'DEFECTIVE' && (
                  <span style={{ fontSize:'0.73rem', color:'#c62828', marginLeft:'auto' }}>
                    → {inspectDefectLabel} 이동
                  </span>
                )}
              </div>
            </div>
          ))}

          {/* 판정 요약 */}
          {allJudged && (
            <div style={{ padding:'8px 12px', borderRadius:6, marginTop:4,
              background: hasDefective ? '#fff3e0' : '#e8f5e9',
              border:`1px solid ${hasDefective ? '#ffcc80' : '#a5d6a7'}`,
              fontSize:'0.78rem', fontWeight:600,
              color: hasDefective ? '#e65100' : '#2e7d32' }}>
              {hasDefective
                ? `⚠ 불량 상품 포함 → 검수 완료 후 환불/교환 처리 대기 (INSPECTING)`
                : `✅ 전체 정상 → 검수 완료 (COMPLETED)`}
            </div>
          )}
        </div>

        {/* 작업메모 */}
        <div style={{ marginBottom:14 }}>
          <div style={{ fontSize:'0.78rem', color:'#555', fontWeight:600, marginBottom:6 }}>작업메모</div>
          <textarea value={inspectMemo} onChange={e=>setInspectMemo(e.target.value)}
            style={{ ...S.inp, width:'100%', height:48, resize:'none', boxSizing:'border-box' }}
            placeholder="검수 내용, 특이사항 등 메모"/>
        </div>

        {error && (
          <div style={{ color:'#c62828', fontSize:'0.8rem', marginBottom:10,
            padding:'6px 10px', background:'#ffebee', borderRadius:4 }}>⚠ {error}</div>
        )}

        <div style={{ display:'flex', gap:8, justifyContent:'flex-end' }}>
          <button onClick={onClose} style={S.btn('#757575')}>취소</button>
          <button onClick={submit} disabled={loading || !allJudged}
            style={{ ...S.btn(hasDefective ? '#e65100' : '#2e7d32'),
              opacity: allJudged ? 1 : 0.5 }}>
            {loading ? '처리중...' : hasDefective ? '검수 완료 (불량 포함)' : '검수 완료 (전체 정상)'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 환불/교환 모달 ──────────────────────────────────────── */
function ResolveModal({ ret, onClose, onUpdated }) {
  const [form, setForm] = useState({
    resolutionType: ret.returnType === 'EXCHANGE' ? 'EXCHANGE' : 'REFUND',
    refundAmount: '',
    exchangeOrderNo: ret.exchangeOrderNo || '',
    resolutionMemo: ''
  });
  const [loading, setLoading] = useState(false);

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const submit = async () => {
    setLoading(true);
    try {
      const r = await fetch(`${API_BASE}/api/returns/${ret.returnId}/resolve`, {
        method:'PUT', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ ...form, refundAmount: Number(form.refundAmount) || null })
      });
      onUpdated(await r.json());
      onClose();
    } finally { setLoading(false); }
  };

  const row = (label, node) => (
    <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:10 }}>
      <div style={{ width:90, fontSize:'0.8rem', color:'#555', flexShrink:0 }}>{label}</div>
      {node}
    </div>
  );

  return (
    <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.4)',
      zIndex:1000, display:'flex', alignItems:'center', justifyContent:'center' }}>
      <div style={{ background:'#fff', borderRadius:8, padding:'1.5rem',
        width:440, boxShadow:'0 8px 32px rgba(0,0,0,0.2)' }}>
        <div style={{ fontWeight:700, fontSize:'1rem', marginBottom:'1rem', color:'#1a1a1a' }}>
          💳 환불/교환 처리
        </div>

        {row('처리 유형', (
          <select value={form.resolutionType} onChange={e=>set('resolutionType',e.target.value)}
            style={{ ...S.inp, flex:1 }}>
            <option value="REFUND">환불</option>
            <option value="EXCHANGE">교환</option>
            <option value="NONE">해당없음</option>
          </select>
        ))}
        {form.resolutionType === 'REFUND' && row('환불 금액', (
          <input type="number" value={form.refundAmount}
            onChange={e=>set('refundAmount',e.target.value)}
            style={{ ...S.inp, width:120 }} placeholder="원"/>
        ))}
        {form.resolutionType === 'EXCHANGE' && row('교환 주문번호', (
          <input value={form.exchangeOrderNo}
            onChange={e=>set('exchangeOrderNo',e.target.value)}
            style={{ ...S.inp, flex:1 }} placeholder="신규 주문번호"/>
        ))}
        {row('처리 메모', <textarea value={form.resolutionMemo}
          onChange={e=>set('resolutionMemo',e.target.value)}
          style={{ ...S.inp, flex:1, height:60, resize:'none' }}
          placeholder="처리 내용"/>)}

        <div style={{ display:'flex', gap:8, justifyContent:'flex-end', marginTop:'1rem' }}>
          <button onClick={onClose} style={S.btn('#757575')}>취소</button>
          <button onClick={submit} disabled={loading}
            style={S.btn('#7b1fa2')}>{loading ? '처리중...' : '완료 처리'}</button>
        </div>
      </div>
    </div>
  );
}

/* ── 메인 페이지 ──────────────────────────────────────────── */
export default function ReturnManagement({ prefilledOrder, onMounted }) {
  const [returns, setReturns]       = useState([]);
  const [loading, setLoading]       = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [keyword, setKeyword]       = useState('');
  const [stats, setStats]           = useState({});
  const [showCreate, setShowCreate] = useState(false);
  const [createInit, setCreateInit] = useState(null);

  // CS에서 반품 접수 시 자동으로 모달 열기
  useEffect(() => {
    if (prefilledOrder) {
      setCreateInit(prefilledOrder);
      setShowCreate(true);
      if (onMounted) onMounted(); // deliveryParams 초기화
    }
  }, [prefilledOrder]);
  const [inspecting, setInspecting] = useState(null);  // 검수 대상
  const [resolving, setResolving]   = useState(null);  // 환불/교환 대상
  const [toast, setToast]           = useState('');

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3500);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      if (keyword.trim()) params.set('keyword', keyword.trim());

      const [retRes, statRes] = await Promise.all([
        fetch(`${API_BASE}/api/returns?${params}`),
        fetch(`${API_BASE}/api/returns/stats`),
      ]);
      const retData = await retRes.json();
      setReturns(Array.isArray(retData) ? retData : []);
      const statData = statRes.ok ? await statRes.json() : {};
      setStats(statData);
    } catch(e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, keyword]);

  useEffect(() => { load(); }, [load]);

  const onCreated = (r) => {
    setReturns(prev => [r, ...prev]);
    setStats(s => ({ ...s, requested: (s.requested||0)+1, total: (s.total||0)+1 }));
    showToast('✅ 반품 접수 완료');
  };

  const onInspected = (updated, stockMsg) => {
    setReturns(prev => prev.map(r => r.returnId === updated.returnId ? updated : r));
    showToast('✅ 검수 완료' + (stockMsg && stockMsg !== '재고 처리 없음 (SKU 미입력)' ? ` · ${stockMsg}` : ''));
  };

  const onResolved = (updated) => {
    setReturns(prev => prev.map(r => r.returnId === updated.returnId ? updated : r));
    showToast('✅ 처리 완료');
  };

  const cancelReturn = async (id) => {
    if (!confirm('이 반품을 취소하시겠습니까?\n검수 시 입고된 재고가 자동으로 차감됩니다.')) return;
    try {
      const res  = await fetch(`${API_BASE}/api/returns/${id}/cancel`, { method:'PUT' });
      const data = await res.json();
      if (data.success) {
        setReturns(prev => prev.map(r => r.returnId===id ? data.return : r));
        showToast('반품 취소 완료 — ' + (data.rollbackMessage || ''));
      } else {
        showToast('취소 실패');
      }
    } catch(e) {
      showToast('취소 실패: ' + e.message);
    }
  };

  return (
    <div style={S.page}>
      {/* 토스트 */}
      {toast && (
        <div style={{ position:'fixed', top:20, right:20, zIndex:2000,
          background:'#1a1a1a', color:'#fff', padding:'10px 20px',
          borderRadius:6, fontSize:'0.85rem', boxShadow:'0 4px 12px rgba(0,0,0,0.3)' }}>
          {toast}
        </div>
      )}

      {/* 헤더 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <div>
            <h1 style={{ fontSize:'1.2rem', fontWeight:700, margin:'0 0 4px', color:'#1a1a1a' }}>
              📦 반품 관리
            </h1>
            <div style={{ fontSize:'0.78rem', color:'#888' }}>
              취소 · 환불 · 교환 접수 및 검수 처리
            </div>
          </div>
          <button onClick={() => setShowCreate(true)} style={S.btn('#1565c0')}>
            + 반품 접수
          </button>
        </div>
      </div>

      {/* 통계 카드 */}
      <div style={{ display:'flex', gap:10, marginBottom:'1rem', flexWrap:'wrap' }}>
        {[
          { key:'requested',  label:'접수',   color:'#1565c0', bg:'#e3f2fd' },
          { key:'inspecting', label:'검수중', color:'#e65100', bg:'#fff3e0' },
          { key:'completed',  label:'완료',   color:'#2e7d32', bg:'#e8f5e9' },
          { key:'total',      label:'전체',   color:'#555',   bg:'#f5f5f5' },
        ].map(s => (
          <div key={s.key} style={{ background:s.bg, borderRadius:6, padding:'10px 18px',
            flex:'1 1 100px', textAlign:'center', cursor:'pointer',
            border:`1px solid ${s.color}22`
          }} onClick={() => setStatusFilter(s.key === 'total' ? 'ALL' : s.key.toUpperCase())}>
            <div style={{ fontSize:'1.4rem', fontWeight:700, color:s.color }}>
              {stats[s.key] ?? 0}
            </div>
            <div style={{ fontSize:'0.76rem', color:'#666' }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* 필터 */}
      <div style={{ ...S.card, display:'flex', gap:8, flexWrap:'wrap', alignItems:'center' }}>
        <div style={{ display:'flex', gap:4 }}>
          {['ALL','REQUESTED','INSPECTING','COMPLETED','CANCELLED'].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ ...S.btn(statusFilter===s ? '#1565c0' : '#f0f2f5',
                               statusFilter===s ? '#fff' : '#555'),
                border:'1px solid ' + (statusFilter===s ? '#1565c0' : '#ddd') }}>
              {s==='ALL' ? '전체' : RETURN_STATUSES[s]?.label}
            </button>
          ))}
        </div>
        <input value={keyword} onChange={e=>setKeyword(e.target.value)}
          onKeyDown={e=>e.key==='Enter'&&load()}
          placeholder="주문번호 / 수령자 / 상품명 검색"
          style={{ ...S.inp, minWidth:220 }}/>
        <button onClick={load} style={S.btn('#546e7a')}>🔍 조회</button>
      </div>

      {/* 테이블 */}
      <div style={{ ...S.card, padding:0, overflowX:'auto' }}>
        {loading ? (
          <div style={{ textAlign:'center', padding:'2rem', color:'#aaa' }}>조회 중...</div>
        ) : returns.length === 0 ? (
          <div style={{ textAlign:'center', padding:'3rem', color:'#bbb' }}>
            반품 내역이 없습니다
          </div>
        ) : (
          <table style={{ width:'100%', borderCollapse:'collapse', minWidth:1000 }}>
            <thead>
              <tr>
                {['접수일','판매처','주문번호','수령자','상품명','수량','유형','반품사유',
                  '상태','접수판정','접수창고','검수결과','검수창고','작업메모','처리'].map(h => (
                  <th key={h} style={S.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {returns.map((r, idx) => (
                <tr key={r.returnId}
                  style={{ background: idx%2===0 ? '#fff' : '#fafbff' }}>
                  <td style={{ ...S.td, fontSize:'0.72rem', color:'#777' }}>{fmtDate(r.createdAt)}</td>
                  <td style={S.td}>
                    {r.channelName && (
                      <span style={{ padding:'1px 6px', borderRadius:8, background:'#e3f2fd',
                        color:'#1565c0', fontSize:'0.7rem', fontWeight:700 }}>
                        {r.channelName}
                      </span>
                    )}
                  </td>
                  <td style={{ ...S.td, fontWeight:600, color:'#1565c0', fontSize:'0.75rem' }}>
                    {r.orderNo}
                  </td>
                  <td style={{ ...S.td, fontWeight:600 }}>{r.recipientName}</td>
                  <td style={{ ...S.td, textAlign:'left', maxWidth:160,
                    overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                    {r.productName}
                  </td>
                  <td style={S.td}>{r.quantity}</td>
                  <td style={S.td}>
                    <span style={{ fontSize:'0.73rem', fontWeight:600,
                      color: r.returnType==='CANCEL' ? '#757575'
                           : r.returnType==='REFUND'  ? '#c62828' : '#7b1fa2' }}>
                      {RETURN_TYPES[r.returnType] || r.returnType}
                    </span>
                  </td>
                  <td style={{ ...S.td, maxWidth:120, overflow:'hidden',
                    textOverflow:'ellipsis', whiteSpace:'nowrap', fontSize:'0.76rem', color:'#666' }}>
                    {r.returnReason || '-'}
                  </td>
                  <td style={S.td}><StatusBadge status={r.status}/></td>
                  {/* 접수 판정 */}
                  <td style={S.td}>
                    {r.receiveResult ? (
                      <span style={{ fontWeight:700, fontSize:'0.75rem',
                        color: r.receiveResult==='NORMAL' ? '#2e7d32' : '#c62828' }}>
                        {r.receiveResult==='NORMAL' ? '✅ 정상' : '❌ 불량'}
                      </span>
                    ) : '-'}
                  </td>
                  {/* 접수 창고 */}
                  <td style={{ ...S.td, fontSize:'0.75rem' }}>
                    {r.receiveWarehouseCode === 'ANYANG_KO_RETURN' ? '4.안양(국내온라인 반품)'
                     : r.receiveWarehouseCode === 'RETURN_POOR' ? '4.반품(불량)'
                     : r.receiveWarehouseCode || '-'}
                  </td>
                  {/* 검수 결과 */}
                  <td style={S.td}>
                    {r.inspectResult ? (
                      <span style={{ fontWeight:700, fontSize:'0.75rem',
                        color: r.inspectResult==='NORMAL' ? '#2e7d32' : '#c62828' }}>
                        {INSPECT_RESULTS[r.inspectResult]}
                      </span>
                    ) : '-'}
                  </td>
                  {/* 검수 후 창고 */}
                  <td style={{ ...S.td, fontSize:'0.75rem' }}>
                    {r.inspectResult === 'NORMAL' ? '본사(안양)'
                     : r.inspectResult === 'DEFECTIVE' ? '4.반품(불량)'
                     : '-'}
                  </td>
                  {/* 작업메모 */}
                  <td style={{ ...S.td, maxWidth:140, overflow:'hidden',
                    textOverflow:'ellipsis', whiteSpace:'nowrap', fontSize:'0.74rem', color:'#666',
                    textAlign:'left' }}>
                    {r.inspectMemo || r.receiveMemo || '-'}
                  </td>
                  <td style={{ ...S.td }} onClick={e=>e.stopPropagation()}>
                    <div style={{ display:'flex', gap:4, justifyContent:'center', flexWrap:'wrap' }}>
                      {/* 검수 버튼 (접수 상태만) */}
                      {r.status === 'REQUESTED' && (
                        <button onClick={() => setInspecting(r)}
                          style={{ ...S.btn('#e65100'), padding:'2px 8px', fontSize:'0.72rem' }}>
                          검수
                        </button>
                      )}
                      {/* 환불/교환 버튼 (검수 완료 후) */}
                      {(r.status === 'INSPECTING' || r.status === 'REQUESTED') && (
                        <button onClick={() => setResolving(r)}
                          style={{ ...S.btn('#7b1fa2'), padding:'2px 8px', fontSize:'0.72rem' }}>
                          처리
                        </button>
                      )}
                      {/* 취소 버튼 */}
                      {(r.status === 'REQUESTED' || r.status === 'INSPECTING') && (
                        <button onClick={() => cancelReturn(r.returnId)}
                          style={{ ...S.btn('#bdbdbd','#333'), padding:'2px 8px', fontSize:'0.72rem' }}>
                          취소
                        </button>
                      )}
                      {r.status === 'COMPLETED' && (
                        <span style={{ fontSize:'0.72rem', color:'#2e7d32' }}>✓ 완료</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 모달들 */}
      {showCreate && <CreateModal onClose={() => { setShowCreate(false); setCreateInit(null); }} onCreated={onCreated} init={createInit}/>}
      {inspecting && <InspectModal ret={inspecting} onClose={() => setInspecting(null)} onUpdated={onInspected}/>}
      {resolving  && <ResolveModal ret={resolving}  onClose={() => setResolving(null)}  onUpdated={onResolved}/>}
    </div>
  );
}
