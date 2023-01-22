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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    Void add(RegexElement el) {
        currElement.add(el);
        return null;
    }

    <T> T nest(ContainerRegexElement what, Function<Consumer<RegexElement>, T> f) {
        ContainerRegexElement old = currElement;
        try {
            currElement = what;
            T result = f.apply(what::add);
            // Use currElement here - we may have replaced it with a bounded instance

            return result;
        } finally {
            if (old.kind() == ElementKinds.ALTERNATION) {
                if (!currElement.isEmpty()) {
                    old.add(currElement);
                } else {
                    // We have a structure like (\W|^) which results in an empty
                    // added element; convert it to \W? which amounts to the same
                    // thing for our purposes
                    old = new Bounds(old, 0, 1);
                }
            } else {

                old.add(currElement);
            }
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
//                    add(EMPTY);
                    return null;
                }
                return visitExpr(ctx.expr());
            });
            ctx.alternative().forEach(al -> {
                if ("^".equals(al.getText()) || "$".equals(al.getText())) {
                    // Substitute nothing - ^ and $ are not interesting for us
//                    add(EMPTY);
                    return;
                }
                GeneralBag nextExpression = new GeneralBag(REGEX, ALL);
                nest(nextExpression, c -> {
                    visitAlternative(al);
                    return null;
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

    private boolean negated;

    <T> T negated(Supplier<T> supp) {
        boolean old = negated;
        boolean val = !negated;
        negated = val;
        try {
            return supp.get();
        } finally {
            negated = old;
        }
    }

    @Override
    public Void visitCharacter_class(Character_classContext ctx) {
        Supplier<Void> supp = () -> {
            return nest(new CharacterClass(ctx.Caret() != null), c -> {
                return super.visitCharacter_class(ctx);
            });
        };
        if (ctx.Caret() != null) {
//            System.out.println("NEGATED CHAR CLASS");
//            return negated(supp);
            return supp.get();
        } else {
            return supp.get();
        }
    }

    static boolean isDigits(Token tok) {
        if (tok == null) {
            return false;
        }
        String txt = tok.getText().trim();
        return isDigits(txt);
    }

    private static boolean isDigits(String txt) {
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
        if (negated) {
            kinds = kinds.opposite();
        }
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

    private static final Pattern HEX_PATTERN = Pattern.compile("[0-9A-Fa-f]+");

    private static final Pattern ESC_HEX_PATTERN = Pattern.compile("\\[ux][0-9a-fA-F]{1,4}");
//    private static final Pattern HEX_PATTERN = Pattern.compile("[0-9A-Fa-f]+");

    private Optional<Character> interpretEscape(String what) {
        if (what.charAt(0) == '\\') {
            switch (what.charAt(1)) {
                case '0':
                    // octal - because EVERYONE uses octal...
                    if (what.length() > 2 && isDigits(what.substring(2))) {
                        char c = (char) Integer.parseInt(what.substring(2), 8);
                        return Optional.of(c);
                    }
                    break;
                case 'x':
                    // hex or hex range
                    Matcher m1 = HEX_PATTERN.matcher(what.substring(2));
                    if (m1.find()) {
                        char c = (char) Integer.parseInt(what.substring(2), 16);
                        return Optional.of(c);
                    }
                    break;
                case 'u': // hex via unicode escape
                    Matcher m2 = HEX_PATTERN.matcher(what.substring(2));
                    if (m2.find()) {
                        char c = (char) Integer.parseInt(what.substring(2), 16);
                        return Optional.of(c);
                    }
                    break;
                case 'c':
                    // The java spec says:
                    // \cx - The control character corresponding to x
                    // But what is x?
                    throw new IllegalArgumentException("Control char escapes not supported");
//                    break;
                case '\\':
                    return Optional.of('\\');
                case 'f':
                    return Optional.of('\f');
                case 'n':
                    return Optional.of('\n');
                case 'r':
                    return Optional.of('\r');
                case 't':
                    return Optional.of('\t');
                case 'a':
                    return Optional.of('\u0007');
                case 'e':
                    return Optional.of('\u001B');
                default:
                    if (what.length() == 2) {
                        return Optional.of(what.charAt(1));
                    }
            }
        }
        return Optional.empty();
    }

    @Override
    public Void visitShared_literal(XegerParser.Shared_literalContext ctx) {
        String txt = ctx.getText();
        if (".".equals(txt)) {
            add(new AnyChar());
            return null;
        }

        Optional<Character> esc = interpretEscape(ctx.getText());
        if (esc.isPresent()) {
            add(new OneChar(esc.get(), negated));
            return null;
        }

        if (txt.length() == 1) {
            add(new OneChar(txt.charAt(0), negated));
        } else if ("\\\\".equals(txt)) {
            add(new OneChar('\\', negated));
        } else {
            add(new OneString(txt, negated));
        }
        return null;
    }

    @Override
    public Void visitCc_atom(Cc_atomContext ctx) {
        if (ctx.Hyphen() != null) {
            return nest(new CharRange(negated), c -> super.visitCc_atom(ctx));
        } else {
            String txt = ctx.getText();
            if (txt.length() == 2 && txt.charAt(0) == '\\') {
                switch (txt) {
                    case "\\d":
                        return add(new CharRange('0', '9'));
                    case "\\w":
                        add(new CharRange('A', 'Z'));
                        return add(new CharRange('a', 'z'));
                    case "\\D":
                        add(new CharRange(32, '0' - 1));
                        return add(new CharRange('9' + 1, 127));
                    case "\\W":
                        add(new CharRange(32, 'A'));
                        add(new CharRange('Z' + 1, 'a' - 1));
                        return add(new CharRange('z' + 1, 127));
                    case "\\s":
                        add(new OneChar(' '));
                        add(new OneChar('\t'));
                        add(new OneChar('\r'));
                        return add(new OneChar('\n'));
                    case "\\S":
                        for (int i = 0; i < 127; i++) {
                            char c = (char) i;
                            if (!Character.isWhitespace(c)) {
                                add(new OneChar(c));
                            }
                        }
                        return null;
                }
            }
            Optional<Character> esc = interpretEscape(ctx.getText());
            if (esc.isPresent()) {
                return add(new OneChar(esc.get(), negated));
            }
        }
        return super.visitCc_atom(ctx);
    }

    @Override
    public Void visitCc_literal(XegerParser.Cc_literalContext ctx) {
        String txt = ctx.getText();
        switch (txt) {
            case "\\d":
                add(new CharRange('0', '9', false));
                return null;
            case "\\w":
                add(new CharRange('a', 'z', false));
                add(new CharRange('A', 'Z', false));
                return null;
        }
        if ("\\.".equals(ctx.toString())) {
            add(new OneChar('\\', negated));
            add(new OneChar('.', negated));
            return null;
        }
        if (txt.length() == 2 && txt.charAt(0) == '\\') {
            switch (txt.charAt(1)) {
                case ']':
                    add(new OneChar(']', negated));
                    return null;
                case '[':
                    add(new OneChar('^', negated));
                    return null;
                case '-':
                    add(new OneChar('-', negated));
                    break;
                case '\\':
                    add(new OneChar('\\', negated));
                    return null;
                default:
//                    System.out.println("ADD '" + txt + "' as " + txt.charAt(1));
                    add(new OneChar(txt.charAt(1), negated));
//                    throw new IllegalStateException("Cannot interpret escaped '" + txt + "'");
            }
        } else {
//            System.out.println("HAVE '" + txt + "'");
            Optional<Character> esc = interpretEscape(txt);
            if (esc.isPresent()) {
//                System.out.println("INTERPRET '" + txt + "' as " + esc.get());
                add(new OneChar(esc.get(), negated));
            } else {
                if (txt.length() > 1) {
                    throw new IllegalStateException("Failed to interpret literal '" + txt + "'");
                }
                add(new OneChar(txt.charAt(0), negated));
            }
        }
        return null;
    }

    @Override
    public Void visitLiteral(LiteralContext ctx) {
        String txt = ctx.getText();
        if (".".equals(txt)) {
            add(new AnyChar());
            return null;
        } else {
            Optional<Character> esc = interpretEscape(txt);
            if (esc.isPresent()) {
                add(new OneChar(esc.get(), negated));
                return null;
            }
        }
        if (txt.length() == 1) {
            add(new OneChar(txt.charAt(0), negated));
        } else {
            if (txt.length() == 2 && txt.charAt(0) == '\\') {
                add(new OneChar(txt.charAt(1), negated));
            } else {
                add(new OneString(txt, negated));
            }
        }
        //            return super.visitLiteral(ctx);
        return null;
    }

    @Override
    public Void visitDigit(DigitContext ctx) {
        add(new OneChar(ctx.getText().charAt(0), negated));
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
        add(new OneChar(ctx.getText().charAt(0), negated));
        return null;
    }

}
