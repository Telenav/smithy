/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.blog.server.spi.impl;

import com.google.inject.Inject;
import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.telenav.smithy.http.AuthenticationResultConsumer;
import static com.telenav.smithy.http.HeaderTypes.headerTypes;
import com.telenav.smithy.http.ResponseException;
import com.telenav.smithy.http.SmithyRequest;
import com.telenav.blog.auth.AuthenticateWithAuthUser;
import com.telenav.blog.auth.BlogServiceAuthenticatedOperations;
import com.telenav.blog.model.AuthUser;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class AuthImpl implements AuthenticateWithAuthUser {

    private final String userName;
    private final String password;

    @Inject
    AuthImpl() {
        userName = "admin";
        password = "password";
    }

    @Override
    public void authenticate(BlogServiceAuthenticatedOperations target, SmithyRequest request,
            boolean optional, AuthenticationResultConsumer<AuthUser> results) {
        Optional<BasicCredentials> authInfo = request.header(headerTypes().basicAuth());
        System.out.println("AUTHENTICATE " + request.httpMethod() + " " + request.requestUri() 
                +  " - credentials: " + authInfo);
        if (optional && !authInfo.isPresent()) {
            System.out.println("  optional auth, no credentials");
            results.ok();
        } else if (!authInfo.isPresent()) {
            System.out.println("No auth info and it is required");
            System.out.println("CALL FAILED");
            results.failed(new ResponseException(401, "Not authenticated", "WWW-Authenticate", "basic realm=blogs"));
//            results.unauthorized();
        } else {
            BasicCredentials creds = authInfo.get();
            System.out.println("CREDENTIALS: '" + creds.username + "' password '" + creds.password + "'");
            if (!userName.equals(creds.username) || !password.equals(creds.password)) {
                System.out.println("CALL FORBIDDEN");
                results.forbidden();
            } else {
                System.out.println("CALL OK");
                results.ok(new AuthUser(creds.username));
            }
        }
    }
}
