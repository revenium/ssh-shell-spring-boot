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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Environment post processor to set JLine system properties for layered JAR support
 */
public class SshShellEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Set system properties to help JLine work with layered JARs
        // These need to be system properties, not Spring properties
        
        // Force Jansi provider which works best with layered JARs
        System.setProperty("org.jline.terminal.provider", "jansi");
        System.setProperty("org.jline.terminal.jansi", "true");
        
        // Disable providers that may not work in containers
        System.setProperty("org.jline.terminal.jna", "false");
        System.setProperty("org.jline.terminal.jni", "false");
        System.setProperty("org.jline.terminal.exec", "false");
        
        // Set terminal type for better compatibility
        if (System.getProperty("org.jline.terminal.type") == null) {
            System.setProperty("org.jline.terminal.type", "xterm");
        }
        
        // Enable dumb terminal fallback if needed
        System.setProperty("org.jline.terminal.dumb", "true");
    }
}