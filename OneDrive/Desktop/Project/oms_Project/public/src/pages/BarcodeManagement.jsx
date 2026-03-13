import { useState, useRef, useEffect, useCallback } from "react";

const API_BASE = "https://oms-backend-production-8a38.up.railway.app/api/inventory";

function loadScript(src, key) {
  return new Promise((resolve, reject) => {
    if (window[key]) return resolve(window[key]);
    const s = document.createElement("script");
    s.src = src;
    s.onload = () => resolve(window[key]);
    s.onerror = () => reject(new Error("load fail: " + src));
    document.head.appendChild(s);
  });
}
async function loadLibs() {
  await loadScript("https://cdnjs.cloudflare.com/ajax/libs/jsbarcode/3.11.5/JsBarcode.all.min.js", "JsBarcode");
  await loadScript("https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js", "jspdf");
}

// " - " 기준 분리, 없으면 마지막 공백
function splitName(name) {
  if (!name) return { line1: "", line2: "" };
  const t = name.trim();
  const di = t.indexOf(" - ");
  if (di !== -1) return { line1: t.slice(0, di), line2: t.slice(di + 3) };
  const li = t.lastIndexOf(" ");
  if (li === -1) return { line1: t, line2: "" };
  return { line1: t.slice(0, li), line2: t.slice(li + 1) };
}

// ─────────────────────────────────────────────────────────────────────────────
// 샘플 이미지 픽셀 측정값 (261×111px → 60×30mm)
//
//  line1 글자높이 : 2.97mm  → bold
//  line1 Y 시작   : 3.24mm
//  line2 글자높이 : 2.97mm  → bold
//  line2 Y 시작   : 7.30mm
//  바코드 막대 Y  : 13.51mm
//  바코드 막대 H  : 5.95mm  (숫자 포함 전체 ~7.7mm)
//  바코드 X       : 6.90mm  너비: 46mm
//  xexymix Y      : 26.5mm  높이: 1.89mm
// ─────────────────────────────────────────────────────────────────────────────

// ── Canvas 텍스트 → 고해상도 PNG ──────────────────────────────────────────────
// fontSizeMm: 배치할 폰트 크기(mm 단위), OVER배 오버샘플링으로 선명하게 렌더
// PDF에 배치할 때는 (w/h * fontSizeMm*1.4) × fontSizeMm*1.4 크기로 넣으면 됨
const MM_TO_PX = 300 / 25.4;  // 11.811 px/mm @300dpi
const OVER = 4;

function makePng(text, { fontSizeMm, weight = "bold", color = "#000000" } = {}) {
  const fontSizePx = Math.round(fontSizeMm * MM_TO_PX * OVER);
  const font = `${weight} ${fontSizePx}px 'Malgun Gothic','Apple SD Gothic Neo','NanumGothic',sans-serif`;
  const c = document.createElement("canvas");
  const ctx = c.getContext("2d");
  ctx.font = font;
  const tw = ctx.measureText(text).width;
  c.width  = Math.ceil(tw) + Math.ceil(fontSizePx * 0.2);
  c.height = Math.ceil(fontSizePx * 1.4);
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, c.width, c.height);
  ctx.font = font;
  ctx.fillStyle = color;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(text, c.width / 2, c.height / 2);
  // PDF 배치 높이 = fontSizeMm * 1.4 (canvas h와 동일 비율)
  const pdfH = fontSizeMm * 1.4;
  // PDF 배치 너비 = 텍스트 가로비율 유지
  const pdfW = (c.width / c.height) * pdfH;
  return { url: c.toDataURL("image/png"), w: c.width, h: c.height, pdfW, pdfH };
}

// ── 바코드 PNG (고해상도) ──────────────────────────────────────────────────────
// 샘플 측정: 바코드 전체(바+숫자) ≈ 7.7mm, 바만 ≈ 5.95mm
function makeBarcodePng(value) {
  return new Promise((resolve) => {
    const c = document.createElement("canvas");
    try {
      window.JsBarcode(c, value || "NOBARCODE", {
        format: "CODE128",
        width: 4,          // 바 굵기
        height: 220,       // 바 높이 px
        displayValue: true,
        fontSize: 44,      // 숫자 크기
        margin: 16,
        textMargin: 6,
        background: "#ffffff",
        lineColor: "#000000",
      });
      resolve({ url: c.toDataURL("image/png"), w: c.width, h: c.height });
    } catch { resolve(null); }
  });
}

// ── PDF 생성 ──────────────────────────────────────────────────────────────────
async function makePdf(product, qty) {
  await loadLibs();
  const { jsPDF } = window.jspdf;
  const W = 60, H = 30;
  const doc = new jsPDF({ orientation: "landscape", unit: "mm", format: [H, W] });

  const rawName    = product.productName || product.product_name || "";
  const barcodeVal = product.barcode || product.sku || "NOBARCODE";
  const { line1, line2 } = splitName(rawName);

  // 샘플 측정: 글자 cap-height=2.97mm → fontSize = 2.97/0.72 ≈ 4.1mm
  // xexymix cap-height=1.89mm → fontSize = 1.89/0.72 ≈ 2.6mm
  const i1     = line1 ? makePng(line1,    { fontSizeMm: 4.1, weight: "bold" })            : null;
  const i2     = line2 ? makePng(line2,    { fontSizeMm: 4.1, weight: "bold" })            : null;
  const iBrand =          makePng("xexymix", { fontSizeMm: 2.6, weight: "normal", color: "#666666" });
  const iBC    = await makeBarcodePng(barcodeVal);

  for (let pg = 0; pg < qty; pg++) {
    if (pg > 0) doc.addPage([H, W]);
    doc.setFillColor(255, 255, 255);
    doc.rect(0, 0, W, H, "F");

    // ── line1: 상단 중앙 ────────────────────────────────────────────────────
    // pdfH=fontSizeMm*1.4=5.74mm, 글자 중심이 3.24+2.97/2=4.73mm에 오도록
    if (i1) {
      const Y = 3.24 - i1.pdfH / 2 + (4.1 * 0.72) / 2;  // cap-height 중심 정렬
      doc.addImage(i1.url, "PNG", (W - i1.pdfW) / 2, Y, i1.pdfW, i1.pdfH);
    }

    // ── line2: 중앙 ─────────────────────────────────────────────────────────
    if (i2) {
      const Y = 7.30 - i2.pdfH / 2 + (4.1 * 0.72) / 2;
      doc.addImage(i2.url, "PNG", (W - i2.pdfW) / 2, Y, i2.pdfW, i2.pdfH);
    }

    // ── 바코드: Y=13.51mm, H=7.7mm, X=6.9mm, W=46.2mm ───────────────────
    if (iBC) {
      doc.addImage(iBC.url, "PNG", 6.9, 13.51, 46.2, 7.7);
    }

    // ── xexymix: Y=26.5mm 중앙 ──────────────────────────────────────────
    const bY = 26.5 - iBrand.pdfH / 2 + (2.6 * 0.72) / 2;
    doc.addImage(iBrand.url, "PNG", (W - iBrand.pdfW) / 2, bY, iBrand.pdfW, iBrand.pdfH);
  }

  return doc;
}

// ── 미리보기 (60×30mm → 240×120px = 4배) ────────────────────────────────────
const PX_PER_MM = 4;  // 미리보기 스케일

function LabelPreview({ product }) {
  const ref = useRef(null);

  useEffect(() => {
    if (!product || !ref.current) return;
    const el = ref.current;

    loadScript(
      "https://cdnjs.cloudflare.com/ajax/libs/jsbarcode/3.11.5/JsBarcode.all.min.js",
      "JsBarcode"
    ).then(() => {
      el.innerHTML = "";

      const name = product.productName || product.product_name || "";
      const { line1, line2 } = splitName(name);
      const barcodeVal = product.barcode || product.sku || "NOBARCODE";
      const font = "'Malgun Gothic','Apple SD Gothic Neo','NanumGothic',sans-serif";

      const wrap = document.createElement("div");
      wrap.style.cssText = `
        position:relative;
        width:${60 * PX_PER_MM}px; height:${30 * PX_PER_MM}px;
        background:#ffffff; overflow:hidden;
      `;

      // 텍스트 div 생성 헬퍼
      const addLabel = (text, y_mm, glyphH_mm, weight, color = "#000") => {
        const d = document.createElement("div");
        d.textContent = text;
        const fontSizePx = (glyphH_mm / 0.72) * PX_PER_MM;
        const lineH = fontSizePx * 1.4;
        d.style.cssText = `
          position:absolute;
          width:100%; text-align:center;
          top:${y_mm * PX_PER_MM - (lineH - glyphH_mm * PX_PER_MM) / 2}px;
          height:${lineH}px; line-height:${lineH}px;
          font-size:${fontSizePx}px;
          font-weight:${weight};
          font-family:${font};
          color:${color};
          background:#ffffff;
          white-space:nowrap;
          overflow:hidden;
        `;
        wrap.appendChild(d);
      };

      // line1: Y=3.24mm, glyph=2.97mm, bold
      if (line1) addLabel(line1, 3.24, 2.97, "bold");
      // line2: Y=7.30mm, glyph=2.97mm, bold
      if (line2) addLabel(line2, 7.30, 2.97, "bold");

      // 바코드: Y=13.51mm, H=7.7mm, X=6.9mm, W=46mm
      const canvas = document.createElement("canvas");
      try {
        window.JsBarcode(canvas, barcodeVal, {
          format: "CODE128",
          width: 1.6,
          height: 38,
          displayValue: true,
          fontSize: 9,
          margin: 4,
          textMargin: 2,
          background: "#ffffff",
          lineColor: "#000000",
        });
        canvas.style.cssText = `
          position:absolute;
          top:${13.51 * PX_PER_MM}px;
          left:${6.9 * PX_PER_MM}px;
          width:${46.2 * PX_PER_MM}px;
          height:${7.7 * PX_PER_MM}px;
          background:#ffffff;
        `;
        wrap.appendChild(canvas);
      } catch (e) { console.error(e); }

      // xexymix: Y=26.5mm, glyph=1.89mm, normal
      addLabel("xexymix", 26.5, 1.89, "normal", "#666666");

      el.appendChild(wrap);
    });
  }, [product]);

  return (
    <div ref={ref} style={{ width: `${60 * PX_PER_MM}px`, height: `${30 * PX_PER_MM}px`, background: "#ffffff" }} />
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────────────────────
export default function BarcodeManagement() {
  const [searchInput, setSearchInput] = useState("");
  const [result,      setResult]      = useState(null);
  const [loading,     setLoading]     = useState(false);
  const [pdfBusy,     setPdfBusy]     = useState(false);
  const [error,       setError]       = useState("");
  const [qty,         setQty]         = useState(1);
  const inputRef = useRef(null);

  useEffect(() => { inputRef.current?.focus(); loadLibs(); }, []);

  useEffect(() => {
    if (searchInput.length < 10) return;
    const t = setTimeout(() => doSearch(), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const doSearch = useCallback(async () => {
    const kw = searchInput.trim();
    if (!kw) return;
    setLoading(true); setError(""); setResult(null);
    try {
      const res  = await fetch(`${API_BASE}/products/search?keyword=${encodeURIComponent(kw)}`);
      if (!res.ok) throw new Error("서버 오류");
      const data = await res.json();
      const list = Array.isArray(data) ? data : data.content || [];
      if (!list.length) setError("검색 결과가 없습니다.");
      else setResult(list[0]);
    } catch (e) { setError("검색 오류: " + e.message); }
    finally { setLoading(false); }
  }, [searchInput]);

  const openPdf = async () => {
    if (!result) return;
    setPdfBusy(true);
    try {
      const doc  = await makePdf(result, qty);
      const blob = doc.output("blob");
      const url  = URL.createObjectURL(blob);
      window.open(url, "_blank");
      setTimeout(() => URL.revokeObjectURL(url), 30000);
    } catch (e) { alert("PDF 오류: " + e.message); }
    finally { setPdfBusy(false); }
  };

  const savePdf = async () => {
    if (!result) return;
    setPdfBusy(true);
    try {
      const doc  = await makePdf(result, qty);
      const name = (result.productName || result.product_name || "label")
        .replace(/[^\w가-힣_-]/g, "_").slice(0, 40);
      doc.save(`${name}_라벨_${qty}장.pdf`);
    } catch (e) { alert("저장 오류: " + e.message); }
    finally { setPdfBusy(false); }
  };

  return (
    <div style={{ padding: "24px", maxWidth: "900px", margin: "0 auto" }}>
      <div style={{ marginBottom: "28px" }}>
        <h1 style={{ fontSize: "22px", fontWeight: "700", color: "#1a202c", margin: 0 }}>바코드 관리</h1>
        <p style={{ color: "#718096", marginTop: "6px", fontSize: "14px" }}>
          바코드 또는 SKU를 입력하여 상품을 검색하고 라벨 PDF를 출력하세요
        </p>
      </div>

      <div style={ST.card}>
        <label style={ST.lbl}>바코드 / SKU 검색</label>
        <div style={{ display: "flex", gap: "10px" }}>
          <input
            ref={inputRef}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doSearch()}
            placeholder="바코드 스캔 또는 SKU 입력 후 Enter"
            style={ST.input}
            onFocus={(e) => (e.target.style.borderColor = "#319795")}
            onBlur={(e)  => (e.target.style.borderColor = "#cbd5e0")}
          />
          <button onClick={doSearch} disabled={loading}
            style={{ ...ST.searchBtn, background: loading ? "#a0aec0" : "#319795", cursor: loading ? "not-allowed" : "pointer" }}>
            {loading ? "검색 중..." : "검색"}
          </button>
        </div>
        <p style={{ fontSize: "12px", color: "#a0aec0", marginTop: "8px" }}>
          💡 바코드 스캐너로 스캔하면 자동으로 검색됩니다 (10자 이상)
        </p>
      </div>

      {error && <div style={ST.errBox}>⚠️ {error}</div>}

      {result && (
        <div style={ST.card}>
          <div style={{ display: "flex", gap: "24px", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: "240px" }}>
              <span style={ST.badge}>검색 완료</span>
              <h2 style={{ fontSize: "17px", fontWeight: "700", color: "#1a202c", margin: "12px 0 16px" }}>
                {result.productName || result.product_name}
              </h2>
              <table style={{ borderCollapse: "collapse", width: "100%" }}>
                {[
                  ["SKU",     result.sku],
                  ["바코드",  result.barcode],
                  ["카테고리",result.category],
                  ["위치",    result.warehouseLocation || result.warehouse_location],
                  ["총 재고", `${result.totalStock ?? result.total_stock ?? 0}개`],
                  ["안양",    `${result.warehouseStockAnyang ?? result.warehouse_stock_anyang ?? 0}개`],
                  ["이천",    `${result.warehouseStockIcheon ?? result.warehouse_stock_icheon ?? 0}개`],
                  ["부천",    `${result.warehouseStockBucheon ?? result.warehouse_stock_bucheon ?? 0}개`],
                ].filter(([, v]) => v != null && v !== "").map(([k, v]) => (
                  <tr key={k}>
                    <td style={{ padding: "5px 0", fontSize: "13px", color: "#718096", width: "80px" }}>{k}</td>
                    <td style={{ padding: "5px 0", fontSize: "13px", color: "#2d3748", fontWeight: "500" }}>{v}</td>
                  </tr>
                ))}
              </table>
            </div>

            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "14px", minWidth: "260px" }}>
              <div>
                <div style={{ fontSize: "12px", fontWeight: "600", color: "#718096", marginBottom: "8px", textAlign: "center" }}>
                  라벨 미리보기 (60 × 30mm)
                </div>
                <div style={{ border: "2px solid #bee3f8", borderRadius: "4px", padding: "2px", background: "#ebf8ff" }}>
                  <LabelPreview product={result} />
                </div>
              </div>

              <div style={ST.qtyBox}>
                <span style={{ fontSize: "13px", color: "#4a5568", fontWeight: "600" }}>출력 수량</span>
                <button onClick={() => setQty(q => Math.max(1, q - 1))} style={ST.qtyBtn}>−</button>
                <span style={{ minWidth: "32px", textAlign: "center", fontSize: "16px", fontWeight: "700", color: "#2d3748" }}>{qty}</span>
                <button onClick={() => setQty(q => q + 1)} style={ST.qtyBtn}>+</button>
                <span style={{ fontSize: "13px", color: "#718096" }}>장</span>
              </div>

              <button onClick={openPdf} disabled={pdfBusy}
                style={{
                  width:"100%", height:"46px", border:"none", borderRadius:"10px",
                  fontSize:"15px", fontWeight:"700", color:"#fff",
                  display:"flex", alignItems:"center", justifyContent:"center", gap:"8px",
                  background: pdfBusy ? "linear-gradient(135deg,#a0aec0,#718096)" : "linear-gradient(135deg,#319795,#2c7a7b)",
                  cursor: pdfBusy ? "not-allowed" : "pointer",
                  boxShadow:"0 2px 8px rgba(49,151,149,0.35)", transition:"transform .1s,box-shadow .1s",
                }}
                onMouseEnter={(e) => { if (!pdfBusy) { e.currentTarget.style.transform="translateY(-1px)"; e.currentTarget.style.boxShadow="0 4px 14px rgba(49,151,149,0.5)"; }}}
                onMouseLeave={(e) => { e.currentTarget.style.transform="translateY(0)"; e.currentTarget.style.boxShadow="0 2px 8px rgba(49,151,149,0.35)"; }}
              >
                {pdfBusy ? "⏳ PDF 생성 중..." : `📄 PDF 보기 (${qty}장)`}
              </button>

              <button onClick={savePdf} disabled={pdfBusy}
                style={{
                  width:"100%", height:"38px", background:"#fff", color:"#319795",
                  border:"1.5px solid #319795", borderRadius:"10px", fontSize:"13px", fontWeight:"600",
                  cursor: pdfBusy ? "not-allowed" : "pointer",
                  display:"flex", alignItems:"center", justifyContent:"center", gap:"6px",
                  transition:"background .15s",
                }}
                onMouseEnter={(e) => { if (!pdfBusy) e.currentTarget.style.background="#e6fffa"; }}
                onMouseLeave={(e) => { e.currentTarget.style.background="#fff"; }}
              >
                💾 PDF 저장
              </button>

              <p style={{ fontSize: "11px", color: "#a0aec0", textAlign: "center", lineHeight: 1.5 }}>
                PDF 뷰어에서 인쇄 시<br />용지 크기를 60×30mm로 설정하세요
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const ST = {
  card:      { background:"#fff", border:"1px solid #e2e8f0", borderRadius:"12px", padding:"20px 24px", marginBottom:"20px", boxShadow:"0 1px 4px rgba(0,0,0,0.06)" },
  lbl:       { fontSize:"13px", fontWeight:"600", color:"#4a5568", display:"block", marginBottom:"8px" },
  input:     { flex:1, height:"44px", padding:"0 16px", border:"1.5px solid #cbd5e0", borderRadius:"8px", fontSize:"15px", outline:"none", transition:"border-color .2s", fontFamily:"monospace" },
  searchBtn: { height:"44px", padding:"0 24px", color:"#fff", border:"none", borderRadius:"8px", fontSize:"14px", fontWeight:"600", whiteSpace:"nowrap" },
  errBox:    { background:"#fff5f5", border:"1px solid #fed7d7", borderRadius:"8px", padding:"12px 16px", color:"#c53030", fontSize:"14px", marginBottom:"20px" },
  badge:     { display:"inline-block", background:"#e6fffa", color:"#234e52", fontSize:"11px", fontWeight:"600", padding:"3px 10px", borderRadius:"20px" },
  qtyBox:    { display:"flex", alignItems:"center", gap:"10px", background:"#f7fafc", border:"1px solid #e2e8f0", borderRadius:"8px", padding:"10px 14px", width:"100%", justifyContent:"center" },
  qtyBtn:    { width:"28px", height:"28px", border:"1.5px solid #cbd5e0", borderRadius:"6px", background:"#fff", cursor:"pointer", fontSize:"16px", display:"flex", alignItems:"center", justifyContent:"center", fontWeight:"600", color:"#4a5568", lineHeight:1 },
};
