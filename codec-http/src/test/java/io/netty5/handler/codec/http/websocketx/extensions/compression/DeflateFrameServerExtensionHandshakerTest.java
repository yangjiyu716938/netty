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
package io.netty5.handler.codec.http.websocketx.extensions.compression;

import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.netty5.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker.DEFLATE_FRAME_EXTENSION;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker.X_WEBKIT_DEFLATE_FRAME_EXTENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeflateFrameServerExtensionHandshakerTest {

    @Test
    public void testNormalHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker();

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, Collections.emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerFrameDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerFrameDeflateEncoder);
    }

    @Test
    public void testWebkitHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker();

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(X_WEBKIT_DEFLATE_FRAME_EXTENSION, Collections.emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerFrameDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerFrameDeflateEncoder);
    }

    @Test
    public void testFailedHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker();

        Map<String, String> parameters;
        parameters = new HashMap<>();
        parameters.put("unknown", "11");

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, parameters));

        // test
        assertNull(extension);
    }

}
