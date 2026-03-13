import { useState } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') ||
  'https://oms-backend-production-8a38.up.railway.app/api/inventory';

const INIT_API = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/init') ||
  'https://oms-backend-production-8a38.up.railway.app/api/init';

export default function ProductManagement() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      alert('검색어를 입력하세요.');
      return;
    }

    setLoading(true);
    setSearched(true);

    try {
      const response = await fetch(`${API_BASE}/products/search?keyword=${encodeURIComponent(searchKeyword)}`);
      const data = await response.json();
      setProducts(data);
    } catch (error) {
      console.error('검색 실패:', error);
      alert('검색 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setSearchKeyword('');
    setProducts([]);
    setSearched(false);
  };

  const loadProducts = async () => {
    setLoading(true);
    setSearched(true);
    try {
      const response = await fetch(`${API_BASE}/products`);
      const data = await response.json();
      setProducts(data);
    } catch (error) {
      console.error('상품 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAll = async () => {
    if (!window.confirm('⚠️ 정말로 전체 상품을 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다!')) {
      return;
    }

    if (!window.confirm('⚠️⚠️ 최종 확인\n\n전체 상품이 삭제됩니다. 계속하시겠습니까?')) {
      return;
    }

    setLoading(true);

    try {
      console.log('🗑️ 전체 삭제 요청:', `${INIT_API}/products/all`);

      const response = await fetch(`${INIT_API}/products/all`, {
        method: 'DELETE'
      });

      console.log('📡 응답 상태:', response.status, response.statusText);

      if (response.ok) {
        const message = await response.text();
        console.log('✅ 성공:', message);
        alert('✅ ' + message);
        handleReset();
      } else {
        const error = await response.text();
        console.error('❌ 실패:', error);
        alert('삭제 실패: ' + error);
      }
    } catch (error) {
      console.error('❌ 삭제 오류:', error);
      alert('삭제 중 오류가 발생했습니다: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    if (!file.name.endsWith('.csv')) {
      alert('CSV 파일만 업로드 가능합니다.');
      return;
    }

    console.log('📁 파일 선택:', file.name, file.size, 'bytes');

    setUploading(true);

    const formData = new FormData();
    formData.append('file', file);

    try {
      console.log('🚀 업로드 시작:', `${INIT_API}/products/upload-csv`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 300000);

      const response = await fetch(`${INIT_API}/products/upload-csv`, {
        method: 'POST',
        body: formData,
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      console.log('📡 응답 상태:', response.status, response.statusText);

      if (response.ok) {
        const message = await response.text();
        console.log('✅ 성공:', message);
        alert(message + '\n\n검색을 통해 상품을 확인하세요.');
        handleReset();
      } else {
        const error = await response.text();
        console.error('❌ 실패:', error);
        alert('업로드 실패: ' + error);
      }
    } catch (error) {
      console.error('❌ 업로드 오류:', error);

      if (error.name === 'AbortError') {
        alert('⏰ 업로드 시간 초과\n\n파일이 너무 큽니다. 잠시 후 다시 시도하거나\n데이터를 분할해서 업로드해주세요.');
      } else {
        alert('업로드 중 오류가 발생했습니다:\n' + error.message);
      }
    } finally {
      setUploading(false);
      event.target.value = '';
    }
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
          📦 상품 등록 관리
        </h1>
        <p style={{ color: '#333', fontSize: '0.875rem' }}>
          CSV 파일을 업로드하여 상품을 일괄 등록하거나 개별 상품을 추가하세요
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
              • 11번가 재고 현황 CSV 파일을 그대로 업로드하세요<br />
              • 파일 인코딩: EUC-KR (자동 변환됨)<br />
              • 필수 컬럼: 바코드번호, 상품명, 옵션명<br />
              • 신규 상품만 추가되며, 기존 상품은 유지됩니다
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

          <button
            type="button"
            onClick={handleDeleteAll}
            disabled={uploading || loading}
            style={{
              padding: '0.75rem 1.5rem',
              background: (uploading || loading) ? '#cbd5e0' : '#dc2626',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: (uploading || loading) ? 'not-allowed' : 'pointer',
              fontWeight: '600',
              fontSize: '0.875rem'
            }}
          >
            🗑️ 전체 삭제
          </button>

          <div style={{ fontSize: '0.875rem', color: '#555' }}>
            {uploading ? '파일을 업로드하는 중입니다...' : '*.csv 파일만 업로드 가능'}
          </div>
        </div>
      </div>

      {/* 검색 */}
      <div style={{
        background: 'white',
        padding: '1.5rem 2rem',
        marginBottom: '1.5rem',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="상품명, SKU, 바코드 검색..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            style={{
              flex: 1,
              padding: '0.75rem 1rem',
              border: '2px solid #e2e8f0',
              borderRadius: '8px',
              fontSize: '1rem'
            }}
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            style={{
              padding: '0.75rem 1.5rem',
              borderRadius: '8px',
              border: 'none',
              background: loading ? '#cbd5e0' : '#1976d2',
              color: 'white',
              cursor: loading ? 'not-allowed' : 'pointer',
              fontWeight: '600',
              whiteSpace: 'nowrap'
            }}
          >
            🔍 검색
          </button>
          <button
            onClick={handleReset}
            style={{
              padding: '0.75rem 1.5rem',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              background: 'white',
              color: '#333',
              cursor: 'pointer',
              fontWeight: '600',
              whiteSpace: 'nowrap'
            }}
          >
            초기화
          </button>
          <button
            onClick={loadProducts}
            disabled={loading}
            style={{
              padding: '0.75rem 1.5rem',
              borderRadius: '8px',
              border: 'none',
              background: loading ? '#cbd5e0' : '#38a169',
              color: 'white',
              cursor: loading ? 'not-allowed' : 'pointer',
              fontWeight: '600',
              whiteSpace: 'nowrap'
            }}
          >
            📋 전체보기
          </button>
        </div>
        {searched && (
          <div style={{ marginTop: '1rem', color: '#666', fontSize: '0.875rem' }}>
            검색 결과: <strong style={{ color: '#000' }}>{products.length.toLocaleString()}개</strong>
          </div>
        )}
      </div>

      {/* 상품 목록 테이블 */}
      {loading ? (
        <div style={{
          background: 'white',
          padding: '3rem',
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>⏳</div>
          <p style={{ color: '#333' }}>검색 중...</p>
        </div>
      ) : !searched ? (
        <div style={{
          background: 'white',
          padding: '3rem',
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🔍</div>
          <p style={{ color: '#333', fontSize: '1.1rem', marginBottom: '0.5rem' }}>검색어를 입력하세요</p>
          <p style={{ color: '#999', fontSize: '0.9rem' }}>상품명, SKU, 바코드로 검색하거나 "전체보기"를 클릭하세요</p>
        </div>
      ) : products.length === 0 ? (
        <div style={{
          background: 'white',
          padding: '3rem',
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>📦</div>
          <p style={{ color: '#333' }}>검색 결과가 없습니다</p>
        </div>
      ) : (
        <div style={{
          background: 'white',
          borderRadius: '8px',
          overflow: 'hidden',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{
            maxHeight: '600px',
            overflowY: 'auto'
          }}>
            <table style={{
              width: '100%',
              borderCollapse: 'collapse'
            }}>
              <thead style={{
                position: 'sticky',
                top: 0,
                background: '#1a1a1a',
                color: 'white',
                zIndex: 10
              }}>
                <tr>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>바코드번호</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>상품명</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>카테고리</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>현재재고</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>가용재고</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>예약재고</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>위치</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product, index) => (
                  <tr
                    key={product.productId}
                    style={{
                      borderBottom: '1px solid #e2e8f0',
                      background: index % 2 === 0 ? 'white' : '#f7fafc'
                    }}
                  >
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', fontFamily: 'monospace', color: '#000' }}>
                      {product.barcode}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', color: '#000' }}>
                      {product.productName}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', color: '#000' }}>
                      {product.category || '-'}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: '#000' }}>
                      {product.totalStock}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', color: '#2e7d32', fontWeight: '600' }}>
                      {product.availableStock}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', color: '#d32f2f', fontWeight: '600' }}>
                      {product.reservedStock}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', color: '#000' }}>
                      {product.warehouseLocation || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
