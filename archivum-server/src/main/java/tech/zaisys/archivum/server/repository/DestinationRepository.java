package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.server.domain.Destination;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Destination entities.
 */
@Repository
public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    /**
     * Find all active destinations.
     */
    List<Destination> findByIsActiveTrueOrderByPriorityDesc();

    /**
     * Find destinations by type.
     */
    List<Destination> findByDestinationType(Destination.DestinationType type);

    /**
     * Find destination by mounted path.
     */
    Optional<Destination> findByMountedPath(String mountedPath);

    /**
     * Check if a mounted path already exists.
     */
    boolean existsByMountedPath(String mountedPath);
}
