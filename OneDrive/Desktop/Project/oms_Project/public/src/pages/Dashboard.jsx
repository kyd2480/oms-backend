import { useMemo, useState } from 'react';
import Sidebar from '../components/layout/Sidebar';
import PageShell from '../components/ui/PageShell';
import Toolbar from '../components/ui/Toolbar';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import Chip from '../components/ui/Chip';
import TableWrap from '../components/ui/TableWrap';
import AnalyticsDashboard from './AnalyticsDashboard';
import ChannelManagement from './ChannelManagement';
import InventoryList from './InventoryList';
import InboundForm from './InboundForm';
import OutboundForm from './OutboundForm';
import ProductManagement from './ProductManagement';
import TransactionHistory from './TransactionHistory';
import ProductOptionUpdate from './ProductOptionUpdate';
import BarcodeManagement from './BarcodeManagement';
import WarehouseManagement from './WarehouseManagement';
import WarehouseTransfer from './WarehouseTransfer';
import OrderInput from './OrderInput';

export default function Dashboard({ user, onLogout }) {
  const [currentView, setCurrentView] = useState('dashboard');
  const [openMenu, setOpenMenu] = useState({ orders: false, inventory: false });

  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('all');

  const stats = useMemo(() => ({
    totalOrders: 1248,
    pendingOrders: 87,
    completedOrders: 1089,
    cancelledOrders: 72
  }), []);

  const recentOrders = useMemo(() => ([
    { id: 'ORD-2024-001', customer: '삼성전자', amount: 1250000, status: 'PENDING', date: '2024-02-03' },
    { id: 'ORD-2024-002', customer: 'LG전자', amount: 890000, status: 'CONFIRMED', date: '2024-02-03' },
    { id: 'ORD-2024-003', customer: '현대자동차', amount: 2340000, status: 'SHIPPED', date: '2024-02-02' },
  ]), []);

  const renderDashboardHome = () => (
    <>
      <div className="header">
        <div className="header-left">
          <h1>주문 관리 시스템</h1>
          <p>안녕하세요, {user.name}님!</p>
        </div>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-content">
            <h3>총 주문</h3>
            <div className="stat-value">{stats.totalOrders.toLocaleString()}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-content">
            <h3>대기</h3>
            <div className="stat-value">{stats.pendingOrders}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-content">
            <h3>완료</h3>
            <div className="stat-value">{stats.completedOrders.toLocaleString()}</div>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-content">
            <h3>취소</h3>
            <div className="stat-value">{stats.cancelledOrders}</div>
          </div>
        </div>
      </div>

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>주문번호</th><th>고객명</th><th>주문금액</th><th>상태</th><th>주문일</th>
            </tr>
          </thead>
          <tbody>
            {recentOrders.map(o => (
              <tr key={o.id}>
                <td><span className="order-id">{o.id}</span></td>
                <td>{o.customer}</td>
                <td>₩{o.amount.toLocaleString()}</td>
                <td>{o.status}</td>
                <td>{o.date}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );

  const renderTemplate = (title, desc, chipText) => (
    <PageShell
      title={title}
      desc={desc}
      actions={
        <>
          <Button variant="ghost" onClick={() => alert('추후 구현')}>설정</Button>
          <Button variant="primary" onClick={() => alert('추후 구현')}>실행</Button>
        </>
      }
    >
      <Toolbar
        left={
          <>
            <Input value={q} onChange={setQ} placeholder="검색" />
            <Select value={filter} onChange={setFilter} options={[
              { value: 'all', label: '전체' },
              { value: 'a', label: '옵션A' },
              { value: 'b', label: '옵션B' },
            ]} />
          </>
        }
        right={<Chip text={chipText} tone="blue" />}
      />
      <TableWrap>
        <table>
          <thead><tr><th>컬럼1</th><th>컬럼2</th><th>컬럼3</th><th>액션</th></tr></thead>
          <tbody>
            <tr>
              <td>샘플</td><td>샘플</td><td>샘플</td>
              <td><Button variant="ghost" onClick={() => alert('추후 구현')}>보기</Button></td>
            </tr>
          </tbody>
        </table>
      </TableWrap>
    </PageShell>
  );

  const renderMain = () => {
    if (currentView === 'analytics') return <AnalyticsDashboard user={user} />;
    if (currentView === 'dashboard') return renderDashboardHome();

    // 주문관리
    if (currentView === 'orders.orderinput') return <OrderInput />;
    if (currentView === 'orders.dupcheck') return renderTemplate('중복주문체크', '주문 중복을 식별합니다.', '템플릿');
    if (currentView === 'orders.automatch') return renderTemplate('자동매칭', '주문 ↔ 상품 자동 매칭', '템플릿');
    if (currentView === 'orders.nameMatch') return renderTemplate('상품명 매칭', '상품명 매칭 룰', '템플릿');
    if (currentView === 'orders.stockMatch') return renderTemplate('재고 매칭', '주문수량 vs 재고', '템플릿');
    if (currentView === 'orders.bundle') return renderTemplate('묶음정리', '합포장 그룹핑', '템플릿');
    if (currentView === 'orders.allocate') return renderTemplate('재고할당', '할당/차감 미리보기', '템플릿');
    if (currentView === 'orders.invoice') return renderTemplate('송장출력', '송장 생성/출력', '템플릿');
    if (currentView === 'orders.inspectShip') return renderTemplate('검수발송', '검수/출고 상태', '템플릿');
    if (currentView === 'orders.marketShip') return renderTemplate('판매처 발송처리', '마켓 발송처리', '템플릿');

    // 재고관리
    if (currentView === 'inventory.product.list') return <InventoryList />;
    if (currentView === 'inventory.product.create') return <ProductManagement />;
    if (currentView === 'inventory.product.option') return <ProductOptionUpdate />;

    if (currentView === 'inventory.io.list') return <TransactionHistory />;
    if (currentView === 'inventory.io.in') return <InboundForm />;
    if (currentView === 'inventory.io.out') return <OutboundForm />;

    if (currentView === 'inventory.barcode.product') return <BarcodeManagement />;

    if (currentView === 'inventory.warehouse.manage') return <WarehouseManagement />;
    if (currentView === 'inventory.warehouse.moveStock') return <WarehouseTransfer />;

    if (currentView === 'customers') return renderTemplate('고객 관리', '고객 목록/관리', '템플릿');
    if (currentView === 'delivery') return renderTemplate('배송 관리', '배송 상태/관리', '템플릿');
    if (currentView === 'channels') return <ChannelManagement />;

    return renderTemplate('준비 중', `키: ${currentView}`, 'N/A');
  };

  return (
    <div className="dashboard-container">
      <Sidebar
        user={user}
        currentView={currentView}
        setCurrentView={setCurrentView}
        onLogout={onLogout}
        openMenu={openMenu}
        setOpenMenu={setOpenMenu}
      />
      <main className="main-content">{renderMain()}</main>
    </div>
  );
}
