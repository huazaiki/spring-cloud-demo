import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, Tag, message, Typography, Select, DatePicker } from 'antd';
import { PlusOutlined, ReloadOutlined, SendOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { listRequisitions, createRequisition, submitRequisition, convertRequisition, cancelRequisition } from '../api/requisitions';
import { listSuppliers } from '../api/supplier';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

interface Requisition { id: string; prNo: string; totalAmount: number; status: string; expectedDate?: string; purpose?: string; createTime: string; }

const statusColor: Record<string, string> = { DRAFT: 'default', SUBMITTED: 'processing', APPROVED: 'green', REJECTED: 'red', CONVERTED: 'purple', CANCELLED: 'default' };
const statusLabel: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '审批中', APPROVED: '已审批', REJECTED: '已驳回', CONVERTED: '已转订单', CANCELLED: '已取消' };

export default function RequisitionList() {
  const { hasPermission } = useAuth();
  const [list, setList] = useState<Requisition[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [suppliers, setSuppliers] = useState<{ id: string; name: string }[]>([]);
  const [form] = Form.useForm();

  const fetchList = async () => {
    setLoading(true);
    try { const res = await listRequisitions(); setList(res.data || []); }
    catch { message.error('获取请购列表失败'); } finally { setLoading(false); }
  };
  const fetchSuppliers = async () => {
    try {
      const res = await listSuppliers({ page: 1, size: 200 });
      const rows = res.data?.records || res.data || [];
      setSuppliers(rows.map((s: { id: string; name: string }) => ({ id: s.id, name: s.name })));
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchList(); fetchSuppliers(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const items = ((values as { items?: Array<{ itemId: string; itemName: string; quantity: number; amount?: number }> }).items || []).map((it) => ({
        itemId: Number(it.itemId), itemName: it.itemName, quantity: it.quantity, amount: it.amount ?? 0,
      }));
      await createRequisition({
        supplierId: values.supplierId ? Number(values.supplierId) : undefined,
        expectedDate: values.expectedDate ? (values.expectedDate as unknown as dayjs.Dayjs).format('YYYY-MM-DD') : undefined,
        purpose: values.purpose as string, items,
      });
      message.success('请购单创建成功');
      setModalOpen(false); form.resetFields(); fetchList();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败');
    } finally { setSubmitting(false); }
  };

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); message.success(ok); fetchList(); }
    catch (err: unknown) { message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '操作失败'); }
  };

  const columns = [
    { title: '请购单号', dataIndex: 'prNo' },
    { title: '金额', dataIndex: 'totalAmount', width: 120, render: (v: number) => '¥' + Number(v).toFixed(2) },
    { title: '期望到货', dataIndex: 'expectedDate', width: 120 },
    { title: '状态', dataIndex: 'status', width: 110, render: (s: string) => <Tag color={statusColor[s]}>{statusLabel[s] || s}</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    { title: '操作', width: 260, render: (_: unknown, r: Requisition) => (
      <Space>
        {hasPermission('pr:submit') && r.status === 'DRAFT' && (
          <Button size="small" type="primary" icon={<SendOutlined />} onClick={() => act(() => submitRequisition(Number(r.id)), '已提交审批')}>提交</Button>
        )}
        {hasPermission('pr:convert') && r.status === 'APPROVED' && (
          <Button size="small" icon={<CheckOutlined />} onClick={() => act(() => convertRequisition(Number(r.id)), '已转采购订单')}>转订单</Button>
        )}
        {hasPermission('pr:update') && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status) && (
          <Button size="small" danger icon={<CloseOutlined />} onClick={() => act(() => cancelRequisition(Number(r.id)), '已取消')}>取消</Button>
        )}
      </Space>
    )},
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>请购管理</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchList}>刷新</Button>
          {hasPermission('pr:create') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新建请购</Button>
          )}
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={list} loading={loading} />
      <Modal title="新建请购单" open={modalOpen} width={640} onCancel={() => { setModalOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="supplierId" label="意向供应商">
            <Select allowClear showSearch placeholder="可选" filterOption={(input, option) => (option?.label as string || '').includes(input)}
              options={suppliers.map((s) => ({ label: s.name, value: s.id }))} />
          </Form.Item>
          <Form.Item name="expectedDate" label="期望到货日期"><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="purpose" label="用途说明"><Input.TextArea rows={2} /></Form.Item>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item {...rest} name={[name, 'itemId']} rules={[{ required: true, message: '物料ID' }]}><Input placeholder="物料ID" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'itemName']} rules={[{ required: true, message: '物料名' }]}><Input placeholder="物料名称" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'quantity']} rules={[{ required: true, message: '数量' }]}><InputNumber placeholder="数量" min={1} /></Form.Item>
                    <Form.Item {...rest} name={[name, 'amount']}><InputNumber placeholder="预估金额" min={0} /></Form.Item>
                    <Button danger onClick={() => remove(name)}>删除</Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block>+ 添加行项</Button>
              </>
            )}
          </Form.List>
          <Form.Item style={{ marginTop: 16 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block>保存请购单</Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}