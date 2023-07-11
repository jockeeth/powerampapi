package com.maxmpz.poweramp.widgetpackcommon;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;
import android.widget.RemoteViews;
import com.maxmpz.poweramp.player.PowerampAPI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import java.util.Arrays;

/**
 * Base widget provider for PowerampAPI based app widgets
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider implements
		IWidgetUpdater
{
	private static final String TAG = "BaseWidgetProvider";
	private static final boolean LOG = false;

	public static final int API_VERSION_200 = 200;

	public static class WidgetContext {
		public long lastAATimeStamp;
		public int id;
	}

	public enum ShuffleModeV140 {
        ;
        public static final int SHUFFLE_NONE = 0;
		public static final int SHUFFLE_ALL = 1;
		public static final int SHUFFLE_IN_CAT = 2;
		public static final int SHUFFLE_HIER = 3;
	}

	public enum RepeatModeV140 {
        ;
        public static final int REPEAT_NONE = 0;
		public static final int REPEAT_ALL = 1;
		public static final int REPEAT_SONG = 2;
		public static final int REPEAT_CAT = 3;
	}

	/**
	 * Min. AA image size to show (otherwise logo shown)
	 */
	protected static final int MIN_SIZE = 1; // NOTE: no reason not to show AA if it's handed by PA
	
	private @Nullable ComponentName mComponentName; // This provider component name
	private @Nullable AppWidgetManager mAppWidgetManager;
	
	/**
	 * Creates and caches widgetupdater suitable for updating this provider. Called when provider is called by system or by widget configure. Implmentation should be thread safe
	 */
	// REVISIT: threading - actually always called on gui thread 
	protected abstract @NonNull WidgetUpdater getWidgetUpdater(Context context);

	/**
	 * THREADING: any
	 */
	public abstract @NonNull RemoteViews update(Context context, @NonNull WidgetUpdateData data, @NonNull SharedPreferences prefs, int id);


	// NOTE: called by system
	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		if(0 == appWidgetIds.length) {
			if(BaseWidgetProvider.LOG) Log.e(BaseWidgetProvider.TAG, "no widget ids");
			return;
		}

		if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "onUpdate ids=" + Arrays.toString(appWidgetIds));

		final WidgetUpdater widgetUpdater = this.getWidgetUpdater(context);

		try {

			widgetUpdater.updateSafe(this, true, true, appWidgetIds); // Immediate update, ignores power state

		} catch(final Throwable th) {
			Log.e(BaseWidgetProvider.TAG, "", th);
		}
	}



	// THREADING: any
	@Override
	public @Nullable WidgetUpdateData pushUpdate(final Context context, @NonNull final SharedPreferences prefs, int @Nullable[] ids,
                                                 final boolean mediaRemoved, @NonNull final WidgetUpdateData data
	) {
		AppWidgetManager appWidgetManager = this.mAppWidgetManager;
		if(null == appWidgetManager) {
			appWidgetManager = this.mAppWidgetManager = AppWidgetManager.getInstance(context);
		}
		
		if(null == ids) {
			try { // java.lang.RuntimeException: system server dead?  at android.appwidget.AppWidgetManager.getAppWidgetIds(AppWidgetManager.java:492) at com.maxmpz.audioplayer.widgetpackcommon.BaseWidgetProvider (":139)
				long start;
				if(BaseWidgetProvider.LOG) start = System.nanoTime();
				
				if(null == mComponentName) {
                    this.mComponentName = new ComponentName(context, getClass());
				}
				ids = appWidgetManager.getAppWidgetIds(this.mComponentName);

				if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "pushUpdate getAppWidgetIds in=" + (System.nanoTime() - start) / 1000 + " =>ids=" + Arrays.toString(ids) + " me=" + this);
			} catch(final Exception ex) {
				Log.e(BaseWidgetProvider.TAG, "", ex);
			}
		}

		if(null == ids || 0 == ids.length) {
			if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "pushUpdate FAIL no ids me=" + this);
			return null;
		}

		if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "pushUpdate ids to update: " + Arrays.toString(ids) + " data=" + data + " me=" + this);

		try {
			for(final int id : ids) {
				if(0 == id) { // Skip possible zero ids
					if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "pushUpdate SKIP as ids[0] me=" + this);
					break;
				}
				final RemoteViews rv = this.update(context, data, prefs, id); // java.lang.RuntimeException: Could not write bitmap to parcel blob.
				appWidgetManager.updateAppWidget(id, rv);
			}

		} catch(final Exception ex) {
			Log.e(BaseWidgetProvider.TAG, "", ex);
		}
		return data;
	}


	// NOTE: further overridden
	protected boolean getAANoAnimState(final WidgetUpdateData data, final WidgetContext widgetCtx) {
		if(data.albumArtNoAnim
			   || widgetCtx.lastAATimeStamp == data.albumArtTimestamp
			   || data.hasTrack && 0 != (data.flags & PowerampAPI.Track.Flags.FLAG_FIRST_IN_PLAYER_SESSION)
		) {

			if(BaseWidgetProvider.LOG) Log.w(BaseWidgetProvider.TAG, "getAANoAnimState =>true data.albumArtNoAnim=" + data.albumArtNoAnim + " same ts=" + (widgetCtx.lastAATimeStamp == data.albumArtTimestamp) +
					"  FLAG_FIRST_IN_PLAYER_SESSION=" + (data.flags & PowerampAPI.Track.Flags.FLAG_FIRST_IN_PLAYER_SESSION) + " bitmap=" + data.albumArtBitmap);

			return true;
		}
		return false;
	}


	public static String getReadable(final String title, final String unknown) {
		return BaseWidgetProvider.getReadable(title, unknown, false);
	}
	
	public static String getReadable(final String title, final String unknown, final boolean allowEmpty) {
		if(null != title && (allowEmpty || 0 < title.length())) {
			return title;
		}
		return unknown;
	}
}