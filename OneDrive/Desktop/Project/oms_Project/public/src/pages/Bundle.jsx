import { useState, useEffect } from 'react';

const API = (import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
             'https://oms-backend-production-8a38.up.railway.app') + '/api/bundle';

const S = {
  page:   { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card:   { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem',
            boxShadow:'0 1px 4px rgba(0,0,0,0.06)' },
  tbl:    { width:'100%', borderCollapse:'collapse' },
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
  bBig:   { padding:'0.7rem 2.5rem', background:'#1565c0', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.95rem', fontWeight:700 },
  sec:    { display:'flex', alignItems:'center', gap:'0.5rem', fontWeight:700,
            fontSize:'0.95rem', color:'#1565c0' },
  dot:    { width:9, height:9, borderRadius:'50%', background:'#1565c0', flexShrink:0 },
  tab:    { padding:'0.6rem 1.5rem', border:'none', background:'transparent',
            cursor:'pointer', fontSize:'0.9rem', borderBottom:'2px solid transparent' },
};

function Badge({ text, color }) {
  return (
    <span style={{ padding:'0.15rem 0.5rem', borderRadius:'10px', fontSize:'0.72rem',
      fontWeight:700, background:color+'18', color, border:`1px solid ${color}30` }}>
      {text}
    </span>
  );
}

function StatCard({ label, value, color, sub }) {
  return (
    <div style={{ background:'#fff', padding:'1.1rem 1.25rem', borderRadius:'8px',
      boxShadow:'0 2px 6px rgba(0,0,0,0.07)', borderTop:`3px solid ${color}` }}>
      <div style={{ fontSize:'0.78rem', color:'#666', marginBottom:'0.25rem' }}>{label}</div>
      <div style={{ fontSize:'1.7rem', fontWeight:700, color }}>{typeof value==='number'?value.toLocaleString():value}</div>
      {sub && <div style={{ fontSize:'0.72rem', color:'#999', marginTop:2 }}>{sub}</div>}
    </div>
  );
}

export default function Bundle() {
  const [view,       setView]       = useState('detect');   // detect | confirmed
  const [detecting,  setDetecting]  = useState(false);
  const [result,     setResult]     = useState(null);       // detect 결과
  const [confirmed,  setConfirmed]  = useState([]);         // 확정 목록
  const [loading,    setLoading]    = useState(false);
  const [expanded,   setExpanded]   = useState(new Set());
  const [search,     setSearch]     = useState('');
  const [msg,        setMsg]        = useState('');

  // ── 탐지 실행 ─────────────────────────────────────────────────
  const detect = async () => {
    setDetecting(true); setResult(null); setMsg('');
    try {
      const res  = await fetch(`${API}/detect`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setResult(data);
      // 기본으로 5그룹 이하면 전체 펼침
      if (data.candidates?.length <= 5)
        setExpanded(new Set(data.candidates.map(c => c.bundleKey)));
    } catch(e) { alert('탐지 실패: ' + e.message); }
    finally { setDetecting(false); }
  };

  // ── 확정 목록 로드 ────────────────────────────────────────────
  const loadConfirmed = async () => {
    setLoading(true);
    try {
      const res  = await fetch(`${API}/list`);
      const data = await res.json();
      setConfirmed(data);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  useEffect(() => { if (view === 'confirmed') loadConfirmed(); }, [view]);

  // ── 단일 묶음 확정 ────────────────────────────────────────────
  const confirmBundle = async (candidate) => {
    try {
      const res = await fetch(`${API}/confirm`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({
          bundleKey: candidate.bundleKey,
          orderNos:  candidate.orders.map(o => o.orderNo),
        }),
      });
      const data = await res.json();
      if (data.success) {
        setMsg(`✅ ${candidate.recipientName} 묶음 확정 완료 (대표: ${data.representativeOrderNo}, ${data.cancelled}건 취소)`);
        // 해당 후보 alreadyBundled 표시
        setResult(prev => ({
          ...prev,
          candidates: prev.candidates.map(c =>
            c.bundleKey === candidate.bundleKey ? {...c, alreadyBundled:true} : c
          )
        }));
      }
    } catch(e) { alert('확정 실패: ' + e.message); }
  };

  // ── 전체 묶음 일괄 확정 ───────────────────────────────────────
  const confirmAll = async () => {
    const newCount = result?.candidates?.filter(c => !c.alreadyBundled).length || 0;
    if (!newCount) { alert('확정할 묶음이 없습니다.'); return; }
    if (!window.confirm(`${newCount}그룹을 일괄 묶음 확정하시겠습니까?`)) return;
    setLoading(true);
    try {
      const res  = await fetch(`${API}/confirm-all`, { method:'POST' });
      const data = await res.json();
      setMsg(`✅ ${data.confirmed}그룹 일괄 묶음 확정 완료`);
      await detect();
    } catch(e) { alert('실패: ' + e.message); }
    finally { setLoading(false); }
  };

  // ── 묶음 해제 ─────────────────────────────────────────────────
  const release = async (bundleId, name) => {
    if (!window.confirm(`'${name}' 묶음을 해제하시겠습니까?`)) return;
    try {
      await fetch(`${API}/release/${bundleId}`, { method:'POST' });
      setMsg(`✅ 묶음 해제 완료`);
      loadConfirmed();
    } catch(e) { alert('해제 실패: ' + e.message); }
  };

  const toggleExpand = (k) => setExpanded(prev => {
    const n = new Set(prev); n.has(k) ? n.delete(k) : n.add(k); return n;
  });

  // ── 필터 ──────────────────────────────────────────────────────
  const filteredCandidates = result?.candidates?.filter(c => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return [c.recipientName, c.recipientPhone, c.address]
      .some(v => v?.toLowerCase().includes(kw));
  }) || [];

  const filteredConfirmed = confirmed.filter(b => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return [b.recipientName, b.recipientPhone, b.address]
      .some(v => v?.toLowerCase().includes(kw));
  });

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:0 }}>묶음 정리</h1>
          <div style={{ display:'flex', gap:'0.5rem' }}>
            <button onClick={() => { setView('detect'); setSearch(''); }}
              style={{ ...S.tab, borderBottomColor: view==='detect'?'#1976d2':'transparent',
                color: view==='detect'?'#1976d2':'#666', fontWeight: view==='detect'?700:400 }}>
              묶음 탐지
              {result && <span style={{ marginLeft:6, background:'#1976d2', color:'#fff',
                borderRadius:'10px', padding:'0 6px', fontSize:'0.72rem' }}>
                {result.bundleCandidates}
              </span>}
            </button>
            <button onClick={() => { setView('confirmed'); setSearch(''); }}
              style={{ ...S.tab, borderBottomColor: view==='confirmed'?'#2e7d32':'transparent',
                color: view==='confirmed'?'#2e7d32':'#666', fontWeight: view==='confirmed'?700:400 }}>
              확정된 묶음
              {confirmed.length > 0 && <span style={{ marginLeft:6, background:'#2e7d32', color:'#fff',
                borderRadius:'10px', padding:'0 6px', fontSize:'0.72rem' }}>
                {confirmed.length}
              </span>}
            </button>
          </div>
        </div>
      </div>

      {/* 알림 메시지 */}
      {msg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem', color:'#1b5e20', fontWeight:600 }}>
          {msg} <button onClick={()=>setMsg('')}
            style={{float:'right', background:'none', border:'none', cursor:'pointer', color:'#666'}}>✕</button>
        </div>
      )}

      {/* ══ 탐지 뷰 ══════════════════════════════════════════════ */}
      {view === 'detect' && (
        <>
          {/* 실행 버튼 */}
          {!result && (
            <div style={{...S.card, textAlign:'center', padding:'3rem'}}>
              <div style={{ fontSize:'2.5rem', marginBottom:'1rem' }}>📦</div>
              <div style={{ fontSize:'1rem', color:'#555', marginBottom:'0.5rem' }}>
                수취인 + 연락처 + 주소가 동일한 주문을 묶음 그룹으로 탐지합니다
              </div>
              <div style={{ fontSize:'0.875rem', color:'#999', marginBottom:'2rem' }}>
                묶음 확정 시 최신 주문 1건을 대표로 유지하고, 나머지는 자동 취소됩니다
              </div>
              <button onClick={detect} disabled={detecting} style={S.bBig}>
                {detecting ? '⏳ 탐지 중...' : '🔍 묶음 탐지 실행'}
              </button>
            </div>
          )}

          {/* 탐지 결과 */}
          {result && (
            <>
              {/* 통계 */}
              <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(150px,1fr))',
                gap:'1rem', marginBottom:'1.5rem' }}>
                <StatCard label="전체 주문" value={result.totalOrders} color="#1565c0" />
                <StatCard label="묶음 가능 그룹" value={result.bundleCandidates} color="#e65100"
                  sub={result.bundleCandidates===0?'묶음 없음':undefined} />
                <StatCard label="묶음 대상 주문" value={result.bundleableOrders} color="#6a1b9a" />
                <StatCard label="미확정 그룹"
                  value={result.candidates?.filter(c=>!c.alreadyBundled).length||0}
                  color="#c62828" />
              </div>

              {result.bundleCandidates === 0 ? (
                <div style={{...S.card, textAlign:'center', padding:'3rem'}}>
                  <div style={{fontSize:'2.5rem', marginBottom:'0.75rem'}}>✅</div>
                  <div style={{fontWeight:700, color:'#2e7d32', fontSize:'1.05rem'}}>묶음 가능한 주문이 없습니다</div>
                  <button onClick={detect} style={{...S.bGray, marginTop:'1.5rem'}}>다시 탐지</button>
                </div>
              ) : (
                <div style={S.card}>
                  {/* 툴바 */}
                  <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap',
                    alignItems:'center', marginBottom:'1rem' }}>
                    <div style={{...S.sec}}>
                      <span style={S.dot}/>묶음 후보 그룹
                      <Badge text={`${result.bundleCandidates}그룹`} color="#e65100" />
                    </div>
                    <div style={{flex:1}}/>
                    <input value={search} onChange={e=>setSearch(e.target.value)}
                      placeholder="수취인, 연락처, 주소 검색..."
                      style={{ padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px',
                        fontSize:'0.85rem', minWidth:200 }} />
                    <button onClick={confirmAll} disabled={loading} style={S.bGreen}>
                      ✓ 전체 일괄 확정
                    </button>
                    <button onClick={detect} disabled={detecting} style={S.bGray}>
                      🔄 재탐지
                    </button>
                    <button onClick={()=> expanded.size>0
                      ? setExpanded(new Set())
                      : setExpanded(new Set(filteredCandidates.map(c=>c.bundleKey)))}
                      style={S.bGray}>
                      {expanded.size>0?'전체 접기':'전체 펼치기'}
                    </button>
                  </div>

                  <div style={{fontSize:'0.8rem', color:'#666', marginBottom:'0.75rem'}}>
                    {search ? `검색결과 ${filteredCandidates.length}그룹` : `총 ${result.bundleCandidates}그룹`}
                  </div>

                  {/* 그룹 목록 */}
                  {filteredCandidates.map((c, gi) => {
                    const isExp = expanded.has(c.bundleKey);
                    return (
                      <div key={c.bundleKey} style={{ marginBottom:'0.6rem',
                        border:`1px solid ${c.alreadyBundled?'#c8e6c9':'#ffe0b2'}`,
                        borderRadius:'6px', overflow:'hidden' }}>
                        {/* 그룹 헤더 */}
                        <div style={{ background: c.alreadyBundled?'#f1f8e9':'#fff3e0',
                          padding:'0.65rem 1rem', display:'flex',
                          justifyContent:'space-between', alignItems:'center',
                          cursor:'pointer' }} onClick={()=>toggleExpand(c.bundleKey)}>
                          <div style={{display:'flex', alignItems:'center', gap:'0.6rem'}}>
                            <span style={{fontSize:'0.75rem', color:'#999'}}>#{gi+1}</span>
                            <Badge text={`${c.orderCount}건`} color="#e65100" />
                            {c.alreadyBundled && <Badge text="확정됨" color="#2e7d32" />}
                            <span style={{fontWeight:600, fontSize:'0.875rem', color:'#222'}}>
                              {c.recipientName}
                            </span>
                            <span style={{fontSize:'0.8rem', color:'#666'}}>{c.recipientPhone}</span>
                            <span style={{fontSize:'0.78rem', color:'#999',
                              maxWidth:200, overflow:'hidden', textOverflow:'ellipsis',
                              whiteSpace:'nowrap'}}>
                              {c.address}
                            </span>
                          </div>
                          <div style={{display:'flex', gap:'0.5rem', alignItems:'center'}}>
                            {!c.alreadyBundled && (
                              <button onClick={e=>{e.stopPropagation(); confirmBundle(c);}}
                                style={{...S.bGreen, padding:'0.2rem 0.7rem', fontSize:'0.75rem'}}>
                                묶음 확정
                              </button>
                            )}
                            <span style={{fontSize:'0.8rem', color:'#999'}}>{isExp?'▲':'▼'}</span>
                          </div>
                        </div>

                        {/* 주문 상세 */}
                        {isExp && (
                          <div style={{overflowX:'auto'}}>
                            <table style={{...S.tbl, minWidth:600}}>
                              <thead>
                                <tr>
                                  {['주문번호','쇼핑몰','상품명','주문일시','금액'].map(h=>(
                                    <th key={h} style={S.th}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {c.orders.map((o, idx) => (
                                  <tr key={o.orderNo}
                                    style={{background:idx%2===0?'#fff':'#fafafa'}}>
                                    <td style={{...S.td, fontWeight:600, color:'#1565c0'}}>
                                      {idx===0 && (
                                        <span style={{fontSize:'0.68rem', color:'#2e7d32',
                                          display:'block'}}>대표</span>
                                      )}
                                      {o.orderNo}
                                    </td>
                                    <td style={S.td}>
                                      <Badge text={o.channelName||'-'} color="#1565c0" />
                                    </td>
                                    <td style={{...S.td, textAlign:'left', maxWidth:220,
                                      overflow:'hidden', textOverflow:'ellipsis'}}>
                                      {o.productNames?.join(', ') || '-'}
                                    </td>
                                    <td style={{...S.td, fontSize:'0.75rem', color:'#999'}}>
                                      {o.orderedAt ? new Date(o.orderedAt).toLocaleString('ko-KR',
                                        {month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}) : '-'}
                                    </td>
                                    <td style={{...S.td, textAlign:'right'}}>
                                      {o.totalAmount ? Number(o.totalAmount).toLocaleString()+'원' : '-'}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </>
      )}

      {/* ══ 확정된 묶음 뷰 ═══════════════════════════════════════ */}
      {view === 'confirmed' && (
        <div style={S.card}>
          <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap',
            alignItems:'center', marginBottom:'1rem' }}>
            <div style={S.sec}><span style={S.dot}/>확정된 묶음 목록
              <Badge text={`${confirmed.length}건`} color="#2e7d32" />
            </div>
            <div style={{flex:1}}/>
            <input value={search} onChange={e=>setSearch(e.target.value)}
              placeholder="수취인, 연락처, 주소 검색..."
              style={{ padding:'0.4rem 0.6rem', border:'1px solid #ccc',
                borderRadius:'4px', fontSize:'0.85rem', minWidth:200 }} />
            <button onClick={loadConfirmed} style={S.bGray}>🔄 새로고침</button>
          </div>

          {loading ? (
            <div style={{textAlign:'center', padding:'3rem', color:'#999'}}>⏳ 로딩 중...</div>
          ) : filteredConfirmed.length === 0 ? (
            <div style={{textAlign:'center', padding:'3rem', color:'#bbb'}}>
              <div style={{fontSize:'2rem', marginBottom:'0.5rem'}}>📭</div>
              확정된 묶음이 없습니다
            </div>
          ) : (
            <table style={S.tbl}>
              <thead>
                <tr>
                  {['#','수취인','연락처','주소','주문 수','대표주문번호','상태','확정일시','해제'].map(h=>(
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filteredConfirmed.map((b, idx) => (
                  <tr key={b.bundleId} style={{background:idx%2===0?'#fff':'#fafafa'}}>
                    <td style={{...S.td, color:'#999', fontSize:'0.75rem'}}>{idx+1}</td>
                    <td style={{...S.td, fontWeight:600}}>{b.recipientName}</td>
                    <td style={S.td}>{b.recipientPhone}</td>
                    <td style={{...S.td, maxWidth:180, overflow:'hidden',
                      textOverflow:'ellipsis', textAlign:'left'}}>{b.address}</td>
                    <td style={S.td}>
                      <Badge text={`${b.orderCount}건`} color="#e65100" />
                    </td>
                    <td style={{...S.td, color:'#1565c0', fontWeight:600}}>
                      {b.representativeOrderNo}
                    </td>
                    <td style={S.td}>
                      <Badge
                        text={b.status==='BUNDLED'?'묶음완료':b.status==='RELEASED'?'해제':'출고완료'}
                        color={b.status==='BUNDLED'?'#2e7d32':b.status==='RELEASED'?'#999':'#1565c0'}
                      />
                    </td>
                    <td style={{...S.td, fontSize:'0.75rem', color:'#999'}}>
                      {b.createdAt ? new Date(b.createdAt).toLocaleString('ko-KR',
                        {month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}) : '-'}
                    </td>
                    <td style={S.td}>
                      {b.status === 'BUNDLED' && (
                        <button onClick={()=>release(b.bundleId, b.recipientName)}
                          style={{...S.bRed, padding:'0.2rem 0.6rem', fontSize:'0.75rem'}}>
                          해제
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
