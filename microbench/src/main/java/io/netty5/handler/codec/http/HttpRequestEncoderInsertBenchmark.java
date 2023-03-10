/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpConstants.CR;
import static io.netty5.handler.codec.http.HttpConstants.LF;
import static io.netty5.handler.codec.http.HttpConstants.SP;
import static java.nio.charset.StandardCharsets.US_ASCII;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
public class HttpRequestEncoderInsertBenchmark extends AbstractMicrobenchmark {

    private final String uri = "http://localhost?eventType=CRITICAL&from=0&to=1497437160327&limit=10&offset=0";
    private final OldHttpRequestEncoder encoderOld = new OldHttpRequestEncoder();
    private final HttpRequestEncoder encoderNew = new HttpRequestEncoder();

    @Benchmark
    public Buffer oldEncoder() {
        try (Buffer buffer = preferredAllocator().allocate(100)) {
            encoderOld.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, uri));
            return buffer;
        }
    }

    @Benchmark
    public Buffer newEncoder() throws Exception {
        try (Buffer buffer = preferredAllocator().allocate(100)) {
            encoderNew.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, uri));
            return buffer;
        }
    }

    private static class OldHttpRequestEncoder extends HttpObjectEncoder<HttpRequest> {
        private static final byte[] CRLF = {CR, LF};
        private static final char SLASH = '/';
        private static final char QUESTION_MARK = '?';

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return super.acceptOutboundMessage(msg) && !(msg instanceof HttpResponse);
        }

        @Override
        protected void encodeInitialLine(Buffer buf, HttpRequest request) {
            AsciiString method = request.method().asciiName();
            buf.writeCharSequence(method, US_ASCII);
            buf.writeByte(SP);

            // Add / as absolute path if no is present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            String uri = request.uri();

            if (uri.isEmpty()) {
                uri += SLASH;
            } else {
                int start = uri.indexOf("://");
                if (start != -1 && uri.charAt(0) != SLASH) {
                    int startIndex = start + 3;
                    // Correctly handle query params.
                    // See https://github.com/netty/netty/issues/2732
                    int index = uri.indexOf(QUESTION_MARK, startIndex);
                    if (index == -1) {
                        if (uri.lastIndexOf(SLASH) <= startIndex) {
                            uri += SLASH;
                        }
                    } else {
                        if (uri.lastIndexOf(SLASH, index) <= startIndex) {
                            int len = uri.length();
                            StringBuilder sb = new StringBuilder(len + 1);
                            sb.append(uri, 0, index)
                                    .append(SLASH)
                                    .append(uri, index, len);
                            uri = sb.toString();
                        }
                    }
                }
            }

            buf.writeBytes(uri.getBytes(StandardCharsets.UTF_8));

            buf.writeByte(SP);
            request.protocolVersion().encode(buf);
            buf.writeBytes(CRLF);
        }
    }
}
