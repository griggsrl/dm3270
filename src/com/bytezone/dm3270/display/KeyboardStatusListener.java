package com.bytezone.dm3270.display;

public interface KeyboardStatusListener
{
  public abstract void keyboardStatusChanged (boolean keyboardLocked, boolean insert);
}
