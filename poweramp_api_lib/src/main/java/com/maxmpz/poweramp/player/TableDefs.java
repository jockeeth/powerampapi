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

import org.eclipse.jdt.annotation.NonNull;

public interface TableDefs {
	/**
	 * Alias used for category. Useful when query is actually a multi table join
	 */
	@NonNull String CATEGORY_ALIAS = "cat";

	/** 
	 * Id used for all "unknown" categories. Also see {@link PowerampAPI#NO_ID}
	 */
    long UNKNOWN_ID = 1000L;
	
	/**
	 * Alias used for category aliased table _id
	 */
	@NonNull String CATEGORY_ALIAS_ID = TableDefs.CATEGORY_ALIAS + "._id";

	/**
	 * Tracks
	 */
    interface Files {
		/** Special value for {@link #TRACK_NUMBER} - means no valid track number exists for the given track */
        int INVALID_TRACK_NUMBER = 10000;

		@NonNull String TABLE = "folder_files";

		@NonNull String _ID = Files.TABLE + "._id";

		/**
		 * Short filename<br>
		 * TEXT
		 */
		@NonNull String NAME = Files.TABLE + ".name";

		/**
		 * Track number extracted from tag or filename. May include disc number (if >= 2) as thousands (2001, 2002, etc.). Can be {@link #INVALID_TRACK_NUMBER}.<br>
		 * Used for sorting<br>
		 * INTEGER
		 */
		@NonNull String TRACK_NUMBER = "track_number";

		/**
		 * Track number for display purposes (since 858), prior 858 just track number from track tags. 0 or NULL if no track tag exists.<br> 
		 * Never includes disc number (since 858)<br>
		 * INTEGER
		 */
		@NonNull String TRACK_TAG = "track_tag";
		
		/**
		 * Track disc or 0 if no such tag exists<br>
		 * INTEGER 
		 * @since 859<br>
		 */
		@NonNull String DISC = "disc";

		/**
		 * Track name without number. For streams - this is name of stream, if available<br>
		 * TEXT
		 */
		@NonNull String NAME_WITHOUT_NUMBER = "name_without_number";

		/**
		 * One of the TAG_* constants<br>
		 * INTEGER
		 * @see com.maxmpz.poweramp.player.PowerampAPI.Track.TagStatus
		 */
		@NonNull String TAG_STATUS = "tag_status";

		/**
		 * Parent folder id<br>
		 * INTEGER
		 */
		@NonNull String FOLDER_ID = "folder_id";

		/**
		 * Title tag<br>
		 * NOTE: important to have it w/o table name for the header-enabled compound selects<br>
		 * TEXT
		 */
		@NonNull String TITLE_TAG = "title_tag";

		/**
		 * NOTE: non-null for streams only<br>
		 * TEXT
		 */
		@NonNull String ALBUM_TAG = "album_tag";

		/**
		 * NOTE: non-null for streams only<br>
		 * TEXT
		 */
		@NonNull String ARTIST_TAG = "artist_tag";

		/**
		 * Duration in milliseconds<br>
		 * INTEGER
		 */
		@NonNull String DURATION = Files.TABLE + ".duration";

		/**
		 * Time of update in epoch seconds<br>
		 * INTEGER
		 */
		@NonNull String UPDATED_AT = Files.TABLE + ".updated_at";

		/**
		 * One of the file types - {@link PowerampAPI.Track.FileType}<br>
		 */
		@NonNull String FILE_TYPE = "file_type";

		/**
		 * Milliseconds, updated when track started<br>
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Files.TABLE + ".played_at";

		/**
		 * Milliseconds, updated with play start time (=played_at) when and only if track is counted as played<br>
		 * INTEGER
		 * @since 900
		 */
		@NonNull String PLAYED_FULLY_AT = Files.TABLE + ".played_fully_at";

		/**
		 * This is the file last modified time - mtime (for the most Android variants).<br>
		 * The naming "file_created_at" is a legacy.<br>
		 * Seconds<br>
		 * INTEGER
		 */
		@NonNull String FILE_CREATED_AT = "file_created_at";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Files.TABLE + ".aa_status";

		/**
		 * INTEGER
		 */
		@NonNull String RATING = "rating";

		/**
		 * INTEGER
		 */
		@NonNull String PLAYED_TIMES = Files.TABLE + ".played_times";

		/**
		 * INTEGER
		 */
		@NonNull String ALBUM_ID = Files.TABLE + ".album_id";

		/**
		 * INTEGER
		 */
		@NonNull String ARTIST_ID = Files.TABLE + ".artist_id";

		/**
		 * INTEGER
		 */
		@NonNull String ALBUM_ARTIST_ID = Files.TABLE + ".album_artist_id";

		/**
		 * INTEGER
		 */
		@NonNull String COMPOSER_ID = Files.TABLE + ".composer_id";

		/**
		 * INTEGER
		 */
		@NonNull String YEAR = Files.TABLE + ".year";

		/**
		 * Cue offset milliseconds<br>
		 * INTEGER
		 */
		@NonNull String OFFSET_MS = Files.TABLE + ".offset_ms";

		/**
		 * If non-null - this is cue "source" (big uncut image) file with that given virtual folder id<br>
		 * NOTE: enforces 1-1 between source files and cues. No multiple cues per single image thus possible<br>
		 * INTEGER
		 */
		@NonNull String CUE_FOLDER_ID = "cue_folder_id";

		/**
		 * First seen time<br>
		 * Seconds<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Files.TABLE + ".created_at";

		/**
		 * Wave scan data<br>
		 * byte[] blob, nullable
		 */
		@NonNull String WAVE = "wave";
		
		/**
		 * TEXT
		 */
		@NonNull String META = Files.TABLE + ".meta";
		
		/**
		 * Last played position in milliseconds<br>
		 * INTEGER
		 */
		@NonNull String LAST_POS = "last_pos";

		/**
		 * INTEGER
		 */
		@NonNull String SHUFFLE_ORDER = Files.TABLE + ".shuffle_order";
		
		/**
		 * 1 if this item (most probably stream track) was manually added and shouldn't be removed by rescans<br>
		 * INTEGER (boolean)
		 * @since 857<br>
		 */
		@NonNull String USER_ADDED = Files.TABLE + ".user_added";
		
		/**
		 * Optional http(s) URL pointing to the target track. If exists, this will be used
		 * instead of the path (== folder.path + file_name.name)<br>
		 * Can be {@link TrackProviderConsts#DYNAMIC_URL}<br> 
		 * TEXT
		 */
		@NonNull String URL = Files.TABLE + ".url";
		
		/**
		 * Optional full path to the file. This file_path always overrides parent folder path + filename. Used for the cases
		 * when actual file path can't be derived from the parent folder due to non-hierarchical or opaque paths,
		 * e.g. for tracks providers.<br>
		 * Path is in Poweramp internal path format<br>
		 * TEXT
		 * @since 862
		 */
		@NonNull String FILE_PATH = Files.TABLE + ".file_path";
		
		/**
		 * Optional bitrate of the file. Currently set only for the provider tracks if provided in the appropiate metadata<br>
		 * INTEGER
		 * @since 862
		 */
		@NonNull String BIT_RATE = Files.TABLE + ".bit_rate";

		/**
		 * Full path. Works only if the query is joined with the folders, otherwise this may fail<br>
		 * TEXT
		 */
		@NonNull String FULL_PATH = "COALESCE(" + Files.FILE_PATH + ","+ Folders.PATH + "||" + Files.NAME + "," + Files.NAME + ")";
		
		/**
		 * Alternative track number. Currently applied only in Folders/Folders Hierarchy files for track number sorting. May differ for provider tracks, equals track_number for
		 * all other tracks<br>
		 * INTEGER
		 */
		@NonNull String TRACK_NUMBER_ALT = "track_number_alt";


		/**
		 * @deprecated
		 * @see com.maxmpz.poweramp.player.PowerampAPI.Track.TagStatus#TAG_NOT_SCANNED
		 */
		@Deprecated
        int TAG_NOT_SCANNED = PowerampAPI.Track.TagStatus.TAG_NOT_SCANNED;

		/**
		 * @deprecated
		 * @see com.maxmpz.poweramp.player.PowerampAPI.Track.TagStatus#TAG_SCANNED
		 */
		@Deprecated
        int TAG_SCANNED = PowerampAPI.Track.TagStatus.TAG_SCANNED;


		/**
		 * If non-NULL, references lrc_files entry in {@link LrcFiles}
		 * INTEGER NULL
		 * @since 948
		 */
		@NonNull String LRC_FILES_ID = "lrc_files_id";

		/**
		 * If non-NULL, defines priority for {@link #LRC_FILES_ID}. The higher value is the higher priority.
		 * LRC priority is used to support multiple possible sources of the LRC file for the track and
		 * reduce the scope of the LRC resolition against files.<br>
		 * INTEGER NOT NULL DEFAULT 0
		 * @since 948
		 */
		@NonNull String LRC_FILES_PRIO = "lrc_files_prio";

		/**
		 * 1 if this track is known to have lyrics tag (not necessarily a synchronized lyrics)<br>
		 * INTEGER NOT NULL (boolean)
		 * @since 948
		 */
		@NonNull String HAS_LYRICS_TAG = "has_lyrics_tag";

		/**
		 * If non-NULL, references cached lyrics entry in {@link CachedLyrics}
		 * INTEGER NULL
		 * @since 948
		 */
		@NonNull String CACHED_LYRICS_ID = "cached_lyrics_id";

		/**
		 * Cached lyrics loading start timestamp<br>
		 * Milliseconds in System.currentTimeMillis timebase<br>
		 * NULL if loading is not started yet or loading is complete<br>
		 * INTEGER NULL
		 * @since 948
		 */
		@NonNull String CACHED_LYRICS_LOADING_STARTED_AT = "cached_lyrics_loading_started_at";

		/**
		 * Calculated field<br>
		 * INTEGER (boolean)
		 */
		@NonNull String HAS_LYRICS =
			// NOTE: avoid matching cached_lyrics_id for stream as stream constantly changes metadata
			"(has_lyrics_tag OR lrc_files_id IS NOT NULL" +
				" OR cached_lyrics_id IS NOT NULL AND cached_lyrics_loading_started_at IS NULL AND file_type!=" + PowerampAPI.Track.FileType.TYPE_STREAM +
               ") AS _has_lyrics";
	}

	/**
	 * Contains the single track entry when/if some path is requested to be played and that path is not in Poweramp Music Folders/Library.<br>
	 * @since 949 this is always a structural copy of folder_files table (with just that one _id={@link PowerampAPI#RAW_TRACK_ID} (-2) entry)
	 */
    interface RawFiles extends Files {
		@NonNull String TABLE = "raw_files";
	}

	/** All known Poweramp folders */
    interface Folders {
		@NonNull String TABLE = "folders";

		@NonNull String _ID = Folders.TABLE + "._id";

		/**
		 * Display (label) name of the folder. Can be long for roots (e.g. can include storage description).<br>
		 * May or may not match actual filesystem folder name<br> 
		 * TEXT
		 */
		@NonNull String NAME = Folders.TABLE + ".name";

		/**
		 * (Always) short name of the folder. Always matches actual filesystem folder name<br>
		 * For CUE - name of the file (either CUE or the target track with the embedded CUE)
		 * TEXT
		 * @since 828
		 */
		@NonNull String SHORT_NAME = Folders.TABLE + ".short_name";

		/**
		 * Short path of the parent folder. Always matches parent short_name which is parent actual filesystem folder name<br>
		 * TEXT
		 */
		@NonNull String PARENT_NAME = Folders.TABLE + ".parent_name";

		/**
		 * Parent folder display label (which can be much longer than just PARENT_NAME, e.g. include storage description) to display in the UI.<br>
		 * Corresponds to parent name<br>
		 * TEXT
		 */
		@NonNull String PARENT_LABEL = Folders.TABLE + ".parent_label";

		/**
		 * Full path of the folder<br>
		 * NOTE: avoid TABLE name here to allow using field in raw_files. "path" is (almost) unique column, also used in playlists<br>
		 * The path has a trailing /
		 * TEXT
		 */
		@NonNull String PATH = "path";

		/**
		 * This is the same as path for usual folders, but for the cue virtual folders, this is path + name<br>
		 * Used for proper folders/subfolders hiearachy sorting and it's ciritcal for correct hieararchy playing/reshuffle<br>
		 * TEXT
		 */
		@NonNull String SORT_PATH = "sort_path";

		/**
		 * Folder album art/thumb image (short name)<br>
		 * TEXT
		 */
		@NonNull String THUMB = "thumb";

		/**
		 * INTEGER
		 */
		@NonNull String DIR_MODIFIED_AT = Folders.TABLE + ".dir_modified_at";

		/**
		 * Seconds<br>
		 * INTEGER
		 */
		@NonNull String UPDATED_AT = Folders.TABLE + ".updated_at";

		/**
		 * Id of the parent folder or 0 for "root" folders<br>
		 * INTEGER
		 */
		@NonNull String PARENT_ID = Folders.TABLE + ".parent_id";

		/**
		 * INTEGER
		 */
		@NonNull String IS_CUE = Folders.TABLE + ".is_cue";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Folders.TABLE + ".created_at";

		/**
		 * Number of direct child subfolders<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_SUBFOLDERS = Folders.TABLE + ".num_subfolders";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Folders.TABLE + ".num_files";

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_ALL_FILES = Folders.TABLE + ".num_all_files";
		
		/**
		 * Number of tracks in the whole folder hierarchy, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829
		 */
		@NonNull String HIER_NUM_FILES = Folders.TABLE + ".hier_num_files";

		/**
		 * Duration in milliseconds for the tracks inside this folder only, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = Folders.TABLE + ".duration";
		
		/**
		 * Duration in milliseconds for the whole hierarchy inside this folder, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String HIER_DURATION = Folders.TABLE + ".hier_duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = Folders.TABLE + ".dur_meta";

		/**
		 * Hierarchy duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String HIER_DUR_META = Folders.TABLE + ".hier_dur_meta";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Folders.TABLE + ".aa_status";

		/**
		 * If 1 (true), this folder restores last played track<br>
		 * INTEGER (boolean)
		 * @since 821<br>
		 */
		@NonNull String KEEP_LIST_POS = Folders.TABLE + ".keep_list_pos"; // Sync with RestLibraryListMemorizable

		/**
		 * If 1 (true), this folder restores last played track position<br>
		 * INTEGER (boolean)
		 * @since 821<br>
		 */
		@NonNull String KEEP_TRACK_POS = Folders.TABLE + ".keep_track_pos"; // Sync with RestLibraryListMemorizable
		
		@NonNull String KEEP_LIST_AND_TRACK_POS_COMBINED = "(" + Folders.KEEP_TRACK_POS + "<<1)+" + Folders.KEEP_LIST_POS;
		
		/**
		 * Non-null for provider folders, where provider wants to control default sorting order in Folders Hierarchy<br>
		 * INTEGER<br>
		 * @since 869
		 */
		@NonNull String SORT_ORDER = "sort_order";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Folders.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of tracks in the whole folder hierarchy, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829
		 * @deprecated since 864. {@link #HIER_NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String HIER_NUM_ALL_FILES = Folders.TABLE + ".hier_num_all_files";

		/**
		 * Duration in milliseconds for the tracks inside this folder only, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864. {@link #DURATION} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Folders.TABLE + ".duration_all";

		/**
		 * Hierarchy duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String HIER_DUR_ALL_META = Folders.TABLE + ".hier_dur_all_meta";

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864. {@link #DUR_META} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Folders.TABLE + ".dur_all_meta";

		/**
		 * Duration in milliseconds for the whole hierarchy inside this folder, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String HIER_DURATION_ALL = Folders.TABLE + ".hier_duration_all";

		/**
		 * Calculated subquery column which retrieves parent name<br>
		 * TEXT
		 * @deprecated use {@link #PARENT_LABEL}
		 */
		@Deprecated
        @NonNull String PARENT_NAME_SUBQUERY = "(SELECT name FROM folders AS f2 WHERE f2._id=folders.parent_id) AS parent_name_subquery";
		
		/**
		 * Calculated subquery column which retrieves short parent name<br>
		 * TEXT
		 * @deprecated use {@link #PARENT_NAME} 
		 */
		@Deprecated
        @NonNull String PARENT_SHORT_NAME_SUBQUERY = "(SELECT short_name FROM folders AS f2 WHERE f2._id=folders.parent_id) AS parent_short_name_subquery";
	}


	interface Albums {
		@NonNull String TABLE = "albums";

		@NonNull String _ID = Albums.TABLE + "._id";

		/**
		 * NOTE: important to have it w/o table for headers-enabled compound selects<br>
		 * TEXT
		 */
		@NonNull String ALBUM = "album";

		/**
		 * NOTE: important to have it w/o table for headers-enabled compound selects<br>
		 * TEXT
		 */
		@NonNull String ALBUM_SORT = "album_sort";

		/**
		 * NOTE: this is NULL for Unknown album, so not all joins are possible with just albums + album_artists (use folder_files for joins)<br>
		 * INTEGER
		 */
		@NonNull String ALBUM_ARTIST_ID = Albums.TABLE + ".album_artist_id";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Albums.TABLE + ".created_at";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Albums.TABLE + ".num_files";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Albums.TABLE + ".aa_status";
		
		/**
		 * The guessed album year<br>
		 * INTEGER
		 */
		@NonNull String ALBUM_YEAR = "album_year";
		
		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = Albums.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = Albums.TABLE + ".dur_meta";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Albums.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTRGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Albums.TABLE + ".num_all_files";

		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Albums.TABLE + ".duration_all";

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Albums.TABLE + ".dur_all_meta";
	}


	interface Artists {
		@NonNull String TABLE = "artists";

		@NonNull String _ID = Artists.TABLE + "._id";

		/**
		 * TEXT
		 */
		@NonNull String ARTIST = "artist";

		/**
		 * TEXT
		 */
		@NonNull String ARTIST_SORT = "artist_sort";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Artists.TABLE + ".created_at";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Artists.TABLE + ".aa_status";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Artists.TABLE + ".num_files";

		
		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = Artists.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = Artists.TABLE + ".dur_meta";
		
		/**
		 * If true (1) this is unsplit combined multi-artist<br>
		 * INTEGER (boolean)
		 * @since 899<br>
		 */
		@NonNull String IS_UNSPLIT = Artists.TABLE + ".is_unsplit";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Artists.TABLE + ".played_at";

		// Deprecated
		
		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Artists.TABLE + ".num_all_files";

		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Artists.TABLE + ".duration_all";

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Artists.TABLE + ".dur_all_meta";
	}

	/** 
	 * One-to-many relation table for track artists<br> 
	 * Always used. First entry may be the UNKNOWN_ID or an unsplit artist
	 * @since 899
	 */
    interface MultiArtists {
		@NonNull String TABLE = "multi_artists";

		@NonNull String _ID = MultiArtists.TABLE + "._id";
		@NonNull String ARTIST_ID = MultiArtists.TABLE + ".artist_id";
		@NonNull String FILE_ID = MultiArtists.TABLE + ".file_id";
	}

	/** This is similar to Artists, but uses Album Artist tag, where available */
    interface AlbumArtists {
		@NonNull String TABLE = "album_artists";

		@NonNull String _ID = AlbumArtists.TABLE + "._id";

		/**
		 * TEXT
		 */
		@NonNull String ALBUM_ARTIST = "album_artist";

		/**
		 * TEXT
		 */
		@NonNull String ALBUM_ARTIST_SORT = "album_artist_sort";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = AlbumArtists.TABLE + ".created_at";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = AlbumArtists.TABLE + ".aa_status";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = AlbumArtists.TABLE + ".num_files";

		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = AlbumArtists.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = AlbumArtists.TABLE + ".dur_meta";
		
		/**
		 * If true (1) this is combined unsplit multi-artist<br>
		 * INTEGER (boolean)
		 * @since 899<br>
		 */
		@NonNull String IS_UNSPLIT = AlbumArtists.TABLE + ".is_unsplit";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = AlbumArtists.TABLE + ".played_at";

		// Deprecated fields
		
		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = AlbumArtists.TABLE + ".dur_all_meta";
		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829
		 * @deprecated since 864
		 */

		@Deprecated
        @NonNull String DURATION_ALL = AlbumArtists.TABLE + ".duration_all";
		
		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = AlbumArtists.TABLE + ".num_all_files";
	}

	/** 
	 * One-to-many relation table for track album artists<br> 
	 * Always used. First entry may be the UNKNOWN_ID or an unsplit album artist
	 * @since 899
	 * */
    interface MultiAlbumArtists {
		@NonNull String TABLE = "multi_album_artists";

		@NonNull String _ID = MultiAlbumArtists.TABLE + "._id";
		@NonNull String ALBUM_ARTIST_ID = MultiAlbumArtists.TABLE + ".album_artist_id";
		@NonNull String FILE_ID = MultiAlbumArtists.TABLE + ".file_id";
	}

	/**
	 * Album => artist 1:1 binding table<br>
	 * Used for Albums by Artist category, where can be multiple same Album repeated per each Artist
	 */
    interface AlbumsByArtist {
		@NonNull String TABLE = "artist_albums";

		@NonNull String _ID = AlbumsByArtist.TABLE + "._id";

		/**
		 * INTEGER
		 */
		@NonNull String ARTIST_ID = AlbumsByArtist.TABLE + ".artist_id";

		/**
		 * INTEGER
		 */
		@NonNull String ALBUM_ID = AlbumsByArtist.TABLE + ".album_id";

		/**
		 * First seen time<br>
		 * Seconds unix time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = AlbumsByArtist.TABLE + ".created_at";
		
		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = AlbumsByArtist.TABLE + ".num_files";

		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = AlbumsByArtist.TABLE + ".duration";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = AlbumsByArtist.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = AlbumsByArtist.TABLE + ".num_all_files";

		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = AlbumsByArtist.TABLE + ".duration_all";

		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = AlbumsByArtist.TABLE + ".dur_meta";
		
		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = AlbumsByArtist.TABLE + ".dur_all_meta";
	}


	interface Composers {
		@NonNull String TABLE = "composers";

		@NonNull String _ID = Composers.TABLE + "._id";

		/**
		 * TEXT
		 */
		@NonNull String COMPOSER = "composer";

		/**
		 * INTEGER
		 */
		@NonNull String COMPOSER_SORT = "composer_sort";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Composers.TABLE + ".created_at";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Composers.TABLE + ".aa_status";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Composers.TABLE + ".num_files";
		
		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = Composers.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = Composers.TABLE + ".dur_meta";
		
		/**
		 * If true (1) this is combined unsplit multi-composer<br>
		 * INTEGER (boolean)
		 * @since 899<br>
		 */
		@NonNull String IS_UNSPLIT = Composers.TABLE + ".is_unsplit";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Composers.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Composers.TABLE + ".num_all_files";
		
		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Composers.TABLE + ".duration_all";

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Composers.TABLE + ".dur_all_meta";
	}
	

	/** 
	 * One-to-many relation table for track album artists<br> 
	 * Always used. First entry may be the UNKNOWN_ID or an unsplit composer
	 * @since 899
	 * */
    interface MultiComposers {
		@NonNull String TABLE = "multi_composers";

		@NonNull String _ID = MultiComposers.TABLE + "._id";
		@NonNull String COMPOSER_ID = MultiComposers.TABLE + ".composer_id";
		@NonNull String FILE_ID = MultiComposers.TABLE + ".file_id";
	}
	

	interface Genres {
		@NonNull String TABLE = "genres";

		@NonNull String _ID = Genres.TABLE + "._id";

		/**
		 * TEXT
		 */
		@NonNull String GENRE = "genre";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Genres.TABLE + ".created_at";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Genres.TABLE + ".num_files";

		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 */
		@NonNull String DURATION = Genres.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 */
		@NonNull String DUR_META = Genres.TABLE + ".dur_meta";
		
		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Genres.TABLE + ".aa_status";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Genres.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Genres.TABLE + ".num_all_files";

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Genres.TABLE + ".dur_all_meta";

		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 829<br>
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Genres.TABLE + ".duration_all";

	}


	interface GenreEntries {
		@NonNull String TABLE = "genre_entries";

		@NonNull String _ID = GenreEntries.TABLE + "._id";

		/**
		 * Actual id of the file in folder_files table<br>
		 * INTEGER
		 */
		@NonNull String FOLDER_FILE_ID = "folder_file_id";

		/**
		 * Genre id<br>
		 * INTEGER
		 */
		@NonNull String GENRE_ID = "genre_id";
	}

	
	/** 
	 * @since 856
	 */
    interface Years {
		@NonNull String TABLE = "years";

		@NonNull String _ID = Years.TABLE + "._id";

		/**
		 * INTEGER
		 */
		@NonNull String YEAR = Years.TABLE + ".year";

		/**
		 * First seen time<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Years.TABLE + ".created_at";

		/**
		 * Number of tracks in this category, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Years.TABLE + ".num_files";

		/**
		 * Duration in milliseconds, excluding cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String DURATION = Years.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 */
		@NonNull String DUR_META = Years.TABLE + ".dur_meta";
		
		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Years.TABLE + ".aa_status";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Years.TABLE + ".played_at";

		// Deprecated

		/**
		 * Duration meta including cues<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DUR_ALL_META = Years.TABLE + ".dur_all_meta";

		/**
		 * Duration in milliseconds, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864
		 */
		@Deprecated
        @NonNull String DURATION_ALL = Years.TABLE + ".duration_all";

		/**
		 * Number of tracks in this category, including cue source images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Years.TABLE + ".num_all_files";
	}


	/** 
	 * Used for specific dynamic subcategories, such as Artist Albums, where data subset is dynamically generated, thus, no table for stats otherwise exist<br>
	 * TYPE + REF_ID is an unique index
	 * @since 863
	 */
    interface CatStats {
		@NonNull String TABLE = "cat_stats";

		@NonNull String _ID = CatStats.TABLE + "._id";

		/**
		 * Matches category numeric type ({@link PowerampAPI.Cats})<br>
		 * INTEGER
		 */
		@NonNull String TYPE = CatStats.TABLE + ".type";

		/**
		 * First referenced target id, e.g. genre _id<br>
		 * INTEGER
		 */
		@NonNull String REF_ID = CatStats.TABLE + ".ref_id";

		/**
		 * Second referenced target id, e.g. album _id<br>
		 * INTEGER
		 */
		@NonNull String REF_ID2 = CatStats.TABLE + ".ref_id2";

		/**
		 * Number of tracks in this category<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = CatStats.TABLE + ".num_files";

		/**
		 * Duration in milliseconds<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String DURATION = CatStats.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 */
		@NonNull String DUR_META = CatStats.TABLE + ".dur_meta";
	}

	
	interface PlaylistEntries {
		@NonNull String TABLE = "playlist_entries";

		@NonNull String _ID = PlaylistEntries.TABLE + "._id";

		/**
		 * Actual id of the file in folder_files table<br>
		 * INTEGER
		 */
		@NonNull String FOLDER_FILE_ID = "folder_file_id";

		/**
		 * Folder Playlist id<br>
		 * INTEGER
		 */
		@NonNull String PLAYLIST_ID = "playlist_id";

		/**
		 * Sort order<br>
		 * INTEGER
		 */
		@NonNull String SORT = "sort";
		
		/**
		 * Filename, the full or final part of the stream URL<br>
		 * TEXT
		 * @since 842<br>
		 */
		@NonNull String FILE_NAME = "file_name";
		
		/**
		 * Parent folder path (matching {@link Folders#PATH}, or base URL (or NULL) for streams<br>
		 * TEXT
		 * @since 842<br>
		 */
		@NonNull String FOLDER_PATH = "folder_path";
		
		/**
		 * Cue offset for .cue tracks<br>
		 * INTEGER
		 * @since 842<br>
		 */
		@NonNull String CUE_OFFSET_MS = PlaylistEntries.TABLE + ".cue_offset_ms";

		/**
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = PlaylistEntries.TABLE + ".played_at";

		/**
		 * INTEGER
		 */
		@NonNull String SHUFFLE_ORDER = PlaylistEntries.TABLE + ".shuffle_order";
	}


	interface Playlists {
		@NonNull String TABLE = "playlists";

		@NonNull String _ID = Playlists.TABLE + "._id";

		/**
		 * Name of the playlist<br>
		 * TEXT
		 */
		@NonNull String PLAYLIST = Playlists.TABLE + ".playlist";

		/**
		 * Updated to match file based playlist. Also updated on entry insert/reorder/deletion - for all playlists<br>
		 * INTEGER
		 */
		@NonNull String MTIME = Playlists.TABLE + ".mtime";

		/**
		 * TEXT
		 */
		@NonNull String PATH = Playlists.TABLE + ".playlist_path";

		/**
		 * Seconds<br>
		 * INTEGER
		 */
		@NonNull String CREATED_AT = Playlists.TABLE + ".created_at";

		/**
		 * Seconds<br>
		 * INTEGER
		 */
		@NonNull String UPDATED_AT = Playlists.TABLE + ".updated_at";

		/**
		 * Number of playlist entries<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 */
		@NonNull String NUM_FILES = Playlists.TABLE + ".num_files";

		/**
		 * Bitwise flag<br>
		 * INTEGER
		 */
		@NonNull String AA_STATUS = Playlists.TABLE + ".aa_status";
		
		/**
		 * INTEGER (boolean)
		 */
		@NonNull String KEEP_LIST_POS = Playlists.TABLE + ".keep_list_pos"; // Sync with RestLibraryListMemorizable

		/**
		 * INTEGER (boolean)
		 */
		@NonNull String KEEP_TRACK_POS = Playlists.TABLE + ".keep_track_pos"; // Sync with RestLibraryListMemorizable
		
		@NonNull String KEEP_LIST_AND_TRACK_POS_COMBINED = "(" + Playlists.KEEP_TRACK_POS + "<<1)+" + Playlists.KEEP_LIST_POS;
		
		/**
		 * Duration in milliseconds<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 826<br>
		 */
		@NonNull String DURATION = Playlists.TABLE + ".duration";
		
		/**
		 * Duration meta<br>
		 * Dynamically recalculated on rescans<br>
		 * TEXT
		 * @since 826<br>
		 */
		@NonNull String DUR_META = Playlists.TABLE + ".dur_meta";

		/**
		 * Calculated column
		 * INTEGER (boolean)
		 */
		@NonNull String IS_FILE = Playlists.TABLE + ".playlist_path IS NOT NULL AS _is_file";

		/**
		 * This is usually not updated, unless shuffled
		 * INTEGER
		 */
		@NonNull String PLAYED_AT = Playlists.TABLE + ".played_at";

		// Deprecated

		/**
		 * Number of playlist entries. Since 824 - same as num_files<br>
		 * Poweramp can insert CUE images into playlists if appropriate option enabled, or it skips them completely. In anycase, playlist should show # of all possible entries in it,
		 * without filtering for CUE images<br>
		 * Dynamically recalculated on rescans<br>
		 * INTEGER
		 * @since 796<br>
		 * @deprecated since 864. {@link #NUM_FILES} is now dynamically updated depending on "show cue images" preference
		 */
		@Deprecated
        @NonNull String NUM_ALL_FILES = Playlists.TABLE + ".num_all_files";
	}



	enum Queue {
        ;
        public static final @NonNull String TABLE = "queue";

		public static final @NonNull String _ID = Queue.TABLE + "._id";

		/**
		 * Folder file id<br>
		 * INTEGER
		 */
		public static final @NonNull String FOLDER_FILE_ID = Queue.TABLE + ".folder_file_id";

		/**
		 * Milliseconds<br>
		 * INTEGER
		 */
		public static final @NonNull String CREATED_AT = Queue.TABLE + ".created_at";

		/**
		 * INTEGER
		 */
		public static final @NonNull String SORT = Queue.TABLE + ".sort";

		/**
		 * INTEGER
		 */
		public static final @NonNull String SHUFFLE_ORDER = Queue.TABLE + ".shuffle_order";

		public static final @NonNull String CALC_PLAYED = "folder_files.played_at >= queue.created_at"; // If played at is the same as queue entry time, consider it played already 
		public static final @NonNull String CALC_UNPLAYED = "folder_files.played_at < queue.created_at";
	}

	/**
	 * @since 877
	 */
    enum Bookmarks {
        ;
        public static final @NonNull String TABLE = "bookmarks";

		public static final @NonNull String _ID = Bookmarks.TABLE + "._id";

		/**
		 * Folder file id<br>
		 * INTEGER
		 */
		public static final @NonNull String FOLDER_FILE_ID = Bookmarks.TABLE + ".folder_file_id";

		/**
		 * Milliseconds<br>
		 * INTEGER
		 */
		public static final @NonNull String OFFSET_MS = Bookmarks.TABLE + ".offset_ms";

		/**
		 * INTEGER
		 */
		public static final @NonNull String SORT = Bookmarks.TABLE + ".sort";

		/**
		 * TEXT
		 */
		public static final @NonNull String META = Bookmarks.TABLE + ".meta";

		/**
		 * Milliseconds<br>
		 * INTEGER
		 */
		public static final @NonNull String CREATED_AT = Bookmarks.TABLE + ".created_at";
	}

	/** Never used, to be removed */
	@Deprecated
    enum ShuffleSessionIds {
        ;
        public static final @NonNull String TABLE = "shuffle_session_ids";

		public static final @NonNull String _ID = ShuffleSessionIds.TABLE + "._id";
	}


	enum EqPresets {
        ;
        public static final @NonNull String TABLE = "eq_presets";

		public static final @NonNull String _ID = EqPresets.TABLE + "._id";

		/**
		 * If non-null - this is the predefined preset (see res/values/arrays/eq_preset_labels).<br>
		 * preset=0 defines the user default preset (the preset selected when no any named preset is selected),
		 * which is tehcnically built-in (provided by the app)
		 * INTEGER
		 */
		public static final @NonNull String PRESET = "preset";

		/**
		 * Eq preset string. Either this or data_blob is used for the preset data<br>
		 * The text version of data is supported for the graphic mode only (since 906)
		 * TEXT
		 */
		public static final @NonNull String _DATA = EqPresets.TABLE + "._data";

		/**
		 * Eq preset data. Either this or _data is used for the preset data, app always saves to DATA_BLOB.<br>
		 * To read user/built-in/AutoEq preset data properly, use {@link #RESOLVED_BLOB}, as DATA_BLOB
		 * is NULL for built-in/AutoEq presets until user changes the preset.<br>
		 * The blob data format supports both graphic and parametric modes<br>
		 * BLOB
		 * @since 906
		 */
		public static final @NonNull String DATA_BLOB = EqPresets.TABLE + ".data_blob";

		/**
		 * Default eq preset data for built-in and AutoEq presets.
		 * BLOB
		 * @since 960
		 */
		public static final @NonNull String DEFAULT_BLOB = EqPresets.TABLE + ".default_blob";

		/**
		 * Eq preset data resolved to either user changed data (if any) or the default preset data<br>
		 * BLOB
		 * @since 960
		 */
		public static final @NonNull String RESOLVED_BLOB = "COALESCE(" + EqPresets.DATA_BLOB + "," + EqPresets.DEFAULT_BLOB + ")";

		/**
		 * 1 if preset is parametric<br>
		 * INTEGER (boolean)
		 * @since 906
		 */
		public static final @NonNull String PARAMETRIC = "parametric";

		/**
		 * Preset name<br>
		 * TEXT
		 */
		public static final @NonNull String NAME = EqPresets.TABLE + ".name";

		/**
		 * Additional meta information, such as AutoEq category<br>
		 * TEXT
		 */
		public static final @NonNull String META = EqPresets.TABLE + ".meta";

		/**
		 * Updated automatically when name, _data, data_blob, or parametric fields are updated, other fields ignored<br>
		 * Seconds before 980, milliseconds for 980+<br>
		 * INTEGER
		 * @since 906
		 */
		public static final @NonNull String UPDATED_AT = EqPresets.TABLE + ".updated_at";

		/**
		 * BLOB
		 * @since 906
		 */
		public static final @NonNull String SHARE_BLOB = "share_blob";

		/**
		 * Seconds before 980, milliseconds for 980+<br>
		 * INTEGER
		 * @since 906
		 */
		public static final @NonNull String SHARE_BLOB_UPDATED_AT = "share_blob_updated_at";

		/**
		 * INTEGER<br>
		 * Defines type of preset: user, builtin, autoeq, etc.<br>
		 * NOTE: parametric/graphic behavior is defined by {@link #PARAMETRIC}
		 * @see EqPresetConsts
		 * @since 960
		 */
		public static final @NonNull String TYPE = EqPresets.TABLE + ".type";

		/**
		 * 1 if preset is bound to speaker, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_SPEAKER = "bind_to_speaker";

		/**
		 * 1 if preset is bound to wired headset, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_WIRED = "bind_to_wired";

		/**
		 * 1 if preset is bound to bluetooth audio output, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_BT = "bind_to_bt";

		/**
		 * 1 if preset is bound to USB audio output, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_USB = "bind_to_usb";

		/**
		 * 1 if preset is bound to other audio outputs, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_OTHER = "bind_to_other";

		/**
		 * 1 if preset is bound to chromecast output, 0 otherwise<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_CHROMECAST = "bind_to_cc";

		/**
		 * Baked up devices as ints string for the assigned named devices. Used as an optimization for the UI layer<br>
		 * TEXT<br>
		 * @since 963
		 */
		public static final @NonNull String META_BOUND_DEVICES = "meta_bound_devices";

		/**
		 * Baked up first assigned device and device name (device-device_name). Used as an optimization for the UI layer<br>
		 * TEXT<br>
		 * @since 963
		 */
		public static final @NonNull String META_BOUND_DEVICE_NAME = "meta_bound_device_name";

		/**
		 * Virtual field, used for insert/update contentValues.<br>
		 * If set to 1 preset is bound to track specified by {@link #BOUND_TRACK_ID}, if set to 0, preset is unbound from that track<br>
		 * NOTE: track related assignments required preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * INTEGER (boolean)<br>
		 * @since 856<br>
		 */
		public static final @NonNull String BIND_TO_TRACK = "__bind_to_track";

		/**
		 * Virtual field, used for insert/update contentValues.<br>
		 * Should be set to track id which should be bound/unbound with {@link #BIND_TO_TRACK}
		 * NOTE: track related assignents required preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * @since 856<br>  
		 * INTEGER
		 */
		public static final @NonNull String BOUND_TRACK_ID = "__bound_track_id";

		/**
		 * Virtual field, used for insert/update contentValues.<br>
		 * If set to 1 preset is bound to all category tracks specified by {@link #BOUND_CAT_URI}, if set to 0, preset is unbound from these tracks<br>
		 * NOTE: track related assignents required preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * @since 856<br>
		 * INTEGER (boolean)
		 */
		public static final @NonNull String BIND_TO_CAT = "__bind_to_cat";

		/**
		 * Virtual field, used for insert/update contentValues.<br>
		 * Should be set to category uri which should be bound/unbound with {@link #BIND_TO_CAT}<br>
		 * NOTE: track related assignents required preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * @since 856<br>
		 * TEXT  
		 */
		public static final @NonNull String BOUND_CAT_URI = "__bound_cat_uri";

		/**
		 * Virtual field, used for insert/update contentValues. If this is set, no other track bind/unbind assignments will be executed and preset is unbound from all tracks<br>
		 * NOTE: track related assignents required preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * INTEGER (boolean) 
		 */
		public static final @NonNull String UNBIND_FROM_ALL_TRACK_IDS = "__unbind_from_all_track_ids";
		
		/**
		 * Virtual field, used for insert/update contentValues. If this is set, no other device bind/unbind assignments will be executed and preset is unbound from all devices<br>
		 * @since 856<br>
		 * INTEGER (boolean) 
		 */
		public static final @NonNull String UNBIND_FROM_ALL_DEVICES = "__unbind_from_all_devices";
	
		/**
		 * Virtual field, used for insert/update contentValues. <br>
		 * If this is set to value > 0, * content values named {@link #BIND_TO_DEVICE_PREFIX}#, {@link #DEVICE_PREFIX}#, {@link #DEVICE_ADDRESS_PREFIX}#, {@link #DEVICE_NAME_PREFIX}#,
		 * will be checked and if all 4 are set to appropriate valid non-empty values, preset will be bound/unbound to that device according to the {@link #BIND_TO_DEVICE_PREFIX}#.<br>
		 * # is number [0, NUM_BIND_DEVICES)<br>
		 * @since 856
		 * INTEGER 
		 */
		public static final @NonNull String NUM_BIND_DEVICES = "__num_bind_devices";

		/**
		 * Prefix for virtual fields, used for insert/update contentValues.<br>
		 * Content values named BIND_TO_DEVICE_PREFIX, {@link #DEVICE_PREFIX}#, {@link #DEVICE_ADDRESS_PREFIX}#, {@link #DEVICE_NAME_PREFIX}#,
		 * will be checked and if all set to appropriate valid values, preset will be bound/unmound to the device.
		 * # is number [0, NUM_BIND_DEVICES)<br>
		 * @since 856<br>
		 * INTEGER (boolean) 
		 */
		public static final @NonNull String BIND_TO_DEVICE_PREFIX = "__bind_to_device_";

		/**
		 * Prefix for virtual fields, used for insert/update contentValues.<br>
		 * Sets device type for device assignment. See {@link RouterConsts} DEVICE_* constants<br>
		 * Content values named {@link #BIND_TO_DEVICE_PREFIX}#, DEVICE_PREFIX, {@link #DEVICE_ADDRESS_PREFIX}#, {@link #DEVICE_NAME_PREFIX}#,
		 * will be checked and if all set to appropriate valid values, preset will be bound/unmound to the device.
		 * # is number [0, NUM_BIND_DEVICES)<br>
		 * @since 856<br>
		 * INTEGER 
		 */
		public static final @NonNull String DEVICE_PREFIX = "__device_";
		
		/**
		 * Prefix for virtual fields, used for insert/update contentValues. Sets device address for given device assignment. Device address may match device name,
		 * but for BT / Chromecast device this is usually mac address or some other unique identifier.<br>
		 * Content values named {@link #BIND_TO_DEVICE_PREFIX}#, {@link #DEVICE_PREFIX}#, DEVICE_ADDRESS_PREFIX, {@link #DEVICE_NAME_PREFIX}#,
		 * will be checked and if all set to appropriate valid values, preset will be bound/unmound to the device.
		 * # is number [0, NUM_BIND_DEVICES)<br>
		 * @since 856<br>
		 * TEXT 
		 */
		public static final @NonNull String DEVICE_ADDRESS_PREFIX = "__device_address_";
		
		/**
		 * Prefix for virtual fields, used for insert/update contentValues. Sets visible device name for given device assignment.<br> 
		 * Content values named {@link #BIND_TO_DEVICE_PREFIX}, {@link #DEVICE_PREFIX}, {@link #DEVICE_ADDRESS_PREFIX}, DEVICE_NAME_PREFIX,
		 * will be checked and if all set to appropriate valid values, preset will be bound/unmound to the device.
		 * # is number [0, NUM_BIND_DEVICES)<br>
		 * @since 856<br>
		 * TEXT 
		 */
		public static final @NonNull String DEVICE_NAME_PREFIX = "__device_name_";

		/**
		 * Virtual field, used for insert/update contentValues. If set, preset is bound to that track<br>
		 * NOTE: track related assigments requires preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * INTEGER
		 * @deprecated see {@link #BIND_TO_TRACK} 
		 */
		@Deprecated
		public static final @NonNull String BIND_TO_TRACK_ID = "__bind_to_track_id";
		
		/**
		 * Virtual field, used for insert/update contentValues. If set, preset is unbound from that track<br>
		 * NOTE: track related assignents requires preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * INTEGER
		 * @deprecated see {@link #BIND_TO_TRACK} 
		 */
		@Deprecated
		public static final @NonNull String UNBIND_FROM_TRACK_ID = "__unbind_to_track_id";
		
		/**
		 * Virtual field, used for insert/update contentValues. If set, appropriate category uri (string==getMassOpsItemsUri(false)) is bound for this track<br>
		 * NOTE: track related assigments requires preset id in uri, for example uri path should end with /eq_presets/123<br>
		 * INTEGER (boolean) 
		 * @deprecated see {@link #BIND_TO_CAT}
		 */
		@Deprecated
		public static final @NonNull String BIND_TO_CAT_URI = "__bind_to_cat_uri";

		/**
		 * Extra sort field<br>
		 * INTEGER
		 * @since 906
		 * @deprecated not used since 962
		 */
		@Deprecated
		public static final @NonNull String SORT = EqPresets.TABLE + ".sort";
	}

	enum EqPresetSongs {
        ;
        public static final @NonNull String TABLE = "eq_preset_songs";

		public static final @NonNull String _ID = EqPresetSongs.TABLE + "._id";

		/**
		 * Either folder_file_id<br>
		 * INTEGER 
		 */
		public static final @NonNull String FILE_ID = EqPresetSongs.TABLE + ".file_id";

		/**
		 * Eq preset id<br>
		 * INTEGER
		 */
		public static final @NonNull String PRESET_ID = EqPresetSongs.TABLE + ".preset_id";
	}

	enum EqPresetDevices {
        ;
        public static final @NonNull String TABLE = "eq_preset_devices";

		public static final @NonNull String _ID = EqPresetDevices.TABLE + "._id";

		/**
		 * Eq preset id<br>
		 * INTEGER
		 */
		public static final @NonNull String PRESET_ID = EqPresetDevices.TABLE + ".preset_id";

		/**
		 * Device type<br>
		 * INTEGER
		 */
		public static final @NonNull String DEVICE = "device";

		/**
		 * Device name<br>
		 * TEXT
		 */
		public static final @NonNull String DEVICE_NAME = "device_name";

		/**
		 * Device address<br>
		 * TEXT
		 */
		public static final @NonNull String DEVICE_ADDRESS = "device_address";
	}

	/** @since 960 */
    enum KnownDevices {
        ;
        public static final @NonNull String TABLE = "known_devices";

		public static final @NonNull String _ID = KnownDevices.TABLE + "._id";

		/**
		 * Device name<br>
		 * TEXT
		 */
		public static final @NonNull String DEVICE_NAME = KnownDevices.TABLE + ".device_name";
	}

	enum ReverbPresets {
        ;
        public static final @NonNull String TABLE = "reverb_presets";

		public static final @NonNull String _ID = ReverbPresets.TABLE + "._id";

		/**
		 * TEXT
		 */
		public static final @NonNull String _DATA = ReverbPresets.TABLE + "._data";

		/**
		 * TEXT
		 */
		public static final @NonNull String NAME = ReverbPresets.TABLE + ".name";
	}


	/** @since 841 */
    enum PrefSearch {
        ;
        public static final @NonNull String TABLE = "pref_search";
		public static final @NonNull String _ID = PrefSearch.TABLE + "._id";
		public static final @NonNull String BREADCRUMB = "breadcrumb";
		public static final @NonNull String PREF_URI = "pref_uri";
		public static final @NonNull String PREF_KEY = "pref_key";
		public static final @NonNull String ICON = "icon";
		public static final @NonNull String TYPE = "type";
	}

	/** @since 841 */
    enum PrefSearchFts {
        ;
        public static final @NonNull String TABLE = "pref_search_fts";
		public static final @NonNull String DOCID = "docid";
		public static final @NonNull String TITLE = "title";
		public static final @NonNull String SUMMARY = "summary";
	}
	
	/** 
	 * Search history for the main (tracks) search
	 * @since 907 
	 */
    enum SearchHistory {
        ;
        public static final @NonNull String TABLE = "search_history";
		public static final @NonNull String _ID = SearchHistory.TABLE + "._id";
		public static final @NonNull String TERM = "term";
		public static final @NonNull String UPDATED_AT = SearchHistory.TABLE + ".updated_at";
	}

	/**
	 * LRC files found during the file system/providers scan
	 * @since 948
	 */
    interface LrcFiles {
		@NonNull String TABLE = "lrc_files";

		@NonNull String _ID = LrcFiles.TABLE + "._id";

		/**
		 * First seen time<br>
		 * Seconds<br>
		 * INTEGER NOT NULL
		 */
		@NonNull String CREATED_AT = LrcFiles.TABLE + ".created_at";

		/**
		 * Time of update<br>
		 * Seconds<br>
		 * INTEGER NOT NULL
		 */
		@NonNull String UPDATED_AT = LrcFiles.TABLE + ".updated_at";

		/**
		 * LRC file mtime<br>
		 * INTEGER NOT NULL
		 */
		@NonNull String MTIME = LrcFiles.TABLE + ".mtime";

		/**
		 * Title extracted either from [ti:] tag or from the file name or NULL if none<br>
		 * TEXT NULL
		 */
		@NonNull String TITLE = LrcFiles.TABLE + ".title";

		/**
		 * Artist extracted either from [ar:] tag or from the file name or NULL if none<br>
		 * TEXT NULL
		 */
		@NonNull String ARTIST = LrcFiles.TABLE + ".artist";

		/**
		 * [al:] album tag contents or NULL if none<br>
		 * TEXT NULL
		 */
		@NonNull String ALBUM = LrcFiles.TABLE + ".album";

		/**
		 * [length:] tag in milliseconds or NULL if none or 0<br>
		 * INTEGER NULL
		 */
		@NonNull String LENGTH = LrcFiles.TABLE + ".length";

		/**
		 * Simple filename - the filename without path or extension<br>
		 * TEXT NON NULL
		 */
		@NonNull String SIMPLE_FILENAME = LrcFiles.TABLE + ".simple_filename";

		/**
		 * File extension with the dot, e.g. ".lrc" or empty string if none<br>
		 * TEXT NON NULL
		 */
		@NonNull String EXTENSION = LrcFiles.TABLE + ".extension";

		/**
		 * Parent folder path including the last /<br>
		 * TEXT NON NULL
		 */
		@NonNull String FOLDER_PATH = LrcFiles.TABLE + ".folder_path";

		/**
		 * 1 if this file should be treated as utf8<br>
		 * BOOLEAN NON NULL
		 */
		@NonNull String IS_UTF8 = LrcFiles.TABLE + ".is_utf8";

		/**
		 * One of the TAG_* constants<br>
		 * INTEGER NOT NULL
		 * @see com.maxmpz.poweramp.player.PowerampAPI.Track.TagStatus
		 */
		@NonNull String TAG_STATUS = LrcFiles.TABLE + ".tag_status";

		/**
		 * Full path to the lrc file.<br>
		 * Calculated field
		 */
		@NonNull String FULL_PATH = LrcFiles.FOLDER_PATH + "||" + LrcFiles.SIMPLE_FILENAME + "||" + LrcFiles.EXTENSION;
	}

	/**
	 * The cached lyrics. Only lyrics from 3rd party plugins gets here.<br>
	 * LRC files and embedded/tag lyrics are always loaded from the respective LRC file or the track tag.
	 * @since 948
	 */
    interface CachedLyrics {
		@NonNull String TABLE = "cached_lyrics";

		@NonNull String _ID = CachedLyrics.TABLE + "._id";

		/**
		 * First seen time<br>
		 * Seconds<br>
		 * INTEGER NOT NULL
		 */
		@NonNull String CREATED_AT = CachedLyrics.TABLE + ".created_at";

		/**
		 * Time of update<br>
		 * Seconds<br>
		 * INTEGER NOT NULL
		 */
		@NonNull String UPDATED_AT = CachedLyrics.TABLE + ".updated_at";

		/**
		 * 3rd party plugin package, the source of the lyrics
		 * TEXT NULL
		 */
		@NonNull String CREATED_BY_PAK = CachedLyrics.TABLE + ".created_by_pak";

		/**
		 * 3rd party plugin info line text, shown as last line in Poweramp lyrics. Can be copyright or other similar
		 * additional short info text
		 * TEXT NULL
		 */
		@NonNull String INFO_LINE = CachedLyrics.TABLE + ".info_line";

		/**
		 * Lyrics content or NULL if none.<br>
		 * NOTE: we may have NULL while lyrics is requested via the plugin, but we haven't received data from it yet
		 * TEXT 
		 */
		@NonNull String CONTENT = CachedLyrics.TABLE + ".content";
	}

}
