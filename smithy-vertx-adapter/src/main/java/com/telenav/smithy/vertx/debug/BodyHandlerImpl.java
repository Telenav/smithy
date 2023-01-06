/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.vertx.debug;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.FileUploadImpl;
import io.vertx.ext.web.impl.RoutingContextInternal;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Tim Boudreau
 */
public class BodyHandlerImpl implements BodyHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BodyHandlerImpl.class);

    private long bodyLimit = DEFAULT_BODY_LIMIT;
    private boolean handleFileUploads;
    private String uploadsDir;
    private boolean mergeFormAttributes = DEFAULT_MERGE_FORM_ATTRIBUTES;
    private boolean deleteUploadedFilesOnEnd = DEFAULT_DELETE_UPLOADED_FILES_ON_END;
    private boolean isPreallocateBodyBuffer = DEFAULT_PREALLOCATE_BODY_BUFFER;
    private static final int DEFAULT_INITIAL_BODY_BUFFER_SIZE = 1024; //bytes

    public BodyHandlerImpl() {
        this(true, DEFAULT_UPLOADS_DIRECTORY);
    }

    public BodyHandlerImpl(boolean handleFileUploads) {
        this(handleFileUploads, DEFAULT_UPLOADS_DIRECTORY);
    }

    public BodyHandlerImpl(String uploadDirectory) {
        this(true, uploadDirectory);
    }

    private BodyHandlerImpl(boolean handleFileUploads, String uploadDirectory) {
        this.handleFileUploads = handleFileUploads;
        setUploadsDirectory(uploadDirectory);
    }
    
    static void log(String what) {
        System.out.println("BH: " + what);
    }

    @Override
    public void handle(RoutingContext context) {
        
        final HttpServerRequest request = context.request();
        final HttpServerResponse response = context.response();
        
        log("BodyHandlerImpl.handle " + request.method() + " " + request.uri());

        // we need to keep state since we can be called again on reroute
        if (!((RoutingContextInternal) context).seenHandler(RoutingContextInternal.BODY_HANDLER)) {
            ((RoutingContextInternal) context).visitHandler(RoutingContextInternal.BODY_HANDLER);
            
            log("  was not seen");

            // Check if a request has a request body.
            // A request with a body __must__ either have `transfer-encoding`
            // or `content-length` headers set.
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.3
            final long parsedContentLength = parseContentLengthHeader(request);

            if (!request.headers().contains(HttpHeaders.TRANSFER_ENCODING) && parsedContentLength == -1) {
                log("No transfer encoding, no parsed length - skip.");
                // there is no "body", so we can skip this handler
                context.next();
                return;
            }

            // before parsing the body we can already discard a bad request just by inspecting the content-length against
            // the body limit, this will reduce load, on the server by totally skipping parsing the request body
            if (bodyLimit != -1 && parsedContentLength != -1) {
                if (parsedContentLength > bodyLimit) {
                    log("  Body too large " + bodyLimit + " vs " + parsedContentLength);
                    context.fail(413);
                    return;
                }
            }

            // handle expectations
            // https://httpwg.org/specs/rfc7231.html#header.expect
            final String expect = request.getHeader(HttpHeaders.EXPECT);
            if (expect != null) {
                log("  have expect header '" + expect + "'");
                // requirements validation
                if (expect.equalsIgnoreCase("100-continue")) {
                    // A server that receives a 100-continue expectation in an HTTP/1.0 request MUST ignore that expectation.
                    if (request.version() != HttpVersion.HTTP_1_0) {
                        log("  write continue");
                        // signal the client to continue
                        response.writeContinue();
                    }
                } else {
                    // the server cannot meet the expectation, we only know about 100-continue
                    context.fail(417);
                    return;
                }
            }

            log("  create a BHandler");
            final BHandler handler = new BHandler(context, isPreallocateBodyBuffer ? parsedContentLength : -1);
            request
                    // resume the request (if paused)
                    .resume()
                    .handler(handler)
                    .endHandler(handler::end);
        } else {
            log("request already seen");
            // on reroute we need to re-merge the form params if that was desired
            if (mergeFormAttributes && request.isExpectMultipart()) {
                request.params().addAll(request.formAttributes());
            }

            context.next();
        }
    }

    @Override
    public BodyHandler setHandleFileUploads(boolean handleFileUploads) {
        this.handleFileUploads = handleFileUploads;
        return this;
    }

    @Override
    public BodyHandler setBodyLimit(long bodyLimit) {
        this.bodyLimit = bodyLimit;
        return this;
    }

    @Override
    public BodyHandler setUploadsDirectory(String uploadsDirectory) {
        this.uploadsDir = uploadsDirectory;
        return this;
    }

    @Override
    public BodyHandler setMergeFormAttributes(boolean mergeFormAttributes) {
        this.mergeFormAttributes = mergeFormAttributes;
        return this;
    }

    @Override
    public BodyHandler setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd) {
        this.deleteUploadedFilesOnEnd = deleteUploadedFilesOnEnd;
        return this;
    }

    @Override
    public BodyHandler setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer) {
        this.isPreallocateBodyBuffer = isPreallocateBodyBuffer;
        return this;
    }

    private long parseContentLengthHeader(HttpServerRequest request) {
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        try {
            long parsedContentLength = Long.parseLong(contentLength);
            return parsedContentLength < 0 ? -1 : parsedContentLength;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private class BHandler implements Handler<Buffer> {

        private static final int MAX_PREALLOCATED_BODY_BUFFER_BYTES = 65535;

        final RoutingContext context;
        final long contentLength;
        Buffer body;
        boolean failed;
        final AtomicInteger uploadCount = new AtomicInteger();
        final AtomicBoolean cleanup = new AtomicBoolean(false);
        boolean ended;
        long uploadSize = 0L;
        final boolean isMultipart;
        final boolean isUrlEncoded;

        public BHandler(RoutingContext context, long contentLength) {
            this.context = context;
            this.contentLength = contentLength;
            log("Have content length for " + context.request().method() + " " + context.request().uri());
            // the request clearly states that there should
            // be a body, so we respect the client and ensure
            // that the body will not be null
            if (contentLength != -1) {
                initBodyBuffer();
            }

            List<FileUpload> fileUploads = context.fileUploads();

            final String contentType = context.request().getHeader(HttpHeaders.CONTENT_TYPE);
            if (contentType == null) {
                isMultipart = false;
                isUrlEncoded = false;
            } else {
                log("HAVE CONTENT TYPE " + contentType);
                final String lowerCaseContentType = contentType.toLowerCase();
                isMultipart = lowerCaseContentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString());
                isUrlEncoded = lowerCaseContentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
            }

            if (isMultipart || isUrlEncoded) {
                context.request().setExpectMultipart(true);
                if (handleFileUploads) {
                    makeUploadDir(context.vertx().fileSystem());
                }
                context.request().uploadHandler(upload -> {
                    if (bodyLimit != -1 && upload.isSizeAvailable()) {
                        // we can try to abort even before the upload starts
                        long size = uploadSize + upload.size();
                        if (size > bodyLimit) {
                            failed = true;
                            cancelAndCleanupFileUploads();
                            context.fail(413);
                            return;
                        }
                    }
                    if (handleFileUploads) {
                        // we actually upload to a file with a generated filename
                        uploadCount.incrementAndGet();
                        String uploadedFileName = new File(uploadsDir, UUID.randomUUID().toString()).getPath();
                        FileUploadImpl fileUpload = new FileUploadImpl(uploadedFileName, upload);
                        fileUploads.add(fileUpload);
                        Future<Void> fut = upload.streamToFileSystem(uploadedFileName);
                        fut.onComplete(ar -> {
                            if (fut.succeeded()) {
                                log("upload ended");
                                uploadEnded();
                            } else {
                                cancelAndCleanupFileUploads();
                                context.fail(ar.cause());
                            }
                        });
                    }
                });
            } else {
                log("Not multipart, not url encoded");
            }

            context.request().exceptionHandler(t -> {
                cancelAndCleanupFileUploads();
                if (t instanceof DecoderException) {
                    // bad request
                    context.fail(400, t.getCause());
                } else {
                    context.fail(t);
                }
            });
        }

        private void initBodyBuffer() {
            log("initBodyBuffer");
            int initialBodyBufferSize;
            if (contentLength < 0) {
                initialBodyBufferSize = DEFAULT_INITIAL_BODY_BUFFER_SIZE;
            } else if (contentLength > MAX_PREALLOCATED_BODY_BUFFER_BYTES) {
                initialBodyBufferSize = MAX_PREALLOCATED_BODY_BUFFER_BYTES;
            } else {
                initialBodyBufferSize = (int) contentLength;
            }

            if (bodyLimit != -1) {
                initialBodyBufferSize = (int) Math.min(initialBodyBufferSize, bodyLimit);
            }

            this.body = Buffer.buffer(initialBodyBufferSize);
        }

        private void makeUploadDir(FileSystem fileSystem) {
            if (!fileSystem.existsBlocking(uploadsDir)) {
                fileSystem.mkdirsBlocking(uploadsDir);
            }
        }

        @Override
        public void handle(Buffer buff) {
            log("handle buffer " + buff + " of " + buff.length());
            if (failed) {
                return;
            }
            uploadSize += buff.length();
            System.out.println("  up size now " + uploadSize);
            if (bodyLimit != -1 && uploadSize > bodyLimit) {
                failed = true;
                cancelAndCleanupFileUploads();
                context.fail(413);
            } else {
                // multipart requests will not end up in the request body
                // url encoded should also not, however jQuery by default
                // post in urlencoded even if the payload is something else
                if (!isMultipart /* && !isUrlEncoded */) {
                    if (body == null) {
                        initBodyBuffer();
                    }
                    body.appendBuffer(buff);
                }
            }
        }

        void uploadEnded() {
            System.out.println("Upload ended.");
            int count = uploadCount.decrementAndGet();
            // only if parsing is done and count is 0 then all files have been processed
            if (ended && count == 0) {
                doEnd();
            }
        }

        void end(Void v) {
            log("end.");
            // this marks the end of body parsing, calling doEnd should
            // only be possible from this moment onwards
            ended = true;

            // only if parsing is done and count is 0 then all files have been processed
            if (uploadCount.get() == 0) {
                doEnd();
            }
        }

        void doEnd() {
            log("doEnd");
            if (failed) {
                cancelAndCleanupFileUploads();
                return;
            }

            if (deleteUploadedFilesOnEnd) {
                context.addBodyEndHandler(x -> cancelAndCleanupFileUploads());
            }

            HttpServerRequest req = context.request();
            if (mergeFormAttributes && req.isExpectMultipart()) {
                req.params().addAll(req.formAttributes());
            }
            log("setBody on context " + body);
            ((RoutingContextInternal) context).setBody(body);
            // release body as it may take lots of memory
            body = null;

            log("call next");
            context.next();
        }

        /**
         * Cancel all unfinished file upload in progress and delete all uploaded
         * files.
         */
        private void cancelAndCleanupFileUploads() {
            log("Cancel and cleanup uploads");
            if (cleanup.compareAndSet(false, true) && handleFileUploads) {
                for (FileUpload fileUpload : context.fileUploads()) {
                    FileSystem fileSystem = context.vertx().fileSystem();
                    if (!fileUpload.cancel()) {
                        String uploadedFileName = fileUpload.uploadedFileName();
                        fileSystem.delete(uploadedFileName, deleteResult -> {
                            if (deleteResult.failed()) {
                                LOG.warn("Delete of uploaded file failed: " + uploadedFileName, deleteResult.cause());
                            }
                        });
                    }
                }
            }
        }
    }

}
