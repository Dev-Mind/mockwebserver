/*
 * Copyright (C) 2011 Google Inc.
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
package com.devmind.mockwebserver;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.internal.Util;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockWebServerExtension.class)
@DisplayName("Test MockWebServerExtension")
public final class MockWebServerExtensionTest {

    MockWebServerExtension server = new MockWebServerExtension();

    @Nested
    @DisplayName("Headers ")
    class TestMockResponseHeader {
        @Test
        @DisplayName("default mock response contains no header")
        public void defaultMockResponse() {
            MockResponse response = new MockResponse();
            assertThat(headersToList(response)).containsExactly("Content-Length: 0");
            assertThat(response.getStatus()).isEqualTo("HTTP/1.1 200 OK");
        }

        @Test
        @DisplayName("should return different code response with good message")
        public void setResponseMockReason() {
            String[] reasons = {
                    "Mock Response",
                    "Informational",
                    "OK",
                    "Redirection",
                    "Client Error",
                    "Server Error",
                    "Mock Response"
            };
            for (int i = 0; i < 600; i++) {
                MockResponse response = new MockResponse().setResponseCode(i);
                String expectedReason = reasons[i / 100];
                assertThat(response.getStatus()).isEqualTo("HTTP/1.1 " + i + " " + expectedReason);
                assertThat(headersToList(response)).containsExactly("Content-Length: 0");
            }
        }

        @Test
        @DisplayName("should set status control")
        public void setStatusControlsWholeStatusLine() {
            MockResponse response = new MockResponse().setStatus("HTTP/1.1 202 That'll do pig");
            assertThat(headersToList(response)).containsExactly("Content-Length: 0");
            assertThat(response.getStatus()).isEqualTo("HTTP/1.1 202 That'll do pig");
        }

        @Test
        @DisplayName("should adjuste header when body is set")
        public void setBodyAdjustsHeaders() throws IOException {
            MockResponse response = new MockResponse().setBody("ABC");
            assertThat(headersToList(response)).containsExactly("Content-Length: 3");
            assertThat(response.getBody().readUtf8()).isEqualTo("ABC");
        }

        @Test
        @DisplayName("should add headers")
        public void mockResponseAddHeader() {
            MockResponse response = new MockResponse()
                    .clearHeaders()
                    .addHeader("Cookie: s=square")
                    .addHeader("Cookie", "a=android");
            assertThat(headersToList(response)).containsExactly("Cookie: s=square", "Cookie: a=android");
        }

        @Test
        @DisplayName("should add cookies in header")
        public void mockResponseSetHeader() {
            MockResponse response = new MockResponse()
                    .clearHeaders()
                    .addHeader("Cookie: s=square")
                    .addHeader("Cookie: a=android")
                    .addHeader("Cookies: delicious");
            response.setHeader("cookie", "r=robot");
            assertThat(headersToList(response)).containsExactly("Cookies: delicious", "cookie: r=robot");
        }

        @Test
        @DisplayName("should add cookies in header")
        public void mockResponseSetHeaders() {
            MockResponse response = new MockResponse()
                    .clearHeaders()
                    .addHeader("Cookie: s=square")
                    .addHeader("Cookies: delicious");

            response.setHeaders(new Headers.Builder().add("Cookie", "a=android").build());
            assertThat(headersToList(response)).containsExactly("Cookie: a=android");
        }

        private List<String> headersToList(MockResponse response) {
            Headers headers = response.getHeaders();
            return IntStream.range(0, headers.size())
                    .mapToObj(i -> headers.name(i) + ": " + headers.value(i))
                    .collect(Collectors.toList());
        }
    }

    @Nested
    @DisplayName("Server ")
    class TestServer {
        @BeforeEach
        public void setUp() throws Exception {
            server.start();
        }

        @AfterEach
        public void tearDown() throws Exception {
            server.shutdown();
        }

        @Test
        @DisplayName("should return simple response with status and header")
        public void regularResponse() throws Exception {
            server.enqueue(new MockResponse().setBody("hello world"));

            URL url = server.url("/").url();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept-Language", "en-US");
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            assertThat(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(reader.readLine()).isEqualTo("hello world");

            RecordedRequest request = server.takeRequest();
            assertThat(request.getRequestLine()).isEqualTo("GET / HTTP/1.1");
            assertThat(request.getHeader("Accept-Language")).isEqualTo("en-US");
            server.shutdown();
        }


        @Test
        @DisplayName("should redirect request")
        public void redirect() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                    .addHeader("Location: " + server.url("/new-path"))
                    .setBody("This page has moved!"));
            server.enqueue(new MockResponse().setBody("This is the new location!"));

            URLConnection connection = server.url("/").url().openConnection();
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            assertThat(reader.readLine()).isEqualTo("This is the new location!");

            RecordedRequest first = server.takeRequest();
            assertThat(first.getRequestLine()).isEqualTo("GET / HTTP/1.1");
            RecordedRequest redirect = server.takeRequest();
            assertThat(redirect.getRequestLine()).isEqualTo("GET /new-path HTTP/1.1");
        }

        @Test
        @DisplayName("should blocks for a call to enqueue() if a request is made before a mock response is ready.")
        public void dispatchBlocksWaitingForEnqueue() throws Exception {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException ignored) {
                    }
                    server.enqueue(new MockResponse().setBody("enqueued in the background"));
                }
            }.start();

            URLConnection connection = server.url("/").url().openConnection();
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            assertThat(reader.readLine()).isEqualTo("enqueued in the background");
        }

        @Test
        @DisplayName("should fail when non hexadecimal chunk size")
        public void nonHexadecimalChunkSize() throws Exception {
            server.enqueue(new MockResponse()
                    .setBody("G\r\nxxxxxxxxxxxxxxxx\r\n0\r\n\r\n")
                    .clearHeaders()
                    .addHeader("Transfer-encoding: chunked"));

            URLConnection connection = server.url("/").url().openConnection();
            InputStream in = connection.getInputStream();

            assertThatThrownBy(() -> in.read()).isExactlyInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("should fail for timeout")
        public void responseTimeout() throws Exception {
            server.enqueue(new MockResponse()
                    .setBody("ABC")
                    .clearHeaders()
                    .addHeader("Content-Length: 4"));
            server.enqueue(new MockResponse().setBody("DEF"));

            URLConnection urlConnection = server.url("/").url().openConnection();
            urlConnection.setReadTimeout(1000);
            InputStream in = urlConnection.getInputStream();
            assertThat(in.read()).isEqualTo('A');
            assertThat(in.read()).isEqualTo('B');
            assertThat(in.read()).isEqualTo('C');

            // if Content-Length was accurate, this would return -1 immediately
            assertThatThrownBy(() -> in.read()).isExactlyInstanceOf(SocketTimeoutException.class);

            URLConnection urlConnection2 = server.url("/").url().openConnection();
            InputStream in2 = urlConnection2.getInputStream();
            assertThat(in2.read()).isEqualTo('D');
            assertThat(in2.read()).isEqualTo('E');
            assertThat(in2.read()).isEqualTo('F');
            assertThat(in2.read()).isEqualTo(-1);

            assertThat(server.takeRequest().getSequenceNumber()).isEqualTo(0);
        }

        @Test
        @DisplayName("should disconect at start")
        public void disconnectAtStart() throws Exception {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
            server.enqueue(new MockResponse()); // The jdk's HttpUrlConnection is a bastard.
            server.enqueue(new MockResponse());
            try {
                server.url("/a").url().openConnection().getInputStream();
            }
            catch (IOException expected) {
            }
            server.url("/b").url().openConnection().getInputStream(); // Should succeed.
        }

        @DisplayName("Throttle the request body by sleeping 500ms after every 3 bytes. With a 6-byte request, this should yield one sleep for a total delay of 500ms.")
        @Test
        public void throttleRequest() throws Exception {
            server.enqueue(new MockResponse()
                    .throttleBody(3, 500, TimeUnit.MILLISECONDS));

            long startNanos = System.nanoTime();
            URLConnection connection = server.url("/").url().openConnection();
            connection.setDoOutput(true);
            connection.getOutputStream().write("ABCDEF".getBytes("UTF-8"));
            InputStream in = connection.getInputStream();
            assertThat(in.read()).isEqualTo(-1);

            long elapsedNanos = System.nanoTime() - startNanos;
            long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);

            assertThat(elapsedMillis >= 500).isTrue().describedAs(Util.format("Request + Response: %sms", elapsedMillis));
            assertThat(elapsedMillis < 1000).isTrue().describedAs(Util.format("Request + Response: %sms", elapsedMillis));
        }

        @Test
        @DisplayName("Throttle the request body by sleeping 500ms after every 3 bytes. With a 6-byte request, this should yield one sleep for a total delay of 500ms.")
        public void throttleResponse() throws Exception {
            server.enqueue(new MockResponse()
                    .setBody("ABCDEF")
                    .throttleBody(3, 500, TimeUnit.MILLISECONDS));

            long startNanos = System.nanoTime();
            URLConnection connection = server.url("/").url().openConnection();
            InputStream in = connection.getInputStream();
            assertThat(in.read()).isEqualTo('A');
            assertThat(in.read()).isEqualTo('B');
            assertThat(in.read()).isEqualTo('C');
            assertThat(in.read()).isEqualTo('D');
            assertThat(in.read()).isEqualTo('E');
            assertThat(in.read()).isEqualTo('F');
            assertThat(in.read()).isEqualTo(-1);

            long elapsedNanos = System.nanoTime() - startNanos;
            long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);

            assertThat(elapsedMillis >= 500).isTrue().describedAs(Util.format("Request + Response: %sms", elapsedMillis));
            assertThat(elapsedMillis < 1000).isTrue().describedAs(Util.format("Request + Response: %sms", elapsedMillis));
        }

        @Test
        @DisplayName("should delay the response body by sleeping 1s.")
        public void delayResponse() throws IOException {
            server.enqueue(new MockResponse()
                    .setBody("ABCDEF")
                    .setBodyDelay(1, SECONDS));

            long startNanos = System.nanoTime();
            URLConnection connection = server.url("/").url().openConnection();
            InputStream in = connection.getInputStream();
            assertThat(in.read()).isEqualTo('A');
            long elapsedNanos = System.nanoTime() - startNanos;
            long elapsedMillis = NANOSECONDS.toMillis(elapsedNanos);
            assertThat(elapsedMillis >= 1000).isTrue().describedAs(Util.format("Request + Response: %sms", elapsedMillis));

            in.close();
        }

        @Test
        @DisplayName("should disconnect request halfway")
        public void disconnectRequestHalfway() throws IOException {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));
            // Limit the size of the request body that the server holds in memory to an arbitrary
            // 3.5 MBytes so this test can pass on devices with little memory.
            server.setBodyLimit(7 * 512 * 1024);

            HttpURLConnection connection = (HttpURLConnection) server.url("/").url().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(1024 * 1024 * 1024); // 1 GB
            connection.connect();
            OutputStream out = connection.getOutputStream();

            byte[] data = new byte[1024 * 1024];
            int i;
            for (i = 0; i < 1024; i++) {
                try {
                    out.write(data);
                    out.flush();
                }
                catch (IOException e) {
                    break;
                }
            }
            assertThat(i).isCloseTo(512, Percentage.withPercentage(10d));
        }

        @Test
        @DisplayName("should disconnect response halfway")
        public void disconnectResponseHalfway() throws IOException {
            server.enqueue(new MockResponse()
                    .setBody("ab")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

            URLConnection connection = server.url("/").url().openConnection();
            assertThat(connection.getContentLength()).isEqualTo(2);

            InputStream in = connection.getInputStream();
            assertThat(in.read()).isEqualTo('a');
            try {
                int byteRead = in.read();
                // OpenJDK behavior: end of stream.
                assertThat(in.read()).isEqualTo(-1);
            }
            catch (ProtocolException e) {
                // On Android, HttpURLConnection is implemented by OkHttp v2. OkHttp
                // treats an incomplete response body as a ProtocolException.
            }
        }


    }

    @Nested
    @DisplayName("Server shutdown")
    class TestServerShutdown {
        @Test
        @DisplayName("should disconnect response halfway")
        public void shutdownWithoutStart() throws IOException {
            MockWebServerExtension server = new MockWebServerExtension();
            server.shutdown();
        }

        @Test
        @DisplayName("should close via closeable")
        public void closeViaClosable() throws IOException {
            Closeable server = new MockWebServerExtension();
            server.close();
        }

        @Test
        @DisplayName("should shutdown without enqueue")
        public void shutdownWithoutEnqueue() throws IOException {
            MockWebServerExtension server = new MockWebServerExtension();
            server.start();
            server.shutdown();
        }

        @Test
        @DisplayName("should shutdown while blocked dispatching")
        public void shutdownWhileBlockedDispatching() throws Exception {
            server.start();
            // Enqueue a request that'll cause MockWebServer to hang on QueueDispatcher.dispatch().
            HttpURLConnection connection = (HttpURLConnection) server.url("/").url().openConnection();
            connection.setReadTimeout(500);
            assertThatThrownBy(() -> connection.getResponseCode()).isExactlyInstanceOf(SocketTimeoutException.class);
            // Shutting down the server should unblock the dispatcher.
            server.shutdown();
        }
    }

    @Nested
    @DisplayName("Server start")
    class TestServerStart {
        @BeforeEach
        public void setUp() throws Exception {
            server.start();
        }

        @AfterEach
        public void tearDown() throws Exception {
            server.shutdown();
        }

        @Test
        @DisplayName("should attribute a random port")
        public void portImplicitlyStarts() throws IOException {
            assertThat(server.getPort() > 0).isTrue();
        }

        @Test
        @DisplayName("should set the hostname")
        public void hostnameImplicitlyStarts() throws IOException {
            assertThat(server.getHostName()).isNotEmpty();
        }

        @Test
        @DisplayName("should set the proxy adress")
        public void toProxyAddressImplicitlyStarts() throws IOException {
            assertThat(server.toProxyAddress()).isNotNull();
        }

        @Test
        @DisplayName("should attribute different port to servers")
        public void differentInstancesGetDifferentPorts() throws IOException {
            MockWebServerExtension other = new MockWebServerExtension();
            assertThat(server.getPort()).isNotEqualTo(other.getPort());
            other.shutdown();
        }

        @Test
        @DisplayName("should continue on code status 100")
        public void http100Continue() throws Exception {
            server.enqueue(new MockResponse().setBody("response"));

            URL url = server.url("/").url();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Expect", "100-Continue");
            connection.getOutputStream().write("request".getBytes(StandardCharsets.UTF_8));

            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            assertThat(reader.readLine()).isEqualTo("response");

            RecordedRequest request = server.takeRequest();
            assertThat(request.getBody().readUtf8()).isEqualTo("request");
        }

        @Test
        @DisplayName("should reconstruct URL")
        public void requestUrlReconstructed() throws Exception {
            server.enqueue(new MockResponse().setBody("hello world"));

            URL url = server.url("/a/deep/path?key=foo%20bar").url();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            assertThat(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(reader.readLine()).isEqualTo("hello world");

            RecordedRequest request = server.takeRequest();
            assertThat(request.getRequestLine()).isEqualTo("GET /a/deep/path?key=foo%20bar HTTP/1.1");

            HttpUrl requestUrl = request.getRequestUrl();
            assertThat(requestUrl.scheme()).isEqualTo("http");
            assertThat(server.getHostName()).isEqualTo(requestUrl.host());
            assertThat(server.getPort()).isEqualTo(requestUrl.port());
            assertThat(requestUrl.encodedPath()).isEqualTo("/a/deep/path");
            assertThat(requestUrl.queryParameter("key")).isEqualTo("foo bar");
        }
    }

}
