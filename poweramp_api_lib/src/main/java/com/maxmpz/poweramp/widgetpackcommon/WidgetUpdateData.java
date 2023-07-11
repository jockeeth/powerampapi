/*
Copyright (C) 2011-2018 Maksim Petrov

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

import android.graphics.Bitmap;
import android.util.Log;
import com.maxmpz.poweramp.player.PowerampAPI;
import org.eclipse.jdt.annotation.Nullable;


/**
 * The data required for widget update
 */
public class WidgetUpdateData {
	private static final String TAG = "WidgetUpdateData";
	private static final boolean LOG = false;

	public int apiVersion;

	public boolean hasTrack;
	public String title;
	public String album; // null for hide_unknown_album + unknown album
	public String artist;
	public boolean supportsCatNav;
	public int posInList;
	public int listSize;
	public int flags;

	public Bitmap albumArtBitmap;
	public long albumArtTimestamp;
	public boolean albumArtResolved;
	/** ATM used for the debugging purposes */
	public @Nullable String albumArtSource;

	public boolean playing;

	public int shuffle = PowerampAPI.ShuffleMode.SHUFFLE_NONE;
	public int repeat = PowerampAPI.RepeatMode.REPEAT_NONE;

	public boolean albumArtNoAnim; // Used by widget configurator

	@Override
	public String toString() {
		return super.toString() + " hasTrack=" + this.hasTrack + " title=" + this.title + " album=" + this.album + " artist=" + this.artist + " supportsCatNav=" + this.supportsCatNav +
					" posInList=" + this.posInList + " listSize=" + this.listSize + " flags=0x" + Integer.toHexString(this.flags) + " albumArtBitmap=" + this.albumArtBitmap +
					" albumArtTimestamp=" + this.albumArtTimestamp + " albumArtSource=" + this.albumArtSource +
			        " playing=" + this.playing + " shuffle=" + this.shuffle + " repeat=" + this.repeat;
	}

	/**
	 * Resets textual track information, but not album art, as album art is generally independent from track info (==can be shared between different tracks).
	 * Same for repeat/shuffle, playing state
	 */
	public void resetTrackData() {
		if(WidgetUpdateData.LOG) Log.w(WidgetUpdateData.TAG, "resetTrackData", new Exception());
        this.hasTrack = false;
        this.title = this.album = this.artist = null;
        this.supportsCatNav = false;
        this.posInList = 0;
        this.listSize = 0;
		// NOTE: not resetting album art, repeat/shuffle, nor playing
	}
}