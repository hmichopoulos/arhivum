/**
 * Zone selector component for classifying files and folders.
 */

import { useState } from 'react';
import { Zone, ZONE_LABELS, ZONE_COLORS } from '../types/zone';
import { updateFileZone } from '../api/files';

type ZoneSelectorProps = {
  fileId: string;
  currentZone: Zone;
  onZoneChange?: (newZone: Zone) => void;
};

export function ZoneSelector({ fileId, currentZone, onZoneChange }: ZoneSelectorProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [selectedZone, setSelectedZone] = useState<Zone>(currentZone);
  const [isLoading, setIsLoading] = useState(false);
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
    setIsLoading(true);

    try {
      await updateFileZone(fileId, newZone);
      setIsEditing(false);
      onZoneChange?.(newZone);
    } catch (err) {
      console.error('Failed to update zone:', err);
      setError('Failed to update zone');
      setSelectedZone(currentZone); // Revert to original zone
    } finally {
      setIsLoading(false);
    }
  };

  const handleBlur = () => {
    if (!isLoading) {
      setIsEditing(false);
      setSelectedZone(currentZone);
      setError(null);
    }
  };

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
      `}
      title="Click to change zone classification"
    >
      {ZONE_LABELS[currentZone]}
    </button>
  );
}
