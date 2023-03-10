/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;

import static io.netty5.handler.codec.http2.Http2TestUtil.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class Http2EmptyDataFrameListenerTest {

    @Mock
    private Http2FrameListener frameListener;
    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Buffer nonEmpty;

    private Http2EmptyDataFrameListener listener;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        when(nonEmpty.readableBytes()).thenReturn(1);
        listener = new Http2EmptyDataFrameListener(frameListener, 2);
    }

    @Test
    public void testEmptyDataFrames() throws Http2Exception {
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, false);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    listener.onDataRead(ctx, 1, empty, 0, false);
                }
            });
        }
        verify(frameListener, times(2)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithNonEmptyInBetween() throws Http2Exception {
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, nonEmpty, 0, false);

            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, false);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    listener.onDataRead(ctx, 1, empty, 0, false);
                }
            });
        }
        verify(frameListener, times(4)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithEndOfStreamInBetween() throws Http2Exception {
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, true);

            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, false);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    listener.onDataRead(ctx, 1, empty, 0, false);
                }
            });
        }

        verify(frameListener, times(1)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithHeaderFrameInBetween() throws Http2Exception {
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onHeadersRead(ctx, 1, Http2Headers.emptyHeaders(), 0, true);

            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, false);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    listener.onDataRead(ctx, 1, empty, 0, false);
                }
            });
        }

        verify(frameListener, times(1)).onHeadersRead(eq(ctx), eq(1), eq(Http2Headers.emptyHeaders()), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithHeaderFrameInBetween2() throws Http2Exception {
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onHeadersRead(ctx, 1, Http2Headers.emptyHeaders(), 0, (short) 0, false, 0, true);

            listener.onDataRead(ctx, 1, empty, 0, false);
            listener.onDataRead(ctx, 1, empty, 0, false);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    listener.onDataRead(ctx, 1, empty, 0, false);
                }
            });
        }

        verify(frameListener, times(1)).onHeadersRead(eq(ctx), eq(1),
                eq(Http2Headers.emptyHeaders()), eq(0), eq((short) 0), eq(false), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(Buffer.class), eq(0), eq(false));
    }
}
