/*
Copyright (C) 2011-2020 Maksim Petrov

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

package com.maxmpz.poweramp.widgetpackcommon;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPIHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("deprecation")
public abstract class WidgetUpdater {
	private static final String TAG = "WidgetUpdater";
	private static final boolean LOG = false;

	/**
	 * If true, loadDefaultOrPersistantUpdateData call is used to generate widget update data in case of system calls to update widget.
	 * loadDefaultOrPersistantUpdateData should be able to retrieve all the data needed + album art
	 */
	private static final boolean ALWAYS_USE_PERSISTANT_DATA = true;

	/**
	 * NOTE: as of v3 betas, no album art event is sent anymore
	 */
	private static final boolean USE_AA_EVENT = false;
	public static final @NonNull String WIDGETS_PREFS_NAME = "appwidgets";

	private static boolean sUpdatedOnce;

	public static final IntentFilter sTrackFilter = new IntentFilter(PowerampAPI.ACTION_TRACK_CHANGED);
	public static final IntentFilter sAAFilter = WidgetUpdater.USE_AA_EVENT ? new IntentFilter(PowerampAPI.ACTION_AA_CHANGED) : null;
	public static final IntentFilter sStatusFilter = new IntentFilter(PowerampAPI.ACTION_STATUS_CHANGED);
	public static final IntentFilter sModeFilter = new IntentFilter(PowerampAPI.ACTION_PLAYING_MODE_CHANGED);

	private final Context mContext; // NOTE: PS context ATM

	private static @Nullable SharedPreferences sCachedPrefs;

	private final @NonNull PowerManager mPowerManager;

	protected final @NonNull Object mLock = new Object();
	protected final @NonNull List<IWidgetUpdater> mProviders = new ArrayList<>(4);

	/**
	 * Used by PS to push updates, usually all providers added in constructor of the derived class
	 */
    protected WidgetUpdater(final Context context) {
		long start;
		if(WidgetUpdater.LOG) start = System.nanoTime();
		
		final PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
		if(null == powerManager) throw new AssertionError();
        this.mPowerManager = powerManager;

        this.mContext = context; // NOTE: PS context ATM
		
		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "ctor in=" + (System.nanoTime() - start) / 1000);
	} 

	/**
	 * Per-single provider ctor, used for cases when provider is called by system
	 */
    protected WidgetUpdater(final Context context, @NonNull final BaseWidgetProvider prov) {
		this(context);

		synchronized(this.mLock) {
            this.mProviders.add(prov);
		}
	}

	/**
	 * Called during system onUpdate() call which requires remote views for widget due to some system event (boot, etc.)
	 * Called just for given provider 
	 */
	// THREADING: any
	public void updateSafe(@NonNull final BaseWidgetProvider provider, final boolean ignorePowerState, final boolean updateByOs, final int[] appWidgetIds) {
		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "updateSafe th=" + Thread.currentThread() + " provider=" + provider);

		synchronized(this.mLock) {
			if(!ignorePowerState && !this.mPowerManager.isInteractive() && WidgetUpdater.sUpdatedOnce){
				if(WidgetUpdater.LOG) Log.e(WidgetUpdater.TAG, "skipping update, screen is off");
				return;
			}

			final WidgetUpdateData data = this.generateUpdateData(this.mContext);

			if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "========== updateSafe UPDATE data=" + data);

            this.pushUpdateCore(data, appWidgetIds);
		}

		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "update done ");
	}

	private void pushUpdateCore(@NonNull final WidgetUpdateData data, final int[] ids) {
		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "pushUpdateCore ids=" + Arrays.toString(ids) + " data=" + data +  " mProviders.length=" + this.mProviders.size());

		final SharedPreferences prefs = WidgetUpdater.getCachedSharedPreferences(this.mContext);

		for(final IWidgetUpdater prov : this.mProviders) {
			prov.pushUpdate(this.mContext, prefs, ids, false, data); // Media never removed, not changing signature for now
		}

		if(data.hasTrack && !WidgetUpdater.sUpdatedOnce) {
			if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "pushUpdateCore sUpdatedOnce=>true");
            WidgetUpdater.sUpdatedOnce = true;
		}
	}

	/**
	 * Called by ExternalAPI
	 * @return true if update happened, false if power state doesn't allow update now
	 */
	public boolean updateDirectSafe(@NonNull final WidgetUpdateData data, final boolean ignorePowerState, final boolean isScreenOn) {
		synchronized(this.mLock) {
			if(!ignorePowerState && !isScreenOn && WidgetUpdater.sUpdatedOnce){
				if(WidgetUpdater.LOG) Log.e(WidgetUpdater.TAG, "updateDirectSafe skipping update, screen is off");
				return false;
			}

			if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "updateDirectSafe data=" + data + " th=" + Thread.currentThread()); // + " extras=" + intent == null ? null : Arrays.toString(intent.getExtras().keySet().toArray(new String[]{})));

            this.pushUpdateCore(data, null);
		}

		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "update done ");

		return true;
	}

	// NOTE: specifically not synchronized as Context.getSharedPreferences() is thread safe and synchronized, so if we get contested here, we just get same preferences
	// from context 2 times
	// THREADING: any
	@SuppressWarnings("null")
	public static @NonNull SharedPreferences getCachedSharedPreferences(final Context context) {
		SharedPreferences cachedPrefs = WidgetUpdater.sCachedPrefs;
		if(null == cachedPrefs) {
			// NOTE: getting Poweramp shared prefs implementation via explicit app context
			final Context app = context.getApplicationContext();
			cachedPrefs = WidgetUpdater.sCachedPrefs = app.getSharedPreferences(WidgetUpdater.WIDGETS_PREFS_NAME, 0);
		}
		return cachedPrefs;
	}

	/** NOTE: we're using #WIDGTS_PREFS_NAME now for widgets, but we still expose previous name to allow prefs code to migrate */
	public static String getOldSharedPreferencesName(final Context context) {
		return context.getPackageName() + "_appwidgets";
	}


	/**
	 * Called when generateUpdateData is not able to find any sticky intents (e.g. after reboot), so default or previously stored data should be retrieved
	 */
	protected abstract void loadDefaultOrPersistantUpdateData(Context context, @NonNull WidgetUpdateData data);

	/**
	 * Generates WidgetUpdateData from sticky intents
	 */
	// Data should be always the same for any type of widgets as data is reused by other widgets, thus method is final.
	public @NonNull WidgetUpdateData generateUpdateData(final Context context) {
		final WidgetUpdateData data = new WidgetUpdateData();

		if(WidgetUpdater.ALWAYS_USE_PERSISTANT_DATA) {
			// Still check for actual playing status, as persistent data is stored per track change, thus never reflects playing state
			// Do it before loadDefaultOrPersistantUpdateData
            this.getPlayingState(context, data);

            this.loadDefaultOrPersistantUpdateData(context, data);

			return data;
		}

		final Bundle track;

		final Intent trackIntent = context.registerReceiver(null, sTrackFilter);

		if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "generateUpdateData trackIntent=" + trackIntent);


		if(null != trackIntent) {
			track = trackIntent.getParcelableExtra(PowerampAPI.EXTRA_TRACK);

			if(null != track) {
				data.hasTrack = true;
				data.title = track.getString(PowerampAPI.Track.TITLE);
				data.album = track.getString(PowerampAPI.Track.ALBUM);
				data.artist = track.getString(PowerampAPI.Track.ARTIST);
				data.listSize = track.getInt(PowerampAPI.Track.LIST_SIZE);
				data.posInList = track.getInt(PowerampAPI.Track.POS_IN_LIST);
				data.supportsCatNav = track.getBoolean(PowerampAPI.Track.SUPPORTS_CAT_NAV);
				data.flags = track.getInt(PowerampAPI.Track.FLAGS);
				if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "received trackIntent data=" + data);

			} else {
                this.loadDefaultOrPersistantUpdateData(context, data);
				return data;
			}
		} else {
			// No any intent stored, need to get some defaults or previously saved persistent data 
            this.loadDefaultOrPersistantUpdateData(context, data);
			return data;
		}

		// NOTE: as of v3 betas, no album art event is sent anymore
//		if(USE_AA_EVENT) { // == false
//			Intent aaIntent = context.registerReceiver(null, WidgetUpdater.sAAFilter);
//			if(aaIntent != null) {
//				try {
//					data.albumArtBitmap = PowerampAPIHelper.getAlbumArt(context, track, 512, 512);
//					if(LOG) Log.w(TAG, "generateUpdateData got aa=" + data.albumArtBitmap);
//					data.albumArtTimestamp = aaIntent.getLongExtra(PowerampAPI.EXTRA_TIMESTAMP, 0);
//					if(LOG) Log.w(TAG, "received AA TIMESTAMP=" + data.albumArtTimestamp);
//				} catch(OutOfMemoryError oom) {
//					Log.e(TAG, "", oom);
//				}
//			}
//		}

        this.getPlayingState(context, data);

		final Intent modeIntent = context.registerReceiver(null, sModeFilter);
		if(null != modeIntent) {
			data.shuffle = modeIntent.getIntExtra(PowerampAPI.EXTRA_SHUFFLE, PowerampAPI.ShuffleMode.SHUFFLE_NONE);
			data.repeat = modeIntent.getIntExtra(PowerampAPI.EXTRA_REPEAT, PowerampAPI.RepeatMode.REPEAT_NONE);
			if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "repeat=" + data.repeat + " shuffle=" + data.shuffle);
		}
		return data;
	}

	@SuppressWarnings("static-method")
	private void getPlayingState(final Context context, @NonNull final WidgetUpdateData data) {
		final Intent statusIntent = context.registerReceiver(null, sStatusFilter);
		if(null != statusIntent) {

			final boolean paused = statusIntent.getBooleanExtra(PowerampAPI.EXTRA_PAUSED, true);
			data.playing = !paused;

			data.apiVersion = statusIntent.getIntExtra(PowerampAPI.EXTRA_API_VERSION, 0);

			if(WidgetUpdater.LOG) Log.w(WidgetUpdater.TAG, "getPlayingState statusIntent=" + statusIntent + " paused=" + paused + " playing=" + data.playing);
		} else if(WidgetUpdater.LOG)  Log.e(WidgetUpdater.TAG, "getPlayingState statusIntent==null");
	}
}
