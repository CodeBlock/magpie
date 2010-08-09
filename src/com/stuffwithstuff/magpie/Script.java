package com.stuffwithstuff.magpie;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import com.stuffwithstuff.magpie.interpreter.Interpreter;

public class Script {
  public static Script fromPath(String path) throws IOException {
    return new Script(path, readFile(path));
  }

  public static Script fromString(String text) {
    return new Script("", text);
  }
  
  public static void loadBase(Interpreter interpreter) throws IOException {
    Script script = Script.fromPath("base/base.mag");
    script.execute(interpreter);
  }
  
  public String getText() { return mText; }

  public void execute() throws IOException {
    Interpreter interpreter = new Interpreter(new ScriptInterpreterHost());
    
    // Load the base script first.
    loadBase(interpreter);
    execute(interpreter);
  }
  
  public void execute(Interpreter interpreter) {
    Lexer lexer = new Lexer(mPath, mText);
    MagpieParser parser = new MagpieParser(lexer);

    interpreter.load(parser.parse());
  }

  private static String readFile(String path) throws IOException {
    FileInputStream stream = new FileInputStream(path);

    try {
      InputStreamReader input = new InputStreamReader(
          stream, Charset.forName("UTF-8"));
      Reader reader = new BufferedReader(input);

      StringBuilder builder = new StringBuilder();
      char[] buffer = new char[8192];
      int read;

      while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
        builder.append(buffer, 0, read);
      }

      return builder.toString();
    } finally {
      stream.close();
    }
  }

  private Script(String path, String text) {
    mPath = path;
    mText = text;
  }
  
  private final String mPath;
  private final String mText;
}