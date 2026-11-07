package com.campuson.api.eclass;

import java.time.Instant;

/** No raw upstream password, cookie, or upstream error body crosses this boundary. */
public record EclassConnectionResponse(String state, Instant lastSyncedAt, String lastErrorCode,
                                       boolean credentialsRemembered) {
    static EclassConnectionResponse from(EclassConnection connection) {
        return new EclassConnectionResponse(connection.getSyncState(), connection.getLastSyncedAt(),
                connection.getLastErrorCode(), connection.getEncryptedPassword() != null);
    }
}
