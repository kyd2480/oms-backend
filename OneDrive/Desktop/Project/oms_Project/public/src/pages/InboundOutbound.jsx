import { useState, useEffect, useCallback, useRef } from "react";

const API_BASE = "https://oms-backend-production-8a38.up.railway.app/api";

// ── API 헬퍼 ────────────────────────────────────────────────────────────────
async function apiFetch(url, options = {}) {
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || `HTTP ${res.status}`);
  }
  return res.json();
}

const TYPE_COLOR = {
  REAL:    "#2563eb", TRANSIT: "#d97706", RETURN: "#7c3aed",
  DEFECT:  "#dc2626", SPECIAL: "#059669", VIRTUAL: "#4b5563", UNUSED: "#9ca3af",
};
const TYPE_LABEL = {
  REAL:"실제창고", TRANSIT:"이동중", RETURN:"반품", DEFECT:"불량/폐기",
  SPECIAL:"특수", VIRTUAL:"가상", UNUSED:"미사용",
};

// ── 아이콘 ───────────────────────────────────────────────────────────────────
const Icons = {
  In:      () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>,
  Out:     () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>,
  Search:  () => <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>,
  X:       () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>,
  History: () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-3.51"/></svg>,
  Check:   () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>,
};

// ── 창고 선택 드롭다운 ────────────────────────────────────────────────────────
function WarehouseSelect({ warehouses, value, onChange, placeholder = "창고 선택" }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const ref = useRef(null);

  useEffect(() => {
    const handler = e => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const selected = warehouses.find(w => w.code === value);

  // 타입별 그룹
  const filtered = warehouses.filter(w =>
    !search || w.name.toLowerCase().includes(search.toLowerCase()) || w.code.toLowerCase().includes(search.toLowerCase())
  );
  const grouped = {};
  filtered.forEach(w => {
    if (!grouped[w.type]) grouped[w.type] = [];
    grouped[w.type].push(w);
  });

  return (
    <div ref={ref} style={{ position: "relative" }}>
      {/* 선택 버튼 */}
      <button onClick={() => setOpen(!open)} type="button" style={{
        width: "100%", height: 40, border: `1.5px solid ${open ? "#3b82f6" : "#e5e7eb"}`,
        borderRadius: 8, padding: "0 12px", background: open ? "#fff" : "#fafafa",
        display: "flex", alignItems: "center", justifyContent: "space-between",
        cursor: "pointer", fontSize: 13,
        boxShadow: open ? "0 0 0 3px rgba(59,130,246,0.12)" : "none",
        transition: "all .15s",
      }}>
        {selected ? (
          <span style={{ display: "flex", alignItems: "center", gap: 8, overflow: "hidden" }}>
            <span style={{ width: 8, height: 8, borderRadius: "50%", flexShrink: 0,
              background: TYPE_COLOR[selected.type] || "#6b7280" }} />
            <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
              color: "#111827", fontWeight: 600 }}>
              {selected.name}
            </span>
            <span style={{ fontSize: 10, color: "#9ca3af", flexShrink: 0 }}>
              {TYPE_LABEL[selected.type] || ""}
            </span>
          </span>
        ) : (
          <span style={{ color: "#9ca3af" }}>{placeholder}</span>
        )}
        <span style={{ color: "#9ca3af", transform: open ? "rotate(180deg)" : "none",
          transition: "transform .15s", fontSize: 10, marginLeft: 4 }}>▼</span>
      </button>

      {/* 드롭다운 */}
      {open && (
        <div style={{
          position: "absolute", top: "calc(100% + 4px)", left: 0, right: 0, zIndex: 500,
          background: "#fff", border: "1.5px solid #e5e7eb", borderRadius: 10,
          boxShadow: "0 8px 30px rgba(0,0,0,0.12)", maxHeight: 360, overflow: "hidden",
          display: "flex", flexDirection: "column",
        }}>
          {/* 검색 */}
          <div style={{ padding: "10px 10px 6px", borderBottom: "1px solid #f3f4f6" }}>
            <div style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 8, top: "50%",
                transform: "translateY(-50%)", color: "#9ca3af" }}><Icons.Search /></div>
              <input value={search} onChange={e => setSearch(e.target.value)}
                placeholder="창고 검색..." autoFocus
                style={{ width: "100%", height: 32, border: "1px solid #e5e7eb",
                  borderRadius: 6, padding: "0 8px 0 28px", fontSize: 12, outline: "none",
                  boxSizing: "border-box" }} />
            </div>
          </div>

          {/* 창고 목록 */}
          <div style={{ overflowY: "auto", flex: 1 }}>
            {/* 선택 해제 옵션 */}
            {value && (
              <div onClick={() => { onChange(""); setOpen(false); setSearch(""); }}
                style={{ padding: "8px 12px", cursor: "pointer", fontSize: 12,
                  color: "#9ca3af", borderBottom: "1px solid #f9fafb",
                  display: "flex", alignItems: "center", gap: 6 }}
                onMouseEnter={e => e.currentTarget.style.background = "#f9fafb"}
                onMouseLeave={e => e.currentTarget.style.background = "transparent"}>
                <Icons.X /> 선택 해제
              </div>
            )}

            {Object.entries(TYPE_LABEL).map(([type, typeLabel]) => {
              const list = grouped[type];
              if (!list || list.length === 0) return null;
              return (
                <div key={type}>
                  {/* 섹션 헤더 */}
                  <div style={{ padding: "6px 12px 3px", fontSize: 10, fontWeight: 700,
                    color: TYPE_COLOR[type] || "#6b7280",
                    background: "#fafafa", letterSpacing: "0.05em" }}>
                    {typeLabel} ({list.length})
                  </div>
                  {list.map(wh => (
                    <div key={wh.code}
                      onClick={() => { onChange(wh.code); setOpen(false); setSearch(""); }}
                      style={{ padding: "9px 12px", cursor: "pointer",
                        display: "flex", alignItems: "center", gap: 8,
                        background: wh.code === value ? "#eff6ff" : "transparent",
                        transition: "background .1s" }}
                      onMouseEnter={e => { if (wh.code !== value) e.currentTarget.style.background = "#f9fafb"; }}
                      onMouseLeave={e => { e.currentTarget.style.background = wh.code === value ? "#eff6ff" : "transparent"; }}>
                      <span style={{ width: 7, height: 7, borderRadius: "50%", flexShrink: 0,
                        background: TYPE_COLOR[wh.type] || "#6b7280" }} />
                      <span style={{ flex: 1, fontSize: 13, color: "#111827",
                        overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {wh.name}
                      </span>
                      {wh.code === value && (
                        <span style={{ color: "#3b82f6", flexShrink: 0 }}><Icons.Check /></span>
                      )}
                    </div>
                  ))}
                </div>
              );
            })}

            {filtered.length === 0 && (
              <div style={{ padding: "20px", textAlign: "center", color: "#9ca3af", fontSize: 13 }}>
                검색 결과 없음
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ── 상품 검색 ─────────────────────────────────────────────────────────────────
function ProductSearch({ onSelect }) {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const timer = useRef(null);

  useEffect(() => {
    clearTimeout(timer.current);
    if (keyword.length < 2) { setResults([]); return; }
    timer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await apiFetch(`${API_BASE}/inventory/products/search?keyword=${encodeURIComponent(keyword)}`);
        setResults(data.slice(0, 15));
      } catch { setResults([]); }
      finally { setLoading(false); }
    }, 300);
  }, [keyword]);

  return (
    <div>
      <div style={{ position: "relative" }}>
        <div style={{ position: "absolute", left: 10, top: "50%",
          transform: "translateY(-50%)", color: "#9ca3af" }}><Icons.Search /></div>
        <input value={keyword} onChange={e => setKeyword(e.target.value)}
          placeholder="상품명, SKU, 바코드로 검색..."
          style={{ width: "100%", height: 42, border: "1.5px solid #e5e7eb", borderRadius: 8,
            padding: "0 12px 0 34px", fontSize: 13, outline: "none",
            boxSizing: "border-box", background: "#fafafa" }}
          onFocus={e => e.target.style.borderColor = "#3b82f6"}
          onBlur={e => e.target.style.borderColor = "#e5e7eb"} />
        {keyword && (
          <button onClick={() => { setKeyword(""); setResults([]); }}
            style={{ position: "absolute", right: 10, top: "50%", transform: "translateY(-50%)",
              border: "none", background: "none", cursor: "pointer", color: "#9ca3af" }}>
            <Icons.X />
          </button>
        )}
      </div>

      {loading && (
        <div style={{ padding: "12px", textAlign: "center", color: "#9ca3af", fontSize: 12 }}>검색 중...</div>
      )}

      {results.length > 0 && (
        <div style={{ border: "1px solid #e5e7eb", borderRadius: 8, marginTop: 4,
          maxHeight: 280, overflowY: "auto", background: "#fff",
          boxShadow: "0 4px 16px rgba(0,0,0,0.08)" }}>
          {results.map(p => (
            <div key={p.productId} onClick={() => { onSelect(p); setKeyword(""); setResults([]); }}
              style={{ padding: "10px 14px", cursor: "pointer", borderBottom: "1px solid #f9fafb",
                transition: "background .1s" }}
              onMouseEnter={e => e.currentTarget.style.background = "#f0f9ff"}
              onMouseLeave={e => e.currentTarget.style.background = "transparent"}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#111827" }}>{p.productName}</div>
              <div style={{ fontSize: 11, color: "#9ca3af", marginTop: 2 }}>
                SKU: {p.sku} | 바코드: {p.barcode} | 총재고: {p.totalStock}개
              </div>
              <div style={{ fontSize: 11, color: "#6b7280", marginTop: 1 }}>
                안양: {p.warehouseStockAnyang ?? 0} | 이천: {p.warehouseStockIcheon ?? 0} | 부천: {p.warehouseStockBucheon ?? 0}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────────
export default function InboundOutbound() {
  const [tab,        setTab]        = useState("IN"); // IN | OUT
  const [warehouses, setWarehouses] = useState([]);
  const [whLoading,  setWhLoading]  = useState(true);

  // 폼 상태
  const [product,   setProduct]   = useState(null);
  const [warehouse, setWarehouse] = useState("");
  const [quantity,  setQuantity]  = useState(1);
  const [location,  setLocation]  = useState("");
  const [notes,     setNotes]     = useState("");
  const [submitting, setSubmitting] = useState(false);

  // 최근 거래내역
  const [transactions, setTransactions] = useState([]);
  const [txLoading,    setTxLoading]    = useState(false);

  const [toast, setToast] = useState(null);

  // ── 창고 목록 로드 (활성 창고만) ──────────────────────────────────────────
  const loadWarehouses = useCallback(async () => {
    try {
      setWhLoading(true);
      const data = await apiFetch(`${API_BASE}/warehouses/active`);
      setWarehouses(data);
    } catch (e) {
      showToast("창고 목록 로드 실패: " + e.message, false);
    } finally {
      setWhLoading(false);
    }
  }, []);

  // ── 최근 거래내역 로드 ─────────────────────────────────────────────────────
  const loadTransactions = useCallback(async () => {
    try {
      setTxLoading(true);
      const data = await apiFetch(`${API_BASE}/inventory/transactions?limit=30`);
      setTransactions(data);
    } catch {
      setTransactions([]);
    } finally {
      setTxLoading(false);
    }
  }, []);

  useEffect(() => { loadWarehouses(); loadTransactions(); }, [loadWarehouses, loadTransactions]);

  const showToast = (msg, ok = true) => {
    setToast({ msg, ok });
    setTimeout(() => setToast(null), 3000);
  };

  const reset = () => {
    setProduct(null);
    setWarehouse("");
    setQuantity(1);
    setLocation("");
    setNotes("");
  };

  // ── 입고 처리 ─────────────────────────────────────────────────────────────
  const handleInbound = async () => {
    if (!product || !warehouse || quantity < 1) {
      showToast("상품, 창고, 수량을 모두 입력하세요", false); return;
    }
    setSubmitting(true);
    try {
      await apiFetch(`${API_BASE}/inventory/inbound-warehouse`, {
        method: "POST",
        body: JSON.stringify({
          productId: product.productId,
          quantity,
          warehouse, // ✅ 창고 코드 전송
          location,
          notes,
        }),
      });
      const whName = warehouses.find(w => w.code === warehouse)?.name || warehouse;
      showToast(`✅ 입고 완료: ${product.productName} ${quantity}개 → ${whName}`);
      reset();
      loadTransactions();
    } catch (e) {
      showToast("입고 실패: " + e.message, false);
    } finally {
      setSubmitting(false);
    }
  };

  // ── 출고 처리 ─────────────────────────────────────────────────────────────
  const handleOutbound = async () => {
    if (!product || !warehouse || quantity < 1) {
      showToast("상품, 창고, 수량을 모두 입력하세요", false); return;
    }
    setSubmitting(true);
    try {
      await apiFetch(`${API_BASE}/inventory/outbound-warehouse`, {
        method: "POST",
        body: JSON.stringify({
          productId: product.productId,
          quantity,
          warehouse, // ✅ 창고 코드 전송
          notes,
        }),
      });
      const whName = warehouses.find(w => w.code === warehouse)?.name || warehouse;
      showToast(`✅ 출고 완료: ${product.productName} ${quantity}개 ← ${whName}`);
      reset();
      loadTransactions();
    } catch (e) {
      showToast("출고 실패: " + e.message, false);
    } finally {
      setSubmitting(false);
    }
  };

  const isIN  = tab === "IN";
  const accentColor = isIN ? "#10b981" : "#ef4444";

  return (
    <div style={{ padding: "24px", maxWidth: 1100, margin: "0 auto",
      fontFamily: "'Pretendard','Apple SD Gothic Neo',sans-serif" }}>

      {/* 헤더 */}
      <div style={{ marginBottom: 22 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: "#111827", margin: 0 }}>입출고 관리</h1>
        <p style={{ color: "#6b7280", fontSize: 13, marginTop: 4 }}>
          창고 관리에서 생성된 창고가 아래 목록에 즉시 반영됩니다
        </p>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>

        {/* ── 좌측: 입출고 폼 ── */}
        <div>
          {/* 탭 */}
          <div style={{ display: "flex", background: "#f3f4f6", borderRadius: 10,
            padding: 4, marginBottom: 20 }}>
            {[["IN", "📦 입고", "#10b981"], ["OUT", "📤 출고", "#ef4444"]].map(([t, l, c]) => (
              <button key={t} onClick={() => { setTab(t); reset(); }} style={{
                flex: 1, height: 40, border: "none", borderRadius: 8, cursor: "pointer",
                fontSize: 14, fontWeight: 700, transition: "all .15s",
                background: tab === t ? "#fff" : "transparent",
                color: tab === t ? c : "#6b7280",
                boxShadow: tab === t ? "0 1px 4px rgba(0,0,0,0.1)" : "none",
              }}>{l}</button>
            ))}
          </div>

          {/* 폼 카드 */}
          <div style={{ background: "#fff", border: `1.5px solid ${accentColor}22`,
            borderRadius: 14, padding: 20,
            boxShadow: `0 2px 20px ${accentColor}11` }}>

            {/* 상단 액센트 바 */}
            <div style={{ height: 3, background: accentColor, borderRadius: 3, marginBottom: 20 }} />

            {/* 1. 상품 검색 */}
            <div style={{ marginBottom: 16 }}>
              <Label>① 상품 선택</Label>
              {product ? (
                <div style={{ background: "#f0fdf4", border: "1.5px solid #10b981",
                  borderRadius: 8, padding: "12px 14px",
                  display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#111827" }}>
                      {product.productName}
                    </div>
                    <div style={{ fontSize: 12, color: "#6b7280", marginTop: 4 }}>
                      SKU: {product.sku} | 총재고: <strong>{product.totalStock}개</strong>
                    </div>
                    <div style={{ fontSize: 11, color: "#9ca3af", marginTop: 2 }}>
                      안양:{product.warehouseStockAnyang ?? 0} | 이천:{product.warehouseStockIcheon ?? 0} | 부천:{product.warehouseStockBucheon ?? 0}
                    </div>
                  </div>
                  <button onClick={() => setProduct(null)}
                    style={{ border: "none", background: "none", cursor: "pointer",
                      color: "#9ca3af", padding: 4 }}><Icons.X /></button>
                </div>
              ) : (
                <ProductSearch onSelect={setProduct} />
              )}
            </div>

            {/* 2. 창고 선택 */}
            <div style={{ marginBottom: 16 }}>
              <Label>
                ② 창고 선택
                {whLoading && <span style={{ fontSize: 11, color: "#9ca3af", marginLeft: 6 }}>로딩 중...</span>}
                <span style={{ fontSize: 11, color: "#9ca3af", marginLeft: 6 }}>
                  (활성 창고 {warehouses.length}개)
                </span>
              </Label>
              <WarehouseSelect
                warehouses={warehouses}
                value={warehouse}
                onChange={setWarehouse}
                placeholder={whLoading ? "창고 목록 로딩 중..." : "창고를 선택하세요"}
              />
            </div>

            {/* 3. 수량 */}
            <div style={{ marginBottom: 16 }}>
              <Label>③ 수량</Label>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <button onClick={() => setQuantity(q => Math.max(1, q - 1))}
                  style={qBtn}> − </button>
                <input type="number" value={quantity} min={1}
                  onChange={e => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                  style={{ width: 80, height: 40, border: "1.5px solid #e5e7eb", borderRadius: 8,
                    textAlign: "center", fontSize: 16, fontWeight: 700, outline: "none" }} />
                <button onClick={() => setQuantity(q => q + 1)} style={qBtn}> + </button>
                <span style={{ fontSize: 13, color: "#6b7280" }}>개</span>
              </div>
            </div>

            {/* 4. 위치/메모 */}
            {isIN && (
              <div style={{ marginBottom: 16 }}>
                <Label>④ 위치 (선택)</Label>
                <input value={location} onChange={e => setLocation(e.target.value)}
                  placeholder="예: A-1-3 (창고 내 위치)"
                  style={inpSt} />
              </div>
            )}
            <div style={{ marginBottom: 20 }}>
              <Label>{isIN ? "⑤" : "④"} 비고 (선택)</Label>
              <textarea value={notes} onChange={e => setNotes(e.target.value)}
                placeholder={isIN ? "입고 사유 또는 메모" : "출고 사유 또는 주문번호"}
                style={{ ...inpSt, height: 64, resize: "none", paddingTop: 10 }} />
            </div>

            {/* 제출 버튼 */}
            <button onClick={isIN ? handleInbound : handleOutbound}
              disabled={submitting || !product || !warehouse}
              style={{
                width: "100%", height: 46, border: "none", borderRadius: 10,
                fontSize: 15, fontWeight: 800, cursor: "pointer", color: "#fff",
                background: (!product || !warehouse)
                  ? "#e5e7eb"
                  : isIN
                    ? "linear-gradient(135deg,#10b981,#059669)"
                    : "linear-gradient(135deg,#ef4444,#dc2626)",
                transition: "all .15s",
                boxShadow: (!product || !warehouse) ? "none"
                  : `0 3px 12px ${accentColor}44`,
              }}>
              {submitting ? "처리 중..." : isIN ? `📦 입고 처리 (${quantity}개)` : `📤 출고 처리 (${quantity}개)`}
            </button>

            {(!product || !warehouse) && (
              <div style={{ textAlign: "center", fontSize: 12, color: "#9ca3af", marginTop: 8 }}>
                {!product ? "상품을 선택해주세요" : "창고를 선택해주세요"}
              </div>
            )}
          </div>
        </div>

        {/* ── 우측: 최근 거래내역 ── */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center",
            marginBottom: 14 }}>
            <div style={{ fontSize: 15, fontWeight: 700, color: "#111827",
              display: "flex", alignItems: "center", gap: 7 }}>
              <Icons.History /> 최근 거래내역
            </div>
            <button onClick={loadTransactions} disabled={txLoading}
              style={{ padding: "5px 12px", border: "1px solid #e5e7eb", borderRadius: 6,
                background: "#fff", cursor: "pointer", fontSize: 12, color: "#6b7280" }}>
              새로고침
            </button>
          </div>

          <div style={{ background: "#fff", border: "1px solid #f3f4f6", borderRadius: 14,
            overflow: "hidden", maxHeight: 600, overflowY: "auto" }}>
            {txLoading ? (
              <div style={{ textAlign: "center", padding: "40px 0", color: "#9ca3af" }}>로딩 중...</div>
            ) : transactions.length === 0 ? (
              <div style={{ textAlign: "center", padding: "40px 0", color: "#9ca3af" }}>
                <div style={{ fontSize: 32, marginBottom: 8 }}>📋</div>
                <div>거래내역이 없습니다</div>
              </div>
            ) : (
              transactions.map((tx, idx) => {
                const isIn = tx.transactionType === "IN" || tx.transactionType === "ADJUST";
                return (
                  <div key={tx.transactionId || idx} style={{
                    padding: "12px 16px",
                    borderBottom: idx < transactions.length - 1 ? "1px solid #f9fafb" : "none",
                    display: "flex", alignItems: "flex-start", gap: 10,
                  }}>
                    {/* 타입 뱃지 */}
                    <div style={{
                      width: 36, height: 36, borderRadius: 8, flexShrink: 0,
                      display: "flex", alignItems: "center", justifyContent: "center",
                      background: isIn ? "#ecfdf5" : "#fef2f2",
                      color: isIn ? "#10b981" : "#ef4444",
                      fontSize: 16,
                    }}>
                      {tx.transactionType === "IN" ? "↓" : tx.transactionType === "OUT" ? "↑" : "⇆"}
                    </div>

                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", justifyContent: "space-between",
                        alignItems: "flex-start", gap: 8 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#111827",
                          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {tx.productName}
                        </div>
                        <div style={{ fontSize: 13, fontWeight: 800, flexShrink: 0,
                          color: isIn ? "#10b981" : "#ef4444" }}>
                          {isIn ? "+" : "-"}{tx.quantity}개
                        </div>
                      </div>
                      <div style={{ fontSize: 11, color: "#9ca3af", marginTop: 2 }}>
                        {tx.sku} | {tx.beforeStock} → {tx.afterStock}개
                      </div>
                      {tx.notes && (
                        <div style={{ fontSize: 11, color: "#6b7280", marginTop: 2,
                          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {tx.notes}
                        </div>
                      )}
                      <div style={{ fontSize: 10, color: "#d1d5db", marginTop: 2 }}>
                        {tx.createdAt ? new Date(tx.createdAt).toLocaleString("ko-KR") : ""}
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <div style={{ position: "fixed", bottom: 24, left: "50%", transform: "translateX(-50%)",
          background: toast.ok ? "#111827" : "#ef4444", color: "#fff",
          padding: "12px 22px", borderRadius: 10, fontSize: 13, fontWeight: 600,
          zIndex: 9999, boxShadow: "0 4px 20px rgba(0,0,0,0.25)",
          maxWidth: "90vw", animation: "toastIn .2s ease" }}>
          {toast.msg}
        </div>
      )}

      <style>{`
        @keyframes toastIn {
          from { opacity:0; transform:translateX(-50%) translateY(8px); }
          to   { opacity:1; transform:translateX(-50%) translateY(0); }
        }
        * { box-sizing:border-box; }
        input:focus, textarea:focus { border-color:#3b82f6 !important; outline:none; }
      `}</style>
    </div>
  );
}

const Label = ({ children }) => (
  <div style={{ fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 7 }}>{children}</div>
);

const inpSt = { width: "100%", height: 40, border: "1.5px solid #e5e7eb", borderRadius: 8,
  padding: "0 12px", fontSize: 13, outline: "none", background: "#fafafa", display: "block" };

const qBtn = { width: 40, height: 40, border: "1.5px solid #e5e7eb", borderRadius: 8,
  background: "#fff", cursor: "pointer", fontSize: 18, fontWeight: 700,
  display: "flex", alignItems: "center", justifyContent: "center", color: "#374151" };
