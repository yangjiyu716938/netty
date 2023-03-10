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

import io.netty5.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
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

public class DeflateFrameClientExtensionHandshakerTest {

    @Test
    public void testWebkitDeflateFrameData() {
        DeflateFrameClientExtensionHandshaker handshaker =
                new DeflateFrameClientExtensionHandshaker(true);

        WebSocketExtensionData data = handshaker.newRequestData();

        assertEquals(X_WEBKIT_DEFLATE_FRAME_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());
    }

    @Test
    public void testDeflateFrameData() {
        DeflateFrameClientExtensionHandshaker handshaker =
                new DeflateFrameClientExtensionHandshaker(false);

        WebSocketExtensionData data = handshaker.newRequestData();

        assertEquals(DEFLATE_FRAME_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());
    }

    @Test
    public void testNormalHandshake() {
        DeflateFrameClientExtensionHandshaker handshaker =
                new DeflateFrameClientExtensionHandshaker(false);

        WebSocketClientExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, Collections.emptyMap()));

        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerFrameDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerFrameDeflateEncoder);
    }

    @Test
    public void testFailedHandshake() {
        // initialize
        DeflateFrameClientExtensionHandshaker handshaker =
                new DeflateFrameClientExtensionHandshaker(false);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("invalid", "12");

        // execute
        WebSocketClientExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, parameters));

        // test
        assertNull(extension);
    }
}
