package com.maxmpz.powerampproviderexample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.maxmpz.poweramp.player.PowerampAPI;
import com.maxmpz.poweramp.player.PowerampAPI.Lyrics;
import com.maxmpz.poweramp.player.PowerampAPI.Track;
import com.maxmpz.poweramp.player.PowerampAPIHelper;
import com.maxmpz.poweramp.player.TrackProviderConsts;
import com.maxmpz.poweramp.player.TrackProviderHelper;
import com.maxmpz.poweramp.player.TrackProviderProto;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * Example provider demonstrating:
 * - providing tracks hierarchy for the Poweramp scanner
 * - providing tracks via direct file descriptor and via seekable socket protocol
 */
public class ExampleProvider extends DocumentsProvider {
	private static final String TAG = "ExampleProvider";
	private static final boolean LOG = true;

	/**
	 * If true, we're copying our mp3s from assets to local filesystem. This is because Poweramp can't reliable play assets fd - fd pointing to apk file itself, mostly as
	 * end of file can't be properly detected for many formats without proper length header
	 */
	private static final boolean USE_MP3_COPY = true;

	/**
	 * If true, use {@link android.os.storage.StorageManager#openProxyFileDescriptor} on Android 8+ which provides seekable file descriptors implemented by Android Framework.<br>
	 * It's much easier to implement vs Seekable sockets protocol and recommended for apps targeting Android 8+
	 */
	private static final boolean USE_OPEN_PROXY_FILE_DESCRIPTOR = true;

	/** Link to mp3 track to demonstrate http track with the duration */
	private static final String DUBSTEP_HTTP_URL = "https://raw.githubusercontent.com/maxmpz/powerampapi/master/poweramp_provider_example/app/src/main/assets/bensound-dubstep.mp3";

	/** Link to mp3 track to demonstrate http track with the duration */
	private static final String SUMMER_HTTP_URL = "https://raw.githubusercontent.com/maxmpz/powerampapi/master/poweramp_provider_example/app/src/main/assets/bensound-summer.mp3";

	/** Link to flac track to demonstrate http track with the duration */
	private static final String DUBSTEP_FLAC_HTTP_URL = "https://raw.githubusercontent.com/maxmpz/powerampapi/master/poweramp_provider_example/app/src/main/assets/bensound-dubstep.flac";

	/** Docid suffix for static url tracks */
	private static final String DOCID_STATIC_URL_SUFFIX = ".url";

	/** Docid suffix for dynamic url tracks */
	private static final String DOCID_DYNAMIC_URL_SUFFIX = ".dynamicurl";

	private static final long DUBSTEP_SIZE = 2044859L;
	private static final long DUBSTEP_DURATION_MS = 125000L;
	private static final long SUMMER_SIZE = 4620151L;
	private static final long SUMMER_DURATION_MS = 217000L;

	private static final long DUBSTEP_FAKE_FLAC_SIZE = 14000000;

	// ~116 bytes per ms in the real bensound-dubstep.flac, but "fake" value is an approximation
	private static final long DUBSTEP_FAKE_AVERAGE_BYTES_PER_MS = 112;

	/** If > 0, we'll force-stop the playback after these bytes played. Works for seekable sockets/PA protocol */
	private static final long DEBUG_STOP_PROTOCOL_AFTER_BYTES = 0; // e.g. = 500000

	/** If true, we'll force-stop playback immediately in open. Works for seekable sockets/PA protocol */
	private static final boolean DEBUG_ALWAYS_STOP_IN_OPEN = false;

	/** If true, we'll force-stop playback immediately in thread. Works for seekable sockets/PA protocol */
	private static final boolean DEBUG_ALWAYS_STOP_PROTOCOL = false;

	/** If true, we'll force-stop playback after the header. Works for seekable sockets/PA protocol */
	private static final boolean DEBUG_STOP_PROTOCOL_AFTER_HEADER = false;


	/** Default columns returned for roots */
	private static final String[] DEFAULT_ROOT_PROJECTION = {
			DocumentsContract.Root.COLUMN_ROOT_ID,
			DocumentsContract.Root.COLUMN_TITLE,
			DocumentsContract.Root.COLUMN_SUMMARY,
			DocumentsContract.Root.COLUMN_FLAGS,
			DocumentsContract.Root.COLUMN_ICON,
			DocumentsContract.Root.COLUMN_DOCUMENT_ID,
	};

	/** Default columns returned for documents - folders and tracks (not including metadata) */
	private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
			DocumentsContract.Document.COLUMN_DOCUMENT_ID,
			DocumentsContract.Document.COLUMN_MIME_TYPE,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_LAST_MODIFIED,
			DocumentsContract.Document.COLUMN_FLAGS,
			DocumentsContract.Document.COLUMN_SIZE,
	};

	/**
	 * Default columns returned for tracks, including metadata.<br>
	 * NOTE: where possible, we're using standard MediaStore or MediaFormat column names<br><br>
	 *
	 * NOTE: if TITLE and DURATION columns are missing, empty, or null, Poweramp assumes cursor doesn't contain metadata and doesn't check other metadata columns.<br>
	 * Instead of relying on the cursor metadata, Poweramp will try to open the file via openDocument and will scan tags in it.
	 * If TITLE or DURATION exists in cursor, Poweramp won't attempt to read any tags directly from the track, using what is given in cursor.
	 * NOTE: if TITLE and DURATION are missing, Poweramp also won't use thumbnail API (even if FLAG_SUPPORTS_THUMBNAIL is set). Instead Poweramp reads cover directly
	 * from track (via another openDocument call)
	 */
	private static final String[] DEFAULT_TRACK_AND_METADATA_PROJECTION = {
			DocumentsContract.Document.COLUMN_DOCUMENT_ID,
			DocumentsContract.Document.COLUMN_MIME_TYPE,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_LAST_MODIFIED,
			DocumentsContract.Document.COLUMN_FLAGS,
			DocumentsContract.Document.COLUMN_SIZE,

			MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.ARTIST,
            MediaStore.MediaColumns.ALBUM,
			MediaStore.Audio.AudioColumns.YEAR,
			TrackProviderConsts.COLUMN_ALBUM_ARTIST,
            MediaStore.MediaColumns.COMPOSER,
			TrackProviderConsts.COLUMN_GENRE,
			MediaStore.Audio.AudioColumns.TRACK,
			TrackProviderConsts.COLUMN_TRACK_ALT,
			MediaFormat.KEY_SAMPLE_RATE,
			MediaFormat.KEY_CHANNEL_COUNT,
			MediaFormat.KEY_BIT_RATE,
			TrackProviderConsts.COLUMN_BITS_PER_SAMPLE
	};


	private long mApkInstallTime;


	@Override
	public boolean onCreate() {
		// Code to retrieve own apk install time. We use this here as lastModified. Not needed for real providers which can retrieve lastModified from the
		// content itself (either from network or filesystem)
		final PackageManager pm = this.getContext().getPackageManager();
		try {
			final PackageInfo pakInfo = pm.getPackageInfo(this.getContext().getApplicationInfo().packageName, 0);
            this.mApkInstallTime = 0 < pakInfo.lastUpdateTime ? pakInfo.lastUpdateTime : pakInfo.firstInstallTime;

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "onCreate mApkInstallTime=" + this.mApkInstallTime);
		} catch(final PackageManager.NameNotFoundException ex) {
			Log.e(ExampleProvider.TAG, "", ex);
            this.mApkInstallTime = System.currentTimeMillis();
		}

		if(ExampleProvider.USE_MP3_COPY) {
			// Extract our mp3s to storage as Poweramp won't properly play asset apk fd (fd points to apk itself, so Poweramp tries to play the apk file itself,
			// basically playing first found mp3 from it)
			final File dir = this.getContext().getFilesDir();
            this.copyAsset("bensound-dubstep.mp3", dir, false);
            this.copyAsset("bensound-dubstep.flac", dir, false);
            this.copyAsset("bensound-summer.mp3", dir, false);
            this.copyAsset("streams-playlist.m3u8", dir, false);
		}

		return true;
	}

	@Override
	public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "queryRoots projection=" + Arrays.toString(projection));
		try {

			final MatrixCursor c = new MatrixCursor(ExampleProvider.resolveRootProjection(projection));
			MatrixCursor.RowBuilder row;

			// Items without metadata provided by the provider (Poweramp reads track metadata from track itself)
			row = c.newRow();
			row.add(DocumentsContract.Root.COLUMN_ROOT_ID, "rootId1");
			row.add(DocumentsContract.Root.COLUMN_TITLE, "Root 1");
			row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Poweramp Example Provider");
			row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD); // Required
			row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher);
			row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root1");

			// Items with metadata (Poweramp gets metadata from cursor and doesn't try to read tags from tracks)
			row = c.newRow();
			row.add(DocumentsContract.Root.COLUMN_ROOT_ID, "rootId2");
			row.add(DocumentsContract.Root.COLUMN_TITLE, "Root 2");
			row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Poweramp Example Provider");
			row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD); // Required
			row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher);
			row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root2");

			// Streams: m3u8 playlist, http stream with the duration, http no-duration stream (radio)
			row = c.newRow();
			row.add(DocumentsContract.Root.COLUMN_ROOT_ID, "rootId3");
			row.add(DocumentsContract.Root.COLUMN_TITLE, "Root 3 (Streams)");
			row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Poweramp Example Provider");
			row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD); // Required
			row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher);
			row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root3");

			return c;

		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "", th);
		}
		return null;
	}

	/**
	 * Query document is used:<br>
	 * - by Android picker to show appropriate directory and tracks<br>
	 * - by Poweramp to retrieve track metadata during the library scan<br>
	 * - by Poweramp to retrieve track metadata for Info/Tags/Lyrics UI<br>
	 *   - in this case Poweramp will ask for extra fields, such as lyrics/synced lyrics<br>
	 */
	@Override
	public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "queryDocument documentId=" + documentId + " projection=" + Arrays.toString(projection));

		try {

			// NOTE: using simplified root/folder/file detection here. Real provider should base this on actual hierarchy, either retrieved from network
			// or from filesystem

			// If this is root, just return static root data
			if(!documentId.contains("/") && documentId.startsWith("root")) {
				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveDocumentProjection(projection));
				final MatrixCursor.RowBuilder row = c.newRow();
				final AssetManager assets = this.getContext().getResources().getAssets();
                this.fillFolderRow(documentId, row, this.hasSubDirs(assets, documentId) ? TrackProviderConsts.FLAG_HAS_SUBDIRS : TrackProviderConsts.FLAG_NO_SUBDIRS);
				// NOTE: we return display name derived from documentId here VS returning the same label as used for Root.COLUMN_TITLE
				// Real app should use same labels in both places (roots and queryDocument) for same root
				row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, this.capitalize(documentId));
				return c;

			} else if(documentId.startsWith("root3") && documentId.endsWith(ExampleProvider.DOCID_STATIC_URL_SUFFIX)) {
				// Url mp3 with a duration. We must provide duration here to avoid endless/non-seekable stream

				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveTrackProjection(projection));
				final int trackNum = ExampleProvider.extractTrackNum(documentId);
				if(documentId.contains("dubstep")) {
                    this.fillURLRow(documentId, c.newRow(), ExampleProvider.DUBSTEP_HTTP_URL, ExampleProvider.DUBSTEP_SIZE, "Dubstep", 1 == trackNum ? 0 : ExampleProvider.DUBSTEP_DURATION_MS, true, true, true); // Send wave
				} else {
					final boolean emptyWave = 4 > trackNum; // 1..4 summer tracks with empty wave, for the others - allow Poweramp to scan them
                    this.fillURLRow(documentId, c.newRow(), ExampleProvider.SUMMER_HTTP_URL, ExampleProvider.SUMMER_SIZE, "Summer", 1 == trackNum ? 0 : ExampleProvider.SUMMER_DURATION_MS, true, false, emptyWave);
				}
				return c;

			} else if(documentId.startsWith("root3") && documentId.endsWith(ExampleProvider.DOCID_DYNAMIC_URL_SUFFIX)) {
				// Dynamic url to mp3 with a duration. We must provide duration here to avoid endless/non-seekable stream
				// NOTE: we use TrackProviderConsts.DYNAMIC_URL as URL here to indicate dynamic url track

				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveTrackProjection(projection));
				final int trackNum = ExampleProvider.extractTrackNum(documentId);
				if(documentId.contains("dubstep")) {
                    this.fillURLRow(documentId, c.newRow(), TrackProviderConsts.DYNAMIC_URL, ExampleProvider.DUBSTEP_SIZE, "Dubstep", ExampleProvider.DUBSTEP_DURATION_MS, true, true, true); // Send wave
				} else {
					final boolean emptyWave = 4 > trackNum; // 1..4 summer tracks with empty wave, for the others - allow Poweramp to scan them
                    this.fillURLRow(documentId, c.newRow(), TrackProviderConsts.DYNAMIC_URL, ExampleProvider.SUMMER_SIZE, "Summer", ExampleProvider.SUMMER_DURATION_MS, true, false, emptyWave);
				}
				return c;

			} else if(documentId.endsWith(".mp3") || documentId.endsWith(".flac")) { // Seems like a track
				// We are adding metadata for root2 and check if it's actually requested as a small optimization (which can be big if track metadata retrieval requires additional processing)
				final boolean addMetadata = documentId.startsWith("root2/") && null != projection && this.arrayContains(projection, MediaStore.MediaColumns.TITLE);
				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveTrackProjection(projection));

				final boolean addLyrics = null != projection
					                    && (this.arrayContains(projection, TrackProviderConsts.COLUMN_TRACK_LYRICS)
											|| this.arrayContains(projection, TrackProviderConsts.COLUMN_TRACK_LYRICS_SYNCED));
				final boolean sendWave = documentId.contains("dubstep") && null != projection
					                   && this.arrayContains(projection, TrackProviderConsts.COLUMN_TRACK_WAVE);
                this.fillTrackRow(
					documentId,
					c.newRow(),
					addMetadata,
					sendWave, // Adding wave as well to root2 tracks
					addLyrics,
                        ExampleProvider.extractTrackNum(documentId),
					0,
					TrackProviderConsts.FLAG_HAS_LYRICS // Set lyrics flag for all of these tracks
				);
				return c;

			} else if(documentId.endsWith(".m3u")) { // Seems like a playlist
				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveDocumentProjection(projection));
                this.fillPlaylistRow(documentId, c.newRow());
				return c;

			} else { // This must be a directory
				MatrixCursor c = new MatrixCursor(ExampleProvider.resolveDocumentProjection(projection));
				final AssetManager assets = this.getContext().getResources().getAssets();
                this.fillFolderRow(documentId, c.newRow(), this.hasSubDirs(assets, documentId) ? TrackProviderConsts.FLAG_HAS_SUBDIRS : TrackProviderConsts.FLAG_NO_SUBDIRS);
				return c;
			}

		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
		}
		return null;
	}

	private void fillURLRow(@NonNull final String documentId, @NonNull final MatrixCursor.RowBuilder row, @NonNull final String url,
                            final long size, @NonNull String title,
                            final long duration, final boolean sendMetadata, final boolean sendWave, final boolean sendEmptyWave
	) {
		row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
		row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "audio/mpeg");
		// The display name defines name of the track "file" in "Show File Names" mode. There is also a title via MediaStore.MediaColumns.TITLE.
		// It's up to you how you define display name, it can be anything filename alike, or it can just match track title
		row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ExampleProvider.getShortName(documentId));
		// As our assets data is always static, we just return own apk installation time. For real folder structure, preferable last modified for given folder should be returned.
		// This ensures Poweramp incremental scanning process. If we return <= 0 value here, Poweramp will be forced to rescan whole provider hierarchy each time it scans
		row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, this.mApkInstallTime);
		// Optional, real provider should preferable return real track file size here or 0
		row.add(DocumentsContract.Document.COLUMN_SIZE, size);
		// Setting this will cause Poweramp to ask for the track album art via getDocumentThumbnail, but only if other metadata (MediaStore.MediaColumns.TITLE/MediaStore.MediaColumns.DURATION) exists
		row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL);

		row.add(TrackProviderConsts.COLUMN_URL, url);
		row.add(MediaStore.MediaColumns.DURATION, duration); // Milliseconds, long. If duration <= 0, this is endless non-seekable stream (e.g. radio)

		if(sendMetadata) { // NOTE: Poweramp doesn't need extra metadata (except COLUMN_URL/DURATION for streams) for queryDocuments, but requires that for queryDocument
			if(TrackProviderConsts.DYNAMIC_URL.equals(url)) {
				title += " Dynamic";
			}

			final String prefix = title + " ";

			// Some dump tags logic - as we have 2 static files here as an example, but they have docId like dubstep1.mp3, summer2.mp3, etc.
			// Real provider should get this info from network or extract from the file
			final int trackNum = ExampleProvider.extractTrackNum(documentId);

			row.add(MediaStore.MediaColumns.TITLE, prefix + "URL Track " + trackNum);
			row.add(MediaStore.MediaColumns.ARTIST, prefix + "URL Artist");
			row.add(MediaStore.MediaColumns.ALBUM, prefix + "URL Album");
			row.add(MediaStore.Audio.AudioColumns.YEAR, 2020); // Integer
			row.add(TrackProviderConsts.COLUMN_ALBUM_ARTIST, prefix + "URL Album Artist");
			row.add(MediaStore.MediaColumns.COMPOSER, prefix + "URL Composer");
			row.add(TrackProviderConsts.COLUMN_GENRE, prefix + "URL Genre");
			// Track number. Optional, but needed for proper sorting in albums
			row.add(MediaStore.Audio.AudioColumns.TRACK, trackNum);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_SAMPLE_RATE, 44100);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_CHANNEL_COUNT, 2);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_BIT_RATE, 128000);
			// Optional, used just for Info/Tags and lists  (for hi-res)
			row.add(TrackProviderConsts.COLUMN_BITS_PER_SAMPLE, 16);
			if(sendWave && 0 < duration) {
				row.add(TrackProviderConsts.COLUMN_TRACK_WAVE, TrackProviderHelper.floatsToBytes(this.genRandomWave())); // We must put byte[] array here
			} else if(sendEmptyWave) {
				// Add this for the default waveseek if you don't want URL to be downloaded one more time and scanned for the wave
				row.add(TrackProviderConsts.COLUMN_TRACK_WAVE, new byte[0]);
			} // Else we allow Poweramp to scan URL for wave
		}
	}

	private float[] genRandomWave() {
		final float[] wave = new float[100];
		for(int i = 0; i < wave.length; i++) {
			wave[i] = (float)(Math.random() * 2.0 - 1.0);
		}
		return wave;
	}

	private void fillFolderRow(@NonNull final String documentId, @NonNull final MatrixCursor.RowBuilder row, final int flags) {
		row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
		row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
		// Here we're returning actual folder name, but Poweramp supports anything in display name for folders, not necessary the name matching or related to the documentId or path.
		row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ExampleProvider.getShortDirName(documentId));
		row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, this.mApkInstallTime);

		final boolean hasThumb = documentId.endsWith("1");
		if(hasThumb) {
			row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL); // Thumbnails for folders are supported since build 869
		}
		// If asked to add the subfolders hint, add it
		if(0 != flags) {
			row.add(TrackProviderConsts.COLUMN_FLAGS, flags);
		}
	}

	private void fillPlaylistRow(@NonNull final String documentId, @NonNull final MatrixCursor.RowBuilder row) {
		// NOTE: for playlists, the playlist documentId should preferable end with some extension. Poweramp also looks into mime type, or assumes it's .m3u8 playlist if no mime type
		row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
		row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "audio/mpegurl");
		// The display name defines name of the track "file" in "Show File Names" mode. There is also a title via MediaStore.MediaColumns.TITLE.
		// It's up to you how you define display name, it can be anything filename alike, or it can just match track title
		row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ExampleProvider.getShortName(documentId));
		// As our assets data is always static, we just return own apk installation time. For real folder structure, preferable last modified for given folder should be returned.
		// This ensures Poweramp incremental scanning process. If we return <= 0 value here, Poweramp will be forced to rescan whole provider hierarchy each time it scans
		row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, this.mApkInstallTime);
	}

	private void fillTrackRow(
            @NonNull final String documentId,
            @NonNull final MatrixCursor.RowBuilder row,
            final boolean addMetadata,
            final boolean sendWave,
            final boolean sendLyrics,
            final int trackNum,
            final int trackNumAlt,
            final int extraFlags
	) {
		final boolean isFlac = documentId.endsWith(".flac");
		final boolean isDubstep = documentId.contains("dubstep");

		row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
		row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, isFlac ? "audio/flac" : "audio/mpeg");
		// The display name defines name of the track "file" in "Show File Names" mode. There is also a title via MediaStore.MediaColumns.TITLE.
		// It's up to you how you define display name, it can be anything filename alike, or it can just match track title
		row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ExampleProvider.getShortName(documentId));
		// As our assets data is always static, we just return own apk installation time. For real folder structure, preferable last modified for given folder should be returned.
		// This ensures Poweramp incremental scanning process. If we return <= 0 value here, Poweramp will be forced to rescan whole provider hierarchy each time it scans
		row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, this.mApkInstallTime);

		final String filePath = this.docIdToFileName(documentId); // We only have 2 real mp3s here for many "virtual" tracks

		// Optional, real provider should preferable return real track file size here or 0.
		row.add(DocumentsContract.Document.COLUMN_SIZE, this.getAssetFileSize(this.getContext().getResources().getAssets(), filePath));

		// NOTE: Poweramp doesn't need extra metadata (except COLUMN_URL/DURATION for streams) for queryDocuments,
		// but requires that for queryDocument for tracks, which are not direct fd. Direct fd tracks still can be quickly scanned by Poweramp, but
		// socket/pipe/url tracks won't be scanned and thus metadata is required for them

		// If provided, COLUMN_TRACK_ALT will sort tracks differently (for "by track #" sorting) in Folders/Folders Hierarchy
		if(0 < trackNumAlt) {
			row.add(TrackProviderConsts.COLUMN_TRACK_ALT, trackNumAlt);
		}

		if(addMetadata) {
			// Some dump tags logic - as we have 2 static files here as an example, but they have docId like dubstep1.mp3, summer2.mp3, etc.
			// Real provider should get this info from network or extract from the file
			final String prefix = isDubstep ? "Dubstep " : "Summer ";

			int flags = 0;

			// Setting this will cause Poweramp to ask for track album art via getDocumentThumbnail, but only if other metadata (MediaStore.MediaColumns.TITLE/MediaStore.MediaColumns.DURATION) exists
			flags |= DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;
			flags |= TrackProviderConsts.FLAG_HAS_LYRICS;

			row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
			row.add(MediaStore.MediaColumns.TITLE, prefix + "Track " + trackNum);
			row.add(MediaStore.MediaColumns.ARTIST, prefix + "Artist");
			row.add(MediaStore.MediaColumns.DURATION, isDubstep ? 125000L : 217000L); // Milliseconds, long
			row.add(MediaStore.MediaColumns.ALBUM, prefix + "Album");
			row.add(MediaStore.Audio.AudioColumns.YEAR, isDubstep ? 2020 : 2019); // Integer
			row.add(TrackProviderConsts.COLUMN_ALBUM_ARTIST, prefix + "Album Artist");
			row.add(MediaStore.MediaColumns.COMPOSER, prefix + " Composer");
			row.add(TrackProviderConsts.COLUMN_GENRE, prefix + " Genre");
			// Track number. Optional, but needed for proper sorting in albums.
			// If not defined (or set to <= 0), Poweramp will use cursor position for track - this may be useful for folders where we want default cursor based ordering of items -
			// exactly as provided by cursor. Just don't send MediaStore.Audio.AudioColumns.TRACK column for such tracks.
			// NOTE: Poweramp won't scan track number from filename for provider provided tracks, nor it will cut number (e.g. "01-" from "01-trackname") from displayName
			// as it does by default for normal filesystem tracks
			row.add(MediaStore.Audio.AudioColumns.TRACK, trackNum);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_SAMPLE_RATE, isDubstep ? 44100 : 48000);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_CHANNEL_COUNT, 2);
			// Optional, used just for Info/Tags
			row.add(MediaFormat.KEY_BIT_RATE, isFlac ? 720000 : (isDubstep ? 128000 : 192000));
			// Optional, used just for Info/Tags and lists  (for hi-res)
			row.add(TrackProviderConsts.COLUMN_BITS_PER_SAMPLE, 16);

			if(sendWave) {
				row.add(TrackProviderConsts.COLUMN_TRACK_WAVE, TrackProviderHelper.floatsToBytes(this.genRandomWave()));
			}

			// Add our own extra flags if any
			if(0 != extraFlags) {
				row.add(TrackProviderConsts.COLUMN_FLAGS, extraFlags);
			}

			if(sendLyrics) {
				if(isDubstep) {
					// For dubstep add LRC (synced) lyrics
					row.add(
						TrackProviderConsts.COLUMN_TRACK_LYRICS_SYNCED,
						"[0:00.00]La la la\n[0:05.00]Synced Lyrics for track " + prefix + "Track " + trackNum + "\n" +
						"[0:05.00]Line\n" +
						"[0:10.00]Line\n" +
						"[0:30.00]The last line\n"
					);
				} else {
					row.add(TrackProviderConsts.COLUMN_TRACK_LYRICS, "La la la\nLyrics for track " + prefix + "Track " + trackNum);
				}
			}
		}
	}

	/**
	 * @param sortOrder this field is not used directly as sorting order as Poweramp always use some user defined sorting which is
	 * based on track # or other user selected criteria. Instead, we use sortOrder as optional additional parameter for things like
	 */
	@Override
	public Cursor queryChildDocuments(final String parentDocumentId, final String[] projection, final String sortOrder) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "queryChildDocuments parentDocumentId=" + parentDocumentId + " projection=" + Arrays.toString(projection));

		try {
			final AssetManager assets = this.getContext().getResources().getAssets();
			final String[] filesAndDirs = assets.list(parentDocumentId);

			// To demonstrate folders and files sorting based on cursor position, sort and reverse the array. Do this for Root1
			if("root1".equals(parentDocumentId)) {
				Arrays.sort(filesAndDirs, 0, filesAndDirs.length, new Comparator<String>() {
					@Override
					public int compare(final String o1, final String o2) {
						return o2.compareToIgnoreCase(o1);
					}
				});
			}

			// We are adding metadata for root2 and check if it's actually requested as a small optimization (which can be big if track metadata retrieval requires additional processing)
			final boolean addMetadata = parentDocumentId.startsWith("root2") && null != projection && this.arrayContains(projection, MediaStore.MediaColumns.TITLE);

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "queryChildDocuments filesAndDirs=" + Arrays.toString(filesAndDirs));

			MatrixCursor c = new MatrixCursor(ExampleProvider.resolveDocumentProjection(projection));

			final int ix = 0;
			for(final String fileOrDir : filesAndDirs) {
				final String path = parentDocumentId + "/" + fileOrDir; // Path is our documentId. Note that this provider defines paths/documentIds format. Poweramp treats them as opaque string

				if(this.isAssetDir(assets, path)) {
                    this.fillFolderRow(path, c.newRow(), this.hasSubDirs(assets, path) ? TrackProviderConsts.FLAG_HAS_SUBDIRS : TrackProviderConsts.FLAG_NO_SUBDIRS);

				} // Else this is empty.txt file, we skip it
			}

			// Generate some number of files for given folder

			// NOTE: Poweramp scans each directly once for its normal scans, and never rescans them again, until:
			// - parent folder lastModified changed
			// - full rescan required by user or external intent

			final int count = parentDocumentId.length(); // Just various number based on parent document path length

			String docId;

			if("root3".equals(parentDocumentId)) {
				// For root3 add m3u8 playlist
                this.fillPlaylistRow(parentDocumentId + "/" + "streams-playlist.m3u8", c.newRow());

				// Add dynamic URL tracks
				docId = parentDocumentId + "/" + "dubstep" + "-" + 1 + ExampleProvider.DOCID_DYNAMIC_URL_SUFFIX;
                this.fillURLRow(docId, c.newRow(),
						TrackProviderConsts.DYNAMIC_URL,
                        ExampleProvider.DUBSTEP_SIZE,
						"", // NOTE: titles not sent here
                        ExampleProvider.DUBSTEP_DURATION_MS,
						false, false, false); // Not sending metadata here

				docId = parentDocumentId + "/" + "summer" + "-" + 2 + ExampleProvider.DOCID_DYNAMIC_URL_SUFFIX;
                this.fillURLRow(docId, c.newRow(),
						TrackProviderConsts.DYNAMIC_URL,
                        ExampleProvider.SUMMER_SIZE,
						"", // NOTE: titles not sent here
                        ExampleProvider.SUMMER_DURATION_MS,
						false, false, false); // Not sending metadata here

				// And fill with random number of http links to the tracks
				for(int i = 0; i < count; i++) {
					final boolean isDubstep = 0 != (i & 1);
					final boolean isStream = 0 == i; // First track here will be a "stream" - non seekable, no duration
					docId = parentDocumentId + "/" + (isDubstep ? "dubstep" : "summer") + "-" + (i + 3) + ExampleProvider.DOCID_STATIC_URL_SUFFIX;
                    this.fillURLRow(docId, c.newRow(),
							isDubstep ? ExampleProvider.DUBSTEP_HTTP_URL : ExampleProvider.SUMMER_HTTP_URL,
							isDubstep ? ExampleProvider.DUBSTEP_SIZE : ExampleProvider.SUMMER_SIZE,
							"", // NOTE: titles not sent here
							isStream ? 0 : (isDubstep ? ExampleProvider.DUBSTEP_DURATION_MS : ExampleProvider.SUMMER_DURATION_MS),
							false, false, false);
				}

			} else {
				// For root1 and root2 generate docId like root1/Folder2/dubstep-10.mp3

				// For root1, demonstrate Folders/Folders Hierarchy sorting based on alternative track number
				// We reverse number positions of tracks here, but still providing non-reversed track number to use as tag number in albums and other non-folder categories
				for(int i = 0; i < count; i++) {
					docId = parentDocumentId + "/" + (0 != (i & 1) ? "dubstep" : "summer") + "-" + (i + 1) + (1 == i ? ".flac" : ".mp3"); // First dubstep track will be flac
					final int sort = i + 1;
					int sortAlt = 0;
					if("root1".equals(parentDocumentId)) {
						sortAlt = count - i;
					}
                    this.fillTrackRow(docId, c.newRow(), addMetadata, false, false, sort, sortAlt, 0);
				}
			}

			return c;
		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + parentDocumentId, th);
		}

		return null;
	}

	/**
	 * Simple method to check our assets directory structure for children folders. We have only folders and empty.txt files there
	 */
	private boolean hasSubDirs(final AssetManager assets, final String path) {
		try {
			final String[] filesAndDirs = assets.list(path);
			if(null != filesAndDirs && 0 < filesAndDirs.length) {
				for(final String child : filesAndDirs) {
					if(!child.endsWith(".txt")) {
						return true;
					}
				}
			}
		} catch(final IOException e) {
			Log.e(ExampleProvider.TAG, path, e);
		}
		return false;
	}

	/** Send album art for tracks with track-provided metadata */
	@Override
	public AssetFileDescriptor openDocumentThumbnail(final String documentId, final Point sizeHint, final CancellationSignal signal) throws FileNotFoundException {

		final String imageSrc;

		if(documentId.endsWith(".mp3") || documentId.endsWith(".flac") || documentId.endsWith(ExampleProvider.DOCID_STATIC_URL_SUFFIX)
			|| documentId.endsWith(ExampleProvider.DOCID_DYNAMIC_URL_SUFFIX)
		) {
			// We have just 2 images here and we ignore sizeHint. Poweramp preferred image size is 1024x1024px
			final boolean isDubstep = documentId.contains("dubstep");
			imageSrc = isDubstep ? "cover-1.jpg" : "cover-2.jpg";

		} else {
			// Assume this is a folder. Real provider should verify if documentId is a real folder
			imageSrc = "folder-1.jpg";
		}

		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openDocumentThumbnail documentId=" + documentId + " sizeHint=" + sizeHint + " imageSrc=" + imageSrc);

		if(null == imageSrc) throw new FileNotFoundException(documentId);


		try {
			return this.getContext().getResources().getAssets().openFd(imageSrc);
		} catch(final IOException e) {
			throw new FileNotFoundException(documentId);
		}
	}

	/**
	 * Send actual track data as direct file descriptor or seekable socket protocol
	 */
	@Override
	public ParcelFileDescriptor openDocument(final String documentId, final String mode, final CancellationSignal signal) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openDocument documentId=" + documentId + " mode=" + mode + " callingPak=" + this.getCallingPackage());
		final int accessMode = ParcelFileDescriptor.parseMode(mode);
		if(0 == (accessMode & ParcelFileDescriptor.MODE_READ_ONLY)) throw new IllegalAccessError("documentId=" + documentId + " mode=" + mode);

		// Poweramp provides CancellationSignal, so we may want to check that on open and periodically (for example, in case of using pipe here)
		if(null != signal) {
			signal.throwIfCanceled();
		}

		final String filePath = this.docIdToFileName(documentId);
		if(null == filePath) throw new FileNotFoundException(documentId);

		// Let's send root2 "dubstep" via seekable socket and other files - via direct fd. Don't do this for root1 where no metadata given and Poweramp expects direct fd tracks
		// Check package name for Poweramp (or other known client) which supports this fd protocol
		final String pak = this.getCallingPackage();
		if(null != pak && pak.equals(PowerampAPIHelper.getPowerampPackageName(this.getContext()))
				&& documentId.startsWith("root2/") && documentId.contains("dubstep")
		) {
			// Let's open dubstep-2 via milliseconds based seekbable sockets, and other dubsteps - via byte offset seekable sockets
			if(documentId.endsWith("-2.flac")) {
				return this.openViaSeekableSocket2(documentId, filePath, signal);
			} else {
				return this.openViaSeekableSocket(documentId, filePath, signal);
			}
		}

		// Let's send root2 "summer" via seekable proxy file descriptors. No need to check for client package as these file descriptors should be supported everywhere
		if(26 <= Build.VERSION.SDK_INT && ExampleProvider.USE_OPEN_PROXY_FILE_DESCRIPTOR && documentId.startsWith("root2/") && documentId.contains("summer")) {
			return this.openViaProxyFd(documentId, filePath, signal);
		}

		return this.openViaDirectFD(documentId, filePath);
	}

	/** For given docId, return appropriate asset file name */
    @Nullable
    private String docIdToFileName(final String documentId) {
		if(documentId.endsWith(".m3u8")) {
			return "streams-playlist.m3u8";

		} else if(documentId.endsWith(".mp3")) {
			final boolean isDubstep = documentId.contains("dubstep");
			return isDubstep ? "bensound-dubstep.mp3" : "bensound-summer.mp3"; // We only have 2 real mp3s here for many "virtual" tracks

		} else if(documentId.endsWith(".flac")) {
			return "bensound-dubstep.flac";

		}
		return null;
	}

	/** This version of the method uses byte offset based seeks */
	private ParcelFileDescriptor openViaSeekableSocket(String documentId, final String filePath, CancellationSignal signal) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket documentId=" + documentId + " filePath=" + filePath);
		try {
			ParcelFileDescriptor[] fds = ParcelFileDescriptor.createSocketPair();
			File file = new File(this.getContext().getFilesDir(), filePath);
			long fileLength = file.length();

			if(ExampleProvider.DEBUG_ALWAYS_STOP_IN_OPEN) {
				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP due to DEBUG_ALWAYS_STOP_IN_OPEN");
				// NOTE: in this case if we just return socket here and stop, Poweramp will still wait for timeout
				// as socket was never closed from our side
				// If we want fail-fast approach from Poweramp, either return null here, or shutdown socket properly
				new Thread(new Runnable() {
					public void run() {
						if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP shutdown socket");
						final FileDescriptor socket = fds[1].getFileDescriptor();
						try {
							Os.shutdown(socket, 0);
						} catch(final ErrnoException ex) {
						}
						try {
							Os.close(socket);
						} catch(final ErrnoException ex) {
						}
					}
				}).start();
				return fds[0];
			}
			// NOTE: it's not possible to use timeouts on this side of the socket as Poweramp may open and hold the socket for an indefinite time while in the paused state
			// NOTE: don't use AsyncTask or other short-time thread pools here, as:
			// - this thread will be alive as long as Poweramp holds the file
			// - this can take an indefinite time, as Poweramp can be paused on the file

			new Thread(new Runnable() {
				public void run() {
					// NOTE: we can use arbitrary buffer size here >0, but increasing buffer will increase non-seekable "window" at the end of file
					// Using buffer size > MAX_DATA_SIZE will cause buffer to be split into multiple packets
					final ByteBuffer buf = ByteBuffer.allocateDirect(TrackProviderProto.MAX_DATA_SIZE);
					buf.order(ByteOrder.nativeOrder());

					long bytesSent = 0;

					try(final FileInputStream fis = new FileInputStream(file)) {
						final FileChannel fc = fis.getChannel(); // We'll be using nio for the buffer loading
						try(final TrackProviderProto proto = new TrackProviderProto(fds[1], fileLength)) {

							if(ExampleProvider.DEBUG_ALWAYS_STOP_PROTOCOL) {
								if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP due to DEBUG_ALWAYS_STOP_PROTOCOL");
								return; // Immediately stop. NOTE: this will call proto.close automatically
							}

							proto.sendHeader(); // Send initial header

							if(ExampleProvider.DEBUG_STOP_PROTOCOL_AFTER_HEADER) {
								if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP due to DEBUG_STOP_PROTOCOL_AFTER_HEADER");
								return; // Immediately stop. NOTE: this will call proto.close automatically
							}

							while(true) {
								int len;
								//noinspection UnusedAssignment
								while(0 < (len = fc.read(buf))) {
									buf.flip();

									// Send some data to Poweramp and optionally receive seek request
									// NOTE: avoid sending empty buffers here (!buf.hasRemaining()), as this will cause premature EOF
									final long seekRequestPos = proto.sendData(buf);

                                    ExampleProvider.this.handleSeekRequest(proto, seekRequestPos, fc, fileLength); // May be handle seek request

									bytesSent += buf.limit();

									buf.clear();

									if(ExampleProvider.DEBUG_STOP_PROTOCOL_AFTER_BYTES > 0 && DEBUG_STOP_PROTOCOL_AFTER_BYTES <= bytesSent) {
										// Force-stop playback from our side
										if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP due to DEBUG_STOP_AFTER_BYTES");
										return;
									}
								}

								// Here we're almost done with the file and hit EOF. Still keep file and socket opened until Poweramp closes socket
								//
								// As we may send number of pre-loaded buffers to Poweramp we may hit EOF much earlier prior the track actually finishes playing:
								// - we hit EOF here and may attempt to exit this thread/close socket
								// - Poweramp plays some buffered data
								// - user seeks the track. Poweramp will fail to seek it as our provider is done with the track and socket is closed
								//
								// Solution to this is to keep file and socket opened here and to continue seek commands processing until Poweramp actually closes socket.
								// This scenario can be easily tested by pausing Poweramp close to the track end and seeking while paused

								final long seekRequestPos = proto.sendEOFAndWaitForSeekOrClose();
								if(ExampleProvider.this.handleSeekRequest(proto, seekRequestPos, fc, fileLength)) {
									if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket file seek past EOF documentId=" + documentId);
									//noinspection UnnecessaryContinue
									continue; // We've just processed extra seek request, continue sending buffers
								} else {
									break; // We done here, Poweramp closed socket
								}
							}

							if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, " openViaSeekableSocket file DONE documentId=" + documentId);
						}
					} catch(final TrackProviderProto.TrackProviderProtoClosed ex) {
						if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket closed documentId=" + documentId + " " + ex.getMessage());
					} catch(final Throwable th) {
						// If we're here, we can't do much - close connection, release resources, and exit
						Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
					}
				}
			}).start();

			return fds[0];

		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
			throw new FileNotFoundException(documentId);
		}
	}

	/** This version of the method uses milliseconds offset based seek requests */
	private ParcelFileDescriptor openViaSeekableSocket2(String documentId, final String filePath, CancellationSignal signal) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 documentId=" + documentId + " filePath=" + filePath);
		try {
			ParcelFileDescriptor[] fds = ParcelFileDescriptor.createSocketPair();
			File file = new File(this.getContext().getFilesDir(), filePath);

			if(ExampleProvider.DEBUG_ALWAYS_STOP_IN_OPEN) {
				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket STOP due to DEBUG_ALWAYS_STOP_IN_OPEN - return null");
				// NOTE: in this case if we just return socket here and stop, Poweramp will still wait for timeout
				// as socket was never closed from our side
				// If we want fail-fast approach from Poweramp, either return null here, or shutdown socket properly
				return null;
			}

			//long fileLength = file.length();

			// NOTE: for the sake of testing, let's send some approximation of the file instead, based on
			// duration and average bytes per ms
			// The real DUBSTEP_AVERAGE_BYTES_PER_MS depends on compression and file format
			long fileLength = ExampleProvider.DUBSTEP_FAKE_AVERAGE_BYTES_PER_MS * ExampleProvider.DUBSTEP_DURATION_MS;

			// NOTE: it's not possible to use timeouts on this side of the socket as Poweramp may open and hold the socket for an indefinite time while in the paused state
			// NOTE: don't use AsyncTask or other short-time thread pools here, as:
			// - this thread will be alive as long as Poweramp holds the file
			// - this can take an indefinite time, as Poweramp can be paused on the file

			new Thread(new Runnable() {
				@SuppressWarnings("UnusedAssignment") public void run() {
					// NOTE: we can use arbitrary buffer size here >0, but increasing buffer will increase non-seekable "window" at the end of file
					// Using buffer size > MAX_DATA_SIZE will cause buffer to be split into multiple packets
					final ByteBuffer buf = ByteBuffer.allocateDirect(TrackProviderProto.MAX_DATA_SIZE);
					buf.order(ByteOrder.nativeOrder());
					long bytesSent = 0;

					try(final FileInputStream fis = new FileInputStream(file)) {
						final FileChannel fc = fis.getChannel(); // We'll be using nio for the buffer loading
						try(final TrackProviderProto proto = new TrackProviderProto(fds[1], fileLength)) {

							if(ExampleProvider.DEBUG_ALWAYS_STOP_PROTOCOL) {
								if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 STOP due to DEBUG_ALWAYS_STOP_PROTOCOL");
								return; // Immediately stop. NOTE: this will call proto.close automatically
							}

							proto.sendHeader(); // Send initial header

							if(ExampleProvider.DEBUG_STOP_PROTOCOL_AFTER_HEADER) {
								if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 STOP due to DEBUG_STOP_PROTOCOL_AFTER_HEADER");
								return; // Immediately stop. NOTE: this will call proto.close automatically
							}

							while(true) {
								int len;
								while(0 < (len = fc.read(buf))) {
									buf.flip();

									// Send some data to Poweramp and optionally receive seek request
									// NOTE: avoid sending empty buffers here (!buf.hasRemaining()), as this will cause premature EOF
									final TrackProviderProto.SeekRequest seekRequest = proto.sendData2(buf);

                                    ExampleProvider.this.handleSeekRequest2(proto, seekRequest, fc, fileLength); // May be handle seek request

									bytesSent += buf.limit();

									buf.clear();

									if(ExampleProvider.DEBUG_STOP_PROTOCOL_AFTER_BYTES > 0 && DEBUG_STOP_PROTOCOL_AFTER_BYTES <= bytesSent) {
										// Force-stop playback from our side
										if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 STOP due to DEBUG_STOP_AFTER_BYTES");
										return;
									}
								}

								// Here we're almost done with the file and hit EOF. Still keep file and socket opened until Poweramp closes socket
								//
								// As we may send number of pre-loaded buffers to Poweramp we may hit EOF much earlier prior the track actually finishes playing:
								// - we hit EOF here and may attempt to exit this thread/close socket
								// - Poweramp plays some buffered data
								// - user seeks the track. Poweramp will fail to seek it as our provider is done with the track and socket is closed
								//
								// Solution to this is to keep file and socket opened here and to continue seek commands processing until Poweramp actually closes socket.
								// This scenario can be easily tested by pausing Poweramp close to the track end and seeking while paused

								final TrackProviderProto.SeekRequest seekRequest = proto.sendEOFAndWaitForSeekOrClose2();
								if(ExampleProvider.this.handleSeekRequest2(proto, seekRequest, fc, fileLength)) {
									if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 file seek past EOF documentId=" + documentId);
									//noinspection UnnecessaryContinue
									continue; // We've just processed extra seek request, continue sending buffers
								} else {
									break; // We done here, Poweramp closed socket
								}
							}

							if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, " openViaSeekableSocket2 file DONE documentId=" + documentId);
						}
					} catch(final TrackProviderProto.TrackProviderProtoClosed ex) {
						if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaSeekableSocket2 closed documentId=" + documentId + " " + ex.getMessage());
					} catch(final Throwable th) {
						// If we're here, we can't do much - close connection, release resources, and exit
						Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
					}
				}
			}).start();

			return fds[0];

		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
			throw new FileNotFoundException(documentId);
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	private ParcelFileDescriptor openViaProxyFd(String documentId, String filePath, CancellationSignal signal) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaProxyFd documentId=" + documentId + " filePath=" + filePath);
		FileInputStream fis = null;
		try {
			File file = new File(this.getContext().getFilesDir(), filePath);
			long fileLength = file.length();
			fis = new FileInputStream(file);
			FileDescriptor fd = fis.getFD();

			HandlerThread thread = new HandlerThread(documentId); // This is the thread we're handling fd reading on
			thread.start();
			final Handler handler = new Handler(thread.getLooper());

			final StorageManager storageManager = (StorageManager) this.getContext().getSystemService(Context.STORAGE_SERVICE);

			FileInputStream finalFis = fis;

			final ParcelFileDescriptor pfd = storageManager.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, new ProxyFileDescriptorCallback() {
				@Override
				public long onGetSize() throws ErrnoException {
					if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "onGetSize fileLength=" + fileLength + " documentId=" + documentId);
					return fileLength;
				}

				public int onRead(final long offset, final int size, final byte[] data) throws ErrnoException {
					try {
						// NOTE: doing direct low level reads on file descriptor
						// Real provider, for example, for http streaming, could track offset and request remote seek if needed,
						// then read data from http stream to byte[] data

						if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "onRead documentId=" + documentId + " offset=" + offset + " size=" + size + " thread=" + Thread.currentThread());

						return Os.pread(fd, data, 0, size, offset);

					} catch(final ErrnoException errno) {
						Log.e(ExampleProvider.TAG, "documentId=" + documentId + " filePath=" + filePath, errno);
						throw errno; // Rethrow

					} catch(final Throwable e) {
						Log.e(ExampleProvider.TAG, "documentId=" + documentId + " filePath=" + filePath, e);
						throw new ErrnoException("onRead", OsConstants.EBADF);
					}
				}

				@Override
				public void onRelease() {
					if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaProxyFd onRelease");

					thread.quitSafely();

                    ExampleProvider.closeSilently(finalFis);

					if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaProxyFd DONE");
				}
			}, handler);

			return pfd;
		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
            ExampleProvider.closeSilently(fis); // If we here, we failed with or prior the proxy, so close everything
			throw new FileNotFoundException(documentId);
		}
	}

	/**
	 * THREADING: worker thread
	 * @return true if we actually handled seek request, false otherwise
	 */
	private boolean handleSeekRequest(@NonNull final TrackProviderProto proto, final long seekRequestPos, @NonNull final FileChannel fc, final long fileLength) {
		if(TrackProviderProto.INVALID_SEEK_POS != seekRequestPos) {
			// We have a seek request.
			// Your code may take any reasonable time to fulfil the seek request, e.g. it can reopen http connection with appropriate offset, etc.
			// Poweramp just waits for the seek result packet (the waiting is limited by the user-set timeout).
			// Now we should either send appropriate seek result packet or close the connection (e.g. on some error).
			// Poweramp ignores any other packets sent before the seek result packet.

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleSeekRequest seekRequestPos=" + seekRequestPos + " fileLength=" + fileLength);

			final long newPos = this.seekTrack(fc, seekRequestPos, fileLength);
			proto.sendSeekResult(newPos);
			return true;
		}
		return false;
	}

	/**
	 * THREADING: worker thread<br>
	 * NOTE: this version handles seek request based on byte offset BUT it can also handle it based on milliseconds.<br>
	 * Still, we need to send some byte based newPos offset back to Poweramp. The resulting byte offset may be an approximation
	 * @return true if we actually handled seek request, false otherwise
	 */
	private boolean handleSeekRequest2(@NonNull final TrackProviderProto proto, @Nullable final TrackProviderProto.SeekRequest seekRequest,
                                       @NonNull final FileChannel fc, final long fileLength
	) {
		if(null != seekRequest && TrackProviderProto.INVALID_SEEK_POS != seekRequest.offsetBytes) {
			// We have a seek request.
			// Your code may take any reasonable time to fulfil the seek request, e.g. it can reopen http connection with appropriate offset, etc.
			// Poweramp just waits for the seek result packet (the waiting is limited by the user-set timeout).
			// Now we should either send appropriate seek result packet or close the connection (e.g. on some error).
			// Poweramp ignores any other packets sent before the seek result packet.

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleSeekRequest seekRequestPos=" + seekRequest + " fileLength=" + fileLength);

			// NOTE: we could seek track here based on seekRequest.ms field.
			// In this case we still need to send back newPos as new byte offset.
			// This may be a rough approximation, e.g. something like: newPos = seekRequest.ms * averageBytesPerMsInThisFile

			// For the sake of testing, we'll seek track properly here, but will send "fake" newPos

			final long newPos = this.seekTrack(fc, seekRequest.offsetBytes, fileLength);

			final long fakeAverageBytesPerMs = 116; // ~116 bytes per ms in bensound-dubstep.flac
			final long fakeNewPos = seekRequest.ms * fakeAverageBytesPerMs;

			proto.sendSeekResult(fakeNewPos);
			return true;
		}
		return false;
	}

	/**
	 * THREADING: worker thread.
	 * @return new position within the track, or <0 on error
	 * */
	private long seekTrack(@NonNull final FileChannel channel, final long seekPosBytes, final long fileLength) {
		// Out seeking is simple as we just seek the FileChannel
		try {
			if(0 <= seekPosBytes) {
				channel.position(seekPosBytes);
			} else { // If seekPos < 0, this is a seek request from the end of the file
				channel.position(fileLength + seekPosBytes);
			}

			return channel.position();

		} catch(final IOException ex) {
			Log.e(ExampleProvider.TAG, "seekPosBytes=" + seekPosBytes, ex);
			return -1;
		}
	}

	/**
	 * For tracks available on the device as file, it's much easier to send direct file descriptor pointing to the file itself. The file descriptor is seekable
	 * and track can be reopened multiple times in this case, e.g. if tags, seek-wave, or album art scanning is needed.
	 */
	private ParcelFileDescriptor openViaDirectFD(@NonNull final String documentId, @NonNull final String filePath) throws FileNotFoundException {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openViaDirectFD documentId=" + documentId + " filePath=" + filePath);
		try {
			if(ExampleProvider.USE_MP3_COPY) {
				final File file = new File(this.getContext().getFilesDir(), filePath);
				return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			} else {
				final AssetFileDescriptor afd = this.getContext().getResources().getAssets().openFd(filePath);
				final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();
				Os.lseek(pfd.getFileDescriptor(), afd.getStartOffset(), OsConstants.SEEK_SET);
				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "openDocument afd.offset=" + afd.getStartOffset() + " len=" + afd.getLength() + " lseek=" + Os.lseek(pfd.getFileDescriptor(), 0, OsConstants.SEEK_CUR));
				return pfd;
			}
		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "documentId=" + documentId, th);
			throw new FileNotFoundException(documentId);
		}
	}

	/**
	 * Implementing CALL_GET_URL here to return dynamic URL for given track. Document uri is passed as string in arg
	 */
	@Override
	public Bundle call(final String method, final String arg, final Bundle extras) {
		final Bundle res = super.call(method, arg, extras);
		if(null == res) {
			if(TrackProviderConsts.CALL_GET_URL.equals(method)) {
				return this.handleGetUrl(arg, extras);

			} else if(TrackProviderConsts.CALL_RESCAN.equals(method)) {
				return this.handleRescan(arg, extras);

			} else if(TrackProviderConsts.CALL_GET_DIR_METADATA.equals(method)) {
				return this.handleGetDirMetadata(arg, extras);

			} else Log.e(ExampleProvider.TAG, "call bad method=" + method, new Exception());
		}
		return res;
	}

	/**
	 * Returns dynamic URL for given track. Document uri is passed as string in arg
	 * @return bundle with the appropriate data is expected, or null error
	 */
	private Bundle handleGetUrl(final String arg, final Bundle extras) {
		if(TextUtils.isEmpty(arg)) throw new IllegalArgumentException(arg);
		final Uri uri = Uri.parse(arg);
        this.enforceTree(uri);
		final String documentId = DocumentsContract.getDocumentId(uri);
		if(documentId.endsWith(ExampleProvider.DOCID_DYNAMIC_URL_SUFFIX)) {
			final boolean isDubstep = documentId.contains("dubstep");
			final Bundle res = new Bundle();
			final String url = isDubstep ? ExampleProvider.DUBSTEP_HTTP_URL : ExampleProvider.SUMMER_HTTP_URL;
			res.putString(TrackProviderConsts.COLUMN_URL, url);

			// Optionally add some headers to send with given url. These headers are used just once for this track playback and are not persisted
			res.putString(TrackProviderConsts.COLUMN_HEADERS, "Debug-header1: some\r\nDebug-header2: another\r\n");

			// Optionally add some cookies. These cookies are used just once for this track playback and are not persisted
			res.putString(TrackProviderConsts.COLUMN_COOKIES, "cookie1=value1; Secure\\ncookie2=value; SameSite=Strict");

			// Optionally set some http method. By default it's GET, setting GET here for the demonstration purpose
			res.putString(TrackProviderConsts.COLUMN_HTTP_METHOD, "GET");

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "call CALL_GET_URL url=>" + url);
			return res;
		} else Log.e(ExampleProvider.TAG, "call CALL_GET_URL bad documentId=" + documentId, new Exception());
		return null;
	}

	/**
	 * Provider is informed regarding the automatic or user initiated scan.<br>
	 * This is called prior Poweramp calls any {@link android.provider.DocumentsProvider#queryChildDocuments} and other methods to rescan
	 * the actual files hierarchy and metadata.<br><br>
	 *
	 * Depending on ths passed arguments, provider may do some refresh on data - if absolutely needed. Poweramp (and user) waits until this
	 * method returns, so in most cases, it should return as soon as possible.<br><br>
	 *
	 * If we're sending the rescan intent from this app (see {@link MainActivity#sendScanThisSubdir(View)}), we're getting the extras
	 * we sent.
	 */
	private Bundle handleRescan(final String arg, final Bundle extras) {
		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleRescan extras=" + ExampleProvider.dumpBundle(extras));

		// Analyze optional EXTRA_PROVIDER and EXTRA_PATH here.
		// If EXTRA_PROVIDER matches this provider, we may update the cached remote data
		final String targetProvider = extras.getString(PowerampAPI.Scanner.EXTRA_PROVIDER);

		if(TextUtils.equals(targetProvider, this.getContext().getPackageName())) { // Assuming authority == package name for this provider

			final String path = extras.getString(PowerampAPI.Scanner.EXTRA_PATH);

			if(!TextUtils.isEmpty(path)) {

				// - update data just for the EXTRA_PATH sub-directory hierarchy

				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleRescan targeted rescan path=" + path);

			} else {

				// - update data from the remote server

				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleRescan targeted provider rescan");

			}
		} else //noinspection StatementWithEmptyBody
			if(TextUtils.isEmpty(targetProvider)) {

			// This is non-targeted scan request:
			// - from the Poweramp UI for categories outside this provider folders
			// - from the Poweramp Settings, including Full Rescan
			// We can ignore those, or we can handle specific cases, e.g. we may want to reload everything from the remote server if this is a Full Rescan
			// (Poweramp indicates this with both EXTRA_ERASE_TAGS and EXTRA_FULL_RESCAN set)

			if(extras.getBoolean(PowerampAPI.Scanner.EXTRA_ERASE_TAGS) && extras.getBoolean(PowerampAPI.Scanner.EXTRA_FULL_RESCAN)) {

				// - force update all data

				if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleRescan forced full update");

			}

		} else {
			// This is targeted to some other provider, ignore
		}

		return null;
	}

	/**
	 * @param arg the directory uri
	 * @return bundle filled with extras: {@link TrackProviderConsts#EXTRA_ANCESTORS}
	 */
	private Bundle handleGetDirMetadata(final String arg, final Bundle extras) {
		final Uri uri = Uri.parse(arg);
        this.enforceTree(uri);

		// DocumentId for the directory which hierarchy (up to the root) Poweramp wants to retrieve
		final String docId = DocumentsContract.getDocumentId(uri);

		// We should return array of ancestor documentIds for given documentId from root to the direct parent of the given documentId

		// As for this provider, we have full path information in the document id itself, we just provide at as is.
		// Real provider may additionally specifically process the directory docId as needed to generate the valid hierarchy path

		final String[] segments = docId.split("/");

		if(1 >= segments.length) { // If we have just one segment, this is a root documentId and no parents exist
			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleGetDirMetadata IGNORE ROOT docId=" + docId + " uri=" + uri);
			return Bundle.EMPTY;
		}

		final StringBuilder sb = new StringBuilder();
		final String[] ancestors = new String[segments.length - 1]; // We want to return all ancestor document ids from root to parent dir, not including the dir in question
		for(int i = 0; i < ancestors.length; i++) {
			sb.append(segments[i]);
			ancestors[i] = sb.toString(); // Return full documentId for each ancestor
			sb.append('/');
		}

		final Bundle res =  new Bundle();
		res.putStringArray(TrackProviderConsts.EXTRA_ANCESTORS, ancestors);

		if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "handleGetDirMetadata uri=" + uri + " docId=" + docId + " ancestors=" + Arrays.toString(ancestors) + " res=" + ExampleProvider.dumpBundle(res));
		return res;
	}


	/** We need to override this DocumentProvider API method, which is called by super class to check if given documentId is a correct child of parentDocumentId */
	@Override
	public boolean isChildDocument(final String parentDocumentId, final String documentId) {
		try {
			// As our hierarchy is defined by assets/, we could just return true here, but for a sake of example, let's verify that given documentId is inside the folder
			// This is track, we randomly generate track entries, so we can't verify them
			final boolean res = documentId.endsWith(".mp3") || documentId.endsWith(".m3u8") || documentId.endsWith(ExampleProvider.DOCID_STATIC_URL_SUFFIX)
					|| documentId.startsWith(parentDocumentId) && this.isAssetDir(this.getContext().getResources().getAssets(), documentId);

			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "isChildDocument =>" + res + " parentDocumentId=" + parentDocumentId + " documentId=" + documentId);

			return res;
		} catch(final Throwable th) {
			Log.e(ExampleProvider.TAG, "parentDocumentId=" + parentDocumentId + " documentId=" + documentId, th);
		}
		return false;
	}

	/**
	 * Very inefficient way of checking folders vs files in assets, but Android SDK doesn't provide anything better.
	 * This shouldn't be used in production providers anyway
	 */
	private boolean isAssetDir(final AssetManager assets, @NonNull final String path) {
		// Ignore empty.txt
		return !path.endsWith("/empty.txt");
	}

	private long getAssetFileSize(final AssetManager assets, final String path) {
		try {
			try(final AssetFileDescriptor afd = assets.openFd(path)) {
				return afd.getLength();
			}
		} catch(final IOException ex) {
			return 0L;
		}
	}

	private static String[] resolveRootProjection(final String[] projection) {
		return null != projection ? projection : ExampleProvider.DEFAULT_ROOT_PROJECTION;
	}

	private static String[] resolveDocumentProjection(final String[] projection) {
		return null != projection ? projection : ExampleProvider.DEFAULT_DOCUMENT_PROJECTION;
	}

	private static String[] resolveTrackProjection(final String[] projection) {
		return null != projection ? projection : ExampleProvider.DEFAULT_TRACK_AND_METADATA_PROJECTION;
	}

	/** Assumes docId is track path, thus we can extract digits after - and before .mp3, e.g. trackNum = 10 for dubstep-10.mp3 */
	private static int extractTrackNum(final String docId) {
		final int startIx = docId.lastIndexOf('-');
		final int endIx = docId.lastIndexOf('.');
		if(0 > startIx || 0 > endIx || startIx >= endIx) {
			return 0;
		}
		try {
			return Integer.parseInt(docId.substring(startIx + 1, endIx), 10);
		} catch(final NumberFormatException ex) {
			Log.e(ExampleProvider.TAG, "docId=" + docId, ex);
		}
		return 0;
	}

	/**
	 * Returns last segment of the path before last /, or the path itself
	 * NOTE: for /foo/bar/file.mp3 it returns file.mp3, but for /foo/bar or /foo/bar/ it returns bar, so it assumes folder path, not any path
	 * @param path should be a _folder_ path
	 */
    @SuppressWarnings("null")
    @NonNull
    private static String getShortDirName(@NonNull final String path) {
		int ix = path.lastIndexOf('/');
		if(-1 == ix) {
			return path;
		}
		int end = path.length();
		if(ix == end - 1) {
			end--;
			ix = path.lastIndexOf('/', end - 1);
		}
		// if ix== -1 it will be 0 in substring (-1 + 1)
		return path.substring(ix + 1, end);
	}

	/**
	 * @return last segment of the path (after the last /), this is usually filename with extension, or the path itself if no slashes
	 */
    @SuppressWarnings("null")
    @NonNull
    private static String getShortName(@NonNull final String path) {
		int ix = path.lastIndexOf('/'); //
		if(-1 == ix) {
			ix = path.lastIndexOf('\\');
		}
		if(-1 == ix) {
			return path;
		}
		return path.substring(ix + 1);
	}

	/** To provide direct file descriptors, we need to have tracks extracted to local filesystem files */
	private void copyAsset(final String assetFile, final File targetDir, final boolean overwrite) {
		final File outFile = new File(targetDir, ExampleProvider.getShortName(assetFile));
		if(!overwrite && outFile.exists()) {
			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "copyAsset IGNORE exists assetFile=" + assetFile + " =>" + outFile);
			return;
		}
		try {
			try(final InputStream in = this.getContext().getResources().getAssets().open(assetFile)) {
				try(final OutputStream out = new FileOutputStream(outFile)) {
					final byte[] buffer = new byte[16 * 1024];
					int read;
					while(-1 != (read = in.read(buffer))){
						out.write(buffer, 0, read);
					}
				}
			}
			if(ExampleProvider.LOG) Log.w(ExampleProvider.TAG, "copyAsset assetFile=" + assetFile + " =>" + outFile);
		} catch(final IOException ex) {
			Log.e(ExampleProvider.TAG, "", ex);
		}
	}

	private <T> boolean arrayContains(@NonNull final T[] array, final T needle) {
		for(final T item : array) {
			if(Objects.equals(item, needle)) {
				//if(LOG) Log.w(TAG, "arrayContains FOUND needle=" + needle);
				return true;
			}
		}
		//if(LOG) Log.w(TAG, "arrayContains FAILED needle=" + needle + " array=" + Arrays.toString(array));
		return false;
	}

	@Nullable
    private String capitalize(@Nullable final String documentId) {
		if(null != documentId && 0 < documentId.length()) {
			return Character.toUpperCase(documentId.charAt(0)) + documentId.substring(1);
		}
		return documentId;
	}

	@SuppressLint("NewApi")
	private void enforceTree(final Uri documentUri) {
		if(DocumentsContract.isTreeUri(documentUri)) { // Exists in SDK=21, but hidden there
			String parent = DocumentsContract.getTreeDocumentId(documentUri);
			String child = DocumentsContract.getDocumentId(documentUri);
			if (Objects.equals(parent, child)) {
				return;
			}
			if (!this.isChildDocument(parent, child)) {
				throw new SecurityException(
						"Document " + child + " is not a descendant of " + parent);
			}
		}
	}

	private static void closeSilently(@Nullable final Closeable c) {
		if(null != c) {
			try {
				c.close();
			} catch(final IOException ex) {
				Log.e(ExampleProvider.TAG, "", ex);
			}
		}
	}

	@SuppressWarnings("null")
    @NonNull
    private static String dumpBundle(@Nullable final Bundle bundle) {
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
}
