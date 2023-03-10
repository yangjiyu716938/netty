/*
 * Copyright 2022 The Netty Project
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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.util.Send;
import io.netty5.channel.embedded.EmbeddedChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompressionTestUtils {

    static void assertDecodeInputThrows(EmbeddedChannel channel, byte[] bytes,
                                        Class<? extends Exception> exceptionClass) {
        Buffer in = channel.bufferAllocator().copyOf(bytes);
        assertThrows(exceptionClass, () -> channel.writeInbound(in));
    }

    static void assertDecodeInput(EmbeddedChannel channel, byte[] input, byte[] expected) {
        Buffer in = BufferAllocator.onHeapUnpooled().copyOf(input);
        assertTrue(channel.writeInbound(in));
        assertInbound(channel, expected);
    }

    static void assertInbound(EmbeddedChannel channel, byte[] expected) {
        try (Buffer expectedBuffer = channel.bufferAllocator().copyOf(expected);
             Buffer actual = channel.readInbound()) {
            assertEquals(expectedBuffer, actual);
        }
    }

    static void assertOutbound(EmbeddedChannel channel, byte[] expected) {
        try (Buffer expectedBuffer = channel.bufferAllocator().copyOf(expected);
             Buffer actual = channel.readOutbound()) {
            assertEquals(expectedBuffer, actual);
        }
    }

    static CompositeBuffer compose(BufferAllocator allocator, Supplier<Buffer> supplier) {
        List<Send<Buffer>> bufferList = new ArrayList<>();
        for (;;) {
            try (Buffer msg = supplier.get()) {
                if (msg == null) {
                    break;
                }
                bufferList.add(msg.send());
            }
        }
        return allocator.compose(bufferList);
    }

    private CompressionTestUtils() { }
}
