import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Typography } from 'antd';
import { TeamOutlined, InboxOutlined, FileTextOutlined, DollarOutlined } from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { listSuppliers } from '../api/supplier';
import { listItems } from '../api/inventory';
import { listOrders } from '../api/purchase';
import { listPayables } from '../api/payment';

const { Title, Paragraph } = Typography;

const roleLabels: Record<string, string> = {
  PURCHASER: '采购员', WAREHOUSE: '仓管员', FINANCE: '财务', ADMIN: '管理员',
  PURCHASE_MANAGER: '采购经理', DEPT_MANAGER: '部门经理', FINANCE_MANAGER: '财务经理', FINANCE_DIRECTOR: '财务总监',
};

export default function Dashboard() {
  const { username, roles } = useAuth();
  const [stats, setStats] = useState({ suppliers: '-', items: '-', orders: '-', payables: '-' });

  useEffect(() => {
    (async () => {
      try {
        const [s, i, o, p] = await Promise.allSettled([listSuppliers({ page: 1, size: 1 }), listItems(), listOrders(), listPayables()]);
        const val = (r: PromiseSettledResult<unknown>) => {
          if (r.status !== 'fulfilled') return '-';
          const data = (r.value as { data?: unknown }).data;
          if (Array.isArray(data)) return String(data.length);
          if (data && typeof data === 'object' && 'total' in (data as object)) return String((data as { total: unknown }).total);
          return '-';
        };
        setStats({ suppliers: val(s), items: val(i), orders: val(o), payables: val(p) });
      } catch { /* keep placeholder */ }
    })();
  }, []);

  return (
    <div>
      <Title level={4}>欢迎，{username}（{roleLabels[(roles || [])[0]] || (roles || [])[0] || '—'}）</Title>
      <Paragraph type="secondary">采购-入库-付款全链路管理系统。使用左侧导航进入各功能模块。</Paragraph>
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} sm={12} lg={6}><Card><Statistic title="供应商" prefix={<TeamOutlined />} value={stats.suppliers} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card><Statistic title="物料" prefix={<InboxOutlined />} value={stats.items} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card><Statistic title="采购订单" prefix={<FileTextOutlined />} value={stats.orders} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card><Statistic title="应付账款" prefix={<DollarOutlined />} value={stats.payables} /></Card></Col>
      </Row>
      <Card style={{ marginTop: 24 }}>
        <Title level={5}>业务闭环</Title>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', padding: '16px 0' }}>
          {[
            { label: '请购', desc: '请购 → 审批链' },
            { label: '采购订单', desc: '审批 + 预留库存' },
            { label: '收货质检', desc: '收货 → 质检 → 入库' },
            { label: '应付发票', desc: '应付 → 发票 → 三单匹配' },
            { label: '付款核销', desc: '付款单 → 核销 → 结清' },
          ].map((step, i) => (
            <div key={step.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Card size="small" style={{ minWidth: 130, textAlign: 'center' }}>
                <div style={{ fontWeight: 600, fontSize: 13 }}>{step.label}</div>
                <div style={{ fontSize: 11, color: '#888', marginTop: 4 }}>{step.desc}</div>
              </Card>
              {i < 4 && <span style={{ fontSize: 18, color: '#1677ff' }}>→</span>}
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}