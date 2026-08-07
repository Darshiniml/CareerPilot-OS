package com.careerpilot.sdk.auth;

public interface AuthenticationProvider {

    /**
     * Authenticates the given AuthRequest.
     * @param request Authentication credentials
     * @return Result containing authentication details
     */
    AuthResponse authenticate(AuthRequest request);

    /**
     * Checks if this provider supports the given authentication method.
     */
    boolean supports(AuthMethod method);
}
