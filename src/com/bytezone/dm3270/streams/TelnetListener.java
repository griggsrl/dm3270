package com.bytezone.dm3270.streams;

import java.time.LocalDateTime;

import javafx.application.Platform;

import com.bytezone.dm3270.application.Console;
import com.bytezone.dm3270.application.Console.Function;
import com.bytezone.dm3270.application.Utility;
import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.extended.BindCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.extended.CommandHeader.DataType;
import com.bytezone.dm3270.extended.ResponseCommand;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.extended.UnbindCommand;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.session.SessionRecord;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;
import com.bytezone.dm3270.streams.TelnetSocket.Source;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand;
import com.bytezone.dm3270.telnet.TelnetCommand;
import com.bytezone.dm3270.telnet.TelnetCommandProcessor;
import com.bytezone.dm3270.telnet.TelnetProcessor;
import com.bytezone.dm3270.telnet.TelnetSubcommand;
import com.bytezone.dm3270.telnet.TerminalTypeSubcommand;

public class TelnetListener implements BufferListener, TelnetCommandProcessor
{
  private final Session session;
  private final Source source;

  // convenience variables obtained from the Session parameter
  private final TelnetState telnetState;
  private final Screen screen;
  private final Function function;

  private CommandHeader currentCommandHeader;
  private LocalDateTime currentDateTime;
  private boolean currentGenuine;
  private boolean debug;

  private final TelnetProcessor telnetProcessor = new TelnetProcessor (this);

  // Use this when recording the session in SPY mode, or replaying the session
  // in REPLAY mode.
  public TelnetListener (Source source, Session session)
  {
    this.session = session;       // where we store the session records
    this.source = source;         // are we listening to a SERVER or a CLIENT?

    this.screen = session.getScreen ();
    this.telnetState = session.getTelnetState ();
    function = screen != null ? screen.getFunction () : Console.Function.TEST;

    assert function != Console.Function.TERMINAL;
  }

  // Use this when not recording the session and running in TERMINAL mode.
  public TelnetListener (Screen screen, TelnetState telnetState)
  {
    this.screen = screen;
    this.telnetState = telnetState;
    //    this.sessionMode = SessionMode.TERMINAL;      // acting as a terminal
    this.function = screen.getFunction ();
    this.source = Source.SERVER;                  // listening to a server
    this.session = null;
  }

  // This method is always called with a copy of the original buffer. It can be
  // called from a background thread, so any GUI calls must be placed on the EDT.
  // Converts buffer arrays to Messages.

  // Called from Session when recreating a session from a file     - REPLAY mode
  // Called from a TelnetSocket thread whilst eavesdropping        - SPY mode
  // Called from a TerminalServer thread during a Terminal session - TERMINAL mode

  @Override
  public synchronized void listen (Source source, byte[] buffer, LocalDateTime dateTime,
      boolean genuine)
  {
    assert source == this.source : "Incorrect source: " + source + ", expecting: "
        + this.source;

    currentDateTime = dateTime;
    currentGenuine = genuine;

    telnetProcessor.listen (buffer);     // will call one of the processXXX routines

    //    if (sessionMode == SessionMode.TERMINAL)
    if (function == Function.TERMINAL)
      telnetState.setLastAccess (dateTime, buffer.length);
  }

  private void addDataRecord (ReplyBuffer message, SessionRecordType sessionRecordType)
  {
    // add the SessionRecord to the Session - is it OK to do this from a non-EDT?
    if (session != null)
    {
      SessionRecord sessionRecord =
          new SessionRecord (sessionRecordType, message, source, currentDateTime,
              currentGenuine);
      session.add (sessionRecord);
    }

    //    if (sessionMode == SessionMode.TERMINAL)
    if (function == Function.TERMINAL)
    {
      if (sessionRecordType == SessionRecordType.TELNET)      // no gui involved
        processMessage (message);
      else
        Platform.runLater ( () -> processMessage (message));
    }
  }

  private void processMessage (ReplyBuffer message)
  {
    message.process ();
    Buffer reply = message.getReply ();
    if (reply != null)
      telnetState.write (reply.getTelnetData ());
  }

  @Override
  public void close ()
  {
    Platform.runLater ( () -> screen.displayText (telnetState.getSummary ()));
  }

  @Override
  public void processData (byte[] buffer, int length)
  {
    System.out.println ("Unknown telnet data received:");
    System.out.println (new String (buffer, 0, length));
    System.out.println (Utility.toHex (buffer, 0, length, false));
  }

  @Override
  public void processRecord (byte[] data, int dataPtr)
  {
    int offset;
    int length;
    DataType dataType;

    if (telnetState.does3270Extended ())
    {
      offset = 5;
      length = dataPtr - 7;         // exclude IAC/EOR and header
      currentCommandHeader = new CommandHeader (data, 0, 5);
      dataType = currentCommandHeader.getDataType ();
    }
    else
    {
      offset = 0;
      length = dataPtr - 2;         // exclude IAC/EOR
      currentCommandHeader = null;
      dataType = DataType.TN3270_DATA;
    }

    switch (dataType)
    {
      case TN3270_DATA:
        ReplyBuffer command =
            source == Source.SERVER ? Command.getCommand (data, offset, length, screen)
                : Command.getReply (screen, data, offset, length);
        if (currentCommandHeader != null)
          command = new TN3270ExtendedCommand (currentCommandHeader, (Command) command);
        addDataRecord (command, SessionRecordType.TN3270);
        break;

      case BIND_IMAGE:
        BindCommand bindCommand =
            new BindCommand (currentCommandHeader, data, offset, length);
        addDataRecord (bindCommand, SessionRecordType.TN3270E);
        break;

      case UNBIND:
        UnbindCommand unbindCommand =
            new UnbindCommand (currentCommandHeader, data, offset, length);
        addDataRecord (unbindCommand, SessionRecordType.TN3270E);
        break;

      case RESPONSE:
        ResponseCommand responseCommand =
            new ResponseCommand (currentCommandHeader, data, offset, length);
        addDataRecord (responseCommand, SessionRecordType.TN3270E);
        break;

      default:
        System.out.println ("Data type not written: " + dataType);
        System.out.println (Utility.toHex (data, offset, length));
    }
  }

  @Override
  public void processTelnetCommand (byte[] data, int dataPtr)
  {
    TelnetCommand telnetCommand = new TelnetCommand (telnetState, data, dataPtr);
    telnetCommand.process ();       // updates TelnetState
    addDataRecord (telnetCommand, SessionRecordType.TELNET);
  }

  @Override
  public void processTelnetSubcommand (byte[] data, int dataPtr)
  {
    TelnetSubcommand subcommand = null;

    if (data[2] == TelnetSubcommand.TERMINAL_TYPE)
      subcommand = new TerminalTypeSubcommand (data, 0, dataPtr, telnetState);
    else if (data[2] == TelnetSubcommand.TN3270E)
      subcommand = new TN3270ExtendedSubcommand (data, 0, dataPtr, telnetState);
    else
      System.out.printf ("Unknown command type : %02X%n" + data[2]);

    if (debug)
    {
      System.out.printf ("%s: ", source);
      System.out.println (subcommand);
      System.out.println ();
    }

    if (subcommand != null)
    {
      subcommand.process ();
      addDataRecord (subcommand, SessionRecordType.TELNET);
    }
  }
}