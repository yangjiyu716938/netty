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
package io.netty5.channel.unix.tests;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class DetectPeerCloseWithoutReadTest {
    protected abstract EventLoopGroup newGroup();
    protected abstract Class<? extends ServerChannel> serverChannel();
    protected abstract Class<? extends Channel> clientChannel();

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void clientCloseWithoutServerReadIsDetectedNoExtraReadRequested() throws Exception {
        clientCloseWithoutServerReadIsDetected0(false);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void clientCloseWithoutServerReadIsDetectedExtraReadRequested() throws Exception {
        clientCloseWithoutServerReadIsDetected0(true);
    }

    private void clientCloseWithoutServerReadIsDetected0(final boolean extraReadRequested) throws Exception {
        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger bytesRead = new AtomicInteger();
            final int expectedBytes = 100;
            serverGroup = newGroup();
            clientGroup = newGroup();
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup);
            sb.channel(serverChannel());
            // Ensure we read only one message per read() call and that we need multiple read()
            // calls to consume everything.
            sb.childOption(ChannelOption.AUTO_READ, false);
            sb.childOption(ChannelOption.READ_HANDLE_FACTORY,
                    new FixedReadHandleFactory(1, expectedBytes / 10));
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new TestHandler(bytesRead, extraReadRequested, latch));
                }
            });

            serverChannel = sb.bind(new InetSocketAddress(0)).asStage().get();

            Bootstrap cb = new Bootstrap();
            cb.group(serverGroup);
            cb.channel(clientChannel());
            cb.handler(new ChannelHandler() { });
            Channel clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();
            Buffer buf = clientChannel.bufferAllocator().allocate(expectedBytes);
            buf.skipWritableBytes(expectedBytes);
            clientChannel.writeAndFlush(buf).addListener(clientChannel, ChannelFutureListeners.CLOSE);

            latch.await();
            assertEquals(expectedBytes, bytesRead.get());
        } finally {
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
            if (serverGroup != null) {
                serverGroup.shutdownGracefully();
            }
            if (clientGroup != null) {
                clientGroup.shutdownGracefully();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void serverCloseWithoutClientReadIsDetectedNoExtraReadRequested() throws Exception {
        serverCloseWithoutClientReadIsDetected0(false);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void serverCloseWithoutClientReadIsDetectedExtraReadRequested() throws Exception {
        serverCloseWithoutClientReadIsDetected0(true);
    }

    private void serverCloseWithoutClientReadIsDetected0(final boolean extraReadRequested) throws Exception {
        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger bytesRead = new AtomicInteger();
            final int expectedBytes = 100;
            serverGroup = newGroup();
            clientGroup = newGroup();
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup);
            sb.channel(serverChannel());
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            Buffer buf = ctx.bufferAllocator().allocate(expectedBytes);
                            buf.skipWritableBytes(expectedBytes);
                            ctx.writeAndFlush(buf).addListener(ctx.channel(), ChannelFutureListeners.CLOSE);
                            ctx.fireChannelActive();
                        }
                    });
                }
            });

            serverChannel = sb.bind(new InetSocketAddress(0)).asStage().get();

            Bootstrap cb = new Bootstrap();
            cb.group(serverGroup);
            cb.channel(clientChannel());
            // Ensure we read only one message per read() call and that we need multiple read()
            // calls to consume everything.
            cb.option(ChannelOption.AUTO_READ, false);
            cb.option(ChannelOption.READ_HANDLE_FACTORY,
                    new FixedReadHandleFactory(1, expectedBytes / 10));
            cb.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new TestHandler(bytesRead, extraReadRequested, latch));
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();

            latch.await();
            assertEquals(expectedBytes, bytesRead.get());
        } finally {
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverGroup != null) {
                serverGroup.shutdownGracefully();
            }
            if (clientGroup != null) {
                clientGroup.shutdownGracefully();
            }
        }
    }

    private static final class TestHandler extends SimpleChannelInboundHandler<Buffer> {
        private final AtomicInteger bytesRead;
        private final boolean extraReadRequested;
        private final CountDownLatch latch;

        TestHandler(AtomicInteger bytesRead, boolean extraReadRequested, CountDownLatch latch) {
            this.bytesRead = bytesRead;
            this.extraReadRequested = extraReadRequested;
            this.latch = latch;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
            bytesRead.addAndGet(msg.readableBytes());

            if (extraReadRequested) {
                // Because autoread is off, we call read to consume all data until we detect the close.
                ctx.read();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            latch.countDown();
            ctx.fireChannelInactive();
        }
    }
}
