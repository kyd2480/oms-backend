import { useState } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

const S = {
  th:  { padding:'5px 8px', background:'#f5f6fa', borderBottom:'1px solid #dde3ef',
         fontSize:'0.73rem', fontWeight:700, color:'#444', textAlign:'center', whiteSpace:'nowrap' },
  td:  { padding:'6px 8px', borderBottom:'1px solid #f0f0f0',
         fontSize:'0.78rem', color:'#222', textAlign:'center' },
  inp: { padding:'5px 8px', border:'1px solid #ccc', borderRadius:3, fontSize:'0.8rem', outline:'none' },
  btn: (bg, c='#fff') => ({
    padding:'4px 12px', background:bg, color:c, border:'none',
    borderRadius:3, cursor:'pointer', fontSize:'0.78rem', fontWeight:600, whiteSpace:'nowrap',
  }),
};

const STATUS_META = {
  PENDING:   { label:'매칭대기', color:'#e65100', bg:'#fff3e0' },
  CONFIRMED: { label:'처리중',   color:'#1565c0', bg:'#e3f2fd' },
  CANCELLED: { label:'취소완료', color:'#c62828', bg:'#ffebee' },
};
function StatusBadge({ status }) {
  const m = STATUS_META[status] || { label:status, color:'#888', bg:'#f5f5f5' };
  return <span style={{ padding:'2px 8px', borderRadius:8, fontSize:'0.72rem',
    fontWeight:700, background:m.bg, color:m.color }}>{m.label}</span>;
}
function fmtDate(str) {
  if (!str) return '-';
  try { return new Date(str).toLocaleDateString('ko-KR'); } catch { return str; }
}
function fmt(d) { return d.toISOString().slice(0,10); }

export default function CancelManagement() {
  const today   = new Date();
  const weekAgo = new Date(today); weekAgo.setDate(today.getDate()-6);

  const [tab,        setTab]        = useState('pending');
  const [startDate,  setStartDate]  = useState(fmt(weekAgo));
  const [endDate,    setEndDate]    = useState(fmt(today));
  const [keyword,    setKeyword]    = useState('');
  const [orders,     setOrders]     = useState(null);
  const [loading,    setLoading]    = useState(false);
  const [msg,        setMsg]        = useState('');
  const [cancelling, setCancelling] = useState(null);

  const applyPreset = p => {
    const end = new Date(), start = new Date();
    if      (p==='어제')   { start.setDate(end.getDate()-1); end.setDate(end.getDate()-1); }
    else if (p==='일주일') { start.setDate(end.getDate()-6); }
    else if (p==='한달')   { start.setMonth(end.getMonth()-1); }
    else if (p==='3개월')  { start.setMonth(end.getMonth()-3); }
    setStartDate(fmt(start)); setEndDate(fmt(end));
  };

  const doSearch = async (overrideTab) => {
    const activeTab = overrideTab || tab;
    setLoading(true); setMsg(''); setOrders(null);
    try {
      const params = new URLSearchParams({ startDate, endDate });
      if (keyword.trim()) params.set('keyword', keyword.trim());
      const endpoint = activeTab === 'pending'
        ? `/api/cancel/pending?${params}`
        : `/api/cancel/orders?${params}`;
      const res  = await fetch(`${API_BASE}${endpoint}`);
      if (!res.ok) throw new Error(`서버 오류 ${res.status}`);
      const data = await res.json();
      setOrders(Array.isArray(data) ? data : []);
      setMsg(`${Array.isArray(data) ? data.length : 0}건`);
    } catch(e) { setMsg('조회 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const switchTab = (t) => { setTab(t); setOrders(null); setMsg(''); };

  const doCancel = async (orderNo) => {
    if (!window.confirm(
      `[${orderNo}] 주문을 취소 처리하시겠습니까?\n\n` +
      `• CONFIRMED 상태라면 예약 재고도 자동 해제됩니다\n` +
      `• 취소된 주문은 재고 매칭에서 영구 제외됩니다`
    )) return;
    setCancelling(orderNo);
    try {
      const res  = await fetch(`${API_BASE}/api/cancel/orders/${encodeURIComponent(orderNo)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }
      });
      const data = await res.json();
      if (data.success) {
        setMsg('✅ 취소 완료: ' + orderNo);
        setOrders(prev => prev ? prev.filter(o => o.orderNo !== orderNo) : prev);
      } else {
        alert(data.message || '취소 실패');
      }
    } catch(e) { alert('취소 실패: ' + e.message); }
    finally { setCancelling(null); }
  };

  return (
    <div style={{ padding:'1.25rem', background:'#f0f2f5', minHeight:'100vh',
      fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif", fontSize:'0.84rem' }}>

      <div style={{ background:'#fff', borderRadius:6, padding:'0.9rem 1.25rem',
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'0.75rem' }}>
        <h1 style={{ fontSize:'1.15rem', fontWeight:700, margin:'0 0 4px', color:'#1a1a1a' }}>
          🚫 배송전 취소
        </h1>
        <div style={{ fontSize:'0.78rem', color:'#888' }}>
          발송 전 취소 처리 — 취소된 주문은 재고 매칭에서 자동 제외됩니다
        </div>
      </div>

      {/* 탭 */}
      <div style={{ display:'flex', marginBottom:'0.75rem', background:'#fff',
        borderRadius:6, overflow:'hidden', boxShadow:'0 1px 4px rgba(0,0,0,0.08)' }}>
        {[
          { key:'pending',   label:'📋 취소 처리 대기', color:'#e65100' },
          { key:'cancelled', label:'✅ 취소 완료 내역', color:'#c62828' },
        ].map(t => (
          <button key={t.key} onClick={() => switchTab(t.key)}
            style={{ flex:1, padding:'11px 0', border:'none', cursor:'pointer',
              fontSize:'0.85rem', fontWeight: tab===t.key ? 700 : 400,
              background: tab===t.key ? '#fff' : '#f8f8f8',
              color: tab===t.key ? t.color : '#999',
              borderBottom: tab===t.key ? `2px solid ${t.color}` : '2px solid transparent' }}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'pending' && (
        <div style={{ background:'#fff3e0', borderRadius:6, padding:'10px 16px',
          marginBottom:'0.75rem', border:'1px solid #ffcc80', fontSize:'0.8rem', color:'#e65100' }}>
          ⚠ <strong>취소 처리</strong>를 누르면 해당 주문은 <strong>재고 매칭 대상에서 영구 제외</strong>됩니다.
          CONFIRMED 상태 주문은 <strong>예약 재고도 자동 해제</strong>됩니다.
        </div>
      )}

      {/* 검색 */}
      <div style={{ background:'#fff', borderRadius:6, padding:'0.9rem 1.25rem',
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'0.75rem' }}>
        <div style={{ display:'flex', gap:8, alignItems:'center', marginBottom:8, flexWrap:'wrap' }}>
          {['어제','일주일','한달','3개월'].map(p => (
            <button key={p} onClick={() => applyPreset(p)}
              style={{ ...S.btn('#f0f2f5','#555'), border:'1px solid #ddd', padding:'4px 10px' }}>
              {p}
            </button>
          ))}
          <input type="date" value={startDate} onChange={e=>setStartDate(e.target.value)} style={S.inp}/>
          <span style={{ color:'#aaa' }}>~</span>
          <input type="date" value={endDate} onChange={e=>setEndDate(e.target.value)} style={S.inp}/>
        </div>
        <div style={{ display:'flex', gap:8, alignItems:'center' }}>
          <input value={keyword} onChange={e=>setKeyword(e.target.value)}
            onKeyDown={e=>e.key==='Enter'&&doSearch()}
            placeholder="주문번호, 수취인, 연락처, 상품명"
            style={{ ...S.inp, minWidth:280 }}/>
          <button onClick={() => doSearch()} disabled={loading} style={S.btn('#e53935')}>
            {loading ? '⏳ 조회 중...' : '🔍 조회'}
          </button>
          {msg && <span style={{ fontSize:'0.78rem',
            color: msg.startsWith('✅') ? '#2e7d32' : '#888' }}>{msg}</span>}
        </div>
      </div>

      {/* 테이블 */}
      <div style={{ background:'#fff', borderRadius:6,
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', overflowX:'auto' }}>
        {orders === null ? (
          <div style={{ textAlign:'center', padding:'3rem', color:'#bbb' }}>
            <div style={{ fontSize:'2rem', marginBottom:8 }}>🔍</div>
            조회 버튼을 눌러 {tab==='pending' ? '취소 대기 주문을' : '취소 완료 내역을'} 불러오세요
          </div>
        ) : orders.length === 0 ? (
          <div style={{ textAlign:'center', padding:'2rem', color:'#bbb' }}>
            {tab==='pending' ? '취소 대기 중인 주문이 없습니다' : '취소 완료된 주문이 없습니다'}
          </div>
        ) : (
          <table style={{ width:'100%', borderCollapse:'collapse', minWidth:820 }}>
            <thead>
              <tr>
                <th style={S.th}>주문일자</th>
                {tab==='pending'
                  ? <th style={S.th}>주문번호</th>
                  : <th style={S.th}>취소일자</th>}
                <th style={S.th}>판매처</th>
                <th style={S.th}>수령자</th>
                <th style={S.th}>연락처</th>
                <th style={S.th}>상품명</th>
                <th style={S.th}>수량</th>
                <th style={S.th}>상태</th>
                {tab==='pending' && <th style={S.th}>취소 처리</th>}
              </tr>
            </thead>
            <tbody>
              {orders.map((o, idx) => (
                <tr key={o.orderNo} style={{ background: idx%2===0 ? '#fff' : '#fafbff' }}>
                  <td style={{ ...S.td, fontSize:'0.72rem', color:'#777' }}>{fmtDate(o.orderedAt)}</td>
                  {tab==='pending'
                    ? <td style={{ ...S.td, fontWeight:600, fontSize:'0.75rem', color:'#1565c0' }}>{o.orderNo}</td>
                    : <td style={{ ...S.td, fontSize:'0.72rem', color:'#c62828' }}>{fmtDate(o.cancelledAt)}</td>}
                  <td style={S.td}>
                    {o.channelName && !o.channelName.includes('@')
                      ? <span style={{ padding:'1px 6px', borderRadius:8, background:'#e3f2fd',
                          color:'#1565c0', fontSize:'0.7rem', fontWeight:700 }}>{o.channelName}</span>
                      : '-'}
                  </td>
                  <td style={{ ...S.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                  <td style={{ ...S.td, fontSize:'0.72rem', color:'#555' }}>{o.recipientPhone||'-'}</td>
                  <td style={{ ...S.td, textAlign:'left', maxWidth:220,
                    overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                    {o.productName||'-'}
                  </td>
                  <td style={S.td}>{o.quantity||0}</td>
                  <td style={S.td}><StatusBadge status={o.orderStatus}/></td>
                  {tab==='pending' && (
                    <td style={S.td}>
                      <button onClick={() => doCancel(o.orderNo)}
                        disabled={cancelling === o.orderNo}
                        style={{ ...S.btn('#e53935'), fontSize:'0.72rem', padding:'3px 10px' }}>
                        {cancelling === o.orderNo ? '처리중...' : '취소 처리'}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
