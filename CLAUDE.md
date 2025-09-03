# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Seal is a Java-based security tool that analyzes MySQL database traffic by parsing tcpdump files to identify risky SQL queries. The application provides a web interface for database connection management, pcap file upload and analysis, and SQL risk assessment with optimization suggestions.

## Build & Development

**Build Command:**
```bash
mvn clean package
```

**Run Application:**
```bash
java -Dsoar.path=/path/to/soar -jar seal-1.0-SNAPSHOT.jar
```

**Configuration Options:**
```bash
# Database connection pool configuration
-Ddb.max-pool-size=3
-Ddb.connection-timeout=6000

# Application configuration
-Dsoar.path=/path/to/soar
```

**Java Version:** 17+

**Key Dependencies:**
- Javalin 6.1.3 (web framework)
- EclipseStore 1.3.2 (embedded storage)
- MySQL Connector 8.2.0
- Kaitai Struct 0.10 (pcap parsing)
- Jackson 2.16.1 (JSON processing)
- Lombok 1.18.30 (code generation)
- Apache Commons Lang3, Exec
- Hutool Core

## Architecture

The application follows a modern layered architecture pattern:

**Main Application:** `SealApplication.java` - Entry point with Javalin web server configuration and API routing

**Controllers:**
- `DatabaseController` - HTTP request handling for database operations
- `SqlAnalysisController` - HTTP request handling for SQL analysis operations

**Core Managers:**
- `DatabaseManager` - Database connection management (extends AbstractManager)
- `SqlAnalysisManager` - SQL analysis and risk assessment (extends AbstractManager)

**Base Classes:**
- `AbstractManager<T>` - Base manager class with common CRUD operations
- `Manager<T>` - Manager interface defining standard operations

**Configuration:**
- `AppConfig` - Centralized configuration management with type-safe settings

**Key Architecture Components:**
- **Storage:** EclipseStore for embedded data persistence
- **Web Framework:** Javalin with RESTful API design and proper error handling
- **Frontend:** Static files with Alpine.js and HyperUI CSS
- **Pcap Parsing:** Kaitai Struct for binary protocol parsing (refactored into PcapParser)
- **External Integration:** SOAR tool for SQL analysis (requires external binary)

**Package Structure:**
- `org.fisheep.config` - Configuration management
- `org.fisheep.controller` - HTTP request controllers
- `org.fisheep.manager` - Business logic managers and base classes
- `org.fisheep.common` - Shared utilities, exceptions, and storage management
- `org.fisheep.util` - Utility classes (refactored: StringUtils, ByteArrayUtils, PcapParser)
- `org.fisheep.bean` - Data models and DTOs
- `org.fisheep.kaitai` - Kaitai parsing classes (auto-generated)

## API Endpoints

All APIs are prefixed with `/seal`:

**Database Management (RESTful):**
- `GET /api/v1/databases` - Get all database connections
- `POST /api/v1/databases` - Add database connection
- `DELETE /api/v1/databases/{id}` - Delete database connection

**SQL Analysis:**
- `POST /upload` - Upload PCAP file (rate-limited)
- `GET /api/v1/analysis/db-timestamp` - Get database and timestamp info
- `POST /api/v1/analysis/results` - Get analysis results (paginated)
- `POST /api/v1/analysis/status` - Get task status
- `POST /api/v1/analysis/export` - Export results (JSON/CSV)
- `SSE /analysis/status/{taskId}` - Real-time task status updates

## Error Handling

**Enhanced Exception System:**
- `SealException` - Custom exception with error codes and messages
- `ErrorEnum` - Comprehensive error code enumeration with proper categorization
- Global exception handling in controllers with proper HTTP status codes

**Error Code Categories:**
- 10000-19999: General errors
- 11000-11999: Database-related errors
- 12000-12999: File-related errors
- 13000-13999: Task-related errors
- 14000-14999: Business logic errors

## Code Quality Improvements

**Recent Refactoring:**
1. **Configuration Management** - Centralized configuration with `AppConfig`
2. **Manager Pattern** - Introduced base `Manager<T>` interface and `AbstractManager<T>`
3. **Controller Layer** - Separated HTTP handling from business logic
4. **Error Handling** - Enhanced exception system with comprehensive error codes
5. **Utility Classes** - Refactored utilities into focused classes:
   - `StringUtils` - String manipulation utilities
   - `ByteArrayUtils` - Byte array operations
   - `PcapParser` - PCAP file parsing (replaced complex PcapUtil)
6. **API Design** - RESTful API design with proper versioning (/api/v1/)
7. **Task Management** - Enhanced task tracking with `TaskInfo` class

**Design Patterns:**
- **Singleton Pattern** - `AppConfig` for centralized configuration
- **Template Method Pattern** - `AbstractManager<T>` for common CRUD operations
- **Factory Pattern** - Configuration class initialization
- **Strategy Pattern** - Different export formats (JSON, CSV)

## Development Notes

- The application requires an external `soar` binary for SQL analysis (configured via `-Dsoar.path` JVM argument)
- MySQL connections must use `useSSL=false` parameter for tcpdump parsing to work
- The application uses lazy reference management for storage optimization
- Rate limiting is applied to file upload endpoints (configurable via `AppConfig`)
- All database connections are managed via connection pooling with configurable settings
- Comprehensive logging and error handling throughout the application
- Thread-safe implementation with proper concurrency handling