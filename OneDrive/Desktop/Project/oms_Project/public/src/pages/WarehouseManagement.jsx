import { useState, useEffect, useCallback } from "react";

const API = "https://oms-backend-production-8a38.up.railway.app/api/warehouses";

const TYPE_META = {
  REAL:    { label: "실제창고",  color: "#2563eb", bg: "#eff6ff", dot: "#2563eb" },
  TRANSIT: { label: "이동중",    color: "#d97706", bg: "#fffbeb", dot: "#d97706" },
  RETURN:  { label: "반품창고",  color: "#7c3aed", bg: "#f5f3ff", dot: "#7c3aed" },
  DEFECT:  { label: "불량/폐기", color: "#dc2626", bg: "#fef2f2", dot: "#dc2626" },
  SPECIAL: { label: "특수창고",  color: "#059669", bg: "#ecfdf5", dot: "#059669" },
  VIRTUAL: { label: "가상재고",  color: "#4b5563", bg: "#f9fafb", dot: "#4b5563" },
  UNUSED:  { label: "미사용",    color: "#9ca3af", bg: "#f3f4f6", dot: "#d1d5db" },
};

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
  if (res.status === 204) return null;
  return res.json();
}

// ── 아이콘 ───────────────────────────────────────────────────────────────────
const Icons = {
  Plus:     () => <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>,
  Edit:     () => <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>,
  Trash:    () => <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>,
  Power:    () => <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18.36 6.64a9 9 0 1 1-12.73 0"/><line x1="12" y1="2" x2="12" y2="12"/></svg>,
  Search:   () => <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>,
  X:        () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>,
  Refresh:  () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>,
  Warehouse:() => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>,
};

// ── 창고 카드 ────────────────────────────────────────────────────────────────
function WarehouseCard({ wh, onEdit, onDelete, onToggle }) {
  const [hov, setHov] = useState(false);
  const meta = TYPE_META[wh.type] || TYPE_META.REAL;

  return (
    <div
      onMouseEnter={() => setHov(true)} onMouseLeave={() => setHov(false)}
      style={{
        background: wh.isActive ? "#fff" : "#f9fafb",
        border: `1.5px solid ${hov ? meta.color : "#e5e7eb"}`,
        borderRadius: "10px", padding: "13px 15px",
        display: "flex", alignItems: "center", gap: "10px",
        transition: "all .15s",
        boxShadow: hov ? `0 2px 12px ${meta.color}22` : "0 1px 2px rgba(0,0,0,0.04)",
        opacity: wh.isActive ? 1 : 0.55,
      }}
    >
      <div style={{ width: 9, height: 9, borderRadius: "50%", flexShrink: 0,
        background: wh.isActive ? meta.dot : "#d1d5db" }} />

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: "13.5px", fontWeight: "600",
          color: wh.isActive ? "#111827" : "#9ca3af",
          whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {wh.name}
        </div>
        <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "1px" }}>{wh.code}</div>
      </div>

      <span style={{ fontSize: "10px", fontWeight: "700", padding: "2px 7px", borderRadius: "20px",
        background: meta.bg, color: meta.color, flexShrink: 0 }}>
        {meta.label}
      </span>

      <div style={{ display: "flex", gap: "3px", flexShrink: 0,
        opacity: hov ? 1 : 0, transition: "opacity .15s" }}>
        <Btn title="이름 변경" c="#3b82f6" onClick={() => onEdit(wh)}><Icons.Edit /></Btn>
        <Btn title={wh.isActive ? "비활성화" : "활성화"} c="#f59e0b" onClick={() => onToggle(wh)}><Icons.Power /></Btn>
        <Btn title="삭제" c="#ef4444" onClick={() => onDelete(wh)}><Icons.Trash /></Btn>
      </div>
    </div>
  );
}

function Btn({ title, c, onClick, children, disabled }) {
  const [h, setH] = useState(false);
  return (
    <button title={title} onClick={onClick} disabled={disabled}
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{ width: 27, height: 27, border: "none", borderRadius: "6px",
        background: h ? c : "#f3f4f6", color: h ? "#fff" : "#6b7280",
        cursor: disabled ? "not-allowed" : "pointer", padding: 0,
        display: "flex", alignItems: "center", justifyContent: "center",
        transition: "all .12s", opacity: disabled ? 0.4 : 1 }}>
      {children}
    </button>
  );
}

// ── 모달 ─────────────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }) {
  return (
    <div onClick={e => e.target === e.currentTarget && onClose()}
      style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.45)",
        display: "flex", alignItems: "center", justifyContent: "center",
        zIndex: 1000, padding: 16 }}>
      <div style={{ background: "#fff", borderRadius: "14px", width: "100%", maxWidth: 440,
        boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ padding: "16px 20px", borderBottom: "1px solid #f3f4f6",
          display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <span style={{ fontSize: "15px", fontWeight: "700" }}>{title}</span>
          <button onClick={onClose} style={{ width: 28, height: 28, border: "none",
            borderRadius: "6px", background: "#f3f4f6", cursor: "pointer",
            display: "flex", alignItems: "center", justifyContent: "center", color: "#6b7280" }}>
            <Icons.X />
          </button>
        </div>
        <div style={{ padding: 20 }}>{children}</div>
      </div>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────────
export default function WarehouseManagement() {
  const [warehouses, setWarehouses] = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [saving,     setSaving]     = useState(false);
  const [search,     setSearch]     = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [activeFilter, setActiveFilter] = useState("ALL");

  const [editTarget,   setEditTarget]   = useState(null);
  const [editName,     setEditName]     = useState("");
  const [editType,     setEditType]     = useState("");
  const [addModal,     setAddModal]     = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [newWh, setNewWh] = useState({ code: "", name: "", type: "REAL", sortOrder: 999 });
  const [toast, setToast] = useState(null);

  // ── 로드 ──────────────────────────────────────────────────────────────────
  const loadWarehouses = useCallback(async () => {
    try {
      setLoading(true);
      const data = await apiFetch(API);
      setWarehouses(data);
    } catch (e) {
      showToast("창고 목록 로드 실패: " + e.message, false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadWarehouses(); }, [loadWarehouses]);

  const showToast = (msg, ok = true) => {
    setToast({ msg, ok });
    setTimeout(() => setToast(null), 2800);
  };

  // ── 필터 ──────────────────────────────────────────────────────────────────
  const filtered = warehouses.filter(w => {
    const kw = search.toLowerCase();
    const matchKw = !search || w.name.toLowerCase().includes(kw) || w.code.toLowerCase().includes(kw);
    const matchType = typeFilter === "ALL" || w.type === typeFilter;
    const matchActive = activeFilter === "ALL"
      || (activeFilter === "ACTIVE" ? w.isActive : !w.isActive);
    return matchKw && matchType && matchActive;
  });

  // 타입별 그룹
  const grouped = {};
  filtered.forEach(w => {
    if (!grouped[w.type]) grouped[w.type] = [];
    grouped[w.type].push(w);
  });

  // ── 이름/유형 변경 ─────────────────────────────────────────────────────────
  const openEdit = (wh) => {
    setEditTarget(wh);
    setEditName(wh.name);
    setEditType(wh.type);
  };

  const submitEdit = async () => {
    if (!editName.trim()) return;
    setSaving(true);
    try {
      const updated = await apiFetch(`${API}/${editTarget.warehouseId}`, {
        method: "PUT",
        body: JSON.stringify({ name: editName.trim(), type: editType }),
      });
      setWarehouses(prev => prev.map(w => w.warehouseId === updated.warehouseId ? updated : w));
      showToast(`"${updated.name}" 수정 완료`);
      setEditTarget(null);
    } catch (e) {
      showToast("수정 실패: " + e.message, false);
    } finally {
      setSaving(false);
    }
  };

  // ── 활성/비활성 토글 ───────────────────────────────────────────────────────
  const handleToggle = async (wh) => {
    try {
      const updated = await apiFetch(`${API}/${wh.warehouseId}/toggle`, { method: "PATCH" });
      setWarehouses(prev => prev.map(w => w.warehouseId === updated.warehouseId ? updated : w));
      showToast(`"${updated.name}" ${updated.isActive ? "활성화" : "비활성화"} 완료`);
    } catch (e) {
      showToast("상태 변경 실패: " + e.message, false);
    }
  };

  // ── 삭제 ──────────────────────────────────────────────────────────────────
  const handleDelete = async () => {
    setSaving(true);
    try {
      await apiFetch(`${API}/${deleteTarget.warehouseId}`, { method: "DELETE" });
      setWarehouses(prev => prev.filter(w => w.warehouseId !== deleteTarget.warehouseId));
      showToast(`"${deleteTarget.name}" 삭제 완료`, false);
      setDeleteTarget(null);
    } catch (e) {
      showToast("삭제 실패: " + e.message, false);
    } finally {
      setSaving(false);
    }
  };

  // ── 창고 추가 ─────────────────────────────────────────────────────────────
  const handleAdd = async () => {
    if (!newWh.name.trim() || !newWh.code.trim()) return;
    setSaving(true);
    try {
      const created = await apiFetch(API, {
        method: "POST",
        body: JSON.stringify({
          ...newWh,
          code: newWh.code.trim().toUpperCase(),
          name: newWh.name.trim(),
        }),
      });
      setWarehouses(prev => [...prev, created]);
      showToast(`"${created.name}" 창고 추가 완료`);
      setNewWh({ code: "", name: "", type: "REAL", sortOrder: 999 });
      setAddModal(false);
    } catch (e) {
      showToast("추가 실패: " + e.message, false);
    } finally {
      setSaving(false);
    }
  };

  // ── 통계 ──────────────────────────────────────────────────────────────────
  const stats = {
    total:   warehouses.length,
    active:  warehouses.filter(w => w.isActive).length,
    real:    warehouses.filter(w => w.type === "REAL" && w.isActive).length,
    virtual: warehouses.filter(w => w.type === "VIRTUAL" && w.isActive).length,
  };

  return (
    <div style={{ padding: "24px", maxWidth: 1100, margin: "0 auto",
      fontFamily: "'Pretendard','Apple SD Gothic Neo',sans-serif" }}>

      {/* 헤더 */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start",
        flexWrap: "wrap", gap: 12, marginBottom: 22 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: "#111827", margin: 0,
            display: "flex", alignItems: "center", gap: 8 }}>
            <Icons.Warehouse /> 창고 관리
          </h1>
          <p style={{ color: "#6b7280", fontSize: 13, marginTop: 4 }}>
            창고를 생성·수정·삭제하면 입고/출고 페이지에 즉시 반영됩니다
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={loadWarehouses} disabled={loading}
            style={{ height: 38, padding: "0 14px", border: "1.5px solid #e5e7eb",
              borderRadius: 8, background: "#fff", cursor: "pointer",
              display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "#374151" }}>
            <Icons.Refresh /> 새로고침
          </button>
          <button onClick={() => setAddModal(true)}
            style={{ height: 38, padding: "0 16px", border: "none", borderRadius: 8,
              background: "linear-gradient(135deg,#3b82f6,#2563eb)", color: "#fff",
              fontWeight: 700, fontSize: 13, cursor: "pointer",
              display: "flex", alignItems: "center", gap: 6,
              boxShadow: "0 2px 8px rgba(59,130,246,0.3)" }}>
            <Icons.Plus /> 창고 추가
          </button>
        </div>
      </div>

      {/* 통계 */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(120px,1fr))",
        gap: 10, marginBottom: 18 }}>
        {[
          { label: "전체", value: stats.total,   color: "#3b82f6" },
          { label: "활성", value: stats.active,  color: "#10b981" },
          { label: "실제창고", value: stats.real, color: "#6366f1" },
          { label: "가상재고", value: stats.virtual, color: "#f59e0b" },
        ].map(s => (
          <div key={s.label} style={{ background: "#fff", border: "1px solid #f3f4f6",
            borderRadius: 10, padding: "14px 16px" }}>
            <div style={{ fontSize: 26, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#6b7280", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* 검색 + 필터 */}
      <div style={{ background: "#fff", border: "1px solid #f3f4f6", borderRadius: 12,
        padding: "12px 16px", marginBottom: 14,
        display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
        {/* 검색 */}
        <div style={{ position: "relative", flex: 1, minWidth: 200 }}>
          <div style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)",
            color: "#9ca3af", pointerEvents: "none" }}><Icons.Search /></div>
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="창고명 또는 코드 검색..."
            style={{ width: "100%", height: 36, border: "1.5px solid #e5e7eb", borderRadius: 8,
              padding: "0 12px 0 34px", fontSize: 13, outline: "none",
              background: "#fafafa", boxSizing: "border-box" }} />
        </div>

        {/* 타입 필터 */}
        <div style={{ display: "flex", gap: 5, flexWrap: "wrap" }}>
          {[["ALL","전체",null], ...Object.entries(TYPE_META).map(([k,v]) => [k, v.label, v.color])].map(([v,l,c]) => (
            <button key={v} onClick={() => setTypeFilter(v)} style={{
              padding: "4px 10px", border: "1.5px solid", borderRadius: 6,
              fontSize: 11, fontWeight: 600, cursor: "pointer",
              borderColor: typeFilter === v ? (c || "#374151") : "#e5e7eb",
              background: typeFilter === v ? (c || "#374151") : "#fff",
              color: typeFilter === v ? "#fff" : "#6b7280", transition: "all .12s" }}>
              {l}
            </button>
          ))}
        </div>

        {/* 활성 필터 */}
        <div style={{ display: "flex", gap: 5 }}>
          {[["ALL","전체"],["ACTIVE","활성"],["INACTIVE","비활성"]].map(([v,l]) => (
            <button key={v} onClick={() => setActiveFilter(v)} style={{
              padding: "4px 10px", border: "1.5px solid", borderRadius: 6,
              fontSize: 11, fontWeight: 600, cursor: "pointer",
              borderColor: activeFilter === v ? "#374151" : "#e5e7eb",
              background: activeFilter === v ? "#374151" : "#fff",
              color: activeFilter === v ? "#fff" : "#6b7280" }}>
              {l}
            </button>
          ))}
        </div>
      </div>

      {/* 결과 수 */}
      <div style={{ fontSize: 12, color: "#9ca3af", marginBottom: 10 }}>
        {loading ? "로딩 중..." : `${filtered.length}개의 창고`}
      </div>

      {/* 로딩 */}
      {loading && (
        <div style={{ textAlign: "center", padding: "60px 0", color: "#9ca3af" }}>
          <div style={{ fontSize: 32, marginBottom: 8 }}>⏳</div>
          <div>창고 목록 불러오는 중...</div>
        </div>
      )}

      {/* 창고 목록 - 타입별 그룹 */}
      {!loading && typeFilter === "ALL" ? (
        Object.entries(TYPE_META).map(([type, meta]) => {
          const list = grouped[type];
          if (!list || list.length === 0) return null;
          return (
            <div key={type} style={{ marginBottom: 22 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 9 }}>
                <div style={{ width: 8, height: 8, borderRadius: "50%", background: meta.dot }} />
                <span style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>{meta.label}</span>
                <span style={{ fontSize: 12, color: "#9ca3af" }}>({list.length})</span>
              </div>
              <div style={{ display: "grid",
                gridTemplateColumns: "repeat(auto-fill,minmax(290px,1fr))", gap: 7 }}>
                {list.map(wh => (
                  <WarehouseCard key={wh.warehouseId} wh={wh}
                    onEdit={openEdit} onDelete={setDeleteTarget} onToggle={handleToggle} />
                ))}
              </div>
            </div>
          );
        })
      ) : !loading ? (
        <div style={{ display: "grid",
          gridTemplateColumns: "repeat(auto-fill,minmax(290px,1fr))", gap: 7 }}>
          {filtered.map(wh => (
            <WarehouseCard key={wh.warehouseId} wh={wh}
              onEdit={openEdit} onDelete={setDeleteTarget} onToggle={handleToggle} />
          ))}
        </div>
      ) : null}

      {!loading && filtered.length === 0 && (
        <div style={{ textAlign: "center", padding: "60px 0", color: "#9ca3af" }}>
          <div style={{ fontSize: 40, marginBottom: 10 }}>🏭</div>
          <div style={{ fontSize: 15, fontWeight: 600 }}>검색 결과 없음</div>
        </div>
      )}

      {/* ── 이름 변경 모달 ── */}
      {editTarget && (
        <Modal title="창고 수정" onClose={() => setEditTarget(null)}>
          <div style={{ marginBottom: 6, fontSize: 12, color: "#6b7280" }}>창고 코드</div>
          <div style={{ padding: "8px 12px", background: "#f9fafb", borderRadius: 8,
            fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 14, fontFamily: "monospace" }}>
            {editTarget.code}
          </div>
          <div style={{ marginBottom: 6, fontSize: 12, color: "#6b7280" }}>창고명</div>
          <input value={editName} onChange={e => setEditName(e.target.value)}
            onKeyDown={e => e.key === "Enter" && submitEdit()} autoFocus
            style={{ width: "100%", height: 40, border: "1.5px solid #3b82f6", borderRadius: 8,
              padding: "0 12px", fontSize: 14, outline: "none",
              marginBottom: 14, boxSizing: "border-box" }} />
          <div style={{ marginBottom: 6, fontSize: 12, color: "#6b7280" }}>창고 유형</div>
          <select value={editType} onChange={e => setEditType(e.target.value)}
            style={{ width: "100%", height: 40, border: "1.5px solid #e5e7eb", borderRadius: 8,
              padding: "0 12px", fontSize: 13, outline: "none",
              marginBottom: 20, background: "#fafafa", boxSizing: "border-box" }}>
            {Object.entries(TYPE_META).map(([k,v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => setEditTarget(null)} style={grayBtn}>취소</button>
            <button onClick={submitEdit} disabled={saving}
              style={{ ...blueBtn, flex: 1, opacity: saving ? 0.6 : 1 }}>
              {saving ? "저장 중..." : "저장"}
            </button>
          </div>
        </Modal>
      )}

      {/* ── 창고 추가 모달 ── */}
      {addModal && (
        <Modal title="새 창고 추가" onClose={() => setAddModal(false)}>
          <div style={{ display: "flex", flexDirection: "column", gap: 13 }}>
            <div>
              <div style={labelSt}>창고 코드 * <span style={{ color: "#9ca3af", fontWeight: 400 }}>(영문/숫자/_)</span></div>
              <input value={newWh.code}
                onChange={e => setNewWh(p => ({ ...p, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g,"") }))}
                placeholder="예: ICHEON_EXPORT" autoFocus style={inputSt} />
            </div>
            <div>
              <div style={labelSt}>창고명 *</div>
              <input value={newWh.name}
                onChange={e => setNewWh(p => ({ ...p, name: e.target.value }))}
                placeholder="예: 고백창고(이천)-수출" style={inputSt} />
            </div>
            <div>
              <div style={labelSt}>창고 유형 *</div>
              <select value={newWh.type} onChange={e => setNewWh(p => ({ ...p, type: e.target.value }))}
                style={{ ...inputSt, cursor: "pointer" }}>
                {Object.entries(TYPE_META).map(([k,v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            </div>
            <div>
              <div style={labelSt}>정렬 순서</div>
              <input type="number" value={newWh.sortOrder}
                onChange={e => setNewWh(p => ({ ...p, sortOrder: parseInt(e.target.value) || 999 }))}
                style={inputSt} />
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
              <button onClick={() => setAddModal(false)} style={grayBtn}>취소</button>
              <button onClick={handleAdd}
                disabled={saving || !newWh.name.trim() || !newWh.code.trim()}
                style={{ ...blueBtn, flex: 1,
                  opacity: (saving || !newWh.name.trim() || !newWh.code.trim()) ? 0.5 : 1 }}>
                {saving ? "추가 중..." : "추가"}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* ── 삭제 확인 모달 ── */}
      {deleteTarget && (
        <Modal title="창고 삭제" onClose={() => setDeleteTarget(null)}>
          <div style={{ textAlign: "center", padding: "8px 0 20px" }}>
            <div style={{ fontSize: 40, marginBottom: 12 }}>⚠️</div>
            <div style={{ fontSize: 15, fontWeight: 700, marginBottom: 8 }}>정말 삭제하시겠습니까?</div>
            <div style={{ fontSize: 13, color: "#6b7280", lineHeight: 1.7 }}>
              <strong style={{ color: "#111827" }}>"{deleteTarget.name}"</strong><br />
              삭제 후 복구할 수 없습니다.<br />
              해당 창고의 재고 거래 내역은 유지됩니다.
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => setDeleteTarget(null)} style={{ ...grayBtn, flex: 1 }}>취소</button>
            <button onClick={handleDelete} disabled={saving}
              style={{ ...redBtn, flex: 1, opacity: saving ? 0.6 : 1 }}>
              {saving ? "삭제 중..." : "삭제"}
            </button>
          </div>
        </Modal>
      )}

      {/* Toast */}
      {toast && (
        <div style={{ position: "fixed", bottom: 24, left: "50%", transform: "translateX(-50%)",
          background: toast.ok ? "#111827" : "#ef4444", color: "#fff",
          padding: "11px 20px", borderRadius: 10, fontSize: 13, fontWeight: 600,
          zIndex: 9999, boxShadow: "0 4px 20px rgba(0,0,0,0.25)",
          whiteSpace: "nowrap", animation: "toastIn .2s ease" }}>
          {toast.ok ? "✓ " : "✕ "}{toast.msg}
        </div>
      )}

      <style>{`
        @keyframes toastIn {
          from { opacity:0; transform:translateX(-50%) translateY(8px); }
          to   { opacity:1; transform:translateX(-50%) translateY(0); }
        }
        * { box-sizing:border-box; }
        input:focus, select:focus { border-color:#3b82f6 !important; box-shadow:0 0 0 3px rgba(59,130,246,0.12); }
      `}</style>
    </div>
  );
}

const grayBtn = { height: 40, border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700,
  cursor: "pointer", background: "#f3f4f6", color: "#374151", padding: "0 16px" };
const blueBtn = { height: 40, border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700,
  cursor: "pointer", background: "#3b82f6", color: "#fff", padding: "0 16px" };
const redBtn  = { height: 40, border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700,
  cursor: "pointer", background: "#ef4444", color: "#fff", padding: "0 16px" };
const labelSt = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 6 };
const inputSt = { width: "100%", height: 40, border: "1.5px solid #e5e7eb", borderRadius: 8,
  padding: "0 12px", fontSize: 13, outline: "none", background: "#fafafa" };
