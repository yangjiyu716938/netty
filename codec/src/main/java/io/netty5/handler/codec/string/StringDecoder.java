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
package io.netty5.handler.codec.string;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.DelimiterBasedFrameDecoder;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.codec.MessageToMessageDecoder;

import java.nio.charset.Charset;

import static java.util.Objects.requireNonNull;

/**
 * Decodes a received {@link Buffer} into a {@link String}.  Please
 * note that this decoder must be used with a proper {@link ByteToMessageDecoder}
 * such as {@link DelimiterBasedFrameDecoder} or {@link LineBasedFrameDecoder}
 * if you are using a stream-based transport such as TCP/IP.  A typical setup for a
 * text-based line protocol in a TCP/IP socket would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link LineBasedFrameDecoder}(80));
 * pipeline.addLast("stringDecoder", new {@link StringDecoder}(CharsetUtil.UTF_8));
 *
 * // Encoder
 * pipeline.addLast("stringEncoder", new {@link StringEncoder}(CharsetUtil.UTF_8));
 * </pre>
 * and then you can use a {@link String} instead of a {@link Buffer}
 * as a message:
 * <pre>
 * void channelRead({@link ChannelHandlerContext} ctx, {@link String} msg) {
 *     ch.write("Did you say '" + msg + "'?\n");
 * }
 * </pre>
 */
public class StringDecoder extends MessageToMessageDecoder<Buffer> {

    // TODO Use CharsetDecoder instead.
    private final Charset charset;

    /**
     * Creates a new instance with the current system character set.
     */
    public StringDecoder() {
        this(Charset.defaultCharset());
    }

    /**
     * Creates a new instance with the specified character set.
     */
    public StringDecoder(Charset charset) {
        requireNonNull(charset, "charset");
        this.charset = charset;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer msg) throws Exception {
        ctx.fireChannelRead(msg.toString(charset));
    }
}
