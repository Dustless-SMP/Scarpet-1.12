package carpet.script.exception;

import carpet.CarpetSettings;

public class TempException extends RuntimeException{//todo replace this or upgrade this once lang fleshes out
    public TempException(String message){
        super(message);
        CarpetSettings.LOG.error("[Scarpet Error] "+message);
    }
}
