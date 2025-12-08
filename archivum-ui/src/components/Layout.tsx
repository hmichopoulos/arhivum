/**
 * Main layout component with navigation.
 */

import { Link, useLocation } from 'react-router-dom';

type LayoutProps = {
  children: React.ReactNode;
};

export function Layout({ children }: LayoutProps) {
  const location = useLocation();

  const navItems = [
    { path: '/sources', label: 'Sources', icon: '💾' },
    { path: '/code-projects', label: 'Code Projects', icon: '💻' }
  ];

  const isActive = (path: string) => {
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex items-center justify-between h-16">
            {/* Logo */}
            <Link to="/" className="flex items-center gap-3">
              <span className="text-2xl">📦</span>
              <span className="text-xl font-bold text-gray-900">Archivum</span>
            </Link>

            {/* Navigation */}
            <nav className="flex items-center gap-1">
              {navItems.map((item) => (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`
                    flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium
                    transition-colors
                    ${
                      isActive(item.path)
                        ? 'bg-blue-50 text-blue-700'
                        : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                    }
                  `}
                >
                  <span>{item.icon}</span>
                  <span>{item.label}</span>
                </Link>
              ))}
            </nav>

            {/* User Menu (Placeholder) */}
            <div className="flex items-center gap-3">
              <button className="text-gray-600 hover:text-gray-900">
                <span className="text-xl">⚙️</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main>{children}</main>

      {/* Footer */}
      <footer className="bg-white border-t border-gray-200 mt-auto">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="text-center text-sm text-gray-500">
            © 2025 Archivum - Personal File Organization System
          </div>
        </div>
      </footer>
    </div>
  );
}
