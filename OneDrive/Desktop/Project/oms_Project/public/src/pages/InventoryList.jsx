import { useState, useEffect, useRef, useCallback } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') ||
                 'https://oms-backend-production-8a38.up.railway.app/api/inventory';
const API_WH   = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/warehouses') ||
                 'https://oms-backend-production-8a38.up.railway.app/api/warehouses';

const PAGE_SIZE = 50;

// ─── 보기방식 옵션 정의 ───────────────────────────────────────────────────
const VIEW_OPTIONS = [
  { key: 'simpleView',      label: '간단히보기' },
  { key: 'todayOutbound',   label: '당일입출고수량표시' },
  { key: 'productSummary',  label: '상품별합계표시' },
  { key: 'warehouseQty',    label: '창고별 옵션수량표시' },
  { key: 'warehouseDetail', label: '창고별 세부상품위치표시' },
  { key: 'availableStock',  label: '가용재고표시' },
  { key: 'barcodeView',     label: '바코드번호표시' },
  { key: 'purchaseOption',  label: '사입옵션명표시' },
];

// ─── 공통 스타일 ──────────────────────────────────────────────────────────
const thStyle = {
  padding: '0.75rem 1rem', textAlign: 'left', borderBottom: '2px solid #e2e8f0',
  color: '#000', fontWeight: '700', background: '#f7fafc', whiteSpace: 'nowrap',
};
const tdStyle = {
  padding: '0.75rem 1rem', borderBottom: '1px solid #e2e8f0',
  color: '#000', fontSize: '0.875rem',
};
const tdRight = { ...tdStyle, textAlign: 'right' };
const tdCenter = { ...tdStyle, textAlign: 'center' };

// ─── 재고 상태 ────────────────────────────────────────────────────────────
function getStatusColor(p) {
  if (p.isOutOfStock || p.totalStock === 0) return '#e53e3e';
  if (p.isBelowSafetyStock) return '#dd6b20';
  return '#38a169';
}
function getStatusText(p) {
  if (p.isOutOfStock || p.totalStock === 0) return '재고없음';
  if (p.isBelowSafetyStock) return '부족';
  return '정상';
}

// ─── 보기방식 드롭다운 ────────────────────────────────────────────────────
function ViewSelector({ viewOpts, onChange }) {
  const [open, setOpen]   = useState(false);
  const [local, setLocal] = useState({ ...viewOpts });
  const ref = useRef(null);

  useEffect(() => {
    const handler = e => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const toggle = (key) => setLocal(prev => ({ ...prev, [key]: !prev[key] }));

  const handleConfirm = () => { onChange(local); setOpen(false); };
  const handleReset   = () => {
    const reset = Object.fromEntries(VIEW_OPTIONS.map(o => [o.key, false]));
    reset.availableStock = true;
    setLocal(reset);
  };

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen(!open)}
        style={{
          padding: '0.5rem 1rem', borderRadius: '8px',
          border: '1px solid #667eea', background: open ? '#667eea' : '#fff',
          color: open ? '#fff' : '#667eea', cursor: 'pointer', fontWeight: '600',
          fontSize: '0.875rem', display: 'flex', alignItems: 'center', gap: '0.4rem',
        }}
      >
        보기 방식 ▾
      </button>

      {open && (
        <div style={{
          position: 'absolute', right: 0, top: 'calc(100% + 4px)', zIndex: 999,
          background: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)', minWidth: 220, padding: '0.5rem 0',
        }}>
          {VIEW_OPTIONS.map(opt => (
            <label key={opt.key}
              style={{
                display: 'flex', alignItems: 'center', gap: '0.6rem',
                padding: '0.5rem 1rem', cursor: 'pointer',
                background: local[opt.key] ? '#f0f4ff' : 'transparent',
              }}
              onMouseEnter={e => { if (!local[opt.key]) e.currentTarget.style.background='#f7fafc'; }}
              onMouseLeave={e => { e.currentTarget.style.background = local[opt.key] ? '#f0f4ff' : 'transparent'; }}
            >
              <input type="checkbox" checked={!!local[opt.key]}
                onChange={() => toggle(opt.key)}
                style={{ width: 15, height: 15, cursor: 'pointer' }} />
              <span style={{ fontSize: '0.875rem', color: '#333' }}>{opt.label}</span>
            </label>
          ))}
          <div style={{ borderTop: '1px solid #e2e8f0', margin: '0.5rem 0' }} />
          <div style={{ display: 'flex', gap: '0.5rem', padding: '0.25rem 0.75rem 0.5rem' }}>
            <button onClick={handleConfirm}
              style={{ flex: 1, padding: '0.5rem', background: '#667eea', color: '#fff',
                border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '0.875rem' }}>
              확인
            </button>
            <button onClick={handleReset}
              style={{ flex: 1, padding: '0.5rem', background: '#edf2f7', color: '#333',
                border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '0.875rem' }}>
              초기화
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── 페이지네이션 ─────────────────────────────────────────────────────────
function Pagination({ page, totalPages, totalElements, onPageChange }) {
  if (totalPages <= 1) return null;

  const range = [];
  const delta = 2;
  const left  = Math.max(0, page - delta);
  const right = Math.min(totalPages - 1, page + delta);
  for (let i = left; i <= right; i++) range.push(i);

  const btnBase = {
    padding: '0.4rem 0.75rem', border: '1px solid #e2e8f0', borderRadius: '6px',
    cursor: 'pointer', fontSize: '0.875rem', background: '#fff', color: '#333',
  };
  const btnActive = { ...btnBase, background: '#667eea', color: '#fff', borderColor: '#667eea', fontWeight: 700 };
  const btnDisabled = { ...btnBase, opacity: 0.4, cursor: 'not-allowed' };

  return (
    <div style={{ display:'flex', justifyContent:'center', alignItems:'center',
      gap:'0.4rem', padding:'1.25rem', flexWrap:'wrap' }}>
      <button style={page===0 ? btnDisabled : btnBase}
        disabled={page===0} onClick={() => onPageChange(0)}>«</button>
      <button style={page===0 ? btnDisabled : btnBase}
        disabled={page===0} onClick={() => onPageChange(page-1)}>‹</button>

      {left > 0 && <span style={{ padding:'0 0.4rem', color:'#999' }}>…</span>}
      {range.map(p => (
        <button key={p} style={p===page ? btnActive : btnBase} onClick={() => onPageChange(p)}>
          {p + 1}
        </button>
      ))}
      {right < totalPages-1 && <span style={{ padding:'0 0.4rem', color:'#999' }}>…</span>}

      <button style={page>=totalPages-1 ? btnDisabled : btnBase}
        disabled={page>=totalPages-1} onClick={() => onPageChange(page+1)}>›</button>
      <button style={page>=totalPages-1 ? btnDisabled : btnBase}
        disabled={page>=totalPages-1} onClick={() => onPageChange(totalPages-1)}>»</button>

      <span style={{ marginLeft:'0.5rem', fontSize:'0.8rem', color:'#666' }}>
        {page+1} / {totalPages}페이지 (총 {totalElements?.toLocaleString()}개)
      </span>
    </div>
  );
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────
export default function InventoryList() {
  const [products,       setProducts]      = useState([]);
  const [loading,        setLoading]       = useState(false);
  const [searchKeyword,  setSearchKeyword] = useState('');
  const [filterType,     setFilterType]    = useState('all');
  const [stats,          setStats]         = useState({
    totalProducts:0, totalStockValue:0, lowStockCount:0, outOfStockCount:0,
  });
  const [page,           setPage]          = useState(0);
  const [totalPages,     setTotalPages]    = useState(0);
  const [totalElements,  setTotalElements] = useState(0);
  const [warehouses,     setWarehouses]    = useState([]);

  // 보기방식 상태
  const [viewOpts, setViewOpts] = useState({
    simpleView:      false,
    todayOutbound:   false,
    productSummary:  false,
    warehouseQty:    false,
    warehouseDetail: false,
    availableStock:  true,   // 기본 체크
    barcodeView:     false,
    purchaseOption:  false,
  });

  // 초기 로드
  useEffect(() => {
    loadStats();
    loadWarehouses();
    loadProducts(0, 'all', '');
  }, []);

  const loadStats = async () => {
    try {
      const res  = await fetch(`${API_BASE}/stats`);
      const data = await res.json();
      setStats(data);
    } catch (e) { console.error('통계 로드 실패', e); }
  };

  const loadWarehouses = async () => {
    try {
      const res  = await fetch(`${API_WH}/active`);
      const data = await res.json();
      setWarehouses(data);
    } catch (e) { console.error('창고 로드 실패', e); }
  };

  // ── 핵심: 서버사이드 페이지네이션 로드 ────────────────────────────────
  const loadProducts = useCallback(async (targetPage, filter, keyword) => {
    setLoading(true);
    try {
      let url;
      const pageParams = `page=${targetPage}&size=${PAGE_SIZE}`;

      if (keyword && keyword.trim()) {
        // 검색: 키워드 + 페이지
        url = `${API_BASE}/products/search?keyword=${encodeURIComponent(keyword.trim())}&${pageParams}`;
      } else if (filter === 'low-stock') {
        url = `${API_BASE}/low-stock?${pageParams}`;
      } else if (filter === 'out-of-stock') {
        url = `${API_BASE}/out-of-stock?${pageParams}`;
      } else {
        // 전체
        url = `${API_BASE}/products?${pageParams}`;
      }

      const res  = await fetch(url);
      const data = await res.json();

      // Spring Page 응답 or 일반 배열 모두 처리
      if (data && typeof data === 'object' && 'content' in data) {
        // 페이지네이션 응답
        setProducts(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      } else if (Array.isArray(data)) {
        // 배열 응답 → 프론트에서 슬라이싱
        const start = targetPage * PAGE_SIZE;
        setProducts(data.slice(start, start + PAGE_SIZE));
        setTotalPages(Math.ceil(data.length / PAGE_SIZE));
        setTotalElements(data.length);
      } else {
        setProducts([]);
        setTotalPages(0);
        setTotalElements(0);
      }
    } catch (e) {
      console.error('상품 로드 실패', e);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // 페이지 변경
  const handlePageChange = (newPage) => {
    setPage(newPage);
    loadProducts(newPage, filterType, searchKeyword);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // 검색
  const handleSearch = () => {
    setPage(0);
    setFilterType('all');
    loadProducts(0, 'all', searchKeyword);
  };

  // 필터 변경
  const handleFilterChange = (type) => {
    setFilterType(type);
    setSearchKeyword('');
    setPage(0);
    loadProducts(0, type, '');
  };

  // 초기화
  const handleReset = () => {
    setSearchKeyword('');
    setFilterType('all');
    setPage(0);
    loadProducts(0, 'all', '');
  };

  // 보기방식 적용
  const handleViewChange = (newOpts) => setViewOpts(newOpts);

  // ── 창고별 수량 컬럼 계산 ─────────────────────────────────────────────
  // 창고 코드 → 상품 필드 매핑
  const getWhStock = (product, whCode) => {
    switch (whCode) {
      case 'ANYANG':     return product.warehouseStockAnyang  ?? '-';
      case 'ICHEON_BOX':
      case 'ICHEON_PCS': return product.warehouseStockIcheon  ?? '-';
      case 'BUCHEON':    return product.warehouseStockBucheon ?? '-';
      default:
        // 동적 필드 시도
        return product[`stock_${whCode}`] ?? product[`warehouseStock_${whCode}`] ?? '-';
    }
  };

  // ── 컬럼 헤더 결정 ───────────────────────────────────────────────────
  const renderHeaders = () => {
    const cols = [];

    // 기본 컬럼
    if (!viewOpts.simpleView) {
      cols.push(<th key="sku"  style={thStyle}>SKU</th>);
    }
    cols.push(<th key="name" style={{ ...thStyle, minWidth: 200 }}>상품명</th>);


    // 바코드 표시
    if (viewOpts.barcodeView) {
      cols.push(<th key="barcode" style={thStyle}>바코드번호</th>);
    }

    // 사입옵션명
    if (viewOpts.purchaseOption) {
      cols.push(<th key="popt" style={thStyle}>사입옵션명</th>);
    }

    // 창고별 수량
    if (viewOpts.warehouseQty && warehouses.length > 0) {
      warehouses.forEach(wh => {
        cols.push(<th key={`wh_${wh.code}`} style={{ ...thStyle, textAlign:'right' }}>{wh.name}</th>);
      });
    }

    // 창고별 세부위치
    if (viewOpts.warehouseDetail) {
      cols.push(<th key="loc" style={thStyle}>창고위치</th>);
    }

    // 가용재고 (기본 표시)
    if (viewOpts.availableStock || (!viewOpts.simpleView && !viewOpts.warehouseQty)) {
      cols.push(<th key="avail" style={{ ...thStyle, textAlign:'right' }}>가용재고</th>);
    }

    // 예약재고 (간단보기 아닐 때)
    if (!viewOpts.simpleView) {
      cols.push(<th key="res"   style={{ ...thStyle, textAlign:'right' }}>예약재고</th>);
    }

    // 총재고
    cols.push(<th key="total" style={{ ...thStyle, textAlign:'right' }}>총재고</th>);

    // 안전재고
    if (!viewOpts.simpleView) {
      cols.push(<th key="safe" style={{ ...thStyle, textAlign:'right' }}>안전재고</th>);
    }

    // 당일 입출고
    if (viewOpts.todayOutbound) {
      cols.push(<th key="tin"  style={{ ...thStyle, textAlign:'right' }}>당일입고</th>);
      cols.push(<th key="tout" style={{ ...thStyle, textAlign:'right' }}>당일출고</th>);
    }

    cols.push(<th key="status" style={{ ...thStyle, textAlign:'center' }}>상태</th>);

    if (!viewOpts.simpleView) {
      cols.push(<th key="pos" style={thStyle}>위치</th>);
    }

    return cols;
  };

  const renderRow = (product) => {
    const cols = [];
    const statusColor = getStatusColor(product);
    const statusText  = getStatusText(product);

    if (!viewOpts.simpleView) {
      cols.push(
        <td key="sku" style={tdStyle}>
          <span style={{ background:'#edf2f7', padding:'0.2rem 0.6rem',
            borderRadius:'4px', fontSize:'0.8rem', fontWeight:'600', color:'#000' }}>
            {product.sku}
          </span>
        </td>
      );
    }
    cols.push(
      <td key="name" style={{ ...tdStyle, fontWeight:'600' }}>{product.productName}</td>
    );


    if (viewOpts.barcodeView) {
      cols.push(<td key="barcode" style={tdStyle}>{product.barcode || '-'}</td>);
    }

    if (viewOpts.purchaseOption) {
      cols.push(<td key="popt" style={tdStyle}>{product.purchaseOption || product.optionName || '-'}</td>);
    }

    // 창고별 수량
    if (viewOpts.warehouseQty && warehouses.length > 0) {
      warehouses.forEach(wh => {
        const stock = getWhStock(product, wh.code);
        cols.push(
          <td key={`wh_${wh.code}`} style={{ ...tdRight, fontWeight: stock > 0 ? '700' : '400',
            color: stock === 0 || stock === '-' ? '#999' : '#000' }}>
            {stock === '-' ? '-' : Number(stock).toLocaleString()}
          </td>
        );
      });
    }

    if (viewOpts.warehouseDetail) {
      cols.push(<td key="loc" style={tdStyle}>{product.warehouseLocation || '-'}</td>);
    }

    if (viewOpts.availableStock || (!viewOpts.simpleView && !viewOpts.warehouseQty)) {
      cols.push(
        <td key="avail" style={{ ...tdRight, fontWeight:'700',
          color: (product.availableStock ?? 0) === 0 ? '#e53e3e' : '#000' }}>
          {(product.availableStock ?? 0).toLocaleString()}
        </td>
      );
    }

    if (!viewOpts.simpleView) {
      cols.push(<td key="res"   style={tdRight}>{(product.reservedStock ?? 0).toLocaleString()}</td>);
    }

    cols.push(
      <td key="total" style={{ ...tdRight, fontWeight:'700' }}>
        {(product.totalStock ?? 0).toLocaleString()}
      </td>
    );

    if (!viewOpts.simpleView) {
      cols.push(<td key="safe" style={tdRight}>{(product.safetyStock ?? 0).toLocaleString()}</td>);
    }

    if (viewOpts.todayOutbound) {
      cols.push(<td key="tin"  style={{ ...tdRight, color:'#38a169' }}>+{product.todayInbound  ?? 0}</td>);
      cols.push(<td key="tout" style={{ ...tdRight, color:'#e53e3e' }}>-{product.todayOutbound ?? 0}</td>);
    }

    cols.push(
      <td key="status" style={tdCenter}>
        <span style={{ padding:'0.2rem 0.6rem', borderRadius:'12px', fontSize:'0.75rem',
          fontWeight:'600', background:`${statusColor}20`, color: statusColor }}>
          {statusText}
        </span>
      </td>
    );

    if (!viewOpts.simpleView) {
      cols.push(<td key="pos" style={tdStyle}>{product.warehouseLocation || '-'}</td>);
    }

    return cols;
  };

  return (
    <div style={{ padding:'2rem', background:'#f5f5f5', minHeight:'100vh' }}>

      {/* 헤더 */}
      <div style={{ background:'#fff', padding:'1.5rem 2rem', marginBottom:'1.5rem',
        borderRadius:'8px', boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>
        <h1 style={{ fontSize:'1.75rem', fontWeight:'700', color:'#000', margin:0 }}>
          📊 재고 현황
        </h1>
      </div>

      {/* 통계 카드 */}
      <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(200px,1fr))',
        gap:'1rem', marginBottom:'1.5rem' }}>
        {[
          { label:'전체 상품',    value: stats.totalProducts.toLocaleString(),                     color:'#000' },
          { label:'총 재고 가치', value: stats.totalStockValue.toLocaleString() + '원',            color:'#000' },
          { label:'부족 상품',    value: stats.lowStockCount.toLocaleString(),                     color:'#dd6b20' },
          { label:'재고 없음',    value: stats.outOfStockCount.toLocaleString(),                   color:'#e53e3e' },
        ].map(s => (
          <div key={s.label} style={{ background:'#fff', padding:'1.5rem', borderRadius:'8px',
            boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>
            <div style={{ fontSize:'0.875rem', color:'#666', marginBottom:'0.5rem' }}>{s.label}</div>
            <div style={{ fontSize:'2rem', fontWeight:'700', color: s.color }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* 필터 & 검색 & 보기방식 */}
      <div style={{ background:'#fff', padding:'1.25rem 1.5rem', marginBottom:'1.5rem',
        borderRadius:'8px', boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>
        <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap', alignItems:'center' }}>

          {/* 필터 버튼 */}
          {[
            { type:'all',           label:'전체',      active:'#667eea' },
            { type:'low-stock',     label:'⚠️ 부족',   active:'#dd6b20' },
            { type:'out-of-stock',  label:'❌ 재고없음', active:'#e53e3e' },
          ].map(f => (
            <button key={f.type} onClick={() => handleFilterChange(f.type)}
              style={{
                padding:'0.5rem 1rem', borderRadius:'8px', cursor:'pointer', fontWeight:'600',
                fontSize:'0.875rem', border:'none',
                background: filterType===f.type ? f.active : '#edf2f7',
                color:      filterType===f.type ? '#fff' : '#333',
              }}>
              {f.label}
            </button>
          ))}

          {/* 검색 입력 */}
          <input type="text" value={searchKeyword}
            onChange={e => setSearchKeyword(e.target.value)}
            onKeyDown={e => e.key==='Enter' && handleSearch()}
            placeholder="상품명, SKU, 바코드 검색..."
            style={{ flex:1, minWidth:200, padding:'0.5rem 1rem',
              border:'2px solid #e2e8f0', borderRadius:'8px', fontSize:'0.9rem', outline:'none' }} />

          {/* 검색/초기화 */}
          <button onClick={handleSearch} disabled={loading}
            style={{ padding:'0.5rem 1.25rem', borderRadius:'8px', border:'none',
              background: loading?'#cbd5e0':'#667eea', color:'#fff', cursor:'pointer',
              fontWeight:'600', fontSize:'0.875rem', whiteSpace:'nowrap' }}>
            🔍 검색
          </button>
          <button onClick={handleReset}
            style={{ padding:'0.5rem 1rem', borderRadius:'8px', border:'1px solid #e2e8f0',
              background:'#fff', color:'#333', cursor:'pointer', fontSize:'0.875rem', whiteSpace:'nowrap' }}>
            초기화
          </button>

          {/* 보기방식 */}
          <ViewSelector viewOpts={viewOpts} onChange={handleViewChange} />
        </div>
      </div>

      {/* 상품 목록 */}
      {loading ? (
        <div style={{ background:'#fff', padding:'4rem', borderRadius:'12px', textAlign:'center',
          boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>
          <div style={{ fontSize:'3rem', marginBottom:'1rem' }}>⏳</div>
          <p style={{ color:'#333' }}>로딩 중...</p>
        </div>
      ) : products.length === 0 ? (
        <div style={{ background:'#fff', padding:'4rem', borderRadius:'12px', textAlign:'center',
          boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>
          <div style={{ fontSize:'3rem', marginBottom:'1rem' }}>📦</div>
          <p style={{ color:'#333', fontSize:'1.1rem' }}>상품이 없습니다</p>
        </div>
      ) : (
        <div style={{ background:'#fff', borderRadius:'12px', overflow:'hidden',
          boxShadow:'0 2px 4px rgba(0,0,0,0.1)' }}>

          {/* 결과 헤더 */}
          <div style={{ padding:'0.85rem 1.5rem', borderBottom:'1px solid #e2e8f0',
            background:'#f7fafc', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
            <p style={{ color:'#333', fontWeight:'600', margin:0, fontSize:'0.9rem' }}>
              검색 결과: {totalElements.toLocaleString()}개
              <span style={{ color:'#999', fontWeight:'400', marginLeft:'0.5rem' }}>
                ({page*PAGE_SIZE+1} ~ {Math.min((page+1)*PAGE_SIZE, totalElements)}번째)
              </span>
            </p>
            <span style={{ fontSize:'0.8rem', color:'#999' }}>페이지당 {PAGE_SIZE}개</span>
          </div>

          {/* 테이블 */}
          <div style={{ overflowX:'auto' }}>
            <table style={{ width:'100%', borderCollapse:'collapse', minWidth:600 }}>
              <thead>
                <tr>{renderHeaders()}</tr>
              </thead>
              <tbody>
                {products.map((product, idx) => (
                  <tr key={product.productId || idx}
                    style={{ background: idx%2===0 ? '#fff' : '#fafafa' }}
                    onMouseEnter={e  => e.currentTarget.style.background='#f0f4ff'}
                    onMouseLeave={e  => e.currentTarget.style.background = idx%2===0?'#fff':'#fafafa'}>
                    {renderRow(product)}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 페이지네이션 */}
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={handlePageChange}
          />
        </div>
      )}
    </div>
  );
}
