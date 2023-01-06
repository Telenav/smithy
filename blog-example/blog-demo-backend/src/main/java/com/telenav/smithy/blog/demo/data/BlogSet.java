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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.telenav.blog.model.BlogId;
import com.telenav.blog.model.Comment;
import com.telenav.blog.model.ReadBlogOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public class BlogSet {

    public final Map<BlogId, ReadBlogOutput> blogs;
    public final Map<BlogId, List<Comment>> comments;
    public final Map<BlogId, Map<String, byte[]>> assets;

    @JsonCreator
    public BlogSet(@JsonProperty(value = "blogs") Map<BlogId, ReadBlogOutput> blogs,
            @JsonProperty(value = "comments") Map<BlogId, List<Comment>> comments,
            @JsonProperty(value = "assets", required = false) Map<BlogId, Map<String, byte[]>> assets) {
        this.blogs = new LinkedHashMap<>(blogs);
        this.comments = new LinkedHashMap<>(comments);
        this.assets = assets == null ? new HashMap<>() : new HashMap<>(assets);
    }

    public static void main(String[] args) throws IOException {
        BlogSet set = load();
        System.out.println("BLOGSET " + set);

        Path dir = Paths.get("/tmp/bunpack");
        set.unpack(dir);
    }

    public static BlogSet load() throws IOException {
        ObjectMapper mapper = JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper())
                .configure(SerializationFeature.INDENT_OUTPUT, true);
        try (InputStream in = new GZIPInputStream(BlogSet.class.getResourceAsStream("blogs.json.gz"))) {
            if (in == null) {
                throw new IOException("blogs.json.gz not in jar");
            }
            return load(in);
        }
    }

    public void save(Path dest) throws IOException {
        ObjectMapper mapper = JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper())
                .configure(SerializationFeature.INDENT_OUTPUT, true);
        boolean gzip = dest.getFileName().toString().endsWith(".gz");
        try (OutputStream out = Files.newOutputStream(dest, CREATE, TRUNCATE_EXISTING, WRITE)) {
            if (gzip) {
                try (GZIPOutputStream gzout = new GZIPOutputStream(out)) {
                    mapper.writeValue(gzout, this);
                }
            } else {
                mapper.writeValue(out, this);
            }
        }
    }

    public static BlogSet load(InputStream in) throws IOException {
        ObjectMapper mapper = JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper())
                .configure(SerializationFeature.INDENT_OUTPUT, true);
        return mapper.readValue(in, BlogSet.class);
    }

    public void unpack(Path dir) throws IOException {
        ObjectMapper mapper = JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper())
                .configure(SerializationFeature.INDENT_OUTPUT, true);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new IOException("Exists and not dir: " + dir);
        }
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        for (Map.Entry<BlogId, ReadBlogOutput> e : blogs.entrySet()) {
            String dirName = e.getKey().get();
            Path blogDir = dir.resolve(dirName);
            Path pubDir = blogDir.resolve("published");
            maybeCreateDir(pubDir);
            Path blogFile = blogDir.resolve("body.json");
            try (OutputStream out = Files.newOutputStream(blogFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
                mapper.writeValue(out, e.getValue());
            }
            FileTime lastModified = FileTime.from(e.getValue().metadata().created());

            Files.setLastModifiedTime(blogFile, lastModified);
            if (e.getValue().published()) {
                Path blogShadow = pubDir.resolve("body.json");
                if (!Files.exists(blogShadow)) {
                    Files.createSymbolicLink(blogShadow, Paths.get("../body.json"));
                }
                Files.setLastModifiedTime(blogShadow, lastModified);
            }
            List<Comment> cmts = comments.get(e.getKey());
            if (cmts != null && !cmts.isEmpty()) {
                for (Comment c : cmts) {
                    FileTime cmtLastModified = FileTime.from(c.created());
                    Path commentFile = blogDir.resolve(c.commentId().get() + ".comment");
                    try (OutputStream out = Files.newOutputStream(commentFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
                        mapper.writeValue(out, c);
                    }
                    Files.setLastModifiedTime(commentFile, cmtLastModified);
                    Path commentPub = pubDir.resolve(commentFile.getFileName());
                    Path commentShadow = pubDir.resolve("..").resolve(commentFile.getFileName());
                    if (!Files.exists(commentPub)) {
                        Files.createSymbolicLink(commentPub, commentShadow);
                        Files.setLastModifiedTime(commentShadow, cmtLastModified);
                    }
                }
            }
            Map<String, byte[]> as = assets.get(e.getKey());
            Path filesDir = blogDir.resolve("files");
            maybeCreateDir(filesDir);
            if (as != null) {
                for (Map.Entry<String, byte[]> ae : as.entrySet()) {
                    Path fl = filesDir.resolve(ae.getKey());
                    try (OutputStream out = Files.newOutputStream(fl, WRITE, TRUNCATE_EXISTING, CREATE)) {
                        out.write(ae.getValue());
                    }
                }
            }
        }
    }

    private void maybeCreateDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    @Override
    public String toString() {
        return "blogs: " + blogs.keySet();
    }

}
