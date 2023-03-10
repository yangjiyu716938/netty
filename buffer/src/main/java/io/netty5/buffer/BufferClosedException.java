/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer;

/**
 * An exception thrown when an operation is attempted on a {@link Buffer} when it has been closed.
 */
public final class BufferClosedException extends IllegalStateException {
    private static final long serialVersionUID = 85913332711192868L;

    public BufferClosedException() {
        this("This buffer is closed.");
    }

    public BufferClosedException(final String message) {
        super(message);
    }
}
