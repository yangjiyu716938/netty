/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.Buffer;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.Resource;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.codec.http.HttpServerCodec;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class WebSocketServerHandshaker13Test extends WebSocketServerHandshakerTest {

    @Override
    protected WebSocketServerHandshaker newHandshaker(String webSocketURL, String subprotocols,
            WebSocketDecoderConfig decoderConfig) {
        return new WebSocketServerHandshaker13(webSocketURL, subprotocols, decoderConfig);
    }

    @Override
    protected WebSocketVersion webSocketVersion() {
        return WebSocketVersion.V13;
    }

    @Test
    public void testPerformOpeningHandshake() {
        testPerformOpeningHandshake0(true);
    }

    @Test
    public void testPerformOpeningHandshakeSubProtocolNotSupported() {
        testPerformOpeningHandshake0(false);
    }

    private static void testPerformOpeningHandshake0(boolean subProtocol) {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator<DefaultHttpContent>(42), new HttpResponseEncoder(),
                new HttpRequestDecoder());

        if (subProtocol) {
            testUpgrade0(ch, new WebSocketServerHandshaker13(
                    "ws://example.com/chat", "chat", false, Integer.MAX_VALUE
                    , false));
        } else {
            testUpgrade0(ch, new WebSocketServerHandshaker13(
                    "ws://example.com/chat", null, false, Integer.MAX_VALUE,
                    false));
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testCloseReasonWithEncoderAndDecoder() {
        testCloseReason0(new HttpResponseEncoder(), new HttpRequestDecoder());
    }

    @Test
    public void testCloseReasonWithCodec() {
        testCloseReason0(new HttpServerCodec());
    }

    @Test
    public void testHandshakeExceptionWhenConnectionHeaderIsAbsent() {
        EmbeddedChannel channel = new EmbeddedChannel();
        final WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                         "chat", WebSocketDecoderConfig.DEFAULT);
       try (FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "ws://example.com/chat", preferredAllocator().allocate(0))) {
           request.headers()
                   .set(HttpHeaderNames.HOST, "server.example.com")
               .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
               .set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com")
               .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
               .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
           Throwable exception = assertThrows(WebSocketServerHandshakeException.class, () -> {
               serverHandshaker.handshake(channel, request, null);
           });

           assertEquals("not a WebSocket request: a |Connection| header must includes a token 'Upgrade'",
                        exception.getMessage());
           assertFalse(channel.finishAndReleaseAll());
       }
    }

    @Test
    public void testHandshakeExceptionWhenInvalidConnectionHeader() {
        EmbeddedChannel channel = new EmbeddedChannel();
        final WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                         "chat", WebSocketDecoderConfig.DEFAULT);
        try (FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "ws://example.com/chat", preferredAllocator().allocate(0))) {
            request.headers()
                    .set(HttpHeaderNames.HOST, "server.example.com")
                    .set(HttpHeaderNames.CONNECTION, "close")
                    .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                    .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
            Throwable exception = assertThrows(WebSocketServerHandshakeException.class, () -> {
                serverHandshaker.handshake(channel, request, null);
            });

            assertEquals("not a WebSocket request: a |Connection| header must includes a token 'Upgrade'",
                    exception.getMessage());
            assertFalse(channel.finishAndReleaseAll());
        }
    }

    @Test
    public void testHandshakeExceptionWhenInvalidUpgradeHeader() {
        EmbeddedChannel channel = new EmbeddedChannel();
        final WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                         "chat", WebSocketDecoderConfig.DEFAULT);
        try (FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "ws://example.com/chat", preferredAllocator().allocate(0))) {
            request.headers()
                    .set(HttpHeaderNames.HOST, "server.example.com")
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                    .set(HttpHeaderNames.UPGRADE, "my_websocket")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
                    .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
            Throwable exception = assertThrows(WebSocketServerHandshakeException.class, () -> {
                serverHandshaker.handshake(channel, request, null);
            });

            assertEquals("not a WebSocket request: a |Upgrade| header must containing the value 'websocket'",
                    exception.getMessage());
            assertFalse(channel.finishAndReleaseAll());
        }
    }

    private static void testCloseReason0(ChannelHandler... handlers) {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator<DefaultHttpContent>(42));
        ch.pipeline().addLast(handlers);
        testUpgrade0(ch, new WebSocketServerHandshaker13("ws://example.com/chat", "chat",
                WebSocketDecoderConfig.newBuilder().maxFramePayloadLength(4).closeOnProtocolViolation(true).build()));

        ch.writeOutbound(new BinaryWebSocketFrame(ch.bufferAllocator().copyOf(new byte[8])));
        Buffer buffer = ch.readOutbound();
        try {
            ch.writeInbound(buffer);
            fail();
        } catch (CorruptedWebSocketFrameException expected) {
            // expected
        }
        Resource<?> closeMessage = ch.readOutbound();
        assertThat(closeMessage).isInstanceOf(Buffer.class);
        closeMessage.close();
        assertFalse(ch.finish());
    }

    private static void testUpgrade0(EmbeddedChannel ch, WebSocketServerHandshaker13 handshaker) {
        try (FullHttpRequest req = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.GET, "/chat", preferredAllocator().allocate(0))) {
            req.headers().set(HttpHeaderNames.HOST, "server.example.com");
            req.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
            req.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
            req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
            req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
            req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");

            handshaker.handshake(ch, req);

            EmbeddedChannel ch2 = new EmbeddedChannel(new HttpResponseDecoder());
            ch2.writeInbound(ch.<Buffer>readOutbound());
            HttpResponse res = ch2.readInbound();

            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT));
            Iterator<String> subProtocols = handshaker.subprotocols().iterator();
            if (subProtocols.hasNext()) {
                assertEquals(subProtocols.next(), res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
            } else {
                assertNull(res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
            }
        }
    }
}
