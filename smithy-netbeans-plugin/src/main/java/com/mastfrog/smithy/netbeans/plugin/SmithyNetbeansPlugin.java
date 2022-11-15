package com.mastfrog.smithy.netbeans.plugin;

import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.Boilerplate;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.DelimiterPair;
import com.mastfrog.smithy.antlr.SmithyModalParser.CompilationUnitContext;
import static com.mastfrog.smithy.antlr.SmithyModalLexer.DocComment;
import static com.mastfrog.smithy.antlr.SmithyModalLexer.LineComment;
import static com.mastfrog.smithy.antlr.SmithyModalLexer.Whitespace;
import com.mastfrog.smithy.antlr.SmithyModalLexer;
import com.mastfrog.smithy.antlr.SmithyModalParser;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.ENUM;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.LIST;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.MAP;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.OPERATION;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.RESOURCE;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.SERVICE;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.SET;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.SIMPLE;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.STRUCTURE;
import static com.mastfrog.smithy.netbeans.plugin.SmithyNetbeansPlugin.StructKinds.UNION;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.antlr.v4.runtime.Token;
import org.nemesis.antlr.fold.AntlrFoldsRegistration;
import org.nemesis.antlr.fold.FoldTypeSpec;
import org.nemesis.antlr.navigator.SimpleNavigatorRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.CodeCompletion.RuleSubstitutions;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.CodeCompletion.SupplementaryTokenCompletion;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.SyntaxInfo;
import org.nemesis.antlr.spi.language.Goto;
import org.nemesis.antlr.spi.language.highlighting.Coloration;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.ALL_POPULAR_THEMES;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.POPULAR_BRIGHT_THEMES;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.POPULAR_DARK_THEMES;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.NameAndOffsets;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author timb
 */
@AntlrLanguageRegistration(
        name = "Smithy",
        lexer = SmithyModalLexer.class,
        useImplicitLanguageNameFromLexerName = true,
        file = @AntlrLanguageRegistration.FileType(
                hooks = DOHooks.class,
                extension = "smithy",
                iconBase = "com/mastfrog/smithy/netbeans/plugin/quick-smithy-icon.png",
                multiview = true
        ),
        mimeType = "text/x-smithy",
        lineCommentPrefix = "//",
        parser = @AntlrLanguageRegistration.ParserControl(
                generateSyntaxTreeNavigatorPanel = true,
                type = SmithyModalParser.class,
                entryPointRule = SmithyModalParser.RULE_compilationUnit,
                generateExtractionDebugNavigatorPanel = true,
                defaultErrorHighlightingEnabled = false,
                parserStreamChannel = 0,
                changeSupport = false
        ),
        genericCodeCompletion = @AntlrLanguageRegistration.CodeCompletion(
                ignoreTokens = {
                    Whitespace,
                    LineComment,
                    DocComment},
                ruleSubstitutions = {
                    @RuleSubstitutions(
                            complete = SmithyModalParser.RULE_shapeIdMember,
                            withCompletionsOf = SmithyModalParser.RULE_shapeIdMember),
                    @RuleSubstitutions(
                            complete = SmithyModalParser.RULE_shapeSection,
                            withCompletionsOf = SmithyModalParser.RULE_shapeIdMember),
                    @RuleSubstitutions(
                            complete = SmithyModalParser.RULE_structureMembers,
                            withCompletionsOf = SmithyModalParser.RULE_structureMember)

                },
                tokenCompletions = {
                    @SupplementaryTokenCompletion(tokenId = SmithyModalLexer.TraitIdentifier,
                            text = "@required\n"),
//                    @SupplementaryTokenCompletion(tokenId = SmithyModalLexer.TraitIdentifier,
//                            text = "@pattern(\"^\")\n"),
                    @SupplementaryTokenCompletion(tokenId = SmithyModalLexer.BooleanValue,
                            text = "true")
                },
                //                    @SupplementaryTokenCompletion(tokenId = SmithyModalLexer.BooleanValue,
                //                            text = "false"),},
                //                tokenCompletions = {
                //                    @SupplementaryTokenCompletion(
                //                            text = "structure",
                //                            tokenId = SmithyModalLexer.Structure
                //                    ),
                //                    @SupplementaryTokenCompletion(
                //                            text = "resource",
                //                            tokenId = SmithyModalLexer.Resource
                //                    ),
                //                    @SupplementaryTokenCompletion(
                //                            text = "operation",
                //                            tokenId = SmithyModalLexer.Operation
                //                    ),
                //                    @SupplementaryTokenCompletion(
                //                            text = "service",
                //                            tokenId = SmithyModalLexer.Service
                //                    ),
                //                    @SupplementaryTokenCompletion(
                //                            text = "list",
                //                            tokenId = SmithyModalLexer.List
                //                    )
                //                },
                preferredRules = {
                    //                    SmithyModalParser.RULE_namespace,
                    SmithyModalParser.RULE_shapeStatement,
                    SmithyModalParser.RULE_shapeIdMember,
                    SmithyModalParser.RULE_shapeId,
                    SmithyModalParser.RULE_shapeStatement,
                    //                    SmithyModalParser.RULE_structureDeclaration,
                    SmithyModalParser.RULE_structureForClause,
                    SmithyModalParser.RULE_nodeObjectKvp,
                    //                    SmithyModalParser.RULE_operationDeclaration,
                    //                    SmithyModalParser.RULE_resourceDeclaration,
                    SmithyModalParser.RULE_listDeclaration,
                    SmithyModalParser.RULE_setDeclaration,
                    SmithyModalParser.RULE_setMember,
                    SmithyModalParser.RULE_listMember, //                    SmithyModalParser.RULE_serviceDeclaration
                }
        ),
        categories = {
            @TokenCategory(name = "comment",
                    tokenIds = {
                        SmithyModalLexer.LineComment,
                        SmithyModalLexer.DocComment
                    },
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "comment"
                    )
            ),
            @TokenCategory(name = "whitespace",
                    tokenIds = {
                        SmithyModalLexer.Whitespace
                    },
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "whitespace"
                    )
            ),

            @TokenCategory(name = "keywords",
                    tokenIds = {
                        SmithyModalLexer.Member,
                        SmithyModalLexer.Metadata,
                        SmithyModalLexer.Use,
                        SmithyModalLexer.Value,
                        SmithyModalLexer.Namespace,
                        SmithyModalLexer.DollarsVersion,
                        SmithyModalLexer.With,
                        SmithyModalLexer.Apply,
                        SmithyModalLexer.For,
                        SmithyModalLexer.Key
                    },
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "keyword"
                    )
            ),

            @TokenCategory(name = "shapetypes",
                    tokenIds = {
                        SmithyModalLexer.Union,
                        SmithyModalLexer.Set,
                        SmithyModalLexer.List,
                        SmithyModalLexer.Structure,
                        SmithyModalLexer.Operation,
                        SmithyModalLexer.Service,
                        SmithyModalLexer.Resource,
                        SmithyModalLexer.Map,
                        SmithyModalLexer.Enum
                    },
                    colors = {
                        @Coloration(
                                bold = true,
                                derivedFrom = "keyword",
                                themes = ALL_POPULAR_THEMES
                        )
                    }
            ),

            @TokenCategory(name = "text",
                    tokenIds = {
                        SmithyModalLexer.String,
                        SmithyModalLexer.VersionString,},
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "string"
                    )),

            @TokenCategory(name = "symbols",
                    tokenIds = {
                        SmithyModalLexer.Assign,
                        SmithyModalLexer.OpenArray,
                        SmithyModalLexer.OpenBrace,
                        SmithyModalLexer.OpenParen,
                        SmithyModalLexer.CloseArray,
                        SmithyModalLexer.CloseBrace,
                        SmithyModalLexer.CloseParen
                    },
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "operator"
                    )),

            @TokenCategory(name = "Errors",
                    tokenIds = SmithyModalLexer.Erroneous,
                    colors = {
                        @Coloration(
                                bg = {170, 0, 0},
                                fg = {255, 128, 128},
                                bold = true,
                                waveUnderline = {255, 255, 255},
                                themes = POPULAR_DARK_THEMES
                        ),
                        @Coloration(
                                bg = {100, 10, 10},
                                fg = {255, 96, 96},
                                bold = true,
                                waveUnderline = {80, 0, 0},
                                themes = POPULAR_BRIGHT_THEMES
                        )
                    }
            ),

            @TokenCategory(name = "Paths",
                    tokenIds = {
                        SmithyModalLexer.UsePath,
                        SmithyModalLexer.NamespacePath
                    },
                    colors = @Coloration(
                            fg = {128, 128, 245},
                            derivedFrom = "identifier",
                            themes = ALL_POPULAR_THEMES
                    )),
            @TokenCategory(name = "Members",
                    tokenIds = {
                        SmithyModalLexer.MemberReference,
                        SmithyModalLexer.ShapeMemberName,
                        SmithyModalLexer.EnumMemberName},
                    colors = {
                        @Coloration(
                                fg = {180, 82, 30},
                                derivedFrom = "identifier",
                                themes = POPULAR_BRIGHT_THEMES
                        ),
                        @Coloration(
                                fg = {247, 247, 211},
                                derivedFrom = "identifier",
                                themes = POPULAR_DARK_THEMES
                        )
                    }
            ),
            @TokenCategory(name = "identifier",
                    tokenIds = {
                        SmithyModalLexer.Identifier,},
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "identifier"
                    )
            ),

            @TokenCategory(name = "traits",
                    tokenIds = {
                        SmithyModalLexer.TraitIdentifier},
                    colors = {
                        @Coloration(
                                derivedFrom = "operator",
                                themes = POPULAR_BRIGHT_THEMES,
                                fg = {204, 153, 0}
                        ),
                        @Coloration(
                                derivedFrom = "operator",
                                themes = POPULAR_DARK_THEMES,
                                fg = {255, 197, 25}
                        )

                    }
            ),

            @TokenCategory(name = "types",
                    tokenIds = {
                        SmithyModalLexer.TypeName,},
                    colors = @Coloration(
                            derivedFrom = "field",
                            themes = ALL_POPULAR_THEMES
                    )
            ),
            @TokenCategory(name = "number",
                    tokenIds = {
                        SmithyModalLexer.Number,},
                    colors = @Coloration(
                            derivedFrom = "number",
                            themes = ALL_POPULAR_THEMES
                    )
            ),
            @TokenCategory(name = "literal",
                    tokenIds = {
                        SmithyModalLexer.BooleanValue,
                        SmithyModalLexer.Null},
                    colors = @Coloration(
                            derivedFrom = "character",
                            themes = ALL_POPULAR_THEMES
                    )
            ),
            @TokenCategory(name = "keys",
                    parserRuleIds = SmithyModalParser.RULE_nodeObjectKey,
                    colors = @Coloration(
                            derivedFrom = "default",
                            themes = ALL_POPULAR_THEMES,
                            bold = true
                    )),
            @TokenCategory(name = "shapeNames",
                    tokenIds = {SmithyModalLexer.CollectionIdentifier,
                        SmithyModalLexer.MapIdentifier,
                        SmithyModalLexer.ShapeIdentifier,
                        SmithyModalLexer.EnumIdentifier
                    },
                    colors = @Coloration(
                            derivedFrom = "default",
                            themes = ALL_POPULAR_THEMES,
                            bold = true
                    ))

        },
        syntax = @SyntaxInfo(
                commentTokens = {
                    SmithyModalLexer.LineComment,
                    SmithyModalLexer.DocComment
                },
                whitespaceTokens = {
                    SmithyModalLexer.Whitespace},
                bracketSkipTokens = {
                    SmithyModalLexer.String,
                    SmithyModalLexer.LineComment,
                    SmithyModalLexer.DocComment
                }),
        localizingBundle = "com.mastfrog.smithy.netbeans.plugin.smithy",
        sample = "sample.smithy"
)
@EditorFeaturesRegistration(
        mimeType = SmithyNetbeansPlugin.MIME_TYPE,
        order = 9,
        lexer = SmithyModalLexer.class,
        languageDisplayName = "Smithy",
        insertBoilerplate = {
            @Boilerplate(
                    category = "Braces",
                    onChar = '{',
                    inserting = "\n    ^\n}",
                    whenPrecedingToken = SmithyModalLexer.OpenBrace,
                    linePosition = EditorFeaturesRegistration.LinePosition.AT_END,
                    name = "Insert Closing Brace",
                    description = "Inserts a matching '}' when '{' is typed."),

            @Boilerplate(
                    category = "Arrays",
                    onChar = '[',
                    inserting = "^]",
                    whenPrecedingToken = SmithyModalLexer.OpenArray,
                    linePosition = EditorFeaturesRegistration.LinePosition.AT_END,
                    name = "Insert Closing Square Bracket",
                    description = "Inserts a matching '}' when '{' is typed."),

            @Boilerplate(
                    onChar = ' ',
                    inserting = ": ^",
                    whenPrecedingToken = SmithyModalLexer.Identifier,
                    linePosition = EditorFeaturesRegistration.LinePosition.AT_END,
                    whenCurrentTokenNot = SmithyModalLexer.Assign,
                    name = "Insert Colon",
                    description = "Inserts a colon when adding an identifier."),},
        deleteMatchingDelimiter = {
            @DelimiterPair(
                    category = "Braces",
                    openingToken = SmithyModalLexer.OpenBrace,
                    closingToken = SmithyModalLexer.CloseBrace,
                    ignoring = {SmithyModalLexer.Whitespace, SmithyModalLexer.LineComment, SmithyModalLexer.DocComment}
            ),},
        elideTypedChars = {
            @EditorFeaturesRegistration.Elision(
                    category = "Braces",
                    onKeyTyped = '{',
                    name = "Skip present braces",
                    description = "If a { is typed when the next character is a {, don't insert one, just reposition the cursor.",
                    whenNotIn = {},
                    backward = false
            )
        }
)
public class SmithyNetbeansPlugin {

    static final String MIME_TYPE = "text/x-smithy";

    @Localize(displayName = "Shapes")
    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "shapes", guardedStart = 3,
            guardedEnd = 3, displayText = "shapes"))
    public static final NamedRegionKey<StructKinds> SHAPE_BOUNDS
            = NamedRegionKey.create("shapes", StructKinds.class);

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, colors = @ColoringCategory(name = "Shapes",
            colors = {
                @Coloration(
                        themes = ALL_POPULAR_THEMES,
                        bold = true
                )
            }))
    @SimpleNavigatorRegistration(displayName = "Shapes", order = 1, mimeType = MIME_TYPE)
    @Localize(displayName = "Shape Names")
    public static final NamedRegionKey<StructKinds> SHAPE_NAME_BOUNDS
            = NamedRegionKey.create("structureNames", StructKinds.class);

    @Goto(mimeType = MIME_TYPE)
    @Localize(displayName = "Shape Members")
    public static final NameReferenceSetKey<StructKinds> SHAPE_REFS
            = SHAPE_BOUNDS.createReferenceKey("shapeMembers");

    public static final RegionsKey<KeyValue> KEY_NAMES = RegionsKey.create(KeyValue.class, "keyPairs");

    @ExtractionRegistration(mimeType = MIME_TYPE, entryPoint = CompilationUnitContext.class)
    static void extract(ExtractorBuilder<? super CompilationUnitContext> bldr) {

        bldr.extractingRegionsUnder(KEY_NAMES)
                .summingTokensFor(SmithyModalParser.VOCABULARY)
                .whenRuleType(SmithyModalParser.NodeObjectKvpContext.class)
                .extractingBoundsFromRuleAndKeyWith(kvp -> {
                    if (kvp.nodeObjectKey() != null && kvp.nodeValue() != null) {
                        return new KeyValue(kvp.nodeObjectKey().getText(), kvp.nodeValue().getText());
                    }
                    return null;
                })
                .finishRegionExtractor();

        bldr.extractNamedRegionsKeyedTo(StructKinds.class)
                .recordingRuleRegionUnder(SHAPE_BOUNDS)
                .recordingNamePositionUnder(SHAPE_NAME_BOUNDS)
                .whereRuleIs(SmithyModalParser.SimpleShapeStatementContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((SmithyModalParser.SimpleShapeStatementContext ctx, BiConsumer<StructKinds, Token> consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(SIMPLE, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.ResourceDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((SmithyModalParser.ResourceDeclarationContext ctx, BiConsumer<StructKinds, Token> consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(RESOURCE, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.ServiceDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(SERVICE, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.OperationDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(OPERATION, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.MapDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(MAP, ctx.start);
                    }
                })
                .whereRuleIs(SmithyModalParser.EnumDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(ENUM, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.SetDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(SET, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.UnionDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(UNION, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.ListDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(LIST, ctx.id);
                    }
                })
                .whereRuleIs(SmithyModalParser.StructureDeclarationContext.class)
                .whenInAncestorRule(SmithyModalParser.ShapeStatementContext.class)
                .derivingNameFromTokenWith((ctx, consumer) -> {
                    if (ctx.id != null) {
                        consumer.accept(STRUCTURE, ctx.id);
                    }
                })
                .collectingReferencesUnder(SHAPE_REFS)
                .whereReferenceContainingRuleIs(SmithyModalParser.NodeObjectKvpContext.class)
                .derivingReferenceOffsetsExplicitlyWith(kvp
                        -> ifNotNull(kvp, ()
                        -> ifNotNull(kvp.nodeObjectKey(), ()
                        -> ifNotNull(kvp.nodeObjectKey().getText(), ()
                        -> ifNotNull(kvp.nodeObjectKey().start, ()
                        -> ifNotNull(kvp.nodeObjectKey().stop, ()
                        -> NameAndOffsets
                        .create(
                                kvp.nodeObjectKey().getText(),
                                kvp.nodeObjectKey().start.getStartIndex(),
                                kvp.nodeObjectKey().stop.getStopIndex() + 1)
                )))))
                ).finishReferenceCollector()
                .finishNamedRegions();
    }

    private static <T, R> R ifNotNull(T obj, Supplier<R> r) {
        if (obj == null) {
            return null;
        }
        return r.get();
    }

    public static enum StructKinds {
        STRUCTURE,
        LIST,
        SET,
        RESOURCE,
        SERVICE,
        MAP,
        UNION,
        ENUM,
        OPERATION,
        SIMPLE,
    }

    public static final class KeyValue {

        private final String key;
        private final String value;

        public KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.key);
            hash = 97 * hash + Objects.hashCode(this.value);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final KeyValue other = (KeyValue) obj;
            if (!Objects.equals(this.key, other.key)) {
                return false;
            }
            return Objects.equals(this.value, other.value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

    }

}
