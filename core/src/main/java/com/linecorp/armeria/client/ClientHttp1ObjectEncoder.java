/*
 * Copyright 2020 LINE Corporation
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

package com.linecorp.armeria.client;

import java.net.InetSocketAddress;

import com.linecorp.armeria.common.Http1HeaderNaming;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.internal.common.ArmeriaHttpUtil;
import com.linecorp.armeria.internal.common.Http1ObjectEncoder;
import com.linecorp.armeria.internal.common.KeepAliveHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

final class ClientHttp1ObjectEncoder extends Http1ObjectEncoder implements ClientHttpObjectEncoder {

    private final Http1HeaderNaming http1HeaderNaming;
    private final KeepAliveHandler keepAliveHandler;

    ClientHttp1ObjectEncoder(Channel ch, SessionProtocol protocol, Http1HeaderNaming http1HeaderNaming,
                             KeepAliveHandler keepAliveHandler) {
        super(ch, protocol);
        this.http1HeaderNaming = http1HeaderNaming;
        this.keepAliveHandler = keepAliveHandler;
    }

    @Override
    public ChannelFuture doWriteHeaders(int id, int streamId, RequestHeaders headers, boolean endStream,
                                        ChannelPromise promise) {
        return writeNonInformationalHeaders(id, convertHeaders(headers, endStream), endStream, promise);
    }

    private HttpObject convertHeaders(RequestHeaders headers, boolean endStream) {
        final String method = headers.method().name();
        final HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method),
                                                       headers.path(), false);
        final io.netty.handler.codec.http.HttpHeaders nettyHeaders = req.headers();
        ArmeriaHttpUtil.toNettyHttp1ClientHeaders(headers, nettyHeaders, http1HeaderNaming);

        if (!nettyHeaders.contains(HttpHeaderNames.HOST)) {
            final InetSocketAddress remoteAddress = (InetSocketAddress) channel().remoteAddress();
            nettyHeaders.add(HttpHeaderNames.HOST, ArmeriaHttpUtil.authorityHeader(remoteAddress.getHostName(),
                                                                                   remoteAddress.getPort(),
                                                                                   protocol().defaultPort()));
        }

        if (endStream) {
            nettyHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);

            // Set or remove the 'content-length' header depending on request method.
            // See: https://datatracker.ietf.org/doc/html/rfc7230#section-3.3.2
            //
            // > A user agent SHOULD send a Content-Length in a request message when
            // > no Transfer-Encoding is sent and the request method defines a meaning
            // > for an enclosed payload body.  For example, a Content-Length header
            // > field is normally sent in a POST request even when the value is 0
            // > (indicating an empty payload body).  A user agent SHOULD NOT send a
            // > Content-Length header field when the request message does not contain
            // > a payload body and the method semantics do not anticipate such a
            // > body.
            switch (method) {
                case "POST":
                case "PUT":
                case "PATCH":
                    nettyHeaders.set(HttpHeaderNames.CONTENT_LENGTH, "0");
                    break;
                default:
                    nettyHeaders.remove(HttpHeaderNames.CONTENT_LENGTH);
            }
        } else if (HttpUtil.getContentLength(req, -1L) >= 0) {
            // Avoid the case where both 'content-length' and 'transfer-encoding' are set.
            nettyHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);
        } else {
            nettyHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        return req;
    }

    @Override
    protected ChannelFuture write(HttpObject obj, ChannelPromise promise) {
        return channel().write(obj, promise);
    }

    @Override
    protected void convertTrailers(HttpHeaders inputHeaders,
                                   io.netty.handler.codec.http.HttpHeaders outputHeaders) {
        ArmeriaHttpUtil.toNettyHttp1ClientTrailers(inputHeaders, outputHeaders, http1HeaderNaming);
    }

    @Override
    public KeepAliveHandler keepAliveHandler() {
        return keepAliveHandler;
    }

    @Override
    protected boolean isPing(int id) {
        final KeepAliveHandler keepAliveHandler = keepAliveHandler();
        return keepAliveHandler instanceof Http1ClientKeepAliveHandler &&
               ((Http1ClientKeepAliveHandler) keepAliveHandler).isPing(id);
    }
}
