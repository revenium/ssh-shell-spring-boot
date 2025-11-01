/*
 * Copyright (c) 2021 François Onimus
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

import com.github.fonimus.ssh.shell.auth.SshShellPublicKeyAuthenticationProvider;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.kex.KeyExchange;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.mac.MacFactory;
import org.apache.sshd.common.signature.SignatureFactory;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.extension.KexExtensions;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Ssh shell configuration
 */

@Slf4j
@Configuration
@AllArgsConstructor
public class SshShellConfiguration {

    private final SshShellProperties properties;

    private final SshShellCommandFactory shellCommandFactory;

    private final PasswordAuthenticator passwordAuthenticator;

    /**
     * Create the bean responsible for starting and stopping the SSH server
     *
     * @param sshServer the ssh server to manage
     * @return ssh server lifecycle
     */
    @Bean
    public SshServerLifecycle sshServerLifecycle(SshServer sshServer) {
        return new SshServerLifecycle(sshServer, this.properties);
    }

    /**
     * Construct ssh server thanks to ssh shell properties
     *
     * @return ssh server
     */
    @Bean
    public SshServer sshServer() throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        
        // Configure host key provider to only generate secure key types (no NIST curves)
        File hostKeyFile = getHostKeyFile(properties.getHostKeyFile());
        SimpleGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(hostKeyFile.toPath());
        keyProvider.setAlgorithm(KeyUtils.RSA_ALGORITHM);  // Use RSA instead of ECDSA with NIST curves
        keyProvider.setKeySize(3072);  // Use 3072-bit RSA for better security than 2048-bit
        server.setKeyPairProvider(keyProvider);
        server.setHost(properties.getHost());
        server.setPasswordAuthenticator(passwordAuthenticator);
        server.setPublickeyAuthenticator(RejectAllPublickeyAuthenticator.INSTANCE);
        if (properties.getAuthorizedPublicKeys() != null) {
            if (properties.getAuthorizedPublicKeys().exists()) {
                File publicKeysFile = getFile(properties.getAuthorizedPublicKeys());
                server.setPublickeyAuthenticator(
                        new SshShellPublicKeyAuthenticationProvider(publicKeysFile)
                );
                LOGGER.info("Using authorized public keys from : {} (resolved to: {})",
                        properties.getAuthorizedPublicKeys().getDescription(), publicKeysFile.getAbsolutePath());
                LOGGER.debug("Public key file exists: {}, readable: {}, size: {} bytes", 
                        publicKeysFile.exists(), publicKeysFile.canRead(), publicKeysFile.length());
                
                // Log the contents of the authorized keys file for debugging
                try {
                    List<String> lines = Files.readAllLines(publicKeysFile.toPath());
                    LOGGER.debug("Authorized keys file contains {} lines", lines.size());
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                LOGGER.debug("Key {}: {} {}...{}", i + 1, parts[0], 
                                    parts[1].substring(0, Math.min(20, parts[1].length())),
                                    parts[1].length() > 20 ? parts[1].substring(parts[1].length() - 10) : "");
                            } else {
                                LOGGER.debug("Key {}: Invalid format - {}", i + 1, line.substring(0, Math.min(50, line.length())));
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not read authorized keys file for debugging: {}", e.getMessage());
                }
            } else {
                LOGGER.warn("Could not read authorized public keys from : {}, public key authentication is disabled.",
                        properties.getAuthorizedPublicKeys().getDescription());
            }
        } else {
            LOGGER.info("No authorized public keys configured, public key authentication is disabled");
        }
        server.setPort(properties.getPort());
        server.setShellFactory(channelSession -> shellCommandFactory);
        server.setCommandFactory((channelSession, s) -> shellCommandFactory);
        
        // Configure secure algorithms only - remove failed algorithms from ssh-audit
        
        // Key exchange algorithms - remove NIST curve based algorithms
        List<String> insecureKexAlgorithms = Arrays.asList(
            "ecdh-sha2-nistp256",
            "ecdh-sha2-nistp384",
            "ecdh-sha2-nistp521",
            "mlkem1024nistp384-sha384",
            "mlkem768nistp256-sha256"
        );
        
        List<KeyExchangeFactory> kexFactories = server.getKeyExchangeFactories().stream()
            .filter(factory -> !insecureKexAlgorithms.contains(factory.getName()))
            .collect(Collectors.toList());
        
        // Only set if we have remaining factories
        if (!kexFactories.isEmpty()) {
            server.setKeyExchangeFactories(kexFactories);
        }
        
        // Filter out insecure signature algorithms (ssh-rsa uses SHA-1)
        List<String> insecureSignatureAlgorithms = Arrays.asList(
            "ssh-rsa"  // Uses broken SHA-1 hash algorithm
        );
        
        List<NamedFactory<Signature>> signatureFactories = server.getSignatureFactories().stream()
            .filter(factory -> !insecureSignatureAlgorithms.contains(factory.getName()))
            .collect(Collectors.toList());
        
        // Only set if we have remaining factories
        if (!signatureFactories.isEmpty()) {
            server.setSignatureFactories(signatureFactories);
        }
        
        // MAC algorithms - remove SHA-1 based algorithms
        List<String> insecureMacAlgorithms = Arrays.asList(
            "hmac-sha1",
            "hmac-sha1-etm@openssh.com"
        );
        
        List<NamedFactory<Mac>> macFactories = server.getMacFactories().stream()
            .filter(factory -> !insecureMacAlgorithms.contains(factory.getName()))
            .collect(Collectors.toList());
        
        // Only set if we have remaining factories
        if (!macFactories.isEmpty()) {
            server.setMacFactories(macFactories);
        }
        
        return server;
    }

    private File getHostKeyFile(Resource hostKeyResource) throws IOException {
        // For host key files, we need to handle the case where the file doesn't exist yet
        // The SimpleGeneratorHostKeyProvider will create it if needed
        if (!hostKeyResource.exists()) {
            // If the resource doesn't exist but is a file:// URL, return the File object
            // so SimpleGeneratorHostKeyProvider can create it
            if ("file".equals(hostKeyResource.getURL().getProtocol())) {
                return hostKeyResource.getFile();
            } else {
                // For non-file resources that don't exist, throw an exception
                throw new IOException("Host key file resource does not exist and cannot be created: " + hostKeyResource);
            }
        }
        // If the resource exists, use the regular getFile method
        return getFile(hostKeyResource);
    }

    private File getFile(Resource authorizedPublicKeys) throws IOException {
        if ("file".equals(authorizedPublicKeys.getURL().getProtocol())) {
            return authorizedPublicKeys.getFile();
        } else {
            File tmp = Files.createTempFile("sshShellPubKeys-", ".tmp").toFile();
            try (InputStream is = authorizedPublicKeys.getInputStream();
                 OutputStream os = Files.newOutputStream(tmp.toPath())) {
                IoUtils.copy(is, os);
            }
            tmp.deleteOnExit();
            LOGGER.info("Copying {} to following temporary file : {}", authorizedPublicKeys, tmp.getAbsolutePath());
            return tmp;
        }
    }

    /**
     * Ssh server lifecycle class used to start and stop ssh server
     */
    @RequiredArgsConstructor
    public static class SshServerLifecycle {

        private final SshServer sshServer;

        private final SshShellProperties properties;

        /**
         * Start ssh server
         *
         * @throws IOException in case of error
         */
        @PostConstruct
        public void startServer() throws IOException {
            sshServer.start();
            LOGGER.info("Ssh server started [{}:{}]", properties.getHost(), properties.getPort());
        }

        /**
         * Stop ssh server
         *
         * @throws IOException in case of error
         */
        @PreDestroy
        public void stopServer() throws IOException {
            sshServer.stop();
        }
    }
}
