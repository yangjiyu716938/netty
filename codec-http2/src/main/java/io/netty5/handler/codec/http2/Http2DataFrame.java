/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.util.Resource;
import io.netty5.util.internal.UnstableApi;

/**
 * HTTP/2 DATA frame.
 */
@UnstableApi
public interface Http2DataFrame extends Http2StreamFrame, Resource<Http2DataFrame> {

    /**
     * Frame padding to use. Will be non-negative and less than 256.
     */
    int padding();

    /**
     * Payload of DATA frame. Will not be {@code null}.
     */
    Buffer content();

    /**
     * Returns the number of bytes that are flow-controlled initially, so even if the {@link #content()} is consumed
     * this will not change.
     */
    int initialFlowControlledBytes();

    /**
     * Returns {@code true} if the END_STREAM flag is set.
     */
    boolean isEndStream();

    /**
     * Produce a copy of this data frame, which contain a copy of the frame {@linkplain #content() contents}.
     *
     * @return A copy of this data frame.
     */
    Http2DataFrame copy();
}
