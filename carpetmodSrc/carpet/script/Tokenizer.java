package carpet.script;

import carpet.script.exception.TempException;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class Tokenizer implements Iterator<Tokenizer.Token> {

    /** What character to use for decimal separators. */
    private static final char decimalSeparator = '.';
    /** What character to use for minus sign (negative values). */
    private static final char minusSign = '-';
    private String input;
    private Expression expression;
    private int pos = 0;
    private int linepos = 0;
    private int lineno = 0;
    private boolean comments = false; // only works in file, always false for now todo change
    private boolean newLinesMarkers = true; //cos only got commands for the time being todo change
    private Token previousToken;

    public Tokenizer(String input) {
        this.input = input;
        this.expression = new Expression();
    }

    private char peekNextChar(){
        return (pos < (input.length() - 1)) ? input.charAt(pos + 1) : 0;
    }

    private boolean isHexDigit(char ch)
    {
        return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    @Override
    public boolean hasNext() {
        return input.length()>pos;
    }

    @Override
    public Token next() {

        if (pos >= input.length()){
            return previousToken = null;
        }

        Token tok = new Token();

        char ch = input.charAt(pos);

        while(Character.isWhitespace(ch) && pos < input.length()){
            linepos++;
            if(ch=='\n'){
                lineno++;
                linepos = 0;
            }
            ch = input.charAt(++pos);
        }

        tok.pos = pos;
        tok.lineno = lineno;
        tok.linepos = linepos;

        boolean isHex = false;

        if(Character.isDigit(ch)) {
            if (ch == '0' && (peekNextChar() == 'x' || peekNextChar() == 'X'))
                isHex = true;
            while ((isHex
                    && isHexDigit(
                    ch))
                    || (Character.isDigit(ch) || ch == decimalSeparator || ch == 'e' || ch == 'E'
                    || (ch == minusSign && tok.length() > 0
                    && ('e' == tok.surface.charAt(tok.length() - 1)
                    || 'E' == tok.surface.charAt(tok.length() - 1)))
                    || (ch == '+' && tok.length() > 0
                    && ('e' == tok.surface.charAt(tok.length() - 1)
                    || 'E' == tok.surface.charAt(tok.length() - 1))))
                    && (pos < input.length())) {
                tok.append(input.charAt(pos++));
                linepos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            tok.type = Token.TokenType.NUM;
        } else if(ch =='\''){
            pos++;
            linepos++;
            tok.type = Token.TokenType.STRING;
            if(pos == input.length())
                throw new TempException("Program truncated"); //todo replace with other exception
            ch = input.charAt(pos);
            while(ch !='\''){
                if(ch=='\\'){
                    switch (peekNextChar()){
                        case 'n':
                            tok.append('\n');
                            break;

                        case 't':
                            throw new TempException("Tab character not supported");
                            //tok.append('\t')

                        case 'r':
                            throw new TempException("Carriage return character not supported");
                            //tok.append('\r')
                        case '\\':
                        case '\'':
                            tok.append(peekNextChar());
                        default:
                            pos--;
                            linepos--;
                    }
                    pos+=2;
                    linepos+=2;
                } else{
                    tok.append(input.charAt(pos++));
                    linepos++;
                    if (ch=='\n')
                    {
                        lineno++;
                        linepos = 0;
                    }
                }
                if(pos == input.length())
                    throw new TempException("Program truncated");
                ch = input.charAt(pos);
            }
            pos++;
            linepos++;
        } else if(Character.isLetter(ch) || "_".indexOf(ch) >=0){
            while ((Character.isLetter(ch) || Character.isDigit(ch) || "_".indexOf(ch) >= 0) && (pos < input.length())) {
                tok.append(input.charAt(pos++));
                linepos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            // Remove optional white spaces after function or variable name
            if (Character.isWhitespace(ch)){
                while (Character.isWhitespace(ch) && pos < input.length()){
                    ch = input.charAt(pos++);
                    linepos++;
                    if (ch=='\n'){
                        lineno++;
                        linepos = 0;
                    }
                }
                pos--;
                linepos--;
            }
            tok.type = ch == '(' ? Token.TokenType.FUNC : Token.TokenType.VAR;
        } else if(ch =='(' || ch ==')' || ch==',' || ch=='[' || ch==']' || ch == '{' || ch == '}'){
            switch (ch){
                case '(':
                    tok.type = Token.TokenType.LPAR;
                    break;
                case ')':
                    tok.type = Token.TokenType.RPAR;
                    break;
                case ',':
                    tok.type = Token.TokenType.COMMA;
                    break;
                default :
                    tok.type = Token.TokenType.MARKER;
            }
            tok.append(ch);
            pos++;
            linepos++;
        } else {
            String greedyMatch = "";
            int initialPos = pos;
            int initialLinePos = linepos;
            ch = input.charAt(pos);
            int validOperatorSeenUntil = -1;
            while (!Character.isLetter(ch) && !Character.isDigit(ch) && "_".indexOf(ch) < 0
                    && !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
                    && (pos < input.length()))
            {
                greedyMatch += ch;
                if (comments && "//".equals(greedyMatch))
                {

                    while ( ch != '\n' && pos < input.length())
                    {
                        ch = input.charAt(pos++);
                        linepos++;
                        greedyMatch += ch;
                    }
                    if (ch=='\n')
                    {
                        lineno++;
                        linepos = 0;
                    }
                    tok.append(greedyMatch);
                    tok.type = Token.TokenType.MARKER;
                    return tok; // skipping setting previous
                }
                pos++;
                linepos++;
                if (Expression.isAnOperator(greedyMatch))
                {
                    validOperatorSeenUntil = pos;
                }
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            if (newLinesMarkers && "$".equals(greedyMatch))
            {
                lineno++;
                linepos = 0;
                tok.type = Token.TokenType.MARKER;
                tok.append('$');
                return tok; // skipping previous token lookback
            }
            if (validOperatorSeenUntil != -1)
            {
                tok.append(input.substring(initialPos, validOperatorSeenUntil));
                pos = validOperatorSeenUntil;
                linepos = initialLinePos+validOperatorSeenUntil-initialPos;
            }
            else
            {
                tok.append(greedyMatch);
            }

            if (previousToken == null || previousToken.type == Token.TokenType.OPERATOR
                    || previousToken.type == Token.TokenType.LPAR || previousToken.type == Token.TokenType.COMMA
                    || (previousToken.type == Token.TokenType.MARKER && ( previousToken.surface.equals("{") || previousToken.surface.equals("[") ) )
            )
            {
                tok.surface += "u";
                tok.type = Token.TokenType.UNARY_OPERATOR;
            }
            else
            {
                tok.type = Token.TokenType.OPERATOR;
            }
        }

        if (expression != null && previousToken != null &&(
                    tok.type == Token.TokenType.NUM ||
                    tok.type == Token.TokenType.VAR ||
                    tok.type == Token.TokenType.STRING ||
                    ( tok.type == Token.TokenType.MARKER && ( previousToken.surface.equalsIgnoreCase("{") || previousToken.surface.equalsIgnoreCase("["))) ||
                    tok.type == Token.TokenType.FUNC
                ) && (
                    previousToken.type == Token.TokenType.VAR ||
                    previousToken.type == Token.TokenType.FUNC ||
                    previousToken.type == Token.TokenType.NUM ||
                    previousToken.type == Token.TokenType.RPAR ||
                    ( previousToken.type == Token.TokenType.MARKER && ( previousToken.surface.equalsIgnoreCase("}") || previousToken.surface.equalsIgnoreCase("]"))) ||
                    previousToken.type == Token.TokenType.STRING
                )
            ) {
            throw new TempException("'"+tok.surface +"' is not allowed after '"+previousToken.surface+"'");
        }

        return previousToken = tok;
    }

    public static class Token {

        enum TokenType {
            LPAR, RPAR, FUNC, OPERATOR, UNARY_OPERATOR, COMMA,
            NUM, STRING, VAR, MARKER,
        }

        public String surface = "";
        public TokenType type;
        public int pos;
        public int linepos;
        public int lineno;

        public int length()
        {
            return surface.length();
        }

        public void append(char c){
            surface += c;
        }

        public void append(String s){
            surface += s;
        }

        public String toString() {
            return this.type.name() + ": " + this.surface;
        }
    }
}