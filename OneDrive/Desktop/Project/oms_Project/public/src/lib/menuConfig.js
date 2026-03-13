export const MENU = [
  { key: 'dashboard', label: '대시보드', group: null },

  {
    key: 'orders',
    label: '주문 관리',
    group: 'section',
    children: [
      { key: 'orders.orderinput', label: '주문입력' },
      { key: 'orders.dupcheck', label: '중복주문체크' },
      { key: 'orders.automatch', label: '자동매칭' },
      { key: 'orders.nameMatch', label: '상품명 매칭' },
      { key: 'orders.stockMatch', label: '재고 매칭' },
      { key: 'orders.bundle', label: '묶음정리' },
      { key: 'orders.allocate', label: '재고할당' },
      { key: 'orders.invoice', label: '송장출력' },
      { key: 'orders.inspectShip', label: '검수발송' },
      { key: 'orders.marketShip', label: '판매처 발송처리' },
    ],
  },

  {
    key: 'inventory',
    label: '재고 관리',
    group: 'section',
    children: [
      { key: 'inventory.product.list', label: '재고현황목록' },
      { key: 'inventory.product.create', label: '상품등록' },
      { key: 'inventory.product.option', label: '상품 옵션수정' },

      { key: 'inventory.io.list', label: '입출고 목록' },
      { key: 'inventory.io.in', label: '상품입고' },
      { key: 'inventory.io.out', label: '상품출고' },

      { key: 'inventory.barcode.product', label: '상품바코드관리' },

      { key: 'inventory.warehouse.manage', label: '창고 관리' },
      { key: 'inventory.warehouse.moveStock', label: '상품 창고 이동' },
    ],
  },

  { key: 'customers', label: '고객 관리', group: null },
  { key: 'delivery', label: '배송 관리', group: null },
];
