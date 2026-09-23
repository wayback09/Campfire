// Pure-Java invite ticket model shared by mod + backend (docs/protocol.md).
package com.wayback09.campfire;

import java.time.Instant;
import java.util.UUID;

/** Author: wayback09. */
public record InviteTicket(String code, UUID hostUuid, Instant expiresAt, int relaySlot) {
    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
