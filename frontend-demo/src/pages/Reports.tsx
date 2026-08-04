import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, message, Typography, Tabs, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { procurementDashboard, payableAging, inventorySummary } from '../api/reports';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

const statusLabel: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '审批中', APPROVED: '已审批', SHIPPED: '已发货', RECEIVED: '已入库', SETTLED: '已结清', CANCELLED: '已取消' };

export default function Reports() {
  const { hasPermission } = useAuth();
  const [proc, setProc] = useState<{ totalOrders: number; totalAmount: number; byStatus: { status: string; count: number; amount: number }[] } | null>(null);
  const [aging, setAging] = useState<{ totalPayable: number; overdueCount: number; buckets: { bucket: string; amount: number; count: number }[] } | null>(null);
  const [inv, setInv] = useState<{ totalItems: number; totalAvailable: number; totalReserved: number; lowStock: { itemId: string; itemName: string; available: number; reorderPoint: number }[] } | null>(null);

  const fetchAll = async () => {
    try {
      const [p, a, i] = await Promise.allSettled([procurementDashboard(), payableAging(), inventorySummary()]);
      if (p.status === 'fulfilled') setProc(p.value.data);
      if (a.status === 'fulfilled') setAging(a.value.data);
      if (i.status === 'fulfilled') setInv(i.value.data);
    } catch { message.error('获取报表失败'); }
  };
  useEffect(() => { fetchAll(); }, []);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>报表</Title>
        <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
      </div>
      <Tabs items={[
        { key: 'proc', label: '采购看板', disabled: !hasPermission('report:procurement'), children: proc ? (
          <>
            <Row gutter={[16, 16]}>
              <Col span={8}><Card><Statistic title="订单总数" value={proc.totalOrders} /></Card></Col>
              <Col span={8}><Card><Statistic title="订单总金额" value={proc.totalAmount} precision={2} prefix="¥" /></Card></Col>
              <Col span={8}><Card><Statistic title="状态数" value={proc.byStatus.length} /></Card></Col>
            </Row>
            <Table rowKey="status" style={{ marginTop: 16 }} pagination={false}
              columns={[
                { title: '状态', dataIndex: 'status', render: (s: string) => <Tag>{statusLabel[s] || s}</Tag> },
                { title: '单数', dataIndex: 'count' },
                { title: '金额', dataIndex: 'amount', render: (v: number) => '¥' + Number(v).toFixed(2) },
              ]} dataSource={proc.byStatus} />
          </>
        ) : <p>无权限或暂无数据</p> },
        { key: 'aging', label: '应付账龄', disabled: !hasPermission('report:payable-aging'), children: aging ? (
          <>
            <Row gutter={[16, 16]}>
              <Col span={8}><Card><Statistic title="应付总额" value={aging.totalPayable} precision={2} prefix="¥" /></Card></Col>
              <Col span={8}><Card><Statistic title="逾期笔数" value={aging.overdueCount} valueStyle={{ color: aging.overdueCount > 0 ? '#cf1322' : undefined }} /></Card></Col>
            </Row>
            <Table rowKey="bucket" style={{ marginTop: 16 }} pagination={false}
              columns={[
                { title: '账龄区间', dataIndex: 'bucket' },
                { title: '笔数', dataIndex: 'count' },
                { title: '金额', dataIndex: 'amount', render: (v: number) => '¥' + Number(v).toFixed(2) },
              ]} dataSource={aging.buckets} />
          </>
        ) : <p>无权限或暂无数据</p> },
        { key: 'inv', label: '库存汇总', disabled: !hasPermission('report:inventory'), children: inv ? (
          <>
            <Row gutter={[16, 16]}>
              <Col span={8}><Card><Statistic title="库存行数" value={inv.totalItems} /></Card></Col>
              <Col span={8}><Card><Statistic title="可用总量" value={inv.totalAvailable} /></Card></Col>
              <Col span={8}><Card><Statistic title="预留总量" value={inv.totalReserved} /></Card></Col>
            </Row>
            <Title level={5} style={{ marginTop: 24 }}>低库存预警（≤再订购点）</Title>
            <Table rowKey="itemId" pagination={false} dataSource={inv.lowStock}
              columns={[
                { title: '物料', dataIndex: 'itemName' },
                { title: '可用', dataIndex: 'available' },
                { title: '再订购点', dataIndex: 'reorderPoint' },
              ]} />
          </>
        ) : <p>无权限或暂无数据</p> },
      ]} />
    </div>
  );
}