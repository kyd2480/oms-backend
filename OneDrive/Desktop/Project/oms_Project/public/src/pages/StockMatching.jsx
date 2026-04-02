import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
                 'https://oms-backend-production-8a38.up.railway.app';

const S = {
  page:   { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card:   { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem',
            boxShadow:'0 1px 4px rgba(0,0,0,0.06)' },
  th:     { padding:'0.6rem 0.75rem', background:'#f5f5f5', border:'1px solid #e0e0e0',
            fontSize:'0.78rem', fontWeight:700, color:'#444', textAlign:'center', whiteSpace:'nowrap' },
  td:     { padding:'0.55rem 0.75rem', border:'1px solid #e0e0e0',
            fontSize:'0.8rem', color:'#333', textAlign:'center', whiteSpace:'nowrap' },
  bBlue:  { padding:'0.4rem 1rem', background:'#1976d2', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem', fontWeight:600 },
  bGreen: { padding:'0.4rem 1rem', background:'#2e7d32', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem', fontWeight:600 },
  bGray:  { padding:'0.4rem 1rem', background:'#e0e0e0', color:'#333', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem' },
  bRed:   { padding:'0.4rem 1rem', background:'#c62828', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem' },
  sec:    { display:'flex', alignItems:'center', gap:'0.5rem', fontWeight:700,
            fontSize:'0.95rem', color:'#1565c0' },
  dot:    { width:9, height:9, borderRadius:'50%', background:'#1565c0', flexShrink:0 },
};

const STATUS_CONFIG = {
  FULL:        { label:'완전출고 가능', color:'#2e7d32', bg:'#e8f5e9' },
  PARTIAL:     { label:'부분출고',      color:'#e65100', bg:'#fff3e0' },
  IMPOSSIBLE:  { label:'출고 불가',     color:'#c62828', bg:'#ffebee' },
  NOT_MATCHED: { label:'상품 미매칭',   color:'#9e9e9e', bg:'#f5f5f5' },
  ALLOCATED:   { label:'할당 완료',     color:'#1565c0', bg:'#e3f2fd' },
};

function Badge({ status }) {
  const cfg = STATUS_CONFIG[status] || { label:status, color:'#999', bg:'#f5f5f5' };
  return (
    <span style={{ padding:'0.2rem 0.6rem', borderRadius:'10px', fontSize:'0.72rem',
      fontWeight:700, background:cfg.bg, color:cfg.color }}>
      {cfg.label}
    </span>
  );
}

function StatCard({ label, value, color, sub }) {
  return (
    <div style={{ background:'#fff', padding:'1.1rem 1.25rem', borderRadius:'8px',
      boxShadow:'0 2px 6px rgba(0,0,0,0.07)', borderTop:`3px solid ${color}` }}>
      <div style={{ fontSize:'0.78rem', color:'#666', marginBottom:'0.25rem' }}>{label}</div>
      <div style={{ fontSize:'1.7rem', fontWeight:700, color }}>{value?.toLocaleString?.() ?? value}</div>
      {sub && <div style={{ fontSize:'0.72rem', color:'#999', marginTop:2 }}>{sub}</div>}
    </div>
  );
}

// ══ 매칭 탭 ══════════════════════════════════════════════════════
function MatchTab({ warehouse }) {
  const [result,   setResult]   = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [filter,   setFilter]   = useState('ALL');
  const [search,   setSearch]   = useState('');
  const [selected, setSelected] = useState(new Set());
  const [msg,      setMsg]      = useState('');

  const runMatch = async () => {
    if (!warehouse) { alert('창고를 먼저 선택해주세요.'); return; }
    setLoading(true); setResult(null); setSelected(new Set()); setMsg('');
    try {
      const res  = await fetch(
        `${API_BASE}/api/stock-matching/match?warehouseCode=${warehouse.code}&warehouseName=${encodeURIComponent(warehouse.name)}`
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setResult(data);
      // 초기 선택: FULL만 (완전출고 가능만 자동선택, PARTIAL 제외)
      setSelected(new Set(
        data.items.filter(i => i.shipStatus === 'FULL').map(i => i.orderNo)
      ));
    } catch(e) { alert('매칭 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const reserve = async () => {
    const targets = [...selected];
    if (!targets.length) { alert('예약할 주문을 선택해주세요.'); return; }
    if (!window.confirm(`${targets.length}건 재고를 ${warehouse.warehouseName} 창고에서 예약하시겠습니까?`)) return;
    setLoading(true);
    try {
      const res  = await fetch(`${API_BASE}/api/stock-matching/reserve`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ warehouseCode: warehouse.code, orderNos: targets }),
      });
      const data = await res.json();
      setMsg(`✅ ${data.reserved}건 재고 예약 완료${data.failed>0?` (${data.failed}건 실패)`:''}`);
      await runMatch();
    } catch(e) { alert('예약 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const toggleSelect = (orderNo, status) => {
    if (status === 'NOT_MATCHED' || status === 'IMPOSSIBLE' || status === 'ALLOCATED') return;
    setSelected(prev => { const n=new Set(prev); n.has(orderNo)?n.delete(orderNo):n.add(orderNo); return n; });
  };

  const filteredItems = result?.items?.filter(i => {
    // ALLOCATED(할당완료)는 이미 처리된 것이므로 ALL 필터에서도 제외
    if (filter === 'ALL' && i.shipStatus === 'ALLOCATED') return false;
    const matchFilter = filter === 'ALL' || i.shipStatus === filter;
    const kw = search.toLowerCase();
    return matchFilter && (!kw || [i.orderNo,i.recipientName,i.address,i.productName].some(v=>v?.toLowerCase().includes(kw)));
  }) || [];

  return (
    <>
      {msg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem', fontWeight:600, color:'#1b5e20' }}>
          {msg}<button onClick={()=>setMsg('')} style={{float:'right',background:'none',border:'none',cursor:'pointer'}}>✕</button>
        </div>
      )}

      {!result ? (
        <div style={{...S.card, textAlign:'center', padding:'3rem'}}>
          <div style={{fontSize:'2.5rem', marginBottom:'1rem'}}>🔍</div>
          <div style={{fontSize:'0.875rem', color:'#666', marginBottom:'2rem'}}>
            {warehouse ? `${warehouse.warehouseName} 창고 재고 기준으로 주문별 출고 가능 여부를 분류합니다` : '창고를 먼저 선택해주세요'}
          </div>
          <button onClick={runMatch} disabled={loading||!warehouse}
            style={{ padding:'0.75rem 2.5rem', background:warehouse?'#1565c0':'#ccc',
              color:'#fff', border:'none', borderRadius:'4px',
              cursor:warehouse?'pointer':'not-allowed', fontSize:'0.95rem', fontWeight:700 }}>
            {loading ? '⏳ 매칭 중...' : '🔍 재고 매칭 실행'}
          </button>
        </div>
      ) : (
        <>
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(150px,1fr))',
            gap:'1rem', marginBottom:'1.5rem' }}>
            <StatCard label="전체" value={result.totalItems} color="#1565c0" />
            <StatCard label="완전출고 가능" value={result.full} color="#2e7d32" sub={`선택: ${selected.size}건`} />
            <StatCard label="부분출고" value={result.partial} color="#e65100" />
            <StatCard label="출고 불가" value={result.impossible} color="#c62828" />
            <StatCard label="상품 미매칭" value={result.notMatched} color="#9e9e9e" />
          </div>

          <div style={S.card}>
            <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap', alignItems:'center', marginBottom:'1rem' }}>
              <div style={S.sec}><span style={S.dot}/>매칭 결과</div>
              <div style={{flex:1}}/>
              <input value={search} onChange={e=>setSearch(e.target.value)}
                placeholder="주문번호, 상품명, 수취인, 주소..."
                style={{ padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px', fontSize:'0.85rem', minWidth:200 }}/>
              <select value={filter} onChange={e => setFilter(e.target.value)}
                style={{ padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px', fontSize:'0.85rem', background:'#fff' }}>
                <option value="ALL">전체</option>
                <option value="FULL">완전출고 가능</option>
                <option value="PARTIAL">부분출고</option>
                <option value="IMPOSSIBLE">출고 불가</option>
                <option value="NOT_MATCHED">상품 미매칭</option>
              </select>
              {/* 전체선택: 현재 필터 목록 중 선택 가능한 것만 추가 */}
              <button onClick={() => setSelected(prev => {
                const n = new Set(prev);
                filteredItems.forEach(i => {
                  if (i.shipStatus === 'FULL' || i.shipStatus === 'PARTIAL') n.add(i.orderNo);
                });
                return n;
              })} style={S.bGray}>전체선택</button>
              {/* 전체해제: 현재 필터 목록만 해제 */}
              <button onClick={() => setSelected(prev => {
                const n = new Set(prev);
                filteredItems.forEach(i => n.delete(i.orderNo));
                return n;
              })} style={S.bGray}>선택해제</button>
              <button onClick={reserve} disabled={loading||!selected.size}
                style={{...S.bGreen, opacity:!selected.size?0.5:1}}>
                ✓ {selected.size}건 재고 예약
              </button>
              <button onClick={runMatch} disabled={loading} style={S.bGray}>🔄 재매칭</button>
            </div>

            <div style={{fontSize:'0.8rem',color:'#666',marginBottom:'0.75rem'}}>{filteredItems.length.toLocaleString()}건 표시</div>

            <div style={{overflowX:'auto'}}>
              <table style={{width:'100%',borderCollapse:'collapse',minWidth:900}}>
                <thead>
                  <tr>
                    {['선택','주문번호','쇼핑몰','수취인','주소','상품명','주문수량',`${warehouse?.name||'창고'} 재고`,'출고가능','상태'].map(h=>(
                      <th key={h} style={S.th}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredItems.map((item,idx) => {
                    const selectable = item.shipStatus==='FULL' || item.shipStatus==='PARTIAL';
                    return (
                      <tr key={`${item.orderNo}-${item.productName}`}
                        onClick={()=>selectable && toggleSelect(item.orderNo, item.shipStatus)}
                        style={{ background:selected.has(item.orderNo)?'#e8f5e9':idx%2===0?'#fff':'#fafafa',
                          cursor:selectable?'pointer':'default' }}>
                        <td style={S.td}>
                          <input type="checkbox" checked={selected.has(item.orderNo)}
                            disabled={!selectable}
                            onChange={()=>toggleSelect(item.orderNo,item.shipStatus)}
                            onClick={e=>e.stopPropagation()} />
                        </td>
                        <td style={{...S.td,fontWeight:600,color:'#1565c0'}}>{item.orderNo}</td>
                        <td style={S.td}>
                          {item.channelName
                            ? <span style={{padding:'0.15rem 0.5rem',borderRadius:'10px',background:'#e3f2fd',color:'#1565c0',fontSize:'0.73rem',fontWeight:600}}>{item.channelName}</span>
                            : <span style={{color:'#bbb'}}>-</span>}
                        </td>
                        <td style={S.td}>{item.recipientName||'-'}</td>
                        <td style={{...S.td,textAlign:'left',maxWidth:160,overflow:'hidden',textOverflow:'ellipsis'}}>{item.address||'-'}</td>
                        <td style={{...S.td,textAlign:'left',maxWidth:180,overflow:'hidden',textOverflow:'ellipsis'}}>{item.productName}</td>
                        <td style={{...S.td,fontWeight:600}}>{item.ordered}</td>
                        <td style={{...S.td,fontWeight:600,
                          color:item.warehouseStock===0?'#c62828':item.warehouseStock<item.ordered?'#e65100':'#2e7d32'}}>
                          {item.shipStatus==='NOT_MATCHED'?'-':item.warehouseStock}
                        </td>
                        <td style={{...S.td,fontWeight:600,
                          color:item.allocatable===0?'#c62828':item.allocatable<item.ordered?'#e65100':'#2e7d32'}}>
                          {item.shipStatus==='NOT_MATCHED'?'-':item.allocatable}
                        </td>
                        <td style={S.td}><Badge status={item.shipStatus}/></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  );
}

// ══ 할당완료 탭 ══════════════════════════════════════════════════
function AllocatedTab({ warehouse }) {
  const [items,   setItems]   = useState([]);
  const [loading, setLoading] = useState(false);
  const [search,  setSearch]  = useState('');
  const [msg,     setMsg]     = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const wc  = warehouse?.warehouseCode || '';
      const res = await fetch(`${API_BASE}/api/stock-matching/allocated?warehouseCode=${wc}`);
      setItems(await res.json());
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const cancelReserve = async (orderNo) => {
    if (!window.confirm(`${orderNo} 할당을 취소하시겠습니까?\n예약된 재고가 복구됩니다.`)) return;
    try {
      const res  = await fetch(`${API_BASE}/api/stock-matching/cancel-reserve/${orderNo}`, { method:'POST' });
      const data = await res.json();
      if (data.success) {
        setMsg(`✅ ${orderNo} 할당 취소 완료`);
        await load();
      }
    } catch(e) { alert('취소 실패: ' + e.message); }
  };

  const cancelAll = async () => {
    if (!window.confirm(`전체 ${items.length}건 할당을 취소하시겠습니까?`)) return;
    setLoading(true);
    let ok = 0;
    for (const item of items) {
      try {
        await fetch(`${API_BASE}/api/stock-matching/cancel-reserve/${item.orderNo}`, { method:'POST' });
        ok++;
      } catch {}
    }
    setMsg(`✅ ${ok}건 할당 취소 완료`);
    await load();
    setLoading(false);
  };

  const filtered = items.filter(i => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return [i.orderNo,i.recipientName,i.address,i.productName].some(v=>v?.toLowerCase().includes(kw));
  });

  return (
    <>
      {msg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem', fontWeight:600, color:'#1b5e20' }}>
          {msg}<button onClick={()=>setMsg('')} style={{float:'right',background:'none',border:'none',cursor:'pointer'}}>✕</button>
        </div>
      )}

      <div style={S.card}>
        <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap', alignItems:'center', marginBottom:'1rem' }}>
          <div style={S.sec}><span style={S.dot}/>재고 할당 완료 목록
            <span style={{ background:'#1565c0', color:'#fff', borderRadius:'10px',
              padding:'0 8px', fontSize:'0.72rem', fontWeight:700 }}>{items.length}</span>
          </div>
          <div style={{flex:1}}/>
          <input value={search} onChange={e=>setSearch(e.target.value)}
            placeholder="주문번호, 상품명, 수취인, 주소..."
            style={{ padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px', fontSize:'0.85rem', minWidth:200 }}/>
          {items.length > 0 && (
            <button onClick={cancelAll} disabled={loading} style={S.bRed}>
              전체 할당 취소
            </button>
          )}
          <button onClick={load} disabled={loading} style={S.bGray}>🔄 새로고침</button>
        </div>

        {loading ? (
          <div style={{textAlign:'center',padding:'3rem',color:'#999'}}>⏳ 로딩 중...</div>
        ) : filtered.length === 0 ? (
          <div style={{textAlign:'center',padding:'3rem',color:'#bbb'}}>
            <div style={{fontSize:'2rem',marginBottom:'0.5rem'}}>📭</div>
            할당 완료된 주문이 없습니다
          </div>
        ) : (
          <div style={{overflowX:'auto'}}>
            <table style={{width:'100%',borderCollapse:'collapse',minWidth:900}}>
              <thead>
                <tr>
                  {['주문번호','쇼핑몰','수취인','주소','상품명','주문수량','상태','할당취소'].map(h=>(
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((item,idx) => (
                  <tr key={`${item.orderNo}-${item.productName}`}
                    style={{background:idx%2===0?'#fff':'#f8f9ff'}}>
                    <td style={{...S.td,fontWeight:600,color:'#1565c0'}}>{item.orderNo}</td>
                    <td style={S.td}>
                      {item.channelName
                        ? <span style={{padding:'0.15rem 0.5rem',borderRadius:'10px',background:'#e3f2fd',color:'#1565c0',fontSize:'0.73rem',fontWeight:600}}>{item.channelName}</span>
                        : <span style={{color:'#bbb'}}>-</span>}
                    </td>
                    <td style={S.td}>{item.recipientName||'-'}</td>
                    <td style={{...S.td,textAlign:'left',maxWidth:160,overflow:'hidden',textOverflow:'ellipsis'}}>{item.address||'-'}</td>
                    <td style={{...S.td,textAlign:'left',maxWidth:200,overflow:'hidden',textOverflow:'ellipsis'}}>{item.productName}</td>
                    <td style={{...S.td,fontWeight:600}}>{item.ordered}</td>
                    <td style={S.td}><Badge status="ALLOCATED"/></td>
                    <td style={S.td}>
                      <button onClick={()=>cancelReserve(item.orderNo)}
                        style={{...S.bRed,padding:'0.2rem 0.6rem',fontSize:'0.75rem'}}>
                        취소
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

// ══ 메인 ════════════════════════════════════════════════════════
export default function StockMatching() {
  const [tab,        setTab]        = useState('match');
  const [warehouses, setWarehouses] = useState([]);
  const [warehouse,  setWarehouse]  = useState(null);

  useEffect(() => {
    fetch(`${API_BASE}/api/warehouses`)
      .then(r => r.json())
      .then(data => {
        const list = Array.isArray(data) ? data : (data.content ?? []);
        setWarehouses(list);

        // 1순위: localStorage 복원
        const saved = localStorage.getItem('stockMatch_warehouseId');
        if (saved) {
          const found = list.find(w => String(w.warehouseId) === saved);
          if (found) { setWarehouse(found); return; }
        }

        // 2순위: 기본값 — 본사(안양)
        const defaultWh = list.find(w =>
          w.code?.toUpperCase() === 'ANYANG' ||
          w.name?.includes('안양')
        );
        if (defaultWh) {
          setWarehouse(defaultWh);
          localStorage.setItem('stockMatch_warehouseId', String(defaultWh.warehouseId));
        }
      })
      .catch(() => {});
  }, []);

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:'1rem' }}>
          <div>
            <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>재고 매칭</h1>
            <div style={{ fontSize:'0.875rem', color:'#999' }}>할당된 창고의 재고 기준으로 주문별 출고 가능 여부를 분류합니다</div>
          </div>
        </div>

        {/* 탭 */}
        <div style={{ display:'flex', borderBottom:'2px solid #e0e0e0', marginBottom:0 }}>
          {[
            { key:'match',     label:'매칭',       color:'#1565c0' },
            { key:'allocated', label:'할당 완료',  color:'#2e7d32' },
          ].map(t => (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{ padding:'0.65rem 1.5rem', border:'none', background:'transparent',
                cursor:'pointer', fontSize:'0.9rem', fontWeight: tab===t.key ? 700 : 400,
                color: tab===t.key ? t.color : '#888',
                borderBottom: tab===t.key ? `2px solid ${t.color}` : '2px solid transparent',
                marginBottom:'-2px' }}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* 창고 선택 */}
      <div style={{ padding:'0.6rem 1.25rem', background:'#e8f0fe', border:'1px solid #c5cae9',
        borderRadius:'6px', marginBottom:'1rem', display:'flex', alignItems:'center', gap:'1rem',
        flexWrap:'wrap' }}>
        <span style={{ color:'#1a3a6b', fontWeight:700, fontSize:'0.875rem' }}>🏭 창고 선택</span>
        <select
          value={warehouse ? String(warehouse.warehouseId) : ''}
          onChange={e => {
            const found = warehouses.find(w => String(w.warehouseId) === e.target.value) || null;
            setWarehouse(found);
            if (found) localStorage.setItem('stockMatch_warehouseId', String(found.warehouseId));
            else localStorage.removeItem('stockMatch_warehouseId');
          }}
          style={{ padding:'0.35rem 0.6rem', border:'1px solid #c5cae9', borderRadius:'4px',
            fontSize:'0.875rem', minWidth:200,
            background: warehouse ? '#e8f5e9' : '#fff3e0',
            color: warehouse ? '#1b5e20' : '#e65100', fontWeight:600 }}>
          <option value="">-- 창고 선택 --</option>
          {warehouses.filter(w => w.isActive !== false).map(w => (
            <option key={w.warehouseId} value={String(w.warehouseId)}>
              {w.name}{w.code ? ` (${w.code})` : ''}
            </option>
          ))}
        </select>
        {warehouse
          ? <span style={{ fontSize:'0.82rem', color:'#1b5e20', fontWeight:600 }}>
              ✅ {warehouse.name} ({warehouse.code}) 재고 기준으로 매칭합니다
            </span>
          : <span style={{ fontSize:'0.82rem', color:'#e65100', fontWeight:600 }}>
              ⚠ 창고를 선택해야 매칭을 실행할 수 있습니다
            </span>}
      </div>

      {/* 탭 컨텐츠 */}
      {tab === 'match'     && <MatchTab     warehouse={warehouse} />}
      {tab === 'allocated' && <AllocatedTab warehouse={warehouse} />}
    </div>
  );
}
