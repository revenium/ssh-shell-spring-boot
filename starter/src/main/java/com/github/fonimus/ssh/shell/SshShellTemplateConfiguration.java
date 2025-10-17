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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.style.TemplateExecutor;
import org.springframework.shell.style.ThemeResolver;

/**
 * Configuration to ensure Spring Shell templates are properly loaded for SSH sessions
 * This is necessary for layered JAR deployments where resource loading can fail
 */
@Configuration
@ConditionalOnClass(TemplateExecutor.class)
public class SshShellTemplateConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(TemplateExecutor.class)
    public TemplateExecutor templateExecutor(ThemeResolver themeResolver) {
        // Create TemplateExecutor with the correct classloader context
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set the classloader to ensure resources can be found in layered JARs
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            
            // Create the executor with the theme resolver
            return new TemplateExecutor(themeResolver);
        } finally {
            // Restore the original classloader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}