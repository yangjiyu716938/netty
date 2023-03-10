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

import static io.netty5.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.CLIENT_MAX_WINDOW;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.CLIENT_NO_CONTEXT;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.PERMESSAGE_DEFLATE_EXTENSION;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.SERVER_MAX_WINDOW;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.SERVER_NO_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PerMessageDeflateServerExtensionHandshakerTest {

    @Test
    public void testNormalHandshake() {
        WebSocketServerExtension extension;
        WebSocketExtensionData data;
        Map<String, String> parameters;

        // initialize
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker();

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, Collections.emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newResponseData();

        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());

        // initialize
        parameters = new HashMap<>();
        parameters.put(CLIENT_MAX_WINDOW, null);
        parameters.put(CLIENT_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, Collections.emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newResponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());

        // initialize
        parameters = new HashMap<>();
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNull(extension);
    }

    @Test
    public void testCustomHandshake() {
        WebSocketServerExtension extension;
        Map<String, String> parameters;
        WebSocketExtensionData data;

        // initialize
        PerMessageDeflateServerExtensionHandshaker handshaker =
                new PerMessageDeflateServerExtensionHandshaker(6, true, 10, true, true);

        parameters = new HashMap<>();
        parameters.put(CLIENT_MAX_WINDOW, null);
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(CLIENT_NO_CONTEXT, null);
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newResponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertEquals("10", data.parameters().get(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("12", data.parameters().get(SERVER_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(CLIENT_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));

        // initialize
        parameters = new HashMap<>();
        parameters.put(SERVER_MAX_WINDOW, "12");
        parameters.put(SERVER_NO_CONTEXT, null);

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketExtension.RSV1, extension.rsv());
        assertTrue(extension.newExtensionDecoder() instanceof PerMessageDeflateDecoder);
        assertTrue(extension.newExtensionEncoder() instanceof PerMessageDeflateEncoder);

        // execute
        data = extension.newResponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertEquals(2, data.parameters().size());
        assertTrue(data.parameters().containsKey(SERVER_MAX_WINDOW));
        assertEquals("12", data.parameters().get(SERVER_MAX_WINDOW));
        assertTrue(data.parameters().containsKey(SERVER_NO_CONTEXT));

        // initialize
        parameters = new HashMap<>();

        // execute
        extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(PERMESSAGE_DEFLATE_EXTENSION, parameters));
        // test
        assertNotNull(extension);

        // execute
        data = extension.newResponseData();

        // test
        assertEquals(PERMESSAGE_DEFLATE_EXTENSION, data.name());
        assertTrue(data.parameters().isEmpty());
    }
}
