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

import org.jline.terminal.Attributes;
import org.jline.terminal.impl.DumbTerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSH Terminal implementation for Docker/container environments
 * Extends DumbTerminal but with proper SSH configuration and echo handling
 */
public class SshTerminal extends DumbTerminal {
    
    private final boolean forceEcho = true;
    
    public SshTerminal(InputStream input, OutputStream output) throws IOException {
        super(input, output);
        configureForSsh();
    }
    
    public SshTerminal(String name, String type, InputStream input, OutputStream output) throws IOException {
        super(name, type, input, output, StandardCharsets.UTF_8);
        configureForSsh();
    }
    
    private void configureForSsh() {
        // Configure attributes for SSH with FORCED echo
        Attributes attr = new Attributes();
        
        // Input flags
        attr.setInputFlag(Attributes.InputFlag.ICRNL, true);  // Map CR to NL on input
        attr.setInputFlag(Attributes.InputFlag.INLCR, false); // Don't map NL to CR
        attr.setInputFlag(Attributes.InputFlag.IGNCR, false); // Don't ignore CR
        attr.setInputFlag(Attributes.InputFlag.IXON, false);  // Disable XON/XOFF
        
        // Output flags  
        attr.setOutputFlag(Attributes.OutputFlag.OPOST, true); // Enable output processing
        attr.setOutputFlag(Attributes.OutputFlag.ONLCR, true); // Map NL to CR-NL on output
        attr.setOutputFlag(Attributes.OutputFlag.OCRNL, false);
        attr.setOutputFlag(Attributes.OutputFlag.ONOCR, false);
        attr.setOutputFlag(Attributes.OutputFlag.ONLRET, false);
        
        // Control flags
        attr.setControlFlag(Attributes.ControlFlag.CREAD, true);
        attr.setControlFlag(Attributes.ControlFlag.CS8, true);
        
        // Local flags - FORCE ECHO ON
        attr.setLocalFlag(Attributes.LocalFlag.ECHO, true);       // Echo input characters
        attr.setLocalFlag(Attributes.LocalFlag.ICANON, true);     // Canonical mode (line editing)
        attr.setLocalFlag(Attributes.LocalFlag.ISIG, true);       // Enable signals
        attr.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);    // Disable extended - can interfere
        attr.setLocalFlag(Attributes.LocalFlag.ECHOE, true);      // Echo erase
        attr.setLocalFlag(Attributes.LocalFlag.ECHOK, true);      // Echo kill
        attr.setLocalFlag(Attributes.LocalFlag.ECHONL, false);    // Don't echo NL
        attr.setLocalFlag(Attributes.LocalFlag.ECHOCTL, false);   // Don't echo control chars as ^X
        
        // Control characters
        attr.setControlChar(Attributes.ControlChar.VEOF, 4);      // Ctrl-D
        attr.setControlChar(Attributes.ControlChar.VEOL, -1);     
        attr.setControlChar(Attributes.ControlChar.VEOL2, -1);
        attr.setControlChar(Attributes.ControlChar.VERASE, 127);  // Backspace
        attr.setControlChar(Attributes.ControlChar.VKILL, 21);    // Ctrl-U
        attr.setControlChar(Attributes.ControlChar.VINTR, 3);     // Ctrl-C
        attr.setControlChar(Attributes.ControlChar.VQUIT, 28);    // Ctrl-\
        attr.setControlChar(Attributes.ControlChar.VSUSP, 26);    // Ctrl-Z
        attr.setControlChar(Attributes.ControlChar.VMIN, 1);
        attr.setControlChar(Attributes.ControlChar.VTIME, 0);
        
        setAttributes(attr);
    }
    
    @Override
    public boolean echo() {
        // Always return true to force echo
        return forceEcho || super.echo();
    }
    
    @Override  
    public boolean echo(boolean echo) {
        // Always enable echo for SSH
        return super.echo(true);
    }
}