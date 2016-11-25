/**
 * Copyright (C) 2016 David Clark
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.net.http;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static groovyx.net.http.NativeHandlers.Parsers.transfer;

/**
 * `HttpBuilder` implementation based on the {@link HttpURLConnection} class.
 *
 * Generally, this class should not be used directly, the preferred method of instantiation is via the
 * `groovyx.net.http.HttpBuilder.configure(java.util.function.Function)` or
 * `groovyx.net.http.HttpBuilder.configure(java.util.function.Function, groovy.lang.Closure)` methods.
 */
public class JavaHttpBuilder extends HttpBuilder {

    protected class Action {

        private final HttpURLConnection connection;
        private final ChainedHttpConfig requestConfig;
        private final String verb;

        public Action(final ChainedHttpConfig requestConfig, final String verb) {
            try {
                this.requestConfig = requestConfig;
                final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
                this.connection = (HttpURLConnection) cr.getUri().toURI().toURL().openConnection();
                this.verb = verb;
                this.connection.setRequestMethod(verb);

                if(cr.actualBody() != null) {
                    this.connection.setDoOutput(true);
                }
            }
            catch(IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void addHeaders() {
            final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
            for(Map.Entry<String,String> entry : cr.actualHeaders(new LinkedHashMap<>()).entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }

            final String contentType = cr.actualContentType();
            if(contentType != null) {
                connection.addRequestProperty("Content-Type", contentType);
            }

            connection.addRequestProperty("Accept-Encoding", "gzip, deflate");

            final URI uri = cr.getUri().toURI();
            final List<Cookie> cookies = cr.actualCookies(new ArrayList<>());
            for(Cookie cookie : cookies) {
                final HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
                httpCookie.setVersion(clienConfig.getCookieVersion());
                httpCookie.setDomain(uri.getHost());
                httpCookie.setPath(uri.getPath());
                if(cookie.getExpires() != null) {
                    final long diff = cookie.getExpires().getTime() - System.currentTimeMillis();
                    httpCookie.setMaxAge(diff / 1_000L);
                }
                else {
                    httpCookie.setMaxAge(3_600L);
                }

                globalCookieManager.getCookieStore().add(uri, httpCookie);
            }
        }

        private PasswordAuthentication getAuthInfo() {
            final HttpConfig.Auth auth = requestConfig.getChainedRequest().actualAuth();
            if(auth == null) {
                return null;
            }

            if(auth.getAuthType() == HttpConfig.AuthType.BASIC || auth.getAuthType() == HttpConfig.AuthType.DIGEST) {
                return new PasswordAuthentication(auth.getUser(), auth.getPassword().toCharArray());
            }
            else {
                throw new UnsupportedOperationException("HttpURLConnection does not support " + auth.getAuthType() + " authentication");
            }
        }

        private void handleToServer() {
            final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
            final Object body = cr.actualBody();
            if(body != null) {
                requestConfig.findEncoder().accept(requestConfig, new JavaToServer());
            }
        }

        private Object handleFromServer() {
            final JavaFromServer fromServer = new JavaFromServer(requestConfig.getChainedRequest().getUri().toURI());
            try {
                final BiFunction<ChainedHttpConfig,FromServer,Object> parser = requestConfig.findParser(fromServer.getContentType());
                final BiFunction<FromServer, Object, ?> action = requestConfig.getChainedResponse().actualAction(fromServer.getStatusCode());

                return action.apply(fromServer, fromServer.getHasBody() ? parser.apply(requestConfig, fromServer) : null );
            }
            finally {
                fromServer.finish();
            }
        }

        public Object execute() {
            try {
                addHeaders();
                return ThreadLocalAuth.with(getAuthInfo(), () -> {
                        // FIXME: this seems to enforce HTTPS for auth - while a good practice, may not want to force
                        if(sslContext != null) {
                            HttpsURLConnection https = (HttpsURLConnection) connection;
                            https.setSSLSocketFactory(sslContext.getSocketFactory());
                        }

                        connection.connect();
                        handleToServer();
                        return handleFromServer();
                    });
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        protected class JavaToServer implements ToServer {

            public void toServer(final InputStream inputStream) {
                try {
                    transfer(inputStream, connection.getOutputStream(), true);
                }
                catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        protected class JavaFromServer implements FromServer {

            private InputStream is;
            private boolean hasBody;
            private List<Header<?>> headers;
            private URI uri;

            public JavaFromServer(final URI originalUri) {
                this.uri = originalUri;
                //TODO: detect non success and read from error stream instead
                try {
                    headers = populateHeaders();
                    BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
                    bis.mark(0);
                    hasBody = bis.read() != -1;
                    bis.reset();
                    is = handleEncoding(bis);
                }
                catch(IOException e) {
                    //swallow, no body is present?
                    is = null;
                    hasBody = false;
                }
            }

            private InputStream handleEncoding(final InputStream is) throws IOException {
                Header<?> encodingHeader = Header.find(headers, "Content-Encoding");
                if(encodingHeader != null) {
                    if(encodingHeader.getValue().equals("gzip")) {
                        return new GZIPInputStream(is);
                    }
                    else if(encodingHeader.getValue().equals("deflate")) {
                        return new InflaterInputStream(is);
                    }
                }

                return is;
            }

            private List<Header<?>> populateHeaders() {
                final List<Header<?>> ret = new ArrayList<>();
                for(int i = 0; i < Integer.MAX_VALUE; ++i) {
                    final String key = connection.getHeaderFieldKey(i);
                    final String value = connection.getHeaderField(i);
                    if(key == null && value == null) {
                        break;
                    }

                    if(key != null && value != null) {
                        ret.add(Header.keyValue(key, value));
                    }
                }

                return Collections.unmodifiableList(ret);
            }

            public InputStream getInputStream() {
                return is;
            }

            public int getStatusCode() {
                try {
                    return connection.getResponseCode();
                }
                catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public String getMessage() {
                try {
                    return connection.getResponseMessage();
                }
                catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public List<Header<?>> getHeaders() {
                return headers;
            }

            public boolean getHasBody() {
                return hasBody;
            }

            public URI getUri() {
                return uri;
            }

            public void finish() {
                //do nothing, should auto cleanup
            }
        }
    }

    protected static class ThreadLocalAuth extends Authenticator {
        private static final ThreadLocal<PasswordAuthentication> tlAuth = new ThreadLocal<PasswordAuthentication>();

        public PasswordAuthentication getPasswordAuthentication() {
            return tlAuth.get();
        }

        public static final <V> V with(final PasswordAuthentication pa, final Callable<V> callable) throws Exception {
            tlAuth.set(pa);
            try {
                return callable.call();
            }
            finally {
                tlAuth.set(null);
            }
        }
    }

    private final static CookieManager globalCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    static {
        Authenticator.setDefault(new ThreadLocalAuth());
        CookieHandler.setDefault(globalCookieManager);
    }

    final private ChainedHttpConfig config;
    final private Executor executor;
    final private SSLContext sslContext;
    final private HttpObjectConfig.Client clienConfig;

    public JavaHttpBuilder(final HttpObjectConfig config) {
        super(config);
        this.config = new HttpConfigs.ThreadSafeHttpConfig(config.getChainedConfig());
        this.executor = config.getExecution().getExecutor();
        this.sslContext = config.getExecution().getSslContext();
        this.clienConfig = config.getClient();
    }

    protected ChainedHttpConfig getObjectConfig() {
        return config;
    }

    protected Object doGet(final ChainedHttpConfig requestConfig) {
        return new Action(requestConfig, "GET").execute();
    }

    protected Object doHead(final ChainedHttpConfig requestConfig) {
        return new Action(requestConfig, "HEAD").execute();
    }

    protected Object doPost(final ChainedHttpConfig requestConfig) {
        return new Action(requestConfig, "POST").execute();
    }

    protected Object doPut(final ChainedHttpConfig requestConfig) {
        return new Action(requestConfig, "PUT").execute();
    }

    protected Object doDelete(final ChainedHttpConfig requestConfig) {
        return new Action(requestConfig, "DELETE").execute();
    }

    public Executor getExecutor() {
        return executor;
    }

    public void close() {
        throw new UnsupportedOperationException();
    }
}
