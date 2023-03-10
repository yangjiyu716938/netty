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

import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOutboundInvoker;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpContentDecompressor;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequestEncoder;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseDecoder;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.AsciiString;
import io.netty5.util.NetUtil;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.jetbrains.annotations.TestOnly;

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

/**
 * Base class for web socket client handshake implementations
 */
public abstract class WebSocketClientHandshaker {

    protected static final int DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS = 10000;

    private final URI uri;

    private final WebSocketVersion version;

    private volatile boolean handshakeComplete;

    private volatile long forceCloseTimeoutMillis;

    private volatile int forceCloseInit;

    private static final AtomicIntegerFieldUpdater<WebSocketClientHandshaker> FORCE_CLOSE_INIT_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(WebSocketClientHandshaker.class, "forceCloseInit");

    private volatile boolean forceCloseComplete;

    private final String expectedSubprotocol;

    private volatile String actualSubprotocol;

    protected final HttpHeaders customHeaders;

    private final int maxFramePayloadLength;

    private final boolean absoluteUpgradeUrl;

    protected final boolean generateOriginHeader;

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength,
                                        long forceCloseTimeoutMillis) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, forceCloseTimeoutMillis, false);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     * @param  absoluteUpgradeUrl
     *            Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over
     *            clear HTTP
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength,
                                        long forceCloseTimeoutMillis, boolean absoluteUpgradeUrl) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, forceCloseTimeoutMillis,
                absoluteUpgradeUrl, true);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     * @param  absoluteUpgradeUrl
     *            Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over
     *            clear HTTP
     * @param generateOriginHeader
     *            Allows to generate the `Origin`|`Sec-WebSocket-Origin` header value for handshake request
     *            according to the given webSocketURL
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
            HttpHeaders customHeaders, int maxFramePayloadLength,
            long forceCloseTimeoutMillis, boolean absoluteUpgradeUrl, boolean generateOriginHeader) {
        this.uri = uri;
        this.version = version;
        expectedSubprotocol = subprotocol;
        this.customHeaders = customHeaders;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        this.absoluteUpgradeUrl = absoluteUpgradeUrl;
        this.generateOriginHeader = generateOriginHeader;
    }

    /**
     * Returns the URI to the web socket. e.g. "ws://myhost.com/path"
     */
    public URI uri() {
        return uri;
    }

    /**
     * Version of the web socket specification that is being used
     */
    public WebSocketVersion version() {
        return version;
    }

    /**
     * Returns the max length for any frame's payload
     */
    public int maxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * Flag to indicate if the opening handshake is complete
     */
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    private void setHandshakeComplete() {
        handshakeComplete = true;
    }

    /**
     * Returns the CSV of requested subprotocol(s) sent to the server as specified in the constructor
     */
    public String expectedSubprotocol() {
        return expectedSubprotocol;
    }

    /**
     * Returns the subprotocol response sent by the server. Only available after end of handshake.
     * Null if no subprotocol was requested or confirmed by the server.
     */
    public String actualSubprotocol() {
        return actualSubprotocol;
    }

    private void setActualSubprotocol(String actualSubprotocol) {
        this.actualSubprotocol = actualSubprotocol;
    }

    public long forceCloseTimeoutMillis() {
        return forceCloseTimeoutMillis;
    }

    /**
     * Flag to indicate if the closing handshake was initiated because of timeout.
     * For testing only.
     */
    @TestOnly
    boolean isForceCloseComplete() {
        return forceCloseComplete;
    }

    /**
     * Sets timeout to close the connection if it was not closed by the server.
     *
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     */
    public WebSocketClientHandshaker setForceCloseTimeoutMillis(long forceCloseTimeoutMillis) {
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        return this;
    }

    /**
     * Begins the opening handshake
     *
     * @param channel
     *            Channel
     */
    public Future<Void> handshake(Channel channel) {
        requireNonNull(channel, "channel");
        ChannelPipeline pipeline = channel.pipeline();
        HttpResponseDecoder decoder = pipeline.get(HttpResponseDecoder.class);
        if (decoder == null) {
            HttpClientCodec codec = pipeline.get(HttpClientCodec.class);
            if (codec == null) {
               return channel.newFailedFuture(new IllegalStateException("ChannelPipeline does not contain " +
                       "an HttpResponseDecoder or HttpClientCodec"));
            }
        }

        if (uri.getHost() == null) {
            if (customHeaders == null || !customHeaders.contains(HttpHeaderNames.HOST)) {
                return channel.newFailedFuture(new IllegalArgumentException("Cannot generate the 'host' header value," +
                        " webSocketURI should contain host or passed through customHeaders"));
            }

            if (generateOriginHeader && !customHeaders.contains(HttpHeaderNames.ORIGIN)) {
                final String originName = HttpHeaderNames.ORIGIN.toString();
                return channel.newFailedFuture(
                        new IllegalArgumentException("Cannot generate the '" + originName + "' header" +
                        " value, webSocketURI should contain host or disable generateOriginHeader or pass value" +
                        " through customHeaders"));
            }
        }

        FullHttpRequest request = newHandshakeRequest(channel.bufferAllocator());

        Promise<Void> promise = channel.newPromise();
        channel.writeAndFlush(request).addListener(channel, (ch, future) -> {
            if (future.isSuccess()) {
                ChannelPipeline p = ch.pipeline();
                ChannelHandlerContext ctx = p.context(HttpRequestEncoder.class);
                if (ctx == null) {
                    ctx = p.context(HttpClientCodec.class);
                }
                if (ctx == null) {
                    promise.setFailure(new IllegalStateException("ChannelPipeline does not contain " +
                            "an HttpRequestEncoder or HttpClientCodec"));
                    return;
                }
                p.addAfter(ctx.name(), "ws-encoder", newWebSocketEncoder());

                promise.setSuccess(null);
            } else {
                promise.setFailure(future.cause());
            }
        });
        return promise.asFuture();
    }

    /**
     * Returns a new {@link FullHttpRequest) which will be used for the handshake.
     */
    protected abstract FullHttpRequest newHandshakeRequest(BufferAllocator allocator);

    /**
     * Validates and finishes the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     */
    public final void finishHandshake(Channel channel, FullHttpResponse response) {
        verify(response);

        // Verify the subprotocol that we received from the server.
        // This must be one of our expected subprotocols - or null/empty if we didn't want to speak a subprotocol
        CharSequence receivedProtocol = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        receivedProtocol = receivedProtocol != null ? AsciiString.trim(receivedProtocol) : null;
        String expectedProtocol = expectedSubprotocol != null ? expectedSubprotocol : "";
        boolean protocolValid = false;

        if (expectedProtocol.isEmpty() && receivedProtocol == null) {
            // No subprotocol required and none received
            protocolValid = true;
            setActualSubprotocol(expectedSubprotocol); // null or "" - we echo what the user requested
        } else if (!expectedProtocol.isEmpty() && receivedProtocol != null && receivedProtocol.length() > 0) {
            // We require a subprotocol and received one -> verify it
            for (String protocol : expectedProtocol.split(",")) {
                if (AsciiString.contentEquals(protocol.trim(), receivedProtocol)) {
                    protocolValid = true;
                    setActualSubprotocol(receivedProtocol.toString());
                    break;
                }
            }
        } // else mixed cases - which are all errors

        if (!protocolValid) {
            throw new WebSocketClientHandshakeException(String.format(
                    "Invalid subprotocol. Actual: %s. Expected one of: %s",
                    receivedProtocol, expectedSubprotocol), response);
        }

        setHandshakeComplete();

        final ChannelPipeline p = channel.pipeline();
        // Remove decompressor from pipeline if its in use
        HttpContentDecompressor decompressor = p.get(HttpContentDecompressor.class);
        if (decompressor != null) {
            p.remove(decompressor);
        }

        // Remove aggregator if present before
        HttpObjectAggregator aggregator = p.get(HttpObjectAggregator.class);
        if (aggregator != null) {
            p.remove(aggregator);
        }

        ChannelHandlerContext ctx = p.context(HttpResponseDecoder.class);
        if (ctx == null) {
            ctx = p.context(HttpClientCodec.class);
            if (ctx == null) {
                throw new IllegalStateException("ChannelPipeline does not contain " +
                        "an HttpRequestEncoder or HttpClientCodec");
            }
            final HttpClientCodec codec =  (HttpClientCodec) ctx.handler();
            // Remove the encoder part of the codec as the user may start writing frames after this method returns.
            codec.removeOutboundHandler();

            p.addAfter(ctx.name(), "ws-decoder", newWebsocketDecoder());

            // Delay the removal of the decoder so the user can setup the pipeline if needed to handle
            // WebSocketFrame messages.
            // See https://github.com/netty/netty/issues/4533
            channel.executor().execute(() -> p.remove(codec));
        } else {
            if (p.get(HttpRequestEncoder.class) != null) {
                // Remove the encoder part of the codec as the user may start writing frames after this method returns.
                p.remove(HttpRequestEncoder.class);
            }
            final ChannelHandlerContext context = ctx;
            p.addAfter(context.name(), "ws-decoder", newWebsocketDecoder());

            // Delay the removal of the decoder so the user can setup the pipeline if needed to handle
            // WebSocketFrame messages.
            // See https://github.com/netty/netty/issues/4533
            channel.executor().execute(() -> p.remove(context.handler()));
        }
    }

    /**
     * Process the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     * @return future
     *            the {@link Future} which is notified once the handshake completes.
     */
    public final Future<Void> processHandshake(final Channel channel, HttpResponse response) {
        if (response instanceof FullHttpResponse) {
            try {
                finishHandshake(channel, (FullHttpResponse) response);
                return channel.newSucceededFuture();
            } catch (Throwable cause) {
                return channel.newFailedFuture(cause);
            }
        } else {
            ChannelPipeline p = channel.pipeline();
            ChannelHandlerContext ctx = p.context(HttpResponseDecoder.class);
            if (ctx == null) {
                ctx = p.context(HttpClientCodec.class);
                if (ctx == null) {
                    return channel.newFailedFuture(new IllegalStateException("ChannelPipeline does not contain " +
                            "an HttpResponseDecoder or HttpClientCodec"));
                }
            }

            Promise<Void> promise = channel.newPromise();
            // Add aggregator and ensure we feed the HttpResponse so it is aggregated. A limit of 8192 should be more
            // then enough for the websockets handshake payload.
            //
            // TODO: Make handshake work without HttpObjectAggregator at all.
            String aggregatorName = "httpAggregator";
            p.addAfter(ctx.name(), aggregatorName, new HttpObjectAggregator(8192));
            p.addAfter(aggregatorName, "handshaker", new SimpleChannelInboundHandler<FullHttpResponse>() {
                @Override
                protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                    // Remove ourself and do the actual handshake
                    ctx.pipeline().remove(this);
                    try {
                        finishHandshake(channel, msg);
                        promise.setSuccess(null);
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    // Remove ourself and fail the handshake promise.
                    ctx.pipeline().remove(this);
                    promise.setFailure(cause);
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    // Fail promise if Channel was closed
                    if (!promise.isDone()) {
                        promise.tryFailure(new ClosedChannelException());
                    }
                    ctx.fireChannelInactive();
                }
            });
            try {
                ctx.fireChannelRead(ReferenceCountUtil.retain(response));
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return promise.asFuture();
        }
    }

    /**
     * Verify the {@link FullHttpResponse} and throws a {@link WebSocketHandshakeException} if something is wrong.
     */
    protected abstract void verify(FullHttpResponse response);

    /**
     * Returns the decoder to use after handshake is complete.
     */
    protected abstract WebSocketFrameDecoder newWebsocketDecoder();

    /**
     * Returns the encoder to use after the handshake is complete.
     */
    protected abstract WebSocketFrameEncoder newWebSocketEncoder();

    /**
     * Performs the closing handshake.
     *
     * When called from within a {@link ChannelHandler} you most likely want to use
     * {@link #close(ChannelHandlerContext, CloseWebSocketFrame)}.
     *
     * @param channel
     *            Channel
     * @param frame
     *            Closing Frame that was received
     */
    public Future<Void> close(Channel channel, CloseWebSocketFrame frame) {
        requireNonNull(channel, "channel");
        return close0(channel, channel, frame);
    }

    /**
     * Performs the closing handshake
     *
     * @param ctx
     *            the {@link ChannelHandlerContext} to use.
     * @param frame
     *            Closing Frame that was received
     */
    public Future<Void> close(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        requireNonNull(ctx, "ctx");
        return close0(ctx, ctx.channel(), frame);
    }

    private Future<Void> close0(final ChannelOutboundInvoker invoker, final Channel channel,
                                 CloseWebSocketFrame frame) {
        Future<Void> f = invoker.writeAndFlush(frame);
        final long forceCloseTimeoutMillis = this.forceCloseTimeoutMillis;
        final WebSocketClientHandshaker handshaker = this;
        if (forceCloseTimeoutMillis <= 0 || !channel.isActive() || forceCloseInit != 0) {
            return f;
        }

        f.addListener(future -> {
            // If flush operation failed, there is no reason to expect
            // a server to receive CloseFrame. Thus this should be handled
            // by the application separately.
            // Also, close might be called twice from different threads.
            if (future.isSuccess() && channel.isActive() &&
                    FORCE_CLOSE_INIT_UPDATER.compareAndSet(handshaker, 0, 1)) {
                final Future<?> forceCloseFuture = channel.executor().schedule(() -> {
                    if (channel.isActive()) {
                        channel.close();
                        forceCloseComplete = true;
                    }
                }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);

                channel.closeFuture().addListener(ignore -> forceCloseFuture.cancel());
            }
        });
        return f;
    }

    /**
     * Return the constructed raw path for the give {@link URI}.
     */
    protected String upgradeUrl(URI wsURL) {
        if (absoluteUpgradeUrl) {
            return wsURL.toString();
        }

        String path = wsURL.getRawPath();
        path = path == null || path.isEmpty() ? "/" : path;
        String query = wsURL.getRawQuery();
        return query != null && !query.isEmpty() ? path + '?' + query : path;
    }

    static CharSequence websocketHostValue(URI wsURL) {
        int port = wsURL.getPort();
        if (port == -1) {
            return wsURL.getHost();
        }
        String host = wsURL.getHost();
        String scheme = wsURL.getScheme();
        if (port == HttpScheme.HTTP.port()) {
            return HttpScheme.HTTP.name().contentEquals(scheme)
                    || WebSocketScheme.WS.name().contentEquals(scheme) ?
                    host : NetUtil.toSocketAddressString(host, port);
        }
        if (port == HttpScheme.HTTPS.port()) {
            return HttpScheme.HTTPS.name().contentEquals(scheme)
                    || WebSocketScheme.WSS.name().contentEquals(scheme) ?
                    host : NetUtil.toSocketAddressString(host, port);
        }

        // if the port is not standard (80/443) its needed to add the port to the header.
        // See https://tools.ietf.org/html/rfc6454#section-6.2
        return NetUtil.toSocketAddressString(host, port);
    }
}
