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
package io.netty5.handler.codec.http2;

import io.netty5.handler.codec.http.headers.HeaderValidationException;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.handler.codec.http2.headers.Http2Headers.PseudoHeaderName;
import io.netty5.util.internal.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map.Entry;

import static io.netty5.util.AsciiString.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultHttp2HeadersTest {

    @Test
    public void nullHeaderNameNotAllowed() {
        assertThrows(HeaderValidationException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                Http2Headers.newHeaders().add(null, "foo");
            }
        });
    }

    @Test
    public void emptyHeaderNameNotAllowed() {
        assertThrows(HeaderValidationException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                Http2Headers.newHeaders().add(StringUtil.EMPTY_STRING, "foo");
            }
        });
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIterating() {
        Http2Headers headers = newHeaders();

        verifyPseudoHeadersFirst(headers);
        verifyAllPseudoHeadersPresent(headers);
    }

    @Test
    public void testPseudoHeadersWithRemovePreservesPseudoIterationOrder() {
        Http2Headers headers = newHeaders();

        Http2Headers nonPseudoHeaders = Http2Headers.newHeaders();
        for (Entry<CharSequence, CharSequence> entry : headers) {
            if (entry.getKey().length() == 0 || entry.getKey().charAt(0) != ':' &&
                !nonPseudoHeaders.contains(entry.getKey())) {
                nonPseudoHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        assertFalse(nonPseudoHeaders.isEmpty());

        // Remove all the non-pseudo headers and verify
        for (Entry<CharSequence, CharSequence> nonPseudoHeaderEntry : nonPseudoHeaders) {
            assertTrue(headers.remove(nonPseudoHeaderEntry.getKey()));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }

        // Add back all non-pseudo headers
        for (Entry<CharSequence, CharSequence> nonPseudoHeaderEntry : nonPseudoHeaders) {
            headers.add(nonPseudoHeaderEntry.getKey(), of("goo"));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }
    }

    @Test
    public void testPseudoHeadersWithClearDoesNotLeak() {
        Http2Headers headers = newHeaders();

        assertFalse(headers.isEmpty());
        headers.clear();
        assertTrue(headers.isEmpty());

        // Combine 2 headers together, make sure pseudo headers stay up front.
        headers.add("name1", "value1").scheme("nothing");
        verifyPseudoHeadersFirst(headers);

        Http2Headers other = Http2Headers.newHeaders().add("name2", "value2").authority("foo");
        verifyPseudoHeadersFirst(other);

        headers.add(other);
        verifyPseudoHeadersFirst(headers);

        // Make sure the headers are what we expect them to be, and no leaking behind the scenes.
        assertEquals(4, headers.size());
        assertEquals("value1", headers.get("name1"));
        assertEquals("value2", headers.get("name2"));
        assertEquals("nothing", headers.scheme());
        assertEquals("foo", headers.authority());
    }

    @Test
    public void testSetHeadersOrdersPseudoHeadersCorrectly() {
        Http2Headers headers = newHeaders();
        Http2Headers other = Http2Headers.newHeaders().add("name2", "value2").authority("foo");

        headers.set(other);
        verifyPseudoHeadersFirst(headers);
        assertEquals(other.size(), headers.size());
        assertEquals("foo", headers.authority());
        assertEquals("value2", headers.get("name2"));
    }

    @Test
    public void testSetAllOrdersPseudoHeadersCorrectly() {
        Http2Headers headers = newHeaders();
        Http2Headers other = Http2Headers.newHeaders().add("name2", "value2").authority("foo");

        int headersSizeBefore = headers.size();
        headers.replace(other);
        verifyPseudoHeadersFirst(headers);
        verifyAllPseudoHeadersPresent(headers);
        assertEquals(headersSizeBefore + 1, headers.size());
        assertEquals("foo", headers.authority());
        assertEquals("value2", headers.get("name2"));
    }

    @Test
    public void testHeaderNameValidation() {
        final Http2Headers headers = newHeaders();

        assertThrows(HeaderValidationException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                headers.add(of("Foo"), of("foo"));
            }
        });
    }

    @Test
    public void testClearResetsPseudoHeaderDivision() {
        Http2Headers http2Headers = Http2Headers.newHeaders();
        http2Headers.method("POST");
        http2Headers.set("some", "value");
        http2Headers.clear();
        http2Headers.method("GET");
        assertEquals(1, http2Headers.names().size());
    }

    @Test
    public void testContainsNameAndValue() {
        Http2Headers headers = newHeaders();
        assertTrue(headers.contains("name1", "value2"));
        assertFalse(headers.contains("name1", "Value2"));
        assertTrue(headers.containsIgnoreCase("2name", "Value3"));
        assertFalse(headers.contains("2name", "Value3"));
    }

    @Test
    void setMustOverwritePseudoHeaders() {
        Http2Headers headers = newHeaders();
        // The headers are already populated with pseudo headers.
        headers.method(of("GET"));
        headers.path(of("/index2.html"));
        headers.status(of("101"));
        headers.authority(of("github.com"));
        headers.scheme(of("http"));
        headers.set(of(":protocol"), of("http"));
        assertEquals(of("GET"), headers.method());
        assertEquals(of("/index2.html"), headers.path());
        assertEquals(of("101"), headers.status());
        assertEquals(of("github.com"), headers.authority());
        assertEquals(of("http"), headers.scheme());
    }

    @ParameterizedTest(name = "{displayName} [{index}] name={0} value={1}")
    @CsvSource(value = {"upgrade,protocol1", "connection,close", "keep-alive,timeout=5", "proxy-connection,close",
            "transfer-encoding,chunked", "te,something-else"})
    void possibleToAddConnectionHeaders(String name, String value) {
        Http2Headers headers = newHeaders();
        headers.add(name, value);
        assertTrue(headers.contains(name, value));
    }

    private static void verifyAllPseudoHeadersPresent(Http2Headers headers) {
        for (PseudoHeaderName pseudoName : PseudoHeaderName.values()) {
            assertNotNull(headers.get(pseudoName.value()), () -> "did not find pseudo-header " + pseudoName);
        }
    }

    static void verifyPseudoHeadersFirst(Http2Headers headers) {
        CharSequence lastNonPseudoName = null;
        for (Entry<CharSequence, CharSequence> entry: headers) {
            if (entry.getKey().length() == 0 || entry.getKey().charAt(0) != ':') {
                lastNonPseudoName = entry.getKey();
            } else if (lastNonPseudoName != null) {
                fail("All pseudo headers must be first in iteration. Pseudo header " + entry.getKey() +
                        " is after a non pseudo header " + lastNonPseudoName);
            }
        }
    }

    private static Http2Headers newHeaders() {
        Http2Headers headers = Http2Headers.newHeaders();
        headers.add(of("name1"), of("value1"), of("value2"));
        headers.method(of("POST"));
        headers.add(of("2name"), of("value3"));
        headers.path(of("/index.html"));
        headers.status(of("200"));
        headers.authority(of("netty.io"));
        headers.add(of("name3"), of("value4"));
        headers.scheme(of("https"));
        headers.add(of(":protocol"), of("websocket"));
        return headers;
    }
}
