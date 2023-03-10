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
import io.netty5.buffer.Buffer;
import io.netty5.buffer.MemoryManager;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.DefaultFileRegion;
import io.netty5.channel.FileRegion;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketFileRegionTest extends AbstractSocketTest {

    static final byte[] data = new byte[1048576 * 10];

    static {
        ThreadLocalRandom.current().nextBytes(data);
    }

    @Test
    public void testFileRegion(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testFileRegion);
    }

    @Test
    public void testCustomFileRegion(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testCustomFileRegion);
    }

    @Test
    public void testFileRegionNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testFileRegionNotAutoRead);
    }

    @Test
    public void testFileRegionCountLargerThenFile(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testFileRegionCountLargerThenFile);
    }

    public void testFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, true);
    }

    public void testCustomFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, false);
    }

    public void testFileRegionNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, true);
    }

    public void testFileRegionCountLargerThenFile(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();

        sb.childHandler(new SimpleChannelInboundHandler<Buffer>() {
            @Override
            protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
                // Just drop the message.
            }
        });
        cb.handler(new ChannelHandler() { });

        Channel sc = sb.bind().asStage().get();
        Channel cc = cb.connect(sc.localAddress()).asStage().get();

        // Request file region which is bigger then the underlying file.
        FileRegion region = new DefaultFileRegion(
                new RandomAccessFile(file, "r").getChannel(), 0, data.length + 1024);

        Throwable result = cc.writeAndFlush(region).asStage().getCause();
        assertThat(result).isInstanceOf(IOException.class);
        cc.close().asStage().sync();
        sc.close().asStage().sync();
    }

    private static void testFileRegion0(
            ServerBootstrap sb, Bootstrap cb, final boolean autoRead, boolean defaultFileRegion)
            throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final int bufferSize = 1024;
        final File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        final Random random = ThreadLocalRandom.current();

        // Prepend random data which will not be transferred, so that we can test non-zero start offset
        final int startOffset = random.nextInt(8192);
        for (int i = 0; i < startOffset; i ++) {
            out.write(random.nextInt());
        }

        // .. and here comes the real data to transfer.
        out.write(data, bufferSize, data.length - bufferSize);

        // .. and then some extra data which is not supposed to be transferred.
        for (int i = random.nextInt(8192); i > 0; i --) {
            out.write(random.nextInt());
        }

        out.close();

        ChannelHandler ch = new SimpleChannelInboundHandler<>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (!autoRead) {
                    ctx.read();
                }
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close();
            }
        };
        TestHandler sh = new TestHandler(autoRead);

        sb.childHandler(sh);
        cb.handler(ch);

        Channel sc = sb.bind().asStage().get();

        Channel cc = cb.connect(sc.localAddress()).asStage().get();
        FileRegion region = new DefaultFileRegion(
                new RandomAccessFile(file, "r").getChannel(), startOffset, data.length - bufferSize);
        FileRegion emptyRegion = new DefaultFileRegion(new RandomAccessFile(file, "r").getChannel(), 0, 0);

        if (!defaultFileRegion) {
            region = new FileRegionWrapper(region);
            emptyRegion = new FileRegionWrapper(emptyRegion);
        }
        // Do write Buffer and then FileRegion to ensure that mixed writes work
        // Also, write an empty FileRegion to test if writing an empty FileRegion does not cause any issues.
        //
        // See https://github.com/netty/netty/issues/2769
        //     https://github.com/netty/netty/issues/2964
        try (Buffer buffer = MemoryManager.unsafeWrap(data)) {
            cc.write(buffer.readSplit(bufferSize));
        }
        cc.write(emptyRegion);
        cc.writeAndFlush(region);

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }

            Thread.sleep(50);
        }

        sh.channel.close().asStage().sync();
        cc.close().asStage().sync();
        sc.close().asStage().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }

        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }

        // Make sure we did not receive more than we expected.
        assertThat(sh.counter).isEqualTo(data.length);
    }

    private static class TestHandler extends SimpleChannelInboundHandler<Buffer> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        volatile int counter;

        TestHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual, 0, actual.length);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }
            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }

    private static final class FileRegionWrapper implements FileRegion {
        private final FileRegion region;

        FileRegionWrapper(FileRegion region) {
            this.region = region;
        }

        @Override
        public int refCnt() {
            return region.refCnt();
        }

        @Override
        public long position() {
            return region.position();
        }

        @Override
        public boolean release() {
            return region.release();
        }

        @Override
        public long transferred() {
            return region.transferred();
        }

        @Override
        public long count() {
            return region.count();
        }

        @Override
        public boolean release(int decrement) {
            return region.release(decrement);
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) throws IOException {
            return region.transferTo(target, position);
        }

        @Override
        public FileRegion retain() {
            region.retain();
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            region.retain(increment);
            return this;
        }

        @Override
        public FileRegion touch() {
            region.touch();
            return this;
        }

        @Override
        public FileRegion touch(Object hint) {
            region.touch(hint);
            return this;
        }
    }
}
