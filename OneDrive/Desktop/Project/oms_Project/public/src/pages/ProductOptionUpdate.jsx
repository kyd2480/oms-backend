import { useState } from 'react';

const API_BASE = 'https://oms-backend-production-8a38.up.railway.app/api/products';

export default function ProductOptionUpdate() {
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    if (!file.name.endsWith('.csv')) {
      alert('CSV 파일만 업로드 가능합니다.');
      return;
    }

    console.log('📁 파일 선택:', file.name, file.size, 'bytes');

    setUploading(true);
    setResult(null);

    const formData = new FormData();
    formData.append('file', file);

    try {
      console.log('🚀 업로드 시작:', `${API_BASE}/update-location`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 120000); // 2분 타임아웃

      const response = await fetch(`${API_BASE}/update-location`, {
        method: 'POST',
        body: formData,
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      console.log('📡 응답 상태:', response.status, response.statusText);

      if (response.ok) {
        const data = await response.json();
        console.log('✅ 성공:', data);
        setResult(data);
        
        // 성공 메시지
        if (data.failCount === 0) {
          alert(`✅ 위치 업데이트 완료!\n\n성공: ${data.successCount}개`);
        } else {
          alert(`⚠️ 위치 업데이트 완료\n\n성공: ${data.successCount}개\n실패: ${data.failCount}개\n\n실패한 바코드는 아래 목록을 확인하세요.`);
        }
      } else {
        const error = await response.json();
        console.error('❌ 실패:', error);
        alert('업로드 실패: ' + (error.error || '알 수 없는 오류'));
      }
    } catch (error) {
      console.error('❌ 업로드 오류:', error);

      if (error.name === 'AbortError') {
        alert('⏰ 업로드 시간 초과\n\n파일이 너무 큽니다. 잠시 후 다시 시도해주세요.');
      } else {
        alert('업로드 중 오류가 발생했습니다:\n' + error.message);
      }
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  };

  const handleReset = () => {
    setResult(null);
  };

  return (
    <div style={{ padding: '2rem', background: '#f7fafc', minHeight: '100vh' }}>
      {/* 헤더 */}
      <div style={{
        background: 'white',
        padding: '1.5rem 2rem',
        marginBottom: '1.5rem',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: '700', marginBottom: '0.5rem', color: '#000' }}>
          📝 상품 옵션 수정
        </h1>
        <p style={{ color: '#333', fontSize: '0.875rem' }}>
          CSV 파일로 상품 위치를 일괄 업데이트하세요
        </p>
      </div>

      {/* CSV 업로드 섹션 */}
      <div style={{
        background: 'white',
        padding: '2rem',
        marginBottom: '1.5rem',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '1rem', color: '#000' }}>
          CSV 파일 업로드
        </h2>

        <div style={{
          background: '#fffaf0',
          border: '1px solid #fbd38d',
          borderRadius: '8px',
          padding: '1rem',
          marginBottom: '1rem'
        }}>
          <div style={{ color: '#744210', fontSize: '0.875rem', lineHeight: '1.6' }}>
            <div style={{ fontWeight: '600', marginBottom: '0.5rem', color: '#744210' }}>📋 CSV 파일 형식 안내</div>
            <div style={{ color: '#744210' }}>
              • <strong>A열 (바코드)</strong>: 상품 바코드 번호<br />
              • <strong>B열 (위치)</strong>: 변경할 위치 정보<br />
              • 첫 번째 행은 헤더로 자동 스킵됩니다<br />
              • 데이터는 A2, B2부터 시작합니다<br />
              • 파일 인코딩: EUC-KR (자동 변환됨)
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <label
            htmlFor="csv-upload"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.75rem 1.5rem',
              background: uploading ? '#cbd5e0' : '#3182ce',
              color: 'white',
              borderRadius: '8px',
              cursor: uploading ? 'not-allowed' : 'pointer',
              fontWeight: '600',
              fontSize: '1rem'
            }}
          >
            {uploading ? '⏳ 업로드 중...' : '📁 CSV 파일 선택'}
          </label>
          <input
            id="csv-upload"
            type="file"
            accept=".csv"
            onChange={handleFileUpload}
            disabled={uploading}
            style={{ display: 'none' }}
          />

          {result && (
            <button
              type="button"
              onClick={handleReset}
              style={{
                padding: '0.75rem 1.5rem',
                background: '#e0e0e0',
                color: '#333',
                border: 'none',
                borderRadius: '8px',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '0.875rem'
              }}
            >
              초기화
            </button>
          )}

          <div style={{ fontSize: '0.875rem', color: '#555' }}>
            {uploading ? '파일을 업로드하는 중입니다...' : '*.csv 파일만 업로드 가능'}
          </div>
        </div>
      </div>

      {/* 결과 표시 */}
      {result && (
        <div style={{
          background: 'white',
          padding: '2rem',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: '700', marginBottom: '1rem', color: '#000' }}>
            업데이트 결과
          </h2>

          {/* 통계 */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1rem',
            marginBottom: '1.5rem'
          }}>
            <div style={{
              background: '#e6f7ff',
              border: '1px solid #91d5ff',
              borderRadius: '8px',
              padding: '1rem'
            }}>
              <div style={{ fontSize: '0.875rem', color: '#096dd9', marginBottom: '0.5rem' }}>총 처리</div>
              <div style={{ fontSize: '2rem', fontWeight: '700', color: '#096dd9' }}>
                {result.successCount + result.failCount}개
              </div>
            </div>

            <div style={{
              background: '#f6ffed',
              border: '1px solid #b7eb8f',
              borderRadius: '8px',
              padding: '1rem'
            }}>
              <div style={{ fontSize: '0.875rem', color: '#52c41a', marginBottom: '0.5rem' }}>성공</div>
              <div style={{ fontSize: '2rem', fontWeight: '700', color: '#52c41a' }}>
                {result.successCount}개
              </div>
            </div>

            <div style={{
              background: result.failCount > 0 ? '#fff1f0' : '#f6ffed',
              border: result.failCount > 0 ? '1px solid #ffa39e' : '1px solid #b7eb8f',
              borderRadius: '8px',
              padding: '1rem'
            }}>
              <div style={{
                fontSize: '0.875rem',
                color: result.failCount > 0 ? '#cf1322' : '#52c41a',
                marginBottom: '0.5rem'
              }}>실패</div>
              <div style={{
                fontSize: '2rem',
                fontWeight: '700',
                color: result.failCount > 0 ? '#cf1322' : '#52c41a'
              }}>
                {result.failCount}개
              </div>
            </div>
          </div>

          {/* 실패 목록 */}
          {result.failCount > 0 && result.failedBarcodes && result.failedBarcodes.length > 0 && (
            <div>
              <h3 style={{
                fontSize: '1rem',
                fontWeight: '600',
                color: '#cf1322',
                marginBottom: '0.5rem'
              }}>
                ❌ 실패한 바코드 목록
              </h3>
              <div style={{
                background: '#fff1f0',
                border: '1px solid #ffa39e',
                borderRadius: '8px',
                padding: '1rem',
                maxHeight: '300px',
                overflowY: 'auto'
              }}>
                {result.failedBarcodes.map((barcode, index) => (
                  <div
                    key={index}
                    style={{
                      padding: '0.5rem',
                      borderBottom: index < result.failedBarcodes.length - 1 ? '1px solid #ffa39e' : 'none',
                      fontFamily: 'monospace',
                      fontSize: '0.875rem',
                      color: '#cf1322'
                    }}
                  >
                    {barcode}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 성공 메시지 */}
          {result.failCount === 0 && (
            <div style={{
              background: '#f6ffed',
              border: '1px solid #b7eb8f',
              borderRadius: '8px',
              padding: '1rem',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '3rem', marginBottom: '0.5rem' }}>✅</div>
              <div style={{ fontSize: '1.1rem', fontWeight: '600', color: '#52c41a' }}>
                모든 상품 위치가 성공적으로 업데이트되었습니다!
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
