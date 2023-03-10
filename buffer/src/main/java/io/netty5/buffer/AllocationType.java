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
 * An object used by {@linkplain BufferAllocator buffer allocators} to communicate desirable properties of an
 * allocation to a {@linkplain MemoryManager memory manager}, such as whether an allocation should be off-heap.
 * <p>
 * Standard implementations of this interface can be found in {@link StandardAllocationTypes}.
 */
public interface AllocationType {
    /**
     * @return {@code true} if this allocation type produces off-heap, or direct buffers.
     * Otherwise {@code false}, if the buffers are on-heap.
     */
    boolean isDirect();
}
