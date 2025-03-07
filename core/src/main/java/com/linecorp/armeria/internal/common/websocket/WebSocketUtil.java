/*
 * Copyright 2022 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
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
package com.linecorp.armeria.internal.common.websocket;

import static java.util.Objects.requireNonNull;

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.websocket.CloseWebSocketFrame;
import com.linecorp.armeria.common.websocket.WebSocketCloseStatus;
import com.linecorp.armeria.common.websocket.WebSocketFrame;
import com.linecorp.armeria.server.websocket.WebSocketProtocolViolationException;

import io.netty.handler.codec.http.HttpHeaderValues;

public final class WebSocketUtil {

    public static boolean isHttp1WebSocketUpgradeRequest(RequestHeaders headers) {
        requireNonNull(headers, "headers");
        // GET /chat HTTP/1.1
        // Upgrade: websocket
        // Connection: Upgrade
        // ...
        return headers.method() == HttpMethod.GET &&
               HttpHeaderValues.UPGRADE.contentEqualsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION)) &&
               HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE));
    }

    public static boolean isHttp2WebSocketUpgradeRequest(RequestHeaders headers) {
        requireNonNull(headers, "headers");
        // HEADERS + END_HEADERS
        // :method = CONNECT
        // :protocol = websocket
        // ...
        return headers.method() == HttpMethod.CONNECT &&
               HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(headers.get(HttpHeaderNames.PROTOCOL));
    }

    static int byteAtIndex(int mask, int index) {
        return (mask >> 8 * (3 - index)) & 0xFF;
    }

    //TODO(minwoox): provide an exception handler that converts a cause into a CloseWebSocketFrame.
    public static CloseWebSocketFrame newCloseWebSocketFrame(Throwable cause) {
        final WebSocketCloseStatus closeStatus;
        if (cause instanceof WebSocketProtocolViolationException) {
            closeStatus = ((WebSocketProtocolViolationException) cause).closeStatus();
        } else {
            closeStatus = WebSocketCloseStatus.INTERNAL_SERVER_ERROR;
        }
        String reasonPhrase = cause.getMessage();
        if (reasonPhrase == null) {
            reasonPhrase = closeStatus.reasonPhrase();
        }
        return WebSocketFrame.ofClose(closeStatus, reasonPhrase);
    }

    private WebSocketUtil() {}
}
