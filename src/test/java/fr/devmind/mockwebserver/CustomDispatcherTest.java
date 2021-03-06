/*
 * Copyright (C) 2012 Google Inc.
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
package fr.devmind.mockwebserver;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


public class CustomDispatcherTest {
    private MockWebServer mockWebServer = new MockWebServer();

    @AfterEach
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    public void simpleDispatch() throws Exception {
        mockWebServer.start();
        final List<RecordedRequest> requestsMade = new ArrayList<>();
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requestsMade.add(request);
                return new MockResponse();
            }
        };
        Assertions.assertThat(requestsMade).isEmpty();
        mockWebServer.setDispatcher(dispatcher);
        final URL url = mockWebServer.url("/").url();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.getResponseCode();

        // Force the connection to hit the "server".
        // Make sure our dispatcher got the request.*
        Assertions.assertThat(requestsMade).hasSize(1);

    }

    @Test
    public void outOfOrderResponses() throws Exception {
        AtomicInteger firstResponseCode = new AtomicInteger();
        AtomicInteger secondResponseCode = new AtomicInteger();
        mockWebServer.start();
        final String secondRequest = "/bar";
        final String firstRequest = "/foo";
        final CountDownLatch latch = new CountDownLatch(1);
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals(firstRequest)) {
                    latch.await();
                }
                return new MockResponse();
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        final Thread startsFirst = buildRequestThread(firstRequest, firstResponseCode);
        startsFirst.start();
        final Thread endsFirst = buildRequestThread(secondRequest, secondResponseCode);
        endsFirst.start();
        endsFirst.join();
        // First response is still waiting.
        Assertions.assertThat(firstResponseCode.get()).isEqualTo(0);
        // Second response is done.
        Assertions.assertThat(secondResponseCode.get()).isEqualTo(200);
        latch.countDown();
        startsFirst.join();
        // And now it's done!
        Assertions.assertThat(firstResponseCode.get()).isEqualTo(200);
        // (Still done).
        Assertions.assertThat(secondResponseCode.get()).isEqualTo(200);
    }

    private Thread buildRequestThread(final String path, final AtomicInteger responseCode) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                final URL url = mockWebServer.url(path).url();
                final HttpURLConnection conn;
                try {
                    conn = (HttpURLConnection) url.openConnection();
                    responseCode.set(conn.getResponseCode()); // Force the connection to hit the "server".
                }
                catch (IOException e) {
                }
            }
        });
    }
}
