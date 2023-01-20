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
package com.telenav.smithy.rex;

import static com.telenav.smithy.rex.AnyChar.ANY_CHAR;
import static com.telenav.smithy.rex.ElementKinds.ALTERNATION;
import static com.telenav.smithy.rex.ElementKinds.CHAR_CLASS;
import static com.telenav.smithy.rex.ElementKinds.REGEX;
import static com.telenav.smithy.rex.EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ALL;
import static com.telenav.smithy.rex.EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ONE;
import static com.telenav.smithy.rex.RegexElement.EMPTY;
import static com.telenav.smithy.rex.ShorthandCharacterClassKinds.of;
import static com.telenav.smithy.rex.XegerLexer.Backslash;
import static com.telenav.smithy.rex.XegerLexer.Plus;
import static com.telenav.smithy.rex.XegerLexer.QuestionMark;
import com.telenav.smithy.rex.XegerParser.AlternationContext;
import com.telenav.smithy.rex.XegerParser.AtomContext;
import com.telenav.smithy.rex.XegerParser.CaptureContext;
import com.telenav.smithy.rex.XegerParser.Capture_nameContext;
import com.telenav.smithy.rex.XegerParser.Cc_atomContext;
import com.telenav.smithy.rex.XegerParser.Character_classContext;
import com.telenav.smithy.rex.XegerParser.DigitContext;
import com.telenav.smithy.rex.XegerParser.ElementContext;
import com.telenav.smithy.rex.XegerParser.ExprContext;
import com.telenav.smithy.rex.XegerParser.LetterContext;
import com.telenav.smithy.rex.XegerParser.LiteralContext;
import com.telenav.smithy.rex.XegerParser.NumberContext;
import com.telenav.smithy.rex.XegerParser.QuantifierContext;
import com.telenav.smithy.rex.XegerParser.Quantifier_typeContext;
import com.telenav.smithy.rex.XegerParser.Shared_atomContext;
import static com.telenav.smithy.rex.XegerParser.Star;
import static java.lang.Character.isDigit;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.fill;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;

/**
 * Antlr visitor which walks the parse tree of a regex and assembles as set of
 * RegexElements for character groups, etc.
 *
 * @author Tim Boudreau
 */
final class RegexDissectingVisitor extends XegerParserBaseVisitor<Void> {

    private final List<CaptureGroup> groups;
    private final Consumer<String> parseTreeLogger;
    private int depth;
    final GeneralBag root = new GeneralBag(REGEX, ALL);
    private ContainerRegexElement currElement = root;

    RegexDissectingVisitor(List<CaptureGroup> groups, Consumer<String> parseTreeLogger) {
        this.groups = groups;
        this.parseTreeLogger = parseTreeLogger;
    }

    @Override
    public Void visitChildren(RuleNode node) {
        depth++;
        try {
            if (node instanceof ParserRuleContext ctx) {
                emit(ctx);
            }
            return super.visitChildren(node);
        } finally {
            depth--;
        }
    }

    void emit(ParserRuleContext ctx) {
        if (parseTreeLogger != null) {
            char[] c = new char[depth * 2];
            fill(c, ' ');
            parseTreeLogger.accept(new String(c) + ctx.getClass().getSimpleName() + ":" + ctx.getText());
        }
    }

    <R extends ParserRuleContext> Optional<? extends ContainerRegexElement> nest(R ctx) {
        if (ctx instanceof AlternationContext) {
            return Optional.of(new GeneralBag(ALTERNATION, ONE));
        }
        return Optional.empty();
    }

    void add(RegexElement el) {
        currElement.add(el);
    }

    <T> T nest(ContainerRegexElement what, Function<Consumer<RegexElement>, T> f) {
        ContainerRegexElement old = currElement;
        try {
            currElement = what;
            T result = f.apply(what::add);
            // Use currElement here - we may have replaced it with a bounded instance
            old.add(currElement);
            return result;
        } finally {
            currElement = old;
        }
    }

    <T, R extends ParserRuleContext> T maybeNest(R ctx, Function<Consumer<RegexElement>, T> f) {
        Optional<? extends ContainerRegexElement> container = nest(ctx);
        if (container.isPresent()) {
            ContainerRegexElement old = currElement;
            try {
                currElement = container.get();
                return currElement.enter(f);
            } finally {
                currElement = old;
            }
        } else {
            return f.apply(currElement::add);
        }
    }

    @Override
    public Void visitElement(ElementContext ctx) {
        return maybeNest(ctx, c -> {
            super.visitElement(ctx);
            ctx.quantifier();
            return null;
        });
    }

    @Override
    public Void visitExpr(ExprContext ctx) {
        return super.visitExpr(ctx);
        //            return null;
    }

    @Override
    public Void visitCapture(CaptureContext ctx) {
        CaptureGroup group = new CaptureGroup();
        groups.add(group);
        return nest(group, c -> {
            return super.visitCapture(ctx);
        });
    }

    @Override
    public Void visitCapture_name(Capture_nameContext ctx) {
        return null;
    }

    @Override
    public Void visitAlternation(AlternationContext ctx) {
        GeneralBag ac = new GeneralBag(ALTERNATION, ONE);
        return nest(ac, cons -> {
            GeneralBag initialExpression = new GeneralBag(REGEX, ALL);
            nest(initialExpression, c -> {
                ExprContext exp = ctx.expr();
                // Omit or's with 
                if ("^".equals(exp.getText()) || "$".equals(exp.getText())) {
                    // Substitute nothing - ^ and $ are not interesting for us
                    add(EMPTY);
                    return null;
                }
                return visitExpr(ctx.expr());
            });
            ctx.alternative().forEach(al -> {
                if ("^".equals(al.getText()) || "$".equals(al.getText())) {
                    // Substitute nothing - ^ and $ are not interesting for us
                    add(EMPTY);
                    return;
                }
                GeneralBag nextExpression = new GeneralBag(REGEX, ALL);
                nest(nextExpression, c -> {
                    return visitAlternative(al);
                });
            });
            return null;
        });
    }

    @Override
    public Void visitAtom(AtomContext ctx) {
        if (".".equals(ctx.getText())) {
            add(ANY_CHAR);
            return null;
        }
        return super.visitAtom(ctx);
    }

    @Override
    public Void visitCharacter_class(Character_classContext ctx) {
        return nest(new GeneralBag(CHAR_CLASS, ONE), c -> {
            return super.visitCharacter_class(ctx);
        });
    }

    static boolean isDigits(Token tok) {
        if (tok == null) {
            return false;
        }
        String txt = tok.getText().trim();
        for (int i = 0; i < txt.length(); i++) {
            if (!isDigit(txt.charAt(i))) {
                return false;
            }
        }
        return txt.length() > 0;
    }

    @Override
    public Void visitShared_atom(Shared_atomContext ctx) {
        ShorthandCharacterClassKinds kinds = of(ctx);
        if (kinds != null) {
            if (ctx.stop != null && ctx.stop != ctx.start) {
                boolean isBackref = ctx.start.getType() == Backslash && isDigits(ctx.stop);
                if (isBackref) {
                    int referencing = parseInt(ctx.getStop().getText());
                    add(new Backreference(referencing));
                    return null;
                }
                return super.visitShared_atom(ctx);
            } else {
                add(new ShorthandCharacterClass(ctx.getStart(), kinds));
            }
            return null;
        } else {
            return super.visitShared_atom(ctx);
        }
    }

    @Override
    public Void visitCc_atom(Cc_atomContext ctx) {
        if (ctx.Hyphen() != null) {
            return nest(new CharRange(), c -> super.visitCc_atom(ctx));
        }
        return super.visitCc_atom(ctx);
    }

    @Override
    public Void visitLiteral(LiteralContext ctx) {
        String txt = ctx.getText();
        if (txt.length() == 1) {
            add(new OneChar(txt.charAt(0)));
        } else {
            if (txt.length() == 2 && txt.charAt(0) == '\\') {
                add(new OneChar(txt.charAt(1)));
            } else {
                add(new OneString(txt));
            }
        }
        //            return super.visitLiteral(ctx);
        return null;
    }

    @Override
    public Void visitDigit(DigitContext ctx) {
        add(new OneChar(ctx.getText().charAt(0)));
        return super.visitDigit(ctx);
    }

    private void bound(int min, int max) {
        currElement.boundLast(min, max);
    }

    @Override
    public Void visitQuantifier(QuantifierContext ctx) {
        Quantifier_typeContext type = ctx.quantifier_type();
        NumberContext start = ctx.start;
        NumberContext count = ctx.count;
        NumberContext end = ctx.end;
        if (start != null && end != null) {
            bound(parseInt(start.getText()), parseInt(end.getText()));
        } else if (start != null && end == null) {
            bound(parseInt(start.getText()), MAX_VALUE - 1);
        } else if (count != null) {
            int ct = parseInt(count.getText());
            bound(ct, ct);
        } else if (type != null) {
            if (type.plus != null) {
                bound(1, MAX_VALUE - 1);
            } else if (type.question != null) {
                bound(0, 1);
            }
        } else {
            switch (ctx.getStart().getType()) {
                case Star:
                    bound(0, MAX_VALUE);
                    break;
                case Plus:
                    bound(1, MAX_VALUE);
                    break;
                case QuestionMark:
                    bound(0, 1);
            }
        }
        return null;
    }

    @Override
    public Void visitLetter(LetterContext ctx) {
        add(new OneChar(ctx.getText().charAt(0)));
        return null;
    }

}
