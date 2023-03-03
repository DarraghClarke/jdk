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
 * @build jdk.httpserver/sun.net.httpserver.HttpServerAccess DrainAmountTest
 * @run junit/othervm -Dsun.net.httpserver.drainAmount=300  DrainAmountTest
 * @run junit/othervm DrainAmountTest
 */

import com.sun.net.httpserver.HttpServer;
import sun.net.httpserver.HttpServerAccess;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DrainAmountTest {

    private static final int DEFAULT_DRAIN_AMOUNT = 64 * 1024;
    int drainAmount, port;
    HttpServer server;
    HttpClient client;
    URI uri;

    @BeforeAll
    void before() throws Exception {
        drainAmount = Integer.getInteger("sun.net.httpserver.drainAmount", DEFAULT_DRAIN_AMOUNT);
        server = startServer();
        port = server.getAddress().getPort();
        client = HttpClient
                .newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();

        uri = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path("/DrainAmountTest/")
                .build();
    }

    @AfterAll
    void after() throws Exception {
        server.stop(0);
    }

    // Requests with an unread amount less than sun.net.httpserver.drainAmount will join
    // the idle connection Set
    @Test
    public void expectSmallRequestsToIdle() throws Exception {

        byte[] post = generatePostBody(drainAmount - 1);

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofByteArray(post))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());

        // assert that there is 1 idle connection as the remaining bytes was under the drain amount
        int idleConnectionCount = HttpServerAccess.getIdleConnectionCount(server);
        assertEquals(1, idleConnectionCount);
    }

    // Requests with an unread amount greater than sun.net.httpserver.drainAmount will be closed
    @Test
    public void expectLargeRequestsToClose() throws Exception {
        byte[] oversizedPost = generatePostBody(drainAmount + 1);

        HttpRequest oversizedRequest = HttpRequest
                .newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofByteArray(oversizedPost))
                .build();

        client.send(oversizedRequest, HttpResponse.BodyHandlers.ofString());

        // assert that the connection was closed as it was over the drain amount
        int totalConnectionCount = HttpServerAccess.getTotalConnectionCount(server);
        assertEquals(0, totalConnectionCount);
    }

    public byte[] generatePostBody(int size) {
        byte[] array = new byte[size];
        new Random().nextBytes(array);
        return array;
    }

    // Create HttpServer that will handle requests with multiple threads
    private static HttpServer startServer() throws IOException {
        final var bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final HttpServer server = HttpServer.create(bindAddr, 0);

        final AtomicInteger threadId = new AtomicInteger();
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r);
            t.setName("http-request-handler-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/DrainAmountTest/", (exchange) -> {
            System.out.println("Request " + exchange.getRequestURI() + " received");
            System.out.println("Sending response for request " + exchange.getRequestURI() +
                               " from " + exchange.getRemoteAddress());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Server started at address " + server.getAddress());
        return server;
    }
}
