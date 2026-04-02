import { useState, useEffect, useCallback } from 'react';

const API = import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
            'https://oms-backend-production-8a38.up.railway.app';

const S = {
  page:  { padding:'2rem', background:'#f5f5f5', minHeight:'100vh', color:'#111' },
  card:  { background:'#fff', borderRadius:'8px', padding:'1.5rem', marginBottom:'1.25rem',
           boxShadow:'0 1px 4px rgba(0,0,0,0.06)', color:'#111' },
  th:    { padding:'0.65rem 1rem', background:'#f5f5f5', fontSize:'0.82rem',
           fontWeight:700, color:'#444', textAlign:'left', borderBottom:'1px solid #e0e0e0' },
  td:    { padding:'0.65rem 1rem', fontSize:'0.85rem', color:'#333',
           borderBottom:'1px solid #f0f0f0' },
};

function KpiCard({ title, value, color, sub }) {
  return (
    <div style={{ background:'#fff', padding:'1.25rem 1.5rem', borderRadius:'8px',
      boxShadow:'0 1px 4px rgba(0,0,0,0.06)', borderTop:`3px solid ${color}`,
      minWidth:140, flex:1 }}>
      <div style={{ fontSize:'0.78rem', color:'#888', marginBottom:'0.4rem' }}>{title}</div>
      <div style={{ fontSize:'1.8rem', fontWeight:700, color }}>{value}</div>
      {sub && <div style={{ fontSize:'0.72rem', color:'#aaa', marginTop:'0.3rem' }}>{sub}</div>}
    </div>
  );
}

function StatusBadge({ text, color, bg }) {
  return (
    <span style={{ padding:'0.18rem 0.55rem', borderRadius:'10px', fontSize:'0.72rem',
      fontWeight:700, background:bg, color }}>
      {text}
    </span>
  );
}

export default function MainDashboard({ user }) {
  const today   = new Date();
  const fmt     = d => d.toISOString().slice(0, 10);
  const weekAgo = new Date(today); weekAgo.setDate(today.getDate() - 6);

  const [startDate, setStartDate] = useState(fmt(weekAgo));
  const [endDate,   setEndDate]   = useState(fmt(today));
  const [stats,     setStats]     = useState(null);
  const [loading,   setLoading]   = useState(false);

  const setPreset = (days) => {
    const end   = new Date();
    const start = new Date(); start.setDate(end.getDate() - (days - 1));
    setStartDate(fmt(start));
    setEndDate(fmt(end));
  };

  const load = useCallback(async (sd, ed) => {
    const s = sd ?? startDate;
    const e = ed ?? endDate;
    const dateParam = `startDate=${s}&endDate=${e}`;
    setLoading(true);
    try {
      const [
        confirmedRes,
        completedRes,
        shippedRes,
        unmatchedRes,
        allocatedRes,
        warehousesRes,
      ] = await Promise.allSettled([
        fetch(`${API}/api/invoice/orders?${dateParam}`),
        fetch(`${API}/api/invoice/completed?${dateParam}`),
        fetch(`${API}/api/invoice/shipped?${dateParam}`),
        fetch(`${API}/api/stock-matching/unmatched-count`),
        fetch(`${API}/api/stock-matching/allocated?warehouseCode=`),
        fetch(`${API}/api/warehouses`),
      ]);

      const safeJson = async (res) => {
        if (res.status !== 'fulfilled' || !res.value.ok) return null;
        try { return await res.value.json(); } catch { return null; }
      };

      const [confirmed, completed, shipped, unmatchedCount, allocated, warehousesRaw] = await Promise.all([
        safeJson(confirmedRes).then(v => v ?? []),
        safeJson(completedRes).then(v => v ?? []),
        safeJson(shippedRes).then(v => v ?? []),
        safeJson(unmatchedRes).then(v => typeof v === 'number' ? v : (Array.isArray(v) ? v.length : 0)),
        safeJson(allocatedRes).then(v => v ?? []),
        safeJson(warehousesRes).then(v => v ?? []),
      ]);

      const warehouses = Array.isArray(warehousesRaw)
        ? warehousesRaw
        : (warehousesRaw.content ?? []);

      // 판매처별 집계
      const byChannelMap = {};
      [...confirmed, ...completed, ...shipped].forEach(o => {
        const ch = o.channelName || '미분류';
        if (!byChannelMap[ch]) byChannelMap[ch] = { channelName: ch, count: 0 };
        byChannelMap[ch].count++;
      });
      const byChannel = Object.values(byChannelMap).sort((a, b) => b.count - a.count);

      const noInvoice = confirmed.filter(o => !o.hasInvoice);

      setStats({
        unmatched:    unmatchedCount,
        allocated:    allocated.length,
        invoiceTodo:  noInvoice.length,
        invoiceDone:  completed.length,
        shipped:      shipped.length,
        pipeline: [
          { label:'미매칭',        value: unmatchedCount,     color:'#c62828', bg:'#ffebee',  desc:'상품명 매칭 필요' },
          { label:'재고 할당 대기', value: noInvoice.length,  color:'#e65100', bg:'#fff3e0',  desc:'재고 매칭 후 할당' },
          { label:'송장 미입력',   value: noInvoice.length,   color:'#f57c00', bg:'#fff8e1',  desc:'송장번호 입력 필요' },
          { label:'검수 대기',     value: completed.length,   color:'#7b1fa2', bg:'#f3e5f5',  desc:'발송 처리 필요' },
          { label:'발송 완료',     value: shipped.length,     color:'#2e7d32', bg:'#e8f5e9',  desc:'누적' },
        ],
        byChannel,
        warehouses: warehouses.filter(w => w.isActive !== false),
        recentShipped: shipped.slice(0, 5),
      });
    } catch(e) {
      console.error('대시보드 로드 실패:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(startDate, endDate); }, []);

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', flexWrap:'wrap', gap:'1rem' }}>
          <div>
            <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>대시보드</h1>
            <div style={{ fontSize:'0.875rem', color:'#666' }}>주문 현황 및 통계</div>
          </div>
          <div style={{ display:'flex', gap:'0.5rem', alignItems:'center', flexWrap:'wrap' }}>
            {/* 프리셋 버튼 */}
            {[{label:'오늘',days:1},{label:'7일',days:7},{label:'30일',days:30}].map(p => (
              <button key={p.days}
                onClick={() => {
                  const end   = new Date();
                  const start = new Date(); start.setDate(end.getDate() - (p.days - 1));
                  const s = fmt(start), e = fmt(end);
                  setStartDate(s); setEndDate(e);
                  load(s, e);
                }}
                style={{ padding:'0.35rem 0.8rem', border:'1px solid #ccc', borderRadius:'4px',
                  background:'#fff', cursor:'pointer', fontSize:'0.82rem', color:'#555' }}>
                {p.label}
              </button>
            ))}
            {/* 날짜 범위 */}
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
              style={{ padding:'0.35rem 0.5rem', border:'1px solid #ccc', borderRadius:'4px',
                fontSize:'0.82rem', color:'#111' }} />
            <span style={{ color:'#888', fontSize:'0.82rem' }}>~</span>
            <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
              style={{ padding:'0.35rem 0.5rem', border:'1px solid #ccc', borderRadius:'4px',
                fontSize:'0.82rem', color:'#111' }} />
            <button onClick={() => load(startDate, endDate)} disabled={loading}
              style={{ padding:'0.35rem 0.9rem', background:'#1976d2', color:'#fff',
                border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'0.82rem', fontWeight:600 }}>
              {loading ? '...' : '조회'}
            </button>
          </div>
        </div>
      </div>

      {loading && !stats ? (
        <div style={{...S.card, textAlign:'center', padding:'3rem', color:'#999'}}>⏳ 로딩 중...</div>
      ) : stats && (
        <>
          {/* KPI 카드 */}
          <div style={{ display:'flex', gap:'1rem', marginBottom:'1.25rem', flexWrap:'wrap' }}>
            <KpiCard title="미매칭"         value={stats.unmatched}   color="#c62828" sub="상품명 매칭 필요" />
            <KpiCard title="재고 할당 완료"  value={stats.allocated}   color="#1565c0" sub="송장 입력 대기" />
            <KpiCard title="송장 미입력"    value={stats.invoiceTodo} color="#f57c00" sub="송장번호 필요" />
            <KpiCard title="검수 대기"      value={stats.invoiceDone} color="#7b1fa2" sub="발송 처리 필요" />
            <KpiCard title="발송 완료"      value={stats.shipped}     color="#2e7d32" sub="누적" />
          </div>

          {/* 주문 처리 파이프라인 */}
          <div style={S.card}>
            <div style={{ fontWeight:700, fontSize:'1rem', color:'#1a1a1a', marginBottom:'1rem' }}>
              주문 처리 파이프라인
            </div>
            <div style={{ display:'flex', alignItems:'stretch', flexWrap:'wrap', gap:0 }}>
              {stats.pipeline.map((step, idx) => (
                <div key={step.label} style={{ display:'flex', alignItems:'center', flex:1, minWidth:110 }}>
                  <div style={{ flex:1, background:step.bg, border:`1px solid ${step.color}30`,
                    borderRadius:6, padding:'1rem', textAlign:'center' }}>
                    <div style={{ fontSize:'0.74rem', color:step.color, fontWeight:700, marginBottom:'0.35rem' }}>
                      {step.label}
                    </div>
                    <div style={{ fontSize:'1.6rem', fontWeight:700, color:step.color }}>
                      {step.value}
                    </div>
                    <div style={{ fontSize:'0.68rem', color:'#aaa', marginTop:'0.2rem' }}>
                      {step.desc}
                    </div>
                  </div>
                  {idx < stats.pipeline.length - 1 && (
                    <div style={{ fontSize:'1.1rem', color:'#ccc', padding:'0 4px', flexShrink:0 }}>→</div>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:'1.25rem' }}>
            {/* 판매처별 주문 */}
            <div style={S.card}>
              <div style={{ fontWeight:700, fontSize:'1rem', color:'#1a1a1a', marginBottom:'1rem' }}>
                판매처별 주문
              </div>
              {stats.byChannel.length > 0 ? (
                <table style={{ width:'100%', borderCollapse:'collapse' }}>
                  <thead>
                    <tr>
                      {['판매처','주문수'].map(h => (
                        <th key={h} style={S.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {stats.byChannel.map((r, i) => (
                      <tr key={i}>
                        <td style={S.td}>
                          <span style={{ padding:'0.15rem 0.5rem', borderRadius:'10px',
                            background:'#e3f2fd', color:'#1565c0', fontSize:'0.75rem', fontWeight:600 }}>
                            {r.channelName}
                          </span>
                        </td>
                        <td style={{ ...S.td, fontWeight:600 }}>{r.count.toLocaleString()}건</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div style={{ color:'#bbb', textAlign:'center', padding:'2rem' }}>데이터 없음</div>
              )}
            </div>

            {/* 최근 발송 완료 */}
            <div style={S.card}>
              <div style={{ fontWeight:700, fontSize:'1rem', color:'#1a1a1a', marginBottom:'1rem' }}>
                최근 발송 완료
              </div>
              {stats.recentShipped.length > 0 ? (
                <table style={{ width:'100%', borderCollapse:'collapse' }}>
                  <thead>
                    <tr>
                      {['수취인','상품명','택배사','송장번호'].map(h => (
                        <th key={h} style={S.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {stats.recentShipped.map((o, i) => (
                      <tr key={i}>
                        <td style={{ ...S.td, fontWeight:600 }}>{o.recipientName || '-'}</td>
                        <td style={{ ...S.td, maxWidth:140, overflow:'hidden',
                          textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                          {o.productName || '-'}
                        </td>
                        <td style={S.td}>
                          <StatusBadge text={o.carrierName || '-'} color="#2e7d32" bg="#e8f5e9"/>
                        </td>
                        <td style={{ ...S.td, fontSize:'0.78rem', color:'#1565c0', fontWeight:600 }}>
                          {o.trackingNo || '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div style={{ color:'#bbb', textAlign:'center', padding:'2rem' }}>발송 완료 내역 없음</div>
              )}
            </div>
          </div>

          {/* 창고 현황 */}
          {stats.warehouses.length > 0 && (
            <div style={S.card}>
              <div style={{ fontWeight:700, fontSize:'1rem', color:'#1a1a1a', marginBottom:'1rem' }}>
                창고 현황
              </div>
              <div style={{ display:'flex', gap:'1rem', flexWrap:'wrap' }}>
                {stats.warehouses.map(w => (
                  <div key={w.warehouseId}
                    style={{ background:'#f8f9ff', border:'1px solid #e3f2fd',
                      borderRadius:6, padding:'0.75rem 1.25rem', minWidth:150 }}>
                    <div style={{ fontSize:'0.72rem', color:'#999', marginBottom:'0.2rem' }}>{w.code}</div>
                    <div style={{ fontWeight:700, color:'#1565c0' }}>{w.name}</div>
                    <div style={{ fontSize:'0.72rem', color:'#aaa', marginTop:'0.15rem' }}>
                      {w.type || '실제창고'}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
