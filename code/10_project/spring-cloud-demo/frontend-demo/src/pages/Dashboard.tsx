import { Card, Row, Col, Statistic, Typography } from 'antd';
import {
  TeamOutlined,
  InboxOutlined,
  FileTextOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';

const { Title, Paragraph } = Typography;

const roleLabels: Record<string, string> = {
  PURCHASER: '采购员',
  WAREHOUSE: '仓管员',
  FINANCE: '财务',
  ADMIN: '管理员',
};

export default function Dashboard() {
  const { username, role } = useAuth();

  return (
    <div>
      <Title level={4}>欢迎，{username}（{roleLabels[role || ''] || role}）</Title>
      <Paragraph type="secondary">
        采购-入库-付款全链路管理系统。使用左侧导航进入各功能模块。
      </Paragraph>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="供应商" prefix={<TeamOutlined />} value="-" />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="物料 SKU" prefix={<InboxOutlined />} value="-" />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="采购订单" prefix={<FileTextOutlined />} value="-" />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="应付账款" prefix={<DollarOutlined />} value="-" />
          </Card>
        </Col>
      </Row>

      <Card style={{ marginTop: 24 }}>
        <Title level={5}>业务流程图</Title>
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 16,
          alignItems: 'center',
          padding: '16px 0',
        }}>
          {[
            { label: '供应商', desc: '管理供应商主数据' },
            { label: '物料', desc: '管理物料与 SKU' },
            { label: '采购订单', desc: 'DRAFT → APPROVED → SHIPPED → RECEIVED → SETTLED' },
            { label: '入库收货', desc: '物料到货入库验收' },
            { label: '应付账款', desc: 'PENDING → APPROVED → SETTLED' },
          ].map((step, i) => (
            <div key={step.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Card size="small" style={{ minWidth: 120, textAlign: 'center' }}>
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
