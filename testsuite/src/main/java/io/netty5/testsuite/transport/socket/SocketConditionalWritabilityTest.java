/*
 * Copyright 2018 The Netty Project
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
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.WriteBufferWaterMark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocketConditionalWritabilityTest extends AbstractSocketTest {
    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testConditionalWritability(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConditionalWritability);
    }

    public void testConditionalWritability(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final int expectedBytes = 100 * 1024 * 1024;
            final int maxWriteChunkSize = 16 * 1024;
            final CountDownLatch latch = new CountDownLatch(1);
            sb.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 16 * 1024));
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        private int bytesWritten;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            Resource.dispose(msg);
                            writeRemainingBytes(ctx);
                        }

                        @Override
                        public void flush(ChannelHandlerContext ctx) {
                            if (ctx.channel().isWritable()) {
                                writeRemainingBytes(ctx);
                            } else {
                                ctx.flush();
                            }
                        }

                        @Override
                        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                            if (ctx.channel().isWritable()) {
                                writeRemainingBytes(ctx);
                            }
                            ctx.fireChannelWritabilityChanged();
                        }

                        private void writeRemainingBytes(ChannelHandlerContext ctx) {
                            while (ctx.channel().isWritable() && bytesWritten < expectedBytes) {
                                int chunkSize = Math.min(expectedBytes - bytesWritten, maxWriteChunkSize);
                                bytesWritten += chunkSize;
                                Buffer buffer = ctx.bufferAllocator().allocate(chunkSize);
                                buffer.skipWritableBytes(chunkSize);
                                ctx.write(buffer);
                            }
                            ctx.flush();
                        }
                    });
                }
            });

            serverChannel = sb.bind().asStage().get();

            cb.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        private int totalRead;

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ctx.writeAndFlush(ctx.bufferAllocator().allocate(1).writeByte((byte) 0));
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Buffer) {
                                try (Buffer buffer = (Buffer) msg) {
                                    totalRead += buffer.readableBytes();
                                    if (totalRead == expectedBytes) {
                                        latch.countDown();
                                    }
                                }
                            } else {
                                Resource.dispose(msg);
                            }
                        }
                    });
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();
            latch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (clientChannel != null) {
                clientChannel.close();
            }
        }
    }
}
