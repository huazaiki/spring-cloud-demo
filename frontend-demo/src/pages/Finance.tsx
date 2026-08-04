import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Tag, message, Typography, Tabs, DatePicker } from 'antd';
import { PlusOutlined, ReloadOutlined, CheckOutlined, DollarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { listInvoices, createInvoice, matchInvoice } from '../api/invoices';
import { listPaymentVouchers, payVoucher } from '../api/paymentVouchers';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

export default function Finance() {
  const { hasPermission } = useAuth();
  const [invoices, setInvoices] = useState<any[]>([]);
  const [vouchers, setVouchers] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [invOpen, setInvOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [invForm] = Form.useForm();
  const [payForm] = Form.useForm();

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [i, p] = await Promise.all([listInvoices(), listPaymentVouchers()]);
      setInvoices(i.data || []); setVouchers(p.data || []);
    } catch { message.error('获取财务数据失败'); } finally { setLoading(false); }
  };
  useEffect(() => { fetchAll(); }, []);

  const handleCreateInvoice = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createInvoice({
        supplierId: Number((values as { supplierId: string }).supplierId),
        orderId: values.orderId ? Number(values.orderId) : undefined,
        invoiceNo: values.invoiceNo as string,
        invoiceDate: (values.invoiceDate as unknown as dayjs.Dayjs).format('YYYY-MM-DD'),
        totalAmount: Number((values as { totalAmount: number }).totalAmount),
        taxAmount: Number((values as { taxAmount?: number }).taxAmount || 0),
      });
      message.success('发票登记成功'); setInvOpen(false); invForm.resetFields(); fetchAll();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '登记失败');
    } finally { setSubmitting(false); }
  };

  const handlePay = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const payableIds = String(values.payableIds).split(',').map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n));
      await payVoucher({
        supplierId: Number((values as { supplierId: string }).supplierId),
        amount: Number((values as { amount: number }).amount),
        method: (values.method as string) || 'TRANSFER',
        payableIds,
      });
      message.success('付款成功'); setPayOpen(false); payForm.resetFields(); fetchAll();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '付款失败');
    } finally { setSubmitting(false); }
  };

  const invoiceColumns = [
    { title: '发票号', dataIndex: 'invoiceNo' },
    { title: '供应商 ID', dataIndex: 'supplierId', width: 110 },
    { title: '金额', dataIndex: 'totalAmount', width: 120, render: (v: number) => '¥' + Number(v).toFixed(2) },
    { title: '状态', dataIndex: 'status', width: 110, render: (s: string) => <Tag color={s === 'MATCHED' ? 'green' : 'default'}>{s}</Tag> },
    { title: '操作', width: 130, render: (_: unknown, r: { id: string; status: string }) => (
      hasPermission('invoice:match') && r.status === 'REGISTERED' ? (
        <Button size="small" icon={<CheckOutlined />} onClick={async () => {
          try { await matchInvoice(Number(r.id)); message.success('匹配完成'); fetchAll(); }
          catch (err: unknown) { message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '匹配失败'); }
        }}>三单匹配</Button>
      ) : null
    )},
  ];
  const voucherColumns = [
    { title: '付款单号', dataIndex: 'paymentNo' },
    { title: '供应商 ID', dataIndex: 'supplierId', width: 110 },
    { title: '金额', dataIndex: 'amount', width: 120, render: (v: number) => '¥' + Number(v).toFixed(2) },
    { title: '方式', dataIndex: 'method', width: 110 },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={s === 'PAID' ? 'green' : 'default'}>{s}</Tag> },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>发票与付款</Title>
      <Tabs items={[
        { key: 'inv', label: '发票', children: (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
              {hasPermission('invoice:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setInvOpen(true)}>登记发票</Button>}
            </div>
            <Table rowKey="id" columns={invoiceColumns} dataSource={invoices} loading={loading} />
          </div>
        )},
        { key: 'pay', label: '付款单', children: (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
              {hasPermission('payment:pay') && <Button type="primary" icon={<DollarOutlined />} onClick={() => setPayOpen(true)}>创建付款单</Button>}
            </div>
            <Table rowKey="id" columns={voucherColumns} dataSource={vouchers} loading={loading} />
          </div>
        )},
      ]} />
      <Modal title="登记发票" open={invOpen} onCancel={() => { setInvOpen(false); invForm.resetFields(); }} footer={null}>
        <Form form={invForm} layout="vertical" onFinish={handleCreateInvoice}>
          <Form.Item name="invoiceNo" label="发票号" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="supplierId" label="供应商 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="orderId" label="订单 ID"><Input /></Form.Item>
          <Form.Item name="invoiceDate" label="开票日期" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="totalAmount" label="金额" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="taxAmount" label="税额"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>保存</Button>
        </Form>
      </Modal>
      <Modal title="创建付款单（执行付款+核销）" open={payOpen} onCancel={() => { setPayOpen(false); payForm.resetFields(); }} footer={null}>
        <Form form={payForm} layout="vertical" onFinish={handlePay}>
          <Form.Item name="supplierId" label="供应商 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="amount" label="付款金额" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="method" label="付款方式"><Input defaultValue="TRANSFER" /></Form.Item>
          <Form.Item name="payableIds" label="应付 ID（逗号分隔）" rules={[{ required: true }]}><Input placeholder="如 1001,1002" /></Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>付款并核销</Button>
        </Form>
      </Modal>
    </div>
  );
}