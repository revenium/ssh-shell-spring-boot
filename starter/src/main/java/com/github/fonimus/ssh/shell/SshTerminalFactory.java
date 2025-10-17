/*
 * Copyright (c) 2020 François Onimus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.fonimus.ssh.shell;

import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Factory for creating SSH terminals with fallback mechanisms for layered JAR deployments
 */
@Slf4j
public class SshTerminalFactory {

    /**
     * Creates a terminal for SSH sessions with robust fallback mechanisms
     *
     * @param is   Input stream
     * @param os   Output stream
     * @param size Terminal size (can be null)
     * @param type Terminal type (can be null)
     * @return Terminal instance
     * @throws IOException If terminal creation fails
     */
    public static Terminal createTerminal(InputStream is, OutputStream os, Size size, String type) throws IOException {
        // First try: Direct instantiation with ExternalTerminal (works with layered JARs)
        try {
            LOGGER.debug("Attempting to create terminal with ExternalTerminal");
            org.jline.terminal.impl.ExternalTerminal terminal = new org.jline.terminal.impl.ExternalTerminal(
                    "ssh-shell",
                    type != null ? type : "xterm",
                    is,
                    os,
                    StandardCharsets.UTF_8
            );
            
            if (size != null) {
                terminal.setSize(size);
            }
            LOGGER.info("Successfully created ExternalTerminal for SSH session");
            return terminal;
        } catch (Exception e) {
            LOGGER.debug("ExternalTerminal creation failed: {}", e.getMessage());
        }

        // Second try: Standard TerminalBuilder with explicit provider
        try {
            LOGGER.debug("Attempting standard TerminalBuilder with system=false");
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
            LOGGER.info("Successfully created terminal via TerminalBuilder");
            return terminal;
        } catch (Exception e) {
            LOGGER.debug("TerminalBuilder failed: {}", e.getMessage());
        }

        // Final fallback: Create DumbTerminal directly with proper configuration
        LOGGER.warn("All terminal providers failed, creating DumbTerminal with SSH-optimized settings");
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
        
        // Configure terminal for proper SSH operation
        terminal.echo(false);
        terminal.setAttributes(terminal.getAttributes());
        
        LOGGER.info("Created DumbTerminal fallback for SSH session");
        return terminal;
    }
}