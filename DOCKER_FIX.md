# Docker Deployment Fix for SSH Shell Spring Boot

## Problem Summary
When deploying SSH Shell Spring Boot applications in Docker with layered images, several issues occur:
1. Terminal providers cannot be found, causing SSH clients to hang
2. Terminal creation fails with "Unable to find terminal provider" errors
3. Keystroke echo doesn't work properly
4. Output is garbled
5. Spring Shell help commands fail due to missing template resources

## Solution Overview

### Files Modified/Created:

1. **SshShellRunnable.java** - Enhanced terminal creation with fallback strategy
2. **SshPtyTerminal.java** - New SSH-specific terminal implementation
3. **SshShellEnvironmentPostProcessor.java** - Docker environment detection and configuration
4. **Template files** - Spring Shell help templates
5. **spring.factories** - Spring Boot auto-configuration
6. **pom.xml** - Added explicit terminal provider dependencies

## Implementation Details

### 1. SshPtyTerminal.java
A custom terminal implementation that:
- Extends AbstractPosixTerminal for proper POSIX behavior
- Configures all necessary terminal attributes for SSH
- Enables proper echo and line editing
- Handles control characters correctly
- Works without native terminal providers

### 2. Terminal Creation Strategy (SshShellRunnable.java)
Three-tier fallback approach:
1. Try standard TerminalBuilder (works in normal environments)
2. Fall back to SshPtyTerminal (works in Docker without providers)
3. Last resort: DumbTerminal (basic functionality)

### 3. Terminal Attributes Configuration
Critical settings for SSH operation:
```java
attr.setLocalFlag(Attributes.LocalFlag.ECHO, true);     // Enable keystroke echo
attr.setLocalFlag(Attributes.LocalFlag.ICANON, true);   // Enable line editing
attr.setInputFlag(Attributes.InputFlag.ICRNL, true);    // Handle carriage returns
attr.setOutputFlag(Attributes.OutputFlag.ONLCR, true);  // Handle newlines
```

## Deployment Instructions for Downstream Projects

### 1. Add Dependencies to Your Project
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

### 2. Configure Docker Environment Variables
```dockerfile
ENV TERM=xterm
ENV DOCKER_CONTAINER=true
```

### 3. JVM Options for Container
```bash
-Dorg.jline.terminal.dumb=false
-Dorg.jline.terminal.jansi=true
-Dfile.encoding=UTF-8
```

### 4. Ensure Resources Are Available
If using layered Docker images, ensure these resources are in the classpath:
- `/template/help-commands-default.stg`
- `/template/help-command-default.stg`
- `/template/version-default.st`

### 5. Spring Boot Layered JAR Configuration
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layers>
            <enabled>true</enabled>
            <includeLayerTools>true</includeLayerTools>
        </layers>
    </configuration>
</plugin>
```

### 6. Dockerfile Example
```dockerfile
FROM eclipse-temurin:17-jre-alpine

# Set environment for terminal support
ENV TERM=xterm
ENV DOCKER_CONTAINER=true

# Copy application layers
COPY --from=builder /workspace/dependencies/ ./
COPY --from=builder /workspace/spring-boot-loader/ ./
COPY --from=builder /workspace/snapshot-dependencies/ ./
COPY --from=builder /workspace/application/ ./

# Ensure JLine terminal providers are accessible
ENV JAVA_OPTS="-Dorg.jline.terminal.jansi=true -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

## Testing the Fix

1. Build the SSH Shell Spring Boot starter with these changes
2. Deploy to Docker using layered images
3. Connect via SSH client
4. Verify:
   - Client connects without hanging
   - Keystrokes are echoed properly
   - Output is properly formatted
   - `help` command works
   - Terminal responds to control characters (Ctrl+C, Ctrl+D, etc.)

## Known Limitations

When using DumbTerminal as ultimate fallback:
- Limited color support
- No cursor movement capabilities
- Basic line editing only
- No terminal resizing support

The SshPtyTerminal implementation addresses most of these limitations for Docker environments.

## Troubleshooting

If issues persist:

1. **Check logs for terminal type**:
   - Look for "Created SshPtyTerminal" vs "Using DumbTerminal"
   
2. **Verify classpath resources**:
   ```bash
   jar tf your-app.jar | grep template
   ```

3. **Test terminal attributes**:
   - SSH in and check if TERM environment variable is set
   - Try `echo $TERM` in the shell

4. **Force specific terminal type**:
   ```properties
   ssh.shell.terminal.type=xterm
   ```

## Alternative Approach

If the custom terminal doesn't work, you can force a specific terminal type in your application properties:

```yaml
ssh:
  shell:
    terminal:
      force-type: dumb
      disable-providers: true
```

This will bypass all provider discovery and use a basic terminal that should work everywhere.