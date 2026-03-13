import { useState, useRef, useCallback } from 'react';

const API_INV = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') ||
                'https://oms-backend-production-8a38.up.railway.app/api/inventory';

// ─── 쇼핑몰 목록 ──────────────────────────────────────────────────────────
const CHANNELS = ['직접입력', '스마트스토어', '쿠팡', '11번가', '옥션', 'G마켓', '위메프', '티몬', '카카오', '롯데온', '기타'];

// ─── CSV 컬럼 정의 ────────────────────────────────────────────────────────
const CSV_COLUMNS = [
  { key: 'orderNo',       label: '주문번호',   required: true  },
  { key: 'channel',       label: '쇼핑몰',     required: false },
  { key: 'receiverName',  label: '수취인',     required: false },
  { key: 'receiverPhone', label: '연락처',     required: false },
  { key: 'address',       label: '주소',       required: false },
  { key: 'productName',   label: '상품명',     required: true  },
  { key: 'sku',           label: 'SKU',        required: false },
  { key: 'barcode',       label: '바코드',     required: false },
  { key: 'quantity',      label: '수량',       required: true  },
  { key: 'salePrice',     label: '판매가',     required: false },
  { key: 'optionName',    label: '옵션명',     required: false },
  { key: 'memo',          label: '메모',       required: false },
];

// ─── 공통 스타일 ──────────────────────────────────────────────────────────
const S = {
  page:    { padding: '2rem', background: '#f5f5f5', minHeight: '100vh' },
  topCard: { background: '#fff', borderRadius: '8px', marginBottom: '1.5rem', overflow: 'hidden' },
  card:    { background: '#fff', borderRadius: '8px', padding: '2rem', marginBottom: '1.5rem' },
  tabBar:  { display: 'flex', background: '#fff' },
  tab:     (on) => ({
    padding: '0.75rem 1.6rem', border: 'none', cursor: 'pointer',
    fontSize: '0.875rem', fontWeight: on ? 700 : 400,
    color: on ? '#1976d2' : '#555', background: on ? '#fff' : '#f5f5f5',
    borderBottom: on ? '2px solid #1976d2' : '2px solid transparent',
    marginBottom: '-2px', whiteSpace: 'nowrap',
  }),
  tbl:     { width: '100%', borderCollapse: 'collapse', border: '1px solid #ddd' },
  th:      { padding: '0.85rem 1rem', background: '#f5f5f5', border: '1px solid #ddd',
             fontWeight: 600, color: '#333', fontSize: '0.875rem',
             verticalAlign: 'middle', whiteSpace: 'nowrap', width: 150 },
  td:      { padding: '0.85rem 1rem', border: '1px solid #ddd', verticalAlign: 'middle' },
  dth:     { padding: '0.65rem 0.75rem', border: '1px solid #ddd', background: '#f5f5f5',
             fontSize: '0.8rem', fontWeight: 600, color: '#333', textAlign: 'center', whiteSpace: 'nowrap' },
  dtd:     { padding: '0.6rem 0.75rem', border: '1px solid #ddd', fontSize: '0.8rem',
             color: '#333', textAlign: 'center', whiteSpace: 'nowrap' },
  inp:     { padding: '0.45rem 0.6rem', border: '1px solid #ccc', borderRadius: '4px',
             fontSize: '0.875rem', color: '#333', outline: 'none', background: '#fff' },
  sel:     { padding: '0.45rem 0.6rem', border: '1px solid #ccc', borderRadius: '4px',
             fontSize: '0.875rem', color: '#333', background: '#fff', cursor: 'pointer' },
  bBlue:   { padding: '0.45rem 1.2rem', background: '#1976d2', color: '#fff', border: 'none',
             borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem', fontWeight: 600 },
  bGray:   { padding: '0.45rem 1.2rem', background: '#e0e0e0', color: '#333', border: 'none',
             borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem' },
  bRed:    { padding: '0.25rem 0.6rem', background: '#e53935', color: '#fff', border: 'none',
             borderRadius: '3px', cursor: 'pointer', fontSize: '0.75rem' },
  bBig:    { padding: '0.75rem 3rem', background: '#1565c0', color: '#fff', border: 'none',
             borderRadius: '4px', cursor: 'pointer', fontSize: '1rem', fontWeight: 700 },
  sec:     { display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700,
             fontSize: '0.95rem', color: '#1565c0', marginBottom: '1rem' },
  dot:     { width: 10, height: 10, borderRadius: '50%', background: '#1565c0', flexShrink: 0 },
};

const statusColor = (s) => ({ '신규':'#1976d2','처리중':'#f57c00','완료':'#2e7d32','취소':'#c62828' }[s] || '#666');

// ─── CSV 파싱 ─────────────────────────────────────────────────────────────
function parseCsv(text) {
  const lines = text.trim().split(/\r?\n/);
  if (lines.length < 2) return { headers: [], rows: [], allRows: [] };
  const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''));
  const allRows = lines.slice(1).map(line => {
    const cols = []; let cur = '', inQ = false;
    for (let i = 0; i < line.length; i++) {
      if (line[i] === '"') { inQ = !inQ; }
      else if (line[i] === ',' && !inQ) { cols.push(cur.trim()); cur = ''; }
      else { cur += line[i]; }
    }
    cols.push(cur.trim());
    return cols;
  }).filter(r => r.some(c => c));
  return { headers, rows: allRows.slice(0, 5), allRows };
}

function autoMapColumns(headers) {
  const mapping = {};
  const aliases = {
    orderNo:       ['주문번호','주문 번호','orderno','order_no','orderid'],
    channel:       ['쇼핑몰','채널','판매처','channel','mall'],
    receiverName:  ['수취인','받는분','수령인','수취인명','receiver','name'],
    receiverPhone: ['연락처','전화번호','휴대폰','phone','tel'],
    address:       ['주소','배송주소','수취인주소','address'],
    productName:   ['상품명','품명','제품명','product','productname','item'],
    sku:           ['sku','상품코드','품목코드'],
    barcode:       ['바코드','barcode','ean'],
    quantity:      ['수량','주문수량','qty','quantity','count'],
    salePrice:     ['판매가','단가','가격','금액','price'],
    optionName:    ['옵션','옵션명','옵션정보','option'],
    memo:          ['메모','비고','요청사항','memo','note'],
  };
  headers.forEach((h, idx) => {
    const lower = h.toLowerCase().replace(/\s/g, '');
    Object.entries(aliases).forEach(([key, aliasList]) => {
      if (!mapping[key] && aliasList.some(a => lower.includes(a))) mapping[key] = idx;
    });
  });
  return mapping;
}

// ═══════════════════════════════════════════════════════════════════════════
// 탭 1: 직접 입력
// ═══════════════════════════════════════════════════════════════════════════
function TabDirect({ onAdd }) {
  const empty = {
    orderNo:'', channel:'직접입력', receiverName:'', receiverPhone:'',
    address:'', productName:'', sku:'', barcode:'', quantity:'1',
    salePrice:'', optionName:'', memo:'',
  };
  const [form, setForm] = useState(empty);
  const [skuSearch, setSkuSearch] = useState('');
  const [skuResults, setSkuResults] = useState([]);
  const [skuLoading, setSkuLoading] = useState(false);
  const timer = useRef(null);
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const handleSkuInput = (val) => {
    setSkuSearch(val);
    clearTimeout(timer.current);
    if (val.length < 2) { setSkuResults([]); return; }
    timer.current = setTimeout(async () => {
      setSkuLoading(true);
      try {
        const res  = await fetch(`${API_INV}/products/search?keyword=${encodeURIComponent(val)}`);
        const data = await res.json();
        const list = Array.isArray(data) ? data : (data?.content || []);
        setSkuResults(list.slice(0, 8));
      } catch { setSkuResults([]); }
      finally { setSkuLoading(false); }
    }, 300);
  };

  const selectProduct = (p) => {
    set('productName', p.productName);
    set('sku',    p.sku    || '');
    set('barcode', p.barcode || '');
    setSkuSearch(''); setSkuResults([]);
  };

  const handleSubmit = () => {
    if (!form.orderNo.trim())     { alert('주문번호를 입력해주세요.'); return; }
    if (!form.productName.trim()) { alert('상품명을 입력해주세요.'); return; }
    if (parseInt(form.quantity) < 1) { alert('수량을 1 이상 입력해주세요.'); return; }
    onAdd([{ ...form, quantity: parseInt(form.quantity), salePrice: parseInt(form.salePrice)||0,
      status:'신규', createdAt: new Date().toISOString(), id: Date.now() }]);
    setForm(empty); setSkuSearch('');
    alert('✅ 주문이 추가되었습니다.');
  };

  return (
    <div style={S.card}>
      <div style={S.sec}><span style={S.dot}/>주문 직접 입력</div>
      <table style={S.tbl}>
        <tbody>
          <tr>
            <td style={S.th}>주문번호 <span style={{color:'#e53935'}}>*</span></td>
            <td style={S.td}>
              <input value={form.orderNo} onChange={e=>set('orderNo',e.target.value)}
                placeholder="예: 2024031500001" style={{...S.inp, width:220}} />
            </td>
            <td style={S.th}>쇼핑몰</td>
            <td style={S.td}>
              <select value={form.channel} onChange={e=>set('channel',e.target.value)}
                style={{...S.sel, minWidth:140}}>
                {CHANNELS.map(c=><option key={c}>{c}</option>)}
              </select>
            </td>
          </tr>
          <tr>
            <td style={S.th}>수취인</td>
            <td style={S.td}>
              <input value={form.receiverName} onChange={e=>set('receiverName',e.target.value)}
                placeholder="수취인 이름" style={{...S.inp, width:160}} />
            </td>
            <td style={S.th}>연락처</td>
            <td style={S.td}>
              <input value={form.receiverPhone} onChange={e=>set('receiverPhone',e.target.value)}
                placeholder="010-0000-0000" style={{...S.inp, width:160}} />
            </td>
          </tr>
          <tr>
            <td style={S.th}>배송주소</td>
            <td style={S.td} colSpan={3}>
              <input value={form.address} onChange={e=>set('address',e.target.value)}
                placeholder="배송 주소" style={{...S.inp, width:'100%'}} />
            </td>
          </tr>
          <tr>
            <td style={S.th}>상품 검색</td>
            <td style={S.td} colSpan={3}>
              <div style={{position:'relative'}}>
                <input value={skuSearch} onChange={e=>handleSkuInput(e.target.value)}
                  placeholder="상품명, SKU, 바코드로 검색하여 자동입력..."
                  style={{...S.inp, width:320}} />
                {skuLoading && <span style={{marginLeft:8,color:'#999',fontSize:'0.8rem'}}>검색 중...</span>}
                {skuResults.length > 0 && (
                  <div style={{position:'absolute',top:'100%',left:0,zIndex:999,background:'#fff',
                    border:'1px solid #ddd',borderRadius:4,boxShadow:'0 4px 12px rgba(0,0,0,0.1)',
                    minWidth:380,maxHeight:240,overflowY:'auto'}}>
                    {skuResults.map(p=>(
                      <div key={p.productId} onClick={()=>selectProduct(p)}
                        style={{padding:'0.6rem 1rem',cursor:'pointer',borderBottom:'1px solid #f5f5f5'}}
                        onMouseEnter={e=>e.currentTarget.style.background='#f0f4ff'}
                        onMouseLeave={e=>e.currentTarget.style.background='#fff'}>
                        <div style={{fontWeight:600,fontSize:'0.875rem',color:'#111'}}>{p.productName}</div>
                        <div style={{fontSize:'0.75rem',color:'#999',marginTop:2}}>
                          SKU: {p.sku} | 바코드: {p.barcode} | 재고: {p.totalStock}개
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </td>
          </tr>
          <tr>
            <td style={S.th}>상품명 <span style={{color:'#e53935'}}>*</span></td>
            <td style={S.td} colSpan={3}>
              <input value={form.productName} onChange={e=>set('productName',e.target.value)}
                placeholder="상품명 입력 또는 위에서 검색" style={{...S.inp, width:'100%'}} />
            </td>
          </tr>
          <tr>
            <td style={S.th}>SKU</td>
            <td style={S.td}>
              <input value={form.sku} onChange={e=>set('sku',e.target.value)}
                placeholder="SKU 코드" style={{...S.inp, width:160}} />
            </td>
            <td style={S.th}>바코드</td>
            <td style={S.td}>
              <input value={form.barcode} onChange={e=>set('barcode',e.target.value)}
                placeholder="바코드 번호" style={{...S.inp, width:160}} />
            </td>
          </tr>
          <tr>
            <td style={S.th}>옵션명</td>
            <td style={S.td} colSpan={3}>
              <input value={form.optionName} onChange={e=>set('optionName',e.target.value)}
                placeholder="예: 색상:빨강 / 사이즈:L" style={{...S.inp, width:'100%'}} />
            </td>
          </tr>
          <tr>
            <td style={S.th}>수량 <span style={{color:'#e53935'}}>*</span></td>
            <td style={S.td}>
              <input type="number" min="1" value={form.quantity}
                onChange={e=>set('quantity',e.target.value)}
                style={{...S.inp, width:80, textAlign:'center'}} />
            </td>
            <td style={S.th}>판매가</td>
            <td style={S.td}>
              <input type="number" value={form.salePrice} onChange={e=>set('salePrice',e.target.value)}
                placeholder="0" style={{...S.inp, width:120, textAlign:'right'}} />
              <span style={{marginLeft:4,fontSize:'0.875rem',color:'#666'}}>원</span>
            </td>
          </tr>
          <tr>
            <td style={S.th}>메모</td>
            <td style={S.td} colSpan={3}>
              <input value={form.memo} onChange={e=>set('memo',e.target.value)}
                placeholder="배송 메모 또는 특이사항" style={{...S.inp, width:'100%'}} />
            </td>
          </tr>
        </tbody>
      </table>
      <div style={{marginTop:'1.5rem', textAlign:'center'}}>
        <button onClick={handleSubmit} style={S.bBig}>✓ 주문 추가</button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 탭 2: CSV 업로드
// ═══════════════════════════════════════════════════════════════════════════
function TabCsv({ onAdd }) {
  const [file,    setFile]    = useState(null);
  const [preview, setPreview] = useState(null);
  const [mapping, setMapping] = useState({});
  const [channel, setChannel] = useState('스마트스토어');
  const [loading, setLoading] = useState(false);
  const [result,  setResult]  = useState(null);

  const handleFileChange = (f) => {
    if (!f) return;
    setFile(f); setResult(null);
    const reader = new FileReader();
    reader.onload = (e) => {
      const parsed = parseCsv(e.target.result);
      setMapping(autoMapColumns(parsed.headers));
      setPreview(parsed);
    };
    reader.readAsText(f, 'UTF-8');
  };

  const handleUpload = () => {
    if (!preview) { alert('파일을 먼저 선택해주세요.'); return; }
    setLoading(true);
    const orders = []; let skipped = 0;
    preview.allRows.forEach((cols, idx) => {
      const get = (key) => { const i = mapping[key]; return i !== undefined ? (cols[i]||'').trim() : ''; };
      const orderNo = get('orderNo'); const productName = get('productName');
      if (!orderNo && !productName) { skipped++; return; }
      orders.push({
        id: Date.now()+idx, orderNo: orderNo||`AUTO-${idx+1}`,
        channel: get('channel')||channel, receiverName: get('receiverName'),
        receiverPhone: get('receiverPhone'), address: get('address'),
        productName, sku: get('sku'), barcode: get('barcode'),
        quantity: parseInt(get('quantity'))||1, salePrice: parseInt(get('salePrice'))||0,
        optionName: get('optionName'), memo: get('memo'),
        status: '신규', createdAt: new Date().toISOString(),
      });
    });
    setTimeout(() => {
      onAdd(orders);
      setResult({ added: orders.length, skipped });
      setFile(null); setPreview(null); setLoading(false);
    }, 200);
  };

  const handleDownloadSample = () => {
    const header = CSV_COLUMNS.map(c=>c.label).join(',');
    const rows = [
      '20240315001,스마트스토어,홍길동,010-1234-5678,서울시 강남구 테헤란로 123,상품A,SKU001,8801234567890,2,15000,색상:빨강,문앞배송',
      '20240315002,쿠팡,김철수,010-9876-5432,부산시 해운대구 456,상품B,SKU002,,1,29000,,',
    ].join('\n');
    const blob = new Blob(['\uFEFF'+header+'\n'+rows], {type:'text/csv;charset=utf-8;'});
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download='주문입력_샘플.csv'; a.click();
  };

  return (
    <div>
      <div style={{...S.card, background:'#e3f2fd', border:'1px solid #90caf9', padding:'1.2rem 1.5rem'}}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',flexWrap:'wrap',gap:'0.75rem'}}>
          <div>
            <div style={{fontWeight:700,color:'#1565c0',marginBottom:'0.4rem'}}>📋 CSV 파일 업로드 안내</div>
            <div style={{fontSize:'0.875rem',color:'#333',lineHeight:1.9}}>
              1. 아래 <strong>샘플 CSV 다운로드</strong> 후 형식에 맞게 작성하세요.<br/>
              2. 필수 컬럼: <strong>주문번호, 상품명, 수량</strong> (나머지는 선택)<br/>
              3. 컬럼 순서가 달라도 헤더명 기준으로 <strong>자동 매핑</strong>됩니다.<br/>
              4. 파일 인코딩은 <strong>UTF-8</strong>로 저장해주세요.
            </div>
          </div>
          <button onClick={handleDownloadSample} style={{...S.bBlue,whiteSpace:'nowrap'}}>
            📥 샘플 CSV 다운로드
          </button>
        </div>
      </div>

      <div style={S.card}>
        <div style={S.sec}><span style={S.dot}/>파일 선택 및 업로드</div>
        <table style={S.tbl}>
          <tbody>
            <tr>
              <td style={S.th}>기본 쇼핑몰</td>
              <td style={S.td}>
                <select value={channel} onChange={e=>setChannel(e.target.value)}
                  style={{...S.sel,minWidth:140}}>
                  {CHANNELS.filter(c=>c!=='직접입력').map(c=><option key={c}>{c}</option>)}
                </select>
                <span style={{fontSize:'0.8rem',color:'#999',marginLeft:8}}>
                  CSV에 쇼핑몰 컬럼 없을 때 적용
                </span>
              </td>
            </tr>
            <tr>
              <td style={S.th}>CSV 파일</td>
              <td style={S.td}>
                <div style={{display:'flex',gap:'0.5rem',alignItems:'center'}}>
                  <label style={{...S.bGray,display:'inline-block',cursor:'pointer'}}>
                    파일 선택
                    <input type="file" accept=".csv,.txt"
                      onChange={e=>handleFileChange(e.target.files[0])}
                      style={{display:'none'}} />
                  </label>
                  <span style={{fontSize:'0.875rem',color:file?'#333':'#999'}}>
                    {file?file.name:'선택된 파일 없음'}
                  </span>
                  {file && <button onClick={()=>{setFile(null);setPreview(null);}} style={S.bGray}>취소</button>}
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        {preview && (
          <div style={{marginTop:'1.5rem'}}>
            <div style={{...S.sec,marginBottom:'0.75rem'}}>
              <span style={S.dot}/>컬럼 매핑 확인
              <span style={{fontSize:'0.8rem',color:'#999',fontWeight:400}}>
                — 자동 감지. 잘못된 경우 수동 변경하세요.
              </span>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(230px,1fr))',gap:'0.5rem',marginBottom:'1rem'}}>
              {CSV_COLUMNS.map(col=>(
                <div key={col.key} style={{display:'flex',alignItems:'center',gap:'0.5rem',fontSize:'0.8rem'}}>
                  <span style={{color:col.required?'#e53935':'#555',fontWeight:600,minWidth:65}}>
                    {col.label}{col.required&&' *'}
                  </span>
                  <select value={mapping[col.key]??''}
                    onChange={e=>setMapping(m=>({...m,[col.key]:e.target.value===''?undefined:parseInt(e.target.value)}))}
                    style={{...S.sel,fontSize:'0.78rem',padding:'0.25rem 0.4rem',flex:1}}>
                    <option value="">— 매핑 안함</option>
                    {preview.headers.map((h,i)=><option key={i} value={i}>{h}</option>)}
                  </select>
                </div>
              ))}
            </div>

            <div style={{...S.sec,marginBottom:'0.5rem'}}><span style={S.dot}/>미리보기 (최대 5행)</div>
            <div style={{overflowX:'auto',border:'1px solid #ddd',borderRadius:4}}>
              <table style={{...S.tbl,border:'none',minWidth:600}}>
                <thead>
                  <tr>{preview.headers.map((h,i)=><th key={i} style={S.dth}>{h}</th>)}</tr>
                </thead>
                <tbody>
                  {preview.rows.map((row,ri)=>(
                    <tr key={ri} style={{background:ri%2===0?'#fff':'#fafafa'}}>
                      {preview.headers.map((_,ci)=>(
                        <td key={ci} style={{...S.dtd,textAlign:'left'}}>{row[ci]||''}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{marginTop:'0.4rem',fontSize:'0.8rem',color:'#999'}}>
              전체 {preview.allRows.length}행 중 5행 미리보기
            </div>
          </div>
        )}

        {result && (
          <div style={{marginTop:'1rem',padding:'1rem',background:'#e8f5e9',
            border:'1px solid #a5d6a7',borderRadius:6,fontSize:'0.875rem'}}>
            ✅ 업로드 완료 — 추가 <strong>{result.added}</strong>건 |
            건너뜀 <strong style={{color:'#999'}}>{result.skipped}</strong>건
          </div>
        )}

        <div style={{marginTop:'1.5rem',textAlign:'center'}}>
          <button onClick={handleUpload} disabled={loading||!preview}
            style={{...S.bBig,opacity:(!preview||loading)?0.5:1,
              cursor:(!preview||loading)?'not-allowed':'pointer'}}>
            {loading?'처리 중...':`📤 주문 업로드${preview?` (${preview.allRows.length}건)`:''}`}
          </button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 탭 3: 주문 목록
// ═══════════════════════════════════════════════════════════════════════════
function TabList({ orders, onDelete, onStatusChange, onClear }) {
  const [search,   setSearch]   = useState('');
  const [chFilter, setChFilter] = useState('전체');
  const [stFilter, setStFilter] = useState('전체');
  const [page,     setPage]     = useState(0);
  const PAGE_SIZE = 50;

  const channels = ['전체', ...new Set(orders.map(o=>o.channel).filter(Boolean))];
  const statuses = ['전체','신규','처리중','완료','취소'];

  const filtered = orders.filter(o => {
    const kw = search.toLowerCase();
    const matchKw = !kw||[o.orderNo,o.productName,o.receiverName,o.sku].some(v=>v?.toLowerCase().includes(kw));
    return matchKw && (chFilter==='전체'||o.channel===chFilter) && (stFilter==='전체'||o.status===stFilter);
  });
  const paged = filtered.slice(page*PAGE_SIZE, (page+1)*PAGE_SIZE);
  const totalPages = Math.ceil(filtered.length/PAGE_SIZE);

  const handleExport = () => {
    const header = [...CSV_COLUMNS.map(c=>c.label),'상태','등록일시'].join(',');
    const rows = filtered.map(o=>[
      o.orderNo,o.channel,o.receiverName,o.receiverPhone,`"${o.address||''}"`,
      `"${o.productName}"`,o.sku,o.barcode,o.quantity,o.salePrice,
      `"${o.optionName||''}"`,`"${o.memo||''}"`,o.status,
      o.createdAt?new Date(o.createdAt).toLocaleString('ko-KR'):''].join(',')).join('\n');
    const blob = new Blob(['\uFEFF'+header+'\n'+rows],{type:'text/csv;charset=utf-8;'});
    const a = document.createElement('a');
    a.href=URL.createObjectURL(blob);
    a.download=`주문목록_${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
  };

  if (orders.length===0) return (
    <div style={{...S.card,textAlign:'center',padding:'4rem',color:'#bbb'}}>
      <div style={{fontSize:'3rem',marginBottom:'1rem'}}>📋</div>
      <div style={{fontSize:'1rem',color:'#666'}}>등록된 주문이 없습니다</div>
      <div style={{fontSize:'0.875rem',marginTop:'0.5rem'}}>직접입력 또는 CSV 업로드로 주문을 추가하세요</div>
    </div>
  );

  return (
    <div style={S.card}>
      <div style={{display:'flex',gap:'0.75rem',flexWrap:'wrap',alignItems:'center',marginBottom:'1rem'}}>
        <input value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}
          placeholder="주문번호, 상품명, 수취인 검색..."
          style={{...S.inp,flex:1,minWidth:200}} />
        <select value={chFilter} onChange={e=>{setChFilter(e.target.value);setPage(0);}} style={S.sel}>
          {channels.map(c=><option key={c}>{c}</option>)}
        </select>
        <select value={stFilter} onChange={e=>{setStFilter(e.target.value);setPage(0);}} style={S.sel}>
          {statuses.map(s=><option key={s}>{s}</option>)}
        </select>
        <button onClick={handleExport} style={S.bBlue}>📥 CSV 내보내기</button>
        <button onClick={()=>{if(window.confirm('전체 주문을 삭제하시겠습니까?'))onClear();}}
          style={{...S.bGray,color:'#e53935'}}>전체삭제</button>
      </div>
      <div style={{marginBottom:'0.75rem',fontSize:'0.875rem',color:'#555'}}>
        총 <strong>{filtered.length.toLocaleString()}</strong>건
        {filtered.length!==orders.length&&` (전체 ${orders.length.toLocaleString()}건)`}
      </div>

      <div style={{overflowX:'auto'}}>
        <table style={{...S.tbl,minWidth:900}}>
          <thead>
            <tr>
              {['주문번호','쇼핑몰','수취인','상품명','옵션','수량','판매가','상태','등록일시','삭제'].map(h=>(
                <th key={h} style={S.dth}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {paged.map((o,idx)=>(
              <tr key={o.id} style={{background:idx%2===0?'#fff':'#fafafa'}}
                onMouseEnter={e=>e.currentTarget.style.background='#f0f4ff'}
                onMouseLeave={e=>e.currentTarget.style.background=idx%2===0?'#fff':'#fafafa'}>
                <td style={{...S.dtd,fontWeight:600,color:'#1565c0'}}>{o.orderNo}</td>
                <td style={S.dtd}>{o.channel||'-'}</td>
                <td style={S.dtd}>{o.receiverName||'-'}</td>
                <td style={{...S.dtd,textAlign:'left',maxWidth:200,overflow:'hidden',textOverflow:'ellipsis'}}>
                  {o.productName}
                </td>
                <td style={{...S.dtd,maxWidth:120,overflow:'hidden',textOverflow:'ellipsis'}}>
                  {o.optionName||'-'}
                </td>
                <td style={S.dtd}>{o.quantity}</td>
                <td style={{...S.dtd,textAlign:'right'}}>
                  {o.salePrice?o.salePrice.toLocaleString()+'원':'-'}
                </td>
                <td style={S.dtd}>
                  <select value={o.status} onChange={e=>onStatusChange(o.id,e.target.value)}
                    style={{...S.sel,fontSize:'0.78rem',padding:'0.2rem 0.4rem',
                      color:statusColor(o.status),fontWeight:600,
                      border:`1px solid ${statusColor(o.status)}50`}}>
                    {['신규','처리중','완료','취소'].map(s=><option key={s}>{s}</option>)}
                  </select>
                </td>
                <td style={{...S.dtd,fontSize:'0.75rem',color:'#999'}}>
                  {o.createdAt?new Date(o.createdAt).toLocaleString('ko-KR',
                    {month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'−'}
                </td>
                <td style={S.dtd}>
                  <button onClick={()=>onDelete(o.id)} style={S.bRed}>삭제</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages>1 && (
        <div style={{display:'flex',justifyContent:'center',gap:'0.4rem',padding:'1rem',flexWrap:'wrap'}}>
          <button disabled={page===0} onClick={()=>setPage(0)}
            style={{...S.bGray,opacity:page===0?0.4:1,padding:'0.3rem 0.6rem'}}>«</button>
          <button disabled={page===0} onClick={()=>setPage(p=>p-1)}
            style={{...S.bGray,opacity:page===0?0.4:1,padding:'0.3rem 0.6rem'}}>‹</button>
          {Array.from({length:Math.min(totalPages,7)},(_,i)=>{
            const p=Math.max(0,Math.min(page-3,totalPages-7))+i;
            return <button key={p} onClick={()=>setPage(p)}
              style={{...p===page?S.bBlue:S.bGray,padding:'0.3rem 0.7rem'}}>{p+1}</button>;
          })}
          <button disabled={page>=totalPages-1} onClick={()=>setPage(p=>p+1)}
            style={{...S.bGray,opacity:page>=totalPages-1?0.4:1,padding:'0.3rem 0.6rem'}}>›</button>
          <button disabled={page>=totalPages-1} onClick={()=>setPage(totalPages-1)}
            style={{...S.bGray,opacity:page>=totalPages-1?0.4:1,padding:'0.3rem 0.6rem'}}>»</button>
          <span style={{fontSize:'0.8rem',color:'#666',padding:'0.3rem 0.5rem'}}>
            {page+1}/{totalPages}p
          </span>
        </div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// 메인 컴포넌트
// ═══════════════════════════════════════════════════════════════════════════
const TABS = [
  { key:'direct', label:'직접 입력' },
  { key:'csv',    label:'CSV 업로드' },
  { key:'list',   label:'주문 목록' },
];

let _globalOrders = [];

export default function OrderInput() {
  const [tab, setTab] = useState('direct');
  const [orders, setOrders] = useState(() => {
    try {
      const saved = sessionStorage.getItem('oms_orders');
      if (saved) { _globalOrders = JSON.parse(saved); return _globalOrders; }
    } catch {}
    return [];
  });

  const saveOrders = (list) => {
    _globalOrders = list;
    try { sessionStorage.setItem('oms_orders', JSON.stringify(list)); } catch {}
    setOrders([...list]);
  };

  const handleAdd = useCallback((newOrders) => {
    saveOrders([..._globalOrders, ...newOrders]);
    setTab('list');
  }, []);

  const handleDelete = useCallback((id) => {
    saveOrders(_globalOrders.filter(o=>o.id!==id));
  }, []);

  const handleStatusChange = useCallback((id, status) => {
    saveOrders(_globalOrders.map(o=>o.id===id?{...o,status}:o));
  }, []);

  const handleClear = useCallback(() => saveOrders([]), []);

  return (
    <div style={S.page}>
      <div style={S.topCard}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',
          padding:'1.5rem 2rem',borderBottom:'1px solid #e0e0e0'}}>
          <h1 style={{fontSize:'1.5rem',fontWeight:700,color:'#1a1a1a',margin:0}}>주문 입력</h1>
          <div style={{display:'flex',gap:'0.5rem',alignItems:'center'}}>
            <span style={{fontSize:'0.875rem',color:'#999'}}>등록된 주문</span>
            <span style={{fontSize:'1.1rem',fontWeight:700,color:'#1565c0'}}>
              {orders.length.toLocaleString()}건
            </span>
          </div>
        </div>
        <div style={{...S.tabBar,borderBottom:'2px solid #e0e0e0'}}>
          {TABS.map(t=>(
            <button key={t.key} onClick={()=>setTab(t.key)} style={S.tab(tab===t.key)}>
              {t.label}
              {t.key==='list' && orders.length>0 && (
                <span style={{marginLeft:6,background:'#1976d2',color:'#fff',
                  borderRadius:'10px',padding:'0 6px',fontSize:'0.72rem',fontWeight:700}}>
                  {orders.length}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {tab==='direct' && <TabDirect onAdd={handleAdd} />}
      {tab==='csv'    && <TabCsv    onAdd={handleAdd} />}
      {tab==='list'   && <TabList   orders={orders} onDelete={handleDelete}
                           onStatusChange={handleStatusChange} onClear={handleClear} />}
    </div>
  );
}
