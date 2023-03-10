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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.util.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketCancelWriteTest extends AbstractSocketTest {
    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCancelWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testCancelWrite);
    }

    public void testCancelWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final TestHandler sh = new TestHandler();
        final TestHandler ch = new TestHandler();
        final Buffer a = preferredAllocator().allocate(1).writeByte((byte) 'a');
        final Buffer b = preferredAllocator().allocate(1).writeByte((byte) 'b');
        final Buffer c = preferredAllocator().allocate(1).writeByte((byte) 'c');
        final Buffer d = preferredAllocator().allocate(1).writeByte((byte) 'd');
        final Buffer e = preferredAllocator().allocate(1).writeByte((byte) 'e');

        cb.handler(ch);
        sb.childHandler(sh);

        Channel sc = sb.bind().asStage().get();
        Channel cc = cb.connect(sc.localAddress()).asStage().get();

        Future<Void> f = cc.write(a);
        assertTrue(f.cancel());
        cc.writeAndFlush(b);
        cc.write(c);
        Future<Void> f2 = cc.write(d);
        assertTrue(f2.cancel());
        cc.writeAndFlush(e);

        while (sh.counter.get() < 3) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }
            Thread.sleep(50);
        }
        sh.channel.close().asStage().sync();
        ch.channel.close().asStage().sync();
        sc.close().asStage().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
        assertEquals(0, ch.counter.get());
        assertNull(ch.received);
        try (Buffer buffer = preferredAllocator().copyOf(new byte[] { 'b', 'c', 'e' })) {
            assertEquals(buffer, sh.received);
        }
        Resource.dispose(sh.received);
    }

    private static class TestHandler extends SimpleChannelInboundHandler<Buffer> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();
        Buffer received;
        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception {
            counter.getAndAdd(in.readableBytes());
            if (received == null) {
                received = preferredAllocator().allocate(32);
            }
            received.writeBytes(in);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx,
                                           Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
