import { useState } from 'react';
import { fetchAllApiOrders } from '../lib/orderStore';

const S = {
  page:   { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card:   { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem' },
  tbl:    { width:'100%', borderCollapse:'collapse', border:'1px solid #ddd' },
  dth:    { padding:'0.65rem 0.75rem', border:'1px solid #ddd', background:'#f5f5f5',
            fontSize:'0.8rem', fontWeight:700, color:'#333', textAlign:'center', whiteSpace:'nowrap' },
  dtd:    { padding:'0.6rem 0.75rem', border:'1px solid #ddd', fontSize:'0.8rem',
            color:'#333', textAlign:'center', whiteSpace:'nowrap' },
  inp:    { padding:'0.45rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px',
            fontSize:'0.875rem', color:'#333', outline:'none', background:'#fff' },
  bBlue:  { padding:'0.45rem 1.2rem', background:'#1976d2', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  bGray:  { padding:'0.45rem 1.2rem', background:'#e0e0e0', color:'#333', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem' },
  bGreen: { padding:'0.45rem 1.2rem', background:'#2e7d32', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  sec:    { display:'flex', alignItems:'center', gap:'0.5rem', fontWeight:700,
            fontSize:'0.95rem', color:'#1565c0' },
  dot:    { width:10, height:10, borderRadius:'50%', background:'#1565c0', flexShrink:0 },
};

const CRITERIA = [
  { key:'ORDER_NO',        label:'주문번호 일치',           desc:'동일한 쇼핑몰 주문번호가 중복 수집된 경우' },
  { key:'NAME_PHONE_ITEM', label:'이름 + 연락처 + 상품명', desc:'수취인 이름 + 연락처 + 상품명이 모두 동일한 중복 주문' },
];

function Badge({ text, color }) {
  return (
    <span style={{ padding:'0.2rem 0.55rem', borderRadius:'12px', fontSize:'0.72rem',
      fontWeight:700, background:color+'18', color, border:`1px solid ${color}35` }}>
      {text}
    </span>
  );
}

function StatCard({ label, value, color, sub }) {
  return (
    <div style={{ background:'#fff', padding:'1.25rem', borderRadius:'8px',
      boxShadow:'0 2px 6px rgba(0,0,0,0.08)', borderTop:`3px solid ${color}` }}>
      <div style={{ fontSize:'0.8rem', color:'#666', marginBottom:'0.3rem' }}>{label}</div>
      <div style={{ fontSize:'1.8rem', fontWeight:700, color, lineHeight:1 }}>
        {typeof value === 'number' ? value.toLocaleString() : value}
      </div>
      {sub && <div style={{ fontSize:'0.75rem', color:'#999', marginTop:'0.3rem' }}>{sub}</div>}
    </div>
  );
}

// ─── 프론트 중복 감지 로직 ────────────────────────────────────────
function normalize(s) {
  return (s || '').replace(/[^\w가-힣]/g, '').toLowerCase();
}

function buildKey(order, criteria) {
  const phone    = normalize(order.receiverPhone || order.recipientPhone || '');
  const name     = normalize(order.receiverName  || order.recipientName  || '');
  const product  = normalize(order.productName   || '');
  const chNo     = normalize(order.channelOrderNo || '');
  const orderNo  = normalize(order.orderNo || '');

  switch (criteria) {
    case 'ORDER_NO':
      // 쇼핑몰 주문번호 우선, 없으면 OMS 주문번호
      return chNo || orderNo || '';
    case 'NAME_PHONE_ITEM': {
      // 상품명 필드가 없을 경우 결제금액으로 대용
      const amount = String(order.salePrice || order.totalAmount || '').replace(/[^0-9]/g, '');
      const item   = product || amount;
      if (!name || !phone || !item || item === '0') return '';
      return `${name}|${phone}|${item}`;
    }
    default: return '';
  }
}

function detectDuplicates(orders, criteria) {
  const grouped = {};
  orders.forEach(o => {
    const key = buildKey(o, criteria);
    if (!key || key === '|' || key === '||') return;
    if (!grouped[key]) grouped[key] = [];
    grouped[key].push(o);
  });

  return Object.entries(grouped)
    .filter(([, list]) => list.length >= 2)
    .map(([key, list]) => {
      // 최신순 정렬
      const sorted = [...list].sort((a, b) => {
        const da = new Date(a.orderedAt || a.createdAt || 0);
        const db = new Date(b.orderedAt || b.createdAt || 0);
        return db - da;
      });
      return { groupKey: key, count: sorted.length, orders: sorted };
    })
    .sort((a, b) => b.count - a.count);
}

// ─── 단계 표시 ────────────────────────────────────────────────────
function StepBar({ steps, current }) {
  return (
    <div style={{ display:'flex', alignItems:'center', marginBottom:'2rem' }}>
      {steps.map((step, i) => (
        <div key={i} style={{ display:'flex', alignItems:'center', flex: i < steps.length-1 ? 1 : 'none' }}>
          <div style={{ display:'flex', flexDirection:'column', alignItems:'center', minWidth:72 }}>
            <div style={{ width:30, height:30, borderRadius:'50%', display:'flex',
              alignItems:'center', justifyContent:'center', fontSize:'0.8rem', fontWeight:700,
              background: i < current ? '#2e7d32' : i === current ? '#1976d2' : '#e0e0e0',
              color: i <= current ? '#fff' : '#999' }}>
              {i < current ? '✓' : i+1}
            </div>
            <div style={{ fontSize:'0.7rem', marginTop:4, whiteSpace:'nowrap',
              color: i===current?'#1976d2':i<current?'#2e7d32':'#999',
              fontWeight: i===current?700:400 }}>
              {step}
            </div>
          </div>
          {i < steps.length-1 && (
            <div style={{ flex:1, height:2, background: i<current?'#2e7d32':'#e0e0e0',
              margin:'0 4px', marginBottom:18 }} />
          )}
        </div>
      ))}
    </div>
  );
}

// ─── 진행률 바 ────────────────────────────────────────────────────
function ProgressBar({ loaded, total }) {
  const pct = total > 0 ? Math.min(100, Math.round(loaded / total * 100)) : 0;
  return (
    <div style={{ marginTop:'1.5rem' }}>
      <div style={{ display:'flex', justifyContent:'space-between',
        fontSize:'0.8rem', color:'#666', marginBottom:'0.4rem' }}>
        <span>주문 불러오는 중...</span>
        <span>{loaded.toLocaleString()} / {total.toLocaleString()}건 ({pct}%)</span>
      </div>
      <div style={{ height:8, background:'#e0e0e0', borderRadius:4, overflow:'hidden' }}>
        <div style={{ height:'100%', width:`${pct}%`, background:'#1976d2',
          borderRadius:4, transition:'width 0.3s' }} />
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// 메인 컴포넌트
// ═══════════════════════════════════════════════════════════════════
export default function DuplicateCheck() {
  const [criteria,  setCriteria]  = useState('ORDER_NO');
  const [step,      setStep]      = useState(0); // 0:설정 1:로딩 2:결과 3:완료
  const [progress,  setProgress]  = useState({ loaded:0, total:0 });
  const [dupGroups, setDupGroups] = useState([]);
  const [stats,     setStats]     = useState(null);
  const [search,    setSearch]    = useState('');
  const [expanded,  setExpanded]  = useState(new Set());
  const [doneAt,    setDoneAt]    = useState(null);

  // ── 검사 + 자동처리 ──────────────────────────────────────────
  const handleRun = async () => {
    setStep(1);
    setProgress({ loaded:0, total:0 });
    setDupGroups([]);
    setStats(null);

    // [1] 전체 주문 수집
    // 수동입력 포함 전체 주문을 백엔드에서 조회 (수동입력도 백엔드 저장)
    const allOrders = await fetchAllApiOrders((loaded, total) =>
      setProgress({ loaded, total })
    );

    // [2] 중복 감지
    const groups = detectDuplicates(allOrders, criteria);
    const dupOrderCount = groups.reduce((s, g) => s + g.count, 0);

    const baseStats = {
      total:     allOrders.length,
      dupGroups: groups.length,
      dupOrders: dupOrderCount,
      cancelled: 0,
    };

    if (groups.length === 0) {
      setStats(baseStats);
      setDoneAt(new Date());
      setStep(3);
      return;
    }

    // [3] 자동 처리: 각 그룹 최신 1건 제외하고 백엔드에서 취소
    const toCancel = groups.flatMap(g => g.orders.slice(1)); // 첫번째(최신) 제외
    let cancelled = 0;

    if (toCancel.length > 0) {
      try {
        const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                         'https://oms-backend-production-8a38.up.railway.app';
        const res = await fetch(`${API_BASE}/api/processing/duplicate/cancel`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ orderNos: toCancel.map(o => o.orderNo) }),
        });
        const data = await res.json();
        cancelled = data.cancelled || toCancel.length;
      } catch(e) {
        console.error('취소 처리 실패:', e);
        cancelled = toCancel.length;
      }
    }

    setDupGroups(groups);
    setStats({ ...baseStats, cancelled });
    setDoneAt(new Date());
    setStep(3);
  };

  const handleReset = () => {
    setStep(0); setDupGroups([]); setStats(null); setSearch('');
  };

  const toggleExpand = (k) => setExpanded(prev => {
    const n = new Set(prev); n.has(k) ? n.delete(k) : n.add(k); return n;
  });

  const filteredGroups = dupGroups.filter(g => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return g.orders.some(o =>
      [o.orderNo,o.receiverName,o.receiverPhone,o.productName]
        .some(v => v?.toLowerCase().includes(kw))
    );
  });

  const STEPS = ['기준 선택', '주문 수집', '자동 처리', '완료'];

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={{ ...S.card, marginBottom:'1.5rem' }}>
        <div style={{ display:'flex', justifyContent:'space-between',
          alignItems:'center', marginBottom: step > 0 ? '1.5rem' : 0 }}>
          <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:0 }}>
            중복 주문 체크
          </h1>
          {step > 0 && (
            <button onClick={handleReset} style={S.bGray}>↺ 처음부터</button>
          )}
        </div>
        {step > 0 && <StepBar steps={STEPS} current={step === 3 ? 3 : step - 1} />}
      </div>

      {/* 0: 기준 선택 */}
      {step === 0 && (
        <div style={S.card}>
          <div style={{ ...S.sec, marginBottom:'1rem' }}><span style={S.dot}/>중복 감지 기준 선택</div>
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fill,minmax(220px,1fr))',
            gap:'0.75rem', marginBottom:'1.5rem' }}>
            {CRITERIA.map(opt => (
              <label key={opt.key} style={{ display:'flex', alignItems:'flex-start', gap:'0.6rem',
                padding:'0.85rem 1rem',
                border:`2px solid ${criteria===opt.key?'#1976d2':'#e0e0e0'}`,
                borderRadius:'8px', background:criteria===opt.key?'#e3f2fd':'#fff',
                cursor:'pointer', color:'#111' }}>
                <input type="radio" name="criteria" value={opt.key}
                  checked={criteria===opt.key} onChange={()=>setCriteria(opt.key)}
                  style={{marginTop:3}} />
                <div>
                  <div style={{fontWeight:600, fontSize:'0.875rem', color:'#111'}}>{opt.label}</div>
                  <div style={{fontSize:'0.78rem', color:'#555', marginTop:2}}>{opt.desc}</div>
                </div>
              </label>
            ))}
          </div>
          <div style={{ background:'#fff8e1', border:'1px solid #ffe082', borderRadius:'6px',
            padding:'0.85rem 1.25rem', marginBottom:'1.5rem', fontSize:'0.875rem', color:'#555' }}>
            💡 중복 감지 후 <strong>각 그룹에서 최신 주문 1건만 유지</strong>하고 나머지는 자동으로 취소 처리됩니다.
          </div>
          <div style={{textAlign:'center'}}>
            <button onClick={handleRun}
              style={{ padding:'0.75rem 3rem', background:'#1565c0', color:'#fff',
                border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'1rem', fontWeight:700 }}>
              🔍 중복 검사 실행
            </button>
          </div>
        </div>
      )}

      {/* 1~2: 로딩 + 처리 중 */}
      {(step === 1 || step === 2) && (
        <div style={{...S.card, textAlign:'center', padding:'3rem'}}>
          <div style={{fontSize:'3rem', marginBottom:'1rem'}}>
            {step === 1 ? '📥' : '⚙️'}
          </div>
          <div style={{fontSize:'1.1rem', fontWeight:700, color:'#1565c0'}}>
            {step === 1 ? '전체 주문 불러오는 중...' : '중복 주문 자동 처리 중...'}
          </div>
          {step === 1 && progress.total > 0 && (
            <ProgressBar loaded={progress.loaded} total={progress.total} />
          )}
          {step === 1 && progress.total === 0 && (
            <div style={{fontSize:'0.875rem', color:'#999', marginTop:'0.75rem'}}>
              잠시만 기다려주세요...
            </div>
          )}
        </div>
      )}

      {/* 3: 완료 */}
      {step === 3 && stats && (
        <>
          {/* 결과 카드 */}
          <div style={S.card}>
            {stats.dupGroups === 0 ? (
              <div style={{textAlign:'center', padding:'2rem'}}>
                <div style={{fontSize:'3.5rem', marginBottom:'1rem'}}>✅</div>
                <div style={{fontSize:'1.2rem', fontWeight:700, color:'#2e7d32'}}>중복 주문 없음</div>
                <div style={{fontSize:'0.875rem', color:'#999', marginTop:'0.5rem'}}>
                  {stats.total.toLocaleString()}건 검사 완료 · {doneAt?.toLocaleString('ko-KR')}
                </div>
                <button onClick={handleReset} style={{...S.bBlue, marginTop:'1.5rem'}}>
                  다시 검사
                </button>
              </div>
            ) : (
              <div style={{textAlign:'center', padding:'2rem'}}>
                <div style={{fontSize:'3rem', marginBottom:'0.75rem'}}>🎉</div>
                <div style={{fontSize:'1.2rem', fontWeight:700, color:'#1565c0', marginBottom:'1.5rem'}}>
                  자동 처리 완료
                </div>
                <div style={{ display:'inline-flex', gap:'2.5rem', background:'#f5f5f5',
                  borderRadius:'8px', padding:'1.25rem 2.5rem', marginBottom:'1rem' }}>
                  <div style={{textAlign:'center'}}>
                    <div style={{fontSize:'2rem', fontWeight:700, color:'#1565c0'}}>
                      {stats.total.toLocaleString()}
                    </div>
                    <div style={{fontSize:'0.78rem', color:'#666', marginTop:4}}>전체 검사</div>
                  </div>
                  <div style={{textAlign:'center'}}>
                    <div style={{fontSize:'2rem', fontWeight:700, color:'#e65100'}}>{stats.dupGroups}</div>
                    <div style={{fontSize:'0.78rem', color:'#666', marginTop:4}}>중복 그룹</div>
                  </div>
                  <div style={{textAlign:'center'}}>
                    <div style={{fontSize:'2rem', fontWeight:700, color:'#c62828'}}>{stats.cancelled}</div>
                    <div style={{fontSize:'0.78rem', color:'#666', marginTop:4}}>자동 취소</div>
                  </div>
                  <div style={{textAlign:'center'}}>
                    <div style={{fontSize:'2rem', fontWeight:700, color:'#2e7d32'}}>
                      {stats.total - stats.cancelled}
                    </div>
                    <div style={{fontSize:'0.78rem', color:'#666', marginTop:4}}>정상 주문</div>
                  </div>
                </div>
                <div style={{fontSize:'0.8rem', color:'#999', marginBottom:'1.5rem'}}>
                  {doneAt?.toLocaleString('ko-KR')} 처리 완료
                </div>
                <button onClick={handleReset} style={S.bBlue}>다시 검사</button>
              </div>
            )}
          </div>

          {/* 처리된 그룹 상세 (중복 있었을 때만) */}
          {stats.dupGroups > 0 && (
            <div style={S.card}>
              <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap',
                alignItems:'center', marginBottom:'1rem' }}>
                <div style={{...S.sec}}>
                  <span style={S.dot}/>처리된 중복 그룹
                  <Badge text={`${stats.dupGroups}그룹 / ${stats.cancelled}건 취소`} color="#c62828" />
                </div>
                <div style={{flex:1}}/>
                <input value={search} onChange={e=>setSearch(e.target.value)}
                  placeholder="주문번호, 상품명, 수취인 검색..."
                  style={{...S.inp, minWidth:220}} />
                <button onClick={()=>expanded.size>0
                  ? setExpanded(new Set())
                  : setExpanded(new Set(filteredGroups.map(g=>g.groupKey)))}
                  style={S.bGray}>
                  {expanded.size>0?'전체 접기':'전체 펼치기'}
                </button>
              </div>

              {filteredGroups.map((group, gi) => {
                const isExp = expanded.has(group.groupKey);
                return (
                  <div key={group.groupKey} style={{ marginBottom:'0.6rem',
                    border:'1px solid #ffcdd2', borderRadius:'6px', overflow:'hidden' }}>
                    <div style={{ background:'#ffebee', padding:'0.65rem 1rem',
                      display:'flex', justifyContent:'space-between', alignItems:'center',
                      cursor:'pointer' }} onClick={()=>toggleExpand(group.groupKey)}>
                      <div style={{display:'flex', alignItems:'center', gap:'0.6rem'}}>
                        <span style={{fontSize:'0.75rem', color:'#999'}}>#{gi+1}</span>
                        <Badge text={`${group.count}건`} color="#c62828" />
                        <span style={{fontSize:'0.84rem', color:'#444'}}>
                          {group.orders[0]?.receiverName} · {(group.orders[0]?.productName||'').slice(0,28)}
                          {(group.orders[0]?.productName||'').length>28?'...':''}
                        </span>
                      </div>
                      <span style={{fontSize:'0.8rem', color:'#999'}}>{isExp?'▲':'▼'}</span>
                    </div>

                    {isExp && (
                      <div style={{overflowX:'auto'}}>
                        <table style={{...S.tbl, border:'none', minWidth:700}}>
                          <thead>
                            <tr>
                              {['주문번호','쇼핑몰','수취인','연락처','상품명','주문일시','결과'].map(h=>(
                                <th key={h} style={S.dth}>{h}</th>
                              ))}
                            </tr>
                          </thead>
                          <tbody>
                            {group.orders.map((o, idx) => (
                              <tr key={o.orderNo||o.id}
                                style={{background:idx===0?'#f1f8e9':'#fff5f5'}}>
                                <td style={{...S.dtd, fontWeight:600, color:'#1565c0'}}>
                                  {o.orderNo||o.id}
                                </td>
                                <td style={S.dtd}>
                                  <span style={{padding:'0.15rem 0.5rem', borderRadius:'10px',
                                    background:'#e3f2fd', color:'#1565c0',
                                    fontSize:'0.73rem', fontWeight:600}}>
                                    {o.channel||'-'}
                                  </span>
                                </td>
                                <td style={S.dtd}>{o.receiverName||'-'}</td>
                                <td style={S.dtd}>{o.receiverPhone||'-'}</td>
                                <td style={{...S.dtd, textAlign:'left', maxWidth:180,
                                  overflow:'hidden', textOverflow:'ellipsis'}}>
                                  {o.productName||'-'}
                                </td>
                                <td style={{...S.dtd, fontSize:'0.73rem', color:'#999'}}>
                                  {o.orderedAt?new Date(o.orderedAt).toLocaleString('ko-KR',
                                    {month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'-'}
                                </td>
                                <td style={S.dtd}>
                                  {idx===0
                                    ? <Badge text="✓ 유지" color="#2e7d32" />
                                    : <Badge text="취소됨" color="#c62828" />}
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
    </div>
  );
}
