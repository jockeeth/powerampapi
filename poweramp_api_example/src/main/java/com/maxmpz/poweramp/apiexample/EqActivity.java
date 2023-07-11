/*
Copyright (C) 2011-2021 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for widgets, plugins, applications and other software
which communicate with Poweramp application on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.maxmpz.poweramp.apiexample;

import java.util.regex.Pattern;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;

public class EqActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener, AdapterView.OnItemSelectedListener {
	private static final String TAG = "EqActivity";
	private static final boolean LOG = true;

	private static final Pattern sSemicolonSplitRe = Pattern.compile(";");
	private static final Pattern sEqualSplitRe = Pattern.compile("=");

	Intent mEquIntent;
	private boolean mEquBuilt;

	private boolean mSettingEqu;
	private boolean mSettingTone;
	private boolean mSettingPreset;

	@SuppressWarnings({ "resource", "deprecation" })
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Ask for PA equ state update immediately by sending "empty" SET_EQU_ENABLED command.
		// The reply may be delayed up to 250ms, so UI should accommodate for that (update asynchronously)
        this.requestEqStatus();

		setContentView(R.layout.activity_eq);

		((CheckBox) this.findViewById(R.id.dynamic)).setOnCheckedChangeListener(this);
        this.findViewById(R.id.commit_eq).setOnClickListener(this);

		// Create and bind spinner which binds to available Poweramp presets.
		final Spinner presetSpinner = this.findViewById(R.id.preset_spinner);
		final String[] cols = { "_id", "name" };
		final Cursor c = this.getContentResolver().query(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("eq_presets").build(),
				cols, null, null, "name");
		if(null != c) this.startManagingCursor(c);
		// Add first empty item to the merged cursor via matrix cursor with single row.
		final MatrixCursor mc = new MatrixCursor(cols);
		mc.addRow(new Object[]{ PowerampAPI.NO_ID, "" });
		final MergeCursor mrgc = new MergeCursor(new Cursor[]{ mc, c });

		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_spinner_dropdown_item,
				mrgc,
				new String[] { "name" },
				new int[] { android.R.id.text1 },
				0);

		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
				((TextView)view).setText(cursor.getString(1));
				return true;
			}
		});

		presetSpinner.setAdapter(adapter);
		presetSpinner.setOnItemSelectedListener(this);

		((CheckBox) this.findViewById(R.id.eq)).setOnCheckedChangeListener(this);
		((CheckBox) this.findViewById(R.id.tone)).setOnCheckedChangeListener(this);
	}

	private void requestEqStatus() {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_ENABLED));
	}

	/**
	 * NOTE: when screen is rotated, by default android will reapply all saved values to the controls, calling the event handlers, which generate appropriate intents, thus,
	 * on screen rotation some commands could be sent to Poweramp unintentionally.
	 * As this activity always syncs everything with the actual state of Poweramp, this automatic restoring of state is just non needed.
	 */
	@Override
	protected void onSaveInstanceState(final Bundle outState) {
	}

	@Override
	protected void onRestoreInstanceState(final Bundle savedInstanceState) {
	}


	/**
	 * NOTE: this method unregister all broadcast receivers on activity pause. This is the correct way of handling things - we're
	 * sure no unnecessary event processing will be done for paused activity, when screen is OFF, etc.
 	 */
	@Override
	protected void onPause() {
        this.unregister();

		super.onPause();
	}

	/**
	 * Register broadcast receivers.
 	 */
	@Override
	protected void onResume() {
		super.onResume();

        this.registerAndLoadStatus();

		// Ask PA for eq state as, while on background, we probably were denied of intent processing due to
		// Android 8+ background limitations
        this.requestEqStatus();
	}


	@Override
	protected void onDestroy() {
        this.unregister();

        this.mEquReceiver = null;

		super.onDestroy();
	}

	/**
	 * NOTE, it's not necessary to set mStatusIntent/mPlayingModeIntent/mEquIntent this way here,
	 * but this approach can be used with null receiver to get current sticky intent without broadcast receiver
	 *
	 * NOTE: For Poweramp v3 this intent is not sticky anymore
	 */
	private void registerAndLoadStatus() {
        this.mEquIntent = this.registerReceiver(this.mEquReceiver, new IntentFilter(PowerampAPI.ACTION_EQU_CHANGED));
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "registerAndLoadStatus mEquIntent=>" + this.mEquIntent);
	}

	private void unregister() {
		if(null != mEquReceiver) {
			try {
                this.unregisterReceiver(this.mEquReceiver);
			} catch(final Exception ignored) {
			}
		}
	}

	private BroadcastReceiver mEquReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
            EqActivity.this.mEquIntent = intent;

			if(EqActivity.LOG) EqActivity.this.debugDumpEquIntent(intent);

            EqActivity.this.updateEqu();
		}

	};

	void updateEqu() {
		if(null == mEquIntent) {
			if(EqActivity.LOG) Log.e(EqActivity.TAG, "updateEqu IGNORE !mEquIntent");
			return;
		}
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "updateEqu", new Exception());

		CheckBox eq = this.findViewById(R.id.eq);
		final boolean equEnabled = this.mEquIntent.getBooleanExtra(PowerampAPI.EXTRA_EQU, false);
		if(eq.isChecked() != equEnabled) {
            this.mSettingEqu = true;
			eq.setChecked(equEnabled);
		}

		CheckBox tone = this.findViewById(R.id.tone);
		final boolean toneEnabled = this.mEquIntent.getBooleanExtra(PowerampAPI.EXTRA_TONE, false);
		if(tone.isChecked() != toneEnabled) {
            this.mSettingTone = true;
			tone.setChecked(toneEnabled);
		}

		final String presetString = this.mEquIntent.getStringExtra(PowerampAPI.EXTRA_VALUE);
		if(null == presetString || 0 == presetString.length()) {
			if(EqActivity.LOG) Log.w(EqActivity.TAG, "updateEqu !presetString");
			return;
		}

		if(!this.mEquBuilt) {
            this.buildEquUI(presetString);
            this.mEquBuilt = true;
		} else {
            this.updateEquUI(presetString);
		}

		final long id = this.mEquIntent.getLongExtra(PowerampAPI.EXTRA_ID, PowerampAPI.NO_ID);
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "updateEqu id=" + id);

		final Spinner presetSpinner = this.findViewById(R.id.preset_spinner);
		final int count = presetSpinner.getAdapter().getCount();
		for(int i = 0; i < count; i++) {
			if(presetSpinner.getAdapter().getItemId(i) == id) {
				if(presetSpinner.getSelectedItemPosition() != i) {
                    this.mSettingPreset = true;
					presetSpinner.setSelection(i);
				}
				break;
			}
		}
	}

	/**
	 * This method parses the equalizer serialized "presetString" and creates appropriate seekbars.
 	 */
	private void buildEquUI(final String string) {
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "buildEquUI string=" + string);
		final String[] pairs = EqActivity.sSemicolonSplitRe.split(string);
		final TableLayout equLayout = this.findViewById(R.id.equ_layout);

		for(final String pair : pairs) {
			final String[] nameValue = EqActivity.sEqualSplitRe.split(pair, 2);
			if(2 == nameValue.length) {
				final String name = nameValue[0];

				try {
					final float value = Float.parseFloat(nameValue[1]);

					final TableRow row = new TableRow(this);

					final TextView label = new TextView(this);
					label.setText(name);
					final TableRow.LayoutParams lp = new TableRow.LayoutParams();
					lp.height = lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
					row.addView(label, lp);

					final SeekBar bar = new SeekBar(this);
					bar.setOnSeekBarChangeListener(this);
					bar.setTag(name);
                    this.setBandValue(name, value, bar);
					row.addView(bar, lp);

					equLayout.addView(row);

				} catch(final NumberFormatException ex) {
					ex.printStackTrace();
					Log.e(EqActivity.TAG, "failed to parse eq value=" + nameValue[1]);
				}
			}
		}
	}

	/**
	 * Preamp, bass/treble and equ bands have different scaling. This method ensures correct scaling is applied.
 	 */
	void setBandValue(final String name, final float value, final SeekBar bar) {
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "setBandValue name=" + name + " value=" + value);
		if("preamp".equals(name)) {
			bar.setMax(200);
			bar.setProgress((int)(value * 100.0f));
		} else if("bass".equals(name) || "treble".equals(name)) {
			bar.setMax(100);
			bar.setProgress((int)(value * 100.0f));
		} else {
			bar.setMax(200);
			bar.setProgress((int)(value * 100.0f + 100.0f));
		}
	}

	/**
	 * Almost the same as buildEquUI, just do the UI update without building it
	 */
	private void updateEquUI(final String string) {
		if(EqActivity.LOG) Log.w(EqActivity.TAG, "updateEquUI string=" + string);
		final String[] pairs = EqActivity.sSemicolonSplitRe.split(string);
		final TableLayout equLayout = this.findViewById(R.id.equ_layout);

		for(int i = 0, pairsLength = pairs.length; i < pairsLength; i++) {
			final String[] nameValue = EqActivity.sEqualSplitRe.split(pairs[i], 2);
			if(2 == nameValue.length) {
				final String name = nameValue[0];
				try {
					final float value = Float.parseFloat(nameValue[1]);

					final SeekBar bar = (SeekBar)((ViewGroup)equLayout.getChildAt(i)).getChildAt(1);
					if(null == bar) {
						Log.w(EqActivity.TAG, "no bar=" + name);
						continue;
					}
                    this.setBandValue(name, value, bar);
				} catch(final NumberFormatException ex) {
					ex.printStackTrace();
					Log.e(EqActivity.TAG, "failed to parse eq value=" + nameValue[1]);
				}
			}
		}
	}

	void debugDumpEquIntent(final Intent intent) {
		if(null != intent) {
			final String presetName = intent.getStringExtra(PowerampAPI.EXTRA_NAME);
			final String presetString = intent.getStringExtra(PowerampAPI.EXTRA_VALUE);
			final long id = this.mEquIntent.getLongExtra(PowerampAPI.EXTRA_ID, PowerampAPI.NO_ID);
			Log.w(EqActivity.TAG, "debugDumpEquIntent presetName=" + presetName + " presetString=" + presetString + " id=" + id);
		} else {
			Log.e(EqActivity.TAG, "debugDumpEquIntent: intent is null");
		}
	}

	@Override
	public void onClick(final View v) {
		if(v.getId() == R.id.commit_eq) {
            this.commitEq();
		}
	}

	/**
	 * Event handler for the checkboxes
	 */
	@Override
	public void onCheckedChanged(final CompoundButton view, final boolean isChecked) {
		Log.w(EqActivity.TAG, "onCheckedChanged=" + view);
		final int id = view.getId();
		if(id == R.id.dynamic) {
            this.findViewById(R.id.commit_eq).setEnabled(!isChecked);
		} else if(id == R.id.eq) {
			if(!this.mSettingEqu) {
				PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_ENABLED)
								.putExtra(PowerampAPI.EXTRA_EQU, isChecked),
						MainActivity.FORCE_API_ACTIVITY
				);
			}
            this.mSettingEqu = false;
		} else if(id == R.id.tone) {
			if(!this.mSettingTone) {
				PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_ENABLED)
								.putExtra(PowerampAPI.EXTRA_TONE, isChecked),
						MainActivity.FORCE_API_ACTIVITY
				);
			}
            this.mSettingTone = false;
		}
	}

	/**
	 * Generates and sends presetString to Poweramp
 	 */
	private void commitEq() {
		final StringBuilder presetString = new StringBuilder();

		final TableLayout equLayout = this.findViewById(R.id.equ_layout);
		final int count = equLayout.getChildCount();
		for(int i = count - 1; 0 <= i; i--) {
			final SeekBar bar = (SeekBar)((ViewGroup)equLayout.getChildAt(i)).getChildAt(1);
			final String name = (String)bar.getTag();
			final float value = this.seekBarToValue(name, bar.getProgress());
			presetString.append(name).append("=").append(value).append(";");
		}

		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
				.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_STRING)
				.putExtra(PowerampAPI.EXTRA_VALUE, presetString.toString()),
				MainActivity.FORCE_API_ACTIVITY
		);
	}

	/**
	 * Applies correct seekBar-to-float scaling
 	 */
	private float seekBarToValue(final String name, final int progress) {
		final float value;
		if("preamp".equals(name) || "bass".equals(name) || "treble".equals(name)) {
			value = progress / 100.0f;
		} else {
			value = (progress - 100) / 100.0f;
		}
		return value;
	}

	/**
	 * Process Eq band change.
	 */
	@Override
	public void onProgressChanged(final SeekBar bar, final int progress, final boolean fromUser) {

		if(((CheckBox) this.findViewById(R.id.dynamic)).isChecked()) {
			final String name = (String)bar.getTag();
			final float value = this.seekBarToValue(name, bar.getProgress());
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
					.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_BAND)
					.putExtra(PowerampAPI.EXTRA_NAME, name)
					.putExtra(PowerampAPI.EXTRA_VALUE, value),
					MainActivity.FORCE_API_ACTIVITY
			);
		}
	}

	@Override
	public void onStartTrackingTouch(final SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(final SeekBar seekBar) {
	}

	/**
	 * Event handler for Presets spinner
 	 */
	@Override
	public void onItemSelected(final AdapterView<?> adapter, final View item, final int pos, final long id) {
		if(!this.mSettingPreset) {
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
					.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SET_EQU_PRESET)
					.putExtra(PowerampAPI.EXTRA_ID, id),
					MainActivity.FORCE_API_ACTIVITY
			);
		} else {
            this.mSettingPreset = false;
		}
	}

	@Override
	public void onNothingSelected(final AdapterView<?> arg0) {
	}
}