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
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.FileRegion;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.Resource;
import io.netty5.util.internal.StringUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;

import static io.netty5.handler.codec.http.HttpConstants.CR;
import static io.netty5.handler.codec.http.HttpConstants.LF;

/**
 * Encodes an {@link HttpMessage} or an {@link HttpContent} into
 * a {@link Buffer}.
 *
 * <h3>Extensibility</h3>
 *
 * Please note that this encoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the encoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 */
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToMessageEncoder<Object> {
    static final short CRLF_SHORT = (CR << 8) | LF;
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) | CRLF_SHORT;
    private static final byte[] CRLF = {CR, LF};
    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };
    private static final float HEADERS_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_WEIGHT_HISTORICAL = 1 - HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_NEW = HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_HISTORICAL = HEADERS_WEIGHT_HISTORICAL;

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;
    private static final int ST_CONTENT_ALWAYS_EMPTY = 3;

    private Supplier<Buffer> crlfBufferSupplier;
    private Supplier<Buffer> zeroCrlfCrlfBufferSupplier;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    /**
     * Used to calculate an exponential moving average of the encoded size of the initial line and the headers for
     * a guess for future buffer allocations.
     */
    private float headersEncodedSizeAccumulator = 256;

    /**
     * Used to calculate an exponential moving average of the encoded size of the trailers for
     * a guess for future buffer allocations.
     */
    private float trailersEncodedSizeAccumulator = 256;

    @Override
    protected void encodeAndClose(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        Buffer buf = null;
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg)
                        + ", state: " + state);
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;

            buf = ctx.bufferAllocator().allocate((int) headersEncodedSizeAccumulator);
            // Encode the message.
            encodeInitialLine(buf, m);
            state = isContentAlwaysEmpty(m) ? ST_CONTENT_ALWAYS_EMPTY :
                    HttpUtil.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;

            sanitizeHeadersBeforeEncode(m, state == ST_CONTENT_ALWAYS_EMPTY);

            encodeHeaders(m.headers(), buf);
            buf.writeShort(CRLF_SHORT);

            headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                                            HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
        }

        // Bypass the encoder in case of an empty buffer, so that the following idiom works:
        //
        //     ch.write(ctx.bufferAllocator().allocate(0)).addListener(ch, ChannelFutureListeners.CLOSE);
        //
        // See https://github.com/netty/netty/issues/2983 for more information.
        if (msg instanceof Buffer && ((Buffer) msg).readableBytes() == 0) {
            out.add(msg);
            return;
        }

        if (msg instanceof HttpContent || msg instanceof Buffer || msg instanceof FileRegion) {
            switch (state) {
                case ST_INIT:
                    Resource.dispose(msg);
                    throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg)
                        + ", state: " + state);
                case ST_CONTENT_NON_CHUNK:
                    final long contentLength = contentLength(msg);
                    if (contentLength > 0) {
                        if (buf != null && buf.writableBytes() >= contentLength && msg instanceof HttpContent) {
                            // merge into other buffer for performance reasons
                            buf.writeBytes(((HttpContent<?>) msg).payload());
                            Resource.dispose(msg);
                            out.add(buf);
                        } else {
                            if (buf != null) {
                                out.add(buf);
                            }
                            out.add(encode(msg));
                        }

                        if (msg instanceof LastHttpContent) {
                            state = ST_INIT;
                        }

                        break;
                    } else {
                        // do not break, let's fall-through
                    }

                    // fall-through!
                case ST_CONTENT_ALWAYS_EMPTY:

                    Resource.dispose(msg);
                    if (buf != null) {
                        // We allocated a buffer so add it now.
                        out.add(buf);
                    } else {
                        // Need to produce some output otherwise an IllegalStateException will be thrown as we did not
                        // write anything. Writing an empty buffer will not actually write anything on the wire, so if
                        // there is a user error with msg it will not be visible externally
                        out.add(ctx.bufferAllocator().allocate(0));
                    }

                    break;
                case ST_CONTENT_CHUNK:
                    if (buf != null) {
                        // We allocated a buffer so add it now.
                        out.add(buf);
                    }
                    encodeChunkedContent(ctx, msg, contentLength(msg), out);

                    break;
                default:
                    throw new Error();
            }

            if (msg instanceof LastHttpContent) {
                state = ST_INIT;
            }
        } else if (buf != null) {
            out.add(buf);
        }
    }

    /**
     * Encode the {@link HttpHeaders} into a {@link Buffer}.
     */
    protected void encodeHeaders(HttpHeaders headers, Buffer buf) {
        for (Entry<CharSequence, CharSequence> header : headers) {
            HttpHeadersEncoder.encoderHeader(header.getKey(), header.getValue(), buf);
        }
    }

    private void encodeChunkedContent(ChannelHandlerContext ctx, Object msg, long contentLength, List<Object> out) {
        if (contentLength > 0) {
            String lengthHex = Long.toHexString(contentLength);
            Buffer buf = ctx.bufferAllocator().allocate(lengthHex.length() + 2);
            buf.writeCharSequence(lengthHex, StandardCharsets.US_ASCII);
            buf.writeShort(CRLF_SHORT);
            out.add(buf);
            out.add(encode(msg));
            out.add(crlfBuffer(ctx.bufferAllocator()));
        }

        if (msg instanceof LastHttpContent) {
            HttpHeaders headers = ((LastHttpContent<?>) msg).trailingHeaders();
            if (headers.isEmpty()) {
                out.add(zeroCrlfCrlfBuffer(ctx.bufferAllocator()));
            } else {
                Buffer buf = ctx.bufferAllocator().allocate((int) trailersEncodedSizeAccumulator);
                buf.writeMedium(ZERO_CRLF_MEDIUM);
                encodeHeaders(headers, buf);
                buf.writeShort(CRLF_SHORT);
                trailersEncodedSizeAccumulator = TRAILERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                                                 TRAILERS_WEIGHT_HISTORICAL * trailersEncodedSizeAccumulator;
                out.add(buf);
            }
            if (contentLength == 0) {
                // EmptyLastHttpContent or LastHttpContent with empty payload
                ((LastHttpContent<?>) msg).close();
            }
        } else if (contentLength == 0) {
            // Need to produce some output otherwise an
            // IllegalStateException will be thrown
            out.add(encode(msg));
        }
    }

    /**
     * Allows to sanitize headers of the message before encoding these.
     */
    protected void sanitizeHeadersBeforeEncode(@SuppressWarnings("unused") H msg, boolean isAlwaysEmpty) {
        // noop
    }

    /**
     * Determine whether a message has a content or not. Some message may have headers indicating
     * a content without having an actual content, e.g the response to an HEAD or CONNECT request.
     *
     * @param msg the message to test
     * @return {@code true} to signal the message has no content
     */
    protected boolean isContentAlwaysEmpty(@SuppressWarnings("unused") H msg) {
        return false;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpObject || msg instanceof Buffer || msg instanceof FileRegion;
    }

    private static Object encode(Object msg) {
        if (msg instanceof Buffer) {
            return msg;
        }
        if (msg instanceof HttpContent) {
            return ((HttpContent<?>) msg).payload();
        }
        if (msg instanceof FileRegion) {
            return msg;
        }
        Resource.dispose(msg);
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    private static long contentLength(Object msg) {
        if (msg instanceof HttpContent) {
            return ((HttpContent<?>) msg).payload().readableBytes();
        }
        if (msg instanceof Buffer) {
            return ((Buffer) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).count();
        }
        Resource.dispose(msg);
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    /**
     * Add some additional overhead to the buffer. The rational is that it is better to slightly over allocate and waste
     * some memory, rather than under allocate and require a resize/copy.
     * @param readableBytes The readable bytes in the buffer.
     * @return The {@code readableBytes} with some additional padding.
     */
    private static int padSizeForAccumulation(int readableBytes) {
        return (readableBytes << 2) / 3;
    }

    protected abstract void encodeInitialLine(Buffer buf, H message) throws Exception;

    protected Buffer crlfBuffer(BufferAllocator allocator) {
        if (crlfBufferSupplier == null) {
            crlfBufferSupplier = allocator.constBufferSupplier(CRLF);
        }
        return crlfBufferSupplier.get();
    }

    protected Buffer zeroCrlfCrlfBuffer(BufferAllocator allocator) {
        if (zeroCrlfCrlfBufferSupplier == null) {
            zeroCrlfCrlfBufferSupplier = allocator.constBufferSupplier(ZERO_CRLF_CRLF);
        }
        return zeroCrlfCrlfBufferSupplier.get();
    }
}
