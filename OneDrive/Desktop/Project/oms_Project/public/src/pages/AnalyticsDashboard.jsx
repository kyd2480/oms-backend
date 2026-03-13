import { useEffect, useState } from 'react';
import { fetchAnalyticsOverview } from '../lib/analyticsService';

export default function AnalyticsDashboard() {
    const [range, setRange] = useState('7d');
    const [data, setData] = useState(null);
    const [isSample, setIsSample] = useState(false);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadData();
    }, [range]);

    const loadData = async () => {
        setLoading(true);
        const result = await fetchAnalyticsOverview(range);
        setData(result.data);
        setIsSample(result.isSample);
        setLoading(false);
    };

    if (loading || !data) {
        return <div className="page">통계 불러오는 중...</div>;
    }

    return (
        <div className="page">
            <div className="page-header">
                <h2>통계 대시보드</h2>
                {isSample && <span style={{ marginLeft: 8, color: '#999' }}>(샘플 데이터)</span>}
            </div>

            {/* 기간 선택 */}
            <div style={{ marginBottom: 16 }}>
                <select value={range} onChange={e => setRange(e.target.value)}>
                    <option value="1d">오늘</option>
                    <option value="7d">최근 7일</option>
                    <option value="30d">최근 30일</option>
                </select>
            </div>

            {/* KPI */}
            <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
                <Kpi title="총 주문" value={data.totalOrders} />
                <Kpi title="총 매출" value={`${data.totalAmount.toLocaleString()}원`} />
                <Kpi title="출고 완료" value={data.shipped} />
                <Kpi title="취소/반품" value={data.canceled} />
            </div>

            {/* 판매처별 */}
            <Section title="판매처별 주문">
                <SimpleTable
                    headers={['판매처', '주문수', '매출']}
                    rows={data.byMall.map(m => [
                        m.mall,
                        m.orders,
                        `${m.amount.toLocaleString()}원`,
                    ])}
                />
            </Section>

            {/* 상태별 */}
            <Section title="주문 상태별">
                <SimpleTable
                    headers={['상태', '건수']}
                    rows={data.byStatus.map(s => [s.status, s.count])}
                />
            </Section>
        </div>
    );
}

function Kpi({ title, value }) {
    return (
        <div style={{ padding: 16, border: '1px solid #ddd', borderRadius: 6, minWidth: 140 }}>
            <div style={{ fontSize: 13, color: '#666' }}>{title}</div>
            <div style={{ fontSize: 20, fontWeight: 600 }}>{value}</div>
        </div>
    );
}

function Section({ title, children }) {
    return (
        <div style={{ marginBottom: 24 }}>
            <h3 style={{ marginBottom: 8 }}>{title}</h3>
            {children}
        </div>
    );
}

function SimpleTable({ headers, rows }) {
    return (
        <table className="table">
            <thead>
                <tr>
                    {headers.map(h => (
                        <th key={h}>{h}</th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {rows.map((r, i) => (
                    <tr key={i}>
                        {r.map((c, j) => (
                            <td key={j}>{c}</td>
                        ))}
                    </tr>
                ))}
            </tbody>
        </table>
    );
}
