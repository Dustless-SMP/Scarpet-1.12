package carpet.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Expression {

    public static final Logger LOGGER = LogManager.getLogger("[SCARPET] ");

    public static Map<String, String> operators = new HashMap<>();

    public String expression;

    public Expression(String expression){
        this.expression = expression;
    }

    Expression(){
        this("");
    }

    public static boolean isAnOperator(String s){
        return operators.containsKey(s);
    }
}
