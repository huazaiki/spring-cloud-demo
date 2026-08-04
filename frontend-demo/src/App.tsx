import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './context/AuthContext';
import MainLayout from './layouts/MainLayout';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import SupplierList from './pages/SupplierList';
import ItemList from './pages/ItemList';
import OrderList from './pages/OrderList';
import PaymentList from './pages/PaymentList';
import RequisitionList from './pages/RequisitionList';
import ApprovalTasks from './pages/ApprovalTasks';
import Receives from './pages/Receives';
import Finance from './pages/Finance';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

function GuestRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return !isAuthenticated ? <>{children}</> : <Navigate to="/" replace />;
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff' } }}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<GuestRoute><Login /></GuestRoute>} />
            <Route path="/register" element={<GuestRoute><Register /></GuestRoute>} />
            <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
              <Route index element={<Dashboard />} />
              <Route path="suppliers" element={<SupplierList />} />
              <Route path="items" element={<ItemList />} />
              <Route path="orders" element={<OrderList />} />
              <Route path="payments" element={<PaymentList />} />
              <Route path="requisitions" element={<RequisitionList />} />
              <Route path="approval-tasks" element={<ApprovalTasks />} />
              <Route path="receives" element={<Receives />} />
              <Route path="finance" element={<Finance />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  );
}