/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.base64.Base64;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientUpgradeHandler;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.util.Send;
import io.netty5.util.collection.CharObjectMap;
import io.netty5.util.internal.UnstableApi;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.netty5.handler.codec.base64.Base64Dialect.URL_SAFE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME;
import static io.netty5.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTING_ENTRY_LENGTH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Client-side clear-text upgrade codec from HTTP to HTTP/2.
 */
@UnstableApi
public class Http2ClientUpgradeCodec implements HttpClientUpgradeHandler.UpgradeCodec {

    private static final List<CharSequence> UPGRADE_HEADERS = Collections.singletonList(HTTP_UPGRADE_SETTINGS_HEADER);

    private final String handlerName;
    private final Http2ConnectionHandler connectionHandler;
    private final ChannelHandler upgradeToHandler;
    private final ChannelHandler http2MultiplexHandler;

    public Http2ClientUpgradeCodec(Http2FrameCodec frameCodec, ChannelHandler upgradeToHandler) {
        this(null, frameCodec, upgradeToHandler);
    }

    public Http2ClientUpgradeCodec(String handlerName, Http2FrameCodec frameCodec, ChannelHandler upgradeToHandler) {
        this(handlerName, frameCodec, upgradeToHandler, null);
    }

    /**
     * Creates the codec using a default name for the connection handler when adding to the
     * pipeline.
     *
     * @param connectionHandler the HTTP/2 connection handler
     */
    public Http2ClientUpgradeCodec(Http2ConnectionHandler connectionHandler) {
        this((String) null, connectionHandler);
    }

    /**
     * Creates the codec using a default name for the connection handler when adding to the
     * pipeline.
     *
     * @param connectionHandler the HTTP/2 connection handler
     * @param http2MultiplexHandler the Http2 Multiplexer handler to work with Http2FrameCodec
     */
    public Http2ClientUpgradeCodec(Http2ConnectionHandler connectionHandler,
        Http2MultiplexHandler http2MultiplexHandler) {
        this(null, connectionHandler, http2MultiplexHandler);
    }

    /**
     * Creates the codec providing an upgrade to the given handler for HTTP/2.
     *
     * @param handlerName the name of the HTTP/2 connection handler to be used in the pipeline,
     *                    or {@code null} to auto-generate the name
     * @param connectionHandler the HTTP/2 connection handler
     */
    public Http2ClientUpgradeCodec(String handlerName, Http2ConnectionHandler connectionHandler) {
        this(handlerName, connectionHandler, connectionHandler, null);
    }

    /**
     * Creates the codec providing an upgrade to the given handler for HTTP/2.
     *
     * @param handlerName the name of the HTTP/2 connection handler to be used in the pipeline,
     *                    or {@code null} to auto-generate the name
     * @param connectionHandler the HTTP/2 connection handler
     */
    public Http2ClientUpgradeCodec(String handlerName, Http2ConnectionHandler connectionHandler,
        Http2MultiplexHandler http2MultiplexHandler) {
        this(handlerName, connectionHandler, connectionHandler, http2MultiplexHandler);
    }

    private Http2ClientUpgradeCodec(String handlerName, Http2ConnectionHandler connectionHandler, ChannelHandler
        upgradeToHandler, Http2MultiplexHandler http2MultiplexHandler) {
        this.handlerName = handlerName;
        this.connectionHandler = requireNonNull(connectionHandler, "connectionHandler");
        this.upgradeToHandler = requireNonNull(upgradeToHandler, "upgradeToHandler");
        this.http2MultiplexHandler = http2MultiplexHandler;
    }

    @Override
    public CharSequence protocol() {
        return HTTP_UPGRADE_PROTOCOL_NAME;
    }

    @Override
    public Collection<CharSequence> setUpgradeHeaders(ChannelHandlerContext ctx,
        HttpRequest upgradeRequest) {
        CharSequence settingsValue = getSettingsHeaderValue(ctx);
        upgradeRequest.headers().set(HTTP_UPGRADE_SETTINGS_HEADER, settingsValue);
        return UPGRADE_HEADERS;
    }

    @Override
    public void upgradeTo(ChannelHandlerContext ctx, Send<FullHttpResponse> upgradeResponse)
        throws Exception {
        upgradeResponse.close();
        try {
            // Add the handler to the pipeline.
            ctx.pipeline().addAfter(ctx.name(), handlerName, upgradeToHandler);

            // Add the Http2 Multiplex handler as this handler handle events produced by the connectionHandler.
            // See https://github.com/netty/netty/issues/9495
            if (http2MultiplexHandler != null) {
                final String name = ctx.pipeline().context(connectionHandler).name();
                ctx.pipeline().addAfter(name, null, http2MultiplexHandler);
            }

            // Reserve local stream 1 for the response.
            connectionHandler.onHttpClientUpgrade();
        } catch (Http2Exception e) {
            ctx.fireChannelExceptionCaught(e);
            ctx.close();
        }
    }

    /**
     * Converts the current settings for the handler to the Base64-encoded representation used in
     * the HTTP2-Settings upgrade header.
     */
    private CharSequence getSettingsHeaderValue(ChannelHandlerContext ctx) {
        // Get the local settings for the handler.
        Http2Settings settings = connectionHandler.decoder().localSettings();
        int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
        // Serialize the payload of the SETTINGS frame.
        try (Buffer buf = ctx.bufferAllocator().allocate(payloadLength)) {
            for (CharObjectMap.PrimitiveEntry<Long> entry : settings.entries()) {
                buf.writeChar(entry.key());
                buf.writeInt(entry.value().intValue());
            }

            // Base64 encode the payload and then convert to a string for the header.
           try (Buffer encodedBuf = Base64.encode(buf, URL_SAFE)) {
               return encodedBuf.toString(UTF_8);
           }
        }
    }
}
