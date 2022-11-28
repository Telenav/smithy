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
package com.telenav.smithy.blog.demo.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.telenav.blog.model.BlogId;
import com.telenav.blog.model.BlogInfo;
import com.telenav.blog.model.BlogList;
import com.telenav.blog.model.BlogMetadata;
import com.telenav.blog.model.Comment;
import com.telenav.blog.model.CommentId;
import com.telenav.blog.model.CommentsList;
import com.telenav.blog.model.ListCommentsOutput;
import com.telenav.blog.model.ReadBlogOutput;
import com.telenav.blog.model.Title;
import com.telenav.smithy.blog.demo.BlogDemo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class BlogStore {

    private final Path dir;
    private BlogList list;
    private final Map<Path, RawBlog> rawBlogs = new HashMap<>();
    private final ObjectMapper mapper;
    private Instant newest;

    @Inject
    public BlogStore(@Named(value = "blogDir") Path dir, ObjectMapper mapper) {
        this.dir = dir;
        this.mapper = mapper;
    }

    public Path blogFile(BlogId id) {
        return dir.resolve(id.get()).resolve("published").resolve("body.json");
    }

    public Optional<Instant> mostRecentLastModified() {
        return Optional.ofNullable(newest);
    }

    public Optional<ListCommentsOutput> comments(BlogId id, boolean published) throws IOException {
        Path blogDir = dir.resolve(id.get());
        if (published) {
            blogDir = blogDir.resolve("published");
        }
        if (!Files.exists(blogDir)) {
            return Optional.empty();
        }
        CommentsList list = new CommentsList();
        Files.list(blogDir).filter(file -> !Files.isDirectory(file) && file.getFileName().toString().endsWith(".comment"))
                .forEachOrdered(cmt -> {
                    try {
                        RawComment comment = mapper.readValue(cmt.toFile(), RawComment.class);
                        CommentId cid = new CommentId(comment.getId(cmt));
                        if (comment.isViable()) {
                            list.add(new Comment(new Title(comment.title),
                                    comment.comment,
                                    comment.author, comment.email,
                                    cid,
                                    comment.date,
                                    published));
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(BlogStore.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
        ListCommentsOutput out = new ListCommentsOutput(id, list);
        return Optional.of(out);
    }

    public String blogHash(BlogId id) {
        Path blf = blogFile(id);
        if (!Files.exists(blf)) {
            return "0000";
        }
        RawBlog raw = rawBlog(blf);
        return Long.toString(raw.hash(), 36);
    }

    public Optional<ReadBlogOutput> blog(BlogId id) {
        Path blf = blogFile(id);
        if (!Files.exists(blf)) {
            return Optional.empty();
        }
        RawBlog raw = rawBlog(blf);
        if (!raw.isViable()) {
            return Optional.empty();
        }
        return Optional.of(
                new ReadBlogOutput(
                        id,
                        new Title(raw.title),
                        BlogMetadata.builder()
                                .withSynopsis(raw.synopsis())
                                .withLastModified(raw.lastModified())
                                .withTags(raw.tags())
                                .buildWithCreated(raw.date), true, raw.body));
    }

    public BlogList list() throws IOException {
        if (list == null) {
            synchronized (this) {
                if (list == null) {
                    list = loadList();
                }
            }
        }
        return list;
    }

    private BlogList loadList() throws IOException {
        BlogList l = new BlogList();
        Files.list(dir).filter(Files::isDirectory).forEach(dir -> {
            Path pubFile = dir.resolve("published/body.json");
            if (Files.exists(pubFile)) {
                BlogInfo info = loadBlogInfo(pubFile);
                l.add(info);
            }
        });
        Collections.sort(l, (a, b) -> {
            return b.metadata().created().compareTo(a.metadata().created());
        });
        return l;
    }

    private BlogInfo loadBlogInfo(Path pubFile) {
        RawBlog bl = rawBlog(pubFile);
        if (newest == null) {
            newest = bl.lastModified();
        } else if (bl.lastModified().isAfter(newest)) {
            newest = bl.lastModified();
        }
        if (!bl.isViable()) {
            return null;
        }
        return BlogInfo.builder().withId(
                new BlogId(pubFile.getParent().getParent().getFileName().toString()))
                .withTitle(new Title(bl.title))
                .withMetadata(
                        BlogMetadata.builder()
                                .withSynopsis(bl.synopsis())
                                .withLastModified(bl.lastModified())
                                .withTags(bl.tags())
                                .buildWithCreated(bl.date))
                .buildWithPublished(true);
    }

    private RawBlog rawBlog(Path pubFile) {
        return rawBlogs.computeIfAbsent(pubFile, fl -> {
            try {
                return mapper.readValue(fl.toFile(), RawBlog.class);
            } catch (IOException ex) {
                Logger.getLogger(BlogDemo.class.getName()).log(Level.SEVERE, null, ex);
                return new RawBlog();
            }
        });
    }

}
