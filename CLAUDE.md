# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

### Building the project
```bash
# Build without samples
mvn clean install -DnoSamples

# Build with samples
mvn clean install

# Skip tests
mvn clean install -DskipTests
```

### Running tests
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl starter

# Run single test class
mvn test -Dtest=SshShellApplicationTest

# Run with coverage
mvn clean test jacoco:report
```

### Release process
```bash
# Prepare release (updates versions)
mvn release:prepare -P release -DnoSamples

# Perform release (deploys to repository)
mvn release:perform -P release -DnoSamples
```

## Architecture Overview

This is a Spring Boot starter that provides SSH server functionality integrated with Spring Shell. The project enables running Spring Shell commands over SSH connections.

### Module Structure
- **starter/**: Main library module containing the SSH Shell Spring Boot starter
- **samples/basic/**: Basic sample application demonstrating minimal setup
- **samples/complete/**: Complete sample with security, actuator, and session management

### Key Components

**Core SSH Integration** (`com.github.fonimus.ssh.shell`):
- `SshShellAutoConfiguration`: Main auto-configuration class that sets up SSH server
- `SshShellCommandFactory`: Factory for creating SSH command instances
- `SshShellRunnable`: Main SSH server thread implementation
- `SshContext`/`SshIO`: Context and I/O handling for SSH sessions

**Authentication** (`com.github.fonimus.ssh.shell.auth`):
- Supports simple password auth, Spring Security integration, and public key authentication
- `SshShellAuthenticationProvider`: Interface for custom authentication implementations

**Commands** (`com.github.fonimus.ssh.shell.commands`):
- Built-in command groups: actuator, datasource, jmx, system, tasks, manage-sessions
- Use `@SshShellComponent` instead of `@ShellComponent` for SSH-specific commands
- Commands can be restricted by roles using `@ShellMethodAvailability`

**Post Processors** (`com.github.fonimus.ssh.shell.postprocess`):
- Chain commands with pipe character (|) 
- Built-in processors: save (>), pretty, json, grep, highlight
- Custom processors implement `PostProcessor` interface

### Configuration Properties

All configuration under `ssh.shell.*`:
- `enable`: Enable/disable SSH server (default: true)
- `port`: SSH port (default: 2222)
- `host`: Bind address (default: 127.0.0.1)
- `authentication`: Type of auth - simple/security (default: simple)
- `authorized-public-keys-file`: Path to authorized_keys file
- Commands can be controlled via `ssh.shell.commands.<group>.enable/restricted/authorized-roles`

### Spring Shell Version

This project uses Spring Shell 3.x (currently 3.4.1) which has significant changes from 2.x:
- Commands use `@ShellMethod` and `@ShellOption` annotations
- Interactive mode is enabled by default
- Return values from commands are automatically displayed

## Testing Approach

Tests use JUnit 5 and Spring Boot Test framework. Key test patterns:
- Use `@SpringBootTest` for integration tests
- Disable SSH in tests with `SshShellProperties.DISABLE_SSH_SHELL` property
- Mock SSH sessions and contexts for unit testing commands
- Test coverage tracked via JaCoCo (visible in SonarCloud)

## Docker/Container Deployment Issues

### Terminal Provider Issues in Layered Images
When deploying with Spring Boot's layered Docker images, JLine terminal providers may not be discoverable. This causes:
- SSH clients hanging on connect
- Terminal creation failures with "Unable to find terminal provider" errors

### Solutions:

1. **Add explicit JLine dependencies** in your downstream project:
```xml
<dependency>
    <groupId>org.jline</groupId>
    <artifactId>jline-terminal-jansi</artifactId>
    <version>3.30.4</version>
</dependency>
<dependency>
    <groupId>org.fusesource.jansi</groupId>
    <artifactId>jansi</artifactId>
    <version>2.4.1</version>
</dependency>
```

2. **Set JVM system properties** for terminal configuration:
```properties
-Dorg.jline.terminal.dumb=true
-Dorg.jline.terminal.jansi=true  
```

3. **Use environment variables** to force terminal type:
```bash
TERM=xterm
```

4. **Modified SshShellRunnable.java** includes fallback terminal creation that:
   - Tries standard terminal creation first
   - Falls back to DumbTerminal for Docker environments with proper SSH configuration
   - Configures terminal attributes for echo and interactive input
   - Sets terminal size from SSH environment variables
   - Provides proper error handling and logging

5. **Added Spring Shell template resources** to handle missing help files:
   - `template/help-commands-default.stg` - Command listing template
   - `template/help-command-default.stg` - Individual command help template  
   - `template/version-default.st` - Version display template

### Common Container Issues:
- Missing terminal providers in classpath
- Resource loading issues with layered images
- Missing native library access
- Spring Shell template files not found in layered images
- Terminal echo and keystroke handling problems with DumbTerminal