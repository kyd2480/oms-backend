import { useState } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') || 
                 'https://oms-backend-production-8a38.up.railway.app/api/inventory';

export default function TransactionHistory() {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searched, setSearched] = useState(false);
  const [filterType, setFilterType] = useState('all'); // all, IN, OUT

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      alert('검색어를 입력하세요.');
      return;
    }
    
    setLoading(true);
    setSearched(true);
    
    try {
      const response = await fetch(`${API_BASE}/transactions/search?keyword=${encodeURIComponent(searchKeyword)}&limit=200`);
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      
      const data = await response.json();
      
      if (!Array.isArray(data)) {
        console.error('Invalid data format:', data);
        setTransactions([]);
        alert('데이터 형식이 올바르지 않습니다.');
        return;
      }
      
      setTransactions(data);
    } catch (error) {
      console.error('검색 실패:', error);
      alert('검색 중 오류가 발생했습니다: ' + error.message);
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  };

  const loadRecentTransactions = async () => {
    setLoading(true);
    setSearched(true);
    
    try {
      const response = await fetch(`${API_BASE}/transactions?limit=200`);
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      
      const data = await response.json();
      
      if (!Array.isArray(data)) {
        console.error('Invalid data format:', data);
        setTransactions([]);
        alert('데이터 형식이 올바르지 않습니다.');
        return;
      }
      
      setTransactions(data);
    } catch (error) {
      console.error('조회 실패:', error);
      alert('조회 중 오류가 발생했습니다: ' + error.message);
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  };

  const filterTransactions = (data) => {
    if (!Array.isArray(data)) return [];
    if (filterType === 'all') return data;
    return data.filter(t => t.transactionType === filterType);
  };

  const handleFilterChange = (type) => {
    setFilterType(type);
    if (searched && transactions.length > 0) {
      // 필터만 변경 (재조회 안 함)
    }
  };

  const handleReset = () => {
    setSearchKeyword('');
    setTransactions([]);
    setSearched(false);
    setFilterType('all');
  };

  const getTypeLabel = (type) => {
    switch(type) {
      case 'IN': return '입고';
      case 'OUT': return '출고';
      case 'ADJUST': return '조정';
      case 'MOVE': return '이동';
      default: return type;
    }
  };

  const getTypeColor = (type) => {
    switch(type) {
      case 'IN': return '#1976d2';
      case 'OUT': return '#d32f2f';
      case 'ADJUST': return '#f57c00';
      case 'MOVE': return '#7b1fa2';
      default: return '#666';
    }
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const filteredTransactions = filterTransactions(transactions);

  return (
    <div style={{ padding: '2rem', background: '#f5f5f5', minHeight: '100vh' }}>
      {/* 헤더 */}
      <div style={{
        background: 'white',
        padding: '1.5rem 2rem',
        marginBottom: '1.5rem',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: '700', color: '#000' }}>📋 입출고 거래 내역</h1>
      </div>

      {/* 필터 & 검색 */}
      <div style={{
        background: 'white',
        padding: '1.5rem 2rem',
        marginBottom: '1.5rem',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center', marginBottom: '1rem' }}>
          {/* 필터 버튼 */}
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              onClick={() => handleFilterChange('all')}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '8px',
                border: filterType === 'all' ? '2px solid #667eea' : '1px solid #e2e8f0',
                background: filterType === 'all' ? '#667eea' : 'white',
                color: filterType === 'all' ? 'white' : '#333',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '0.875rem'
              }}
            >
              전체
            </button>
            <button
              onClick={() => handleFilterChange('IN')}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '8px',
                border: filterType === 'IN' ? '2px solid #1976d2' : '1px solid #e2e8f0',
                background: filterType === 'IN' ? '#1976d2' : 'white',
                color: filterType === 'IN' ? 'white' : '#333',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '0.875rem'
              }}
            >
              📦 입고
            </button>
            <button
              onClick={() => handleFilterChange('OUT')}
              style={{
                padding: '0.5rem 1rem',
                borderRadius: '8px',
                border: filterType === 'OUT' ? '2px solid #d32f2f' : '1px solid #e2e8f0',
                background: filterType === 'OUT' ? '#d32f2f' : 'white',
                color: filterType === 'OUT' ? 'white' : '#333',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '0.875rem'
              }}
            >
              📤 출고
            </button>
          </div>

          {/* 검색 */}
          <div style={{ flex: 1, display: 'flex', gap: '0.5rem', minWidth: '300px' }}>
            <input
              type="text"
              placeholder="상품명, SKU, 바코드 검색..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              style={{
                flex: 1,
                padding: '0.5rem 1rem',
                border: '2px solid #e2e8f0',
                borderRadius: '8px',
                fontSize: '1rem'
              }}
            />
            <button
              onClick={handleSearch}
              disabled={loading}
              style={{
                padding: '0.5rem 1.5rem',
                borderRadius: '8px',
                border: 'none',
                background: loading ? '#cbd5e0' : '#667eea',
                color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
                fontWeight: '600',
                whiteSpace: 'nowrap'
              }}
            >
              🔍 검색
            </button>
            <button
              onClick={loadRecentTransactions}
              disabled={loading}
              style={{
                padding: '0.5rem 1.5rem',
                borderRadius: '8px',
                border: 'none',
                background: loading ? '#cbd5e0' : '#38a169',
                color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
                fontWeight: '600',
                whiteSpace: 'nowrap'
              }}
            >
              📋 최근내역
            </button>
            <button
              onClick={handleReset}
              style={{
                padding: '0.5rem 1.5rem',
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
          </div>
        </div>
        
        {searched && (
          <div style={{ color: '#666', fontSize: '0.875rem' }}>
            조회 결과: <strong style={{ color: '#000' }}>{filteredTransactions.length.toLocaleString()}건</strong>
            {filterType !== 'all' && ` (${getTypeLabel(filterType)} 필터 적용)`}
          </div>
        )}
      </div>

      {/* 거래 내역 목록 */}
      {loading ? (
        <div style={{
          background: 'white',
          padding: '3rem',
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>⏳</div>
          <p style={{ color: '#333' }}>조회 중...</p>
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
          <p style={{ color: '#333', fontSize: '1.1rem', marginBottom: '0.5rem' }}>검색하거나 최근 내역을 조회하세요</p>
          <p style={{ color: '#999', fontSize: '0.9rem' }}>상품명, SKU, 바코드로 검색 가능합니다</p>
        </div>
      ) : filteredTransactions.length === 0 ? (
        <div style={{
          background: 'white',
          padding: '3rem',
          borderRadius: '12px',
          textAlign: 'center',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>📦</div>
          <p style={{ color: '#333' }}>거래 내역이 없습니다</p>
        </div>
      ) : (
        <div style={{
          background: 'white',
          borderRadius: '8px',
          overflow: 'hidden',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
        }}>
          <div style={{ maxHeight: '600px', overflowY: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead style={{
                position: 'sticky',
                top: 0,
                background: '#1a1a1a',
                color: 'white',
                zIndex: 10
              }}>
                <tr>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>유형</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>상품명</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>SKU</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>수량</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>변경전</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>변경후</th>
                  <th style={{ padding: '1rem', textAlign: 'left', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>비고</th>
                  <th style={{ padding: '1rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: 'white' }}>일시</th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map((transaction, index) => (
                  <tr
                    key={transaction.transactionId}
                    style={{
                      borderBottom: '1px solid #e2e8f0',
                      background: index % 2 === 0 ? 'white' : '#f7fafc'
                    }}
                  >
                    <td style={{ padding: '0.75rem', textAlign: 'center' }}>
                      <span style={{
                        padding: '0.25rem 0.75rem',
                        borderRadius: '12px',
                        fontSize: '0.75rem',
                        fontWeight: '600',
                        background: `${getTypeColor(transaction.transactionType)}20`,
                        color: getTypeColor(transaction.transactionType)
                      }}>
                        {getTypeLabel(transaction.transactionType)}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', color: '#000' }}>
                      {transaction.productName}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.875rem', fontFamily: 'monospace', color: '#666' }}>
                      {transaction.sku}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: '#000' }}>
                      {transaction.quantity}개
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', color: '#666' }}>
                      {transaction.beforeStock}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.875rem', fontWeight: '600', color: '#000' }}>
                      {transaction.afterStock}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.75rem', color: '#666' }}>
                      {transaction.notes || '-'}
                    </td>
                    <td style={{ padding: '0.75rem', textAlign: 'center', fontSize: '0.75rem', color: '#666', whiteSpace: 'nowrap' }}>
                      {formatDate(transaction.createdAt)}
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
