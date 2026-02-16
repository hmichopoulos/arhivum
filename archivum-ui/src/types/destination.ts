/**
 * Destination type definition.
 */

export enum DestinationType {
  NAS = 'NAS',
  WAREHOUSE = 'WAREHOUSE',
  STAGING = 'STAGING'
}

export type Destination = {
  id: string;
  name: string;
  destinationType: DestinationType;
  mountedPath: string;
  physicalIdentifier?: string;
  description?: string;
  isActive: boolean;
  totalCapacityBytes?: number;
  priority: number;
  createdAt?: string;
  updatedAt?: string;

  // Real-time filesystem information (not stored in DB)
  availableSpaceBytes?: number;
  usedSpaceBytes?: number;
  usagePercentage?: number;
  isAccessible?: boolean;
  errorMessage?: string;
};

export type CreateDestinationRequest = {
  name: string;
  destinationType: DestinationType;
  mountedPath: string;
  physicalIdentifier?: string;
  description?: string;
  isActive?: boolean;
  totalCapacityBytes?: number;
  priority?: number;
};

export type UpdateDestinationRequest = {
  name?: string;
  destinationType?: DestinationType;
  mountedPath?: string;
  physicalIdentifier?: string;
  description?: string;
  isActive?: boolean;
  totalCapacityBytes?: number;
  priority?: number;
};
