import { Navigate, Route, Routes } from 'react-router-dom';
import { isAdmin, useAuth } from './auth';
import Layout from './components/Layout';
import ChatPage from './pages/ChatPage';
import DocumentsPage from './pages/DocumentsPage';
import EvaluationPage from './pages/EvaluationPage';
import InsightsPage from './pages/InsightsPage';
import LoginPage from './pages/LoginPage';

export default function App() {
  const { user, loading } = useAuth();

  if (loading) return <div className="splash">Loading…</div>;
  if (!user) return <LoginPage />;

  const admin = isAdmin(user);
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        {admin && <Route path="/insights" element={<InsightsPage />} />}
        {admin && <Route path="/evaluation" element={<EvaluationPage />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
