import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, InputNumber, Space, Tag, message, Typography, Select } from 'antd';
import { PlusOutlined, ReloadOutlined, CheckOutlined } from '@ant-design/icons';
import { createPayable, approvePayable } from '../api/payment';
import { listSuppliers } from '../api/supplier';

const { Title } = Typography;

interface Payable { id: number; orderId: number; supplierId: number; amount: number; dueDate: string; status: string; createTime: string; }

const statusColor: Record<string, string> = { PENDING: 'orange', APPROVED: 'blue', SETTLED: 'green' };
const statusLabel: Record<string, string> = { PENDING: '待审批', APPROVED: '已审批', SETTLED: '已结清' };
const fmtMoney = (v: number) => v != null ? ('¥' + v.toFixed(2)) : '-';

export default function PaymentList() {
  const [payables, setPayables] = useState<Payable[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [suppliers, setSuppliers] = useState<{ id: number; name: string }[]>([]);
  const [approvingId, setApprovingId] = useState<number | null>(null);

  const fetchPayables = () => {
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
      const res = await createPayable(values as { orderId: number; supplierId: number; amount: number });
      const created = res.data;
      setPayables((prev) => [...prev, {
        id: created?.id ?? Date.now(),
        orderId: (values as { orderId: number }).orderId,
        supplierId: (values as { supplierId: number }).supplierId,
        amount: (values as { amount: number }).amount,
        dueDate: created?.dueDate ?? '',
        status: 'PENDING',
        createTime: new Date().toISOString(),
      }]);
      message.success('应付账款创建成功');
      setModalOpen(false); form.resetFields();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally { setSubmitting(false); }
  };

  const handleApprove = async (id: number) => {
    setApprovingId(id);
    try {
      await approvePayable(id);
      setPayables((prev) => prev.map((p) => p.id === id ? { ...p, status: 'APPROVED' } : p));
      message.success('审批成功');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '审批失败';
      message.error(msg);
    } finally { setApprovingId(null); }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '订单 ID', dataIndex: 'orderId', width: 80 },
    { title: '供应商 ID', dataIndex: 'supplierId', width: 90 },
    { title: '金额', dataIndex: 'amount', width: 120, render: fmtMoney },
    { title: '账期', dataIndex: 'dueDate', width: 120 },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={statusColor[s]}>{statusLabel[s] || s}</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    { title: '操作', width: 120, render: (_: unknown, record: Payable) => (
      <Button size="small" icon={<CheckOutlined />} loading={approvingId === record.id}
        disabled={record.status !== 'PENDING'} onClick={() => handleApprove(record.id)}>审批</Button>
    )},
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>应付账款</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchPayables}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>创建应付</Button>
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={payables} loading={loading} />
      <Modal title="创建应付账款" open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="orderId" label="采购订单 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="supplierId" label="供应商" rules={[{ required: true }]}>
            <Select showSearch placeholder="选择供应商" filterOption={(input, option) => (option?.label as string || '').includes(input)}
              options={suppliers.map((s) => ({ label: s.name+' (ID: '+s.id+')', value: s.id }))} />
          </Form.Item>
          <Form.Item name="amount" label="金额" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} min={0} step={0.01} prefix="¥" />
          </Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={submitting} block>提交</Button></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}