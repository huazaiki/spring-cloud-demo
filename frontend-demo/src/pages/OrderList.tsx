import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, Tag, message, Typography, Select } from 'antd';
import { PlusOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, ImportOutlined } from '@ant-design/icons';
import { listOrders, createOrder, approveOrder, cancelOrder, advanceOrderStatus } from '../api/purchase';
import { listSuppliers } from '../api/supplier';

const { Title } = Typography;

interface Order { id: string; orderNo: string; supplierId: string; totalAmount: number; status: string; createTime: string; }
interface Supplier { id: string; name: string; }

const statusColor: Record<string, string> = { DRAFT: 'default', APPROVED: 'blue', SHIPPED: 'cyan', RECEIVED: 'green', SETTLED: 'purple', CANCELLED: 'red' };
const statusLabel: Record<string, string> = { DRAFT: '草稿', APPROVED: '已审批', SHIPPED: '已发货', RECEIVED: '已入库', SETTLED: '已结清', CANCELLED: '已取消' };
const fmtMoney = (v: number) => v != null ? ('¥' + Number(v).toFixed(2)) : '-';

export default function OrderList() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [approvingId, setApprovingId] = useState<string | null>(null);

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const res = await listOrders();
      setOrders(res.data || []);
    } catch {
      message.error('获取订单列表失败');
    } finally { setLoading(false); }
  };

  const fetchSuppliers = async () => {
    try {
      const res = await listSuppliers({ page: 1, size: 200 });
      const list = res.data?.records || res.data || [];
      setSuppliers(list.map((s: Supplier) => ({ id: s.id, name: s.name })));
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchOrders(); fetchSuppliers(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const items = ((values as { items?: Array<{ itemId: string; itemName: string; quantity: number; unitPrice: number }> }).items || []).map((it) => ({
        itemId: Number(it.itemId), itemName: it.itemName, quantity: it.quantity, unitPrice: it.unitPrice, amount: it.quantity * it.unitPrice,
      }));
      await createOrder({ supplierId: Number((values as { supplierId: string }).supplierId), items });
      message.success('订单创建成功');
      setModalOpen(false); form.resetFields();
      fetchOrders();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally { setSubmitting(false); }
  };

  const handleApprove = async (id: string) => {
    setApprovingId(id);
    try {
      await approveOrder(Number(id));
      message.success('审批成功');
      fetchOrders();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '审批失败';
      message.error(msg);
    } finally { setApprovingId(null); }
  };

  const handleCancel = async (id: string) => {
    try { await cancelOrder(Number(id)); message.success('订单已取消'); fetchOrders(); }
    catch (err: unknown) { message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '取消失败'); }
  };

  const handleAdvance = async (id: string, status: string) => {
    try { await advanceOrderStatus(Number(id), status); message.success('状态已更新'); fetchOrders(); }
    catch (err: unknown) { message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '状态更新失败'); }
  };
  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, ellipsis: true },
    { title: '订单号', dataIndex: 'orderNo' },
    { title: '供应商 ID', dataIndex: 'supplierId', width: 100, ellipsis: true },
    { title: '金额', dataIndex: 'totalAmount', width: 100, render: fmtMoney },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={statusColor[s]}>{statusLabel[s] || s}</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    { title: '操作', width: 240, render: (_: unknown, record: Order) => (
      <Space>
        <Button size="small" type="primary" icon={<CheckOutlined />} loading={approvingId === record.id}
          disabled={record.status !== 'DRAFT'} onClick={() => handleApprove(record.id)}>审批</Button>
        {['APPROVED', 'SHIPPED'].includes(record.status) && (
          <Button size="small" icon={<ImportOutlined />} onClick={() => handleAdvance(record.id, 'RECEIVED')}>标记收货</Button>
        )}
        {['DRAFT', 'APPROVED'].includes(record.status) && (
          <Button size="small" danger icon={<CloseOutlined />} onClick={() => handleCancel(record.id)}>取消</Button>
        )}
      </Space>
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
                      <Input placeholder="物料ID" />
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