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

package com.maxmpz.poweramp.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import org.eclipse.jdt.annotation.NonNull;


/**
 * This class tracks Poweramp in-song position via as few sync intents as possible.
 * Syncing happens when:
 * - your app calls registerAndLoadStatus() (for example, in activity onResume).
 * - when Poweramp seeks the track (throttled to 500ms)
 * - when track is started/resumed/paused
 */
public class RemoteTrackTime {
	private static final String TAG = "RemoteTrackTime";
	private static final boolean LOG = false; // Make it false for production.

	private static final int UPDATE_DELAY = 1000;

	private final Context mContext;
	int mPosition;

	long mStartTimeMs;
	int mStartPosition;
	private boolean mPlaying;

	final Handler mHandler = new Handler();


	public interface TrackTimeListener {
		void onTrackDurationChanged(int duration);
		void onTrackPositionChanged(int position);
	}

	TrackTimeListener mTrackTimeListener;


	public RemoteTrackTime(final Context context) {
        this.mContext = context;
	}

	public void registerAndLoadStatus() {
		final IntentFilter filter = new IntentFilter(PowerampAPI.ACTION_TRACK_POS_SYNC);
        this.mContext.registerReceiver(this.mTrackPosSyncReceiver, filter);

		PowerampAPIHelper.sendPAIntent(this.mContext, new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.POS_SYNC));

		if(this.mPlaying) {
            this.mHandler.removeCallbacks(this.mTickRunnable);
            this.mHandler.postDelayed(this.mTickRunnable, 0);
		}
	}

	public void unregister() {
		try {
            this.mContext.unregisterReceiver(this.mTrackPosSyncReceiver);
		} catch(final Exception ignored) { }
        this.mHandler.removeCallbacks(this.mTickRunnable);
	}

	private final @NonNull BroadcastReceiver mTrackPosSyncReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int pos = intent.getIntExtra(PowerampAPI.Track.POSITION, 0);
			if(RemoteTrackTime.LOG) Log.w(RemoteTrackTime.TAG, "mTrackPosSyncReceiver sync=" + pos);
            RemoteTrackTime.this.updateTrackPosition(pos);
		}

	};

	public void setTrackTimeListener(final TrackTimeListener l) {
        this.mTrackTimeListener = l;
	}

	public void updateTrackDuration(final int duration) {
		if(null != mTrackTimeListener) {
            this.mTrackTimeListener.onTrackDurationChanged(duration);
		}
	}

	public void updateTrackPosition(final int position) {
        this.mPosition = position;
		if(RemoteTrackTime.LOG) Log.w(RemoteTrackTime.TAG, "updateTrackPosition mPosition=>" + this.mPosition);
		if(this.mPlaying) {
            this.mStartTimeMs = System.currentTimeMillis();
            this.mStartPosition = this.mPosition;
		}
		if(null != mTrackTimeListener) {
            this.mTrackTimeListener.onTrackPositionChanged(position);
		}
	}

	protected final Runnable mTickRunnable = new Runnable() {
		@Override
		public void run() {
            RemoteTrackTime.this.mPosition = (int)(System.currentTimeMillis() - RemoteTrackTime.this.mStartTimeMs + 500) / 1000 + RemoteTrackTime.this.mStartPosition;
			if(RemoteTrackTime.LOG) Log.w(RemoteTrackTime.TAG, "mTickRunnable mPosition=" + RemoteTrackTime.this.mPosition);
			if(null != mTrackTimeListener) {
                RemoteTrackTime.this.mTrackTimeListener.onTrackPositionChanged(RemoteTrackTime.this.mPosition);
			}
            RemoteTrackTime.this.mHandler.removeCallbacks(RemoteTrackTime.this.mTickRunnable);
            RemoteTrackTime.this.mHandler.postDelayed(RemoteTrackTime.this.mTickRunnable, RemoteTrackTime.UPDATE_DELAY);
		}
	};

	public void startSongProgress() {
		if(!this.mPlaying) {
            this.mStartTimeMs = System.currentTimeMillis();
            this.mStartPosition = this.mPosition;
            this.mHandler.removeCallbacks(this.mTickRunnable);
            this.mHandler.postDelayed(this.mTickRunnable, RemoteTrackTime.UPDATE_DELAY);
            this.mPlaying = true;
		}
	}

	public void stopSongProgress() {
		if(this.mPlaying) {
            this.mHandler.removeCallbacks(this.mTickRunnable);
            this.mPlaying = false;
		}
	}
}
