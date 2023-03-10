/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel;

/**
 * Implementations allow to influence how much data / messages are read per read loop invocation.
 */
public interface ReadHandleFactory {
    /**
     * Creates a new handle for the given {@link Channel}.
     *
     * @param channel   the {@link Channel} for which the {@link ReadHandle} is used.
     */
    ReadHandle newHandle(Channel channel);

    /**
     * Handle which allows to customize how data / messages are read.
     */
    interface ReadHandle {

        /**
         * Guess the capacity for the next receive buffer that is probably large enough to read all inbound data and
         * small enough not to waste its space.
         * <p>
         * This also assumes that the given read is about to happen, and may later be paired with a
         * {@link #lastRead(int, int, int)} call.
         * <p>
         * The implementation can return zero, if no reads should be prepared until after the next
         * {@link #readComplete()} call.
         */
        int prepareRead();

        /**
         * Notify the {@link ReadHandle} of the last read operation and its result.
         *
         * @param attemptedBytesRead    The number of  bytes the read operation did attempt to read.
         * @param actualBytesRead       The number of bytes from the previous read operation. This may be negative if a
         *                              read error occurs.
         * @param numMessagesRead       The number of messages read.
         * @return                      {@code true} if the read loop should continue reading, {@code false} otherwise.
         */
        boolean lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead);

        /**
         * Method that must be called once the read loop was completed.
         */
        void readComplete();
    }
}
