# Migration System - Implementation Plan

**Status**: Ready to implement
**Last Updated**: 2025-12-29

---

## Overview

Implementing file migration system based on `docs/MIGRATION_WORKFLOW.md` design.

**Goal**: Enable users to migrate 80TB across 20 external disks to organized NAS and warehouse storage.

---

## Implementation Strategy

**Approach**: Incremental milestones, each delivering working functionality

**Priorities**:
1. **Disk tracking** - Critical for realistic workflow
2. **Basic NAS migration** - MVP to get value quickly
3. **Destination scanning** - Handle already-organized files
4. **Warehouse migration** - For cold storage
5. **Advanced features** - Git archive, bi-directional, etc.

---

## Milestone 1: Foundation & Disk Tracking (CRITICAL)

**Goal**: Support offline workflow (scan → unplug → classify → plug back in → migrate)

**Duration**: 2-3 days

### Database Changes

**1.1 Update Source table**
```sql
-- File: V010__add_disk_tracking.sql

ALTER TABLE source ADD COLUMN serial_number VARCHAR(255);
ALTER TABLE source ADD COLUMN state VARCHAR(20) DEFAULT 'OFFLINE';
ALTER TABLE source ADD COLUMN mount_point VARCHAR(500);
ALTER TABLE source ADD COLUMN last_seen TIMESTAMP;

CREATE INDEX idx_source_serial ON source(serial_number);

COMMENT ON COLUMN source.serial_number IS 'Hardware serial number from disk';
COMMENT ON COLUMN source.state IS 'ONLINE (plugged in) or OFFLINE (unplugged)';
COMMENT ON COLUMN source.mount_point IS 'Current mount point (e.g., /mnt/disk1) or null if offline';
COMMENT ON COLUMN source.last_seen IS 'Last time disk was detected';
```

**1.2 Add File State tracking**
```sql
-- File: V011__add_file_states.sql

CREATE TYPE file_state AS ENUM (
  'DISCOVERED',   -- Found during scan
  'PINNED',       -- Already in final location (don't migrate)
  'STAGED',       -- Ready to migrate
  'MIGRATING',    -- Currently being migrated
  'MIGRATED',     -- Successfully migrated
  'WAREHOUSED',   -- On warehouse disk
  'DELETED'       -- Marked for deletion
);

ALTER TABLE scanned_file ADD COLUMN state file_state DEFAULT 'DISCOVERED';
CREATE INDEX idx_scanned_file_state ON scanned_file(state);

COMMENT ON COLUMN scanned_file.state IS 'Current state in migration workflow';
```

**1.3 Add Source Type**
```sql
-- File: V012__add_source_types.sql

CREATE TYPE source_type AS ENUM (
  'DISCOVERY',    -- Files to be migrated
  'DESTINATION',  -- Already organized (pin files)
  'WAREHOUSE'     -- Warehouse disk (files stay here)
);

ALTER TABLE source ADD COLUMN source_type source_type DEFAULT 'DISCOVERY';

COMMENT ON COLUMN source.source_type IS 'DISCOVERY: scan to migrate, DESTINATION: already organized, WAREHOUSE: catalog only';
```

### Scanner Updates

**1.4 Read disk serial number**

File: `archivum-scanner/src/main/java/tech/zaisys/archivum/scanner/util/DiskInfoUtil.java`

```java
public class DiskInfoUtil {

    /**
     * Read disk serial number from device.
     * @param mountPoint Mount point (e.g., /mnt/disk1)
     * @return Serial number or null if not readable
     */
    public static String getDiskSerial(Path mountPoint) {
        try {
            // Linux
            if (isLinux()) {
                return getDiskSerialLinux(mountPoint);
            }
            // macOS
            else if (isMac()) {
                return getDiskSerialMac(mountPoint);
            }
            return null;
        } catch (Exception e) {
            log.warn("Could not read disk serial for {}: {}", mountPoint, e.getMessage());
            return null;
        }
    }

    private static String getDiskSerialLinux(Path mountPoint) throws IOException {
        // Find device from mount point
        String device = findDevice(mountPoint);
        if (device == null) return null;

        // Read serial using lsblk
        Process process = new ProcessBuilder(
            "lsblk", "-no", "SERIAL", device
        ).start();

        String serial = new String(process.getInputStream().readAllBytes()).trim();
        return serial.isEmpty() ? null : serial;
    }

    private static String getDiskSerialMac(Path mountPoint) throws IOException {
        // Find disk identifier
        String disk = findDiskMac(mountPoint);
        if (disk == null) return null;

        // Read serial using diskutil
        Process process = new ProcessBuilder(
            "diskutil", "info", disk
        ).start();

        String output = new String(process.getInputStream().readAllBytes());

        // Parse: "Device / Media Name:  ... Serial Number: XYZ..."
        Pattern pattern = Pattern.compile("(?i)Serial Number:\\s*(.+)");
        Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
```

**1.5 Update ScanCommand to store serial**

File: `archivum-scanner/src/main/java/tech/zaisys/archivum/scanner/command/ScanCommand.java`

```java
@Override
public Integer call() throws Exception {
    // ... existing scan logic ...

    // Read disk serial
    String diskSerial = DiskInfoUtil.getDiskSerial(scanPath);
    log.info("Disk serial: {}", diskSerial != null ? diskSerial : "not readable");

    // Create source DTO
    SourceDto sourceDto = SourceDto.builder()
        .name(sourceName)
        .serialNumber(diskSerial)  // NEW: Add serial number
        .sourceType(sourceType != null ? sourceType : SourceType.DISCOVERY)
        .physicalId(physicalId)
        .scannedAt(Instant.now())
        .build();

    // ... rest of scan logic ...
}
```

**1.6 Add --source-type CLI option**

File: `archivum-scanner/src/main/java/tech/zaisys/archivum/scanner/command/ScanCommand.java`

```java
@Option(
    names = {"--source-type"},
    description = "Source type: DISCOVERY (default), DESTINATION (already organized), WAREHOUSE (catalog only)"
)
private SourceType sourceType;
```

### Server Updates

**1.7 Disk Detection Service**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/service/DiskDetectionService.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class DiskDetectionService {

    private final SourceRepository sourceRepository;

    /**
     * Periodically check for connected disks and update states.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedDelay = 10000)
    public void detectDisks() {
        List<DiskInfo> connectedDisks = listConnectedDisks();

        for (DiskInfo disk : connectedDisks) {
            if (disk.getSerial() == null) continue;

            Optional<Source> source = sourceRepository.findBySerialNumber(disk.getSerial());

            if (source.isPresent()) {
                Source s = source.get();

                // Was offline, now online
                if (s.getState() == DiskState.OFFLINE) {
                    log.info("Disk detected: {} ({})", s.getName(), disk.getSerial());

                    s.setState(DiskState.ONLINE);
                    s.setMountPoint(disk.getMountPoint());
                    s.setLastSeen(Instant.now());
                    sourceRepository.save(s);

                    // TODO: Send notification via WebSocket
                }
                // Update last seen even if already online
                else if (s.getState() == DiskState.ONLINE) {
                    s.setLastSeen(Instant.now());
                    sourceRepository.save(s);
                }
            }
        }

        // Mark disks as offline if not detected
        markOfflineDisks(connectedDisks);
    }

    /**
     * List all currently connected disks with serial numbers.
     */
    public List<DiskInfo> listConnectedDisks() {
        List<DiskInfo> disks = new ArrayList<>();

        try {
            if (isLinux()) {
                disks = listDisksLinux();
            } else if (isMac()) {
                disks = listDisksMac();
            }
        } catch (Exception e) {
            log.error("Error detecting disks", e);
        }

        return disks;
    }

    private List<DiskInfo> listDisksLinux() throws IOException {
        // Execute: lsblk -ndo NAME,SERIAL,MOUNTPOINT
        Process process = new ProcessBuilder(
            "lsblk", "-ndo", "NAME,SERIAL,MOUNTPOINT"
        ).start();

        String output = new String(process.getInputStream().readAllBytes());

        List<DiskInfo> disks = new ArrayList<>();
        for (String line : output.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 3) {
                String name = parts[0];
                String serial = parts[1];
                String mountPoint = parts[2];

                if (!serial.isEmpty() && !mountPoint.isEmpty()) {
                    disks.add(new DiskInfo(name, serial, mountPoint));
                }
            }
        }

        return disks;
    }

    // Similar for macOS...
}
```

**1.8 REST API for disk status**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/controller/DiskController.java`

```java
@RestController
@RequestMapping("/api/disks")
@RequiredArgsConstructor
public class DiskController {

    private final DiskDetectionService diskDetectionService;
    private final SourceRepository sourceRepository;

    /**
     * Get status of all disks (online/offline).
     */
    @GetMapping("/status")
    public List<DiskStatusDto> getStatus() {
        return sourceRepository.findAll().stream()
            .map(this::toDiskStatusDto)
            .collect(Collectors.toList());
    }

    /**
     * Manually trigger disk detection.
     */
    @PostMapping("/detect")
    public void detect() {
        diskDetectionService.detectDisks();
    }

    private DiskStatusDto toDiskStatusDto(Source source) {
        return DiskStatusDto.builder()
            .id(source.getId())
            .name(source.getName())
            .serialNumber(source.getSerialNumber())
            .state(source.getState())
            .mountPoint(source.getMountPoint())
            .lastSeen(source.getLastSeen())
            .fileCount(/* count from scanned_file */)
            .build();
    }
}
```

### UI Updates

**1.9 Disk Status Dashboard**

File: `archivum-ui/src/pages/DiskStatus.tsx`

```tsx
export function DiskStatusPage() {
  const { data: disks, isLoading } = useQuery({
    queryKey: ['disk-status'],
    queryFn: getDiskStatus,
    refetchInterval: 5000  // Refresh every 5 seconds
  });

  const detectMutation = useMutation({
    mutationFn: triggerDiskDetection,
    onSuccess: () => {
      queryClient.invalidateQueries(['disk-status']);
    }
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Disk Status</h1>
        <Button onClick={() => detectMutation.mutate()}>
          Refresh - Check Disks
        </Button>
      </div>

      <div className="space-y-4">
        {disks?.map(disk => (
          <DiskCard key={disk.id} disk={disk} />
        ))}
      </div>
    </div>
  );
}

function DiskCard({ disk }: { disk: DiskStatus }) {
  const isOnline = disk.state === 'ONLINE';

  return (
    <Card className={isOnline ? 'border-green-500' : 'border-gray-300'}>
      <CardHeader>
        <div className="flex items-center gap-2">
          {isOnline ? (
            <CheckCircle className="text-green-500" />
          ) : (
            <AlertCircle className="text-yellow-500" />
          )}
          <h3 className="font-semibold">{disk.name}</h3>
          <Badge variant={isOnline ? 'success' : 'warning'}>
            {disk.state}
          </Badge>
        </div>
      </CardHeader>

      <CardContent>
        <dl className="space-y-2 text-sm">
          <div>
            <dt className="text-gray-500">Serial</dt>
            <dd className="font-mono">{disk.serialNumber || 'Unknown'}</dd>
          </div>
          {disk.mountPoint && (
            <div>
              <dt className="text-gray-500">Mount Point</dt>
              <dd className="font-mono">{disk.mountPoint}</dd>
            </div>
          )}
          <div>
            <dt className="text-gray-500">Last Seen</dt>
            <dd>{formatDistanceToNow(disk.lastSeen)} ago</dd>
          </div>
          <div>
            <dt className="text-gray-500">Files</dt>
            <dd>{disk.fileCount.toLocaleString()}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}
```

### Testing

**1.10 Manual Testing**

1. **Test disk serial reading**:
   ```bash
   # On Linux
   lsblk -o NAME,SERIAL,MOUNTPOINT

   # Scanner should read serial correctly
   ./archivum-scanner scan --name "Test Disk" /mnt/usb
   # Check logs for: "Disk serial: XYZ123"
   ```

2. **Test disk detection**:
   ```bash
   # Plug disk, check server logs
   # Should see: "Disk detected: Test Disk (XYZ123)"

   # Check UI: Disk should show as ONLINE

   # Unplug disk
   # After 10 seconds, should show as OFFLINE
   ```

3. **Test source types**:
   ```bash
   # Scan as DESTINATION
   ./archivum-scanner scan --source-type DESTINATION --name "NAS Archive" /mnt/nas

   # Check database: source_type should be DESTINATION
   ```

### Acceptance Criteria

- ✅ Scanner reads disk serial number (Linux + macOS)
- ✅ Server tracks disk state (ONLINE/OFFLINE)
- ✅ Server detects when disk plugged in (within 10 seconds)
- ✅ UI shows disk status dashboard
- ✅ Can manually trigger disk detection
- ✅ Source types supported (DISCOVERY, DESTINATION, WAREHOUSE)

---

## Milestone 2: Basic Migration (NAS Only)

**Goal**: Execute simple migrations to NAS

**Duration**: 3-4 days

### Database Changes

**2.1 Migration Plan table**
```sql
-- File: V013__add_migration_tables.sql

CREATE TABLE migration_plan (
  id UUID PRIMARY KEY,
  name VARCHAR(255),
  created_at TIMESTAMP DEFAULT NOW(),
  created_by VARCHAR(100),
  status VARCHAR(50),  -- DRAFT, READY, EXECUTING, COMPLETE, FAILED
  total_files INTEGER,
  total_size_bytes BIGINT,
  destination_type VARCHAR(50)  -- NAS, WAREHOUSE, GIT
);

CREATE TABLE migration_task (
  id UUID PRIMARY KEY,
  plan_id UUID REFERENCES migration_plan(id) ON DELETE CASCADE,
  source_id UUID REFERENCES source(id),
  file_id UUID REFERENCES scanned_file(id),
  destination_path VARCHAR(1000),
  status VARCHAR(50),  -- PENDING, WAITING_FOR_DISK, IN_PROGRESS, COMPLETE, FAILED
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  error_message TEXT
);

CREATE INDEX idx_migration_task_plan ON migration_task(plan_id);
CREATE INDEX idx_migration_task_status ON migration_task(status);
```

**2.2 Migration history table**
```sql
CREATE TABLE file_migration_history (
  id UUID PRIMARY KEY,
  file_id UUID REFERENCES scanned_file(id),
  from_location VARCHAR(500),
  to_location VARCHAR(500),
  migration_type VARCHAR(50),  -- NAS_COPY, WAREHOUSE_COPY, NAS_TO_WAREHOUSE, etc.
  migrated_at TIMESTAMP DEFAULT NOW(),
  migrated_by VARCHAR(100)
);

CREATE INDEX idx_migration_history_file ON file_migration_history(file_id);
```

### Server Services

**2.3 Migration Planning Service**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/service/MigrationPlanningService.java`

```java
@Service
@RequiredArgsConstructor
public class MigrationPlanningService {

    private final ScannedFileRepository fileRepository;
    private final MigrationPlanRepository planRepository;

    /**
     * Create migration plan for selected files.
     */
    public MigrationPlanDto createPlan(CreateMigrationPlanRequest request) {
        // Fetch files to migrate
        List<ScannedFile> files = fileRepository.findAllById(request.getFileIds());

        // Calculate statistics
        long totalSize = files.stream()
            .mapToLong(ScannedFile::getSizeBytes)
            .sum();

        // Create plan
        MigrationPlan plan = MigrationPlan.builder()
            .id(UUID.randomUUID())
            .name(request.getName())
            .status(MigrationStatus.DRAFT)
            .totalFiles(files.size())
            .totalSizeBytes(totalSize)
            .destinationType(request.getDestinationType())
            .createdAt(Instant.now())
            .build();

        plan = planRepository.save(plan);

        // Create tasks for each file
        List<MigrationTask> tasks = files.stream()
            .map(file -> createTask(plan, file, request))
            .collect(Collectors.toList());

        taskRepository.saveAll(tasks);

        return toPlanDto(plan, tasks);
    }

    private MigrationTask createTask(MigrationPlan plan, ScannedFile file, CreateMigrationPlanRequest request) {
        // Determine destination path based on file zone and organization rules
        String destinationPath = determineDestinationPath(file, request);

        return MigrationTask.builder()
            .id(UUID.randomUUID())
            .planId(plan.getId())
            .sourceId(file.getSourceId())
            .fileId(file.getId())
            .destinationPath(destinationPath)
            .status(TaskStatus.PENDING)
            .build();
    }

    private String determineDestinationPath(ScannedFile file, CreateMigrationPlanRequest request) {
        // TODO: Implement organization rules
        // For now, simple path based on zone

        String basePath = request.getBasePath();  // e.g., /mnt/nas/Archive

        Zone zone = file.getZone();
        String relativePath = file.getPath();

        // Example: /mnt/nas/Archive/Private/Documents/2024/...
        return Paths.get(basePath, zone.name(), relativePath).toString();
    }
}
```

**2.4 Migration Execution Service**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/service/MigrationExecutionService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationExecutionService {

    private final MigrationPlanRepository planRepository;
    private final MigrationTaskRepository taskRepository;
    private final SourceRepository sourceRepository;
    private final ScannedFileRepository fileRepository;

    /**
     * Execute migration plan.
     */
    public void executePlan(UUID planId) {
        MigrationPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new NotFoundException("Plan not found"));

        // Check if all required disks are online
        checkRequiredDisks(plan);

        // Update plan status
        plan.setStatus(MigrationStatus.EXECUTING);
        planRepository.save(plan);

        // Execute tasks
        List<MigrationTask> tasks = taskRepository.findByPlanId(planId);

        for (MigrationTask task : tasks) {
            try {
                executeTask(task);
            } catch (Exception e) {
                log.error("Task failed: {}", task.getId(), e);
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                taskRepository.save(task);
            }
        }

        // Update plan status
        plan.setStatus(MigrationStatus.COMPLETE);
        planRepository.save(plan);
    }

    private void executeTask(MigrationTask task) throws IOException {
        log.info("Executing task: {} → {}", task.getFileId(), task.getDestinationPath());

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(Instant.now());
        taskRepository.save(task);

        // Get source file info
        ScannedFile file = fileRepository.findById(task.getFileId())
            .orElseThrow(() -> new NotFoundException("File not found"));

        // Get source disk mount point
        Source source = sourceRepository.findById(task.getSourceId())
            .orElseThrow(() -> new NotFoundException("Source not found"));

        if (source.getState() != DiskState.ONLINE) {
            throw new IllegalStateException("Source disk offline: " + source.getName());
        }

        // Build full source path
        Path sourcePath = Paths.get(source.getMountPoint(), file.getPath());
        Path destPath = Paths.get(task.getDestinationPath());

        // Create destination directory
        Files.createDirectories(destPath.getParent());

        // Copy file
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

        // Verify checksum
        String copiedHash = computeHash(destPath);
        if (!copiedHash.equals(file.getContentHash())) {
            throw new IOException("Checksum mismatch after copy");
        }

        // Update file state
        file.setState(FileState.MIGRATED);
        file.setMigratedPath(destPath.toString());
        fileRepository.save(file);

        // Record history
        recordMigrationHistory(file, sourcePath.toString(), destPath.toString());

        // Update task
        task.setStatus(TaskStatus.COMPLETE);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        log.info("Task complete: {}", task.getId());
    }

    private void checkRequiredDisks(MigrationPlan plan) {
        List<MigrationTask> tasks = taskRepository.findByPlanId(plan.getId());

        Set<UUID> requiredSourceIds = tasks.stream()
            .map(MigrationTask::getSourceId)
            .collect(Collectors.toSet());

        List<Source> offlineDisks = sourceRepository.findAllById(requiredSourceIds).stream()
            .filter(s -> s.getState() == DiskState.OFFLINE)
            .collect(Collectors.toList());

        if (!offlineDisks.isEmpty()) {
            String diskNames = offlineDisks.stream()
                .map(Source::getName)
                .collect(Collectors.joining(", "));

            throw new IllegalStateException("Required disks offline: " + diskNames);
        }
    }
}
```

### REST API

**2.5 Migration Controller**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/controller/MigrationController.java`

```java
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationPlanningService planningService;
    private final MigrationExecutionService executionService;

    @PostMapping("/plans")
    public MigrationPlanDto createPlan(@RequestBody CreateMigrationPlanRequest request) {
        return planningService.createPlan(request);
    }

    @GetMapping("/plans/{id}")
    public MigrationPlanDto getPlan(@PathVariable UUID id) {
        return planningService.getPlan(id);
    }

    @PostMapping("/plans/{id}/execute")
    public void executePlan(@PathVariable UUID id) {
        // Execute in background thread
        CompletableFuture.runAsync(() -> {
            executionService.executePlan(id);
        });
    }

    @GetMapping("/plans/{id}/status")
    public MigrationStatusDto getStatus(@PathVariable UUID id) {
        return planningService.getStatus(id);
    }
}
```

### UI Updates

**2.6 Migration Planning UI**

File: `archivum-ui/src/pages/MigrationPlanning.tsx`

```tsx
export function MigrationPlanningPage() {
  const [selectedFiles, setSelectedFiles] = useState<string[]>([]);
  const [basePath, setBasePath] = useState('/mnt/nas/Archive');

  const createPlanMutation = useMutation({
    mutationFn: createMigrationPlan,
    onSuccess: (plan) => {
      navigate(`/migration/plans/${plan.id}`);
    }
  });

  const handleCreatePlan = () => {
    createPlanMutation.mutate({
      name: `Migration ${new Date().toISOString()}`,
      fileIds: selectedFiles,
      destinationType: 'NAS',
      basePath: basePath
    });
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Migration Planning</h1>

      {/* File selector */}
      <FileSelector onSelect={setSelectedFiles} />

      {/* Destination config */}
      <div className="mt-6">
        <Label>Destination Base Path</Label>
        <Input value={basePath} onChange={e => setBasePath(e.target.value)} />
      </div>

      {/* Summary */}
      <Card className="mt-6">
        <CardHeader>Migration Summary</CardHeader>
        <CardContent>
          <dl>
            <dt>Files selected</dt>
            <dd>{selectedFiles.length}</dd>
          </dl>
        </CardContent>
      </Card>

      <Button onClick={handleCreatePlan} className="mt-6">
        Create Migration Plan
      </Button>
    </div>
  );
}
```

**2.7 Migration Execution UI**

File: `archivum-ui/src/pages/MigrationExecution.tsx`

```tsx
export function MigrationExecutionPage({ planId }: { planId: string }) {
  const { data: plan, isLoading } = useQuery({
    queryKey: ['migration-plan', planId],
    queryFn: () => getMigrationPlan(planId),
    refetchInterval: 2000  // Poll every 2 seconds during execution
  });

  const executeMutation = useMutation({
    mutationFn: () => executeMigrationPlan(planId)
  });

  if (isLoading) return <LoadingSpinner />;

  const progress = plan.completedTasks / plan.totalFiles * 100;

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Migration: {plan.name}</h1>

      {/* Progress */}
      <Card>
        <CardHeader>Progress</CardHeader>
        <CardContent>
          <Progress value={progress} />
          <p className="mt-2">
            {plan.completedTasks} / {plan.totalFiles} files migrated
          </p>
        </CardContent>
      </Card>

      {/* Required disks */}
      <Card className="mt-6">
        <CardHeader>Required Disks</CardHeader>
        <CardContent>
          {plan.requiredDisks.map(disk => (
            <DiskRequirement key={disk.id} disk={disk} />
          ))}
        </CardContent>
      </Card>

      {/* Execute button */}
      {plan.status === 'READY' && (
        <Button onClick={() => executeMutation.mutate()} className="mt-6">
          Start Migration
        </Button>
      )}

      {/* Status */}
      {plan.status === 'EXECUTING' && (
        <Alert className="mt-6">
          <AlertTitle>Migration in progress...</AlertTitle>
          <AlertDescription>
            Do not unplug source disks.
          </AlertDescription>
        </Alert>
      )}
    </div>
  );
}
```

### Testing

**2.8 Integration Test**

```java
@SpringBootTest
@Testcontainers
class MigrationIntegrationTest {

    @Test
    void testBasicMigration() {
        // 1. Create source with files
        Source source = createTestSource();
        ScannedFile file1 = createTestFile(source, "/test/file1.txt");
        ScannedFile file2 = createTestFile(source, "/test/file2.txt");

        // 2. Mark source as ONLINE
        source.setState(DiskState.ONLINE);
        source.setMountPoint("/mnt/test");
        sourceRepository.save(source);

        // 3. Create migration plan
        CreateMigrationPlanRequest request = CreateMigrationPlanRequest.builder()
            .name("Test Migration")
            .fileIds(List.of(file1.getId(), file2.getId()))
            .destinationType("NAS")
            .basePath("/mnt/nas/test")
            .build();

        MigrationPlanDto plan = planningService.createPlan(request);

        // 4. Execute
        executionService.executePlan(plan.getId());

        // 5. Verify
        file1 = fileRepository.findById(file1.getId()).get();
        file2 = fileRepository.findById(file2.getId()).get();

        assertEquals(FileState.MIGRATED, file1.getState());
        assertEquals(FileState.MIGRATED, file2.getState());

        assertTrue(Files.exists(Paths.get(file1.getMigratedPath())));
        assertTrue(Files.exists(Paths.get(file2.getMigratedPath())));
    }
}
```

### Acceptance Criteria

- ✅ Can create migration plan from selected files
- ✅ Plan checks for required disks
- ✅ Shows error if disks offline
- ✅ Executes migration (copy files)
- ✅ Verifies checksums after copy
- ✅ Updates file state to MIGRATED
- ✅ Records migration history
- ✅ UI shows progress during execution
- ✅ Can handle errors gracefully

---

## Milestone 3: Destination Scanning (Baseline)

**Goal**: Support scanning already-organized NAS as DESTINATION

**Duration**: 1-2 days

### Implementation

**3.1 Automatic file state based on source type**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/service/FileIngestionService.java`

```java
public void ingest(FileBatchDto batch) {
    Source source = sourceRepository.findById(batch.getSourceId())
        .orElseThrow(() -> new NotFoundException("Source not found"));

    for (FileDto fileDto : batch.getFiles()) {
        ScannedFile file = toEntity(fileDto);

        // Set file state based on source type
        if (source.getSourceType() == SourceType.DESTINATION) {
            file.setState(FileState.PINNED);  // Already in final location
        } else if (source.getSourceType() == SourceType.WAREHOUSE) {
            file.setState(FileState.WAREHOUSED);  // On warehouse disk
        } else {
            file.setState(FileState.DISCOVERED);  // Default
        }

        fileRepository.save(file);
    }
}
```

**3.2 Deduplication against PINNED files**

File: `archivum-server/src/main/java/tech/zaisys/archivum/server/service/MigrationPlanningService.java`

```java
private MigrationTask createTask(MigrationPlan plan, ScannedFile file, CreateMigrationPlanRequest request) {
    // Check if file already exists in PINNED location
    Optional<ScannedFile> pinnedFile = fileRepository.findByContentHashAndState(
        file.getContentHash(),
        FileState.PINNED
    );

    if (pinnedFile.isPresent()) {
        // File already in archive, skip migration
        log.info("File {} already exists at {}, skipping",
            file.getPath(), pinnedFile.get().getPath());

        // Mark original file as duplicate
        file.setState(FileState.DUPLICATE);
        file.setDuplicateOf(pinnedFile.get().getId());
        fileRepository.save(file);

        return null;  // Don't create migration task
    }

    // ... rest of normal task creation ...
}
```

**3.3 UI - Show already-in-archive status**

File: `archivum-ui/src/components/FileList.tsx`

```tsx
function FileRow({ file }: { file: ScannedFile }) {
  return (
    <tr>
      <td>{file.name}</td>
      <td>
        {file.state === 'DUPLICATE' && file.duplicateLocation && (
          <Badge variant="success">
            ✓ Already in Archive
            <Tooltip>
              <TooltipContent>
                {file.duplicateLocation}
              </TooltipContent>
            </Tooltip>
          </Badge>
        )}
        {file.state === 'DISCOVERED' && (
          <Badge variant="warning">New file</Badge>
        )}
      </td>
    </tr>
  );
}
```

### Acceptance Criteria

- ✅ Can scan NAS with `--source-type DESTINATION`
- ✅ Files automatically marked as PINNED
- ✅ When scanning new disks, detects duplicates against PINNED files
- ✅ UI shows "Already in Archive" status
- ✅ Migration plan skips files already in archive

---

## Milestone 4: Warehouse Migration

**Goal**: Consolidate files to warehouse disks

**Duration**: 2-3 days

(Similar structure: DB changes, services, API, UI)

---

## Milestone 5: Advanced Features

**Goal**: Git archive, bi-directional migration, etc.

**Duration**: 4-5 days

---

## Implementation Order

1. ✅ **Start with Milestone 1** (Disk tracking) - CRITICAL
2. ✅ **Then Milestone 2** (Basic NAS migration) - MVP
3. ✅ **Then Milestone 3** (Destination scanning) - Important
4. ⏸️ **Milestone 4** (Warehouse) - Can defer if needed
5. ⏸️ **Milestone 5** (Advanced) - Future

---

## Next Steps

1. **Review this plan** - User approval
2. **Start Milestone 1** - Disk tracking implementation
3. **Test thoroughly** - Each milestone before moving to next
4. **Iterate** - Adjust based on learnings

---

**Ready to start?** Let me know if you want to adjust anything, then we'll begin with Milestone 1!
