/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http.websocketx;

import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.headers.HttpHeaders;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;

public class WebSocketRequestBuilder {

    private HttpVersion httpVersion;
    private HttpMethod method;
    private String uri;
    private String host;
    private String upgrade;
    private String connection;
    private String key;
    private String origin;
    private WebSocketVersion version;

    public WebSocketRequestBuilder httpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    public WebSocketRequestBuilder method(HttpMethod method) {
        this.method = method;
        return this;
    }

    public WebSocketRequestBuilder uri(CharSequence uri) {
        if (uri == null) {
            this.uri = null;
        } else {
            this.uri = uri.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder host(CharSequence host) {
        if (host == null) {
            this.host = null;
        } else {
            this.host = host.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder upgrade(CharSequence upgrade) {
        if (upgrade == null) {
            this.upgrade = null;
        } else {
            this.upgrade = upgrade.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder connection(CharSequence connection) {
        if (connection == null) {
            this.connection = null;
        } else {
            this.connection = connection.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder key(CharSequence key) {
        if (key == null) {
            this.key = null;
        } else {
            this.key = key.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder origin(CharSequence origin) {
        if (origin == null) {
            this.origin = null;
        } else {
            this.origin = origin.toString();
        }
        return this;
    }

    public WebSocketRequestBuilder version13() {
        version = WebSocketVersion.V13;
        return this;
    }

    public WebSocketRequestBuilder noVersion() {
        return this;
    }

    public FullHttpRequest build() {
        FullHttpRequest req = new DefaultFullHttpRequest(httpVersion, method, uri, preferredAllocator().allocate(0));
        HttpHeaders headers = req.headers();

        if (host != null) {
            headers.set(HttpHeaderNames.HOST, host);
        }
        if (upgrade != null) {
            headers.set(HttpHeaderNames.UPGRADE, upgrade);
        }
        if (connection != null) {
            headers.set(HttpHeaderNames.CONNECTION, connection);
        }
        if (key != null) {
            headers.set(HttpHeaderNames.SEC_WEBSOCKET_KEY, key);
        }
        if (origin != null) {
            headers.set(HttpHeaderNames.ORIGIN, origin);
        }
        if (version != null) {
            headers.set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, version.toHttpHeaderValue());
        }
        return req;
    }

    public static HttpRequest successful() {
        return new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .host("server.example.com")
                .upgrade(HttpHeaderValues.WEBSOCKET)
                .connection(HttpHeaderValues.UPGRADE)
                .key("dGhlIHNhbXBsZSBub25jZQ==")
                .origin("http://example.com")
                .version13()
                .build();
    }
}
