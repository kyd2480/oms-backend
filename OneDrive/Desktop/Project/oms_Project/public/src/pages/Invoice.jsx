import { useState, useEffect } from 'react';

const API = (import.meta.env.VITE_API_URL?.replace('/api/processing','') ||
             'https://oms-backend-production-8a38.up.railway.app') + '/api/invoice';

const S = {
  page:  { padding:'2rem', background:'#f5f5f5', minHeight:'100vh', color:'#111' },
  card:  { background:'#fff', borderRadius:'8px', padding:'1.5rem', marginBottom:'1.25rem',
           boxShadow:'0 1px 4px rgba(0,0,0,0.06)', color:'#111' },
  th:    { padding:'0.55rem 0.6rem', background:'#f0f4ff', border:'1px solid #dde3f0',
           fontSize:'0.77rem', fontWeight:700, color:'#334', textAlign:'center', whiteSpace:'nowrap' },
  td:    { padding:'0.45rem 0.6rem', border:'1px solid #e8ecf4',
           fontSize:'0.79rem', color:'#333', textAlign:'center' },
  inp:   { padding:'0.32rem 0.45rem', border:'1px solid #ccc', borderRadius:'3px',
           fontSize:'0.79rem', width:'100%', boxSizing:'border-box', outline:'none', color:'#111' },
  sel:   { padding:'0.32rem 0.45rem', border:'1px solid #ccc', borderRadius:'3px',
           fontSize:'0.79rem', background:'#fff', cursor:'pointer', color:'#111' },
  bBlue: { padding:'0.38rem 0.9rem', background:'#1976d2', color:'#fff', border:'none',
           borderRadius:'3px', cursor:'pointer', fontSize:'0.8rem', fontWeight:600 },
  bGreen:{ padding:'0.38rem 0.9rem', background:'#2e7d32', color:'#fff', border:'none',
           borderRadius:'3px', cursor:'pointer', fontSize:'0.8rem', fontWeight:600 },
  bGray: { padding:'0.38rem 0.9rem', background:'#e0e0e0', color:'#333', border:'none',
           borderRadius:'3px', cursor:'pointer', fontSize:'0.8rem' },
};

function Badge({ text, color, bg }) {
  return (
    <span style={{ padding:'0.18rem 0.5rem', borderRadius:'10px', fontSize:'0.71rem',
      fontWeight:700, background:bg||'#f5f5f5', color:color||'#666' }}>
      {text}
    </span>
  );
}

// ── 우체국 송장 인쇄 (111mm x 272mm) ────────────────────────────
function printInvoices(orders) {
  const today = new Date().toLocaleDateString('ko-KR',{year:'numeric',month:'numeric',day:'numeric'});
  const ITEMS_PER_PAGE = 9;

  const slips = orders.filter(o => o.hasInvoice).map(o => {
    const products = (o.productName||'').split(',').map(p=>p.trim()).filter(Boolean);
    const totalQty = o.quantity;
    const trackingNo = o.trackingNo||'';
    const pages = [];

    // 페이지 분할 (9개씩)
    for (let i = 0; i < Math.max(1, Math.ceil(products.length / ITEMS_PER_PAGE)); i++) {
      const chunk = products.slice(i * ITEMS_PER_PAGE, (i+1) * ITEMS_PER_PAGE);
      const pageNum = i + 1;
      const totalPages = Math.ceil(products.length / ITEMS_PER_PAGE) || 1;
      const isFirst = i === 0;

      const rows = chunk.map((p, j) => {
        const idx = i * ITEMS_PER_PAGE + j;
        return `<tr><td>[${String.fromCharCode(65+Math.floor(idx/9))}F-${String(idx+1).padStart(3,'0')}-${idx+1}-${idx+1}]${p}</td><td style="text-align:right;white-space:nowrap;padding-left:3mm">1</td></tr>`;
      }).join('');

      if (isFirst) {
        pages.push(`
        <div class="slip">
          <div class="col-l">
            <div style="font-size:7.5pt;margin-bottom:2mm">신청일 : ${today}</div>
            <div style="font-size:6.5pt;line-height:1.85;margin-bottom:0">
              고객주문문의 : 메이크샵<br>문의처 : 1661-2811<br>주문번호:<br>
              <span style="font-size:6pt;word-break:break-all">${o.orderNo}</span>
            </div>
            <div style="font-size:6.5pt;text-align:right;margin-top:2mm;margin-bottom:2mm">요금: 신용</div>
            <div style="display:flex;flex-direction:column;align-items:center;">
              <svg class="bcL" data-val="${trackingNo}" style="display:block;width:90%"></svg>
              <div style="font-size:8pt;text-align:center;margin-top:1mm;width:100%">${trackingNo.slice(-5)}</div>
            </div>
            <div style="font-size:8.5pt;margin-top:1mm">[총:${totalQty}개]</div>
            <div style="flex:1"></div>
            <div style="display:flex;justify-content:space-between;font-size:6pt;margin-bottom:1mm">
              <span>1/1525</span><span>[${pageNum} / ${totalPages}]</span>
            </div>
            <div style="font-size:7pt">메이크샵</div>
          </div>
          <div class="col-m">
            <div style="text-align:center;font-size:13pt;letter-spacing:0.5px;margin-bottom:1mm">A1(동서울) 100(서울중앙) 05 58</div>
            <div style="text-align:center;font-size:8pt;margin-bottom:2mm">- 033 -</div>
            <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:0.5mm">
              <div style="font-size:9pt;line-height:1.7">경기도 안양시 동안구 시민대로110번길 20<br>(호계동, (주)동명화학)3층</div>
              <div style="font-size:9pt;flex-shrink:0;margin-left:3mm">14079</div>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:2mm">
              <div style="font-size:10pt">젝시믹스(안양)</div>
              <div style="font-size:9pt">T : 1661-2811</div>
            </div>
            <div style="font-size:11pt;line-height:1.5">${o.address||'-'}</div>
            <div style="margin-top:auto">
              <div style="font-size:19pt;letter-spacing:2px;margin-bottom:1.5mm">${o.recipientName||'-'}</div>
              <div style="text-align:right;font-size:9pt;line-height:1.9;margin-bottom:2mm">
                T : ${o.recipientPhone||'-'}
              </div>
              <div style="display:inline-flex;flex-direction:column;align-items:stretch">
                <div class="bcMLabel" style="font-size:7.5pt;text-align:center;margin-bottom:1.5mm">등기번호: ${trackingNo}</div>
                <svg class="bcM" data-val="${trackingNo}" style="display:block"></svg>
              </div>
            </div>
          </div>
          <div class="col-r">
            <div style="text-align:center;font-size:9pt;margin-bottom:2mm">상 품 내 역 서 &nbsp; ${o.recipientName||''}</div>
            <div style="font-size:7.5pt;line-height:1.9;margin-bottom:2mm">
              등기번호:${trackingNo}<br>
              <span>주문번호 : ${o.orderNo}</span><span style="float:right">수량</span>
            </div>
            <table style="width:100%;font-size:9pt;border-collapse:collapse;line-height:2.0">${rows}</table>
            <div style="flex:1"></div>
            <div style="font-size:9.5pt">[총:${totalQty}개]</div>
          </div>
        </div>`);
      } else {
        pages.push(`
        <div class="slip">
          <div class="col-l">
            <div style="font-size:6pt;margin-bottom:2mm">주문번호:<br>
              <span style="word-break:break-all">${o.orderNo}</span>
            </div>
            <div style="font-size:8pt;margin-bottom:2mm">${trackingNo.slice(-5)}</div>
            <div style="flex:1"></div>
            <div style="display:flex;justify-content:space-between;font-size:6pt;margin-bottom:1mm">
              <span>1/1525</span><span>[${pageNum} / ${totalPages}]</span>
            </div>
            <div style="font-size:7pt">메이크샵</div>
          </div>
          <div class="col-m" style="justify-content:center;align-items:center;">
            <div style="font-size:32pt;color:#e00;letter-spacing:4px;text-align:center">발 송 확 인 용</div>
          </div>
          <div class="col-r">
            <div style="text-align:center;font-size:9pt;margin-bottom:2mm">상 품 내 역 서</div>
            <div style="font-size:7.5pt;line-height:1.9;margin-bottom:2mm">
              등기번호:${trackingNo}<br>
              <span>주문번호 : ${o.orderNo}</span><span style="float:right">수량</span>
            </div>
            <table style="width:100%;font-size:9pt;border-collapse:collapse;line-height:2.0">${rows}</table>
            <div style="flex:1"></div>
            <div style="font-size:9.5pt">[총:${totalQty}개]</div>
          </div>
        </div>`);
      }
    }
    return pages.join('');
  }).join('');

  const w = window.open('', '_blank');
  w.document.write(`<!DOCTYPE html><html><head>
    <meta charset="utf-8"><title>송장출력</title>
    <script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js"><\/script>
    <style>
      @page { size: 272mm 111mm landscape; margin: 0; }
      * { box-sizing:border-box; margin:0; padding:0; font-weight:700 !important; }
      body { font-family:'맑은 고딕','Malgun Gothic',sans-serif; background:#fff; }
      div,span,td { font-weight:700 !important; }
      .slip { width:272mm; height:111mm; display:flex; background:#fff; overflow:hidden; page-break-after:always; }
      .col-l { width:60mm; padding:2mm 3mm; display:flex; flex-direction:column; flex-shrink:0; }
      .col-m { width:108mm; padding:2mm 3mm; display:flex; flex-direction:column; flex-shrink:0; }
      .col-r { flex:1; padding:2mm 3mm; display:flex; flex-direction:column; }
      @media print { body { -webkit-print-color-adjust:exact; color-adjust:exact; } }
    </style>
  </head><body>
    ${slips}
    <script>
      window.onload = function() {
        document.querySelectorAll('.bcL[data-val]').forEach(function(el) {
          var v = el.getAttribute('data-val');
          if(v) try { JsBarcode(el,v,{format:'CODE128',width:1.8,height:35,displayValue:false,margin:1}); } catch(e){}
        });
        document.querySelectorAll('.bcM[data-val]').forEach(function(el) {
          var v = el.getAttribute('data-val');
          if(v) try { JsBarcode(el,v,{format:'CODE128',width:1.6,height:55,displayValue:false,margin:1}); } catch(e){}
        });
        setTimeout(function(){
          document.querySelectorAll('.bcM').forEach(function(svg) {
            var label = svg.previousElementSibling;
            if(label) label.style.width = svg.getBoundingClientRect().width + 'px';
          });
          setTimeout(function(){ window.print(); }, 300);
        }, 300);
      };
    <\/script>
  </body></html>`);
  w.document.close();
}

function exportCSV(orders) {
  const header = ['주문번호','수취인','연락처','주소','상품명','수량','택배사','송장번호'].join(',');
  const rows = orders.filter(o => o.hasInvoice).map(o =>
    [o.orderNo, o.recipientName, o.recipientPhone,
     `"${o.address||''}"`, `"${o.productName||''}"`,
     o.quantity, o.carrierName||'', o.trackingNo||''].join(',')
  );
  const blob = new Blob(['\uFEFF'+header+'\n'+rows.join('\n')], { type:'text/csv;charset=utf-8;' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `송장_${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
}

export default function Invoice() {
  const [orders,   setOrders]   = useState([]);
  const [carriers, setCarriers] = useState([]);
  const [loading,  setLoading]  = useState(false);
  const [search,   setSearch]   = useState('');
  const [filter,   setFilter]   = useState('ALL');
  const [msg,      setMsg]      = useState('');
  const [inputs,   setInputs]   = useState({});
  const [bulkCarrier, setBulkCarrier] = useState(null); // 일괄 선택된 택배사

  const load = async () => {
    setLoading(true);
    try {
      const [ordRes, carRes] = await Promise.all([fetch(`${API}/orders`), fetch(`${API}/carriers`)]);
      const [ordData, carData] = await Promise.all([ordRes.json(), carRes.json()]);
      const ords = Array.isArray(ordData) ? ordData : [];
      setOrders(ords);
      setCarriers(Array.isArray(carData) ? carData : []);
      const init = {};
      ords.forEach(o => { init[o.orderNo] = { carrierCode: o.carrierCode||'', carrierName: o.carrierName||'', trackingNo: o.trackingNo||'' }; });
      setInputs(init);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const setInp = (orderNo, field, value) =>
    setInputs(prev => ({ ...prev, [orderNo]: { ...prev[orderNo], [field]: value } }));

  /* ── 일괄 자동 송장번호 부여 ──────────────────────────── */
  const autoAssignAll = async () => {
    if (!bulkCarrier) { alert('위에서 택배사를 먼저 선택해주세요.'); return; }
    if (!window.confirm(`미발급 ${noInvoice.length}건에 ${bulkCarrier.name} 송장번호를 자동 부여합니다.\n\n계속하시겠습니까?`)) return;
    setLoading(true);
    try {
      const res  = await fetch(`${API}/auto-assign-all`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ carrierCode: bulkCarrier.code, carrierName: bulkCarrier.name }),
      });
      const data = await res.json();
      setMsg(`✅ ${data.assigned ?? data.message}`);
      await load();
    } catch(e) { alert('자동 부여 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  /* ── 개별 자동 송장번호 부여 ──────────────────────────── */
  const autoAssignSingle = async (orderNo) => {
    const inp = inputs[orderNo] || {};
    const carrierCode = inp.carrierCode || bulkCarrier?.code;
    const carrierName = inp.carrierName || bulkCarrier?.name;
    if (!carrierCode) { alert('택배사를 먼저 선택해주세요.'); return; }
    setLoading(true);
    try {
      const res  = await fetch(`${API}/auto-assign/${orderNo}`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ carrierCode, carrierName }),
      });
      const data = await res.json();
      if (data.success) {
        setMsg(`✅ ${orderNo} 송장 자동 부여: ${data.trackingNo}`);
        await load();
      } else {
        alert('자동 부여 실패: ' + (data.message || ''));
      }
    } catch(e) { alert('자동 부여 실패: ' + e.message); }
    finally { setLoading(false); }
  };

  const saveSingle = async (orderNo) => {
    const inp = inputs[orderNo];
    if (!inp?.trackingNo?.trim()) { alert('송장번호를 입력해주세요.'); return; }
    try {
      await fetch(`${API}/save`, { method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ orderNo, ...inp }) });
      setMsg(`✅ ${orderNo} 송장 저장 완료`);
      await load();
    } catch(e) { alert('저장 실패: ' + e.message); }
  };

  const hasInvoice   = orders.filter(o => o.hasInvoice);
  const noInvoice    = orders.filter(o => !o.hasInvoice);
  const invoiceCount = hasInvoice.length;

  const filtered = orders.filter(o => {
    const matchFilter = filter==='ALL' || (filter==='DONE' && o.hasInvoice) || (filter==='TODO' && !o.hasInvoice);
    const kw = search.toLowerCase();
    return matchFilter && (!kw || [o.orderNo,o.recipientName,o.productName,o.trackingNo].some(v=>v?.toLowerCase().includes(kw)));
  });

  return (
    <div style={S.page}>
      <div style={S.card}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:'1rem' }}>
          <div>
            <h1 style={{ fontSize:'1.5rem', fontWeight:700, margin:'0 0 0.25rem', color:'#1a1a1a' }}>송장 출력</h1>
            <div style={{ fontSize:'0.875rem', color:'#666' }}>재고 할당 완료된 주문의 송장을 발급합니다</div>
          </div>
          <div style={{ display:'flex', gap:'1.5rem' }}>
            {[{label:'전체',value:orders.length,color:'#1565c0'},{label:'발급완료',value:invoiceCount,color:'#2e7d32'},{label:'미발급',value:noInvoice.length,color:'#c62828'}].map(s => (
              <div key={s.label} style={{ textAlign:'center' }}>
                <div style={{ fontSize:'1.4rem', fontWeight:700, color:s.color }}>{s.value}</div>
                <div style={{ fontSize:'0.72rem', color:'#888' }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ display:'flex', gap:'1rem', flexWrap:'wrap', alignItems:'center' }}>
          <div style={{ fontSize:'0.85rem', fontWeight:600, color:'#333' }}>택배사</div>
          {carriers.map(c => (
            <button key={c.code}
              onClick={() => {
                // 미발급 전체에 이 택배사 일괄 적용
                setInputs(prev => {
                  const next = { ...prev };
                  orders.filter(o => !o.hasInvoice).forEach(o => {
                    next[o.orderNo] = { ...(next[o.orderNo]||{}), carrierCode: c.code, carrierName: c.name };
                  });
                  return next;
                });
                setBulkCarrier(c);
              }}
              style={{
                padding:'0.3rem 0.8rem', borderRadius:'4px', fontSize:'0.8rem', cursor:'pointer',
                fontWeight: bulkCarrier?.code === c.code ? 700 : 400,
                background: bulkCarrier?.code === c.code ? '#1565c0' : '#fff',
                color:      bulkCarrier?.code === c.code ? '#fff'    : '#555',
                border:     bulkCarrier?.code === c.code ? '1px solid #0d47a1' : '1px solid #ccc',
              }}>
              {c.name}
            </button>
          ))}
          <div style={{ flex:1 }} />
          {/* 일괄 자동 송장번호 부여 */}
          <button
            onClick={autoAssignAll}
            disabled={!bulkCarrier || loading || noInvoice.length === 0}
            style={{ ...S.bBlue,
              opacity: (!bulkCarrier || noInvoice.length === 0) ? 0.4 : 1,
              background: '#e65100' }}>
            ⚡ 자동 송장부여 ({noInvoice.length}건)
          </button>
          <button onClick={() => printInvoices(hasInvoice)} disabled={!invoiceCount}
            style={{ ...S.bBlue, opacity:!invoiceCount?0.5:1 }}>
            🖨️ 송장 출력 ({invoiceCount}건)
          </button>
          <button onClick={() => exportCSV(hasInvoice)} disabled={!invoiceCount}
            style={{ ...S.bGreen, opacity:!invoiceCount?0.5:1 }}>
            📥 CSV ({invoiceCount}건)
          </button>
        </div>
      </div>

      {msg && (
        <div style={{ padding:'0.75rem 1.25rem', background:'#e8f5e9', border:'1px solid #a5d6a7',
          borderRadius:'6px', marginBottom:'1rem', fontSize:'0.875rem', fontWeight:600, color:'#1b5e20' }}>
          {msg}
          <button onClick={() => setMsg('')} style={{float:'right',background:'none',border:'none',cursor:'pointer',color:'#666'}}>✕</button>
        </div>
      )}

      <div style={S.card}>
        <div style={{ display:'flex', gap:'0.75rem', flexWrap:'wrap', alignItems:'center', marginBottom:'1rem' }}>
          <div style={{ display:'flex', gap:'0.5rem' }}>
            {[{key:'ALL',label:`전체 (${orders.length})`},{key:'TODO',label:`미발급 (${noInvoice.length})`,color:'#c62828'},{key:'DONE',label:`발급완료 (${invoiceCount})`,color:'#2e7d32'}].map(f => (
              <button key={f.key} onClick={() => setFilter(f.key)}
                style={{ padding:'0.35rem 0.85rem', border:'none', borderRadius:'3px', cursor:'pointer',
                  fontSize:'0.8rem', fontWeight:filter===f.key?700:400,
                  background:filter===f.key?(f.color||'#1976d2'):'#e0e0e0',
                  color:filter===f.key?'#fff':'#333' }}>
                {f.label}
              </button>
            ))}
          </div>
          <div style={{flex:1}}/>
          <input value={search} onChange={e=>setSearch(e.target.value)}
            placeholder="주문번호, 수취인, 상품명..." style={{...S.inp,width:200,color:'#111'}}/>
          <button onClick={load} disabled={loading} style={S.bGray}>🔄</button>
        </div>

        {loading ? (
          <div style={{textAlign:'center',padding:'3rem',color:'#999'}}>⏳ 로딩 중...</div>
        ) : filtered.length === 0 ? (
          <div style={{textAlign:'center',padding:'3rem',color:'#bbb'}}>
            <div style={{fontSize:'2rem',marginBottom:'0.5rem'}}>📭</div>
            <div style={{color:'#666'}}>표시할 주문이 없습니다</div>
            <div style={{fontSize:'0.8rem',marginTop:'0.4rem',color:'#999'}}>재고 매칭에서 할당 완료된 주문이 표시됩니다</div>
          </div>
        ) : (
          <div style={{overflowX:'auto'}}>
            <table style={{width:'100%',borderCollapse:'collapse',minWidth:1000}}>
              <thead>
                <tr>
                  {['주문번호','쇼핑몰','수취인','주소','상품명','수량','택배사','송장번호','상태','저장'].map(h=>(
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((o, idx) => {
                  const inp = inputs[o.orderNo] || {};
                  return (
                    <tr key={o.orderNo} style={{background:o.hasInvoice?'#f1f8e9':idx%2===0?'#fff':'#fafafa'}}>
                      <td style={{...S.td,fontWeight:600,color:'#1565c0',whiteSpace:'nowrap'}}>{o.orderNo}</td>
                      <td style={S.td}>
                        {o.channelName
                          ? <span style={{padding:'0.12rem 0.4rem',borderRadius:'8px',background:'#e3f2fd',color:'#1565c0',fontSize:'0.71rem',fontWeight:600}}>{o.channelName}</span>
                          : <span style={{color:'#bbb'}}>-</span>}
                      </td>
                      <td style={{...S.td,whiteSpace:'nowrap'}}>{o.recipientName}</td>
                      <td style={{...S.td,textAlign:'left',maxWidth:140,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{o.address||'-'}</td>
                      <td style={{...S.td,textAlign:'left',maxWidth:180,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{o.productName}</td>
                      <td style={S.td}>{o.quantity}</td>
                      <td style={{...S.td,minWidth:120,padding:'0.25rem 0.35rem'}}>
                        {o.hasInvoice
                          ? <span style={{fontSize:'0.8rem',color:'#2e7d32',fontWeight:600}}>{o.carrierName}</span>
                          : <select value={inp.carrierCode||''} style={S.sel}
                              onChange={e => { const car=carriers.find(c=>c.code===e.target.value); setInp(o.orderNo,'carrierCode',e.target.value); setInp(o.orderNo,'carrierName',car?.name||''); }}>
                              <option value="">선택</option>
                              {carriers.map(c=><option key={c.code} value={c.code}>{c.name}</option>)}
                            </select>
                        }
                      </td>
                      <td style={{...S.td,minWidth:150,padding:'0.25rem 0.35rem'}}>
                        {o.hasInvoice
                          ? <span style={{fontWeight:700,color:'#1565c0',fontSize:'0.8rem'}}>{o.trackingNo}</span>
                          : <input value={inp.trackingNo||''} placeholder="송장번호"
                              style={{...S.inp,borderColor:inp.trackingNo?'#2e7d32':'#ccc'}}
                              onChange={e=>setInp(o.orderNo,'trackingNo',e.target.value)}
                              onKeyDown={e=>e.key==='Enter'&&saveSingle(o.orderNo)}/>
                        }
                      </td>
                      <td style={S.td}>
                        {o.hasInvoice
                          ? <Badge text="발급완료" color="#2e7d32" bg="#e8f5e9"/>
                          : <Badge text="미발급"   color="#c62828" bg="#ffebee"/>}
                      </td>
                      <td style={S.td}>
                        {o.hasInvoice
                          ? <button onClick={()=>printInvoices([o])} style={{...S.bBlue,padding:'0.2rem 0.6rem',fontSize:'0.73rem'}}>🖨️</button>
                          : <div style={{ display:'flex', gap:4, justifyContent:'center' }}>
                              <button
                                onClick={() => autoAssignSingle(o.orderNo)}
                                disabled={!(inputs[o.orderNo]?.carrierCode || bulkCarrier)}
                                title="자동 송장번호 부여"
                                style={{ ...S.bBlue, padding:'0.2rem 0.6rem', fontSize:'0.73rem',
                                  background:'#e65100',
                                  opacity: (inputs[o.orderNo]?.carrierCode || bulkCarrier) ? 1 : 0.4 }}>
                                ⚡ 자동
                              </button>
                              <button onClick={()=>saveSingle(o.orderNo)} disabled={!inp.trackingNo?.trim()}
                                style={{...S.bGreen,padding:'0.2rem 0.6rem',fontSize:'0.73rem',opacity:!inp.trackingNo?.trim()?0.4:1}}>
                                저장
                              </button>
                            </div>
                        }
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
