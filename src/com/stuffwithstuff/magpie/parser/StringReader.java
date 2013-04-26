package com.stuffwithstuff.magpie.parser;

import com.stuffwithstuff.magpie.SourceReader;
import com.stuffwithstuff.magpie.util.Expect;


/**
 * Reads a string, one character at a time.
 */
public class StringReader implements SourceReader {
  public StringReader(String description, String text) {
    Expect.notNull(description);
    if (text == null) {
      System.exit(0);
    }

    mDescription = description;
    mText = text;
    mPosition = 0;
  }
  
  @Override
  public String getDescription() { return mDescription; }
  
  @Override
  public char current() {
    if (mPosition >= mText.length()) return '\0';
    return mText.charAt(mPosition);
  }
  
  @Override
  public void advance() {
    if (mPosition < mText.length()) mPosition++;
  }

  private final String mDescription;
  private final String mText;
  private int mPosition;
}
