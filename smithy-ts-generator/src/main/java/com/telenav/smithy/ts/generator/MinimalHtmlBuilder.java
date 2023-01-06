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
package com.telenav.smithy.ts.generator;

import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.code.generation.common.util.Holder;
import com.telenav.smithy.ts.vogon.TypescriptCodeGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Quick'n'dirty html/css.
 *
 * @author Tim Boudreau
 */
public class MinimalHtmlBuilder extends TypescriptCodeGenerator {

    private final List<CodeGenerator> tags = new ArrayList<>();
    private final Tag<MinimalHtmlBuilder> html = new Tag<>("html", t -> this);
    private final Tag<Tag<MinimalHtmlBuilder>> head = new Tag<>("head", t -> html);
    private final Tag<Tag<MinimalHtmlBuilder>> body = new Tag<>("body", t -> html);
    private final StyleTag style = new StyleTag();
    private final TrailingTags trailers = new TrailingTags();

    private final String fileName;

    public MinimalHtmlBuilder(String fileName) {
        this.fileName = fileName;
        tags.add(html);
        html.add(head);
        head.add(style);
        html.add(body);
    }

    public static void main(String[] args) {
        MinimalHtmlBuilder h = new MinimalHtmlBuilder()
                .withTitle("An HTML PAGE")
                .style(sty -> {
                    sty.tagRule("body", srb -> {
                        srb.percentage("width", 100)
                                .add("font-family", "Helvetica");
                    }).classRule("thing", srb -> {
                        srb.add("font-weight", "bold");
                        srb.add("color", "#22222e")
                                .add("background", "white");
                    });
                })
                .head(head -> {
                    head.tag("meta", tg -> {
                        tg.selfClosing()
                                .setAttribute("name", "viewport")
                                .setAttribute("content",
                                        "width=device-width, initial-scale=1.0");
                    })
                            .tag("link")
                            .noClose()
                            .setAttribute("rel", "prefetch")
                            .setAttribute("href", "/blog/list")
                            .close();
                }).body(body -> {
            body.tag("h1").append("Whatever").close();
            body.tag("div", div -> {
                div.setAttribute("class", "content");
                div.append(txt -> {
                    txt.append("This here is some stuff.\nAnd more and more stuff.");
                });
                div.tag("div", sub -> {
                    sub.setAttribute("class", "row");
                    sub.tag("input", in -> {
                        in.setAttribute("type", "text")
                                .setAttribute("value", "This is some stuff")
                                .noClose();
                    });
                });
            });
        })
                .withScript("./foo.js");

        System.out.println(h);

    }

    public MinimalHtmlBuilder() {
        this("index.html");
    }

    @Override
    public void generateInto(LinesBuilder lines) {
        if (!body.contents.contains(trailers)) {
            body.contents.add(trailers);
        }
        lines.appendRaw("<!doctype html>").onNewLine();
        for (CodeGenerator cg : tags) {
            cg.generateInto(lines);
            if (cg == body) {
                trailers.generateInto(lines);
            }
        }
    }

    public MinimalHtmlBuilder withScript(String script) {
        Tag<Void> tg = new Tag<>("script", t -> null);
        tg.selfClosing();
        tg.setAttribute("src", script);
        trailers.tags.add(tg);
        return this;
    }

    public MinimalHtmlBuilder withTitle(String script) {
        Tag<Void> tg = new Tag<>("title", t -> null);
        tg.append(script);
        head.add(tg);
        return this;
    }

    public Tag<Tag<MinimalHtmlBuilder>> body() {
        return body;
    }

    public Tag<Tag<MinimalHtmlBuilder>> head() {
        return head;
    }

    public StyleTag style() {
        return style;
    }

    public MinimalHtmlBuilder style(Consumer<? super StyleTag> c) {
        c.accept(style);
        return this;
    }

    public MinimalHtmlBuilder body(Consumer<? super Tag<Tag<MinimalHtmlBuilder>>> c) {
        c.accept(body);
        return this;
    }

    public MinimalHtmlBuilder head(Consumer<? super Tag<Tag<MinimalHtmlBuilder>>> c) {
        c.accept(head);
        return this;
    }

    public enum CloseStyle {
        STANDARD,
        SELF_CLOSING,
        NO_CLOSE;

        void close(String tag, LinesBuilder lines, boolean hasContent) {
            switch (this) {
                case STANDARD:
                    lines.appendRaw("</" + tag + ">");
                case NO_CLOSE:
                    return;
                case SELF_CLOSING:
                    return;
            }
        }

        boolean canSelfClose() {
            return this == STANDARD || this == SELF_CLOSING;
        }
    }

    static class TrailingTags implements CodeGenerator {

        private final List<CodeGenerator> tags = new ArrayList<>();

        @Override
        public void generateInto(LinesBuilder lines) {
            tags.forEach(lines::generateOrPlaceholder);
        }
    }

    public static class StyleTag implements CodeGenerator {

        private final List<CodeGenerator> items = new ArrayList<>();

        @Override
        public void generateInto(LinesBuilder lines) {
            if (items.isEmpty()) {
                return;
            }
            lines.onNewLine().appendRaw("<style>");
            lines.indent(lb -> {
                for (CodeGenerator cg : items) {
                    cg.generateInto(lines);
                }
            });
            lines.onNewLine();
            lines.appendRaw("</style>").onNewLine();
        }

        public StyleTag classRule(String clazz, Consumer<? super StyleRuleBuilder<Void>> c) {
            StyleRuleBuilder<Void> result = new StyleRuleBuilder<>(clazz, StyleRuleTarget.CLASS, srb -> null);
            c.accept(result);
            items.add(result);
            return this;
        }

        public StyleRuleBuilder<StyleTag> classRule(String clazz) {
            return new StyleRuleBuilder<>(clazz, StyleRuleTarget.CLASS, srb -> {
                items.add(srb);
                return this;
            });
        }

        public StyleRuleBuilder<StyleTag> idRule(String clazz) {
            return new StyleRuleBuilder<>(clazz, StyleRuleTarget.ID, srb -> {
                items.add(srb);
                return this;
            });
        }

        public StyleTag idRule(String clazz, Consumer<? super StyleRuleBuilder<Void>> c) {
            StyleRuleBuilder<Void> result = new StyleRuleBuilder<>(clazz, StyleRuleTarget.ID, srb -> null);
            c.accept(result);
            items.add(result);
            return this;
        }

        public StyleRuleBuilder<StyleTag> tagRule(String clazz) {
            return new StyleRuleBuilder<>(clazz, StyleRuleTarget.TAG, srb -> {
                items.add(srb);
                return this;
            });
        }

        public StyleTag tagRule(String clazz, Consumer<? super StyleRuleBuilder<Void>> c) {
            StyleRuleBuilder<Void> result = new StyleRuleBuilder<>(clazz, StyleRuleTarget.TAG, srb -> null);
            c.accept(result);
            items.add(result);
            return this;
        }

        public StyleRuleBuilder<StyleTag> fontFaceRule() {
            return new StyleRuleBuilder<>("font-face", StyleRuleTarget.IMPORT, srb -> {
                items.add(srb);
                return this;
            });
        }

        public StyleTag fontFaceRule(Consumer<StyleRuleBuilder<Void>> c) {
            StyleRuleBuilder<Void> result = new StyleRuleBuilder<>("font-face", StyleRuleTarget.TAG, srb -> null);
            c.accept(result);
            items.add(result);
            return this;
        }

    }

    enum StyleRuleTarget {
        TAG,
        ID,
        CLASS,
        IMPORT;

        String prefix() {
            switch (this) {
                case ID:
                    return "#";
                case CLASS:
                    return ".";
                case IMPORT:
                    return "@";
                default:
                    return "";
            }
        }

    }

    static class Suffixed {

        private final Object what;
        private final String suffix;

        public Suffixed(Object what, String suffix) {
            this.what = what;
            this.suffix = suffix;
        }

        @Override
        public String toString() {
            return what + suffix;
        }
    }

    public static class StyleRuleBuilder<T> implements CodeGenerator {

        private final StyleRuleTarget targ;
        private final Function<StyleRuleBuilder<T>, T> conv;
        private final String name;
        private final Map<String, String> qualifiers = new TreeMap<>();
        private final Map<String, Object> pairs = new TreeMap<>();

        public StyleRuleBuilder(String name, StyleRuleTarget targ, Function<StyleRuleBuilder<T>, T> conv) {
            this.targ = targ;
            this.conv = conv;
            this.name = name;
        }

        @Override
        public boolean isEmpty() {
            return qualifiers.isEmpty() && pairs.isEmpty();
        }

        public T close() {
            return conv.apply(this);
        }

        public StyleRuleBuilder<T> qualifiedBy(String name, String val) {
            qualifiers.put(name, val);
            return this;
        }

        public StyleRuleBuilder<T> add(String name, String val) {
            pairs.put(name, val);
            return this;
        }

        public StyleRuleBuilder<T> ems(String name, Number val) {
            pairs.put(name, new Suffixed(val, "em"));
            return this;
        }

        public StyleRuleBuilder<T> rems(String name, Number val) {
            pairs.put(name, new Suffixed(val, "rem"));
            return this;
        }

        public StyleRuleBuilder<T> px(String name, Number val) {
            pairs.put(name, new Suffixed(val, "px"));
            return this;
        }

        public StyleRuleBuilder<T> percentage(String name, Number val) {
            pairs.put(name, new Suffixed(val, "%"));
            return this;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (isEmpty()) {
                return;
            }
            lines.doubleNewline();
            lines.word(targ.prefix()).appendRaw(name);
            if (!qualifiers.isEmpty()) {
                ArrayList<Map.Entry<String, String>> lm = new ArrayList<>(qualifiers.entrySet());
                lines.appendRaw('[');
                for (int i = 0; i < lm.size(); i++) {
                    lines.appendRaw(lm.get(i).getKey());
                    lines.appendRaw('=');
                    lines.appendRaw(lm.get(i).getValue());
                }
                lines.appendRaw(']').space();
            }
            lines.block(true, lb -> {
                pairs.forEach((k, v) -> {
                    lb.onNewLine();
                    lb.word(k).appendRaw(": ");
                    lb.word(v.toString());
                    lb.appendRaw(';');
                });
            });
        }

    }

    public static class Tag<T> implements CodeGenerator {

        private final String tagName;
        private final List<CodeGenerator> contents = new ArrayList<>();
        private final Map<String, Object> attributes = new TreeMap<>();
        private CloseStyle close;

        private final Function<Tag<T>, T> conv;

        Tag(String tagName, Function<Tag<T>, T> conv) {
            this(tagName, CloseStyle.STANDARD, conv);
        }

        Tag(String tagName, CloseStyle close, Function<Tag<T>, T> conv) {
            this.tagName = tagName;
            this.close = close;
            this.conv = conv;
        }
        
        boolean isInlineable() {
            switch(tagName) {
                case "title":
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                    return true;
                default :
                    return false;
            }
        }

        Tag<T> add(CodeGenerator gen) {
            contents.add(gen);
            return this;
        }
        
        <F> Text<F> configure(Text<F> text) {
            if (isInlineable()) {
                text.inline();
            }
            return text;
        }

        @Override
        public String toString() {
            LinesBuilder lb = new LinesBuilder();
            generateInto(lb);
            return lb.toString();
        }

        public Text<Tag<T>> append() {
            return configure(new Text<>(txt -> {
                contents.add(txt);
                return this;
            }));
        }

        public Tag<T> append(String what) {
            return configure(new Text<Tag<T>>(what, txt -> {
                contents.add(txt);
                return this;
            })).close();
        }

        public Tag<T> append(Consumer<? super Text<Void>> c) {
            Holder<Tag<T>> hold = new Holder<>();
            Text<Void> result = new Text<>(txt -> {
                contents.add(txt);
                hold.set(this);
                return null;
            });
            c.accept(configure(result));
            hold.ifUnset(result::close);
            return hold.get("Text incomplete");
        }

        public Tag<T> append(String text, Consumer<? super Text<Void>> c) {
            Holder<Tag<T>> hold = new Holder<>();
            Text<Void> result = new Text<>(text, txt -> {
                contents.add(txt);
                hold.set(this);
                return null;
            });
            hold.ifUnset(result::close);
            return hold.get("Text incomplete");
        }

        public Tag<Tag<T>> tag(String name) {
            return new Tag<>(name, tag -> {
                contents.add(tag);
                return this;
            });
        }

        public Tag<T> tag(String name, Consumer<? super Tag<Void>> c) {
            Holder<Tag<T>> hold = new Holder<>();
            Tag<Void> result = new Tag<>(name, tg -> {
                this.add(tg);
                hold.set(this);
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::close);
            return hold.get(name + " tag not completed");
        }

        public T close() {
            return conv.apply(this);
        }

        public Tag<T> selfClosing() {
            this.close = CloseStyle.SELF_CLOSING;
            return this;
        }

        public Tag<T> noClose() {
            this.close = CloseStyle.NO_CLOSE;
            return this;
        }

        public Tag<T> setAttribute(String s, Object o) {
            attributes.put(s, o);
            return this;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.appendRaw('<').appendRaw(tagName);
            for (Map.Entry<String, Object> e : attributes.entrySet()) {
                lines.word(e.getKey())
                        .appendRaw('=');
                if (e.getValue() instanceof String) {
                    lines.appendRaw('"').appendRaw(e.getValue().toString()).appendRaw('"');
                } else {
                    lines.appendRaw(e.getValue().toString());
                }
            }

            if (contents.isEmpty() && attributes.isEmpty() && close.canSelfClose()) {
                lines.appendRaw("/>");
                return;
            } else {
                lines.appendRaw(">");
            }
            lines.indent(ll -> {

                for (CodeGenerator cg : contents) {
                    lines.onNewLine();
                    cg.generateInto(lines);
                }
            });
            lines.onNewLine();
            close.close(tagName, lines, true);
        }
    }

    public static class Text<T> implements CodeGenerator {

        private final StringBuilder contents = new StringBuilder();
        private final Function<Text<T>, T> conv;
        private boolean inline;

        public Text(Function<Text<T>, T> conv) {
            this.conv = conv;
        }

        Text(String txt, Function<Text<T>, T> conv) {
            this.contents.append(txt);
            this.conv = conv;
        }

        public Text<T> inline() {
            inline = true;
            return this;
        }

        public Text<T> append(String txt) {
            contents.append(txt);
            return this;
        }

        public T close() {
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            String[] parts = contents.toString().split("\n");
            for (String p : parts) {
                if (!inline) {
                    lines.onNewLine();
                }
                lines.appendRaw(p);
            }
        }
    }
}
