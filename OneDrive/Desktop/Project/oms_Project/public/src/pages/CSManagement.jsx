import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

const S = {
  th:  { padding:'5px 8px', background:'#f5f6fa', borderBottom:'1px solid #dde3ef',
         fontSize:'0.73rem', fontWeight:700, color:'#444', textAlign:'center', whiteSpace:'nowrap' },
  td:  { padding:'5px 8px', borderBottom:'1px solid #f0f0f0',
         fontSize:'0.78rem', color:'#222', textAlign:'center' },
  inp: { padding:'5px 8px', border:'1px solid #ccc', borderRadius:3,
         fontSize:'0.8rem', color:'#111', outline:'none' },
  btn: (bg, c='#fff') => ({
    padding:'4px 12px', background:bg, color:c, border:'none',
    borderRadius:3, cursor:'pointer', fontSize:'0.78rem', fontWeight:600, whiteSpace:'nowrap',
  }),
};

function fmtDate(str) {
  if (!str) return '-';
  try { return new Date(str).toLocaleDateString('ko-KR'); } catch { return str; }
}
function fmtDateTime(str) {
  if (!str) return '-';
  try { return str.replace('T',' ').substring(0,16); } catch { return str; }
}
function fmt(d) { return d.toISOString().slice(0,10); }

const STATUS_META = {
  PENDING:   { label:'매칭대기', color:'#e65100', bg:'#fff3e0' },
  CONFIRMED: { label:'처리중',   color:'#1565c0', bg:'#e3f2fd' },
  SHIPPED:   { label:'발송완료', color:'#2e7d32', bg:'#e8f5e9' },
};
function StatusBadge({ status }) {
  const m = STATUS_META[status] || { label:status||'-', color:'#888', bg:'#f5f5f5' };
  return <span style={{ padding:'1px 7px', borderRadius:8, fontSize:'0.71rem',
    fontWeight:700, background:m.bg, color:m.color }}>{m.label}</span>;
}

const CS_TYPES = ['01.일반상담','02.배송지연','03.교환요청','04.반품요청','05.취소요청','06.기타'];
const CS_DEPTS = ['CS상담구분','고객문의','판매처문의','내부처리'];
const CS_KINDS = ['선택안함','불량','사이즈오류','배송오류','단순변심','기타'];
const STATUS_OPTS = ['미처리','처리중','완료'];

/* ══════════════ 하단 상세 패널 ══════════════ */
function DetailPanel({ order, onClose, onNavigate }) {
  const [csType,     setCsType]     = useState(CS_TYPES[0]);
  const [csDept,     setCsDept]     = useState(CS_DEPTS[0]);
  const [csKind,     setCsKind]     = useState(CS_KINDS[0]);
  const [content,    setContent]    = useState('');
  const [memoStatus, setMemoStatus] = useState('미처리');
  const [memos,      setMemos]      = useState([]);
  const [returns,    setReturns]    = useState([]);
  const [saving,     setSaving]     = useState(false);

  useEffect(() => {
    if (!order?.orderNo) return;
    // CS 메모 조회
    fetch(`${API_BASE}/api/cs-memos/${encodeURIComponent(order.orderNo)}`)
      .then(r => r.ok ? r.json() : [])
      .then(data => setMemos(Array.isArray(data) ? data : []))
      .catch(() => setMemos([]));
    // 반품 이력 조회
    fetch(`${API_BASE}/api/returns?orderNo=${encodeURIComponent(order.orderNo)}`)
      .then(r => r.ok ? r.json() : [])
      .then(data => setReturns(Array.isArray(data) ? data : []))
      .catch(() => setReturns([]));
  }, [order?.orderNo]);

  // 반품 이력 → 타임라인 항목으로 변환
  const returnEvents = returns.flatMap(ret => {
    const events = [];
    const RETURN_TYPE_LABEL = { CANCEL:'취소', REFUND:'환불', EXCHANGE:'교환' };
    const typeLabel = RETURN_TYPE_LABEL[ret.returnType] || ret.returnType || '';
    const base = `[반품${typeLabel ? ' · ' + typeLabel : ''}] ${ret.productName || ''}`;
    // 접수 이벤트
    events.push({
      _type: 'return',
      _kind: 'receive',
      createdAt: ret.createdAt,
      label: `📦 반품 접수`,
      detail: base,
      content: [
        ret.returnReason && `사유: ${ret.returnReason}`,
        ret.receiveResult && `판정: ${ret.receiveResult === 'NORMAL' ? '정상' : '불량'}`,
        ret.receiveMemo,
      ].filter(Boolean).join('\n'),
      status: ret.status,
    });
    // 검수 이벤트
    if (ret.inspectMemo || ret.inspectResult) {
      events.push({
        _type: 'return',
        _kind: 'inspect',
        createdAt: ret.updatedAt || ret.createdAt,
        label: `🔍 반품 검수`,
        detail: base,
        content: [
          ret.inspectResult && `결과: ${ret.inspectResult === 'NORMAL' ? '정상' : '불량'}`,
          ret.inspectMemo,
        ].filter(Boolean).join('\n'),
        status: ret.status,
      });
    }
    // 처리완료/취소 이벤트
    if (ret.resolutionMemo || ret.status === 'COMPLETED' || ret.status === 'CANCELLED') {
      events.push({
        _type: 'return',
        _kind: 'resolve',
        createdAt: ret.completedAt || ret.updatedAt || ret.createdAt,
        label: ret.status === 'CANCELLED' ? '❌ 반품 취소' : '✅ 반품 처리완료',
        detail: base,
        content: [
          ret.resolutionType && `처리: ${ret.resolutionType === 'REFUND' ? '환불' : ret.resolutionType === 'EXCHANGE' ? '교환' : ret.resolutionType}`,
          ret.refundAmount && `환불금액: ${ret.refundAmount.toLocaleString()}원`,
          ret.resolutionMemo,
        ].filter(Boolean).join('\n'),
        status: ret.status,
      });
    }
    return events;
  });

  const saveMemo = async () => {
    if (!content.trim()) { alert('처리내용을 입력해주세요.'); return; }
    setSaving(true);
    try {
      const r = await fetch(`${API_BASE}/api/cs-memos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderNo: order.orderNo, csType, csDept, csKind,
          content, status: memoStatus, writer: '관리자'
        })
      });
      if (r.ok) {
        const saved = await r.json();
        setMemos(prev => [...prev, saved]);
        setContent('');
      }
    } catch(e) { alert('저장 실패: ' + e.message); }
    finally { setSaving(false); }
  };

  return (
    <div style={{
      position:'fixed', bottom:0, left:220, right:0, zIndex:200,
      background:'#fff', borderTop:'2px solid #1565c0',
      boxShadow:'0 -4px 16px rgba(0,0,0,0.13)',
      height:400, display:'flex', flexDirection:'column',
    }}>
      {/* 헤더 */}
      <div style={{ display:'flex', alignItems:'center', padding:'6px 12px',
        background:'#eef2fb', borderBottom:'1px solid #dde3ef', flexShrink:0, gap:8 }}>
        <span style={{ fontWeight:700, fontSize:'0.85rem', color:'#1565c0' }}>
          📋 {order.orderNo}
        </span>
        <span style={{ fontSize:'0.79rem', color:'#555' }}>
          {order.channelName && !order.channelName.includes('@') && !order.channelName.includes('entity.') && (
            <span style={{ padding:'1px 6px', borderRadius:8,
              background:'#e3f2fd', color:'#1565c0', fontSize:'0.72rem', fontWeight:700,
              marginRight:6 }}>{order.channelName}</span>
          )}
          {order.recipientName} · {order.productName?.slice(0,25)}
          {order.trackingNo && ` · ${order.carrierName||''} ${order.trackingNo}`}
        </span>
        <div style={{ flex:1 }}/>
        <button onClick={() => onNavigate && onNavigate('return.management', { newReturn: order })}
          style={{ ...S.btn('#7b1fa2'), fontSize:'0.76rem' }}>
          📦 반품 접수
        </button>
        {order.trackingNo && (
          <button onClick={() => window.open(
            `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${order.trackingNo}&displayHeader=N`,
            'delivery','width=900,height=700,scrollbars=yes'
          )} style={{ ...S.btn('#1565c0'), fontSize:'0.76rem' }}>
            🚚 배송조회
          </button>
        )}
        <button onClick={onClose}
          style={{ ...S.btn('#bbb','#333'), padding:'3px 10px' }}>✕</button>
      </div>

      {/* 바디 */}
      <div style={{ flex:1, display:'flex', overflow:'hidden' }}>
        {/* 좌: 상품 목록 */}
        <div style={{ width:320, borderRight:'1px solid #eee', overflowY:'auto', padding:'8px 10px',
          flexShrink:0 }}>
          <div style={{ fontWeight:700, fontSize:'0.78rem', color:'#555', marginBottom:6 }}>
            📦 상품 목록
          </div>
          {(order.items||[]).length === 0 ? (
            <div style={{ fontSize:'0.8rem', color:'#222', padding:'4px 0' }}>
              {order.productName || '-'}
              {order.quantity ? ` × ${order.quantity}개` : ''}
            </div>
          ) : (order.items||[]).map((it,i) => (
            <div key={i} style={{ padding:'6px 0', borderBottom:'1px solid #f0f0f0', fontSize:'0.78rem' }}>
              <div style={{ fontWeight:600, color:'#1a1a1a' }}>{it.productName}</div>
              <div style={{ color:'#666', fontSize:'0.72rem', marginTop:2 }}>
                {it.optionName && <span style={{ background:'#f0f2f5', padding:'1px 5px',
                  borderRadius:3, marginRight:4 }}>{it.optionName}</span>}
                수량 <strong>{it.quantity}</strong>개
              </div>
            </div>
          ))}
          <div style={{ marginTop:10, fontSize:'0.74rem', color:'#777', lineHeight:1.8 }}>
            <div>📅 주문: {fmtDate(order.orderedAt)}</div>
            {order.shippedAt && <div>🚚 발송: {fmtDate(order.shippedAt)}</div>}
            {order.address && <div>📍 {order.address}</div>}
            {order.recipientPhone && <div>📞 {order.recipientPhone}</div>}
          </div>
        </div>

        {/* 우: CS 메모 */}
        <div style={{ flex:1, display:'flex', flexDirection:'column', overflow:'hidden' }}>
          {/* 통합 타임라인 */}
          <div style={{ flex:1, overflowY:'auto', padding:'8px 12px' }}>
            {(() => {
              // CS메모 + 반품이벤트 시간순 병합
              const csMemoItems = memos.map(m => ({ ...m, _type: 'memo' }));
              const allItems = [...csMemoItems, ...returnEvents]
                .sort((a, b) => new Date(a.createdAt||0) - new Date(b.createdAt||0));
              return (
                <>
                  <div style={{ fontWeight:700, fontSize:'0.78rem', color:'#555', marginBottom:6 }}>
                    🗒 작업 이력 ({allItems.length}건) — 시간순
                  </div>
                  {allItems.length === 0 ? (
                    <div style={{ fontSize:'0.78rem', color:'#ccc', textAlign:'center', paddingTop:16 }}>
                      메모가 없습니다. 아래에 내용을 입력하고 저장하세요.
                    </div>
                  ) : allItems.map((item, i) => {
                    if (item._type === 'memo') {
                      const m = item;
                      return (
                        <div key={m.memoId||i} style={{
                          padding:'8px 10px', marginBottom:6, borderRadius:5,
                          background: m.status==='완료' ? '#f1f8e9' : m.status==='처리중' ? '#e3f2fd' : '#fff8e1',
                          border: '1px solid ' + (m.status==='완료' ? '#c5e1a5' : m.status==='처리중' ? '#90caf9' : '#ffe082'),
                        }}>
                          <div style={{ display:'flex', justifyContent:'space-between',
                            fontSize:'0.71rem', color:'#888', marginBottom:3 }}>
                            <span>
                              <strong style={{ color:'#333' }}>{m.csType}</strong>
                              {m.csDept && m.csDept !== 'CS상담구분' && ` · ${m.csDept}`}
                              {m.csKind && m.csKind !== '선택안함' && ` · ${m.csKind}`}
                            </span>
                            <span style={{ display:'flex', gap:6, alignItems:'center' }}>
                              <span style={{
                                padding:'1px 6px', borderRadius:8, fontSize:'0.7rem', fontWeight:700,
                                color: m.status==='완료' ? '#2e7d32' : m.status==='처리중' ? '#1565c0' : '#e65100',
                                background: m.status==='완료' ? '#e8f5e9' : m.status==='처리중' ? '#e3f2fd' : '#fff3e0',
                              }}>{m.status}</span>
                              {fmtDateTime(m.createdAt)}
                            </span>
                          </div>
                          <div style={{ fontSize:'0.81rem', color:'#222', whiteSpace:'pre-wrap' }}>{m.content}</div>
                        </div>
                      );
                    } else {
                      // 반품 이벤트
                      const ev = item;
                      const kindColor = ev._kind === 'receive' ? '#7b1fa2'
                                      : ev._kind === 'inspect' ? '#e65100'
                                      : ev.status === 'CANCELLED' ? '#757575' : '#2e7d32';
                      const kindBg = ev._kind === 'receive' ? '#f3e5f5'
                                   : ev._kind === 'inspect' ? '#fff3e0'
                                   : ev.status === 'CANCELLED' ? '#f5f5f5' : '#e8f5e9';
                      return (
                        <div key={`ret-${i}`} style={{
                          padding:'8px 10px', marginBottom:6, borderRadius:5,
                          background: kindBg,
                          border: `1px solid ${kindColor}44`,
                        }}>
                          <div style={{ display:'flex', justifyContent:'space-between',
                            fontSize:'0.71rem', color:'#888', marginBottom:3 }}>
                            <span style={{ fontWeight:700, color: kindColor }}>{ev.label}</span>
                            <span style={{ fontSize:'0.7rem', color:'#999' }}>{fmtDateTime(ev.createdAt)}</span>
                          </div>
                          {ev.detail && (
                            <div style={{ fontSize:'0.75rem', color:'#555', marginBottom:3 }}>{ev.detail}</div>
                          )}
                          {ev.content && (
                            <div style={{ fontSize:'0.81rem', color:'#222', whiteSpace:'pre-wrap' }}>{ev.content}</div>
                          )}
                        </div>
                      );
                    }
                  })}
                </>
              );
            })()}
          </div>

          {/* 메모 입력 */}
          <div style={{ borderTop:'1px solid #eee', padding:'8px 12px',
            background:'#fafbff', flexShrink:0 }}>
            <div style={{ display:'flex', gap:6, marginBottom:6, flexWrap:'wrap' }}>
              <select value={csType} onChange={e=>setCsType(e.target.value)}
                style={{ ...S.inp, fontSize:'0.75rem' }}>
                {CS_TYPES.map(t=><option key={t}>{t}</option>)}
              </select>
              <select value={csDept} onChange={e=>setCsDept(e.target.value)}
                style={{ ...S.inp, fontSize:'0.75rem' }}>
                {CS_DEPTS.map(t=><option key={t}>{t}</option>)}
              </select>
              <select value={csKind} onChange={e=>setCsKind(e.target.value)}
                style={{ ...S.inp, fontSize:'0.75rem' }}>
                {CS_KINDS.map(t=><option key={t}>{t}</option>)}
              </select>
              <select value={memoStatus} onChange={e=>setMemoStatus(e.target.value)}
                style={{ ...S.inp, fontSize:'0.75rem' }}>
                {STATUS_OPTS.map(t=><option key={t}>{t}</option>)}
              </select>
            </div>
            <div style={{ display:'flex', gap:6 }}>
              <textarea value={content} onChange={e=>setContent(e.target.value)}
                placeholder="처리 내용 입력 (Ctrl+Enter 빠른 저장)"
                style={{ ...S.inp, flex:1, height:50, resize:'none', fontSize:'0.8rem' }}
                onKeyDown={e=>{ if(e.ctrlKey&&e.key==='Enter') saveMemo(); }}/>
              <button onClick={saveMemo} disabled={saving}
                style={{ ...S.btn('#1565c0'), alignSelf:'stretch', padding:'0 18px' }}>
                {saving ? '...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ══════════════ 메인 ══════════════ */
export default function CSManagement({ onNavigate }) {
  const today   = new Date();
  const weekAgo = new Date(today); weekAgo.setDate(today.getDate()-6);

  const [dateType,     setDateType]     = useState('주문일자');
  const [period,       setPeriod]       = useState('일주일');
  const [startDate,    setStartDate]    = useState(fmt(weekAgo));
  const [endDate,      setEndDate]      = useState(fmt(today));
  const [searchType,   setSearchType]   = useState('통합검색');
  const [searchKw,     setSearchKw]     = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [orders,       setOrders]       = useState(null);
  const [loading,      setLoading]      = useState(false);
  const [msg,          setMsg]          = useState('');
  const [selected,     setSelected]     = useState(null);

  const applyPreset = p => {
    setPeriod(p);
    const end=new Date(), start=new Date();
    if      (p==='어제')  { start.setDate(end.getDate()-1); end.setDate(end.getDate()-1); }
    else if (p==='일주일'){ start.setDate(end.getDate()-6); }
    else if (p==='한달')  { start.setMonth(end.getMonth()-1); }
    else if (p==='3개월') { start.setMonth(end.getMonth()-3); }
    setStartDate(fmt(start)); setEndDate(fmt(end));
  };

  const doSearch = async () => {
    setLoading(true); setMsg(''); setSelected(null);
    try {
      const params = new URLSearchParams({
        startDate:  startDate,
        endDate:    endDate,
        dateType:   dateType === '발송일자' ? 'shipped' : 'ordered',
        searchType: searchType,
      });
      if (searchKw.trim()) params.set('keyword', searchKw.trim());

      const res  = await fetch(`${API_BASE}/api/cs/orders?${params}`);
      if (!res.ok) throw new Error(`서버 오류 ${res.status}`);
      const data = await res.json();
      const list = Array.isArray(data) ? data : [];

      const mapped = list.map(o => ({
        ...o,
        _status:
          o.orderStatus === 'SHIPPED'   ? 'SHIPPED'   :
          o.orderStatus === 'CONFIRMED' ? 'CONFIRMED' : 'PENDING',
      }));

      setOrders(mapped);
      setMsg(`${mapped.length}건`);
    } catch(e) { setMsg('조회 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const filtered = orders===null ? null :
    statusFilter==='ALL' ? orders :
    orders.filter(o => o._status===statusFilter);

  const counts = {
    ALL:       orders?.length ?? 0,
    PENDING:   orders?.filter(o=>o._status==='PENDING').length ?? 0,
    CONFIRMED: orders?.filter(o=>o._status==='CONFIRMED').length ?? 0,
    SHIPPED:   orders?.filter(o=>o._status==='SHIPPED').length ?? 0,
  };

  return (
    <div style={{ padding:'1.25rem', background:'#f0f2f5', minHeight:'100vh',
      fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif", fontSize:'0.84rem',
      paddingBottom: selected ? 420 : '1.25rem' }}>

      {/* 헤더 */}
      <div style={{ background:'#fff', borderRadius:6, padding:'0.9rem 1.25rem',
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'0.75rem' }}>
        <h1 style={{ fontSize:'1.15rem', fontWeight:700, margin:0, color:'#1a1a1a' }}>
          🎧 CS 관리
        </h1>
      </div>

      {/* 검색 */}
      <div style={{ background:'#fff', borderRadius:6, padding:'0.9rem 1.25rem',
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'0.75rem' }}>
        <div style={{ display:'flex', gap:8, alignItems:'center', marginBottom:8, flexWrap:'wrap' }}>
          <select value={dateType} onChange={e=>setDateType(e.target.value)} style={S.inp}>
            <option>주문일자</option><option>발송일자</option>
          </select>
          {['어제','일주일','한달','3개월'].map(p=>(
            <button key={p} onClick={()=>applyPreset(p)}
              style={{ ...S.btn(period===p?'#1565c0':'#f0f2f5', period===p?'#fff':'#555'),
                border:'1px solid '+(period===p?'#1565c0':'#ddd'), padding:'4px 10px' }}>
              {p}
            </button>
          ))}
          <input type="date" value={startDate} onChange={e=>setStartDate(e.target.value)} style={S.inp}/>
          <span style={{ color:'#aaa' }}>~</span>
          <input type="date" value={endDate} onChange={e=>setEndDate(e.target.value)} style={S.inp}/>
        </div>
        <div style={{ display:'flex', gap:8, alignItems:'center', flexWrap:'wrap' }}>
          <select value={searchType} onChange={e=>setSearchType(e.target.value)} style={S.inp}>
            {['통합검색','주문번호','수취인','연락처','송장번호','상품명'].map(t=>(
              <option key={t}>{t}</option>
            ))}
          </select>
          <input value={searchKw} onChange={e=>setSearchKw(e.target.value)}
            onKeyDown={e=>e.key==='Enter'&&doSearch()}
            placeholder="검색어 입력 (선택한 날짜 범위 내에서 검색)"
            style={{ ...S.inp, minWidth:320 }}/>
          <button onClick={doSearch} disabled={loading} style={S.btn('#1565c0')}>
            {loading ? '⏳ 조회 중...' : '🔍 검색'}
          </button>
          {msg && <span style={{ fontSize:'0.78rem', color:'#888' }}>{msg}</span>}
        </div>
      </div>

      {/* 상태 필터 */}
      {orders !== null && (
        <div style={{ display:'flex', gap:6, marginBottom:'0.75rem' }}>
          {[
            { key:'ALL',       label:`전체 ${counts.ALL}건` },
            { key:'PENDING',   label:`매칭대기 ${counts.PENDING}건` },
            { key:'CONFIRMED', label:`처리중 ${counts.CONFIRMED}건` },
            { key:'SHIPPED',   label:`발송완료 ${counts.SHIPPED}건` },
          ].map(s=>(
            <button key={s.key} onClick={()=>setStatusFilter(s.key)}
              style={{ padding:'4px 14px', borderRadius:16, cursor:'pointer',
                fontWeight:600, fontSize:'0.76rem',
                background: statusFilter===s.key ? '#1565c0' : '#f0f2f5',
                color:      statusFilter===s.key ? '#fff'    : '#555',
                border: 'none' }}>
              {s.label}
            </button>
          ))}
        </div>
      )}

      {/* 테이블 */}
      <div style={{ background:'#fff', borderRadius:6,
        boxShadow:'0 1px 4px rgba(0,0,0,0.08)', overflowX:'auto' }}>
        {orders === null ? (
          <div style={{ textAlign:'center', padding:'3rem', color:'#bbb' }}>
            <div style={{ fontSize:'2rem', marginBottom:8 }}>🔍</div>
            검색하면 주문 목록이 표시됩니다<br/>
            <span style={{ fontSize:'0.78rem' }}>주문 행을 클릭하면 CS 메모를 작성할 수 있습니다</span>
          </div>
        ) : !filtered?.length ? (
          <div style={{ textAlign:'center', padding:'2rem', color:'#bbb' }}>조회된 주문이 없습니다</div>
        ) : (
          <table style={{ width:'100%', borderCollapse:'collapse', minWidth:900 }}>
            <thead>
              <tr>
                {['주문일자','판매처','수령자','연락처','상품명','수량',
                  '택배사','송장번호','상태','발송일자','배송조회'].map(h=>(
                  <th key={h} style={S.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((o, idx) => {
                const isSel = selected?.orderNo===o.orderNo;
                return (
                  <tr key={o.orderNo}
                    onClick={()=>setSelected(isSel?null:o)}
                    style={{
                      background: isSel ? '#dbeafe' : idx%2===0 ? '#fff' : '#fafbff',
                      cursor:'pointer',
                      outline: isSel ? '2px solid #1565c0' : 'none',
                      outlineOffset:'-1px',
                    }}
                    onMouseEnter={e=>{ if(!isSel) e.currentTarget.style.background='#eef4ff'; }}
                    onMouseLeave={e=>{ if(!isSel) e.currentTarget.style.background=idx%2===0?'#fff':'#fafbff'; }}>
                    <td style={{ ...S.td, fontSize:'0.72rem', color:'#777' }}>{fmtDate(o.orderedAt)}</td>
                    <td style={S.td}>
                      {(() => {
                        const ch = o.channelName;
                        if (!ch) return '-';
                        // SalesChannel toString 형태 제거
                        if (ch.includes('@') || ch.includes('entity.')) return '-';
                        return <span style={{ padding:'1px 6px', borderRadius:8, background:'#e3f2fd',
                          color:'#1565c0', fontSize:'0.7rem', fontWeight:700 }}>{ch}</span>;
                      })()}
                    </td>
                    <td style={{ ...S.td, fontWeight:600 }}>{o.recipientName||'-'}</td>
                    <td style={{ ...S.td, fontSize:'0.72rem', color:'#555' }}>{o.recipientPhone||'-'}</td>
                    <td style={{ ...S.td, textAlign:'left', maxWidth:180,
                      overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                      {o.productName||'-'}
                    </td>
                    <td style={S.td}>{o.quantity||0}</td>
                    <td style={S.td}>
                      {o.carrierName
                        ? <span style={{ padding:'1px 6px', borderRadius:8, background:'#e8f5e9',
                            color:'#2e7d32', fontSize:'0.7rem', fontWeight:700 }}>{o.carrierName}</span>
                        : '-'}
                    </td>
                    <td style={{ ...S.td, color:'#1565c0', fontWeight:600, fontSize:'0.72rem' }}>
                      {o.trackingNo||'-'}
                    </td>
                    <td style={S.td}><StatusBadge status={o._status}/></td>
                    <td style={{ ...S.td, fontSize:'0.72rem', color:'#777' }}>
                      {o._status==='SHIPPED' ? fmtDate(o.shippedAt) : '-'}
                    </td>
                    <td onClick={e=>e.stopPropagation()} style={S.td}>
                      {o.trackingNo
                        ? <button onClick={() => window.open(
                            `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${o.trackingNo}&displayHeader=N`,
                            'delivery','width=900,height=700,scrollbars=yes'
                          )} style={{ ...S.btn('#e3f2fd','#1565c0'), border:'1px solid #90caf9',
                            padding:'2px 8px', fontSize:'0.71rem' }}>
                            📦 조회
                          </button>
                        : <span style={{ color:'#ccc', fontSize:'0.71rem' }}>-</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* 하단 패널 */}
      {selected && (
        <DetailPanel
          order={selected}
          onClose={() => setSelected(null)}
          onNavigate={(view, params) => {
            setSelected(null);
            if (onNavigate) onNavigate(view, params);
          }}
        />
      )}
    </div>
  );
}
