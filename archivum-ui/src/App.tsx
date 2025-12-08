/**
 * Main application component with routing.
 */

import { Routes, Route, Navigate, useParams } from 'react-router-dom';
import { Layout } from './components/Layout';
import { SourcesListPage } from './pages/SourcesListPage';
import { SourceDetailsPage } from './pages/SourceDetailsPage';
import { CodeProjectsPage } from './pages/CodeProjectsPage';

function App() {
  return (
    <Layout>
      <Routes>
        {/* Default route - redirect to sources */}
        <Route path="/" element={<Navigate to="/sources" replace />} />

        {/* Sources routes */}
        <Route path="/sources" element={<SourcesListPage />} />
        <Route
          path="/sources/:sourceId"
          element={<SourceDetailsPageWrapper />}
        />

        {/* Code projects route */}
        <Route path="/code-projects" element={<CodeProjectsPage />} />

        {/* 404 fallback */}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Layout>
  );
}

// Wrapper to extract route params and pass to SourceDetailsPage
function SourceDetailsPageWrapper() {
  const { sourceId } = useParams<{ sourceId: string }>();
  return <SourceDetailsPage sourceId={sourceId || ''} />;
}

// 404 Not Found page
function NotFound() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-6xl font-bold text-gray-900 mb-4">404</h1>
        <p className="text-xl text-gray-600 mb-8">Page not found</p>
        <a
          href="/sources"
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
        >
          Go to Sources
        </a>
      </div>
    </div>
  );
}

export default App;
