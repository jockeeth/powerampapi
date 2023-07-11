/*
Copyright (C) 2011-2023 Maksim Petrov

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

import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;
import com.maxmpz.poweramp.player.RemoteTrackTime;
import com.maxmpz.poweramp.player.TableDefs;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import java.util.Set;


public class MainActivity extends AppCompatActivity implements
		View.OnClickListener,
		View.OnLongClickListener,
		View.OnTouchListener,
		CompoundButton.OnCheckedChangeListener,
		SeekBar.OnSeekBarChangeListener,
		AdapterView.OnItemSelectedListener,
		RemoteTrackTime.TrackTimeListener
{
	private static final String TAG = "MainActivity";
	private static final boolean LOG_VERBOSE = false;

	/** If set to true, we send all our intents to API activity. Use for Poweramp build 862+ */
	static final boolean FORCE_API_ACTIVITY = true;

	private static final char[] NO_TIME = { '-', ':', '-', '-' };
	private static final int SEEK_THROTTLE = 500;

	protected Intent mTrackIntent;
	private Intent mStatusIntent;
	protected Intent mPlayingModeIntent;

	private Bundle mCurrentTrack;

	private RemoteTrackTime mRemoteTrackTime;
	private SeekBar mSongSeekBar;

	private TextView mDuration;
	private TextView mElapsed;
	private boolean mSettingPreset;

	private long mLastSeekSentTime;

	private final StringBuilder mDurationBuffer = new StringBuilder();
	private final StringBuilder mElapsedBuffer = new StringBuilder();
	private @Nullable Uri mLastCreatedPlaylistFilesUri;
	private static boolean sPermissionAsked;
	/** Use getPowerampBuildNumber to get the build number */
	private int mPowerampBuildNumber;
	private boolean mProcessingLongPress;
	private int mLastSentSeekPosition;


	@Override
	public void onCreate(final Bundle savedInstanceState) {
		if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        this.findViewById(R.id.play).setOnClickListener(this);
        this.findViewById(R.id.play).setOnLongClickListener(this);
        this.findViewById(R.id.pause).setOnClickListener(this);

        this.findViewById(R.id.prev).setOnClickListener(this);
        this.findViewById(R.id.prev).setOnLongClickListener(this);
        this.findViewById(R.id.prev).setOnTouchListener(this);

        this.findViewById(R.id.next).setOnClickListener(this);
        this.findViewById(R.id.next).setOnLongClickListener(this);
        this.findViewById(R.id.next).setOnTouchListener(this);

        this.findViewById(R.id.prev_in_cat).setOnClickListener(this);
        this.findViewById(R.id.next_in_cat).setOnClickListener(this);
        this.findViewById(R.id.repeat).setOnClickListener(this);
        this.findViewById(R.id.shuffle).setOnClickListener(this);
        this.findViewById(R.id.repeat_all).setOnClickListener(this);
        this.findViewById(R.id.repeat_off).setOnClickListener(this);
        this.findViewById(R.id.shuffle_all).setOnClickListener(this);
        this.findViewById(R.id.shuffle_off).setOnClickListener(this);
        this.findViewById(R.id.eq).setOnClickListener(this);

        this.mSongSeekBar = this.findViewById(R.id.song_seekbar);
        this.mSongSeekBar.setOnSeekBarChangeListener(this);

        this.mDuration = this.findViewById(R.id.duration);
        this.mElapsed = this.findViewById(R.id.elapsed);

        this.mRemoteTrackTime = new RemoteTrackTime(this);
        this.mRemoteTrackTime.setTrackTimeListener(this);

		//((TextView)findViewById(R.id.play_file_path)).setText(findFirstMP3(Environment.getExternalStorageDirectory())); // This can be slow, disabled
        this.findViewById(R.id.play_file).setOnClickListener(this);

        this.findViewById(R.id.folders).setOnClickListener(this);

        this.findViewById(R.id.play_album).setOnClickListener(this);
        this.findViewById(R.id.play_all_songs).setOnClickListener(this);
        this.findViewById(R.id.play_second_artist_first_album).setOnClickListener(this);

        this.findViewById(R.id.pa_current_list).setOnClickListener(this);
        this.findViewById(R.id.pa_folders).setOnClickListener(this);
        this.findViewById(R.id.pa_all_songs).setOnClickListener(this);
		((SeekBar) this.findViewById(R.id.sleep_timer_seekbar)).setOnSeekBarChangeListener(this);

		// Ask Poweramp for a permission to access its data provider. Needed only if we want to make queries against Poweramp database, e.g. in FilesActivity/FoldersActivity
		// NOTE: this will work only if Poweramp process is alive.
		// This actually should be done once per this app installation, but for the simplicity, we use per-process static field here
		if(!MainActivity.sPermissionAsked) {
			if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "onCreate skin permission");
			final Intent intent = new Intent(PowerampAPI.ACTION_ASK_FOR_DATA_PERMISSION);
			intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this));
			intent.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());
			if(MainActivity.FORCE_API_ACTIVITY) {
				intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
                this.startActivitySafe(intent);
			} else {
                this.sendBroadcast(intent);
			}
            MainActivity.sPermissionAsked = true;
		}

        this.getComponentNames();

		if(0 == PowerampAPIHelper.getPowerampBuild(this)) {
			final var topHint = (TextView) this.findViewById(R.id.top_hint);
			topHint.setText("-Poweramp not installed-");
			topHint.setVisibility(VISIBLE);
		}

		if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "onCreate DONE");
	}

	/**
	 * When screen is rotated, by default Android will reapply all saved values to the controls, calling the event handlers, which generate appropriate intents, thus
	 * on screen rotation some commands could be sent to Poweramp unintentionally.
	 * As this activity always syncs everything with the actual state of Poweramp, the automatic restoring of state is non needed and harmful.
	 * <br><br>
	 * Nevertheless, the actual implementation should probably manipulate per view View.setSaveEnabled() for specific controls, use some Model pattern, or manage
	 * state otherwise, as empty onSaveInstanceState here denies save for everything
	 */
	@Override
	public void onSaveInstanceState(final Bundle outState, final PersistableBundle outPersistentState) {
	}

	/**
	 * @see #onSaveInstanceState
	 */
	@Override
	protected void onRestoreInstanceState(final Bundle savedInstanceState) {
	}

	/**
	 * This method unregister all broadcast receivers on activity pause. This is the correct way of handling things - we're
	 * sure no unnecessary event processing will be done for paused activity, when screen is OFF, etc.
	 * Alternatively, we may do this in onStop/onStart, esp. for latest Android versions and things like split screen
	 */
	@Override
	protected void onPause() {
        this.unregister();
        this.mRemoteTrackTime.unregister();

		super.onPause();
	}

	/**
	 * Register broadcast receivers
 	 */
	@Override
	protected void onResume() {
		super.onResume();

        this.registerAndLoadStatus();
        this.mRemoteTrackTime.registerAndLoadStatus();
	}


	@Override
	protected void onDestroy() {
		Log.w(MainActivity.TAG, "onDestroy");
		try {
            this.unregister();
            this.mRemoteTrackTime.setTrackTimeListener(null);
            this.mRemoteTrackTime.unregister();

            this.mRemoteTrackTime = null;
            this.mTrackReceiver = null;
            this.mStatusReceiver = null;
            this.mPlayingModeReceiver = null;
		} catch(final Exception ex) {
			Log.e(MainActivity.TAG, "", ex);
		}

		super.onDestroy();
	}

	/**
	 * NOTE: it's not necessary to set mStatusIntent/mPlayingModeIntent this way here,
	 * but this approach can be used with a null receiver to get current sticky intent without broadcast receiver.
	 */
	private void registerAndLoadStatus() {
        this.mTrackIntent = this.registerReceiver(this.mTrackReceiver, new IntentFilter(PowerampAPI.ACTION_TRACK_CHANGED));
        this.mStatusIntent = this.registerReceiver(this.mStatusReceiver, new IntentFilter(PowerampAPI.ACTION_STATUS_CHANGED));
        this.mPlayingModeIntent = this.registerReceiver(this.mPlayingModeReceiver, new IntentFilter(PowerampAPI.ACTION_PLAYING_MODE_CHANGED));
        this.registerReceiver(this.mMediaButtonIgnoredReceiver, new IntentFilter(PowerampAPI.ACTION_MEDIA_BUTTON_IGNORED));
	}

	private void unregister() {
		if(null != mTrackIntent) {
			try {
                this.unregisterReceiver(this.mTrackReceiver);
			} catch(final Exception ignored){} // Can throw exception if for some reason broadcast receiver wasn't registered.
		}
		if(null != mStatusReceiver) {
			try {
                this.unregisterReceiver(this.mStatusReceiver);
			} catch(final Exception ignored){}
		}
		if(null != mPlayingModeReceiver) {
			try {
                this.unregisterReceiver(this.mPlayingModeReceiver);
			} catch(final Exception ignored){}
		}
		if(null != mMediaButtonIgnoredReceiver) {
			try {
                this.unregisterReceiver(this.mMediaButtonIgnoredReceiver);
			} catch(final Exception ignored){}
		}
	}

	private BroadcastReceiver mTrackReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
            MainActivity.this.mTrackIntent = intent;
            MainActivity.this.processTrackIntent();
			if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "mTrackReceiver " + intent);
		}
	};

	private final BroadcastReceiver mMediaButtonIgnoredReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
            MainActivity.debugDumpIntent(MainActivity.TAG, "mMediaButtonIgnoredReceiver", intent);
			Toast.makeText(MainActivity.this, intent.getAction() + " " + MainActivity.dumpBundle(intent.getExtras()), Toast.LENGTH_SHORT).show();
		}
	};

	void processTrackIntent() {
        this.mCurrentTrack = null;

		if(null != mTrackIntent) {
            this.mCurrentTrack = this.mTrackIntent.getBundleExtra(PowerampAPI.EXTRA_TRACK);
			if(null != mCurrentTrack) {
				final int duration = this.mCurrentTrack.getInt(PowerampAPI.Track.DURATION);
                this.mRemoteTrackTime.updateTrackDuration(duration); // Let RemoteTrackTime know about the current song duration.
			}

			final int pos = this.mTrackIntent.getIntExtra(PowerampAPI.Track.POSITION, -1); // Poweramp build-700+ sends position along with the track intent
			if(-1 != pos) {
                this.mRemoteTrackTime.updateTrackPosition(pos);
			}

            this.updateTrackUI();

            this.updateAlbumArt(this.mCurrentTrack);
		}
	}

	private BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
		@SuppressWarnings("synthetic-access")
		@Override
		public void onReceive(final Context context, final Intent intent) {
            MainActivity.this.mStatusIntent = intent;

			if(MainActivity.LOG_VERBOSE) MainActivity.debugDumpIntent(MainActivity.TAG, "mStatusReceiver", intent);

            MainActivity.this.updateStatusUI();
		}
	};

	private BroadcastReceiver mPlayingModeReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
            MainActivity.this.mPlayingModeIntent = intent;

			if(MainActivity.LOG_VERBOSE) MainActivity.debugDumpIntent(MainActivity.TAG, "mPlayingModeReceiver", intent);

            MainActivity.this.updatePlayingModeUI();
		}
	};

	// This method updates track related info, album art.
	@SuppressLint("SetTextI18n")
	private void updateTrackUI() {
		Log.w(MainActivity.TAG, "updateTrackUI");

		if(null != mTrackIntent) {
			if(null != mCurrentTrack) {
				((TextView) this.findViewById(R.id.cat)).setText(Integer.toString(this.mCurrentTrack.getInt(PowerampAPI.Track.CAT)));
				((TextView) this.findViewById(R.id.uri)).setText(this.mCurrentTrack.getParcelable(PowerampAPI.Track.CAT_URI).toString());
				((TextView) this.findViewById(R.id.id)).setText(Long.toString(this.mCurrentTrack.getLong(PowerampAPI.Track.ID)));
				((TextView) this.findViewById(R.id.title)).setText(this.mCurrentTrack.getString(PowerampAPI.Track.TITLE));
				((TextView) this.findViewById(R.id.album)).setText(this.mCurrentTrack.getString(PowerampAPI.Track.ALBUM));
				((TextView) this.findViewById(R.id.artist)).setText(this.mCurrentTrack.getString(PowerampAPI.Track.ARTIST));
				((TextView) this.findViewById(R.id.path)).setText(this.mCurrentTrack.getString(PowerampAPI.Track.PATH));

				final StringBuilder info = new StringBuilder();
				info.append("Codec: ").append(this.mCurrentTrack.getString(PowerampAPI.Track.CODEC)).append(" ");
				info.append("Bitrate: ").append(this.mCurrentTrack.getInt(PowerampAPI.Track.BITRATE, -1)).append(" ");
				info.append("Sample Rate: ").append(this.mCurrentTrack.getInt(PowerampAPI.Track.SAMPLE_RATE, -1)).append(" ");
				info.append("Channels: ").append(this.mCurrentTrack.getInt(PowerampAPI.Track.CHANNELS, -1)).append(" ");
				info.append("Duration: ").append(this.mCurrentTrack.getInt(PowerampAPI.Track.DURATION, -1)).append("sec ");

				((TextView) this.findViewById(R.id.info)).setText(info);
				return;
			}
		}
		// Else clean everything.
		((TextView) this.findViewById(R.id.info)).setText("");
		((TextView) this.findViewById(R.id.title)).setText("");
		((TextView) this.findViewById(R.id.album)).setText("");
		((TextView) this.findViewById(R.id.artist)).setText("");
		((TextView) this.findViewById(R.id.path)).setText("");
	}

	void updateStatusUI() {
		Log.w(MainActivity.TAG, "updateStatusUI");
		if(null != mStatusIntent) {
			final boolean paused;

			final int state = this.mStatusIntent.getIntExtra(PowerampAPI.EXTRA_STATE, PowerampAPI.STATE_NO_STATE); // NOTE: not used here, provides STATE_* int

			// Each status update can contain track position update as well
			final int pos = this.mStatusIntent.getIntExtra(PowerampAPI.Track.POSITION, -1);
			if(-1 != pos) {
                this.mRemoteTrackTime.updateTrackPosition(pos);
			}

			switch(state) {
				case PowerampAPI.STATE_PAUSED:
					paused = true;
                    this.startStopRemoteTrackTime(true);
					break;

				case PowerampAPI.STATE_PLAYING:
					paused = false;
                    this.startStopRemoteTrackTime(false);
					break;

				default:
				case PowerampAPI.STATE_NO_STATE:
				case PowerampAPI.STATE_STOPPED:
                    this.mRemoteTrackTime.stopSongProgress();
					paused = true;
					break;
			}
			((Button) this.findViewById(R.id.play)).setText(paused ? ">" : "||");
		}
	}

	/**
	 * Updates shuffle/repeat UI
 	 */
	void updatePlayingModeUI() {
		Log.w(MainActivity.TAG, "updatePlayingModeUI");
		if(null != mPlayingModeIntent) {
			final int shuffle = this.mPlayingModeIntent.getIntExtra(PowerampAPI.EXTRA_SHUFFLE, -1);
			final String shuffleStr;
			switch(shuffle) {
				case PowerampAPI.ShuffleMode.SHUFFLE_ALL:
					shuffleStr = "Shuffle All";
					break;
				case PowerampAPI.ShuffleMode.SHUFFLE_CATS:
					shuffleStr = "Shuffle Categories";
					break;
				case PowerampAPI.ShuffleMode.SHUFFLE_SONGS:
					shuffleStr = "Shuffle Songs";
					break;
				case PowerampAPI.ShuffleMode.SHUFFLE_SONGS_AND_CATS:
					shuffleStr = "Shuffle Songs And Categories";
					break;
				default:
					shuffleStr = "Shuffle OFF";
					break;
			}
			((Button) this.findViewById(R.id.shuffle)).setText(shuffleStr);

			final int repeat = this.mPlayingModeIntent.getIntExtra(PowerampAPI.EXTRA_REPEAT, -1);
			final String repeatStr;
			switch(repeat) {
				case PowerampAPI.RepeatMode.REPEAT_ON:
					repeatStr = "Repeat List";
					break;
				case PowerampAPI.RepeatMode.REPEAT_ADVANCE:
					repeatStr = "Advance List";
					break;
				case PowerampAPI.RepeatMode.REPEAT_SONG:
					repeatStr = "Repeat Song";
					break;
				default:
					repeatStr = "Repeat OFF";
					break;
			}

			((Button) this.findViewById(R.id.repeat)).setText(repeatStr);
		}
	}

	/**
	 * Commands RemoteTrackTime to start or stop showing the song progress
 	 */
	void startStopRemoteTrackTime(final boolean paused) {
		if(!paused) {
            this.mRemoteTrackTime.startSongProgress();
		} else {
            this.mRemoteTrackTime.stopSongProgress();
		}
	}

	@SuppressLint("SetTextI18n") void updateAlbumArt(final Bundle track) {
		Log.w(MainActivity.TAG, "updateAlbumArt");

		final ImageView aaImage = this.findViewById(R.id.album_art);
		final TextView albumArtInfo = this.findViewById(R.id.album_art_info);

		if(null == track) {
			Log.w(MainActivity.TAG, "no track");
			aaImage.setImageBitmap(null);
			albumArtInfo.setText("no AA");
			return;
		}

		final Bitmap b = PowerampAPIHelper.getAlbumArt(this, track, 1024, 1024);
		if(null != b) {
			aaImage.setImageBitmap(b);
			albumArtInfo.setText("scaled w: " + b.getWidth() + " h: " + b.getHeight());
		} else {
			albumArtInfo.setText("no AA");
			aaImage.setImageBitmap(null);
		}
	}


	/**
	 * Process a button press. Demonstrates sending various commands to Poweramp
 	 */
	@Override
	public void onClick(final View v) {
		Log.w(MainActivity.TAG, "onClick v=" + v);
		final int id = v.getId();
		if(id == R.id.play) {
			Log.w(MainActivity.TAG, "play");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.TOGGLE_PLAY_PAUSE),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.pause) {
			Log.w(MainActivity.TAG, "pause");
			// NOTE: since 867. Sending String command instead of int
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, "PAUSE"), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.prev) {
			Log.w(MainActivity.TAG, "prev");
			// NOTE: since 867. Sending lowcase String command instead of int
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, "previous"), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.next) {
			Log.w(MainActivity.TAG, "next");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.NEXT),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.prev_in_cat) {
			Log.w(MainActivity.TAG, "prev_in_cat");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.PREVIOUS_IN_CAT),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.next_in_cat) {
			Log.w(MainActivity.TAG, "next_in_cat");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.NEXT_IN_CAT),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.repeat) {
			Log.w(MainActivity.TAG, "repeat");
			// No toast for this button just for demo.
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.shuffle) {
			Log.w(MainActivity.TAG, "shuffle");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE),
                    MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.repeat_all) {
			Log.w(MainActivity.TAG, "repeat_all");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT)
					.putExtra(PowerampAPI.EXTRA_REPEAT, PowerampAPI.RepeatMode.REPEAT_ON), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.repeat_off) {
			Log.w(MainActivity.TAG, "repeat_off");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.REPEAT)
					.putExtra(PowerampAPI.EXTRA_REPEAT, PowerampAPI.RepeatMode.REPEAT_NONE), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.shuffle_all) {
			Log.w(MainActivity.TAG, "shuffle_all");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE)
					.putExtra(PowerampAPI.EXTRA_SHUFFLE, PowerampAPI.ShuffleMode.SHUFFLE_ALL), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.shuffle_off) {
			Log.w(MainActivity.TAG, "shuffle_all");
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SHUFFLE)
					.putExtra(PowerampAPI.EXTRA_SHUFFLE, PowerampAPI.ShuffleMode.SHUFFLE_NONE), MainActivity.FORCE_API_ACTIVITY);
		} else if(id == R.id.commit_eq) {
			Log.w(MainActivity.TAG, "commit_eq");
            this.commitEq();
		} else if(id == R.id.play_file) {
			Log.w(MainActivity.TAG, "play_file");
			try {
				final String uri = ((TextView) this.findViewById(R.id.play_file_path)).getText().toString();
				if(uri.length() > "content://".length()) {
					PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
							.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY)
							//.putExtra(PowerampAPI.Track.POSITION, 10) // Play from 10th second.
							.setData(Uri.parse(uri)), MainActivity.FORCE_API_ACTIVITY);
				}
			} catch(final Throwable th) {
				Log.e(MainActivity.TAG, "", th);
				Toast.makeText(this, th.getMessage(), Toast.LENGTH_LONG).show();
			}
		} else if(id == R.id.folders) {
            this.startActivitySafe(new Intent(this, FoldersActivity.class));
		} else if(id == R.id.play_album) {
            this.playAlbum();
		} else if(id == R.id.play_all_songs) {
            this.playAllSongs();
		} else if(id == R.id.play_second_artist_first_album) {
            this.playSecondArtistFirstAlbum();
		} else if(id == R.id.eq) {
            this.startActivitySafe(new Intent(this, EqActivity.class));
		} else if(id == R.id.pa_current_list) {
            this.startActivitySafe(new Intent(PowerampAPI.ACTION_SHOW_CURRENT));
		} else if(id == R.id.pa_folders) {
            this.startActivitySafe(new Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("folders").build()));
		} else if(id == R.id.pa_all_songs) {
            this.startActivitySafe(new Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()));
		} else if(id == R.id.create_playlist) {
            this.createPlaylistAndAddToIt();
		} else if(id == R.id.create_playlist_w_streams) {
            this.createPlaylistWStreams();
		} else if(id == R.id.goto_created_playlist) {
            this.gotoCreatedPlaylist();
		} else if(id == R.id.add_to_q_and_goto_q) {
            this.addToQAndGotoQ();
		} else if(id == R.id.queue) {
            this.startActivitySafe(new Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("queue").build()));
		} else if(id == R.id.get_all_prefs) {
            this.getAllPrefs();
		} else if(id == R.id.get_pref) {
            this.getPref();
		}
	}

	private void startActivitySafe(@NonNull final Intent intent) {
		try {
            this.startActivity(intent);
		} catch(final Throwable th) {
			Log.e(MainActivity.TAG, "FAIL intent=" + intent, th);
		}
	}

	public void openNowPlayingTracks(final View view) {
		if(null != mCurrentTrack) {
			final Uri catUri = this.mCurrentTrack.getParcelable(PowerampAPI.Track.CAT_URI);
			if(null != catUri) {
				// NOTE: Poweramp may include query parameters such as shs=[SHUFFLE MODE], etc. into the CAT_URI
				// To avoid shuffled and otherwise modified list, we're clearing query parameters here
				final Uri uri = catUri.buildUpon().clearQuery().build();

				Log.w(MainActivity.TAG, "openNowPlayingTracks catUri=" + catUri + " uri=>" + uri);

                this.startActivitySafe(new Intent(this, TrackListActivity.class).setData(uri));
				return; // Done here
			}
		}
		Toast.makeText(this, "No current category available", Toast.LENGTH_SHORT).show();
	}

	public void seekBackward10s(final View view) {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK)
						.putExtra(PowerampAPI.EXTRA_RELATIVE_POSITION, -10)
						.putExtra(PowerampAPI.EXTRA_LOCK, true) // If EXTRA_LOCK=true, we don't change track by seeking past start/end
						,
                MainActivity.FORCE_API_ACTIVITY);
	}

	public void seekForward10s(final View view) {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK)
						.putExtra(PowerampAPI.EXTRA_RELATIVE_POSITION, 10)
						.putExtra(PowerampAPI.EXTRA_LOCK, true) // If EXTRA_LOCK=true, we don't change track by seeking past start/end
						,
                MainActivity.FORCE_API_ACTIVITY);
	}

	public void exportPrefs(final View view) {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.Settings.ACTION_EXPORT_SETTINGS)
				.putExtra(PowerampAPI.Settings.EXTRA_UI, true)
				, MainActivity.FORCE_API_ACTIVITY);
	}

	public void importPrefs(final View view) {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.Settings.ACTION_IMPORT_SETTINGS)
						.putExtra(PowerampAPI.Settings.EXTRA_UI, true)
				, MainActivity.FORCE_API_ACTIVITY);
	}

	/** Get the specified preference and show its name, type, value */
	@SuppressLint("SetTextI18n") private void getPref() {
		final EditText prefET = this.findViewById(R.id.pref);
		final String prefName = prefET.getText().toString();
		final TextView prefsTV = this.findViewById(R.id.prefs);

		if(0 < prefName.length()) {
			final Bundle bundle = new Bundle();
			bundle.putString(prefName, null);

			Bundle resultPrefs = null;
			try {
				resultPrefs = this.getContentResolver().call(PowerampAPI.ROOT_URI, PowerampAPI.CALL_PREFERENCE, null, bundle);
			} catch(final IllegalArgumentException ex) {
				Log.e(MainActivity.TAG, "FAIL Poweramp not installed", ex);
			}

			if(null != resultPrefs) {

				final Object value = resultPrefs.get(prefName);
				if(null != value) {
					prefsTV.setText(prefName + " (" + value.getClass().getSimpleName() + "): " + value);
					prefsTV.setBackground(null);
				} else {
					prefsTV.setText(prefName + ": <no value>");
					prefsTV.setBackgroundColor(0x55FF0000);
				}
				prefsTV.getParent().requestChildFocus(prefsTV, prefsTV);
			} else {
				prefsTV.setText("Call failed");
				prefsTV.setBackgroundColor(0x55FF0000);
			}
		}
	}

	/** Get all available preferences and dump the resulting bundle */
	private void getAllPrefs() {
		final TextView prefsTV = this.findViewById(R.id.prefs);

		try {
			final Bundle resultPrefs = this.getContentResolver().call(PowerampAPI.ROOT_URI, PowerampAPI.CALL_PREFERENCE, null, null);

			prefsTV.setText(MainActivity.dumpBundle(resultPrefs));
			prefsTV.getParent().requestChildFocus(prefsTV, prefsTV);
		} catch(final IllegalArgumentException ex) {
			Log.e(MainActivity.TAG, "FAIL Poweramp not installed", ex);
		}
	}

	public void setPref(final View view) {
		final EditText pref = this.findViewById(R.id.pref);
		final String name = pref.getText().toString().trim();
		if(TextUtils.isEmpty(name)) {
			pref.setError("Empty");
			return;
		}

		pref.setError(null);

		final EditText prefValue = this.findViewById(R.id.pref_value);
		final String value = prefValue.getText().toString().trim();

		final Bundle request = new Bundle();
		prefValue.setError(null);
		boolean failed = false;
		// Guess the type from the value
		if(TextUtils.isEmpty(value)) {
			request.putString(name, value); // Empty value is possible only for the String
		} else if("true".equals(value)) {
			request.putBoolean(name, true);
		} else if("false".equals(value)) {
			request.putBoolean(name, false);
		} else {
			try {
				final int intValue = Integer.parseInt(value);
				// We are able to parse this as int, though preference can be any type.
				// Real code should decide the type based on existing knowledge of the preference type, which don't have here
				request.putInt(name, intValue);
			} catch(final NumberFormatException ex) {
				try {
					final float intValue = Float.parseFloat(value);
					// We are able to parse this as float, though actual preference can by any type.
					// Real code should decide the type based on existing knowledge of the preference type, which don't have here
					request.putFloat(name, intValue);
				} catch(final NumberFormatException ex2) {
					prefValue.setError("Failed to guess type");
					failed = true;
				}
			}
		}
		if(!failed) {
			final TextView prefsTV = this.findViewById(R.id.prefs);

			// OK, let's call it

			final Bundle resultPrefs = this.getContentResolver().call(PowerampAPI.ROOT_URI, PowerampAPI.CALL_SET_PREFERENCE, null, request);

			prefsTV.setText(MainActivity.dumpBundle(resultPrefs));
			prefsTV.getParent().requestChildFocus(prefsTV, prefsTV);
		}
	}

	/**
	 * Process some long presses
 	 */
	@Override
	public boolean onLongClick(final View v) {
		final int id = v.getId();
		if(id == R.id.play) {
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.STOP),
                    MainActivity.FORCE_API_ACTIVITY);
			return true;
		} else if(id == R.id.next) {
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.BEGIN_FAST_FORWARD),
                    MainActivity.FORCE_API_ACTIVITY);
            this.mProcessingLongPress = true;
			return true;
		} else if(id == R.id.prev) {
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.BEGIN_REWIND),
                    MainActivity.FORCE_API_ACTIVITY);
            this.mProcessingLongPress = true;
			return true;
		}

		return false;
	}

	/**
	 * Process touch up event to stop ff/rw
 	 */
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(final View v, final MotionEvent event) {
		switch(event.getActionMasked()) {
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				final int id = v.getId();
				if(id == R.id.next) {
					Log.e(MainActivity.TAG, "onTouch next ACTION_UP");
					if(this.mProcessingLongPress) {
						PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND,
								PowerampAPI.Commands.END_FAST_FORWARD), MainActivity.FORCE_API_ACTIVITY);
                        this.mProcessingLongPress = false;
					}
					return false;
				} else if(id == R.id.prev) {
					if(this.mProcessingLongPress) {
						PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.EXTRA_COMMAND,
								PowerampAPI.Commands.END_REWIND), MainActivity.FORCE_API_ACTIVITY);
                        this.mProcessingLongPress = false;
					}
					return false;
				}
			}
		}

		return false;
	}

	/**
	 * Just play all library songs (starting from the first)
 	 */
	private void playAllSongs() {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
				.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY)
				.setData(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build()),
                MainActivity.FORCE_API_ACTIVITY);
	}

	/**
	 * Get first album id and play it
	 */
	private void playAlbum() {
		final Cursor c = this.getContentResolver().query(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("albums").build(), new String[]{ "albums._id", "album" }, null, null, "album");
		if(null != c) {
			if(c.moveToNext()) {
				final long albumId = c.getLong(0);
				final String name = c.getString(1);
				Toast.makeText(this, "Playing album: " + name, Toast.LENGTH_SHORT).show();

				PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY)
						.setData(PowerampAPI.ROOT_URI.buildUpon()
								.appendEncodedPath("albums")
								.appendEncodedPath(Long.toString(albumId))
								.appendEncodedPath("files")
								.build()),
                        MainActivity.FORCE_API_ACTIVITY);
			}
			c.close();
		}
	}

	/**
	 * Play first available album from the first available artist in ARTIST_ALBUMs
 	 */
	private void playSecondArtistFirstAlbum() {
		// Get first artist.
		final Cursor c = this.getContentResolver().query(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("artists").build(),
				new String[]{ "artists._id", "artist" },
				null, null, "artist_sort COLLATE NOCASE");
		if(null != c) {
			c.moveToNext(); // First artist.
			if(c.moveToNext()) { // Second artist.
				final long artistId = c.getLong(0);
				final String artist = c.getString(1);
				final Cursor c2 = this.getContentResolver().query(PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("artists_albums").build(),
						new String[] { "albums._id", "album" },
						"artists._id=?", new String[]{ Long.toString(artistId) }, "album_sort COLLATE NOCASE");
				if(null != c2) {
					if(c2.moveToNext()) {
						final long albumId = c2.getLong(0);
						final String album = c2.getString(1);

						Toast.makeText(this, "Playing artist: " + artist + " album: " + album, Toast.LENGTH_SHORT).show();

						PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.OPEN_TO_PLAY)
								.setData(PowerampAPI.ROOT_URI.buildUpon()
										.appendEncodedPath("artists")
										.appendEncodedPath(Long.toString(artistId))
										.appendEncodedPath("albums")
										.appendEncodedPath(Long.toString(albumId))
										.appendEncodedPath("files")
										.build()
								), MainActivity.FORCE_API_ACTIVITY);
					}
					c2.close();
				}

			}
			c.close();
		}
	}


	/**
	 * Event handler for Dynamic Eq checkbox
 	 */
	@Override
	public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
        this.findViewById(R.id.commit_eq).setEnabled(!isChecked);
	}

	/**
	 * Generates and sends presetString to Poweramp Eq
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
                MainActivity.FORCE_API_ACTIVITY);
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
	 * Event handler for both song progress seekbar and equalizer bands
 	 */
	@Override
	public void onProgressChanged(final SeekBar bar, final int progress, final boolean fromUser) {
		final int id = bar.getId();
		if(id == R.id.song_seekbar) {
			if(fromUser) {
                this.sendSeek(false);
			}
		} else if(id == R.id.sleep_timer_seekbar) {
            this.updateSleepTimer(progress);
		}
	}

	@Override
	public void onStartTrackingTouch(final SeekBar seekBar) {
	}

	/**
	 * Force seek when user ends seeking
	 */
	@Override
	public void onStopTrackingTouch(final SeekBar seekBar) {
        this.sendSeek(true);

	}

	/**
	 * Send a seek command
 	 */
	private void sendSeek(final boolean ignoreThrottling) {

		final int position = this.mSongSeekBar.getProgress();
        this.mRemoteTrackTime.updateTrackPosition(position);

		// Apply some throttling to avoid too many intents to be generated.
		if((0 == mLastSeekSentTime || SEEK_THROTTLE < System.currentTimeMillis() - mLastSeekSentTime)
			|| ignoreThrottling && this.mLastSentSeekPosition != position // Do not send same position for cases like quick seekbar touch
		) {
            this.mLastSeekSentTime = System.currentTimeMillis();
			PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
					.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SEEK)
					.putExtra(PowerampAPI.Track.POSITION, position),
                    MainActivity.FORCE_API_ACTIVITY);
            this.mLastSentSeekPosition = position;
			Log.w(MainActivity.TAG, "sendSeek sent position=" + position);
		} else {
			Log.w(MainActivity.TAG, "sendSeek throttled");
		}
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
                    MainActivity.FORCE_API_ACTIVITY);
		} else {
            this.mSettingPreset = false;
		}
	}

	@Override
	public void onNothingSelected(final AdapterView<?> arg0) {
	}

	/**
	 * Callback from RemoteTrackTime. Updates durations (both seekbar max value and duration label)
 	 */
	@Override
	public void onTrackDurationChanged(final int duration) {
        this.mDurationBuffer.setLength(0);

        MainActivity.formatTimeS(this.mDurationBuffer, duration, true);

        this.mDuration.setText(this.mDurationBuffer);

        this.mSongSeekBar.setMax(duration);
	}

	/**
	 * Callback from RemoteTrackTime. Updates the current song progress. Ensures extra event is not processed (mUpdatingSongSeekBar).
 	 */
	@Override
	public void onTrackPositionChanged(final int position) {
        this.mElapsedBuffer.setLength(0);

        MainActivity.formatTimeS(this.mElapsedBuffer, position, false);

        this.mElapsed.setText(this.mElapsedBuffer);

		if(this.mSongSeekBar.isPressed()) {
			return;
		}

        this.mSongSeekBar.setProgress(position);
	}


	public void setSleepTimer(final View view) {
		PowerampAPIHelper.sendPAIntent(this, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.SLEEP_TIMER)
						.putExtra(PowerampAPI.EXTRA_SECONDS, ((SeekBar) this.findViewById(R.id.sleep_timer_seekbar)).getProgress())
						.putExtra(PowerampAPI.EXTRA_PLAY_TO_END, ((CheckBox) this.findViewById(R.id.sleep_timer_play_to_end)).isChecked()),
                MainActivity.FORCE_API_ACTIVITY);
	}

	public void rescan(final View view) {
		final var componentName = PowerampAPIHelper.getScannerServiceComponentName(this);
		if(null == componentName) {
			Log.e(MainActivity.TAG, "FAIL !componentName");
			return;
		}
		final Intent intent = new Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS)
				.setComponent(componentName)
				.putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, this.getPackageName() + " user requested");
        this.startService(intent);
	}

	public void milkRescan(final View view) {
		int powerampBuild = PowerampAPIHelper.getPowerampBuild(this);
		if(0 >= powerampBuild) {
			Log.e(MainActivity.TAG, "FAIL !powerampBuild");
			return;
		}

		final Intent intent = new Intent(PowerampAPI.MilkScanner.ACTION_SCAN)
				.putExtra(PowerampAPI.MilkScanner.EXTRA_CAUSE, this.getPackageName() + " user requested");

		if(868 <= powerampBuild) {

			PowerampAPIHelper.sendPAIntent(this, intent, MainActivity.FORCE_API_ACTIVITY); // Since 868

		} else {
			ComponentName milkScannerServiceComponentName = PowerampAPIHelper.getMilkScannerServiceComponentName(this);
			intent.setComponent(milkScannerServiceComponentName); // Used prior build 868
            this.startService(intent);
		}
	}


	// =================================================

	@SuppressLint("SetTextI18n") private void updateSleepTimer(final int progress) {
		((TextView) this.findViewById(R.id.sleep_timer_value)).setText("Seep in " + progress + "s");
	}


	/** Retrieves Poweramp build number and normalizes it to ### form, e.g. 846002 => 846 */
	private int getPowerampBuildNumber() {
		int code = this.mPowerampBuildNumber;
		if(0 == code) {
			try {
				code = this.getPackageManager().getPackageInfo(PowerampAPIHelper.getPowerampPackageName(this), 0).versionCode;
			} catch(final PackageManager.NameNotFoundException ex) {
				// code==0 here
				Log.e(MainActivity.TAG, "", ex);
			}
			if(1000 < code) {
				code = code / 1000;
			}
            this.mPowerampBuildNumber = code;
		}
		return code;
	}


	/**
	 * NOTE: real code should run on some worker thread
 	 */
	private void createPlaylistAndAddToIt() {
		final int buildNumber = this.getPowerampBuildNumber();
		if(0 == buildNumber) {
			Log.e(MainActivity.TAG, "createPlaylistAndAddToIt FAIL !buildNumber");
			return;
		}

		final ContentResolver cr = this.getContentResolver();
		final Uri playlistsUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("playlists").build();

		// NOTE: we need raw column names for an insert query (without table name), thus using getRawColName()

		final ContentValues values = new ContentValues();
		values.put(MainActivity.getRawColName(TableDefs.Playlists.PLAYLIST), "Sample Playlist " + System.currentTimeMillis());
		final Uri playlistInsertedUri = cr.insert(playlistsUri, values);

		if(null != playlistInsertedUri) {
			Log.w(MainActivity.TAG, "createPlaylistAndAddToIt inserted=" + playlistInsertedUri);

			// NOTE: we are inserting into /playlists/#/files, playlistInsertedUri (/playlists/#) is not valid for entries insertion
			final Uri playlistEntriesUri = playlistInsertedUri.buildUpon().appendEncodedPath("files").build();

            this.mLastCreatedPlaylistFilesUri = playlistEntriesUri;

			// Select up to 10 random files
			final int numFilesToInsert = 10;
			final Uri filesUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build();
			final Cursor c = this.getContentResolver().query(filesUri, new String[]{ TableDefs.Files._ID, TableDefs.Files.NAME, TableDefs.Folders.PATH }, null, null,
					"RANDOM() LIMIT " + numFilesToInsert);

			int sort = 0;

			if(null != c) {
				while(c.moveToNext()) {
					final long fileId = c.getLong(0);
					final String fileName = c.getString(1);
					final String folderPath = c.getString(2);

					values.clear();
					values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.FOLDER_FILE_ID), fileId);

					// Playlist behavior changed in Poweramp build 842 - now each playlist entry should contain full path
					// This restriction was uplifted in build 846, but anyway, it's preferable to fill playlist entry folder_path and file_name columns to allow
					// easy resolution of playlist entries in case user changes music folders, storage, etc.
					if(842 <= buildNumber) {
						values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.FOLDER_PATH), folderPath);
						values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.FILE_NAME), fileName);
					}

					// Playlist entries are always sorted by "sort" fields, so if we want them to be in order, we should provide it.
					// If we're adding entries to existing playlist, it's a good idea to get MAX(sort) first from the given playlist

					values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.SORT), sort);

					final Uri entryUri = cr.insert(playlistEntriesUri, values);
					if(null != entryUri) {
						Log.w(MainActivity.TAG, "createPlaylistAndAddToIt inserted entry fileId=" + fileId + " sort=" + sort + " folderPath=" + folderPath + " fileName=" + fileName +
								" entryUri=" + entryUri);
						sort++;
					} else {
						Log.e(MainActivity.TAG, "createPlaylistAndAddToIt FAILED to insert entry fileId=" + fileId);
					}
				}

				c.close();

				Toast.makeText(this, "Inserted files=" + sort, Toast.LENGTH_SHORT).show();
			}

			if(0 < sort) {
				// Force Poweramp to reload data in UI / PlayerService as we changed something
				final Intent intent = new Intent(PowerampAPI.ACTION_RELOAD_DATA);
				intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this));
				intent.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());
				// NOTE: important to send the changed table for an adequate UI / PlayerService reloading
				intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.PlaylistEntries.TABLE);
				if(MainActivity.FORCE_API_ACTIVITY) {
					intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
                    this.startActivitySafe(intent);
				} else {
                    this.sendBroadcast(intent);
				}
			}

			// Make open playlist button active
            this.findViewById(R.id.goto_created_playlist).setEnabled(true);

		} else {
			Log.e(MainActivity.TAG, "createPlaylistAndAddToIt FAILED");
		}
	}

	/**
	 * Demonstrates a playlist with the http stream entries<br>
	 * NOTE: real code should run on some worker thread
	 */
	private void createPlaylistWStreams() {
		final int buildNumber = this.getPowerampBuildNumber();
		// We need at least 842 build
		if(842 > buildNumber) {
			Toast.makeText(this, "Poweramp build is too old", Toast.LENGTH_SHORT).show();
			return;
		}

		final ContentResolver cr = this.getContentResolver();
		final Uri playlistsUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("playlists").build();

		// NOTE: we need raw column names for an insert query (without table name), thus using getRawColName()
		// NOTE: playlist with a stream doesn't differ from other (track based) playlists. Only playlist entries differ vs usual file tracks

		final String playlistName = "Stream Playlist " + System.currentTimeMillis();
		final ContentValues values = new ContentValues();
		values.put(MainActivity.getRawColName(TableDefs.Playlists.PLAYLIST), playlistName);
		final Uri playlistInsertedUri = cr.insert(playlistsUri, values);

		if(null == playlistInsertedUri) {
			Toast.makeText(this, "Failed to create playlist", Toast.LENGTH_SHORT).show();
			return;
		}

		Log.w(MainActivity.TAG, "createPlaylistAndAddToIt inserted=" + playlistInsertedUri);

		// NOTE: we are inserting into /playlists/#/files, playlistInsertedUri (/playlists/#) is not valid for the entries insertion
		final Uri playlistEntriesUri = playlistInsertedUri.buildUpon().appendEncodedPath("files").build();
        this.mLastCreatedPlaylistFilesUri = playlistEntriesUri;

		// To create stream entry, we just provide the url. Entry is added as the last one
		values.clear();
		values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.FILE_NAME), "http://64.71.77.150:8000/stream");
		final Uri entryUri1 = cr.insert(playlistEntriesUri, values);

		values.clear();
		values.put(MainActivity.getRawColName(TableDefs.PlaylistEntries.FILE_NAME), "http://94.23.205.82:5726/;stream/1");
		final Uri entryUri2 = cr.insert(playlistEntriesUri, values);

		if(null != entryUri1 && null != entryUri2) {
			Toast.makeText(this, "Inserted streams OK, playlist=" + playlistName, Toast.LENGTH_SHORT).show();

			// Force Poweramp to reload data in UI / PlayerService as we changed something
			final Intent intent = new Intent(PowerampAPI.ACTION_RELOAD_DATA);
			intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this));
			intent.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());
			// NOTE: important to send the changed table for an adequate UI / PlayerService reloading
			intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.PlaylistEntries.TABLE);
			if(MainActivity.FORCE_API_ACTIVITY) {
				intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
                this.startActivitySafe(intent);
			} else {
                this.sendBroadcast(intent);
			}
		}

		// Make open playlist button active
        this.findViewById(R.id.goto_created_playlist).setEnabled(true);
	}

	private void gotoCreatedPlaylist() {
		if(null != mLastCreatedPlaylistFilesUri) {
            this.startActivitySafe(new Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(this.mLastCreatedPlaylistFilesUri));
		}
	}

	private void addToQAndGotoQ() {
		final ContentResolver cr = this.getContentResolver();
		final Uri queueUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("queue").build();
		final ContentValues values = new ContentValues();

		// Get max sort from queue
		int maxSort = 0;
		Cursor c = this.getContentResolver().query(queueUri, new String[]{ "MAX(" + TableDefs.Queue.SORT + ")" }, null, null, null);
		if(null != c) {
			if(c.moveToFirst()) {
				maxSort = c.getInt(0);
			}
			c.close();
		}

		// Select up to 10 random files
		final int numFilesToInsert = 10;
		final Uri filesUri = PowerampAPI.ROOT_URI.buildUpon().appendEncodedPath("files").build();
		c = this.getContentResolver().query(filesUri, new String[]{ TableDefs.Files._ID, TableDefs.Files.NAME }, null, null, "RANDOM() LIMIT " + numFilesToInsert);

		int inserted = 0;

		if(null != c) {
			int sort = maxSort + 1; // Start from maxSort + 1
			while(c.moveToNext()) {
				final long fileId = c.getLong(0);
				final String name = c.getString(1);

				values.clear();
				values.put(MainActivity.getRawColName(TableDefs.Queue.FOLDER_FILE_ID), fileId);
				values.put(MainActivity.getRawColName(TableDefs.Queue.SORT), sort);

				final Uri entryUri = cr.insert(queueUri, values);
				if(null != entryUri) {
					Log.w(MainActivity.TAG, "addToQAndGotoQ inserted entry fileId=" + fileId + " sort=" + sort + " name=" + name + " entryUri=" + entryUri);
					sort++;
					inserted++;
				} else {
					Log.e(MainActivity.TAG, "addToQAndGotoQ FAILED to insert entry fileId=" + fileId);
				}
			}

			c.close();

			Toast.makeText(this, "Inserted files=" + sort, Toast.LENGTH_SHORT).show();
		}

		if(0 < inserted) {
			// Force Poweramp to reload data in UI / PlayerService as we changed something
			final Intent intent = new Intent(PowerampAPI.ACTION_RELOAD_DATA);
			intent.setPackage(PowerampAPIHelper.getPowerampPackageName(this));
			intent.putExtra(PowerampAPI.EXTRA_PACKAGE, this.getPackageName());
			// NOTE: important to send changed table for the adequate UI / PlayerService reloading. This can also make Poweramp to go to Queue
			intent.putExtra(PowerampAPI.EXTRA_TABLE, TableDefs.Queue.TABLE);
			if(MainActivity.FORCE_API_ACTIVITY) {
				intent.setComponent(PowerampAPIHelper.getApiActivityComponentName(this));
                this.startActivitySafe(intent);
			} else {
                this.sendBroadcast(intent);
			}

            this.startActivitySafe(new Intent(PowerampAPI.ACTION_OPEN_LIBRARY).setData(queueUri));
		}
	}


	private void getComponentNames() {
		if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "getComponentNames");
		final TextView tv = this.findViewById(R.id.component_names);
		final SpannableStringBuilder sb = new SpannableStringBuilder();
        MainActivity.appendWithSpan(sb, "Component Names\n", new StyleSpan(Typeface.BOLD));
        MainActivity.appendWithSpan(sb, "Package: ", new StyleSpan(Typeface.BOLD))
				.append(this.orEmpty(PowerampAPIHelper.getPowerampPackageName(this)))
				.append("\n");
        MainActivity.appendWithSpan(sb, "PlayerService: ", new StyleSpan(Typeface.BOLD))
				.append(this.toStringOrEmpty(PowerampAPIHelper.getPlayerServiceComponentName(this)))
				.append("\n");
        MainActivity.appendWithSpan(sb, "MediaBrowserService: ", new StyleSpan(Typeface.BOLD))
				.append(this.toStringOrEmpty(PowerampAPIHelper.getBrowserServiceComponentName(this)))
				.append("\n");
        MainActivity.appendWithSpan(sb, "API Receiver: ", new StyleSpan(Typeface.BOLD))
				.append(this.toStringOrEmpty(PowerampAPIHelper.getApiReceiverComponentName(this)))
				.append("\n");
        MainActivity.appendWithSpan(sb, "Scanner: ", new StyleSpan(Typeface.BOLD))
				.append(this.toStringOrEmpty(PowerampAPIHelper.getScannerServiceComponentName(this)))
				.append("\n");
        MainActivity.appendWithSpan(sb, "Milk Scanner: ", new StyleSpan(Typeface.BOLD))
				.append(this.toStringOrEmpty(PowerampAPIHelper.getMilkScannerServiceComponentName(this)))
				.append("\n");
		tv.setText(sb);
		if(MainActivity.LOG_VERBOSE) Log.w(MainActivity.TAG, "getComponentNames DONE");
	}

	private @NonNull String orEmpty(@Nullable final String s) {
		if(null == s) return "";
		return s;
	}

	private @NonNull String toStringOrEmpty(@Nullable final ComponentName name) {
		return null != name ? name.toString() : "";
	}

	public static @NonNull String getRawColName(@NonNull final String col) {
		final int dot = col.indexOf('.');
		if(0 <= dot && dot + 1 <= col.length()) {
			return col.substring(dot + 1);
		}
		return col;
	}

	public static void formatTimeS(@NonNull final StringBuilder sb, final int secs, final boolean showPlaceholderForZero) {
		if(0 > secs || 0 == secs && showPlaceholderForZero) {
			sb.append(MainActivity.NO_TIME);
			return;
		}

		final int seconds = secs % 60;

		if(3600 > secs) { // min:sec
			final int minutes = secs / 60;
			sb.append(minutes).append(':');
		} else { // hour:min:sec
			final int hours = secs / 3600;
			final int minutes = (secs / 60) % 60;

			sb.append(hours).append(':');
			if(10 > minutes) {
				sb.append('0');
			}
			sb.append(minutes).append(':');
		}
		if(10 > seconds) {
			sb.append('0');
		}
		sb.append(seconds);
	}

	public static void debugDumpIntent(@NonNull final String tag, @NonNull final String description, @Nullable final Intent intent) {
		if(null != intent) {
			Log.w(tag, description + " debugDumpIntent action=" + intent.getAction() + " extras=" + MainActivity.dumpBundle(intent.getExtras()));
			final Bundle track = intent.getBundleExtra(PowerampAPI.EXTRA_TRACK);
			if(null != track) {
				Log.w(tag, "track=" + MainActivity.dumpBundle(track));
			}
		} else {
			Log.e(tag, description + " debugDumpIntent intent is null");
		}
	}

	@SuppressWarnings("null")
	public static @NonNull String dumpBundle(@Nullable final Bundle bundle) {
		if(null == bundle) {
			return "null bundle";
		}
		final StringBuilder sb = new StringBuilder();
		final Set<String> keys = bundle.keySet();
		sb.append("\n");
		for(final String key : keys) {
			sb.append('\t').append(key).append("=");
			final Object val = bundle.get(key);
			sb.append(val);
			if(null != val) {
				sb.append(" ").append(val.getClass().getSimpleName());
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	private static @NonNull SpannableStringBuilder appendWithSpan(@NonNull final SpannableStringBuilder sb, @Nullable final CharSequence str, @NonNull final Object span) {
		final int start = sb.length();
		sb.append(null != str ? str : "");
		sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return sb;
	}
}