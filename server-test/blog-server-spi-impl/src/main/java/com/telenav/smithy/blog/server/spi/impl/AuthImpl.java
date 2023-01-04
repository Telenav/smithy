/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.blog.server.spi.impl;

import com.google.inject.Inject;
import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.smithy.http.AuthenticationResultConsumer;
import static com.mastfrog.smithy.http.HeaderTypes.headerTypes;
import com.mastfrog.smithy.http.ResponseException;
import com.mastfrog.smithy.http.SmithyRequest;
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
