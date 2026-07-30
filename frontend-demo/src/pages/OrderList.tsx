import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, Tag, message, Typography, Select } from 'antd';
import { PlusOutlined, ReloadOutlined, CheckOutlined } from '@ant-design/icons';
import { createOrder, approveOrder } from '../api/purchase';
import { listSuppliers } from '../api/supplier';

const { Title } = Typography;

interface Order { id: number; orderNo: string; supplierId: number; totalAmount: number; status: string; createTime: string; }

const statusColor: Record<string, string> = { DRAFT: 'default', APPROVED: 'blue', SHIPPED: 'cyan', RECEIVED: 'green', SETTLED: 'purple', CANCELLED: 'red' };
const statusLabel: Record<string, string> = { DRAFT: '草稿', APPROVED: '已审批', SHIPPED: '已发货', RECEIVED: '已入库', SETTLED: '已结清', CANCELLED: '已取消' };
const fmtMoney = (v: number) => v != null ? ('¥' + v.toFixed(2)) : '-';

export default function OrderList() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [suppliers, setSuppliers] = useState<{ id: number; name: string }[]>([]);
  const [approvingId, setApprovingId] = useState<number | null>(null);

  const fetchOrders = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 300);
  };

  const fetchSuppliers = async () => {
    try {
      const res = await listSuppliers({ page: 1, size: 200 });
      const list = res.data?.records || res.data || [];
      setSuppliers(list.map((s: { id: number; name: string }) => ({ id: s.id, name: s.name })));
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchSuppliers(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const items = ((values as { items?: Array<{ itemId: number; itemName: string; quantity: number; unitPrice: number }> }).items || []).map((it) => ({
        ...it, amount: it.quantity * it.unitPrice,
      }));
      const res = await createOrder({ supplierId: (values as { supplierId: number }).supplierId, items });
      const created = res.data;
      const total = items.reduce((sum, it) => sum + it.amount, 0);
      setOrders((prev) => [...prev, {
        id: created?.id ?? Date.now(),
        orderNo: created?.orderNo ?? ('PO-' + Date.now()),
        supplierId: (values as { supplierId: number }).supplierId,
        totalAmount: total,
        status: 'DRAFT',
        createTime: new Date().toISOString(),
      }]);
      message.success('订单创建成功');
      setModalOpen(false); form.resetFields();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally { setSubmitting(false); }
  };

  const handleApprove = async (id: number) => {
    setApprovingId(id);
    try {
      await approveOrder(id);
      setOrders((prev) => prev.map((o) => o.id === id ? { ...o, status: 'APPROVED' } : o));
      message.success('审批成功');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '审批失败';
      message.error(msg);
    } finally { setApprovingId(null); }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '订单号', dataIndex: 'orderNo' },
    { title: '供应商 ID', dataIndex: 'supplierId', width: 90 },
    { title: '金额', dataIndex: 'totalAmount', width: 100, render: fmtMoney },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={statusColor[s]}>{statusLabel[s] || s}</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    { title: '操作', width: 120, render: (_: unknown, record: Order) => (
      <Button size="small" icon={<CheckOutlined />} loading={approvingId === record.id}
        disabled={record.status !== 'DRAFT'} onClick={() => handleApprove(record.id)}>审批</Button>
    )},
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>采购订单</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchOrders}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>创建订单</Button>
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={orders} loading={loading} />
      <Modal title="创建采购订单" open={modalOpen} width={640}
        onCancel={() => { setModalOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="supplierId" label="供应商" rules={[{ required: true }]}>
            <Select showSearch placeholder="选择供应商" filterOption={(input, option) => (option?.label as string || '').includes(input)}
              options={suppliers.map((s) => ({ label: s.name+' (ID: '+s.id+')', value: s.id }))} />
          </Form.Item>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item {...rest} name={[name, 'itemId']} rules={[{ required: true, message: '物料ID' }]}>
                      <InputNumber placeholder="物料ID" min={1} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'itemName']} rules={[{ required: true, message: '物料名' }]}>
                      <Input placeholder="物料名称" />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'quantity']} rules={[{ required: true, message: '数量' }]}>
                      <InputNumber placeholder="数量" min={1} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'unitPrice']} rules={[{ required: true, message: '单价' }]}>
                      <InputNumber placeholder="单价" min={0} step={0.01} />
                    </Form.Item>
                    <Button danger onClick={() => remove(name)}>删除</Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block>+ 添加行项</Button>
              </>
            )}
          </Form.List>
          <Form.Item style={{ marginTop: 16 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block>提交订单</Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}