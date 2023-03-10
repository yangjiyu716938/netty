/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.http.websocketx.extensions;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.util.concurrent.Future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static io.netty5.util.internal.ObjectUtil.checkNonEmpty;

/**
 * This handler negotiates and initializes the WebSocket Extensions.
 *
 * This implementation negotiates the extension with the server in a defined order,
 * ensures that the successfully negotiated extensions are consistent between them,
 * and initializes the channel pipeline with the extension decoder and encoder.
 *
 * Find a basic implementation for compression extensions at
 * <tt>io.netty5.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler</tt>.
 */
public class WebSocketClientExtensionHandler implements ChannelHandler {

    private final List<WebSocketClientExtensionHandshaker> extensionHandshakers;

    /**
     * Constructor
     *
     * @param extensionHandshakers
     *      The extension handshaker in priority order. A handshaker could be repeated many times
     *      with fallback configuration.
     */
    public WebSocketClientExtensionHandler(WebSocketClientExtensionHandshaker... extensionHandshakers) {
        this.extensionHandshakers = Arrays.asList(checkNonEmpty(extensionHandshakers, "extensionHandshakers"));
    }

    @Override
    public Future<Void> write(final ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest && WebSocketExtensionUtil.isWebsocketUpgrade(((HttpRequest) msg).headers())) {
            HttpRequest request = (HttpRequest) msg;
            CharSequence headerValue = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
            List<WebSocketExtensionData> extraExtensions =
                    new ArrayList<>(extensionHandshakers.size());
            for (WebSocketClientExtensionHandshaker extensionHandshaker : extensionHandshakers) {
                extraExtensions.add(extensionHandshaker.newRequestData());
            }
            String newHeaderValue = WebSocketExtensionUtil.computeMergeExtensionsHeaderValue(
                    headerValue, extraExtensions);

            request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, newHeaderValue);
        }

        return ctx.write(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            if (WebSocketExtensionUtil.isWebsocketUpgrade(response.headers())) {
                CharSequence extensionsHeader = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);

                if (extensionsHeader != null) {
                    List<WebSocketExtensionData> extensions =
                            WebSocketExtensionUtil.extractExtensions(extensionsHeader);
                    List<WebSocketClientExtension> validExtensions =
                            new ArrayList<>(extensions.size());
                    int rsv = 0;

                    for (WebSocketExtensionData extensionData : extensions) {
                        Iterator<WebSocketClientExtensionHandshaker> extensionHandshakersIterator =
                                extensionHandshakers.iterator();
                        WebSocketClientExtension validExtension = null;

                        while (validExtension == null && extensionHandshakersIterator.hasNext()) {
                            WebSocketClientExtensionHandshaker extensionHandshaker =
                                    extensionHandshakersIterator.next();
                            validExtension = extensionHandshaker.handshakeExtension(extensionData);
                        }

                        if (validExtension != null && (validExtension.rsv() & rsv) == 0) {
                            rsv |= validExtension.rsv();
                            validExtensions.add(validExtension);
                        } else {
                            throw new CodecException(
                                    "invalid WebSocket Extension handshake for \"" + extensionsHeader + '"');
                        }
                    }

                    for (WebSocketClientExtension validExtension : validExtensions) {
                        WebSocketExtensionDecoder decoder = validExtension.newExtensionDecoder();
                        WebSocketExtensionEncoder encoder = validExtension.newExtensionEncoder();
                        ctx.pipeline().addAfter(ctx.name(), decoder.getClass().getName(), decoder);
                        ctx.pipeline().addAfter(ctx.name(), encoder.getClass().getName(), encoder);
                    }
                }
                ctx.fireChannelRead(msg);
                ctx.pipeline().remove(this);
                return;
            }
        }

        ctx.fireChannelRead(msg);
    }
}

