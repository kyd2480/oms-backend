import { useState, useEffect, useRef } from 'react';

const API_INV = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') ||
                'https://oms-backend-production-8a38.up.railway.app/api/inventory';
const API_WH  = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/warehouses') ||
                'https://oms-backend-production-8a38.up.railway.app/api/warehouses';

// ─── 공통 스타일 ──────────────────────────────────────────────────────────
const S = {
  page:    { padding: '2rem', background: '#f5f5f5', minHeight: '100vh' },
  topCard: { background: '#fff', borderRadius: '8px', marginBottom: '1.5rem', overflow: 'hidden' },
  pageTitle:{ fontSize: '1.5rem', fontWeight: 700, color: '#1a1a1a', margin: 0,
              padding: '1.5rem 2rem', borderBottom: '1px solid #e0e0e0' },

  // 탭
  tabBar:  { display: 'flex', background: '#fff' },
  tab:     (on) => ({
    padding: '0.75rem 1.6rem', border: 'none', cursor: 'pointer',
    fontSize: '0.875rem', fontWeight: on ? 700 : 400,
    color: on ? '#1976d2' : '#555', background: on ? '#fff' : '#f5f5f5',
    borderBottom: on ? '2px solid #1976d2' : '2px solid transparent',
    marginBottom: '-2px', whiteSpace: 'nowrap',
  }),

  // 카드
  card:    { background: '#fff', borderRadius: '8px', padding: '2rem', marginBottom: '1.5rem' },

  // 폼 테이블
  tbl:     { width: '100%', borderCollapse: 'collapse', border: '1px solid #ddd' },
  th:      { padding: '0.85rem 1rem', background: '#f5f5f5', border: '1px solid #ddd',
              fontWeight: 600, color: '#333', fontSize: '0.875rem',
              verticalAlign: 'middle', whiteSpace: 'nowrap', width: 160 },
  td:      { padding: '0.85rem 1rem', border: '1px solid #ddd', verticalAlign: 'middle',
              fontSize: '0.875rem', color: '#333' },

  // 데이터 테이블
  dth:     { padding: '0.65rem', border: '1px solid #ddd', background: '#f5f5f5',
              fontSize: '0.875rem', fontWeight: 600, color: '#333', textAlign: 'center' },
  dtd:     { padding: '0.65rem', border: '1px solid #ddd', fontSize: '0.875rem',
              color: '#333', textAlign: 'center' },

  // 입력
  inp:     { padding: '0.45rem 0.6rem', border: '1px solid #ccc', borderRadius: '4px',
              fontSize: '0.875rem', color: '#333', outline: 'none', background: '#fff' },
  sel:     { padding: '0.45rem 0.6rem', border: '1px solid #ccc', borderRadius: '4px',
              fontSize: '0.875rem', color: '#333', background: '#fff', cursor: 'pointer' },

  // 버튼
  bBlue:   { padding: '0.45rem 1.2rem', background: '#1976d2', color: '#fff', border: 'none',
              borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem', fontWeight: 600 },
  bGray:   { padding: '0.45rem 1.2rem', background: '#e0e0e0', color: '#333', border: 'none',
              borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem' },
  bBig:    { padding: '0.75rem 3.5rem', background: '#1565c0', color: '#fff', border: 'none',
              borderRadius: '4px', cursor: 'pointer', fontSize: '1rem', fontWeight: 700 },
  bDel:    { padding: '0.2rem 0.5rem', background: '#e53935', color: '#fff', border: 'none',
              borderRadius: '3px', cursor: 'pointer', fontSize: '0.75rem' },

  // 섹션 제목
  sec:     { display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700,
              fontSize: '0.9rem', color: '#1565c0', marginBottom: '1rem' },
  dot:     { width: 10, height: 10, borderRadius: '50%', background: '#1565c0', flexShrink: 0 },
};

// ─── 창고 드롭다운 ─────────────────────────────────────────────────────────
function WhSelect({ list, value, onChange, placeholder = '(기본) 창고 선택', style }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      style={{ ...S.sel, minWidth: 170, ...style }}>
      <option value="">{placeholder}</option>
      {list.map(w => <option key={w.code} value={w.code}>{w.name}</option>)}
    </select>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 탭 A: CSV 창고이동
// ═══════════════════════════════════════════════════════════════════════════
function TabCsv({ whs }) {
  const today = new Date().toISOString().split('T')[0];
  const workTypes   = ['M to M', '교환', '반품', '재고조정', '이동'];
  const sortOptions = ['공급처순', '바코드순', '상품명순'];

  // 양식 다운로드
  const [dlFrom,   setDlFrom]   = useState('');
  const [dlSort,   setDlSort]   = useState('공급처순');
  const [dlFilter, setDlFilter] = useState({ noStock: true, noSale: true, zeroStock: true });

  // 업로드
  const [ul, setUl] = useState({
    moveDate: today, fromWh: '', toWh: '',
    workType: 'M to M', workMemo: '', stockCheck: true, file: null,
  });
  const [loading, setLoading] = useState(false);
  const [result,  setResult]  = useState(null);
  const set = (k, v) => setUl(f => ({ ...f, [k]: v }));

  // 양식 다운로드
  const handleDownload = async () => {
    if (!dlFrom) { alert('출발창고를 선택해주세요.'); return; }
    try {
      const p = new URLSearchParams({ warehouseCode: dlFrom, sortBy: dlSort,
        excludeNoStock: dlFilter.noStock, excludeNoSale: dlFilter.noSale,
        excludeZeroStock: dlFilter.zeroStock });
      const res = await fetch(`${API_INV}/transfer/excel-template?${p}`);
      if (!res.ok) throw new Error();
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `창고이동_양식_${today}.xlsx`;
      a.click();
    } catch { alert('다운로드 실패. 백엔드 API를 확인해주세요.'); }
  };

  // 파일 전송
  const handleUpload = async () => {
    if (!ul.file)  { alert('파일을 선택해주세요.'); return; }
    if (!ul.fromWh){ alert('출발창고를 선택해주세요.'); return; }
    if (!ul.toWh)  { alert('도착창고를 선택해주세요.'); return; }
    if (ul.fromWh === ul.toWh) { alert('출발/도착 창고가 동일합니다.'); return; }
    setLoading(true); setResult(null);
    try {
      const fd = new FormData();
      fd.append('file', ul.file);
      fd.append('fromWarehouse', ul.fromWh); fd.append('toWarehouse', ul.toWh);
      fd.append('moveDate', ul.moveDate);    fd.append('workType', ul.workType);
      fd.append('workMemo', ul.workMemo);    fd.append('stockCheck', ul.stockCheck);
      const res = await fetch(`${API_INV}/transfer/csv`, { method: 'POST', body: fd });
      if (!res.ok) throw new Error(await res.text() || '업로드 실패');
      setResult(await res.json());
      set('file', null);
    } catch (e) { alert('파일전송 실패: ' + e.message); }
    finally     { setLoading(false); }
  };

  return (
    <>
      {/* ── 주의사항 배너 ── */}
      <div style={{ ...S.card, background: '#fffde7', border: '1px solid #ffe082', padding: '1.2rem 1.5rem' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-start' }}>
          <div style={{ fontSize: '2.2rem', lineHeight: 1, flexShrink: 0 }}>⚠️</div>
          <div>
            <div style={{ fontWeight: 700, color: '#e65100', marginBottom: '0.4rem' }}>
              ● CSV창고이동 주의사항(필수)
            </div>
            <div style={{ fontSize: '0.875rem', color: '#444', lineHeight: 1.85 }}>
              1. 파일 형식을 반드시 <strong>CSV 형태로</strong> 변경하여 업로드 해주세요.{' '}
              <span style={{ color: '#1976d2', cursor: 'pointer', textDecoration: 'underline' }}>[변경방법보기]</span><br />
              2. <strong>바코드번호, 이동수량</strong> 열은 필수 항목입니다. (제목열에 해당이름이 존재해야 합니다.)<br />
              3. <strong>작업메모</strong> 열은 선택 항목입니다. (제목열은 존재해야 합니다.)
            </div>
          </div>
        </div>
      </div>

      {/* ── 양식 엑셀 다운로드 ── */}
      <div style={S.card}>
        <div style={S.sec}><span style={S.dot} />양식 엑셀 다운로드</div>
        <table style={S.tbl}>
          <tbody>
            <tr>
              <td style={S.th}>상품 상태 선택</td>
              <td style={S.td}>
                <div style={{ display: 'flex', gap: '1.2rem', flexWrap: 'wrap' }}>
                  {[['noStock','품절제외'],['noSale','미판매 제외'],['zeroStock','재고 없는 상품 제외']].map(([k,l]) => (
                    <label key={k} style={{ display:'flex', alignItems:'center', gap:5, cursor:'pointer' }}>
                      <input type="checkbox" checked={dlFilter[k]}
                        onChange={e => setDlFilter(f => ({...f,[k]:e.target.checked}))} />
                      {l}
                    </label>
                  ))}
                </div>
              </td>
            </tr>
            <tr>
              <td style={S.th}>출발창고 선택</td>
              <td style={S.td}>
                <WhSelect list={whs} value={dlFrom} onChange={setDlFrom} placeholder="(기본) 출발창고 선택" />
              </td>
            </tr>
            <tr>
              <td style={S.th}>양식 엑셀 정렬</td>
              <td style={S.td}>
                <div style={{ display:'flex', gap:'0.75rem', alignItems:'center' }}>
                  <select value={dlSort} onChange={e => setDlSort(e.target.value)}
                    style={{ ...S.sel, minWidth: 120 }}>
                    {sortOptions.map(o => <option key={o}>{o}</option>)}
                  </select>
                  <button onClick={handleDownload} style={S.bBlue}>📥 엑셀다운로드</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      {/* ── 창고이동 엑셀 업로드 ── */}
      <div style={S.card}>
        <div style={S.sec}><span style={S.dot} />창고이동 엑셀 업로드</div>
        <table style={S.tbl}>
          <tbody>
            <tr>
              <td style={S.th}>이동일자</td>
              <td style={S.td}>
                <input type="date" value={ul.moveDate} onChange={e => set('moveDate', e.target.value)}
                  style={S.inp} />
              </td>
              <td style={S.th}>재고 체크</td>
              <td style={S.td}>
                <label style={{ display:'flex', alignItems:'center', gap:6, cursor:'pointer' }}>
                  <input type="checkbox" checked={ul.stockCheck}
                    onChange={e => set('stockCheck', e.target.checked)} />
                  (재고가 0미만으로 출고 불가)
                </label>
              </td>
            </tr>
            <tr>
              <td style={S.th}>출발창고 선택</td>
              <td style={S.td}>
                <WhSelect list={whs} value={ul.fromWh} onChange={v => set('fromWh', v)} />
              </td>
              <td style={S.th}>도착창고 선택</td>
              <td style={S.td}>
                <WhSelect list={whs} value={ul.toWh} onChange={v => set('toWh', v)} />
              </td>
            </tr>
            <tr>
              <td style={S.th}>작업 구분</td>
              <td style={S.td}>
                <select value={ul.workType} onChange={e => set('workType', e.target.value)}
                  style={{ ...S.sel, minWidth: 120 }}>
                  {workTypes.map(t => <option key={t}>{t}</option>)}
                </select>
              </td>
              <td style={S.th}>작업 메모</td>
              <td style={S.td}>
                <input type="text" value={ul.workMemo} onChange={e => set('workMemo', e.target.value)}
                  style={{ ...S.inp, width: '100%' }} />
              </td>
            </tr>
            <tr>
              <td style={S.th}>창고이동 엑셀파일 입력</td>
              <td style={S.td} colSpan={3}>
                <div style={{ display:'flex', gap:'0.5rem', alignItems:'center' }}>
                  <label style={{ ...S.bGray, display:'inline-block', cursor:'pointer', lineHeight:'normal' }}>
                    파일 선택
                    <input type="file" accept=".csv,.xlsx,.xls"
                      onChange={e => set('file', e.target.files[0])} style={{ display:'none' }} />
                  </label>
                  <span style={{ fontSize:'0.875rem', color:'#999' }}>
                    {ul.file ? ul.file.name : '선택된 파일 없음'}
                  </span>
                  {ul.file && (
                    <button onClick={() => set('file', null)} style={S.bGray}>취소</button>
                  )}
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        {result && (
          <div style={{ marginTop:'1rem', padding:'1rem', background:'#e8f5e9',
            border:'1px solid #a5d6a7', borderRadius:6, fontSize:'0.875rem' }}>
            ✅ 처리완료 &nbsp;—&nbsp; 성공 <strong>{result.successCount}</strong>건 |
            실패 <strong style={{ color:'#d32f2f' }}>{result.failCount}</strong>건
            {result.failItems?.length > 0 && (
              <div style={{ color:'#d32f2f', marginTop:4 }}>
                실패 바코드: {result.failItems.join(', ')}
              </div>
            )}
          </div>
        )}

        <div style={{ marginTop:'1.5rem', textAlign:'center' }}>
          <button onClick={handleUpload}
            disabled={loading || !ul.file || !ul.fromWh || !ul.toWh}
            style={{ ...S.bBig,
              opacity: (!ul.file||!ul.fromWh||!ul.toWh||loading) ? 0.5 : 1,
              cursor:  (!ul.file||!ul.fromWh||!ul.toWh||loading) ? 'not-allowed':'pointer' }}>
            {loading ? '처리 중...' : '파일전송'}
          </button>
        </div>
      </div>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 탭 B: 바코드 창고이동
// ═══════════════════════════════════════════════════════════════════════════
function TabBarcode({ whs }) {
  const today     = new Date().toISOString().split('T')[0];
  const workTypes = ['M to M', '교환', '반품', '재고조정', '이동'];

  const [form, setForm] = useState({
    moveDate: today, workType: 'M to M', qty: 1,
    fromWh: '', toWh: '', workMemo: '', stockCheck: true, barcode: '',
  });
  const [moveList, setMoveList] = useState([]);
  const [loading,  setLoading]  = useState(false);
  const barcodeRef = useRef(null);
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const handleMove = async () => {
    const bc = form.barcode.trim();
    if (!bc) return;
    if (!form.fromWh || !form.toWh) { alert('출발/도착 창고를 모두 선택해주세요.'); return; }
    if (form.fromWh === form.toWh)  { alert('출발창고와 도착창고가 동일합니다.'); return; }

    setLoading(true);
    try {
      // 상품 조회
      const res = await fetch(
        `${API_INV}/products/search?keyword=${encodeURIComponent(bc)}`
      );
      const products = await res.json();
      const product = products?.[0];
      if (!product) { alert('⚠️ 해당 바코드의 상품을 찾을 수 없습니다.'); setLoading(false); return; }

      // 재고 체크
      if (form.stockCheck) {
        let stock = product.totalStock;
        switch (form.fromWh) {
          case 'ANYANG':     stock = product.warehouseStockAnyang  ?? 0; break;
          case 'ICHEON_BOX':
          case 'ICHEON_PCS': stock = product.warehouseStockIcheon  ?? 0; break;
          case 'BUCHEON':    stock = product.warehouseStockBucheon ?? 0; break;
        }
        if (form.qty > stock) {
          alert(`출발창고 재고(${stock}개)가 이동수량(${form.qty}개)보다 적습니다.`);
          setLoading(false); return;
        }
      }

      const note = `창고이동 | 작업:${form.workType}${form.workMemo ? ' | ' + form.workMemo : ''}`;
      await fetch(`${API_INV}/outbound-warehouse`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ productId: product.productId, quantity: form.qty,
          warehouse: form.fromWh, notes: `${note} | 도착:${form.toWh}` }),
      });
      await fetch(`${API_INV}/inbound-warehouse`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ productId: product.productId, quantity: form.qty,
          warehouse: form.toWh, notes: `${note} | 출발:${form.fromWh}` }),
      });

      const fn = whs.find(w => w.code === form.fromWh)?.name || form.fromWh;
      const tn = whs.find(w => w.code === form.toWh)?.name   || form.toWh;
      setMoveList(prev => [{
        id: Date.now(), barcode: bc, name: product.productName,
        from: fn, to: tn, qty: form.qty,
        time: new Date().toLocaleTimeString('ko-KR'), ok: true,
      }, ...prev]);

      set('barcode', '');
      setTimeout(() => barcodeRef.current?.focus(), 80);
    } catch (e) {
      setMoveList(prev => [{
        id: Date.now(), barcode: bc, name: '처리 실패',
        from: '-', to: '-', qty: form.qty,
        time: new Date().toLocaleTimeString('ko-KR'), ok: false,
      }, ...prev]);
    } finally { setLoading(false); }
  };

  return (
    <>
      <div style={S.card}>
        <div style={S.sec}><span style={S.dot} />바코드 창고이동</div>
        <table style={S.tbl}>
          <tbody>
            <tr>
              <td style={S.th}>이동일자</td>
              <td style={S.td}>
                <input type="date" value={form.moveDate}
                  onChange={e => set('moveDate', e.target.value)} style={S.inp} />
              </td>
              <td style={S.th}>재고 체크</td>
              <td style={S.td}>
                <label style={{ display:'flex', alignItems:'center', gap:6, cursor:'pointer' }}>
                  <input type="checkbox" checked={form.stockCheck}
                    onChange={e => set('stockCheck', e.target.checked)} />
                  (재고가 0미만으로 출고 불가)
                </label>
              </td>
            </tr>
            <tr>
              <td style={S.th}>작업 구분</td>
              <td style={S.td}>
                <select value={form.workType} onChange={e => set('workType', e.target.value)}
                  style={{ ...S.sel, minWidth: 120 }}>
                  {workTypes.map(t => <option key={t}>{t}</option>)}
                </select>
              </td>
              <td style={S.th}>이동 수량</td>
              <td style={S.td}>
                <input type="number" min="1" value={form.qty}
                  onChange={e => set('qty', Math.max(1, parseInt(e.target.value)||1))}
                  style={{ ...S.inp, width: 80, textAlign:'center' }} />
              </td>
            </tr>
            <tr>
              <td style={S.th}>출발창고 선택</td>
              <td style={S.td}>
                <div style={{ display:'flex', alignItems:'center', gap:6 }}>
                  <WhSelect list={whs} value={form.fromWh} onChange={v => set('fromWh', v)}
                    placeholder="(기본) 출발창고" />
                  {form.fromWh && (
                    <button onClick={() => set('fromWh', '')}
                      style={{ border:'1px solid #ccc', borderRadius:3, background:'#fff',
                        cursor:'pointer', fontSize:'0.75rem', padding:'2px 6px' }}>×</button>
                  )}
                </div>
              </td>
              <td style={S.th}>도착창고 선택</td>
              <td style={S.td}>
                <div style={{ display:'flex', alignItems:'center', gap:6 }}>
                  <WhSelect list={whs} value={form.toWh} onChange={v => set('toWh', v)}
                    placeholder="(기본) 도착창고" />
                  {form.toWh && (
                    <button onClick={() => set('toWh', '')}
                      style={{ border:'1px solid #ccc', borderRadius:3, background:'#fff',
                        cursor:'pointer', fontSize:'0.75rem', padding:'2px 6px' }}>×</button>
                  )}
                </div>
              </td>
            </tr>
            <tr>
              <td style={S.th}>바코드번호 입력</td>
              <td style={S.td} colSpan={1}>
                <div style={{ display:'flex', gap:'0.5rem', alignItems:'center' }}>
                  <input
                    ref={barcodeRef}
                    type="text" value={form.barcode} autoFocus
                    onChange={e => set('barcode', e.target.value)}
                    onKeyDown={e => e.key==='Enter' && (e.preventDefault(), handleMove())}
                    placeholder="바코드 스캔"
                    style={{ ...S.inp, minWidth: 200, background: '#fff9c4' }} />
                  <button onClick={handleMove} disabled={loading} style={S.bBlue}>
                    {loading ? '처리중...' : '이동하기'}
                  </button>
                </div>
              </td>
              <td style={S.th}>작업 메모</td>
              <td style={S.td}>
                <input type="text" value={form.workMemo}
                  onChange={e => set('workMemo', e.target.value)}
                  style={{ ...S.inp, width:'100%' }} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      {/* 이동 품목 리스트 */}
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:'1rem' }}>
          <div style={S.sec}><span style={S.dot} />이동 품목 리스트</div>
          {moveList.length > 0 && (
            <button onClick={() => setMoveList([])} style={S.bGray}>목록 초기화</button>
          )}
        </div>
        <table style={S.tbl}>
          <thead>
            <tr>
              {['시간','바코드','상품명','출발창고','도착창고','수량','상태'].map(h => (
                <th key={h} style={S.dth}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {moveList.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ ...S.dtd, padding:'3rem', color:'#bbb' }}>
                  바코드를 스캔하면 이동 내역이 표시됩니다
                </td>
              </tr>
            ) : moveList.map(item => (
              <tr key={item.id} style={{ background: item.ok ? '#fff' : '#fff5f5' }}>
                <td style={S.dtd}>{item.time}</td>
                <td style={S.dtd}>{item.barcode}</td>
                <td style={{ ...S.dtd, textAlign:'left' }}>{item.name}</td>
                <td style={S.dtd}>{item.from}</td>
                <td style={S.dtd}>{item.to}</td>
                <td style={S.dtd}>{item.qty}개</td>
                <td style={S.dtd}>
                  {item.ok
                    ? <span style={{ color:'#2e7d32', fontWeight:700 }}>완료</span>
                    : <span style={{ color:'#d32f2f', fontWeight:700 }}>실패</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {moveList.length > 0 && (
          <div style={{ textAlign:'right', marginTop:'0.5rem', fontSize:'0.875rem', color:'#666' }}>
            총 <strong>{moveList.length}</strong>건 |
            완료 <strong style={{ color:'#2e7d32' }}>{moveList.filter(i=>i.ok).length}</strong>건
            {moveList.some(i=>!i.ok) && (
              <> | 실패 <strong style={{ color:'#d32f2f' }}>{moveList.filter(i=>!i.ok).length}</strong>건</>
            )}
          </div>
        )}
      </div>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 메인 컴포넌트
// ═══════════════════════════════════════════════════════════════════════════
const TABS = [
  { key: 'csv',     label: 'CSV 창고이동' },
  { key: 'barcode', label: '바코드 창고이동' },
];

export default function WarehouseTransfer() {
  const [tab,      setTab]      = useState('csv');
  const [whs,      setWhs]      = useState([]);
  const [whLoad,   setWhLoad]   = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const res  = await fetch(`${API_WH}/active`);
        const data = await res.json();
        setWhs(data);
      } catch { console.error('창고 목록 로드 실패'); }
      finally   { setWhLoad(false); }
    })();
  }, []);

  return (
    <div style={S.page}>
      <div style={S.topCard}>
        <h1 style={S.pageTitle}>상품 창고 이동</h1>
        <div style={{ ...S.tabBar, borderBottom: '2px solid #e0e0e0' }}>
          {TABS.map(t => (
            <button key={t.key} onClick={() => setTab(t.key)} style={S.tab(tab===t.key)}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {whLoad ? (
        <div style={{ ...S.card, textAlign:'center', color:'#999', padding:'3rem' }}>
          창고 목록 불러오는 중...
        </div>
      ) : (
        <>
          {tab === 'csv'     && <TabCsv     whs={whs} />}
          {tab === 'barcode' && <TabBarcode whs={whs} />}
        </>
      )}
    </div>
  );
}
