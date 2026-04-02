import { useState, useEffect, useRef } from 'react';

const API = (import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
             'https://oms-backend-production-8a38.up.railway.app') + '/api/invoice';

// ── JsBarcode 로드 ───────────────────────────────────────────────
function useJsBarcode() {
  const [loaded, setLoaded] = useState(!!window.JsBarcode);
  useEffect(() => {
    if (window.JsBarcode) { setLoaded(true); return; }
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js';
    s.onload = () => setLoaded(true);
    document.head.appendChild(s);
  }, []);
  return loaded;
}

function Barcode({ value, height = 50, width = 2, showText = true }) {
  const ref = useRef();
  const loaded = useJsBarcode();
  useEffect(() => {
    if (!loaded || !value || !ref.current) return;
    try {
      window.JsBarcode(ref.current, value, {
        format: 'CODE128', width, height,
        displayValue: showText, fontSize: 10, margin: 2,
        lineColor: '#000', background: 'transparent',
      });
    } catch(e) {}
  }, [loaded, value, height, width, showText]);
  if (!value) return <div style={{height, display:'flex',alignItems:'center',justifyContent:'center',fontSize:9,color:'#aaa',border:'1px dashed #ccc'}}>송장번호없음</div>;
  return <svg ref={ref} style={{display:'block',width:'100%'}} />;
}

// ── 실제 우체국 송장 컴포넌트 ────────────────────────────────────
function PostalInvoice({ order }) {
  const today = new Date().toLocaleDateString('ko-KR',{year:'numeric',month:'2-digit',day:'2-digit'}).replace(/\. /g,'-').replace('.','');
  const products = (order.productName||'').split(',').map(p=>p.trim()).filter(Boolean);

  const cellStyle = (extra={}) => ({
    border:'1px solid #000',
    padding:'1.5mm 2mm',
    boxSizing:'border-box',
    ...extra
  });

  return (
    <div style={{
      width:'111mm', minHeight:'272mm',
      fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif",
      fontSize:'7.5pt', color:'#000', background:'#fff',
      border:'1px solid #000', boxSizing:'border-box',
      pageBreakAfter:'always', display:'flex', flexDirection:'column',
    }}>

      {/* ① 최상단: 로고 + 분류 + 날짜 */}
      <div style={{ display:'flex', borderBottom:'1.5px solid #000', minHeight:'12mm' }}>
        {/* 좌: 날짜 + 로고 */}
        <div style={{ width:'28mm', borderRight:'1px solid #000', padding:'1mm 2mm',
          display:'flex', flexDirection:'column', justifyContent:'space-between' }}>
          <div style={{ fontSize:'6.5pt', color:'#555' }}>발송일 {today}</div>
          <div style={{ display:'flex', alignItems:'center', gap:'1mm' }}>
            <svg width="14" height="14" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="45" fill="#c00" />
              <text x="50" y="68" textAnchor="middle" fill="#fff" fontSize="52" fontWeight="bold">우</text>
            </svg>
            <div>
              <div style={{ fontWeight:900, fontSize:'8pt', color:'#c00' }}>우체국택배</div>
              <div style={{ fontSize:'5.5pt', color:'#666' }}>EMS·ePost</div>
            </div>
          </div>
        </div>

        {/* 중: 분류 코드 */}
        <div style={{ flex:1, borderRight:'1px solid #000', padding:'1mm 2mm',
          display:'flex', flexDirection:'column', justifyContent:'center', alignItems:'center' }}>
          <div style={{ fontSize:'5.5pt', color:'#888', marginBottom:'0.5mm' }}>부1(부산M)</div>
          <div style={{ fontSize:'16pt', fontWeight:900, letterSpacing:'1px', lineHeight:1 }}>605</div>
          <div style={{ fontSize:'9pt', fontWeight:700 }}>부산연제</div>
          <div style={{ fontSize:'7pt', color:'#555' }}>01  42</div>
        </div>

        {/* 우: 등기번호 */}
        <div style={{ width:'38mm', padding:'1mm 2mm',
          display:'flex', flexDirection:'column', justifyContent:'space-between' }}>
          <div style={{ fontSize:'5.5pt', color:'#888' }}>등기번호</div>
          <div style={{ fontWeight:700, fontSize:'7.5pt', wordBreak:'break-all' }}>
            {order.trackingNo || '(미입력)'}
          </div>
          <div style={{ fontSize:'5.5pt', color:'#888', marginTop:'0.5mm' }}>
            주문번호: {order.orderNo}
          </div>
        </div>
      </div>

      {/* ② 메인 섹션: 좌(받는분) + 우(상품내역) */}
      <div style={{ display:'flex', borderBottom:'2px solid #000', flex:1 }}>

        {/* 좌: 보내는분 + 받는분 */}
        <div style={{ width:'62mm', borderRight:'1px solid #000', display:'flex', flexDirection:'column' }}>

          {/* 보내는분 */}
          <div style={{ borderBottom:'1px dashed #000', padding:'1.5mm 2mm', minHeight:'18mm' }}>
            <div style={{ fontSize:'6pt', color:'#888', marginBottom:'0.5mm' }}>보내는 분</div>
            <div style={{ fontWeight:700, fontSize:'8pt' }}>젝시믹스(안양)</div>
            <div style={{ fontSize:'6.5pt', lineHeight:1.4, color:'#333' }}>
              경기도 안양시 동안구<br/>시민대로110번길 20
            </div>
            <div style={{ fontSize:'6.5pt', color:'#555', marginTop:'0.5mm' }}>
              T : 1661-2811
            </div>
          </div>

          {/* 받는분 */}
          <div style={{ padding:'1.5mm 2mm', flex:1 }}>
            <div style={{ fontSize:'6pt', color:'#888', marginBottom:'0.5mm' }}>받는 분</div>
            <div style={{ fontSize:'7pt', lineHeight:1.5, color:'#333', marginBottom:'1mm' }}>
              {order.address}
            </div>
            <div style={{ fontSize:'7pt', color:'#444', marginBottom:'2mm' }}>
              T : {order.recipientPhone}
            </div>
            <div style={{ fontSize:'16pt', fontWeight:900, letterSpacing:'1px' }}>
              {order.recipientName}
            </div>
          </div>
        </div>

        {/* 우: 상품내역 */}
        <div style={{ flex:1, padding:'1.5mm 2mm', display:'flex', flexDirection:'column' }}>
          <div style={{ fontSize:'6pt', color:'#888', marginBottom:'1mm', fontWeight:700 }}>
            상품 내역서 순번
          </div>

          {/* 등기번호 */}
          <div style={{ marginBottom:'1.5mm' }}>
            <div style={{ fontSize:'6pt', color:'#888' }}>등기번호:{order.trackingNo||'-'}</div>
            <div style={{ fontSize:'6pt', color:'#888', wordBreak:'break-all' }}>
              주문번호:{order.orderNo}
            </div>
          </div>

          {/* 상품 목록 */}
          <div style={{ fontSize:'7pt', lineHeight:1.6, flex:1 }}>
            {products.map((p, i) => (
              <div key={i} style={{ display:'flex', gap:'1mm', marginBottom:'0.3mm' }}>
                <span style={{ color:'#888', flexShrink:0 }}>[.{String.fromCharCode(65+i)}F-{String(i+1).padStart(3,'0')}-{i+1}-{i+1}]</span>
                <span style={{ wordBreak:'break-all' }}>{p}</span>
                <span style={{ flexShrink:0, marginLeft:'auto', fontWeight:600 }}>1</span>
              </div>
            ))}
          </div>

          <div style={{ marginTop:'1mm', fontWeight:700, fontSize:'7.5pt', borderTop:'1px solid #ccc', paddingTop:'1mm' }}>
            [총{order.quantity}개]
          </div>
        </div>
      </div>

      {/* ③ 큰 바코드 영역 */}
      <div style={{ padding:'2mm', borderBottom:'1px solid #000', background:'#f9f9f9' }}>
        <div style={{ fontSize:'6pt', color:'#888', textAlign:'center', marginBottom:'1mm' }}>
          등기번호 : {order.trackingNo||'미입력'}
        </div>
        <Barcode value={order.trackingNo} height={45} width={2} showText={false} />
        <div style={{ textAlign:'center', fontSize:'9pt', fontWeight:700, letterSpacing:'3px', marginTop:'0.5mm' }}>
          {order.trackingNo}
        </div>
      </div>

      {/* ④ 하단: 보내는분 + 소형 바코드 */}
      <div style={{ display:'flex', borderBottom:'1px solid #ccc', minHeight:'18mm' }}>
        {/* 좌: 보내는분 반복 */}
        <div style={{ flex:1, borderRight:'1px solid #ccc', padding:'1.5mm 2mm' }}>
          <div style={{ fontSize:'5.5pt', color:'#888' }}>보내는분</div>
          <div style={{ fontWeight:700, fontSize:'7.5pt' }}>젝시믹스(안양)</div>
          <div style={{ fontSize:'6pt', color:'#444' }}>경기도 안양시 동안구 시민대로110번길 20</div>
          <div style={{ fontSize:'6pt', color:'#444' }}>T : 1661-2811</div>
        </div>
        {/* 우: 작은 바코드 */}
        <div style={{ width:'45mm', padding:'1.5mm 2mm', display:'flex', flexDirection:'column', justifyContent:'center' }}>
          <Barcode value={order.trackingNo} height={22} width={1.2} showText={true} />
        </div>
      </div>

      {/* ⑤ 최하단 안내 */}
      <div style={{ padding:'1mm 2mm', display:'flex', justifyContent:'space-between', alignItems:'center' }}>
        <div style={{ fontSize:'5.5pt', color:'#999' }}>
          개인정보 유출방지를 위하여 운송장을 제거하십시오
        </div>
        <div style={{ fontSize:'6pt', color:'#555', textAlign:'right' }}>
          신청 및 배달문의<br/>☎ 1588-1300
        </div>
      </div>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────
export default function InvoicePrint() {
  const [orders,   setOrders]   = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [search,   setSearch]   = useState('');
  const [selected, setSelected] = useState(new Set());
  const printRef = useRef();

  useEffect(() => {
    fetch(`${API}/completed`)
      .then(r => r.json())
      .then(data => setOrders(Array.isArray(data) ? data : []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const handlePrint = () => {
    const content = printRef.current.innerHTML;
    const w = window.open('', '_blank');
    w.document.write(`<!DOCTYPE html><html><head>
      <meta charset="utf-8"><title>송장출력</title>
      <script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js"><\/script>
      <style>
        @page { size: 111mm 272mm; margin: 0; }
        * { box-sizing: border-box; }
        body { margin: 0; padding: 0; background: #fff; }
        @media print { body { -webkit-print-color-adjust: exact; color-adjust: exact; } }
      </style>
    </head><body>
      ${content}
      <script>
        window.onload = function() {
          document.querySelectorAll('svg[data-val]').forEach(function(el) {
            var v = el.getAttribute('data-val');
            if (v && window.JsBarcode) {
              try { JsBarcode(el, v, {format:'CODE128',width:2,height:45,displayValue:false,margin:2}); } catch(e) {}
            }
          });
          setTimeout(function(){ window.print(); }, 800);
        };
      <\/script>
    </body></html>`);
    w.document.close();
  };

  const toggle = (no) => setSelected(prev => { const n=new Set(prev); n.has(no)?n.delete(no):n.add(no); return n; });
  const filtered = orders.filter(o => {
    if (!search) return true;
    const kw = search.toLowerCase();
    return [o.orderNo,o.recipientName,o.trackingNo,o.productName].some(v=>v?.toLowerCase().includes(kw));
  });
  const targets = filtered.filter(o => selected.size===0 || selected.has(o.orderNo));

  const S = {
    page:  { padding:'2rem', background:'#f5f5f5', minHeight:'100vh', color:'#111' },
    card:  { background:'#fff', borderRadius:'8px', padding:'2rem', marginBottom:'1.5rem', boxShadow:'0 1px 4px rgba(0,0,0,0.06)', color:'#111' },
    th:    { padding:'0.6rem 0.75rem', background:'#f5f5f5', border:'1px solid #e0e0e0', fontSize:'0.78rem', fontWeight:700, color:'#444', textAlign:'center', whiteSpace:'nowrap' },
    td:    { padding:'0.55rem 0.75rem', border:'1px solid #e0e0e0', fontSize:'0.8rem', color:'#333', textAlign:'center', whiteSpace:'nowrap' },
    inp:   { padding:'0.4rem 0.6rem', border:'1px solid #ccc', borderRadius:'4px', fontSize:'0.85rem', outline:'none', color:'#111' },
    bBlue: { padding:'0.45rem 1.2rem', background:'#1976d2', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem', fontWeight:600 },
    bGray: { padding:'0.45rem 1.2rem', background:'#e0e0e0', color:'#333', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'0.875rem' },
  };

  return (
    <div style={S.page}>
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', flexWrap:'wrap', gap:'1rem' }}>
          <div>
            <h1 style={{ fontSize:'1.5rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 0.25rem' }}>송장 인쇄</h1>
            <div style={{ fontSize:'0.875rem', color:'#666' }}>111mm × 272mm 우체국 택배 송장</div>
          </div>
          <div style={{ display:'flex', gap:'0.75rem', alignItems:'center', flexWrap:'wrap' }}>
            <input value={search} onChange={e=>setSearch(e.target.value)}
              placeholder="주문번호, 수취인, 송장번호..."
              style={{...S.inp, width:220}} />
            <button onClick={()=>setSelected(new Set(filtered.map(o=>o.orderNo)))} style={S.bGray}>전체선택</button>
            <button onClick={()=>setSelected(new Set())} style={S.bGray}>선택해제</button>
            <button onClick={handlePrint} disabled={!targets.length}
              style={{...S.bBlue, opacity:!targets.length?0.5:1}}>
              🖨️ {selected.size>0?`선택 ${selected.size}건`:`전체 ${filtered.length}건`} 인쇄
            </button>
          </div>
        </div>
      </div>

      {loading ? (
        <div style={{...S.card, textAlign:'center', padding:'3rem', color:'#999'}}>⏳ 로딩 중...</div>
      ) : orders.length === 0 ? (
        <div style={{...S.card, textAlign:'center', padding:'3rem'}}>
          <div style={{fontSize:'2rem', marginBottom:'0.75rem'}}>📭</div>
          <div style={{color:'#666'}}>송장 입력 완료된 주문이 없습니다</div>
          <div style={{fontSize:'0.8rem', color:'#999', marginTop:'0.5rem'}}>송장출력 메뉴에서 송장번호를 입력해주세요</div>
        </div>
      ) : (
        <>
          {/* 목록 */}
          <div style={S.card}>
            <table style={{width:'100%', borderCollapse:'collapse'}}>
              <thead>
                <tr>
                  {['선택','주문번호','수취인','상품명','수량','택배사','송장번호'].map(h=>(
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((o,idx) => (
                  <tr key={o.orderNo} onClick={()=>toggle(o.orderNo)}
                    style={{ background:selected.has(o.orderNo)?'#e3f2fd':idx%2===0?'#fff':'#fafafa', cursor:'pointer' }}>
                    <td style={S.td}>
                      <input type="checkbox" checked={selected.has(o.orderNo)}
                        onChange={()=>toggle(o.orderNo)} onClick={e=>e.stopPropagation()} />
                    </td>
                    <td style={{...S.td,fontWeight:600,color:'#1565c0'}}>{o.orderNo}</td>
                    <td style={S.td}>{o.recipientName}</td>
                    <td style={{...S.td,textAlign:'left',maxWidth:220,overflow:'hidden',textOverflow:'ellipsis'}}>{o.productName}</td>
                    <td style={S.td}>{o.quantity}</td>
                    <td style={S.td}>
                      <span style={{padding:'0.15rem 0.5rem',borderRadius:'10px',background:'#e8f5e9',color:'#2e7d32',fontSize:'0.73rem',fontWeight:600}}>
                        {o.carrierName||'-'}
                      </span>
                    </td>
                    <td style={{...S.td,fontWeight:600,color:'#1565c0'}}>{o.trackingNo}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 미리보기 */}
          {targets.length > 0 && (
            <div style={S.card}>
              <div style={{fontWeight:700,color:'#1565c0',marginBottom:'1rem',fontSize:'0.9rem'}}>
                📋 인쇄 미리보기 ({targets.length}건) — 실제 크기의 50%
              </div>
              <div style={{display:'flex',flexWrap:'wrap',gap:'1.5rem',alignItems:'flex-start'}}>
                {targets.slice(0,4).map(o => (
                  <div key={o.orderNo} style={{
                    transform:'scale(0.5)', transformOrigin:'top left',
                    width:'calc(111mm * 0.5)', height:'calc(272mm * 0.5)',
                    overflow:'hidden', flexShrink:0,
                  }}>
                    <PostalInvoice order={o} />
                  </div>
                ))}
                {targets.length > 4 && (
                  <div style={{display:'flex',alignItems:'center',color:'#999',fontSize:'0.875rem',padding:'1rem'}}>
                    +{targets.length-4}건 더
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {/* 인쇄용 숨김 */}
      <div ref={printRef} style={{display:'none'}}>
        {targets.map(o => <PostalInvoice key={o.orderNo} order={o} />)}
      </div>
    </div>
  );
}
