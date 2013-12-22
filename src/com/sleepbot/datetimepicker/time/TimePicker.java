package com.sleepbot.datetimepicker.time;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.method.TransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fourmob.datetimepicker.Utils;

import de.azapps.mirakel.helper.Helpers;
import de.azapps.mirakel.helper.MirakelPreferences;
import de.azapps.mirakelandroid.R;

public class TimePicker extends LinearLayout implements
		RadialPickerLayout.OnValueSelectedListener {

	private Context ctx;
	private View layout;

	private static final String KEY_HOUR_OF_DAY = "hour_of_day";
	private static final String KEY_MINUTE = "minute";
	private static final String KEY_IS_24_HOUR_VIEW = "is_24_hour_view";
	private static final String KEY_CURRENT_ITEM_SHOWING = "current_item_showing";
	private static final String KEY_IN_KB_MODE = "in_kb_mode";
	private static final String KEY_TYPED_TIMES = "typed_times";

	public static final int HOUR_INDEX = 0;
	public static final int MINUTE_INDEX = 1;
	// NOT a real index for the purpose of what's showing.
	public static final int AMPM_INDEX = 2;
	// Also NOT a real index, just used for keyboard mode.
	public static final int ENABLE_PICKER_INDEX = 3;
	public static final int AM = 0;
	public static final int PM = 1;

	// Delay before starting the pulse animation, in ms.
	private static final int PULSE_ANIMATOR_DELAY = 300;
	private static final String TAG = "TimePicker";

	private OnTimeSetListener mCallback;

	private TextView mDoneButton;
	private TextView mHourView;
	private TextView mHourSpaceView;
	private TextView mMinuteView;
	private TextView mMinuteSpaceView;
	private TextView mAmPmTextView;
	private View mAmPmHitspace;
	private RadialPickerLayout mTimePicker;

	private int mSelectedColor;
	private int mUnselectedColor;
	private String mAmText;
	private String mPmText;

	private boolean mAllowAutoAdvance;
	private int mInitialHourOfDay;
	private int mInitialMinute;
	private boolean mIs24HourMode;

	// For hardware IME input.
	private char mPlaceholderText;
	private String mDoublePlaceholderText;
	private String mDeletedKeyFormat;
	private boolean mInKbMode;
	private ArrayList<Integer> mTypedTimes;
	private Node mLegalTimesTree;
	private int mAmKeyCode;
	private int mPmKeyCode;

	// Accessibility strings.
	private String mHourPickerDescription;
	private String mSelectHours;
	private String mMinutePickerDescription;
	private String mSelectMinutes;

	private Button mNoDateButton;

	private boolean mDark = false;
	public Dialog mDialog;

	public TimePicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		ctx = context;
		TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
				R.styleable.DatePicker, 0, 0);

		try {
			mInitialHourOfDay = a.getInt(R.styleable.TimePicker_initialHour, 0);
			mInitialMinute = a.getInt(R.styleable.TimePicker_initialMinute, 0);
			mIs24HourMode = a.getBoolean(R.styleable.TimePicker_is24HourMode,
					true);
		} finally {
			a.recycle();
		}
		layout=inflate(context, R.layout.time_picker_view, this);
		mDark=MirakelPreferences.isDark();//TODO get this from theme or so...
		initLayout();
	}

	private void initLayout() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			View v = layout.findViewById(R.id.time_picker_dialog);
			v.setBackgroundColor(ctx.getResources().getColor(
					mDark ? android.R.color.black : android.R.color.white));
		}
		KeyboardListener keyboardListener = new KeyboardListener(null);
		layout.findViewById(R.id.time_picker_dialog).setOnKeyListener(
				keyboardListener);

		Resources res = getResources();
		mHourPickerDescription = res
				.getString(R.string.hour_picker_description);
		mSelectHours = res.getString(R.string.select_hours);
		mMinutePickerDescription = res
				.getString(R.string.minute_picker_description);
		mSelectMinutes = res.getString(R.string.select_minutes);
		mSelectedColor = res.getColor(mDark ? android.R.color.holo_red_dark
				: R.color.clock_blue);// R.color.blue
		mUnselectedColor = res.getColor(mDark ? R.color.clock_white
				: R.color.numbers_text_color);// R.color.numbers_text_color

		mHourView = (TextView) layout.findViewById(R.id.hours);
		mHourView.setOnKeyListener(keyboardListener);
		mHourSpaceView = (TextView) layout.findViewById(R.id.hour_space);
		mMinuteSpaceView = (TextView) layout.findViewById(R.id.minutes_space);
		mMinuteView = (TextView) layout.findViewById(R.id.minutes);
		mMinuteView.setOnKeyListener(keyboardListener);
		mAmPmTextView = (TextView) layout.findViewById(R.id.ampm_label);
		mAmPmTextView.setOnKeyListener(keyboardListener);
		if (Build.VERSION.SDK_INT <= 14) {

			mAmPmTextView.setTransformationMethod(new TransformationMethod() {

				private final Locale locale = getResources().getConfiguration().locale;

				@Override
				public CharSequence getTransformation(CharSequence source,
						View view) {
					return source != null ? source.toString().toUpperCase(
							locale) : null;
				}

				@Override
				public void onFocusChanged(View view, CharSequence sourceText,
						boolean focused, int direction,
						Rect previouslyFocusedRect) {

				}
			});
		}
		String[] amPmTexts = new DateFormatSymbols().getAmPmStrings();
		mAmText = amPmTexts[0];
		mPmText = amPmTexts[1];

		mTimePicker = (RadialPickerLayout) layout
				.findViewById(R.id.time_picker_radial);
		mTimePicker.setOnValueSelectedListener(this);
		mTimePicker.setOnKeyListener(keyboardListener);
		mTimePicker.initialize(ctx, mInitialHourOfDay, mInitialMinute,
				mIs24HourMode, mDark);
		int currentItemShowing = HOUR_INDEX;
		// if (savedInstanceState != null
		// && savedInstanceState.containsKey(KEY_CURRENT_ITEM_SHOWING)) {
		// currentItemShowing = savedInstanceState
		// .getInt(KEY_CURRENT_ITEM_SHOWING);
		// }
		setCurrentItemShowing(currentItemShowing, false, true, true);
		mTimePicker.invalidate();

		mHourView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentItemShowing(HOUR_INDEX, true, false, true);
				mTimePicker.tryVibrate();
			}

		});
		mMinuteView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentItemShowing(MINUTE_INDEX, true, false, true);
				mTimePicker.tryVibrate();
			}
		});

		mDoneButton = (TextView) layout.findViewById(R.id.done);
		mDoneButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mInKbMode && isTypedTimeFullyLegal()) {
					finishKbMode(false);
				} else {
					mTimePicker.tryVibrate();
				}
				if (mCallback != null) {
					mCallback.onTimeSet(mTimePicker, mTimePicker.getHours(),
							mTimePicker.getMinutes());
				}
			}
		});
		mDoneButton.setOnKeyListener(keyboardListener);
		this.mNoDateButton = ((Button) layout.findViewById(R.id.dismiss));
		this.mNoDateButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// DatePickerDialog.this.tryVibrate();/
				if (mCallback != null)
					mCallback.onNoTimeSet();
			}
		});
		if (mDark) {
			View header = layout.findViewById(R.id.time_dialog_head);
			header.setBackgroundColor(res.getColor(R.color.dialog_dark_gray));

			View dialog = layout.findViewById(R.id.time_picker_dialog);
			dialog.setBackgroundColor(res.getColor(R.color.dialog_gray));

			View header_background = layout
					.findViewById(R.id.header_background_timepicker);
			header_background.setBackgroundColor(res
					.getColor(R.color.dialog_gray));

			View hairline = layout.findViewById(R.id.hairline_timepicker);
			if (hairline != null)
				hairline.setBackgroundColor(res.getColor(R.color.clock_gray));
			this.mDoneButton.setTextColor(mUnselectedColor);
			this.mNoDateButton.setTextColor(mUnselectedColor);

		}else{
			mDoneButton.setTextColor(res.getColor(R.color.Black));
			mNoDateButton.setTextColor(res.getColor(R.color.Black));
		}

		// Enable or disable the AM/PM view.
		mAmPmHitspace = layout.findViewById(R.id.ampm_hitspace);
		if (mIs24HourMode) {
			mAmPmTextView.setVisibility(View.GONE);

			RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			paramsSeparator.addRule(RelativeLayout.CENTER_IN_PARENT);
			TextView separatorView = (TextView) layout
					.findViewById(R.id.separator);
			separatorView.setLayoutParams(paramsSeparator);
		} else {
			mAmPmTextView.setVisibility(View.VISIBLE);
			updateAmPmDisplay(mInitialHourOfDay < 12 ? AM : PM);
			mAmPmHitspace.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mTimePicker.tryVibrate();
					int amOrPm = mTimePicker.getIsCurrentlyAmOrPm();
					if (amOrPm == AM) {
						amOrPm = PM;
					} else if (amOrPm == PM) {
						amOrPm = AM;
					}
					updateAmPmDisplay(amOrPm);
					mTimePicker.setAmOrPm(amOrPm);
				}
			});
		}

		mAllowAutoAdvance = true;
		setHour(mInitialHourOfDay, true);
		setMinute(mInitialMinute);

		// Set up for keyboard mode.
		mDoublePlaceholderText = res.getString(R.string.time_placeholder);
		mDeletedKeyFormat = res.getString(R.string.deleted_key);
		mPlaceholderText = mDoublePlaceholderText.charAt(0);
		mAmKeyCode = mPmKeyCode = -1;
		generateLegalTimesTree();
		if (mInKbMode) {
			// mTypedTimes = savedInstanceState
			// .getIntegerArrayList(KEY_TYPED_TIMES);
			tryStartingKbMode(-1);
			mHourView.invalidate();
		} else if (mTypedTimes == null) {
			mTypedTimes = new ArrayList<Integer>();
		}

	}

	private void updateAmPmDisplay(int amOrPm) {
		if (amOrPm == AM) {
			mAmPmTextView.setText(mAmText);
			Utils.tryAccessibilityAnnounce(mTimePicker, mAmText);
			mAmPmHitspace.setContentDescription(mAmText);
		} else if (amOrPm == PM) {
			mAmPmTextView.setText(mPmText);
			Utils.tryAccessibilityAnnounce(mTimePicker, mPmText);
			mAmPmHitspace.setContentDescription(mPmText);
		} else {
			mAmPmTextView.setText(mDoublePlaceholderText);
		}
	}

	/**
	 * Called by the picker for updating the header display.
	 */
	@Override
	public void onValueSelected(int pickerIndex, int newValue,
			boolean autoAdvance) {
		if (pickerIndex == HOUR_INDEX) {
			setHour(newValue, false);
			String announcement = String.format(Helpers.getLocal(getContext()),
					"%d", newValue);
			if (mAllowAutoAdvance && autoAdvance) {
				setCurrentItemShowing(MINUTE_INDEX, true, true, false);
				announcement += ". " + mSelectMinutes;
			}
			Utils.tryAccessibilityAnnounce(mTimePicker, announcement);
		} else if (pickerIndex == MINUTE_INDEX) {
			setMinute(newValue);
		} else if (pickerIndex == AMPM_INDEX) {
			updateAmPmDisplay(newValue);
		} else if (pickerIndex == ENABLE_PICKER_INDEX) {
			if (!isTypedTimeFullyLegal()) {
				mTypedTimes.clear();
			}
			finishKbMode(true);
		}
	}

	public void setTime(int hourOfDay, int minutes) {
		mTimePicker.setTime(hourOfDay, minutes);
	}

	public void setOnTimeSetListener(OnTimeSetListener callback) {
		mCallback = callback;
	}

	public void setStartTime(int hourOfDay, int minute) {
		mInitialHourOfDay = hourOfDay;
		mInitialMinute = minute;
		mInKbMode = false;
	}

	public void setHour(int value, boolean announce) {
		String format;
		if (mIs24HourMode) {
			format = "%02d";
		} else {
			format = "%d";
			value = value % 12;
			if (value == 0) {
				value = 12;
			}
		}

		CharSequence text = String.format(format, value);
		if (mHourView != null)
			mHourView.setText(text);
		if (mHourSpaceView != null)
			mHourSpaceView.setText(text);
		if (announce) {
			Utils.tryAccessibilityAnnounce(mTimePicker, text);
		}
	}

	public void set24HourMode(boolean mode) {
		mIs24HourMode = mode;
		if (mTimePicker != null) {
			mTimePicker.initialize(ctx, mInitialHourOfDay, mInitialMinute,
					mIs24HourMode, mDark);
			mTimePicker.invalidate();
		}
		updateDisplay(true);
	}

	public void setMinute(int value) {
		if (value == 60) {
			value = 0;
		}
		CharSequence text = String.format(Locale.getDefault(), "%02d", value);
		Utils.tryAccessibilityAnnounce(mTimePicker, text);
		if (mMinuteView != null)
			mMinuteView.setText(text);
		if (mMinuteSpaceView != null)
			mMinuteSpaceView.setText(text);
	}

	// Show either Hours or Minutes.
	private void setCurrentItemShowing(int index, boolean animateCircle,
			boolean delayLabelAnimate, boolean announce) {
		mTimePicker.setCurrentItemShowing(index, animateCircle);

		TextView labelToAnimate;
		if (index == HOUR_INDEX) {
			int hours = mTimePicker.getHours();
			if (!mIs24HourMode) {
				hours = hours % 12;
			}
			mTimePicker.setContentDescription(mHourPickerDescription + ": "
					+ hours);
			if (announce) {
				Utils.tryAccessibilityAnnounce(mTimePicker, mSelectHours);
			}
			labelToAnimate = mHourView;
		} else {
			int minutes = mTimePicker.getMinutes();
			mTimePicker.setContentDescription(mMinutePickerDescription + ": "
					+ minutes);
			if (announce) {
				Utils.tryAccessibilityAnnounce(mTimePicker, mSelectMinutes);
			}
			labelToAnimate = mMinuteView;
		}

		int hourColor = (index == HOUR_INDEX) ? mSelectedColor
				: mUnselectedColor;
		int minuteColor = (index == MINUTE_INDEX) ? mSelectedColor
				: mUnselectedColor;
		mHourView.setTextColor(hourColor);
		mMinuteView.setTextColor(minuteColor);

		com.nineoldandroids.animation.ObjectAnimator pulseAnimator = Utils
				.getPulseAnimator(labelToAnimate, 0.85f, 1.1f);
		if (delayLabelAnimate) {
			pulseAnimator.setStartDelay(PULSE_ANIMATOR_DELAY);
		}
		pulseAnimator.start();
	}

	/**
	 * For keyboard mode, processes key events.
	 * 
	 * @param keyCode
	 *            the pressed key.
	 * @return true if the key was successfully processed, false otherwise.
	 */
	private boolean processKeyUp(int keyCode) {
		if (keyCode == KeyEvent.KEYCODE_ESCAPE
				|| keyCode == KeyEvent.KEYCODE_BACK) {
			if (mDialog != null)
				mDialog.dismiss();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_TAB) {
			if (mInKbMode) {
				if (isTypedTimeFullyLegal()) {
					finishKbMode(true);
				}
				return true;
			}
		} else if (keyCode == KeyEvent.KEYCODE_ENTER) {
			if (mInKbMode) {
				if (!isTypedTimeFullyLegal()) {
					return true;
				}
				finishKbMode(false);
			}
			if (mCallback != null) {
				mCallback.onTimeSet(mTimePicker, mTimePicker.getHours(),
						mTimePicker.getMinutes());
			}
			if (mDialog != null)
				mDialog.dismiss();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DEL) {
			if (mInKbMode) {
				if (!mTypedTimes.isEmpty()) {
					int deleted = deleteLastTypedKey();
					String deletedKeyStr;
					if (deleted == getAmOrPmKeyCode(AM)) {
						deletedKeyStr = mAmText;
					} else if (deleted == getAmOrPmKeyCode(PM)) {
						deletedKeyStr = mPmText;
					} else {
						deletedKeyStr = String.format(
								Helpers.getLocal(getContext()), "%d",
								getValFromKeyCode(deleted));
					}
					Utils.tryAccessibilityAnnounce(mTimePicker,
							String.format(mDeletedKeyFormat, deletedKeyStr));
					updateDisplay(true);
				}
			}
		} else if (keyCode == KeyEvent.KEYCODE_0
				|| keyCode == KeyEvent.KEYCODE_1
				|| keyCode == KeyEvent.KEYCODE_2
				|| keyCode == KeyEvent.KEYCODE_3
				|| keyCode == KeyEvent.KEYCODE_4
				|| keyCode == KeyEvent.KEYCODE_5
				|| keyCode == KeyEvent.KEYCODE_6
				|| keyCode == KeyEvent.KEYCODE_7
				|| keyCode == KeyEvent.KEYCODE_8
				|| keyCode == KeyEvent.KEYCODE_9
				|| (!mIs24HourMode && (keyCode == getAmOrPmKeyCode(AM) || keyCode == getAmOrPmKeyCode(PM)))) {
			if (!mInKbMode) {
				if (mTimePicker == null) {
					// Something's wrong, because time picker should definitely
					// not be null.
					Log.e(TAG,
							"Unable to initiate keyboard mode, TimePicker was null.");
					return true;
				}
				mTypedTimes.clear();
				tryStartingKbMode(keyCode);
				return true;
			}
			// We're already in keyboard mode.
			if (addKeyIfLegal(keyCode)) {
				updateDisplay(false);
			}
			return true;
		}
		return false;
	}

	/**
	 * Try to start keyboard mode with the specified key, as long as the
	 * timepicker is not in the middle of a touch-event.
	 * 
	 * @param keyCode
	 *            The key to use as the first press. Keyboard mode will not be
	 *            started if the key is not legal to start with. Or, pass in -1
	 *            to get into keyboard mode without a starting key.
	 */
	private void tryStartingKbMode(int keyCode) {
		if (mTimePicker.trySettingInputEnabled(false)
				&& (keyCode == -1 || addKeyIfLegal(keyCode))) {
			mInKbMode = true;
			mDoneButton.setEnabled(false);
			updateDisplay(false);
		}
	}

	private boolean addKeyIfLegal(int keyCode) {
		// If we're in 24hour mode, we'll need to check if the input is full. If
		// in AM/PM mode,
		// we'll need to see if AM/PM have been typed.
		if ((mIs24HourMode && mTypedTimes.size() == 4)
				|| (!mIs24HourMode && isTypedTimeFullyLegal())) {
			return false;
		}

		mTypedTimes.add(keyCode);
		if (!isTypedTimeLegalSoFar()) {
			deleteLastTypedKey();
			return false;
		}

		int val = getValFromKeyCode(keyCode);
		Utils.tryAccessibilityAnnounce(mTimePicker, String.format("%d", val));
		// Automatically fill in 0's if AM or PM was legally entered.
		if (isTypedTimeFullyLegal()) {
			if (!mIs24HourMode && mTypedTimes.size() <= 3) {
				mTypedTimes.add(mTypedTimes.size() - 1, KeyEvent.KEYCODE_0);
				mTypedTimes.add(mTypedTimes.size() - 1, KeyEvent.KEYCODE_0);
			}
			mDoneButton.setEnabled(true);
		}

		return true;
	}

	/**
	 * Traverse the tree to see if the keys that have been typed so far are
	 * legal as is, or may become legal as more keys are typed (excluding
	 * backspace).
	 */
	private boolean isTypedTimeLegalSoFar() {
		Node node = mLegalTimesTree;
		for (int keyCode : mTypedTimes) {
			node = node.canReach(keyCode);
			if (node == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if the time that has been typed so far is completely legal, as is.
	 */
	private boolean isTypedTimeFullyLegal() {
		if (mIs24HourMode) {
			// For 24-hour mode, the time is legal if the hours and minutes are
			// each legal. Note:
			// getEnteredTime() will ONLY call isTypedTimeFullyLegal() when NOT
			// in 24hour mode.
			int[] values = getEnteredTime(null);
			return (values[0] >= 0 && values[1] >= 0 && values[1] < 60);
		}
		// For AM/PM mode, the time is legal if it contains an AM or PM, as
		// those can only be
		// legally added at specific times based on the tree's algorithm.
		return (mTypedTimes.contains(getAmOrPmKeyCode(AM)) || mTypedTimes
				.contains(getAmOrPmKeyCode(PM)));
	}

	private int deleteLastTypedKey() {
		int deleted = mTypedTimes.remove(mTypedTimes.size() - 1);
		if (!isTypedTimeFullyLegal()) {
			mDoneButton.setEnabled(false);
		}
		return deleted;
	}

	/**
	 * Get out of keyboard mode. If there is nothing in typedTimes, revert to
	 * TimePicker's time.
	 * 
	 * @param updateDisplays
	 *            If true, update the displays with the relevant time.
	 */
	private void finishKbMode(boolean updateDisplays) {
		mInKbMode = false;
		if (!mTypedTimes.isEmpty()) {
			int values[] = getEnteredTime(null);
			mTimePicker.setTime(values[0], values[1]);
			if (!mIs24HourMode) {
				mTimePicker.setAmOrPm(values[2]);
			}
			mTypedTimes.clear();
		}
		if (updateDisplays) {
			updateDisplay(false);
			mTimePicker.trySettingInputEnabled(true);
		}
	}

	/**
	 * Update the hours, minutes, and AM/PM displays with the typed times. If
	 * the typedTimes is empty, either show an empty display (filled with the
	 * placeholder text), or update from the timepicker's values.
	 * 
	 * @param allowEmptyDisplay
	 *            if true, then if the typedTimes is empty, use the placeholder
	 *            text. Otherwise, revert to the timepicker's values.
	 */
	private void updateDisplay(boolean allowEmptyDisplay) {
		if (!allowEmptyDisplay && mTypedTimes.isEmpty()) {
			int hour = mTimePicker.getHours();
			int minute = mTimePicker.getMinutes();
			setHour(hour, true);
			setMinute(minute);
			if (!mIs24HourMode) {
				updateAmPmDisplay(hour < 12 ? AM : PM);
			}
			setCurrentItemShowing(mTimePicker.getCurrentItemShowing(), true,
					true, true);
			mDoneButton.setEnabled(true);
		} else {
			Boolean[] enteredZeros = { false, false };
			int[] values = getEnteredTime(enteredZeros);
			String hourFormat = enteredZeros[0] ? "%02d" : "%2d";
			String minuteFormat = (enteredZeros[1]) ? "%02d" : "%2d";
			String hourStr = (values[0] == -1) ? mDoublePlaceholderText
					: String.format(hourFormat, values[0]).replace(' ',
							mPlaceholderText);
			String minuteStr = (values[1] == -1) ? mDoublePlaceholderText
					: String.format(minuteFormat, values[1]).replace(' ',
							mPlaceholderText);
			mHourView.setText(hourStr);
			mHourSpaceView.setText(hourStr);
			mHourView.setTextColor(mUnselectedColor);
			mMinuteView.setText(minuteStr);
			mMinuteSpaceView.setText(minuteStr);
			mMinuteView.setTextColor(mUnselectedColor);
			if (!mIs24HourMode) {
				updateAmPmDisplay(values[2]);
			}
		}
	}

	private int getValFromKeyCode(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_0:
			return 0;
		case KeyEvent.KEYCODE_1:
			return 1;
		case KeyEvent.KEYCODE_2:
			return 2;
		case KeyEvent.KEYCODE_3:
			return 3;
		case KeyEvent.KEYCODE_4:
			return 4;
		case KeyEvent.KEYCODE_5:
			return 5;
		case KeyEvent.KEYCODE_6:
			return 6;
		case KeyEvent.KEYCODE_7:
			return 7;
		case KeyEvent.KEYCODE_8:
			return 8;
		case KeyEvent.KEYCODE_9:
			return 9;
		default:
			return -1;
		}
	}

	/**
	 * Get the currently-entered time, as integer values of the hours and
	 * minutes typed.
	 * 
	 * @param enteredZeros
	 *            A size-2 boolean array, which the caller should initialize,
	 *            and which may then be used for the caller to know whether
	 *            zeros had been explicitly entered as either hours of minutes.
	 *            This is helpful for deciding whether to show the dashes, or
	 *            actual 0's.
	 * @return A size-3 int array. The first value will be the hours, the second
	 *         value will be the minutes, and the third will be either
	 *         TimePickerDialog.AM or TimePickerDialog.PM.
	 */
	private int[] getEnteredTime(Boolean[] enteredZeros) {
		int amOrPm = -1;
		int startIndex = 1;
		if (!mIs24HourMode && isTypedTimeFullyLegal()) {
			int keyCode = mTypedTimes.get(mTypedTimes.size() - 1);
			if (keyCode == getAmOrPmKeyCode(AM)) {
				amOrPm = AM;
			} else if (keyCode == getAmOrPmKeyCode(PM)) {
				amOrPm = PM;
			}
			startIndex = 2;
		}
		int minute = -1;
		int hour = -1;
		for (int i = startIndex; i <= mTypedTimes.size(); i++) {
			int val = getValFromKeyCode(mTypedTimes.get(mTypedTimes.size() - i));
			if (i == startIndex) {
				minute = val;
			} else if (i == startIndex + 1) {
				minute += 10 * val;
				if (enteredZeros != null && val == 0) {
					enteredZeros[1] = true;
				}
			} else if (i == startIndex + 2) {
				hour = val;
			} else if (i == startIndex + 3) {
				hour += 10 * val;
				if (enteredZeros != null && val == 0) {
					enteredZeros[0] = true;
				}
			}
		}

		int[] ret = { hour, minute, amOrPm };
		return ret;
	}

	/**
	 * Get the keycode value for AM and PM in the current language.
	 */
	private int getAmOrPmKeyCode(int amOrPm) {
		// Cache the codes.
		if (mAmKeyCode == -1 || mPmKeyCode == -1) {
			// Find the first character in the AM/PM text that is unique.
			KeyCharacterMap kcm = KeyCharacterMap
					.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
			char amChar;
			char pmChar;
			for (int i = 0; i < Math.max(mAmText.length(), mPmText.length()); i++) {
				amChar = mAmText.toLowerCase(Locale.getDefault()).charAt(i);
				pmChar = mPmText.toLowerCase(Locale.getDefault()).charAt(i);
				if (amChar != pmChar) {
					KeyEvent[] events = kcm.getEvents(new char[] { amChar,
							pmChar });
					// There should be 4 events: a down and up for both AM and
					// PM.
					if (events != null && events.length == 4) {
						mAmKeyCode = events[0].getKeyCode();
						mPmKeyCode = events[2].getKeyCode();
					} else {
						Log.e(TAG, "Unable to find keycodes for AM and PM.");
					}
					break;
				}
			}
		}
		if (amOrPm == AM) {
			return mAmKeyCode;
		} else if (amOrPm == PM) {
			return mPmKeyCode;
		}

		return -1;
	}

	/**
	 * Create a tree for deciding what keys can legally be typed.
	 */
	private void generateLegalTimesTree() {
		// Create a quick cache of numbers to their keycodes.
		int k0 = KeyEvent.KEYCODE_0;
		int k1 = KeyEvent.KEYCODE_1;
		int k2 = KeyEvent.KEYCODE_2;
		int k3 = KeyEvent.KEYCODE_3;
		int k4 = KeyEvent.KEYCODE_4;
		int k5 = KeyEvent.KEYCODE_5;
		int k6 = KeyEvent.KEYCODE_6;
		int k7 = KeyEvent.KEYCODE_7;
		int k8 = KeyEvent.KEYCODE_8;
		int k9 = KeyEvent.KEYCODE_9;

		// The root of the tree doesn't contain any numbers.
		mLegalTimesTree = new Node();
		if (mIs24HourMode) {
			// We'll be re-using these nodes, so we'll save them.
			Node minuteFirstDigit = new Node(k0, k1, k2, k3, k4, k5);
			Node minuteSecondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7,
					k8, k9);
			// The first digit must be followed by the second digit.
			minuteFirstDigit.addChild(minuteSecondDigit);

			// The first digit may be 0-1.
			Node firstDigit = new Node(k0, k1);
			mLegalTimesTree.addChild(firstDigit);

			// When the first digit is 0-1, the second digit may be 0-5.
			Node secondDigit = new Node(k0, k1, k2, k3, k4, k5);
			firstDigit.addChild(secondDigit);
			// We may now be followed by the first minute digit. E.g. 00:09,
			// 15:58.
			secondDigit.addChild(minuteFirstDigit);

			// When the first digit is 0-1, and the second digit is 0-5, the
			// third digit may be 6-9.
			Node thirdDigit = new Node(k6, k7, k8, k9);
			// The time must now be finished. E.g. 0:55, 1:08.
			secondDigit.addChild(thirdDigit);

			// When the first digit is 0-1, the second digit may be 6-9.
			secondDigit = new Node(k6, k7, k8, k9);
			firstDigit.addChild(secondDigit);
			// We must now be followed by the first minute digit. E.g. 06:50,
			// 18:20.
			secondDigit.addChild(minuteFirstDigit);

			// The first digit may be 2.
			firstDigit = new Node(k2);
			mLegalTimesTree.addChild(firstDigit);

			// When the first digit is 2, the second digit may be 0-3.
			secondDigit = new Node(k0, k1, k2, k3);
			firstDigit.addChild(secondDigit);
			// We must now be followed by the first minute digit. E.g. 20:50,
			// 23:09.
			secondDigit.addChild(minuteFirstDigit);

			// When the first digit is 2, the second digit may be 4-5.
			secondDigit = new Node(k4, k5);
			firstDigit.addChild(secondDigit);
			// We must now be followd by the last minute digit. E.g. 2:40, 2:53.
			secondDigit.addChild(minuteSecondDigit);

			// The first digit may be 3-9.
			firstDigit = new Node(k3, k4, k5, k6, k7, k8, k9);
			mLegalTimesTree.addChild(firstDigit);
			// We must now be followed by the first minute digit. E.g. 3:57,
			// 8:12.
			firstDigit.addChild(minuteFirstDigit);
		} else {
			// We'll need to use the AM/PM node a lot.
			// Set up AM and PM to respond to "a" and "p".
			Node ampm = new Node(getAmOrPmKeyCode(AM), getAmOrPmKeyCode(PM));

			// The first hour digit may be 1.
			Node firstDigit = new Node(k1);
			mLegalTimesTree.addChild(firstDigit);
			// We'll allow quick input of on-the-hour times. E.g. 1pm.
			firstDigit.addChild(ampm);

			// When the first digit is 1, the second digit may be 0-2.
			Node secondDigit = new Node(k0, k1, k2);
			firstDigit.addChild(secondDigit);
			// Also for quick input of on-the-hour times. E.g. 10pm, 12am.
			secondDigit.addChild(ampm);

			// When the first digit is 1, and the second digit is 0-2, the third
			// digit may be 0-5.
			Node thirdDigit = new Node(k0, k1, k2, k3, k4, k5);
			secondDigit.addChild(thirdDigit);
			// The time may be finished now. E.g. 1:02pm, 1:25am.
			thirdDigit.addChild(ampm);

			// When the first digit is 1, the second digit is 0-2, and the third
			// digit is 0-5,
			// the fourth digit may be 0-9.
			Node fourthDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
			thirdDigit.addChild(fourthDigit);
			// The time must be finished now. E.g. 10:49am, 12:40pm.
			fourthDigit.addChild(ampm);

			// When the first digit is 1, and the second digit is 0-2, the third
			// digit may be 6-9.
			thirdDigit = new Node(k6, k7, k8, k9);
			secondDigit.addChild(thirdDigit);
			// The time must be finished now. E.g. 1:08am, 1:26pm.
			thirdDigit.addChild(ampm);

			// When the first digit is 1, the second digit may be 3-5.
			secondDigit = new Node(k3, k4, k5);
			firstDigit.addChild(secondDigit);

			// When the first digit is 1, and the second digit is 3-5, the third
			// digit may be 0-9.
			thirdDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
			secondDigit.addChild(thirdDigit);
			// The time must be finished now. E.g. 1:39am, 1:50pm.
			thirdDigit.addChild(ampm);

			// The hour digit may be 2-9.
			firstDigit = new Node(k2, k3, k4, k5, k6, k7, k8, k9);
			mLegalTimesTree.addChild(firstDigit);
			// We'll allow quick input of on-the-hour-times. E.g. 2am, 5pm.
			firstDigit.addChild(ampm);

			// When the first digit is 2-9, the second digit may be 0-5.
			secondDigit = new Node(k0, k1, k2, k3, k4, k5);
			firstDigit.addChild(secondDigit);

			// When the first digit is 2-9, and the second digit is 0-5, the
			// third digit may be 0-9.
			thirdDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
			secondDigit.addChild(thirdDigit);
			// The time must be finished now. E.g. 2:57am, 9:30pm.
			thirdDigit.addChild(ampm);
		}
	}

	/**
	 * Simple node class to be used for traversal to check for legal times.
	 * mLegalKeys represents the keys that can be typed to get to the node.
	 * mChildren are the children that can be reached from this node.
	 */
	public class Node {
		private int[] mLegalKeys;
		private ArrayList<Node> mChildren;

		public Node(int... legalKeys) {
			mLegalKeys = legalKeys;
			mChildren = new ArrayList<Node>();
		}

		public void addChild(Node child) {
			mChildren.add(child);
		}

		public boolean containsKey(int key) {
			for (int i = 0; i < mLegalKeys.length; i++) {
				if (mLegalKeys[i] == key) {
					return true;
				}
			}
			return false;
		}

		public Node canReach(int key) {
			if (mChildren == null) {
				return null;
			}
			for (Node child : mChildren) {
				if (child.containsKey(key)) {
					return child;
				}
			}
			return null;
		}
	}

	public class KeyboardListener implements OnKeyListener {
		public KeyboardListener(Dialog d) {
			mDialog = d;
		}

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				return processKeyUp(keyCode);
			}
			return false;
		}
	}

	public interface OnTimeSetListener {

		/**
		 * @param view
		 *            The view associated with this listener.
		 * @param hourOfDay
		 *            The hour that was set.
		 * @param minute
		 *            The minute that was set.
		 */
		void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute);

		void onNoTimeSet();
	}

	public int getHour() {
		if (mTimePicker != null)
			return mTimePicker.getHours() + (mAmKeyCode == PM ? 12 : 0);
		return 0;
	}

	public int getMinute() {
		if (mTimePicker != null)
			return mTimePicker.getMinutes();
		return 0;
	}

	public void setOnKeyListener(KeyboardListener keyboardListener) {
		if (mDoneButton != null)
			mDoneButton.setOnKeyListener(keyboardListener);
		if (mNoDateButton != null)
			mNoDateButton.setOnKeyListener(keyboardListener);
		if (mMinuteView != null)
			mMinuteView.setOnKeyListener(keyboardListener);
		if (mHourView != null)
			mHourView.setOnKeyListener(keyboardListener);
		if (mAmPmTextView != null)
			mAmPmTextView.setOnKeyListener(keyboardListener);
		if (mTimePicker != null)
			mTimePicker.setOnKeyListener(keyboardListener);
	}

	public KeyboardListener getNewKeyboardListner(Dialog dialog) {
		return new KeyboardListener(dialog);
	}

	@Override
	public Parcelable onSaveInstanceState() {
		super.onSaveInstanceState();
		Bundle outState = new Bundle();
		 if (mTimePicker != null) {
	            outState.putInt(KEY_HOUR_OF_DAY, mTimePicker.getHours());
	            outState.putInt(KEY_MINUTE, mTimePicker.getMinutes());
	            outState.putBoolean(KEY_IS_24_HOUR_VIEW, mIs24HourMode);
	            outState.putInt(KEY_CURRENT_ITEM_SHOWING, mTimePicker.getCurrentItemShowing());
	            outState.putBoolean(KEY_IN_KB_MODE, mInKbMode);
	            if (mInKbMode) {
	                outState.putIntegerArrayList(KEY_TYPED_TIMES, mTypedTimes);
	            }
	        }
		return outState;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof Bundle)) {
			super.onRestoreInstanceState(state);
			return;
		}
		// Bundle b=(Bundle) state;
	}

}
