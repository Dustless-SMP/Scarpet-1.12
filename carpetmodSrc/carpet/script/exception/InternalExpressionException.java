package carpet.script.exception;

import carpet.CarpetSettings;

public class InternalExpressionException extends RuntimeException{//todo replace this or upgrade this once lang fleshes out
    public InternalExpressionException(String message){
        super(message);
        CarpetSettings.LOG.error("[Scarpet Error] "+message);
    }
}
