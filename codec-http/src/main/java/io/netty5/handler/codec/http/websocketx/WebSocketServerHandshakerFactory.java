/*
 * Copyright 2019 The Netty Project
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

import io.netty5.channel.Channel;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.Future;

import java.util.Objects;

/**
 * Auto-detects the version of the Web Socket protocol in use and creates a new proper
 * {@link WebSocketServerHandshaker}.
 */
public class WebSocketServerHandshakerFactory {

    private final String webSocketURL;

    private final String subprotocols;

    private final WebSocketDecoderConfig decoderConfig;

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath".
     *            Subsequent web socket frames will be sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     */
    public WebSocketServerHandshakerFactory(
            String webSocketURL, String subprotocols, boolean allowExtensions) {
        this(webSocketURL, subprotocols, allowExtensions, 65536);
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath".
     *            Subsequent web socket frames will be sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param maxFramePayloadLength
     *            Maximum allowable frame payload length. Setting this value to your application's
     *            requirement may reduce denial of service attacks using long data frames.
     */
    public WebSocketServerHandshakerFactory(
            String webSocketURL, String subprotocols, boolean allowExtensions,
            int maxFramePayloadLength) {
        this(webSocketURL, subprotocols, allowExtensions, maxFramePayloadLength, false);
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath".
     *            Subsequent web socket frames will be sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param maxFramePayloadLength
     *            Maximum allowable frame payload length. Setting this value to your application's
     *            requirement may reduce denial of service attacks using long data frames.
     * @param allowMaskMismatch
     *            When set to true, frames which are not masked properly according to the standard will still be
     *            accepted.
     */
    public WebSocketServerHandshakerFactory(
            String webSocketURL, String subprotocols, boolean allowExtensions,
            int maxFramePayloadLength, boolean allowMaskMismatch) {
        this(webSocketURL, subprotocols, WebSocketDecoderConfig.newBuilder()
            .allowExtensions(allowExtensions)
            .maxFramePayloadLength(maxFramePayloadLength)
            .allowMaskMismatch(allowMaskMismatch)
            .build());
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath".
     *            Subsequent web socket frames will be sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param decoderConfig
     *            Frames decoder options.
     */
    public WebSocketServerHandshakerFactory(
            String webSocketURL, String subprotocols, WebSocketDecoderConfig decoderConfig) {
        this.webSocketURL = webSocketURL;
        this.subprotocols = subprotocols;
        this.decoderConfig = Objects.requireNonNull(decoderConfig, "decoderConfig");
    }

    /**
     * Instances a new handshaker
     *
     * @return A new WebSocketServerHandshaker for the requested web socket version. Null if web
     *         socket version is not supported.
     */
    public WebSocketServerHandshaker newHandshaker(HttpRequest req) {

        CharSequence version = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
        if (version != null && AsciiString.contentEqualsIgnoreCase(version, WebSocketVersion.V13.toAsciiString())) {
            // Version 13 of the wire protocol - RFC 6455 (version 17 of the draft hybi specification).
            return new WebSocketServerHandshaker13(webSocketURL, subprotocols, decoderConfig);
        }
        return null;
    }

    /**
     * @deprecated use {@link #sendUnsupportedVersionResponse(Channel)}
     */
    @Deprecated
    public static void sendUnsupportedWebSocketVersionResponse(Channel channel) {
        sendUnsupportedVersionResponse(channel);
    }

    /**
     * Return that we need cannot not support the web socket version
     */
    public static Future<Void> sendUnsupportedVersionResponse(Channel channel) {
        HttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.UPGRADE_REQUIRED, channel.bufferAllocator().allocate(0));
        res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue());
        HttpUtil.setContentLength(res, 0);
        return channel.writeAndFlush(res);
    }
}
