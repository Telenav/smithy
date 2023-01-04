/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.telenav.smithy.vertx.debug;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author Tim Boudreau
 */
public interface BodyHandler extends Handler<RoutingContext> {

  /**
   * Default max size for a request body = {@code -1} means unlimited
   */
  long DEFAULT_BODY_LIMIT = -1;

  /**
   * Default uploads directory on server for file uploads
   */
  String DEFAULT_UPLOADS_DIRECTORY = "file-uploads";

  /**
   * Default value of whether form attributes should be merged into request params
   */
  boolean DEFAULT_MERGE_FORM_ATTRIBUTES = true;

  /**
   * Default value of whether uploaded files should be removed after handling the request
   */
  boolean DEFAULT_DELETE_UPLOADED_FILES_ON_END = false;

  /**
   * Default value of whether to pre-allocate the body buffer size according to the content-length HTTP request header
   */
  boolean DEFAULT_PREALLOCATE_BODY_BUFFER = false;

  /**
   * Create a body handler with defaults
   *
   * @return the body handler
   */
  static BodyHandler create() {
    return new BodyHandlerImpl();
  }

  /**
   * Create a body handler setting if it should handle file uploads
   *
   * @param handleFileUploads true if files upload should be handled
   * @return the body handler
   */
  static BodyHandler create(boolean handleFileUploads) {
    return new BodyHandlerImpl(handleFileUploads);
  }

  /**
   * Create a body handler and use the given upload directory.
   *
   * @param uploadDirectory  the uploads directory
   * @return the body handler
   */
  static BodyHandler create(String uploadDirectory) {
    return new BodyHandlerImpl(uploadDirectory);
  }

  /**
   * Set whether file uploads will be handled
   *
   * @param handleFileUploads  true if they should be handled
   * @return reference to this for fluency
   */
  BodyHandler setHandleFileUploads(boolean handleFileUploads);

  /**
   * Set the maximum body size in bytes, {@code -1} means no limit
   *
   * @param bodyLimit  the max size in bytes
   * @return reference to this for fluency
   */
  BodyHandler setBodyLimit(long bodyLimit);

  /**
   * Set the uploads directory to use
   *
   * @param uploadsDirectory  the uploads directory
   * @return reference to this for fluency
   */
  BodyHandler setUploadsDirectory(String uploadsDirectory);

  /**
   * Set whether form attributes will be added to the request parameters
   *
   * @param mergeFormAttributes  true if they should be merged
   * @return reference to this for fluency
   */
  BodyHandler setMergeFormAttributes(boolean mergeFormAttributes);

  /**
   * Set whether uploaded files should be removed after handling the request
   *
   * @param deleteUploadedFilesOnEnd  true if uploaded files should be removed after handling the request
   * @return reference to this for fluency
   */
  BodyHandler setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd);

  /**
   * Pre-allocate the body buffer according to the value parsed from content-length header.
   * The buffer is capped at 64KB
   * @param isPreallocateBodyBuffer {@code true} if body buffer is pre-allocated according to the size
   *                               read from content-length Header.
   *                               {code false} if body buffer is pre-allocated to 1KB, and is resized dynamically
   * @return reference to this for fluency
   */
  BodyHandler setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer);

}
