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
package io.netty5.handler.ssl;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class OptionalSslHandlerTest {

    private static final String SSL_HANDLER_NAME = "sslhandler";
    private static final String HANDLER_NAME = "handler";

    @Mock
    private ChannelHandlerContext context;

    @Mock
    private SslContext sslContext;

    @Mock
    private ChannelPipeline pipeline;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(context.pipeline()).thenReturn(pipeline);
    }

    @Test
    public void handlerRemoved() throws Exception {
        OptionalSslHandler handler = new OptionalSslHandler(sslContext);
        try (Buffer payload = onHeapAllocator().copyOf("plaintext", UTF_8)) {
            handler.decode(context, payload);
            verify(pipeline).remove(handler);
        }
    }

    @Test
    public void handlerReplaced() throws Exception {
        final ChannelHandler nonSslHandler = Mockito.mock(ChannelHandler.class);
        OptionalSslHandler handler = new OptionalSslHandler(sslContext) {
            @Override
            protected ChannelHandler newNonSslHandler(ChannelHandlerContext context) {
                return nonSslHandler;
            }

            @Override
            protected String newNonSslHandlerName() {
                return HANDLER_NAME;
            }
        };
        try (Buffer payload = onHeapAllocator().copyOf("plaintext", UTF_8)) {
            handler.decode(context, payload);
            verify(pipeline).replace(handler, HANDLER_NAME, nonSslHandler);
        }
    }

    @Test
    public void sslHandlerReplaced() throws Exception {
        final SslHandler sslHandler = Mockito.mock(SslHandler.class);
        OptionalSslHandler handler = new OptionalSslHandler(sslContext) {
            @Override
            protected SslHandler newSslHandler(ChannelHandlerContext context, SslContext sslContext) {
                return sslHandler;
            }

            @Override
            protected String newSslHandlerName() {
                return SSL_HANDLER_NAME;
            }
        };
        try (Buffer payload = onHeapAllocator().copyOf(new byte[] { 22, 3, 1, 0, 5 })) {
            handler.decode(context, payload);
            verify(pipeline).replace(handler, SSL_HANDLER_NAME, sslHandler);
        }
    }

    @Test
    public void decodeBuffered() throws Exception {
        OptionalSslHandler handler = new OptionalSslHandler(sslContext);
        try (Buffer payload = onHeapAllocator().copyOf(new byte[] { 22, 3 })) {
            handler.decode(context, payload);
            verifyZeroInteractions(pipeline);
        }
    }
}
