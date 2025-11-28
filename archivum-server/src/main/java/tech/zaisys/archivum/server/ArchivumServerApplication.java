package tech.zaisys.archivum.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Archivum Server application.
 *
 * <p>This Spring Boot application provides the central coordination, storage,
 * and intelligence for the Archivum file organization system.</p>
 */
@SpringBootApplication
public class ArchivumServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchivumServerApplication.class, args);
    }
}
