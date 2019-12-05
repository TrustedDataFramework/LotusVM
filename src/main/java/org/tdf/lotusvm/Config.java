package org.tdf.lotusvm;

import lombok.*;
import org.tdf.lotusvm.runtime.Hook;
import org.tdf.lotusvm.runtime.HostFunction;

import java.util.Collections;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Config {
    @Builder.Default
    private boolean initGlobals = true;
    @Builder.Default
    private boolean initMemory = true;
    @Builder.Default
    private Set<HostFunction> hostFunctions = Collections.emptySet();
    @Builder.Default
    private Set<Hook> hooks = Collections.emptySet();

    private byte[] binary;
    private long[] globals;
    private byte[] memory;
}
