import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
                 'https://oms-backend-production-8a38.up.railway.app';

const S = {
  page:   { padding:'2rem', background:'#f5f5f5', minHeight:'100vh' },
  card:   { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem',
            boxShadow:'0 1px 4px rgba(0,0,0,0.06)' },
  bBlue:  { padding:'0.5rem 1.5rem', background:'#1976d2', color:'#fff', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
  bGray:  { padding:'0.5rem 1.5rem', background:'#e0e0e0', color:'#333', border:'none',
            borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem' },
  sec:    { display:'flex', alignItems:'center', gap:'0.5rem', fontWeight:700,
            fontSize:'0.95rem', color:'#1565c0', marginBottom:'1rem' },
  dot:    { width:9, height:9, borderRadius:'50%', background:'#1565c0', flexShrink:0 },
};

export default function StockAllocation() {
  const [warehouses,  setWarehouses]  = useState([]);
  const [selected,    setSelected]    = useState(null); // { warehouseId, code, name }
  const [current,     setCurrent]     = useState(null); // 현재 설정된 창고
  const [loading,     setLoading]     = useState(false);
  const [msg,         setMsg]         = useState('');

  // 창고 목록 + 현재 설정 로드
  useEffect(() => {
    fetch(`${API_BASE}/api/warehouses`)
      .then(r => r.json())
      .then(data => setWarehouses(Array.isArray(data) ? data.filter(w => w.isActive) : []))
      .catch(e => console.error('창고 로드 실패:', e));

    fetch(`${API_BASE}/api/allocation/current`)
      .then(r => r.json())
      .then(data => { if (data.isSet) setCurrent(data); })
      .catch(() => {});
  }, []);

  const handleApply = async () => {
    if (!selected) { alert('창고를 선택해주세요.'); return; }
    setLoading(true);
    try {
      const res  = await fetch(`${API_BASE}/api/allocation/set-warehouse`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          warehouseCode: selected.code,
          warehouseName: selected.name,
        }),
      });
      const data = await res.json();
      if (data.success) {
        setCurrent({ warehouseCode: selected.code, warehouseName: selected.name });
        setMsg(`✅ ${selected.name} 창고로 설정 완료`);
      }
    } catch(e) { alert('설정 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const TYPE_COLOR = {
    REAL:    '#1976d2',
    VIRTUAL: '#9c27b0',
    RETURN:  '#7c3aed',
    SPECIAL: '#059669',
  };

  return (
    <div style={S.page}>
      <div style={S.card}>
        <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>
          재고 할당
        </h1>
        <div style={{ fontSize:'0.875rem', color:'#999' }}>
          출고할 창고를 선택합니다. 선택 후 재고 매칭에서 해당 창고 재고 기준으로 주문이 분류됩니다.
        </div>
      </div>

      {/* 현재 설정 */}
      {current?.isSet !== false && current && (
        <div style={{ padding:'0.85rem 1.25rem', background:'#e3f2fd',
          border:'1px solid #90caf9', borderRadius:'6px', marginBottom:'1rem',
          fontSize:'0.875rem', color:'#1565c0', fontWeight:600 }}>
          현재 할당 창고: <strong>{current.warehouseName}</strong>
          <span style={{ fontSize:'0.78rem', color:'#666', marginLeft:'0.5rem', fontWeight:400 }}>
            (재고 매칭에서 이 창고 재고 기준으로 분류됩니다)
          </span>
        </div>
      )}

      {msg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem',
          fontWeight:600, color:'#1b5e20' }}>
          {msg}
          <button onClick={()=>setMsg('')}
            style={{float:'right',background:'none',border:'none',cursor:'pointer',color:'#666'}}>✕</button>
        </div>
      )}

      {/* 창고 선택 */}
      <div style={S.card}>
        <div style={S.sec}><span style={S.dot}/>출고 창고 선택</div>

        {warehouses.length === 0 ? (
          <div style={{ textAlign:'center', padding:'2rem', color:'#bbb' }}>
            창고 정보를 불러오는 중...
          </div>
        ) : (
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fill,minmax(200px,1fr))',
            gap:'0.75rem', marginBottom:'1.5rem' }}>
            {warehouses.map(wh => {
              const isSelected = selected?.warehouseId === wh.warehouseId;
              const isCurrent  = current?.warehouseCode === wh.code;
              const color      = TYPE_COLOR[wh.type] || '#666';
              return (
                <div key={wh.warehouseId}
                  onClick={() => setSelected(wh)}
                  style={{ padding:'1rem 1.25rem', borderRadius:'8px', cursor:'pointer',
                    border:`2px solid ${isSelected ? color : '#e0e0e0'}`,
                    background: isSelected ? color+'12' : '#fff',
                    transition:'all 0.15s', position:'relative' }}>
                  {isCurrent && (
                    <span style={{ position:'absolute', top:6, right:8,
                      fontSize:'0.68rem', background:'#1976d2', color:'#fff',
                      padding:'0.1rem 0.4rem', borderRadius:'8px', fontWeight:700 }}>
                      현재
                    </span>
                  )}
                  <div style={{ display:'flex', alignItems:'center', gap:'0.5rem', marginBottom:'0.4rem' }}>
                    <span style={{ width:8, height:8, borderRadius:'50%',
                      background:color, flexShrink:0 }}/>
                    <span style={{ fontWeight:700, fontSize:'0.9rem', color:'#111' }}>
                      {wh.name}
                    </span>
                  </div>
                  <div style={{ fontSize:'0.75rem', color:'#888' }}>{wh.code}</div>
                  <div style={{ fontSize:'0.72rem', color, marginTop:2, fontWeight:600 }}>
                    {wh.type === 'REAL' ? '실제창고' : wh.type === 'RETURN' ? '반품창고' : wh.type}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <div style={{ display:'flex', gap:'0.75rem', alignItems:'center' }}>
          <button onClick={handleApply} disabled={loading || !selected} style={{
            ...S.bBlue, opacity:!selected?0.5:1 }}>
            {loading ? '⏳ 적용 중...' : '✓ 창고 적용'}
          </button>
          {selected && (
            <span style={{ fontSize:'0.875rem', color:'#666' }}>
              선택: <strong style={{ color:'#1565c0' }}>{selected.name}</strong>
            </span>
          )}
        </div>
      </div>

      {/* 안내 */}
      <div style={{ ...S.card, background:'#fff8e1', border:'1px solid #ffe082' }}>
        <div style={{ fontSize:'0.875rem', color:'#555' }}>
          💡 <strong>다음 단계</strong>: 재고 매칭 메뉴에서 선택한 창고의 재고 기준으로
          각 주문을 <strong>완전출고 / 부분출고 / 출고불가</strong>로 분류합니다.
        </div>
      </div>
    </div>
  );
}
