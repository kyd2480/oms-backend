import { useState, useEffect, useRef } from 'react';

const API = (import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
             'https://oms-backend-production-8a38.up.railway.app');

const S = {
  page:  { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card:  { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem',
           boxShadow:'0 1px 4px rgba(0,0,0,0.06)' },
  th:    { padding:'0.6rem 0.75rem', background:'#f5f5f5', border:'1px solid #e0e0e0',
           fontSize:'0.78rem', fontWeight:700, color:'#444', textAlign:'center', whiteSpace:'nowrap' },
  td:    { padding:'0.55rem 0.75rem', border:'1px solid #e0e0e0',
           fontSize:'0.8rem', color:'#333', textAlign:'center' },
  inp:   { padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px',
           fontSize:'0.85rem', outline:'none', width:'100%', boxSizing:'border-box' },
  bBlue: { padding:'0.4rem 1rem', background:'#1976d2', color:'#fff', border:'none',
           borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem', fontWeight:600 },
  bGreen:{ padding:'0.4rem 1rem', background:'#2e7d32', color:'#fff', border:'none',
           borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem', fontWeight:600 },
  bGray: { padding:'0.4rem 1rem', background:'#e0e0e0', color:'#333', border:'none',
           borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem' },
  bRed:  { padding:'0.4rem 1rem', background:'#c62828', color:'#fff', border:'none',
           borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem' },
  sec:   { display:'flex', alignItems:'center', gap:'0.5rem', fontWeight:700,
           fontSize:'0.95rem', color:'#1565c0' },
  dot:   { width:9, height:9, borderRadius:'50%', background:'#1565c0', flexShrink:0 },
};

function Badge({ text, color }) {
  return (
    <span style={{ padding:'0.18rem 0.5rem', borderRadius:'10px', fontSize:'0.72rem',
      fontWeight:700, background:color+'18', color, border:`1px solid ${color}30` }}>
      {text}
    </span>
  );
}

// ─── 상품 검색 드롭다운 ────────────────────────────────────────
function ProductSearchInput({ value, onChange, onSelect }) {
  const [results, setResults] = useState([]);
  const [open,    setOpen]    = useState(false);
  const [timer,   setTimer]   = useState(null);
  const ref = useRef();

  const search = (kw) => {
    onChange(kw);
    clearTimeout(timer);
    if (!kw || kw.length < 2) { setResults([]); setOpen(false); return; }
    setTimer(setTimeout(async () => {
      try {
        const res  = await fetch(`${API}/api/matching/search?keyword=${encodeURIComponent(kw)}`);
        const data = await res.json();
        setResults(data);
        setOpen(data.length > 0);
      } catch { setResults([]); }
    }, 300));
  };

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} style={{ position:'relative', flex:1 }}>
      <input value={value} onChange={e => search(e.target.value)}
        placeholder="상품명, SKU, 바코드 검색..."
        style={S.inp} />
      {open && (
        <div style={{ position:'absolute', top:'100%', left:0, right:0, zIndex:100,
          background:'#fff', border:'1px solid #ccc', borderRadius:'4px',
          boxShadow:'0 4px 12px rgba(0,0,0,0.12)', maxHeight:240, overflowY:'auto' }}>
          {results.map(p => (
            <div key={p.productId} onClick={() => { onSelect(p); setOpen(false); onChange(p.productName); }}
              style={{ padding:'0.6rem 0.75rem', cursor:'pointer', borderBottom:'1px solid #f0f0f0',
                fontSize:'0.82rem' }}
              onMouseEnter={e => e.currentTarget.style.background='#f5f5f5'}
              onMouseLeave={e => e.currentTarget.style.background='#fff'}>
              <div style={{ fontWeight:600, color:'#111' }}>{p.productName}</div>
              <div style={{ fontSize:'0.75rem', color:'#888', marginTop:2 }}>
                SKU: {p.sku} | 재고: <span style={{
                  color: p.availableStock > 0 ? '#2e7d32' : '#c62828', fontWeight:600
                }}>{p.availableStock}</span>개
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// 메인
// ═══════════════════════════════════════════════════════════════
export default function NameMatching() {
  const [tab,       setTab]       = useState('unmatched'); // unmatched | rules
  const [items,     setItems]     = useState([]);
  const [rules,     setRules]     = useState([]);
  const [loading,   setLoading]   = useState(false);
  const [autoMsg,   setAutoMsg]   = useState('');
  const [search,    setSearch]    = useState('');

  // 각 row의 선택된 상품
  const [selections, setSelections] = useState({}); // itemId → { productId, productName, sku }
  const [searchKws,  setSearchKws]  = useState({}); // itemId → 검색어

  const loadUnmatched = async () => {
    setLoading(true);
    try {
      const res  = await fetch(`${API}/api/matching/unmatched`);
      const data = await res.json();
      setItems(data);
      // AUTO_SUGGESTED 항목 자동 선택
      const sel = {};
      const kws = {};
      data.forEach(item => {
        if (item.matchStatus === 'AUTO_SUGGESTED') {
          sel[item.itemId] = {
            productId:   item.suggestedProductId,
            productName: item.suggestedProductName,
            sku:         item.suggestedSku,
          };
          kws[item.itemId] = item.suggestedProductName;
        }
      });
      setSelections(sel);
      setSearchKws(kws);
    } catch(e) { alert('조회 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const loadRules = async () => {
    try {
      const res  = await fetch(`${API}/api/matching/rules`);
      setRules(await res.json());
    } catch(e) { console.error(e); }
  };

  useEffect(() => {
    loadUnmatched();
    loadRules();
  }, []);

  // ── 자동매칭 ────────────────────────────────────────────────
  const runAutoMatch = async () => {
    setLoading(true); setAutoMsg('');
    try {
      const res  = await fetch(`${API}/api/matching/auto`, { method:'POST' });
      const data = await res.json();
      setAutoMsg(`✅ ${data.matched}건 자동 매칭 완료, ${data.skipped}건 수동 필요`);
      await loadUnmatched();
      await loadRules();
    } catch(e) { setAutoMsg('자동매칭 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  // ── 단일 확정 ───────────────────────────────────────────────
  const confirmMatch = async (item) => {
    const sel = selections[item.itemId];
    if (!sel) { alert('매칭할 상품을 선택해주세요.'); return; }
    try {
      const res = await fetch(`${API}/api/matching/match`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({
          itemId:            item.itemId,
          productId:         sel.productId,
          channelProductName: item.channelProductName,
        }),
      });
      const data = await res.json();
      if (data.success) {
        setItems(prev => prev.filter(i => i.itemId !== item.itemId));
        await loadRules();
      }
    } catch(e) { alert('확정 실패: ' + e.message); }
  };

  // ── 전체 선택된 것 일괄 확정 ─────────────────────────────────
  const confirmAll = async () => {
    const toConfirm = items.filter(i => selections[i.itemId]);
    if (!toConfirm.length) { alert('선택된 매칭이 없습니다.'); return; }
    if (!window.confirm(`${toConfirm.length}건을 일괄 확정하시겠습니까?`)) return;

    setLoading(true);
    let ok = 0;
    for (const item of toConfirm) {
      const sel = selections[item.itemId];
      try {
        await fetch(`${API}/api/matching/match`, {
          method:'POST', headers:{'Content-Type':'application/json'},
          body: JSON.stringify({
            itemId:            item.itemId,
            productId:         sel.productId,
            channelProductName: item.channelProductName,
          }),
        });
        ok++;
      } catch {}
    }
    setAutoMsg(`✅ ${ok}건 일괄 확정 완료`);
    await loadUnmatched();
    await loadRules();
    setLoading(false);
  };

  // ── 룰 삭제 ─────────────────────────────────────────────────
  const deleteRule = async (ruleId) => {
    if (!window.confirm('이 매칭 룰을 삭제하시겠습니까?')) return;
    await fetch(`${API}/api/matching/rules/${ruleId}`, { method:'DELETE' });
    await loadRules();
  };

  const filteredItems = items.filter(i => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return [i.orderNo, i.channelProductName, i.recipientName]
      .some(v => v?.toLowerCase().includes(kw));
  });

  const suggestedCount = items.filter(i => i.matchStatus === 'AUTO_SUGGESTED').length;
  const selectedCount  = Object.keys(selections).length;

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <div>
            <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>
              상품명 매칭
            </h1>
            <div style={{ fontSize:'0.875rem', color:'#999' }}>
              주문 상품명을 재고 DB 상품과 연결합니다
            </div>
          </div>
          <div style={{ display:'flex', gap:'0.5rem' }}>
            <button onClick={() => { setTab('unmatched'); }}
              style={{ ...S.bGray,
                background: tab==='unmatched' ? '#1976d2' : '#e0e0e0',
                color: tab==='unmatched' ? '#fff' : '#333' }}>
              미매칭 목록
              {items.length > 0 && (
                <span style={{ marginLeft:6, background:'#fff', color:'#1976d2',
                  borderRadius:'10px', padding:'0 6px', fontSize:'0.72rem', fontWeight:700 }}>
                  {items.length}
                </span>
              )}
            </button>
            <button onClick={() => { setTab('rules'); loadRules(); }}
              style={{ ...S.bGray,
                background: tab==='rules' ? '#2e7d32' : '#e0e0e0',
                color: tab==='rules' ? '#fff' : '#333' }}>
              매칭 룰
              {rules.length > 0 && (
                <span style={{ marginLeft:6, background:'#fff', color:'#2e7d32',
                  borderRadius:'10px', padding:'0 6px', fontSize:'0.72rem', fontWeight:700 }}>
                  {rules.length}
                </span>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* 알림 */}
      {autoMsg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem',
          fontWeight:600, color:'#1b5e20' }}>
          {autoMsg}
          <button onClick={()=>setAutoMsg('')}
            style={{float:'right',background:'none',border:'none',cursor:'pointer',color:'#666'}}>✕</button>
        </div>
      )}

      {/* ── 미매칭 목록 탭 ── */}
      {tab === 'unmatched' && (
        <>
          {/* 통계 + 액션 */}
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(160px,1fr))',
            gap:'1rem', marginBottom:'1.5rem' }}>
            {[
              { label:'미매칭 건수',  value:items.length,     color:'#c62828' },
              { label:'자동추천됨',   value:suggestedCount,   color:'#e65100' },
              { label:'선택 완료',    value:selectedCount,    color:'#1976d2' },
              { label:'저장된 룰',    value:rules.length,     color:'#2e7d32' },
            ].map(s => (
              <div key={s.label} style={{ background:'#fff', padding:'1.1rem 1.25rem',
                borderRadius:'8px', boxShadow:'0 2px 6px rgba(0,0,0,0.07)',
                borderTop:`3px solid ${s.color}` }}>
                <div style={{ fontSize:'0.78rem', color:'#666', marginBottom:'0.25rem' }}>{s.label}</div>
                <div style={{ fontSize:'1.7rem', fontWeight:700, color:s.color }}>{s.value}</div>
              </div>
            ))}
          </div>

          <div style={S.card}>
            {/* 툴바 */}
            <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap',
              alignItems:'center', marginBottom:'1rem' }}>
              <div style={S.sec}><span style={S.dot}/>미매칭 주문 상품</div>
              <div style={{flex:1}}/>
              <input value={search} onChange={e=>setSearch(e.target.value)}
                placeholder="주문번호, 상품명, 수취인 검색..."
                style={{ ...S.inp, maxWidth:240 }} />
              <button onClick={runAutoMatch} disabled={loading} style={S.bBlue}>
                🤖 자동매칭
              </button>
              <button onClick={confirmAll} disabled={loading || !selectedCount}
                style={{ ...S.bGreen, opacity:!selectedCount?0.5:1 }}>
                ✓ {selectedCount}건 일괄 확정
              </button>
              <button onClick={loadUnmatched} disabled={loading} style={S.bGray}>
                🔄 새로고침
              </button>
            </div>

            {loading ? (
              <div style={{textAlign:'center',padding:'3rem',color:'#999'}}>⏳ 로딩 중...</div>
            ) : filteredItems.length === 0 ? (
              <div style={{textAlign:'center',padding:'3rem',color:'#bbb'}}>
                <div style={{fontSize:'2.5rem',marginBottom:'0.75rem'}}>✅</div>
                <div style={{fontWeight:700,color:'#2e7d32',fontSize:'1.05rem'}}>
                  {items.length === 0 ? '미매칭 주문이 없습니다' : '검색 결과 없음'}
                </div>
              </div>
            ) : (
              <div style={{overflowX:'auto'}}>
                <table style={{width:'100%',borderCollapse:'collapse',minWidth:900}}>
                  <thead>
                    <tr>
                      {['주문번호','수취인','쇼핑몰','주문 상품명','수량','재고 상품 검색','상태','확정'].map(h=>(
                        <th key={h} style={S.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredItems.map((item, idx) => (
                      <tr key={item.itemId}
                        style={{ background: selections[item.itemId]
                          ? '#f1f8e9' : idx%2===0 ? '#fff' : '#fafafa' }}>
                        <td style={{...S.td,fontWeight:600,color:'#1565c0',whiteSpace:'nowrap'}}>
                          {item.orderNo}
                        </td>
                        <td style={{...S.td,whiteSpace:'nowrap'}}>{item.recipientName||'-'}</td>
                        <td style={S.td}>
                          {item.channelName
                            ? <Badge text={item.channelName} color="#1565c0" />
                            : <span style={{color:'#bbb'}}>-</span>}
                        </td>
                        <td style={{...S.td,textAlign:'left',maxWidth:200,
                          overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>
                          <span title={item.channelProductName}>{item.channelProductName}</span>
                        </td>
                        <td style={S.td}>{item.quantity}</td>
                        <td style={{...S.td,minWidth:260,padding:'0.4rem 0.5rem'}}>
                          <ProductSearchInput
                            value={searchKws[item.itemId] || ''}
                            onChange={kw => setSearchKws(prev => ({...prev,[item.itemId]:kw}))}
                            onSelect={p => setSelections(prev => ({...prev,[item.itemId]:{
                              productId:p.productId, productName:p.productName, sku:p.sku
                            }}))}
                          />
                          {selections[item.itemId] && (
                            <div style={{fontSize:'0.72rem',color:'#2e7d32',marginTop:3}}>
                              ✓ {selections[item.itemId].productName}
                              {selections[item.itemId].sku &&
                                ` (${selections[item.itemId].sku})`}
                            </div>
                          )}
                        </td>
                        <td style={S.td}>
                          <Badge
                            text={item.matchStatus==='AUTO_SUGGESTED'?'자동추천':'미매칭'}
                            color={item.matchStatus==='AUTO_SUGGESTED'?'#e65100':'#c62828'}
                          />
                        </td>
                        <td style={S.td}>
                          <button
                            onClick={() => confirmMatch(item)}
                            disabled={!selections[item.itemId]}
                            style={{ ...S.bGreen, padding:'0.25rem 0.7rem',
                              fontSize:'0.75rem', opacity:!selections[item.itemId]?0.4:1 }}>
                            확정
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
      )}

      {/* ── 매칭 룰 탭 ── */}
      {tab === 'rules' && (
        <div style={S.card}>
          <div style={{...S.sec,marginBottom:'1rem'}}>
            <span style={S.dot}/>저장된 매칭 룰
            <Badge text={`${rules.length}개`} color="#2e7d32" />
          </div>

          {rules.length === 0 ? (
            <div style={{textAlign:'center',padding:'3rem',color:'#bbb'}}>
              저장된 룰이 없습니다
            </div>
          ) : (
            <div style={{overflowX:'auto'}}>
              <table style={{width:'100%',borderCollapse:'collapse'}}>
                <thead>
                  <tr>
                    {['#','쇼핑몰 상품명','→ 재고 상품명','SKU','방식','등록일','삭제'].map(h=>(
                      <th key={h} style={S.th}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rules.map((rule,idx) => (
                    <tr key={rule.ruleId}
                      style={{background:idx%2===0?'#fff':'#fafafa'}}>
                      <td style={{...S.td,color:'#999',fontSize:'0.75rem'}}>{idx+1}</td>
                      <td style={{...S.td,textAlign:'left',maxWidth:200,
                        overflow:'hidden',textOverflow:'ellipsis'}}>
                        {rule.channelProductName}
                      </td>
                      <td style={{...S.td,textAlign:'left',fontWeight:600,color:'#1565c0'}}>
                        {rule.productName}
                      </td>
                      <td style={{...S.td,fontSize:'0.75rem',color:'#666'}}>{rule.sku||'-'}</td>
                      <td style={S.td}>
                        <Badge
                          text={rule.matchType==='AUTO'?'자동':'수동'}
                          color={rule.matchType==='AUTO'?'#1976d2':'#2e7d32'}
                        />
                      </td>
                      <td style={{...S.td,fontSize:'0.75rem',color:'#999'}}>
                        {rule.createdAt ? new Date(rule.createdAt).toLocaleDateString('ko-KR') : '-'}
                      </td>
                      <td style={S.td}>
                        <button onClick={()=>deleteRule(rule.ruleId)}
                          style={{...S.bRed,padding:'0.2rem 0.6rem',fontSize:'0.75rem'}}>
                          삭제
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
