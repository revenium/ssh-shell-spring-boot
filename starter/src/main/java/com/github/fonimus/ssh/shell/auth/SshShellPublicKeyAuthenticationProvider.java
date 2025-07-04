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

package com.github.fonimus.ssh.shell.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.io.File;
import java.security.PublicKey;

import static com.github.fonimus.ssh.shell.auth.SshShellAuthenticationProvider.AUTHENTICATION_ATTRIBUTE;

/**
 * Authorized keys authenticator extension to set authentication attribute
 */
@Slf4j
public class SshShellPublicKeyAuthenticationProvider
        extends AuthorizedKeysAuthenticator {

    /**
     * Default constructor
     *
     * @param publicKeysFile public keys file
     */
    public SshShellPublicKeyAuthenticationProvider(File publicKeysFile) {
        super(publicKeysFile.toPath());
    }

    @Override
    public boolean authenticate(String username, PublicKey key, ServerSession session) {
        LOGGER.info("Attempting public key authentication for user: {}", username);
        LOGGER.info("Client public key - Algorithm: {}, Format: {}", key.getAlgorithm(), key.getFormat());
        
        // Log the public key details for debugging
        try {
            String keyString = java.util.Base64.getEncoder().encodeToString(key.getEncoded());
            LOGGER.info("Client public key (base64): {} {}", key.getAlgorithm(), keyString);
            
            // Convert to OpenSSH format for comparison
            String opensshFormat = convertToOpenSSHFormat(key);
            LOGGER.info("Client public key (OpenSSH format): {}", opensshFormat);
        } catch (Exception e) {
            LOGGER.warn("Could not encode public key for logging: {}", e.getMessage());
        }
        
        boolean authenticated = super.authenticate(username, key, session);
        
        LOGGER.info("Public key authentication result for user {}: {}", username, authenticated);
        
        if (authenticated) {
            session.getIoSession().setAttribute(AUTHENTICATION_ATTRIBUTE, new SshAuthentication(username, username));
            LOGGER.info("Successfully authenticated user {} with public key", username);
        } else {
            LOGGER.warn("Public key authentication failed for user: {} - key not found in authorized keys file", username);
        }
        return authenticated;
    }
    
    private String convertToOpenSSHFormat(PublicKey key) {
        try {
            if (key.getAlgorithm().equals("RSA")) {
                // For RSA keys, convert X.509 format to OpenSSH format
                String base64Key = java.util.Base64.getEncoder().encodeToString(key.getEncoded());
                return "ssh-rsa " + base64Key;
            } else if (key.getAlgorithm().equals("DSA")) {
                String base64Key = java.util.Base64.getEncoder().encodeToString(key.getEncoded());
                return "ssh-dss " + base64Key;
            } else if (key.getAlgorithm().equals("EC")) {
                String base64Key = java.util.Base64.getEncoder().encodeToString(key.getEncoded());
                return "ecdsa-sha2-nistp256 " + base64Key; // This might need adjustment based on curve
            }
            return "unknown-format " + java.util.Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            return "conversion-failed: " + e.getMessage();
        }
    }
}
