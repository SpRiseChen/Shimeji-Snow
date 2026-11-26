package com.group_finity.mascot.action;

import java.util.ResourceBundle;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;

/** Keeps the approach, carry and release attached to the same window. */
public final class WindowThrowSequence extends Sequence {
    private boolean released;

    public WindowThrowSequence(ResourceBundle schema, VariableMap params, Action... actions) {
        super(schema, params, actions);
    }

    @Override
    public void init(Mascot mascot) throws VariableException {
        released = false;
        setWindowTarget(mascot.getEnvironment().getActiveIE());
        super.init(mascot);
    }

    @Override
    protected void setCurrentAction(int index) throws VariableException {
        super.setCurrentAction(index);
        if (getCurrentAction() < getActions().length && getAction() instanceof ThrowIE) {
            released = true;
        }
    }

    @Override
    protected void tick() throws LostGroundException, VariableException {
        if (screenChanged() || (!released && (!getWindowTarget().isVisible()
                || !Boolean.parseBoolean(Main.getInstance().getProperties().getProperty("Throwing", "true"))))) {
            throw new LostGroundException();
        }
        super.tick();
    }
}
