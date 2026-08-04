import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, Tag, message, Typography, Tabs } from 'antd';
import { PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, ImportOutlined } from '@ant-design/icons';
import { listReceives, createReceive, stockIn } from '../api/receives';
import { createQualityInspection } from '../api/qualityInspections';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

interface Receive { id: string; receiveNo: string; orderId: string; status: string; receiveDate: string; }

export default function Receives() {
  const { hasPermission } = useAuth();
  const [list, setList] = useState<Receive[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const [qcForm] = Form.useForm();
  const [createOpen, setCreateOpen] = useState(false);
  const [qcOpen, setQcOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [actingId, setActingId] = useState<string | null>(null);

  const fetchList = async () => {
    setLoading(true);
    try { const res = await listReceives(); setList(res.data || []); }
    catch { message.error('获取收货单失败'); } finally { setLoading(false); }
  };
  useEffect(() => { fetchList(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const items = ((values as { items?: Array<{ orderItemId: string; itemId: string; receivedQty: number }> }).items || []).map((it) => ({
        orderItemId: Number(it.orderItemId), itemId: Number(it.itemId), receivedQty: it.receivedQty,
      }));
      await createReceive({ orderId: Number((values as { orderId: string }).orderId), items });
      message.success('收货单登记成功');
      setCreateOpen(false); form.resetFields(); fetchList();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '登记失败');
    } finally { setSubmitting(false); }
  };

  const handleQc = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createQualityInspection({
        receiveItemId: Number((values as { receiveItemId: string }).receiveItemId),
        inspectType: 'FULL',
        inspectQty: Number((values as { inspectQty: number }).inspectQty),
        qualifiedQty: Number((values as { qualifiedQty: number }).qualifiedQty),
      });
      message.success('质检登记成功');
      setQcOpen(false); qcForm.resetFields();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '质检失败');
    } finally { setSubmitting(false); }
  };

  const columns = [
    { title: '收货单号', dataIndex: 'receiveNo' },
    { title: '订单 ID', dataIndex: 'orderId', width: 120 },
    { title: '收货时间', dataIndex: 'receiveDate', width: 170 },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={s === 'RECEIVED' ? 'blue' : 'default'}>{s}</Tag> },
    { title: '操作', width: 200, render: (_: unknown, r: Receive) => (
      <Space>
        {hasPermission('qc:create') && (
          <Button size="small" icon={<SafetyCertificateOutlined />} onClick={() => setQcOpen(true)}>质检</Button>
        )}
        {hasPermission('stock:stock-in') && (
          <Button size="small" type="primary" icon={<ImportOutlined />} loading={actingId === r.id}
            onClick={async () => {
              setActingId(r.id);
              try { await stockIn(Number(r.id)); message.success('入库成功'); fetchList(); }
              catch (err: unknown) { message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '入库失败'); }
              finally { setActingId(null); }
            }}>入库</Button>
        )}
      </Space>
    )},
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>收货 / 质检 / 入库</Title>
      <Tabs
        items={[{
          key: 'receive', label: '收货单',
          children: (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <Button icon={<ReloadOutlined />} onClick={fetchList}>刷新</Button>
                {hasPermission('receive:create') && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>登记收货</Button>
                )}
              </div>
              <Table rowKey="id" columns={columns} dataSource={list} loading={loading} />
            </div>
          ),
        }]}
      />
      <Modal title="登记收货" open={createOpen} width={640} onCancel={() => { setCreateOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="orderId" label="采购订单 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item {...rest} name={[name, 'orderItemId']} rules={[{ required: true, message: '订单明细ID' }]}><Input placeholder="订单明细ID" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'itemId']} rules={[{ required: true, message: '物料ID' }]}><Input placeholder="物料ID" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'receivedQty']} rules={[{ required: true, message: '实收' }]}><InputNumber placeholder="实收数量" min={0} /></Form.Item>
                    <Button danger onClick={() => remove(name)}>删除</Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block>+ 添加行项</Button>
              </>
            )}
          </Form.List>
          <Form.Item style={{ marginTop: 16 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block>保存收货单</Button>
          </Form.Item>
        </Form>
      </Modal>
      <Modal title="质检登记" open={qcOpen} onCancel={() => { setQcOpen(false); qcForm.resetFields(); }} footer={null}>
        <Form form={qcForm} layout="vertical" onFinish={handleQc}>
          <Form.Item name="receiveItemId" label="收货明细 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="inspectQty" label="检验数量" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="qualifiedQty" label="合格数量" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>保存质检</Button>
        </Form>
      </Modal>
    </div>
  );
}