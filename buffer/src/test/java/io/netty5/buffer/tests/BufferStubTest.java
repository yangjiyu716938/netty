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
package io.netty5.buffer.tests;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferStub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BufferStubTest {

    @Test
    void testAllOverridesDefined() throws NoSuchMethodException {
        for (Method m : Buffer.class.getMethods()) {
           Method stubMethod = BufferStub.class.getMethod(m.getName(), m.getParameterTypes());
           assertEquals(BufferStub.class, stubMethod.getDeclaringClass(), stubMethod + " not overridden");
        }
    }
}
