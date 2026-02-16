/**
 * Zone selector component for classifying files and folders.
 */

import { useState } from 'react';
import { Zone, ZONE_LABELS, ZONE_COLORS } from '../types/zone';
import { updateFileZone } from '../api/files';
import { useUpdateFolderZone } from '../hooks/useSources';

type ZoneSelectorProps = {
  // For files
  fileId?: string;
  // For folders
  sourceId?: string;
  folderPath?: string;
  // Common props
  currentZone: Zone;
  isInherited?: boolean;
  onZoneChange?: (newZone: Zone) => void;
};

export function ZoneSelector({ fileId, sourceId, folderPath, currentZone, isInherited, onZoneChange }: ZoneSelectorProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [selectedZone, setSelectedZone] = useState<Zone>(currentZone);
  const updateFolderZoneMutation = useUpdateFolderZone();
  const [error, setError] = useState<string | null>(null);

  const handleZoneClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsEditing(true);
  };

  const handleZoneChange = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    e.stopPropagation();
    const newZone = e.target.value as Zone;
    setSelectedZone(newZone);
    setError(null);

    try {
      if (fileId) {
        await updateFileZone(fileId, newZone);
        setIsEditing(false);
        onZoneChange?.(newZone);
      } else if (sourceId && folderPath) {
        updateFolderZoneMutation.mutate(
          { sourceId, folderPath, zone: newZone },
          {
            onSuccess: () => {
              setIsEditing(false);
              onZoneChange?.(newZone);
            },
            onError: (err) => {
              console.error('Failed to update zone:', err);
              setError('Failed to update zone');
              setSelectedZone(currentZone); // Revert to original zone
            }
          }
        );
      }
    } catch (err) {
      console.error('Failed to update zone:', err);
      setError('Failed to update zone');
      setSelectedZone(currentZone); // Revert to original zone
    }
  };

  const handleBlur = () => {
    if (!updateFolderZoneMutation.isPending) {
      setIsEditing(false);
      setSelectedZone(currentZone);
      setError(null);
    }
  };

  const isLoading = updateFolderZoneMutation.isPending;

  if (isEditing) {
    return (
      <div className="flex flex-col gap-1" onClick={(e) => e.stopPropagation()}>
        <select
          value={selectedZone}
          onChange={handleZoneChange}
          onBlur={handleBlur}
          disabled={isLoading}
          autoFocus
          className={`
            text-xs px-2 py-0.5 rounded border border-gray-300
            focus:outline-none focus:ring-2 focus:ring-blue-500
            ${isLoading ? 'opacity-50 cursor-wait' : 'cursor-pointer'}
          `}
        >
          {Object.entries(Zone).map(([, value]) => (
            <option key={value} value={value}>
              {ZONE_LABELS[value]}
            </option>
          ))}
        </select>
        {error && (
          <span className="text-xs text-red-600">{error}</span>
        )}
      </div>
    );
  }

  return (
    <button
      onClick={handleZoneClick}
      className={`
        text-xs px-2 py-0.5 rounded font-medium cursor-pointer
        hover:opacity-80 transition-opacity
        ${ZONE_COLORS[currentZone]}
        ${isInherited ? 'italic opacity-90' : ''}
      `}
      title={isInherited ? "Zone inherited from parent folder - click to override" : "Click to change zone classification"}
    >
      {ZONE_LABELS[currentZone]}{isInherited && ' (Inherited)'}
    </button>
  );
}
