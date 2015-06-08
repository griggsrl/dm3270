package com.bytezone.dm3270.orders;

import com.bytezone.dm3270.display.Cursor;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;

public class EraseUnprotectedToAddressOrder extends Order
{
  private final BufferAddress stopAddress;

  public EraseUnprotectedToAddressOrder (byte[] buffer, int offset)
  {
    assert buffer[offset] == Order.ERASE_UNPROTECTED;
    stopAddress = new BufferAddress (buffer[offset + 1], buffer[offset + 2]);

    this.buffer = new byte[3];
    System.arraycopy (buffer, offset, this.buffer, 0, this.buffer.length);
  }

  @Override
  public void process (Screen screen)
  {
    if (oldWay)
    {
      Cursor cursor = screen.getScreenCursor ();
      int cursorPostion = cursor.getLocation ();
      Field resetField = null;

      for (Field field : screen.getUnprotectedFields ())
        if (field.contains (cursorPostion))
        {
          resetField = field;
          break;
        }

      // this relies on stopAddress being in an unprotected field
      while (resetField != null)
      {
        resetField.clear (false);       // don't set modified (is this correct?)
        if (resetField.contains (stopAddress.getLocation ()))
        {
          cursor.moveTo (resetField.getFirstLocation ());
          break;
        }
        resetField = resetField.getNextUnprotectedField ();
      }
    }
    else
    {
      System.out.println ("EraseUnprotected not finished");
    }
  }

  @Override
  public String toString ()
  {
    return "EUA : " + stopAddress;
  }
}