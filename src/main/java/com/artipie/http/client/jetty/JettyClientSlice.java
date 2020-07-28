/*
 * MIT License
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.http.client.jetty;

import com.artipie.asto.ext.PublisherAs;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.StandardRs;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.reactive.client.ReactiveRequest;
import org.reactivestreams.Publisher;

/**
 * ClientSlices implementation using Jetty HTTP client as back-end.
 *
 * @since 0.1
 * @todo #1:30min Test HTTPS connection with `JettyClientSlice`.
 *  `JettyClientSlice` is tested with plain HTTP server which is started in tests.
 *  However it should also be tested that class may work with HTTPS protocol as well.
 *  Such tests should be added to the project.
 */
final class JettyClientSlice implements Slice {

    /**
     * HTTP client.
     */
    private final HttpClient client;

    /**
     * Secure connection flag.
     */
    private final boolean secure;

    /**
     * Host name.
     */
    private final String host;

    /**
     * Port.
     */
    private final int port;

    /**
     * Ctor.
     *
     * @param client HTTP client.
     * @param secure Secure connection flag.
     * @param host Host name.
     * @param port Port.
     * @checkstyle ParameterNumberCheck (2 lines)
     */
    JettyClientSlice(
        final HttpClient client,
        final boolean secure,
        final String host,
        final int port
    ) {
        this.client = client;
        this.secure = secure;
        this.host = host;
        this.port = port;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        return new AsyncResponse(
            this.request(line, headers, body).thenCompose(
                request -> Flowable.fromPublisher(
                    ReactiveRequest.newBuilder(request).build().response(
                        (response, rsbody) -> Flowable.just(StandardRs.EMPTY)
                    )
                ).singleOrError().to(SingleInterop.get())
            )
        );
    }

    /**
     * Create request.
     *
     * @param line Request line.
     * @param headers Request headers.
     * @param body Request body.
     * @return Request built from parameters.
     */
    private CompletionStage<Request> request(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        final RequestLineFrom req = new RequestLineFrom(line);
        final String scheme;
        if (this.secure) {
            scheme = "https";
        } else {
            scheme = "http";
        }
        final URI uri = req.uri();
        final Request request = this.client.newRequest(
            new URIBuilder()
                .setScheme(scheme)
                .setHost(this.host)
                .setPort(this.port)
                .setPath(uri.getPath())
                .setCustomQuery(uri.getQuery())
                .toString()
        ).method(req.method().value());
        for (final Map.Entry<String, String> header : headers) {
            request.header(header.getKey(), header.getValue());
        }
        return new PublisherAs(body).bytes().thenApply(
            bytes -> {
                final Request result;
                if (bytes.length > 0) {
                    result = request.content(new BytesContentProvider(bytes));
                } else {
                    result = request;
                }
                return result;
            }
        );
    }
}
