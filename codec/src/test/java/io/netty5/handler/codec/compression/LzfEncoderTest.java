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

import com.ning.compress.lzf.LZFDecoder;
import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;

public class LzfEncoderTest extends AbstractEncoderTest {

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new CompressionHandler(LzfCompressor.newFactory()));
    }

    @Override
    protected Buffer decompress(Buffer compressed, int originalLength) throws Exception {
        try (compressed) {
            byte[] compressedArray = new byte[compressed.readableBytes()];
            compressed.readBytes(compressedArray, 0, compressedArray.length);

            byte[] decompressed = LZFDecoder.decode(compressedArray);
            return channel.bufferAllocator().copyOf(decompressed);
        }
    }
}
