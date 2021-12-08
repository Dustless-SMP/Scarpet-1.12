package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Expression tokenizer that allows to iterate over a {@link String}
 * expression token by token. Blank characters will be skipped.
 */
public class Tokenizer implements Iterator<Tokenizer.Token> {

    /**
     * What character to use for decimal separators.
     */
    private static final char decimalSeparator = '.';
    /**
     * What character to use for minus sign (negative values).
     */
    private static final char minusSign = '-';
    private final boolean comments;
    private final boolean newLinesMarkers;
    /**
     * The original input expression.
     */
    private final String input;
    private final Expression expression;
    /**
     * Actual position in expression string.
     */
    private int pos = 0;
    private int lineNo = 0;
    private int linePos = 0;
    /**
     * The previous token or <code>null</code> if none.
     */
    private Token previousToken;

    Tokenizer(Expression expr, String input, boolean allowComments, boolean allowNewLineMakers) {
        this.input = input;
        this.expression = expr;
        this.comments = allowComments;
        this.newLinesMarkers = allowNewLineMakers;
    }

    public static List<Token> simplePass(String input) {
        Tokenizer tok = new Tokenizer(null, input, false, false);
        List<Token> res = new ArrayList<>();
        while (tok.hasNext()) res.add(tok.next());
        return res;
    }

    @Override
    public boolean hasNext() {
        return (pos < input.length());
    }

    /**
     * Peek at the next character, without advancing the iterator.
     *
     * @return The next character or character 0, if at end of string.
     */
    private char peekNextChar() {
        return (pos < (input.length() - 1)) ? input.charAt(pos + 1) : 0;
    }

    private boolean isHexDigit(char ch) {
        return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    @Override
    public Token next() {
        Token token = new Token();

        if (pos >= input.length()) {
            return previousToken = null;
        }
        char ch = input.charAt(pos);
        while (Character.isWhitespace(ch) && pos < input.length()) {
            linePos++;
            if (ch == '\n') {
                lineNo++;
                linePos = 0;
            }
            ch = input.charAt(++pos);
        }
        token.pos = pos;
        token.lineNo = lineNo;
        token.linePos = linePos;

        boolean isHex = false;

        if (Character.isDigit(ch) || (ch == decimalSeparator && Character.isDigit(peekNextChar()))) {
            if (ch == '0' && (peekNextChar() == 'x' || peekNextChar() == 'X'))
                isHex = true;
            while ((isHex
                    && isHexDigit(
                    ch))
                    || (Character.isDigit(ch) || ch == decimalSeparator || ch == 'e' || ch == 'E'
                    || (ch == minusSign && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1)))
                    || (ch == '+' && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1))))
                    && (pos < input.length())) {
                token.append(input.charAt(pos++));
                linePos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            token.type = isHex ? Token.TokenType.HEX_LITERAL : Token.TokenType.LITERAL;
        } else if (ch == '\'') {
            pos++;
            linePos++;
            if (pos == input.length()) {
                token.type = Token.TokenType.STRING;
                if (expression != null)
                    throw new ExpressionException(this.expression, token, "Program truncated");
            }
            ch = input.charAt(pos);
            while (ch != '\'') {
                if (ch == '\\') {
                    pos++;
                    linePos++;
                    if (pos == input.length()) {
                        token.type = Token.TokenType.STRING;
                        if (expression != null)
                            throw new ExpressionException(this.expression, token, "Program truncated");
                    }
                }
                token.append(input.charAt(pos++));
                linePos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            pos++;
            linePos++;
            token.type = Token.TokenType.STRING;
        } else if (Character.isLetter(ch) || "_".indexOf(ch) >= 0) {
            while ((Character.isLetter(ch) || Character.isDigit(ch) || "_".indexOf(ch) >= 0
                    || token.length() == 0 && "_".indexOf(ch) >= 0) && (pos < input.length())) {
                token.append(input.charAt(pos++));
                linePos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            // Remove optional white spaces after function or variable name
            if (Character.isWhitespace(ch)) {
                while (Character.isWhitespace(ch) && pos < input.length()) {
                    ch = input.charAt(pos++);
                    linePos++;
                    if (ch == '\n') {
                        lineNo++;
                        linePos = 0;
                    }
                }
                pos--;
                linePos--;
            }
            token.type = ch == '(' ? Token.TokenType.FUNCTION : Token.TokenType.VARIABLE;
        } else if (ch == '(' || ch == ')' || ch == ',') {
            if (ch == '(') {
                token.type = Token.TokenType.OPEN_PAREN;
            } else if (ch == ')') {
                token.type = Token.TokenType.CLOSE_PAREN;
            } else {
                token.type = Token.TokenType.COMMA;
            }
            token.append(ch);
            pos++;
            linePos++;
            if (expression != null && previousToken != null && previousToken.type == Token.TokenType.OPERATOR && (ch == ')' || ch == ',')) {
                if (previousToken.surface.equalsIgnoreCase(";"))
                    throw new ExpressionException(this.expression, previousToken,
                            "Cannot have semicolon at the end of the expression");
                throw new ExpressionException(this.expression, previousToken,
                        "Can't have operator " + previousToken.surface + " at the end of a subexpression");
            }
        } else {
            StringBuilder greedyMatch = new StringBuilder();
            int initialPos = pos;
            int initialLinePos = linePos;
            ch = input.charAt(pos);
            int validOperatorSeenUntil = -1;
            while (!Character.isLetter(ch) && !Character.isDigit(ch) && "_".indexOf(ch) < 0
                    && !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
                    && (pos < input.length())) {
                greedyMatch.append(ch);
                if (comments && "//".equals(greedyMatch.toString())) {

                    while (ch != '\n' && pos < input.length()) {
                        ch = input.charAt(pos++);
                        linePos++;
                        greedyMatch.append(ch);

                    }
                    if (ch == '\n') {
                        lineNo++;
                        linePos = 0;
                    }
                    token.append(greedyMatch.toString());
                    token.type = Token.TokenType.MARKER;
                    return token; // skipping setting previous
                }
                pos++;
                linePos++;
                if (Expression.none.isAnOperator(greedyMatch.toString())) {
                    validOperatorSeenUntil = pos;
                }
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            if (newLinesMarkers && "$".equals(greedyMatch.toString())) {
                lineNo++;
                linePos = 0;
                token.type = Token.TokenType.MARKER;
                token.append('$');
                return token; // skipping previous token look back
            }
            if (validOperatorSeenUntil != -1) {
                token.append(input.substring(initialPos, validOperatorSeenUntil));
                pos = validOperatorSeenUntil;
                linePos = initialLinePos + validOperatorSeenUntil - initialPos;
            } else {
                token.append(greedyMatch.toString());
            }

            if (previousToken == null || previousToken.type == Token.TokenType.OPERATOR
                    || previousToken.type == Token.TokenType.OPEN_PAREN || previousToken.type == Token.TokenType.COMMA) {
                token.surface += "u";
                token.type = Token.TokenType.UNARY_OPERATOR;
            } else {
                token.type = Token.TokenType.OPERATOR;
            }
        }
        if (expression != null && previousToken != null &&
                (
                        token.type == Token.TokenType.LITERAL ||
                                token.type == Token.TokenType.HEX_LITERAL ||
                                token.type == Token.TokenType.VARIABLE ||
                                token.type == Token.TokenType.STRING ||
                                token.type == Token.TokenType.FUNCTION
                ) && (
                previousToken.type == Token.TokenType.VARIABLE ||
                        previousToken.type == Token.TokenType.FUNCTION ||
                        previousToken.type == Token.TokenType.LITERAL ||
                        previousToken.type == Token.TokenType.CLOSE_PAREN ||
                        previousToken.type == Token.TokenType.HEX_LITERAL ||
                        previousToken.type == Token.TokenType.STRING
        )
        ) {
            throw new ExpressionException(this.expression, previousToken, "'" + token.surface + "' is not allowed after '" + previousToken.surface + "'");
        }
        return previousToken = token;
    }

    @Override
    public void remove() {
        throw new InternalExpressionException("remove() not supported");
    }

    public static class Token {
        public String surface = "";
        public TokenType type;
        public int pos;
        public int linePos;
        public int lineNo;

        public void append(char c) {
            surface += c;
        }

        public void append(String s) {
            surface += s;
        }

        public char charAt(int pos) {
            return surface.charAt(pos);
        }

        public int length() {
            return surface.length();
        }

        @Override
        public String toString() {
            return surface;
        }

        enum TokenType {
            VARIABLE, FUNCTION, LITERAL, OPERATOR, UNARY_OPERATOR,
            OPEN_PAREN, COMMA, CLOSE_PAREN, HEX_LITERAL, STRING, MARKER
        }
    }
}
