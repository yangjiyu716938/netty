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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import net.jpountz.lz4.LZ4BlockInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class Lz4FrameEncoderTest extends AbstractEncoderTest {

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.bufferAllocator()).thenReturn(BufferAllocator.onHeapUnpooled());
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new CompressionHandler(Lz4Compressor.newFactory()));
    }

    @Override
    protected Buffer decompress(Buffer compressed, int originalLength) throws Exception {
        InputStream is = new BufferInputStream(compressed.send());
        LZ4BlockInputStream lz4Is = null;
        byte[] decompressed = new byte[originalLength];
        try {
            lz4Is = new LZ4BlockInputStream(is);
            int remaining = originalLength;
            while (remaining > 0) {
                int read = lz4Is.read(decompressed, originalLength - remaining, remaining);
                if (read > 0) {
                    remaining -= read;
                } else {
                    break;
                }
            }
            assertEquals(-1, lz4Is.read());
        } finally {
            if (lz4Is != null) {
                lz4Is.close();
            } else {
                is.close();
            }
        }

        return channel.bufferAllocator().copyOf(decompressed);
    }
}
