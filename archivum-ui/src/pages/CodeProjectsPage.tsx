/**
 * Main page component for code projects with tabs.
 */

import { useState } from 'react';
import { CodeProjectList } from '../components/CodeProjectList';
import { DuplicatesView } from '../components/DuplicatesView';
import { CodeProjectStats } from '../components/CodeProjectStats';

type Tab = 'projects' | 'duplicates' | 'stats';

export function CodeProjectsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('projects');

  const tabs: { id: Tab; label: string }[] = [
    { id: 'projects', label: 'All Projects' },
    { id: 'duplicates', label: 'Duplicates' },
    { id: 'stats', label: 'Statistics' }
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Tabs */}
        <div className="border-b border-gray-200 mb-6">
          <nav className="flex space-x-8">
            {tabs.map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`
                  py-4 px-1 border-b-2 font-medium text-sm transition-colors
                  ${activeTab === tab.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }
                `}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>

        {/* Tab Content */}
        <div>
          {activeTab === 'projects' && <CodeProjectList />}
          {activeTab === 'duplicates' && <DuplicatesView />}
          {activeTab === 'stats' && <CodeProjectStats />}
        </div>
      </div>
    </div>
  );
}
