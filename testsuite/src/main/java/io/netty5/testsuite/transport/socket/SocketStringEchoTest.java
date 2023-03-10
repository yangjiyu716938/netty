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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.DelimiterBasedFrameDecoder;
import io.netty5.handler.codec.Delimiters;
import io.netty5.handler.codec.string.StringDecoder;
import io.netty5.handler.codec.string.StringEncoder;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class SocketStringEchoTest extends AbstractSocketTest {

    static final Random random = new Random();
    static final String[] data = new String[1024];

    static {
        for (int i = 0; i < data.length; i ++) {
            int eLen = random.nextInt(512);
            char[] e = new char[eLen];
            for (int j = 0; j < eLen; j ++) {
                e[j] = (char) ('a' + random.nextInt(26));
            }

            data[i] = new String(e);
        }
    }

    @Test
    @Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    public void testStringEcho(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testStringEcho);
    }

    public void testStringEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStringEcho(sb, cb, true);
    }

    @Test
    @Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    public void testStringEchoNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testStringEchoNotAutoRead);
    }

    public void testStringEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStringEcho(sb, cb, false);
    }

    private static void testStringEcho(ServerBootstrap sb, Bootstrap cb, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Promise<Void> serverDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> clientDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final StringEchoHandler sh = new StringEchoHandler(autoRead, serverDonePromise);
        final StringEchoHandler ch = new StringEchoHandler(autoRead, clientDonePromise);

        sb.childHandler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                sch.pipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
                sch.pipeline().addLast("decoder", new StringDecoder(ISO_8859_1));
                sch.pipeline().addBefore("decoder", "encoder", new StringEncoder(ISO_8859_1));
                sch.pipeline().addAfter("decoder", "handler", sh);
            }
        });

        cb.handler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                sch.pipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
                sch.pipeline().addLast("decoder", new StringDecoder(ISO_8859_1));
                sch.pipeline().addBefore("decoder", "encoder", new StringEncoder(ISO_8859_1));
                sch.pipeline().addAfter("decoder", "handler", ch);
            }
        });

        Channel sc = sb.bind().asStage().get();
        Channel cc = cb.connect(sc.localAddress()).asStage().get();
        for (String element : data) {
            String delimiter = random.nextBoolean() ? "\r\n" : "\n";
            cc.writeAndFlush(element + delimiter);
        }

        ch.donePromise.asFuture().asStage().sync();
        sh.donePromise.asFuture().asStage().sync();
        sh.channel.close().asStage().sync();
        ch.channel.close().asStage().sync();
        sc.close().asStage().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    static class StringEchoHandler extends SimpleChannelInboundHandler<String> {
        private final boolean autoRead;
        private final Promise<Void> donePromise;
        private int dataIndex;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();

        StringEchoHandler(boolean autoRead, Promise<Void> donePromise) {
            this.autoRead = autoRead;
            this.donePromise = donePromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
            if (!data[dataIndex].equals(msg)) {
                donePromise.tryFailure(new IllegalStateException("index: " + dataIndex + " didn't match!"));
                ctx.close();
                return;
            }

            if (channel.parent() != null) {
                String delimiter = random.nextBoolean() ? "\r\n" : "\n";
                channel.write(msg + delimiter);
            }

            if (++dataIndex >= data.length) {
                donePromise.setSuccess(null);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                donePromise.tryFailure(new IllegalStateException("exceptionCaught: " + ctx.channel(), cause));
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            donePromise.tryFailure(new IllegalStateException("channelInactive: " + ctx.channel()));
        }
    }
}
