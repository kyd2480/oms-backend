import { useState } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '') ||
                 'https://oms-backend-production-8a38.up.railway.app';

// 우체국 직접 조회 URL
const POST_TRACK_URL = (no) =>
  `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${no}&displayHeader=N`;

const S = {
  page: { padding:'1.5rem', background:'#f0f2f5', minHeight:'100vh',
          fontFamily:"'Malgun Gothic','맑은 고딕',sans-serif", fontSize:'0.85rem' },
  card: { background:'#fff', borderRadius:6, padding:'1.25rem',
          boxShadow:'0 1px 4px rgba(0,0,0,0.08)', marginBottom:'1rem' },
  inp:  { padding:'6px 10px', border:'1px solid #ccc', borderRadius:4,
          fontSize:'0.85rem', outline:'none', color:'#111' },
  btn:  (bg, color='#fff') => ({
    padding:'6px 16px', background:bg, color,
    border:'none', borderRadius:4, cursor:'pointer',
    fontSize:'0.83rem', fontWeight:600,
  }),
};

/* 상태 → 색상 */
function statusColor(status) {
  if (!status) return { bg:'#f5f5f5', color:'#888' };
  if (status.includes('배달완료') || status.includes('수취인배달')) return { bg:'#e8f5e9', color:'#2e7d32' };
  if (status.includes('배달중') || status.includes('집배원')) return { bg:'#e3f2fd', color:'#1565c0' };
  if (status.includes('도착') || status.includes('접수')) return { bg:'#fff8e1', color:'#f57f17' };
  if (status.includes('출발')) return { bg:'#f3e5f5', color:'#7b1fa2' };
  return { bg:'#f5f6fa', color:'#555' };
}

export default function DeliveryTracking({ initialTracking }) {
  const [trackingNo, setTrackingNo] = useState(initialTracking?.trackingNo || '');
  const [carrierCode, setCarrierCode] = useState(initialTracking?.carrierCode || 'POST');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const search = async (tNo, cCode) => {
    const no = tNo ?? trackingNo;
    const cc = cCode ?? carrierCode;
    if (!no.trim()) { setError('송장번호를 입력해주세요.'); return; }
    setLoading(true); setError(''); setResult(null);
    try {
      const res = await fetch(
        `${API_BASE}/api/delivery/track?trackingNo=${encodeURIComponent(no.trim())}&carrierCode=${cc}`
      );
      const data = await res.json();
      if (!data.success) {
        // API 실패 시 직접 조회 링크 안내
        setError(`배송 정보를 서버에서 가져올 수 없습니다. 아래 버튼으로 직접 조회하세요.`);
        setResult({ directLink: true, trackingNo: no, carrierCode: cc });
      } else {
        setResult(data);
      }
    } catch (e) {
      setError('서버 연결 오류. 아래 버튼으로 직접 조회하세요.');
      setResult({ directLink: true, trackingNo: no, carrierCode: cc });
    } finally {
      setLoading(false);
    }
  };

  // 초기 props로 자동 조회
  useState(() => {
    if (initialTracking?.trackingNo && initialTracking?.carrierCode) {
      search(initialTracking.trackingNo, initialTracking.carrierCode);
    }
  }, []);

  return (
    <div style={S.page}>
      {/* 헤더 */}
      <div style={S.card}>
        <h1 style={{ fontSize:'1.3rem', fontWeight:700, color:'#1a1a1a', margin:'0 0 4px' }}>
          📦 배송 흐름 조회
        </h1>
        <div style={{ fontSize:'0.8rem', color:'#888' }}>
          송장번호로 실시간 배송 상태를 확인합니다 (우체국 지원)
        </div>
      </div>

      {/* 검색 */}
      <div style={S.card}>
        <div style={{ display:'flex', gap:8, alignItems:'center', flexWrap:'wrap' }}>
          <select value={carrierCode} onChange={e => setCarrierCode(e.target.value)}
            style={{ ...S.inp, minWidth:130 }}>
            <option value="POST">🏣 우체국택배</option>
            <option value="CJ" disabled>CJ대한통운 (준비중)</option>
            <option value="HANJIN" disabled>한진택배 (준비중)</option>
            <option value="LOTTE" disabled>롯데택배 (준비중)</option>
          </select>
          <input value={trackingNo} onChange={e => setTrackingNo(e.target.value)}
            onKeyDown={e => e.key==='Enter' && search()}
            placeholder="송장번호 입력 (예: 6080192155227)"
            style={{ ...S.inp, minWidth:280 }}/>
          <button onClick={() => search()} disabled={loading}
            style={S.btn('#1565c0')}>
            {loading ? '⏳ 조회 중...' : '🔍 조회'}
          </button>
          <button onClick={() => { setTrackingNo(''); setResult(null); setError(''); }}
            style={S.btn('#757575')}>
            초기화
          </button>
        </div>
        {error && (
          <div style={{ marginTop:10, padding:'8px 12px', background:'#ffebee',
            border:'1px solid #ef9a9a', borderRadius:4, color:'#c62828', fontSize:'0.82rem' }}>
            ⚠ {error}
          </div>
        )}
      </div>

      {/* 직접 조회 링크 */}
      {result?.directLink && (
        <div style={{ ...S.card, border:'1px solid #90caf9' }}>
          <div style={{ fontWeight:700, fontSize:'0.95rem', color:'#1565c0', marginBottom:10 }}>
            🔗 우체국 사이트에서 직접 조회
          </div>
          <div style={{ fontSize:'0.82rem', color:'#555', marginBottom:14, lineHeight:1.6 }}>
            현재 배포 환경에서는 우체국 API에 직접 접근이 제한됩니다.<br/>
            아래 버튼을 클릭하면 우체국 공식 사이트에서 바로 조회할 수 있습니다.
          </div>
          <div style={{ display:'flex', gap:10, flexWrap:'wrap' }}>
            {result.carrierCode === 'POST' && (
              <button
                onClick={() => window.open(
                  POST_TRACK_URL(result.trackingNo),
                  'delivery_track',
                  'width=900,height=700,scrollbars=yes,resizable=yes,left=200,top=100'
                )}
                style={{ ...S.btn('#e53935'), display:'inline-flex', alignItems:'center', gap:6 }}>
                🏣 우체국 배송조회 열기
              </button>
            )}
            <div style={{ padding:'6px 14px', background:'#f5f5f5', borderRadius:4,
              fontSize:'0.82rem', color:'#555', display:'flex', alignItems:'center' }}>
              송장번호: <strong style={{ marginLeft:6, color:'#1565c0' }}>{result.trackingNo}</strong>
            </div>
          </div>
        </div>
      )}

      {/* 결과 */}
      {result && !result.directLink && (
        <>
          {/* 기본 정보 */}
          <div style={S.card}>
            <div style={{ display:'flex', justifyContent:'space-between',
              alignItems:'center', marginBottom:'1rem', flexWrap:'wrap', gap:8 }}>
              <div>
                <div style={{ fontSize:'1rem', fontWeight:700, color:'#1a1a1a' }}>
                  {result.carrierName} · {result.trackingNo}
                </div>
                <div style={{ fontSize:'0.78rem', color:'#888', marginTop:2 }}>
                  {result.sender && `발송인: ${result.sender}`}
                  {result.receiver && ` → 수취인: ${result.receiver}`}
                </div>
              </div>
              {result.currentStatus && (
                <span style={{
                  ...statusColor(result.currentStatus),
                  padding:'6px 16px', borderRadius:20,
                  fontWeight:700, fontSize:'0.85rem',
                }}>
                  {result.currentStatus}
                </span>
              )}
            </div>

            <div style={{ display:'flex', gap:16, flexWrap:'wrap' }}>
              {[
                ['발송일',   result.sentDate     || '-'],
                ['배달완료', result.deliveryDate  || '-'],
                ['발송인',   result.sender        || '-'],
                ['수취인',   result.receiver      || '-'],
              ].map(([k,v]) => (
                <div key={k} style={{ background:'#f8f9ff', borderRadius:4,
                  padding:'6px 12px', minWidth:120 }}>
                  <div style={{ fontSize:'0.69rem', color:'#888', marginBottom:2 }}>{k}</div>
                  <div style={{ fontWeight:600, color:'#111', fontSize:'0.8rem' }}>{v}</div>
                </div>
              ))}
            </div>
          </div>

          {/* 배송 흐름 타임라인 */}
          <div style={S.card}>
            <div style={{ fontWeight:700, fontSize:'0.92rem', color:'#1565c0',
              marginBottom:'1rem', borderBottom:'1px solid #e3f2fd', paddingBottom:8 }}>
              배송 흐름 ({result.steps.length}단계)
            </div>

            {result.steps.length === 0 ? (
              <div style={{ textAlign:'center', padding:'2rem', color:'#bbb' }}>
                배송 단계 정보가 없습니다
              </div>
            ) : (
              <div style={{ position:'relative' }}>
                {result.steps.map((step, idx) => {
                  const isFirst = idx === 0;
                  const sc = statusColor(step.status);
                  return (
                    <div key={idx} style={{ display:'flex', gap:16, marginBottom:0,
                      position:'relative' }}>
                      {/* 타임라인 선 */}
                      <div style={{ display:'flex', flexDirection:'column',
                        alignItems:'center', width:24, flexShrink:0 }}>
                        <div style={{
                          width:14, height:14, borderRadius:'50%', flexShrink:0,
                          background: isFirst ? '#1565c0' : '#ccc',
                          border: isFirst ? '3px solid #bbdefb' : '2px solid #eee',
                          marginTop:14, zIndex:1,
                        }}/>
                        {idx < result.steps.length-1 && (
                          <div style={{ flex:1, width:2, background:'#e0e0e0',
                            minHeight:24, marginTop:2 }}/>
                        )}
                      </div>

                      {/* 내용 */}
                      <div style={{
                        flex:1, padding:'10px 14px', marginBottom:4, borderRadius:6,
                        background: isFirst ? '#e3f2fd' : '#f8f9ff',
                        border: isFirst ? '1px solid #90caf9' : '1px solid #edf0f7',
                      }}>
                        <div style={{ display:'flex', justifyContent:'space-between',
                          alignItems:'center', flexWrap:'wrap', gap:4 }}>
                          <span style={{
                            ...sc,
                            padding:'2px 10px', borderRadius:10,
                            fontSize:'0.74rem', fontWeight:700,
                          }}>
                            {step.status || '-'}
                          </span>
                          <span style={{ fontSize:'0.74rem', color:'#888' }}>
                            {step.dateTime?.trim() || '-'}
                          </span>
                        </div>
                        <div style={{ marginTop:4, fontSize:'0.82rem',
                          fontWeight: isFirst ? 700 : 400, color:'#222' }}>
                          📍 {step.location || '-'}
                        </div>
                        {step.detail && (
                          <div style={{ fontSize:'0.76rem', color:'#666', marginTop:2 }}>
                            {step.detail}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}

      {/* 초기 안내 */}
      {!result && !loading && !error && (
        <div style={{ ...S.card, textAlign:'center', padding:'3rem', color:'#bbb' }}>
          <div style={{ fontSize:'3rem', marginBottom:'0.75rem' }}>📦</div>
          <div style={{ fontSize:'0.95rem', color:'#888' }}>
            송장번호를 입력하고 조회 버튼을 누르세요
          </div>
          <div style={{ fontSize:'0.78rem', color:'#bbb', marginTop:6 }}>
            현재 우체국택배 지원 · 인증키 설정 필요
          </div>
        </div>
      )}
    </div>
  );
}
