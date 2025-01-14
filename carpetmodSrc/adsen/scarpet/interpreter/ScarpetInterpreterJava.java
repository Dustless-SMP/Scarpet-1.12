package adsen.scarpet.interpreter;

import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.ScarpetScriptServer;
import adsen.scarpet.interpreter.parser.exception.ExitStatement;
import adsen.scarpet.interpreter.parser.exception.ExpressionException;

import java.util.Scanner;

public class ScarpetInterpreterJava {

    public static ScarpetScriptServer scriptServer;

    public static void main(String[] args) {
        System.out.println("Started Scarpet Interpreter");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();

        while (true) {
            try {
                new Expression(input).displayOutput();
            } catch (ExitStatement exit) {
                System.out.println("Exited");
                break;
            } catch (ExpressionException e) {
                System.out.println(e.getMessage());
            } catch (Throwable t) {
                t.printStackTrace();
            }
            input = scanner.nextLine();
        }

        System.out.println("Finished interpreting");
    }
}
