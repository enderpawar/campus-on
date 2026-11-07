package com.campuson.api.eclass;

public interface EclassGateway {
    EclassSession authenticate(String studentId, String password);
    EclassSnapshot fetch(String sessionCookie);
}
