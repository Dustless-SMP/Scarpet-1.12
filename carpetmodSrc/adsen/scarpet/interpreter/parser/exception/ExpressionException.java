package adsen.scarpet.interpreter.parser.exception;

import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.Fluff;
import adsen.scarpet.interpreter.parser.Tokenizer;

import java.util.ArrayList;
import java.util.List;

import static adsen.scarpet.interpreter.parser.ExpressionInspector.Expression_getExpressionSnippet;
import static adsen.scarpet.interpreter.parser.ExpressionInspector.Expression_getName;

/* The expression evaluators exception class. */
public class ExpressionException extends RuntimeException {
    public static Fluff.TriFunction<Expression, Tokenizer.Token, String, List<String>> errorSnooper = null;
    private static Fluff.TriFunction<Expression, Tokenizer.Token, String, List<String>> errorMaker = (expr, token, errmessage) ->
    {
        expr.print(token.toString());
        List<String> snippet = Expression_getExpressionSnippet(token,
                expr);
        List<String> errMsg = new ArrayList<>(snippet);
        if (snippet.size() != 1) {
            errmessage += " at line " + (token.lineNo + 1) + ", pos " + (token.linePos + 1);
        } else {
            errmessage += " at pos " + (token.pos + 1);
        }
        if (Expression_getName(expr) != null) {
            errmessage += " (" + Expression_getName(expr) + ")";
        }
        errMsg.add(errmessage);
        return errMsg;
    };
    public ExpressionException(String message) {
        super(message);
    }

    public ExpressionException(Expression e, Tokenizer.Token t, String message) {
        super(makeMessage(e, t, message));
    }

    static String makeMessage(Expression e, Tokenizer.Token t, String message) throws ExpressionException {
        if (errorSnooper != null) {
            List<String> alternative = errorSnooper.apply(e, t, message);
            if (alternative != null) {
                return String.join("\n", alternative);
            }
        }
        return String.join("\n", errorMaker.apply(e, t, message));
    }
}
