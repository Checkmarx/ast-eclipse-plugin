package com.checkmarx.eclipse.views;

import org.eclipse.swt.widgets.Display;

public class UISynchronizeImpl {
    private Display display;
    public UISynchronizeImpl(Display display){
        this.display = display;
    }
    public void syncExec(Runnable runnable){
        display.syncExec(runnable);
    }

    public void asyncExec(Runnable runnable){
        display.asyncExec(runnable);
    }
}
