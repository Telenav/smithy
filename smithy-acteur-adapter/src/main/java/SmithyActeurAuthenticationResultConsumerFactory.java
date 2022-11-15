
import com.mastfrog.acteur.errors.ResponseException;
import com.mastfrog.smithy.http.AuthenticationResultConsumer;
import com.mastfrog.smithy.http.AuthenticationResultConsumerFactory;
import com.mastfrog.util.service.ServiceProvider;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import java.util.concurrent.CompletableFuture;

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
/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(AuthenticationResultConsumerFactory.class)
public final class SmithyActeurAuthenticationResultConsumerFactory extends AuthenticationResultConsumerFactory {

    @Override
    protected <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut, boolean optional) {
        return new ConsumerImpl<T>(fut, optional);
    }

    private static class ConsumerImpl<T> extends AbstractAuthenticationResultConsumer<T> {

        ConsumerImpl(CompletableFuture<T> fut, boolean optional) {
            super(fut, optional);
        }

        @Override
        public void unauthorized() {
            failed(new ResponseException(UNAUTHORIZED, "Unauthorized"));
        }

        @Override
        public void forbidden() {
            failed(new ResponseException(FORBIDDEN, "Unauthorized"));
        }
    }

}
