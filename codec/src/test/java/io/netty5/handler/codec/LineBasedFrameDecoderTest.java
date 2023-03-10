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
package io.netty5.handler.codec;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, true, false));

        ch.writeInbound(ch.bufferAllocator().copyOf("first\r\nsecond\nthird", US_ASCII));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("first", buf.toString(US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("second", buf2.toString(US_ASCII));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeWithoutStrip() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        ch.writeInbound(ch.bufferAllocator().copyOf("first\r\nsecond\nthird", US_ASCII));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("first\r\n", buf.toString(US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("second\n", buf2.toString(US_ASCII));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testTooLongLine1() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, false));

        try {
            ch.writeInbound(
                    ch.bufferAllocator().copyOf("12345678901234567890\r\nfirst\nsecond", US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        try (Buffer buf = ch.readInbound();
             Buffer buf2 = ch.bufferAllocator().copyOf("first\n", US_ASCII)) {
            assertThat(buf, is(buf2));
        }

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testTooLongLine2() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, false));

        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf("12345678901234567", US_ASCII)));
        try {
            ch.writeInbound(ch.bufferAllocator().copyOf("890\r\nfirst\r\n", US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        try (Buffer buf = ch.readInbound();
             Buffer buf2 = ch.bufferAllocator().copyOf("first\r\n", US_ASCII)) {
            assertThat(buf, is(buf2));
        }

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testTooLongLineWithFailFast() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, true));

        try {
            ch.writeInbound(ch.bufferAllocator().copyOf("12345678901234567", US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        assertThat(ch.writeInbound(ch.bufferAllocator().copyOf("890", US_ASCII)), is(false));
        assertThat(ch.writeInbound(
                ch.bufferAllocator().copyOf("123\r\nfirst\r\n", US_ASCII)), is(true));

        try (Buffer buf = ch.readInbound();
             Buffer buf2 = ch.bufferAllocator().copyOf("first\r\n", US_ASCII)) {
            assertThat(buf, is(buf2));
        }

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testDecodeSplitsCorrectly() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf("line\r\n.\r\n", US_ASCII)));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("line\r\n", buf.toString(US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals(".\r\n", buf2.toString(US_ASCII));
        }

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testFragmentedDecode() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf("huu", US_ASCII)));
        assertNull(ch.readInbound());

        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf("haa\r", US_ASCII)));
        assertNull(ch.readInbound());

        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf("\nhuuhaa\r\n", US_ASCII)));
        try (Buffer buf = ch.readInbound()) {
            assertEquals("huuhaa\r\n", buf.toString(US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("huuhaa\r\n", buf2.toString(US_ASCII));
        }

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testEmptyLine() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, true, false));

        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf("\nabcna\r\n", US_ASCII)));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("", buf.toString(US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("abcna", buf2.toString(US_ASCII));
        }

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testNotFailFast() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(2, false, false));
        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 0, 1, 2 })));
        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[]{ 3, 4 })));
        try {
            ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { '\n' }));
            fail();
        } catch (TooLongFrameException expected) {
            // Expected once we received a full frame.
        }
        assertFalse(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { '5' })));
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { '\n' })));

        try (Buffer expected = ch.bufferAllocator().copyOf(new byte[] { '5', '\n' });
             Buffer buffer = ch.readInbound()) {
            assertEquals(expected, buffer);
        }

        assertFalse(ch.finish());
    }
}
