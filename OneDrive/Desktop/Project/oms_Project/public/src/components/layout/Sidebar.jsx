import { useMemo } from 'react';
import { MENU } from '../../lib/menuConfig';

export default function Sidebar({ user, currentView, setCurrentView, onLogout, openMenu, setOpenMenu }) {
  const isOrdersActive = useMemo(() => currentView.startsWith('orders.'), [currentView]);
  const isInventoryActive = useMemo(() => currentView.startsWith('inventory.'), [currentView]);

  const orders = MENU.find((m) => m.key === 'orders');
  const inventory = MENU.find((m) => m.key === 'inventory');

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="logo">OMS</div>
        <h2 className="sidebar-title">OMS</h2>
      </div>

      <nav className="nav-menu">
        <div className={`nav-item ${currentView === 'analytics' ? 'active' : ''}`} onClick={() => setCurrentView('analytics')}>
          <span>통계 대시보드</span>
        </div>
        <div className={`nav-item ${currentView === 'dashboard' ? 'active' : ''}`} onClick={() => setCurrentView('dashboard')}>
          <span>대시보드</span>
        </div>

        <div className={`nav-item ${isOrdersActive ? 'active' : ''}`} onClick={() => setOpenMenu((p) => ({ ...p, orders: !p.orders }))}>
          <div className="menu-section-head">
            <span>주문 관리</span>
            <span className={`chev ${openMenu.orders ? 'open' : ''}`}>▶</span>
          </div>
        </div>
        {openMenu.orders && (
          <div className="submenu">
            {orders?.children?.map((it) => (
              <div
                key={it.key}
                className={`submenu-item ${currentView === it.key ? 'active' : ''}`}
                onClick={() => setCurrentView(it.key)}
              >
                <span className="dot dot-blue" />
                <span>{it.label}</span>
              </div>
            ))}
          </div>
        )}

        <div className={`nav-item ${isInventoryActive ? 'active' : ''}`} onClick={() => setOpenMenu((p) => ({ ...p, inventory: !p.inventory }))}>
          <div className="menu-section-head">
            <span>재고 관리</span>
            <span className={`chev ${openMenu.inventory ? 'open' : ''}`}>▶</span>
          </div>
        </div>
        {openMenu.inventory && (
          <div className="submenu">
            {inventory?.children?.map((it) => (
              <div
                key={it.key}
                className={`submenu-item ${currentView === it.key ? 'active' : ''}`}
                onClick={() => setCurrentView(it.key)}
              >
                <span className="dot dot-green" />
                <span>{it.label}</span>
              </div>
            ))}
          </div>
        )}

        <div className={`nav-item ${currentView === 'customers' ? 'active' : ''}`} onClick={() => setCurrentView('customers')}>
          <span>고객 관리</span>
        </div>

        <div className={`nav-item ${currentView === 'delivery' ? 'active' : ''}`} onClick={() => setCurrentView('delivery')}>
          <span>배송 관리</span>
        </div>

        <div className={`nav-item ${currentView === 'channels' ? 'active' : ''}`} onClick={() => setCurrentView('channels')}>
          <span>🏪 판매처 관리</span>
        </div>
      </nav>

      <div className="user-info">
        <div className="user-avatar">{(user?.name || '?').slice(0, 1)}</div>
        <div className="user-details">
          <div className="user-name">{user?.name}</div>
          <div className="user-role">{user?.role}</div>
        </div>
        <button className="logout-button" onClick={onLogout} title="로그아웃">
          ↩
        </button>
      </div>
    </aside>
  );
}
