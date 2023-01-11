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
package com.telenav.smithy.blog.demo.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.google.common.base.Charsets.UTF_8;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.telenav.smithy.http.InvalidInputException;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.Escaper;
import com.telenav.blog.model.BlogId;
import com.telenav.blog.model.BlogInfo;
import com.telenav.blog.model.BlogList;
import com.telenav.blog.model.BlogMetadata;
import com.telenav.blog.model.BlogUpdates;
import com.telenav.blog.model.Comment;
import com.telenav.blog.model.CommentId;
import com.telenav.blog.model.CommentsList;
import com.telenav.blog.model.InboundComment;
import com.telenav.blog.model.ListCommentsOutput;
import com.telenav.blog.model.NewBlogInput;
import com.telenav.blog.model.PutCommentInput;
import com.telenav.blog.model.ReadBlogOutput;
import static com.telenav.smithy.blog.demo.data.BlogCriteria.blogCriteria;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class BlogStore {

    private final Path dir;
    private final Map<BlogId, ReadBlogOutput> blogs = new LinkedHashMap<>();
    private final Map<BlogId, List<Comment>> comments = new HashMap<>();
    private final Map<BlogId, List<Comment>> unpublishedComments = new HashMap<>();
    private final ObjectMapper mapper;

    @Inject
    public BlogStore(@Named(value = "blogDir") Path dir, ObjectMapper mapper) throws IOException {
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

    public BlogStore(@Named(value = "blogDir") Path dir, ObjectMapper mapper, Path blogSet) throws IOException {
        this.mapper = mapper;
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            System.out.println("Unpack blog archive to " + dir);
            try (InputStream in = Files.newInputStream(blogSet, READ)) {
                BlogSet.load(in).unpack(dir);
            }
        }
        this.dir = dir;
        load();
        System.out.println("Loaded " + blogs.size() + " blogs");
    }

    private void load() throws IOException {
        List<Path> bodyJsons = new ArrayList<>();
        try (Stream<Path> str = Files.list(dir)){
        str.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("body.json")))
                    .forEach(blogDir -> {
                        bodyJsons.add(blogDir.resolve("body.json"));
                    });
        }
        Collections.sort(bodyJsons, (a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException ex) {
                Logger.getLogger(BlogStore.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        });
        for (Path bj : bodyJsons) {
            try {
                ReadBlogOutput o = mapper.readValue(bj.toFile(), ReadBlogOutput.class);
                blogs.put(o.id(), o);
            } catch (IOException ioe) {
                Logger.getLogger(BlogStore.class.getName()).log(Level.SEVERE, null, ioe);
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
            try (Stream<Path> str = Files.list(blogDir(id, published))){
                str.filter(pth -> pth.getFileName().toString().endsWith(".comment"))
                        .filter(pth -> Files.isRegularFile(pth))
                        .forEachOrdered(cmtFile -> {
                            try {
                                result.add(mapper.readValue(cmtFile.toFile(), Comment.class));
                            } catch (IOException ex) {
                                Logger.getLogger(BlogStore.class.getName()).log(Level.SEVERE, null, ex);
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

    public BlogId newBlog(NewBlogInput input) throws IOException {
        BlogId id = newBlogId(input);

        Path dir = blogDir(id, false);
        if (Files.exists(dir)) {
            throw new BlogAlreadyExistsException(id);
        }
        Instant when = Instant.now();
        BlogMetadata md = new BlogMetadata(when, input.synopsis().orElse(null), when, input.tags().orElse(null));
        ReadBlogOutput out = new ReadBlogOutput(id, input.title(), md, input.published(), input.body());

        Files.createDirectories(dir);
        Path file = blogFile(id, false);
        try (OutputStream outstream = Files.newOutputStream(file, CREATE, TRUNCATE_EXISTING, WRITE)) {
            mapper.writeValue(outstream, out);
        }
        if (input.published()) {
            Path pubFile = blogFile(id, true);
            if (!Files.exists(pubFile)) {
                Files.createDirectories(pubFile.getParent());
            }
            Path shadow = pubFile.getParent().resolve("..").resolve(file.getFileName());
            Files.createSymbolicLink(pubFile, shadow);
        }
        return id;
    }

    private BlogId newBlogId(NewBlogInput input) {
        String munged = ID_ESCAPER.escape(input.title().get().trim().toLowerCase());
        if (munged.length() > 24) {
            munged = munged.substring(0, 24);
            if (munged.charAt(munged.length() - 1) == '_') {
                munged = munged.substring(0, munged.length() - 1);
            }
        }
        return new BlogId(munged);
    }

    public enum CommentApprovalPolicy {
        APPROVE_BY_DEFAULT,
        APPROVE_IF_NO_URLS,
        MODERATE_BY_DEFAULT;

        public boolean isApproved(PutCommentInput input) {
            switch (this) {
                case APPROVE_BY_DEFAULT:
                    return true;
                case MODERATE_BY_DEFAULT:
                    return false;
                case APPROVE_IF_NO_URLS:
                    return !containsUrls(input.content());
                default:
                    throw new AssertionError(this);
            }
        }

        private boolean containsUrls(InboundComment input) {
            if (containsUrls(input.commentBody())) {
                return true;
            }
            if (input.title().isPresent()) {
                return containsUrls(input.title().get().get());
            }
            return false;
        }

        private boolean containsUrls(String what) {
            return what.contains("http:") || what.contains("https:")
                    || what.contains("tel:") || what.contains("mailto:");
        }
    }

    public CommentId addComment(PutCommentInput input,
            CommentApprovalPolicy policy) throws NoSuchAlgorithmException, IOException, NoSuchAlgorithmException, CommentAlreadyExistsException {
        BlogId blog = input.id();
        Path blogPath = blogFile(blog, false);
        if (!Files.exists(blogPath)) {
            throw new NoSuchBlogException(blog);
        }

        CommentId commentId = newCommentId(input, blogPath.getParent());
        InboundComment content = input.content();
        boolean approved = policy.isApproved(input);

        if (approved) {
            Path pubPath = blogDir(blog, true);
            if (!Files.exists(pubPath)) {
                throw new NoSuchBlogException(blog);
            }
        }
        Comment result = new Comment(content.title().orElse(null), content.commentBody(),
                content.author(), content.authorEmail().orElse(null), commentId, Instant.now(), approved);

        Path blogDir = blogPath.getParent();
        Path file = blogDir.resolve(commentId.get() + ".comment");
        if (Files.exists(file)) {
            // Should not happen, but to be safe
            throw new CommentAlreadyExistsException(blog, commentId);
        }

        try (OutputStream out = Files.newOutputStream(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
            mapper.writeValue(out, result);
        }

        if (approved) {
            Path pubFile = blogDir(blog, true);
            if (!Files.exists(pubFile)) {
                throw new NoSuchBlogException(blog);
            }
            Path shadow = pubFile.getParent().resolve("..").resolve(file.getFileName());
            Files.createSymbolicLink(pubFile, shadow);
            // Force a reload
            this.comments.remove(blog);
        }

        this.unpublishedComments.remove(blog);
        return commentId;
    }

    private CommentId newCommentId(PutCommentInput in, Path blogDir) throws NoSuchAlgorithmException {
        String txt = in.content().title().map(ttl -> ttl.get() + in.content().commentBody())
                .orElse(in.content().commentBody());
        MessageDigest dig = MessageDigest.getInstance("SHA-256");
        byte[] bytes = dig.digest(txt.getBytes(UTF_8));
        LongBuffer longs = ByteBuffer.wrap(bytes).asLongBuffer();
        StringBuilder result = new StringBuilder();
        while (longs.hasRemaining()) {
            result.append(Long.toString(longs.get(), 36));
        }
        if (result.length() > 16) {
            result.setLength(16);
        } else if (result.length() < 12) {
            while (result.length() < 12) {
                result.append('0');
            }
        }
        CommentId id = new CommentId(result.toString());
        if (Files.exists(blogDir.resolve(id.get()))) {
            throw new CommentAlreadyExistsException(in.id(), id);
        }
        return id;
    }

    public void updateBlog(BlogId blog, BlogUpdates updates) throws IOException {
        Path file = blogFile(blog);
        if (updates.isEmpty()) {
            throw new InvalidInputException("Updates are empty");
        }
        if (!Files.exists(file)) {
            throw new NoSuchBlogException(blog);
        }
        ReadBlogOutput orig = mapper.readValue(file.toFile(), ReadBlogOutput.class);

        BlogMetadata oldMd = orig.metadata();
        BlogMetadata md = new BlogMetadata(oldMd.created(),
                updates.synopsis().orElse(oldMd.synopsis().orElse(null)),
                Instant.now(), updates.tags().orElse(oldMd.tags().orElse(null))
        );

        ReadBlogOutput nue = new ReadBlogOutput(blog,
                updates.title().orElse(orig.title()),
                md,
                updates.published().orElse(orig.published()),
                updates.body().orElse(orig.body()));

        try (OutputStream output = Files.newOutputStream(file, 
                CREATE, WRITE, TRUNCATE_EXISTING)) {
            mapper.writeValue(output, nue);
        }
        blogs.put(blog, nue);
        if (orig.published() != nue.published()) {
            Path pubFile = blogDir(blog, true);
            if (nue.published()) {
                if (!Files.exists(pubFile)) {
                    Path shadow = pubFile.getParent().resolve("..")
                            .resolve(file.getFileName());
                    Files.createSymbolicLink(pubFile, shadow);
                }
            } else {
                FileUtils.deleteIfExists(pubFile);
            }
        }
    }

    public static class NoSuchBlogException extends RuntimeException {

        NoSuchBlogException(BlogId id) {
            super("No blog with id '" + id + "' exists");
        }
    }

    public static class BlogAlreadyExistsException extends RuntimeException {

        BlogAlreadyExistsException(BlogId id) {
            super("A blog with " + id + " already exists");
        }
    }

    public static class CommentAlreadyExistsException extends RuntimeException {

        CommentAlreadyExistsException(BlogId id, CommentId cid) {
            super("A comment with id " + cid + " for blog with " + id + " already exists");
        }
    }

    private static final BlogIdEscape ID_ESCAPER = new BlogIdEscape();

    static class BlogIdEscape implements Escaper {

        @Override
        public CharSequence escape(char c) {
            if (Character.isWhitespace(c)) {
                return "_";
            }
            if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                return Character.toString(c);
            }
            return "";
        }
    }

}
