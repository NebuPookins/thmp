package net.nebupookins.thmp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import net.nebupookins.thmp.model.SongFile;

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
	public final static String COLLECTION_NAME = "LocalSongFile";
	private final TxMaker txMaker;
	private final SafeObjectMapper objectMapper;

	public LocalSongFileDB(TxMaker db, SafeObjectMapper objectMapper) {
		this.txMaker = db;
		this.objectMapper = objectMapper;
	}

	public void addSongFile(SongFile songFile) {
		assert !songFile.getPath().isEmpty();
		Either<JsonProcessingException, String> songJson = objectMapper.writeValueAsString(songFile);
		if (songJson.isRight()) {
			retryAddingSong(songFile, songJson.right().value());
		} else {
			LOG.warn("Could not serialize song object; therefore did not add it to DB.", songJson.left().value());
		}
	}

	private synchronized void retryAddingSong(SongFile songFile, String songJson) {
		int retryNumber = 0;
		while (true) {
			DB db = txMaker.makeTx();
			try {
				HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
				map.put(songFile.getSha512(), songJson);
				db.commit();
				return;
			} catch (TxRollbackException e) {
				retryNumber++;
				if (retryNumber > 10) {
					throw new RuntimeException("Could not save data even after retrying many times.", e);
				}
			} finally {
				db.close();
			}
		}
	}
	
	public void removeSongFile(String id) {
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			map.remove(id);
			db.commit();
		} finally {
			db.close();
		}
	}

	public List<SongFile> getSongs() {
		final DB db = txMaker.makeTx();
		try {
			final HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			final List<SongFile> retVal = new ArrayList<>(map.size());
			for (Entry<String, String> songEntry : map.entrySet()) {
				final Either<JsonProcessingException, SongFile> songObj =
						objectMapper.readValue(songEntry.getValue(), SongFile.class);
				if (songObj.isRight()) {
					retVal.add(songObj.right().value());
				} else {
					LOG.warn(
							String.format("Could not deserialize song %s. Json was %s.", songEntry.getKey(), songEntry.getValue()),
							songObj.left().value());
					map.remove(songEntry.getKey());
				}
				if (retVal.size() > 10000) {
					return retVal;
				}
			}
			return retVal;
		} finally {
			db.close();
		}
	}

	public Optional<SongFile> getSong(String key) {
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			String songJson = map.get(key);
			if (songJson == null) {
				return Optional.empty();
			}
			Either<JsonProcessingException, SongFile> songObj = objectMapper.readValue(songJson, SongFile.class);
			if (songObj.isRight()) {
				return Optional.of(songObj.right().value());
			} else {
				LOG.warn(String.format("Could not deserialize song for key %s. Json was %s.", key, songJson), songObj.left()
						.value());
				map.remove(key);
				return Optional.empty();
			}
		} finally {
			db.close();
		}
	}
}
