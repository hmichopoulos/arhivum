import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ProjectDetailsModal } from './ProjectDetailsModal';
import { ProjectType } from '../types/codeProject';
import type { CodeProject } from '../types/codeProject';

const mockGetIcon = (type: ProjectType) => {
  const icons: Record<ProjectType, string> = {
    [ProjectType.MAVEN]: '📦',
    [ProjectType.GRADLE]: '🐘',
    [ProjectType.NPM]: '📦',
    [ProjectType.PYTHON]: '🐍',
    [ProjectType.GO]: '🔵',
    [ProjectType.RUST]: '🦀',
    [ProjectType.GIT]: '📂',
    [ProjectType.GENERIC]: '📁',
  };
  return icons[type] || '📁';
};

const mockGetColor = (type: ProjectType) => {
  const colors: Record<ProjectType, string> = {
    [ProjectType.MAVEN]: 'bg-orange-100 text-orange-800',
    [ProjectType.GRADLE]: 'bg-green-100 text-green-800',
    [ProjectType.NPM]: 'bg-red-100 text-red-800',
    [ProjectType.PYTHON]: 'bg-blue-100 text-blue-800',
    [ProjectType.GO]: 'bg-cyan-100 text-cyan-800',
    [ProjectType.RUST]: 'bg-amber-100 text-amber-800',
    [ProjectType.GIT]: 'bg-gray-100 text-gray-800',
    [ProjectType.GENERIC]: 'bg-purple-100 text-purple-800',
  };
  return colors[type] || 'bg-gray-100 text-gray-800';
};

const createMockProject = (overrides?: Partial<CodeProject>): CodeProject => ({
  id: 'test-project-1',
  sourceId: 'source-1',
  rootPath: '/path/to/project',
  identity: {
    type: ProjectType.NPM,
    name: 'test-project',
    identifier: 'npm:test-project:1.0.0',
    version: '1.0.0',
    groupId: undefined,
  },
  contentHash: 'abc123def456',
  sourceFileCount: 50,
  totalFileCount: 100,
  totalSizeBytes: 1024 * 1024, // 1 MB
  scannedAt: '2025-01-15T10:30:00Z',
  ...overrides,
});

describe('ProjectDetailsModal', () => {
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    document.body.style.overflow = '';
  });

  describe('visibility', () => {
    it('renders when isOpen is true and project is provided', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      // Project name appears in header and identity section
      expect(screen.getAllByText('test-project').length).toBeGreaterThan(0);
      expect(screen.getByText('Project Identity')).toBeInTheDocument();
    });

    it('does not render when isOpen is false', () => {
      render(
        <ProjectDetailsModal
          isOpen={false}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.queryByText('test-project')).not.toBeInTheDocument();
    });

    it('does not render when project is null', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={null}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.queryByText('Project Identity')).not.toBeInTheDocument();
    });
  });

  describe('closing behavior', () => {
    it('calls onClose when ESC key is pressed', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      fireEvent.keyDown(document, { key: 'Escape' });
      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when backdrop is clicked', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      const backdrop = document.querySelector('.bg-black.bg-opacity-50');
      expect(backdrop).toBeTruthy();
      fireEvent.click(backdrop!);
      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when close button is clicked', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      const closeButtons = screen.getAllByRole('button');
      // First button is the X in header
      fireEvent.click(closeButtons[0]);
      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when footer Close button is clicked', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      const closeButton = screen.getByText('Close');
      fireEvent.click(closeButton);
      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('does not close when clicking inside the modal', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      // Click on the Project Identity section heading (unique element)
      const sectionHeading = screen.getByText('Project Identity');
      fireEvent.click(sectionHeading);
      expect(mockOnClose).not.toHaveBeenCalled();
    });
  });

  describe('body overflow management', () => {
    it('sets body overflow to hidden when modal opens', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(document.body.style.overflow).toBe('hidden');
    });

    it('restores body overflow when modal closes', () => {
      const { rerender } = render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      rerender(
        <ProjectDetailsModal
          isOpen={false}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(document.body.style.overflow).toBe('unset');
    });
  });

  describe('project data rendering', () => {
    it('displays project name and type', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      // Project name appears in header (h2) and identity section
      const projectNames = screen.getAllByText('test-project');
      expect(projectNames.length).toBe(2);
      expect(screen.getAllByText('NPM').length).toBeGreaterThan(0);
    });

    it('displays project path', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('/path/to/project')).toBeInTheDocument();
    });

    it('displays statistics', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('50')).toBeInTheDocument(); // sourceFileCount
      expect(screen.getByText('100')).toBeInTheDocument(); // totalFileCount
      expect(screen.getByText('1.0 MB')).toBeInTheDocument(); // totalSizeBytes
    });

    it('displays content hash', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('abc123def456')).toBeInTheDocument();
    });
  });

  describe('conditional rendering', () => {
    it('displays version when provided', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('Version')).toBeInTheDocument();
      expect(screen.getByText('1.0.0')).toBeInTheDocument();
    });

    it('does not display version when not provided', () => {
      const project = createMockProject({
        identity: {
          type: ProjectType.NPM,
          name: 'test-project',
          identifier: 'npm:test-project',
          version: undefined,
        },
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.queryByText('Version')).not.toBeInTheDocument();
    });

    it('displays groupId when provided', () => {
      const project = createMockProject({
        identity: {
          type: ProjectType.MAVEN,
          name: 'test-project',
          identifier: 'com.example:test-project:1.0.0',
          version: '1.0.0',
          groupId: 'com.example',
        },
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('Group ID')).toBeInTheDocument();
      expect(screen.getByText('com.example')).toBeInTheDocument();
    });

    it('displays archive path when provided', () => {
      const project = createMockProject({
        archivePath: '/archive/projects/test-project',
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('Archive Path')).toBeInTheDocument();
      expect(screen.getByText('/archive/projects/test-project')).toBeInTheDocument();
    });

    it('displays Git info box for GIT projects', () => {
      const project = createMockProject({
        identity: {
          type: ProjectType.GIT,
          name: 'my-repo',
          identifier: 'git:my-repo',
        },
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('Git Repository')).toBeInTheDocument();
      expect(screen.getByText(/tracked by Git/)).toBeInTheDocument();
    });

    it('does not display Git info box for non-GIT projects', () => {
      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={createMockProject()}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.queryByText('Git Repository')).not.toBeInTheDocument();
    });
  });

  describe('edge cases', () => {
    it('handles zero file counts', () => {
      const project = createMockProject({
        sourceFileCount: 0,
        totalFileCount: 0,
        totalSizeBytes: 0,
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('0 B')).toBeInTheDocument();
    });

    it('handles large numbers', () => {
      const project = createMockProject({
        sourceFileCount: 1000000,
        totalFileCount: 2000000,
        totalSizeBytes: 5 * 1024 * 1024 * 1024, // 5 GB
      });

      render(
        <ProjectDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          project={project}
          getIcon={mockGetIcon}
          getColor={mockGetColor}
        />
      );

      expect(screen.getByText('1,000,000')).toBeInTheDocument();
      expect(screen.getByText('2,000,000')).toBeInTheDocument();
      expect(screen.getByText('5.0 GB')).toBeInTheDocument();
    });
  });
});
