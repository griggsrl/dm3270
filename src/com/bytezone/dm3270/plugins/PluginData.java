package com.bytezone.dm3270.plugins;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.KeyCode;

public class PluginData
{
  public final int sequence;
  public final List<ScreenField> screenFields;
  public final int initialCursorRow;
  public final int initialCursorColumn;

  public int cursorRow;
  public int cursorColumn;
  public KeyCode key;
  public final List<ScreenField> changedFields = new ArrayList<> ();

  public PluginData (int sequence, int row, int column, List<ScreenField> fields)
  {
    this.sequence = sequence;
    cursorRow = initialCursorRow = row;
    cursorColumn = initialCursorColumn = column;
    screenFields = fields;
  }

  public void addChangedField (ScreenField field)
  {
    if (!changedFields.contains (field))
      changedFields.add (field);
  }

  public String trimField (int index)
  {
    if (index >= 0 && index < screenFields.size ())
    {
      String data = screenFields.get (index).data;
      if (data != null)
        return data.trim ();
    }
    return "";
  }

  public void setKey (KeyCode key)
  {
    this.key = key;
  }

  public ScreenField getField (int index)
  {
    if (index >= 0 && index < screenFields.size ())
    {
      ScreenField field = screenFields.get (index);
      field.setData (this);
      return field;
    }
    return null;
  }

  public String listFields ()
  {
    StringBuilder text = new StringBuilder ();
    int count = 0;
    for (ScreenField field : screenFields)
      text.append (String.format ("%3d  %s%n", count++, field));
    if (text.length () > 0)
      text.deleteCharAt (text.length () - 1);
    return text.toString ();
  }

  public void setNewCursorPosition (int row, int column)
  {
    cursorRow = row;
    cursorColumn = column;
  }

  public List<ScreenField> getModifiableFields ()
  {
    List<ScreenField> fields = new ArrayList<> ();
    for (ScreenField field : screenFields)
      if (!field.isProtected)
        fields.add (field);
    return fields;
  }

  public boolean cursorMoved ()
  {
    return initialCursorRow != cursorRow || initialCursorColumn != cursorColumn;
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("Sequence      : %d%n", sequence));
    text.append (String.format ("Screen fields : %d%n", screenFields.size ()));
    text.append (String.format ("Modifiable    : %d%n", getModifiableFields ().size ()));

    int count = 0;
    for (ScreenField sf : screenFields)
      text.append (String.format ("  %2d field    : %s%n", count++, sf));

    return text.toString ();
  }
}