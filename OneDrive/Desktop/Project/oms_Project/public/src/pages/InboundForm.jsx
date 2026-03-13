import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/inventory') || 
                 'https://oms-backend-production-8a38.up.railway.app/api/inventory';

export default function InboundForm() {
  const [products, setProducts] = useState([]);
  
  const [formData, setFormData] = useState({
    barcode: '',
    inboundDate: new Date().toISOString().split('T')[0],
    workType: '교환',
    warehouse: '1.본사(안양)',
    workMemo: '',
    processStatus: '미지정',
    optionSearch: '',
    location: '',
    quantity: ''
  });
  
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [barcodeError, setBarcodeError] = useState('');
  const [loading, setLoading] = useState(false);

  const workTypes = ['교환', '반품', '원물공업입', '구매입고', 'M to M', '재고조정', '정산', '이동'];
  const warehouses = ['1.본사(안양)', '2.고백창고(이천)', '3.부천검수창고'];
  const processStatuses = ['미지정', '처리완료', '처리중'];

  useEffect(() => {
    loadProducts();
  }, []);

  const loadProducts = async () => {
    try {
      const response = await fetch(`${API_BASE}/products`);
      const data = await response.json();
      setProducts(data);
    } catch (error) {
      console.error('상품 로드 실패:', error);
    }
  };

  const handleBarcodeInput = (e) => {
    const value = e.target.value;
    setFormData({ ...formData, barcode: value });
    
    // 바코드 스캐너 자동 검색 (10자 이상)
    if (value.length >= 10) {
      setTimeout(() => {
        handleBarcodeSearch();
      }, 100);
    }
  };

  const handleBarcodeSearch = async () => {
    setBarcodeError('');
    setSelectedProduct(null);

    if (!formData.barcode.trim()) return;

    const searchTerm = formData.barcode.toLowerCase().trim();
    
    const product = products.find(p => 
      p.barcode?.toLowerCase().includes(searchTerm) || 
      p.sku?.toLowerCase().includes(searchTerm) ||
      p.productName?.toLowerCase().includes(searchTerm)
    );
    
    if (product) {
      setSelectedProduct(product);
    } else {
      setBarcodeError('⚠️ 해당 바코드의 상품을 찾을 수 없습니다.');
    }
  };

  const getWarehouseStock = () => {
    if (!selectedProduct) return 0;
    
    switch (formData.warehouse) {
      case '1.본사(안양)':
        return selectedProduct.warehouseStockAnyang || 0;
      case '2.고백창고(이천)':
        return selectedProduct.warehouseStockIcheon || 0;
      case '3.부천검수창고':
        return selectedProduct.warehouseStockBucheon || 0;
      default:
        return 0;
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!selectedProduct) {
      alert('상품을 검색해주세요.');
      return;
    }

    if (!formData.quantity || formData.quantity <= 0) {
      alert('입고 수량을 입력해주세요.');
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(`${API_BASE}/inbound-warehouse`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          productId: selectedProduct.productId,
          quantity: parseInt(formData.quantity),
          warehouse: formData.warehouse,
          location: selectedProduct.warehouseLocation || '',
          notes: `작업명:${formData.workType} | 메모:${formData.workMemo} | 처리:${formData.processStatus}`
        })
      });

      if (response.ok) {
        const updatedProduct = await response.json();
        alert(`✅ 입고 완료!\n\n상품: ${selectedProduct.productName}\n창고: ${formData.warehouse}\n수량: ${formData.quantity}개\n현재 재고: ${updatedProduct.totalStock}개`);
        
        setFormData({
          barcode: '', inboundDate: new Date().toISOString().split('T')[0],
          workType: '교환', warehouse: '1.본사(안양)', workMemo: '',
          processStatus: '미지정', optionSearch: '', location: '', quantity: ''
        });
        setSelectedProduct(null);
        setBarcodeError('');
        
        await loadProducts();
      } else {
        alert('❌ 입고 실패');
      }
    } catch (error) {
      alert('❌ 오류 발생');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '2rem', background: '#f5f5f5', minHeight: '100vh' }}>
      <div style={{ background: 'white', padding: '1.5rem 2rem', marginBottom: '1.5rem', borderRadius: '8px' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: '700', color: '#1a1a1a' }}>상품 입고</h1>
      </div>

      <form onSubmit={handleSubmit}>
        <div style={{ background: 'white', padding: '2rem', marginBottom: '1.5rem', borderRadius: '8px' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', border: '1px solid #ddd' }}>
            <tbody>
              <tr>
                <td style={{ padding: '1rem', background: '#f5f5f5', border: '1px solid #ddd', width: '150px', fontWeight: '600', color: '#333' }}>상품명</td>
                <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <input type="text" value={formData.barcode} onChange={handleBarcodeInput}
                      onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), handleBarcodeSearch())}
                      placeholder="바코드 스캔 또는 입력" autoFocus style={{ flex: 1, padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333' }} />
                    <button type="button" onClick={handleBarcodeSearch} style={{ padding: '0.5rem 1.5rem', background: '#1976d2', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem', fontWeight: '600' }}>상품검색</button>
                    <button type="button" onClick={() => { setFormData({ ...formData, barcode: '', location: '', quantity: '' }); setSelectedProduct(null); setBarcodeError(''); }}
                      style={{ padding: '0.5rem 1.5rem', background: '#e0e0e0', color: '#333', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.875rem' }}>초기화</button>
                  </div>
                  {barcodeError && <div style={{ color: '#d32f2f', fontSize: '0.875rem', marginTop: '0.5rem' }}>{barcodeError}</div>}
                  {selectedProduct && <div style={{ color: '#2e7d32', fontSize: '0.875rem', marginTop: '0.5rem', fontWeight: '600' }}>✓ {selectedProduct.productName}</div>}
                </td>
              </tr>

              <tr>
                <td style={{ padding: '1rem', background: '#f5f5f5', border: '1px solid #ddd', fontWeight: '600', color: '#333' }}>입고일자</td>
                <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <input type="date" value={formData.inboundDate} onChange={(e) => setFormData({ ...formData, inboundDate: e.target.value })}
                      style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333' }} />
                    <select value={formData.workType} onChange={(e) => setFormData({ ...formData, workType: e.target.value })}
                      style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333', minWidth: '120px' }}>
                      {workTypes.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                    <select value={formData.warehouse} onChange={(e) => setFormData({ ...formData, warehouse: e.target.value })}
                      style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333', minWidth: '150px' }}>
                      {warehouses.map(w => <option key={w} value={w}>{w}</option>)}
                    </select>
                  </div>
                </td>
              </tr>

              <tr>
                <td style={{ padding: '1rem', background: '#f5f5f5', border: '1px solid #ddd', fontWeight: '600', color: '#333' }}>작업메모</td>
                <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                    <input type="text" value={formData.workMemo} onChange={(e) => setFormData({ ...formData, workMemo: e.target.value })}
                      style={{ flex: 1, padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333' }} />
                    <span style={{ fontSize: '0.875rem', color: '#666', whiteSpace: 'nowrap' }}>작업처리여부</span>
                    <select value={formData.processStatus} onChange={(e) => setFormData({ ...formData, processStatus: e.target.value })}
                      style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333', minWidth: '120px' }}>
                      {processStatuses.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                </td>
              </tr>

              <tr>
                <td style={{ padding: '1rem', background: '#f5f5f5', border: '1px solid #ddd', fontWeight: '600', color: '#333' }}>옵션검색</td>
                <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                  <input type="text" value={formData.optionSearch} onChange={(e) => setFormData({ ...formData, optionSearch: e.target.value })}
                    style={{ width: '100%', padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.875rem', color: '#333' }} />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div style={{ background: 'white', padding: '2rem', borderRadius: '8px' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', border: '1px solid #ddd' }}>
            <thead style={{ background: '#f5f5f5' }}>
              <tr>
                {['바코드번호', '위치', '옵션이름', '원가', '선택창고 현재재고', '전체재고', '입고수량'].map(h => (
                  <th key={h} style={{ padding: '0.75rem', border: '1px solid #ddd', fontSize: '0.875rem', color: '#333', fontWeight: '600' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {selectedProduct ? (
                <tr>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center', fontSize: '0.875rem', color: '#333' }}>{selectedProduct.barcode || '-'}</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center', fontSize: '0.875rem', color: '#333' }}>{selectedProduct.warehouseLocation || '-'}</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', fontSize: '0.875rem', color: '#333' }}>{selectedProduct.productName}</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'right', fontSize: '0.875rem', color: '#333' }}>{selectedProduct.costPrice?.toLocaleString() || 0}원</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center', fontSize: '0.875rem', color: '#1976d2', fontWeight: '700' }}>{getWarehouseStock()}개</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center', fontSize: '0.875rem', color: '#333' }}>{selectedProduct.totalStock}개</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center' }}>
                    <input type="number" value={formData.quantity} onChange={(e) => setFormData({ ...formData, quantity: e.target.value })} min="1" required
                      style={{ width: '80px', padding: '0.25rem', border: '1px solid #ccc', borderRadius: '4px', textAlign: 'center', fontSize: '0.875rem', color: '#333' }} />
                  </td>
                </tr>
              ) : (
                <tr><td colSpan="7" style={{ padding: '3rem', textAlign: 'center', color: '#999', border: '1px solid #ddd' }}>바코드를 입력하세요</td></tr>
              )}
            </tbody>
          </table>

          <div style={{ marginTop: '2rem', textAlign: 'center' }}>
            <button type="submit" disabled={loading || !selectedProduct}
              style={{ padding: '0.75rem 3rem', background: (loading || !selectedProduct) ? '#ccc' : '#1976d2', color: 'white', border: 'none', borderRadius: '4px', fontSize: '1rem', fontWeight: '600', cursor: (loading || !selectedProduct) ? 'not-allowed' : 'pointer' }}>
              {loading ? '처리 중...' : '✓ 입고하기'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
