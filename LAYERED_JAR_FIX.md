# Fix for SSH Shell Spring Boot Layered JAR Terminal Provider Issues

## Problem
When using Spring Boot layered JARs with SSH Shell, JLine's service discovery mechanism fails to find terminal providers, resulting in:
- `Unable to find terminal provider` errors
- SSH connections hanging indefinitely
- ClassNotFoundException for terminal providers

## Solution

### 1. Add Explicit Dependencies
Add these dependencies to your `pom.xml`:

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

### 2. Create Terminal Factory
Create `SshTerminalFactory.java`:

```java
package com.github.fonimus.ssh.shell;

import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.terminal.impl.ExternalTerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class SshTerminalFactory {

    public static Terminal createTerminal(InputStream is, OutputStream os, Size size, String type) throws IOException {
        // First try: Direct instantiation with ExternalTerminal (works with layered JARs)
        try {
            log.debug("Attempting to create terminal with ExternalTerminal");
            ExternalTerminal terminal = new ExternalTerminal(
                    "ssh-shell",
                    type != null ? type : "xterm",
                    is,
                    os,
                    StandardCharsets.UTF_8
            );
            
            if (size != null) {
                terminal.setSize(size);
            }
            log.info("Successfully created ExternalTerminal for SSH session");
            return terminal;
        } catch (Exception e) {
            log.debug("ExternalTerminal creation failed: {}", e.getMessage());
        }

        // Second try: Standard TerminalBuilder with explicit settings
        try {
            log.debug("Attempting standard TerminalBuilder with system=false");
            TerminalBuilder builder = TerminalBuilder.builder()
                    .system(false)
                    .streams(is, os)
                    .encoding(StandardCharsets.UTF_8)
                    .jansi(true)
                    .jna(false)
                    .jni(false);
            
            if (size != null) {
                builder.size(size);
            }
            if (type != null) {
                builder.type(type);
            }
            
            Terminal terminal = builder.build();
            log.info("Successfully created terminal via TerminalBuilder");
            return terminal;
        } catch (Exception e) {
            log.debug("TerminalBuilder failed: {}", e.getMessage());
        }

        // Final fallback: DumbTerminal
        log.warn("All terminal providers failed, creating DumbTerminal");
        DumbTerminal terminal = new DumbTerminal(
                "ssh-shell-dumb",
                type != null ? type : "dumb",
                is,
                os,
                StandardCharsets.UTF_8
        );
        
        if (size != null) {
            terminal.setSize(size);
        }
        
        terminal.echo(false);
        terminal.setAttributes(terminal.getAttributes());
        
        log.info("Created DumbTerminal fallback for SSH session");
        return terminal;
    }
}
```

### 3. Modify SshShellRunnable
Replace the terminal creation in `SshShellRunnable.run()`:

**Replace this:**
```java
TerminalBuilder terminalBuilder = TerminalBuilder.builder().system(false).streams(is, os);
// ... size and type configuration ...
Terminal terminal = terminalBuilder.build()
```

**With this:**
```java
Size terminalSize = null;
boolean sizeAvailable = false;
if (sshEnv.getEnv().containsKey(SSH_ENV_COLUMNS) && sshEnv.getEnv().containsKey(SSH_ENV_LINES)) {
    try {
        terminalSize = new Size(
                Integer.parseInt(sshEnv.getEnv().get(SSH_ENV_COLUMNS)),
                Integer.parseInt(sshEnv.getEnv().get(SSH_ENV_LINES))
        );
        sizeAvailable = true;
    } catch (NumberFormatException e) {
        // handle error
    }
}

String terminalType = null;
if (sshEnv.getEnv().containsKey(SSH_ENV_TERM)) {
    terminalType = sshEnv.getEnv().get(SSH_ENV_TERM);
}

Terminal terminal = SshTerminalFactory.createTerminal(is, os, terminalSize, terminalType)
```

### 4. Add Environment Post Processor
Create `SshShellEnvironmentPostProcessor.java`:

```java
package com.github.fonimus.ssh.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

public class SshShellEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Set system properties to help JLine work with layered JARs
        System.setProperty("org.jline.terminal.provider", "jansi");
        System.setProperty("org.jline.terminal.jansi", "true");
        System.setProperty("org.jline.terminal.jna", "false");
        System.setProperty("org.jline.terminal.jni", "false");
        System.setProperty("org.jline.terminal.exec", "false");
        
        if (System.getProperty("org.jline.terminal.type") == null) {
            System.setProperty("org.jline.terminal.type", "xterm");
        }
        
        System.setProperty("org.jline.terminal.dumb", "true");
    }
}
```

### 5. Register the Post Processor
Create `META-INF/spring.factories`:

```properties
# Environment Post Processors
org.springframework.boot.env.EnvironmentPostProcessor=\
  com.github.fonimus.ssh.shell.SshShellEnvironmentPostProcessor
```

## How It Works

1. **Direct Terminal Creation**: Bypasses JLine's service discovery by directly instantiating terminal classes
2. **Multiple Fallbacks**: Tries ExternalTerminal → TerminalBuilder → DumbTerminal
3. **System Properties**: Sets JLine properties early in the boot process
4. **Explicit Dependencies**: Ensures terminal provider classes are in the classpath

## Testing

To test with layered JARs:
```bash
# Build with layers
mvn clean package -DnoSamples -DskipTests

# Extract layers (if using Docker)
java -Djarmode=layertools -jar target/*.jar extract

# Run the application
java -jar target/*.jar
```

## Alternative: Disable Layered JARs

If you don't need layered JARs, you can disable them in your `pom.xml`:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layers>
            <enabled>false</enabled>
        </layers>
    </configuration>
</plugin>
```

## Classloading Fix for Layered JARs

When using layered JARs, SSH sessions may fail to load application classes, resulting in `ClassNotFoundException` errors. This happens because SSH threads don't have the correct context classloader.

### Fix in SshShellRunnable

Add classloader management to the `run()` method:

```java
@Override
public void run() {
    // Preserve the main application's classloader for SSH threads
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader applicationClassLoader = this.getClass().getClassLoader();
    
    try {
        // Set the thread context classloader to the application classloader
        Thread.currentThread().setContextClassLoader(applicationClassLoader);
        
        // ... rest of the run method ...
        
    } finally {
        // Restore the original classloader
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
}
```

This ensures that:
- SSH threads can access all application classes
- Spring can properly instantiate beans and repositories
- Commands can access all dependencies

## Template Loading Fix for Help Command

The help command may fail with "template instance 'main' not found" error. This is fixed by:

### 1. Adding 'main' template to help templates

Update `template/help-commands-default.stg` to include a 'main' template:

```stg
group commands;

main(commands, unsupportedCommands, message) ::= <<
<if(message)><message><\n><endif>
<if(commands)>
<header()>
<commands:{c | <command(c)>}; separator="\n">
<endif>
<if(unsupportedCommands)>

Currently unsupported commands (type 'help &lt;command>' for details):
<unsupportedCommands:{c | <command(c)>}; separator="\n">
<endif>
>>
```

### 2. Template Configuration Bean

The `SshShellTemplateConfiguration` class ensures templates are loaded with the correct classloader and resource paths.

## Notes

- The ExternalTerminal approach works best for SSH connections
- DumbTerminal fallback ensures the application always starts, though with limited terminal features
- The classloader fix ensures all classes are accessible in SSH sessions
- The template fix ensures help commands work properly
- These changes are backward compatible with non-layered deployments