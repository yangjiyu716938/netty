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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.testsuite.transport.TestsuitePermutation;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.SocketAddress;
import java.util.List;

@EnabledIf("isSupported")
public class DomainSocketReadAllocatorTest extends SocketReadAllocatorTest {

    static boolean isSupported() {
        return NioDomainSocketTestUtil.isSocketSupported();
    }

    @Override
    protected final SocketAddress newSocketAddress() {
        return SocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.domainSocket();
    }
}
