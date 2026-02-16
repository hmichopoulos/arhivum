/**
 * Form component for creating and editing destinations.
 */

import { useState, FormEvent } from 'react';
import { DestinationType } from '../types/destination';
import type { CreateDestinationRequest } from '../types/destination';

type DestinationFormProps = {
  onSubmit: (destination: CreateDestinationRequest) => void;
  onCancel: () => void;
  isSubmitting?: boolean;
};

export function DestinationForm({ onSubmit, onCancel, isSubmitting }: DestinationFormProps) {
  const [formData, setFormData] = useState<CreateDestinationRequest>({
    name: '',
    destinationType: DestinationType.NAS,
    mountedPath: '',
    physicalIdentifier: '',
    description: '',
    isActive: true,
    priority: 0
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit(formData);
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          {/* Header */}
          <div className="border-b border-gray-200 px-6 py-4">
            <h2 className="text-2xl font-bold text-gray-900">Create New Destination</h2>
            <p className="text-sm text-gray-600 mt-1">
              Add a migration target with real-time disk space monitoring
            </p>
          </div>

          {/* Form Fields */}
          <div className="px-6 py-4 space-y-4">
            {/* Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., Main NAS, Backup Warehouse"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Destination Type */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Type <span className="text-red-500">*</span>
              </label>
              <select
                required
                value={formData.destinationType}
                onChange={(e) =>
                  setFormData({ ...formData, destinationType: e.target.value as DestinationType })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value={DestinationType.NAS}>NAS - Network Attached Storage</option>
                <option value={DestinationType.WAREHOUSE}>Warehouse - Long-term Storage</option>
                <option value={DestinationType.STAGING}>Staging - Temporary Storage</option>
              </select>
            </div>

            {/* Mounted Path */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Mounted Path <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                required
                value={formData.mountedPath}
                onChange={(e) => setFormData({ ...formData, mountedPath: e.target.value })}
                placeholder="/mnt/nas/archive or /volume1/Archivum"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Path must exist and be writable. Server will validate on creation.
              </p>
            </div>

            {/* Physical Identifier */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Physical Hardware ID
              </label>
              <input
                type="text"
                value={formData.physicalIdentifier || ''}
                onChange={(e) =>
                  setFormData({ ...formData, physicalIdentifier: e.target.value || undefined })
                }
                placeholder="e.g., SYN-NAS-001, WD-12345678"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Optional: Serial number or identifier for the physical hardware
              </p>
            </div>

            {/* Priority */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
              <input
                type="number"
                value={formData.priority}
                onChange={(e) =>
                  setFormData({ ...formData, priority: parseInt(e.target.value) || 0 })
                }
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Higher priority destinations are preferred for migration (default: 0)
              </p>
            </div>

            {/* Description */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <textarea
                value={formData.description || ''}
                onChange={(e) =>
                  setFormData({ ...formData, description: e.target.value || undefined })
                }
                placeholder="Optional notes about this destination..."
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Active Status */}
            <div className="flex items-center gap-3">
              <input
                type="checkbox"
                id="isActive"
                checked={formData.isActive}
                onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <label htmlFor="isActive" className="text-sm font-medium text-gray-700">
                Active (available for migration)
              </label>
            </div>
          </div>

          {/* Footer */}
          <div className="border-t border-gray-200 px-6 py-4 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Creating...' : 'Create Destination'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
