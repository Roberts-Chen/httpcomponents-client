/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.fluent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.compat.ClassicToAsyncAdaptor;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Executor for {@link Request}s.
 * <p>
 * A connection pool with maximum 100 connections per route and
 * a total maximum of 200 connections is used internally.
 *
 * @since 4.2
 */
public class Executor {

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static volatile CloseableHttpClient CLIENT;
    private static volatile CloseableHttpClient ASYNC_CLIENT;

    static CloseableHttpClient GET_CLASSIC_CLIENT() {
        final CloseableHttpClient client = CLIENT;
        if (client != null) {
            return client;
        }
        LOCK.lock();
        try {
            if (CLIENT == null) {
                CLIENT = HttpClientBuilder.create()
                        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                                .useSystemProperties()
                                .setMaxConnPerRoute(100)
                                .setMaxConnTotal(200)
                                .setDefaultConnectionConfig(ConnectionConfig.custom()
                                        .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                        .build())
                                .build())
                        .useSystemProperties()
                        .evictExpiredConnections()
                        .evictIdleConnections(TimeValue.ofMinutes(1))
                        .build();
            }
            return CLIENT;
        } finally {
            LOCK.unlock();
        }
    }

    static CloseableHttpClient GET_ASYNC_CLIENT() {
        final CloseableHttpClient client = ASYNC_CLIENT;
        if (client != null) {
            return client;
        }
        LOCK.lock();
        try {
            if (ASYNC_CLIENT == null) {
                ASYNC_CLIENT = new ClassicToAsyncAdaptor(HttpAsyncClientBuilder.create()
                        .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                                .useSystemProperties()
                                .setMaxConnPerRoute(100)
                                .setMaxConnTotal(200)
                                .setMessageMultiplexing(true)
                                .setDefaultConnectionConfig(ConnectionConfig.custom()
                                        .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                        .build())
                                .build())
                        .useSystemProperties()
                        .evictExpiredConnections()
                        .evictIdleConnections(TimeValue.ofMinutes(1))
                        .build(), Timeout.ofMinutes(5));
            }
            return ASYNC_CLIENT;
        } finally {
            LOCK.unlock();
        }
    }

    public static Executor newInstance() {
        return new Executor(GET_CLASSIC_CLIENT());
    }

    public static Executor newInstance(final CloseableHttpClient httpclient) {
        return new Executor(httpclient != null ? httpclient : GET_CLASSIC_CLIENT());
    }

    /**
     * This feature is considered experimental and may be discontinued in the future.
     * @since 5.5
     */
    @Experimental
    public static Executor newInstance(final CloseableHttpAsyncClient httpclient) {
        return new Executor(httpclient != null ? new ClassicToAsyncAdaptor(httpclient, Timeout.ofMinutes(5)) : GET_ASYNC_CLIENT());
    }

    private final CloseableHttpClient httpclient;
    private final AuthCache authCache;
    private volatile CredentialsStore credentialsStore;
    private volatile CookieStore cookieStore;

    Executor(final CloseableHttpClient httpclient) {
        super();
        this.httpclient = httpclient;
        this.authCache = new BasicAuthCache();
    }

    /**
     * @return this instance.
     * @since 4.5
     */
    public Executor use(final CredentialsStore credentialsStore) {
        this.credentialsStore = credentialsStore;
        return this;
    }

    public Executor auth(final AuthScope authScope, final Credentials credentials) {
        CredentialsStore credentialsStoreSnapshot = credentialsStore;
        if (credentialsStoreSnapshot == null) {
            credentialsStoreSnapshot = new BasicCredentialsProvider();
            this.credentialsStore = credentialsStoreSnapshot;
        }
        credentialsStoreSnapshot.setCredentials(authScope, credentials);
        return this;
    }

    public Executor auth(final HttpHost host, final Credentials credentials) {
        return auth(new AuthScope(host), credentials);
    }

    /**
     * @return this instance.
     * @since 4.4
     */
    public Executor auth(final String host, final Credentials credentials) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(host);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return auth(httpHost, credentials);
    }

    public Executor authPreemptive(final HttpHost host) {
        final CredentialsStore credentialsStoreSnapshot = credentialsStore;
        if (credentialsStoreSnapshot != null) {
            final Credentials credentials = credentialsStoreSnapshot.getCredentials(new AuthScope(host), null);
            if (credentials != null) {
                final BasicScheme basicScheme = new BasicScheme();
                basicScheme.initPreemptive(credentials);
                this.authCache.put(host, basicScheme);
            }
        }
        return this;
    }

    /**
     * @return this instance.
     * @since 4.4
     */
    public Executor authPreemptive(final String host) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(host);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return authPreemptive(httpHost);
    }

    public Executor authPreemptiveProxy(final HttpHost proxy) {
        final CredentialsStore credentialsStoreSnapshot = credentialsStore;
        if (credentialsStoreSnapshot != null) {
            final Credentials credentials = credentialsStoreSnapshot.getCredentials(new AuthScope(proxy), null);
            if (credentials != null) {
                final BasicScheme basicScheme = new BasicScheme();
                basicScheme.initPreemptive(credentials);
                this.authCache.put(proxy, basicScheme);
            }
        }
        return this;
    }

    /**
     * @return this instance.
     * @since 4.4
     */
    public Executor authPreemptiveProxy(final String proxy) {
        final HttpHost httpHost;
        try {
            httpHost = HttpHost.create(proxy);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host: " + proxy);
        }
        return authPreemptiveProxy(httpHost);
    }

    public Executor auth(final HttpHost host,
            final String username, final char[] password) {
        return auth(host, new UsernamePasswordCredentials(username, password));
    }

    /**
     * @return this instance.
     * @deprecated Use {@link #auth(HttpHost, String, char[])}.
     */
    @Deprecated
    public Executor auth(final HttpHost host,
            final String username, final char[] password,
            final String workstation, final String domain) {
        return auth(host, new org.apache.hc.client5.http.auth.NTCredentials(username, password, workstation, domain));
    }

    public Executor clearAuth() {
        final CredentialsStore credentialsStoreSnapshot = credentialsStore;
        if (credentialsStoreSnapshot != null) {
            credentialsStoreSnapshot.clear();
        }
        return this;
    }

    /**
     * @return this instance.
     * @since 4.5
     */
    public Executor use(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public Executor clearCookies() {
        final CookieStore cookieStoreSnapshot = cookieStore;
        if (cookieStoreSnapshot != null) {
            cookieStoreSnapshot.clear();
        }
        return this;
    }

    /**
     * Executes the request. Please Note that response content must be processed
     * or discarded using {@link Response#discardContent()}, otherwise the
     * connection used for the request might not be released to the pool.
     *
     * @see Response#handleResponse(org.apache.hc.core5.http.io.HttpClientResponseHandler)
     * @see Response#discardContent()
     */
    public Response execute(
            final Request request) throws IOException {
        final HttpClientContext localContext = HttpClientContext.create();
        final CredentialsStore credentialsStoreSnapshot = credentialsStore;
        if (credentialsStoreSnapshot != null) {
            localContext.setCredentialsProvider(credentialsStoreSnapshot);
        }
        if (this.authCache != null) {
            localContext.setAuthCache(this.authCache);
        }
        final CookieStore cookieStoreSnapshot = cookieStore;
        if (cookieStoreSnapshot != null) {
            localContext.setCookieStore(cookieStoreSnapshot);
        }
        return new Response(request.internalExecute(this.httpclient, localContext));
    }

}
