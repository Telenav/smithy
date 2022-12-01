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
import com.google.inject.name.Named;
import com.telenav.blog.model.BlogId;
import com.telenav.blog.model.BlogInfo;
import com.telenav.blog.model.BlogList;
import com.telenav.blog.model.Comment;
import com.telenav.blog.model.CommentsList;
import com.telenav.blog.model.ListCommentsOutput;
import com.telenav.blog.model.ReadBlogOutput;
import static com.telenav.smithy.blog.demo.data.BlogCriteria.blogCriteria;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public class NewBlogStore {

    private final Path dir;
    private final Map<BlogId, ReadBlogOutput> blogs = new LinkedHashMap<>();
    private final Map<BlogId, List<Comment>> comments = new HashMap<>();
    private final Map<BlogId, List<Comment>> unpublishedComments = new HashMap<>();
    private final ObjectMapper mapper;

    NewBlogStore(@Named(value = "blogDir") Path dir, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            System.out.println("Unpack blog archive to " + dir);
            BlogSet.load().unpack(dir);
        }
        this.dir = dir;
        load();
        System.out.println("Loaded " + blogs.size() + " blogs");
    }

    private void load() throws IOException {
        List<Path> bodyJsons = new ArrayList<>();
        Files.list(dir).filter(Files::isDirectory)
                .filter(p -> Files.exists(p.resolve("body.json")))
                .forEach(blogDir -> {
                    bodyJsons.add(blogDir.resolve("body.json"));
                });
        Collections.sort(bodyJsons, (a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException ex) {
                Logger.getLogger(NewBlogStore.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        });
        for (Path bj : bodyJsons) {
            try {
                ReadBlogOutput o = mapper.readValue(bj.toFile(), ReadBlogOutput.class);
                blogs.put(o.id(), o);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private static int compareBlogs(ReadBlogOutput a, ReadBlogOutput b) {
        return a.metadata().created().compareTo(b.metadata().created());
    }

    private void visitBlogs(Predicate<ReadBlogOutput> test, Consumer<ReadBlogOutput> c) {
        for (Map.Entry<BlogId, ReadBlogOutput> e : blogs.entrySet()) {
            ReadBlogOutput out = e.getValue();
            if (!test.test(out)) {
                continue;
            }
            c.accept(out);
        }
    }

    public Optional<Instant> mostRecentLastModified() {
        return mostRecentLastModified(blogCriteria());
    }

    public Optional<Instant> mostRecentLastModified(Predicate<ReadBlogOutput> test) {
        Instant result = null;
        for (Map.Entry<BlogId, ReadBlogOutput> e : blogs.entrySet()) {
            ReadBlogOutput out = e.getValue();
            if (!test.test(out)) {
                continue;
            }
            if (result == null) {
                result = out.metadata().lastModified().orElse(out.metadata().created());
            } else {
                Instant when = out.metadata().lastModified().orElse(out.metadata().created());
                if (when.isAfter(result)) {
                    result = when;
                }
            }
        }
        return Optional.ofNullable(result);
    }

    public Optional<ListCommentsOutput> comments(BlogId id, boolean published) throws IOException {
        ReadBlogOutput bl = blogs.get(id);
        if (bl == null || (published && !bl.published())) {
            return Optional.empty();
        }
        return Optional.of(new ListCommentsOutput(id, new CommentsList(commentsFor(id, published))));
    }

    private synchronized List<Comment> commentsFor(BlogId id, boolean published) {
        Map<BlogId, List<Comment>> map = published ? comments : unpublishedComments;
        boolean present = map.containsKey(id);
        List<Comment> result = present ? map.get(id) : new ArrayList<>();
        if (!present) {
            try {
                Files.list(blogDir(id, published)).filter(pth -> pth.getFileName().toString().endsWith(".comment"))
                        .filter(pth -> Files.isRegularFile(pth))
                        .forEachOrdered(cmtFile -> {
                            try {
                                result.add(mapper.readValue(cmtFile.toFile(), Comment.class));
                            } catch (IOException ex) {
                                Logger.getLogger(NewBlogStore.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        result.sort((a, b) -> {
            return a.created().compareTo(b.created());
        });
        return result;
    }

    public Path blogFile(BlogId id) {
        return blogFile(id, true);
    }

    public Path blogDir(BlogId id, boolean published) {
        Path base = dir.resolve(id.get());
        if (published) {
            base = base.resolve("published");
        }
        return base;
    }

    public Path blogFile(BlogId id, boolean published) {
        Path base = dir.resolve(id.get());
        return blogDir(id, published).resolve("body.json");
    }

    public BlogList list() throws IOException {
        return list(blogCriteria());
    }

    public BlogList list(Predicate<ReadBlogOutput> test) throws IOException {
        BlogList result = new BlogList();
        visitBlogs(test, rbo -> {
            result.add(BlogInfo.builder().withId(rbo.id())
                    .withPublished(rbo.published())
                    .withTitle(rbo.title())
                    .buildWithMetadata(rbo.metadata()));
        });
        return result;
    }

    public String blogHash(BlogId id) {
        return blog(id).map(rbo -> {
            return Integer.toString(rbo.body().hashCode(), 36)
                    + Integer.toString(rbo.title().hashCode(), 36)
                    + Long.toString(rbo.metadata().lastModified().orElse(rbo.metadata().created()).toEpochMilli(), 36);
        }).orElse("0000");
    }

    public Optional<ReadBlogOutput> blog(BlogId id) {
        return Optional.ofNullable(blogs.get(id));
    }

}
