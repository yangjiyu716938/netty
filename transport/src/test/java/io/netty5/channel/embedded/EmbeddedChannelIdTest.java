/*
 * Copyright 2015 The Netty Project
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
package io.netty5.channel.embedded;

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.BufferOutputStream;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelId;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EmbeddedChannelIdTest {
    @Test
    public void testSerialization() throws Exception {
        // test that a deserialized instance works the same as a normal instance (issue #2869)
        ChannelId normalInstance = EmbeddedChannelId.INSTANCE;

        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(1024);
        try (ObjectOutputStream outStream = new ObjectOutputStream(new BufferOutputStream(buf))) {
            outStream.writeObject(normalInstance);
        }

        ObjectInputStream inStream = new ObjectInputStream(new BufferInputStream(buf.send()));
        final ChannelId deserializedInstance;
        try {
            deserializedInstance = (ChannelId) inStream.readObject();
        } finally {
            inStream.close();
        }

        assertEquals(normalInstance, deserializedInstance);
        assertEquals(normalInstance.hashCode(), deserializedInstance.hashCode());
        assertEquals(0, normalInstance.compareTo(deserializedInstance));
    }
}
