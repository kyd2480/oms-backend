import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_URL?.replace('/api/processing', '/api/admin/channels') || 
                 'https://oms-backend-production-8a38.up.railway.app/api/admin/channels';

export default function ChannelManagement() {
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingChannel, setEditingChannel] = useState(null);
  const [formData, setFormData] = useState({
    channelCode: '',
    channelName: '',
    apiType: 'REST',
    apiBaseUrl: '',
    collectionInterval: 10
  });

  useEffect(() => {
    loadChannels();
  }, []);

  const loadChannels = async () => {
    try {
      const response = await fetch(API_BASE);
      const data = await response.json();
      setChannels(data);
    } catch (error) {
      console.error('판매처 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const openAddModal = () => {
    setEditingChannel(null);
    setFormData({
      channelCode: '',
      channelName: '',
      apiType: 'REST',
      apiBaseUrl: '',
      collectionInterval: 10
    });
    setShowModal(true);
  };

  const openEditModal = (channel) => {
    setEditingChannel(channel);
    setFormData({
      channelCode: channel.channelCode,
      channelName: channel.channelName,
      apiType: channel.apiType || 'REST',
      apiBaseUrl: channel.apiBaseUrl || '',
      collectionInterval: channel.collectionInterval || 10
    });
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    try {
      const url = editingChannel ? `${API_BASE}/${editingChannel.channelId}` : API_BASE;
      const method = editingChannel ? 'PUT' : 'POST';
      
      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        setShowModal(false);
        await loadChannels();
        alert(editingChannel ? '판매처가 수정되었습니다!' : '판매처가 추가되었습니다!');
      } else {
        alert('저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('저장 실패:', error);
      alert('저장 중 오류가 발생했습니다.');
    }
  };

  const toggleChannel = async (id) => {
    try {
      const response = await fetch(`${API_BASE}/${id}/toggle`, { method: 'PATCH' });
      if (response.ok) {
        await loadChannels();
      }
    } catch (error) {
      console.error('상태 변경 실패:', error);
    }
  };

  const deleteChannel = async (id, name) => {
    if (!confirm(`정말 "${name}" 판매처를 삭제하시겠습니까?`)) return;
    
    try {
      const response = await fetch(`${API_BASE}/${id}`, { method: 'DELETE' });
      if (response.ok) {
        await loadChannels();
        alert('판매처가 삭제되었습니다.');
      }
    } catch (error) {
      console.error('삭제 실패:', error);
    }
  };

  if (loading) {
    return <div style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🏪 판매처 관리</h1>
        <p style={{ color: '#718096' }}>판매처를 추가하고 관리하세요</p>
      </div>

      <div style={{ marginBottom: '2rem' }}>
        <button 
          onClick={openAddModal}
          style={{
            padding: '0.75rem 1.5rem',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            fontWeight: '600'
          }}
        >
          ➕ 판매처 추가
        </button>
      </div>

      {channels.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem' }}>
          <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>📦</div>
          <p style={{ color: '#718096', marginBottom: '1.5rem' }}>등록된 판매처가 없습니다</p>
          <button onClick={openAddModal}>첫 판매처 추가하기</button>
        </div>
      ) : (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fill, minmax(350px, 1fr))',
          gap: '1.5rem'
        }}>
          {channels.map(channel => (
            <div key={channel.channelId} style={{
              background: 'white',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <div>
                  <div style={{ fontSize: '1.25rem', fontWeight: '700' }}>{channel.channelName}</div>
                  <div style={{ 
                    fontSize: '0.875rem', 
                    color: '#718096',
                    background: '#edf2f7',
                    padding: '0.25rem 0.75rem',
                    borderRadius: '4px',
                    display: 'inline-block',
                    marginTop: '0.25rem'
                  }}>
                    {channel.channelCode}
                  </div>
                </div>
                <span style={{
                  padding: '0.25rem 0.75rem',
                  borderRadius: '12px',
                  fontSize: '0.75rem',
                  fontWeight: '600',
                  background: channel.isActive ? '#c6f6d5' : '#fed7d7',
                  color: channel.isActive ? '#22543d' : '#742a2a'
                }}>
                  {channel.isActive ? '● 활성' : '○ 비활성'}
                </span>
              </div>

              <div style={{ background: '#f7fafc', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid #e2e8f0' }}>
                  <span style={{ color: '#718096', fontSize: '0.875rem' }}>API 타입</span>
                  <span style={{ fontWeight: '600', fontSize: '0.875rem' }}>{channel.apiType || '-'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid #e2e8f0' }}>
                  <span style={{ color: '#718096', fontSize: '0.875rem' }}>수집 주기</span>
                  <span style={{ fontWeight: '600', fontSize: '0.875rem' }}>{channel.collectionInterval}분</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0' }}>
                  <span style={{ color: '#718096', fontSize: '0.875rem' }}>마지막 수집</span>
                  <span style={{ fontWeight: '600', fontSize: '0.875rem' }}>
                    {channel.lastCollectedAt ? new Date(channel.lastCollectedAt).toLocaleString() : '없음'}
                  </span>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button 
                  onClick={() => toggleChannel(channel.channelId)}
                  style={{
                    flex: 1,
                    padding: '0.5rem',
                    background: '#edf2f7',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '0.875rem'
                  }}
                >
                  {channel.isActive ? '일시정지' : '활성화'}
                </button>
                <button 
                  onClick={() => openEditModal(channel)}
                  style={{
                    flex: 1,
                    padding: '0.5rem',
                    background: '#bee3f8',
                    color: '#2c5282',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '0.875rem'
                  }}
                >
                  수정
                </button>
                <button 
                  onClick={() => deleteChannel(channel.channelId, channel.channelName)}
                  style={{
                    flex: 1,
                    padding: '0.5rem',
                    background: '#fed7d7',
                    color: '#742a2a',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '0.875rem'
                  }}
                >
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          background: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }} onClick={() => setShowModal(false)}>
          <div style={{
            background: 'white',
            padding: '2rem',
            borderRadius: '12px',
            maxWidth: '500px',
            width: '90%'
          }} onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.5rem', marginBottom: '1.5rem' }}>
              {editingChannel ? '판매처 수정' : '판매처 추가'}
            </h2>
            
            <form onSubmit={handleSubmit}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '600' }}>
                  판매처 코드 *
                </label>
                <input
                  type="text"
                  value={formData.channelCode}
                  onChange={e => setFormData({ ...formData, channelCode: e.target.value })}
                  disabled={!!editingChannel}
                  required
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    border: '2px solid #e2e8f0',
                    borderRadius: '8px',
                    fontSize: '1rem'
                  }}
                />
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '600' }}>
                  판매처 이름 *
                </label>
                <input
                  type="text"
                  value={formData.channelName}
                  onChange={e => setFormData({ ...formData, channelName: e.target.value })}
                  required
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    border: '2px solid #e2e8f0',
                    borderRadius: '8px',
                    fontSize: '1rem'
                  }}
                />
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '600' }}>
                  API 타입
                </label>
                <select
                  value={formData.apiType}
                  onChange={e => setFormData({ ...formData, apiType: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    border: '2px solid #e2e8f0',
                    borderRadius: '8px',
                    fontSize: '1rem'
                  }}
                >
                  <option value="REST">REST API</option>
                  <option value="SOAP">SOAP</option>
                  <option value="CSV">CSV</option>
                </select>
              </div>

              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '600' }}>
                  수집 주기 (분)
                </label>
                <input
                  type="number"
                  value={formData.collectionInterval}
                  onChange={e => setFormData({ ...formData, collectionInterval: parseInt(e.target.value) })}
                  min="1"
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    border: '2px solid #e2e8f0',
                    borderRadius: '8px',
                    fontSize: '1rem'
                  }}
                />
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem' }}>
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  style={{
                    flex: 1,
                    padding: '0.75rem',
                    background: '#edf2f7',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '1rem'
                  }}
                >
                  취소
                </button>
                <button
                  type="submit"
                  style={{
                    flex: 1,
                    padding: '0.75rem',
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    color: 'white',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '1rem',
                    fontWeight: '600'
                  }}
                >
                  저장
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
