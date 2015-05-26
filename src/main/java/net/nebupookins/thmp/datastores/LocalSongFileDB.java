package net.nebupookins.thmp.datastores;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import net.nebupookins.thmp.SafeObjectMapper;
import net.nebupookins.thmp.model.SongFile;

import org.apache.commons.lang3.tuple.Pair;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.TxMaker;
import org.mapdb.TxRollbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import fj.data.Either;

public class LocalSongFileDB {
	private final static Logger LOG = LoggerFactory.getLogger(LocalSongFileDB.class);
	public static final String COLLECTION_NAME = "LocalSongFile";
	private final TxMaker txMaker;
	private final SafeObjectMapper objectMapper;

	public LocalSongFileDB(final TxMaker txMaker, final SafeObjectMapper objectMapper) {
		this.txMaker = txMaker;
		this.objectMapper = objectMapper;
	}

	private static interface WithCollectionRunnable<R> {
		/**
		 * @return a pair of a boolean and the result. If the boolean is true, we should commit any changes made to the map.
		 */
		public Pair<Boolean, R> run(HTreeMap<String, String> map);
	}

	private <R> R withCollection(final WithCollectionRunnable<R> wcr) {
		final DB db = txMaker.makeTx();
		try {
			final HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			final Pair<Boolean, R> resultTuple = wcr.run(map);
			if (resultTuple.getLeft().booleanValue()) {
				db.commit();
			}
			return resultTuple.getRight();
		} finally {
			db.close();
		}
	}

	public void addSongFile(final SongFile songFile) {
		assert !songFile.getPath().isEmpty();
		final Either<JsonProcessingException, String> songJson = objectMapper.writeValueAsString(songFile);
		if (songJson.isRight()) {
			retryAddingSong(songFile, songJson.right().value());
		} else {
			LOG.warn("Could not serialize song object; therefore did not add it to DB.", songJson.left().value());
		}
	}

	private synchronized void retryAddingSong(final SongFile songFile, final String songJson) {
		int retryNumber = 0;
		while (true) {
			try {
				withCollection(map -> {
					map.put(songFile.getSha512(), songJson);
					return Pair.of(Boolean.TRUE, null);
				});
				return;
			} catch (final TxRollbackException e) {
				retryNumber++;
				if (retryNumber > 10) {
					throw new RuntimeException("Could not save data even after retrying many times.", e);
				}
			}
		}
	}

	public void removeSongFile(final String id) {
		withCollection(map -> {
			map.remove(id);
			return Pair.of(Boolean.TRUE, null);
		});
	}

	public List<SongFile> getSongs(final int maxResults) {
		return withCollection(map -> {
			boolean modifiedMap = false;
			final List<SongFile> retVal = new ArrayList<>(maxResults);
			for (final Entry<String, String> songEntry : map.entrySet()) {
				final Either<JsonProcessingException, SongFile> songObj =
						objectMapper.readValue(songEntry.getValue(), SongFile.class);
				if (songObj.isRight()) {
					retVal.add(songObj.right().value());
				} else {
					LOG.warn(
							String.format("Could not deserialize song %s. Json was %s.", songEntry.getKey(), songEntry.getValue()),
							songObj.left().value());
					map.remove(songEntry.getKey());
					modifiedMap = true;
				}
				if (retVal.size() >= maxResults) {
					break;
				}
			}
			return Pair.of(modifiedMap, retVal);
		});
	}

	public Optional<SongFile> getSong(final String key) {
		return withCollection(map -> {
			final String songJson = map.get(key);
			if (songJson == null) {
				return Pair.of(false, Optional.empty());
			}
			final Either<JsonProcessingException, SongFile> songObj = objectMapper.readValue(songJson, SongFile.class);
			if (songObj.isRight()) {
				return Pair.of(false, Optional.of(songObj.right().value()));
			} else {
				LOG.warn(String.format("Could not deserialize song for key %s. Json was %s.", key, songJson), songObj.left()
						.value());
				map.remove(key);
				return Pair.of(true, Optional.empty());
			}
		});
	}
}
