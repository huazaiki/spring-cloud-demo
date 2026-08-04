import { useEffect, useState } from 'react';
import { Table, Button, Modal, Input, Space, Tag, message, Typography } from 'antd';
import { ReloadOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { myTasks, approveTask, rejectTask } from '../api/approvalTasks';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

interface Task { id: string; bizType: string; bizId: string; nodeName: string; approverRole: string; status: string; createTime: string; }

const bizLabel: Record<string, string> = { PR: '请购', PO: '采购订单', PAYMENT: '付款' };

export default function ApprovalTasks() {
  const { hasPermission } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [actingId, setActingId] = useState<string | null>(null);
  const [opinion, setOpinion] = useState('');
  const [modal, setModal] = useState<{ type: 'approve' | 'reject'; task: Task } | null>(null);

  const fetchTasks = async () => {
    setLoading(true);
    try { const res = await myTasks(); setTasks(res.data || []); }
    catch { message.error('获取待办失败'); } finally { setLoading(false); }
  };
  useEffect(() => { fetchTasks(); }, []);

  const doAct = async (taskId: string, type: 'approve' | 'reject') => {
    setActingId(taskId);
    try {
      if (type === 'approve') await approveTask(taskId, opinion || undefined);
      else await rejectTask(taskId, opinion || '驳回');
      message.success(type === 'approve' ? '已通过' : '已驳回');
      setModal(null); setOpinion(''); fetchTasks();
    } catch (err: unknown) {
      message.error((err as { response?: { data?: { message?: string } } })?.response?.data?.message || '操作失败');
    } finally { setActingId(null); }
  };

  const columns = [
    { title: '单据类型', dataIndex: 'bizType', width: 100, render: (b: string) => <Tag>{bizLabel[b] || b}</Tag> },
    { title: '单据 ID', dataIndex: 'bizId', width: 120 },
    { title: '节点', dataIndex: 'nodeName', width: 140 },
    { title: '审批角色', dataIndex: 'approverRole', width: 140 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    { title: '操作', width: 180, render: (_: unknown, r: Task) => (
      <Space>
        {hasPermission('approval:task:approve') && (
          <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => setModal({ type: 'approve', task: r })}>通过</Button>
        )}
        {hasPermission('approval:task:reject') && (
          <Button size="small" danger icon={<CloseOutlined />} onClick={() => setModal({ type: 'reject', task: r })}>驳回</Button>
        )}
      </Space>
    )},
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>待办中心</Title>
        <Button icon={<ReloadOutlined />} onClick={fetchTasks}>刷新</Button>
      </div>
      <Table rowKey="id" columns={columns} dataSource={tasks} loading={loading} />
      <Modal
        title={modal?.type === 'approve' ? '审批通过' : '驳回'}
        open={!!modal}
        onCancel={() => { setModal(null); setOpinion(''); }}
        onOk={() => modal && doAct(modal.task.id, modal.type)}
        confirmLoading={actingId !== null}
        okText={modal?.type === 'approve' ? '通过' : '驳回'}
        okButtonProps={{ danger: modal?.type === 'reject' }}
      >
        <p>单据：{modal ? bizLabel[modal.task.bizType] || modal.task.bizType + ' #' + modal.task.bizId : ''}（{modal?.task.nodeName}）</p>
        <Input.TextArea rows={3} placeholder="审批意见" value={opinion} onChange={(e) => setOpinion(e.target.value)} />
      </Modal>
    </div>
  );
}