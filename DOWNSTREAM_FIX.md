# Fix for Downstream Project Using SSH Shell in Docker

## The Problem
When using SSH Shell Spring Boot in Docker with layered images, you're experiencing:
1. Missing keystroke echo 
2. Garbled output
3. Spring Shell help templates not found
4. Terminal fallback to DumbTerminal which doesn't handle echo properly

## Root Cause
Docker layered images split the classpath, causing:
- JLine terminal providers to not be found
- Spring Shell template resources to be inaccessible
- DumbTerminal being used which doesn't properly handle echo in SSH

## Solution for Your Downstream Project

### 1. Update Your Dockerfile

Add these to ensure terminal providers are available:

```dockerfile
# Ensure terminal providers are in the classpath
ENV JAVA_OPTS="-Dorg.jline.terminal.jna=false \
               -Dorg.jline.terminal.jansi=true \
               -Dfile.encoding=UTF-8 \
               -Djava.awt.headless=true"

# Set terminal type
ENV TERM=xterm

# Mark as Docker container for detection
ENV DOCKER_CONTAINER=true
```

### 2. Fix Spring Shell Template Loading

Create a configuration class in your project:

```java
package com.yourcompany.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class SshShellDockerConfig {
    
    @Bean
    public ResourceLoader dockerResourceLoader() {
        // Use a resource loader that works with layered JARs
        return new DefaultResourceLoader(Thread.currentThread().getContextClassLoader());
    }
}
```

### 3. Add Missing Dependencies

Ensure these are in your downstream project's pom.xml:

```xml
<dependency>
    <groupId>com.github.fonimus</groupId>
    <artifactId>ssh-shell-spring-boot-starter</artifactId>
    <version>3.3.1-SNAPSHOT</version>
</dependency>

<!-- Explicitly include terminal providers -->
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

<!-- Ensure Spring Shell templates are available -->
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-standard-commands</artifactId>
    <version>3.4.1</version>
</dependency>
```

### 4. Copy Templates to Your Project

Since the templates aren't being found from the Spring Shell JAR in Docker, copy them directly into your project:

Create these files in your project's `src/main/resources/template/`:

**help-commands-default.stg:**
```stg
group commands;

commands(commands, unsupportedCommands, message) ::= <<
<if(message)><message><\n><endif>
<if(commands)>
Available commands (type 'help &lt;command>' for details):
<commands:{c | <command(c)>}; separator="\n">
<endif>
<if(unsupportedCommands)>

Currently unsupported commands (type 'help &lt;command>' for details):
<unsupportedCommands:{c | <command(c)>}; separator="\n">
<endif>
>>

command(command) ::= <<
  <command.name>: <command.description>
>>
```

**help-command-default.stg:**
```stg
group command;

command(command) ::= <<
<command.name> - <command.description>

USAGE
  <command.usage>

<if(command.options)>
OPTIONS
<command.options:{option | <option(option)>}; separator="\n">
<endif>

<if(command.aliases)>
ALIASES
<command.aliases; separator=", ">
<endif>
>>

option(option) ::= <<
  <option.names; separator=", ">  <option.description><if(option.defaultValue)> (default: <option.defaultValue>)<endif>
>>
```

### 5. Application Properties

Add these to your `application.yml`:

```yaml
ssh:
  shell:
    # Force specific settings for Docker
    terminal:
      type: xterm
    
spring:
  shell:
    # Disable interactive mode if causing issues
    interactive:
      enabled: true
    # Help command configuration
    command:
      help:
        enabled: true
```

### 6. Build Your Docker Image Without Layers (Temporary Workaround)

If the above doesn't work, temporarily disable layering:

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

Or build a traditional JAR:

```bash
mvn clean package spring-boot:repackage
```

### 7. Debug Terminal Creation

Add this to your `application.yml` to see what terminal is being created:

```yaml
logging:
  level:
    com.github.fonimus.ssh.shell: DEBUG
    org.jline: DEBUG
```

## Verification Steps

1. Build your downstream project with the fixes
2. Run the Docker container
3. SSH into the container
4. Check the logs for:
   - "Created SshTerminal with forced echo for Docker environment"
   - NOT "Using basic DumbTerminal as last resort"
5. Test:
   - Type characters - they should echo
   - Press Enter - should work properly
   - Type `help` - should display command list

## If Still Not Working

The issue is that DumbTerminal fundamentally doesn't handle echo properly for SSH. The real fix requires:

1. **Ensuring SshTerminal is used**: The logs should show "Created SshTerminal with forced echo"
2. **Not falling back to DumbTerminal**: This means terminal providers must be found

Try running your container with:

```bash
docker run -it \
  -e TERM=xterm \
  -e DOCKER_CONTAINER=true \
  -e JAVA_OPTS="-Dorg.jline.terminal.dumb=false -Dorg.jline.terminal.jansi=true" \
  your-image
```

## Alternative: Disable Layering Completely

If nothing else works, the nuclear option is to build a traditional (non-layered) Docker image:

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/your-app.jar app.jar
ENV TERM=xterm
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

This ensures all classes and resources are in a single JAR where they can be found.