/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8295785
 * @library /test/lib
 * @modules jdk.httpserver/sun.net.httpserver
 * @build jdk.httpserver/sun.net.httpserver.HttpServerAccess IdleIntervalTest
 * @run junit/othervm -Dsun.net.httpserver.idleInterval=6 IdleIntervalTest
 * @run junit/othervm IdleIntervalTest
 */

import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import sun.net.httpserver.HttpServerAccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IdleIntervalTest {

    HttpServer server;
    static final int DEFAULT_IDLE_INTERVAL_IN_SECS = 30;
    int idleInterval, numberOfRequests;
    CountDownLatch reqFinishedProcessing;

    @BeforeAll
    void before() throws Exception {
        idleInterval = Integer.getInteger("sun.net.httpserver.idleInterval", DEFAULT_IDLE_INTERVAL_IN_SECS);
        numberOfRequests = 2;
        reqFinishedProcessing = new CountDownLatch(2);
        server = startServer(reqFinishedProcessing);
    }

    @AfterAll
    void after() throws Exception {
        server.stop(0);
    }

    @Test
    public void test() throws Exception {

        try (final ExecutorService requestIssuer = Executors.newFixedThreadPool(numberOfRequests)) {
            final int port = server.getAddress().getPort();
            // issue requests
            for (int i = 1; i <= numberOfRequests; i++) {
                URL requestURL = URIBuilder.newBuilder()
                        .scheme("http")
                        .loopback()
                        .port(port)
                        .path("/IdleIntervalTest/" + i)
                        .build()
                        .toURL();
                requestIssuer.submit((Callable<Void>) () -> {
                    System.out.println("Issuing request " + requestURL);
                    final URLConnection conn = requestURL.openConnection();
                    try (final InputStream is = conn.getInputStream()) {
                        is.readAllBytes();
                    }
                    return null;
                });
            }

            // wait for all these issues requests to reach each of the handlers
            System.out.println("Waiting for all " + numberOfRequests + " requests to reach" +
                    " the server side request handler");
            reqFinishedProcessing.await();
        }

        int idleConnectionCount = HttpServerAccess.getIdleConnectionCount(server);

        //assert that there are 2 idle connections currently
        assertEquals(numberOfRequests, idleConnectionCount);

        // sleep for the length of the idle interval, multiplied by 1000 as idleInterval is set
        // in seconds, not milli
        Thread.sleep(idleInterval * 1000);

        idleConnectionCount = HttpServerAccess.getIdleConnectionCount(server);

        // assert that there are 0 connections after the sleep, if there are still 1 or more,
        // there may be a race issue condition so sleep thread for another 10 percent
        // and retry before throwing an exception
        if (idleConnectionCount != 0) {
            System.out.println("didn't close immediately after timeout expired, sleeping for an additional 10%");
            Thread.sleep(idleInterval * 100);
        }

        idleConnectionCount = HttpServerAccess.getIdleConnectionCount(server);
        assertEquals(0, idleConnectionCount);
    }

    // Create HttpServer that will handle requests with multiple threads
    private static HttpServer startServer(final CountDownLatch reqFinishedProcessing) throws IOException {
        final var bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final HttpServer server = HttpServer.create(bindAddr, 0);

        final AtomicInteger threadId = new AtomicInteger();
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r);
            t.setName("http-request-handler-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/IdleIntervalTest/", (exchange) -> {
            System.out.println("Request " + exchange.getRequestURI() + " received");
            System.out.println("Sending response for request " + exchange.getRequestURI() +
                               " from " + exchange.getRemoteAddress());
            reqFinishedProcessing.countDown();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Server started at address " + server.getAddress());
        return server;
    }
}
