package net.nebupookins.thmp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import net.nebupookins.thmp.model.SongFile;
import net.nebupookins.thmp.model.SongFile.SongFileImpl;

import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.TxMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import fj.data.Either;

public class LocalSongFileDB {
	private final static Logger LOG = LoggerFactory
			.getLogger(LocalSongFileDB.class);
	public final static String COLLECTION_NAME = "LocalSongFile";
	private final TxMaker txMaker;
	private final SafeObjectMapper objectMapper;

	public LocalSongFileDB(TxMaker db, SafeObjectMapper objectMapper) {
		this.txMaker = db;
		this.objectMapper = objectMapper;
	}

	public void addSongFile(Path nextPath) {
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			SongFileImpl song = new SongFileImpl();
			song.setPath(nextPath.toString());
			Either<JsonProcessingException, String> songJson = objectMapper
					.writeValueAsString(song);
			if (songJson.isRight()) {
				map.put(nextPath.toString(), songJson.right().value());
			} else {
				LOG.warn(
						"Could not serialize song object; therefore did not add it to DB.",
						songJson.left().value());
			}
		} finally {
			db.commit();
			db.close();
		}
	}

	public Map<String, SongFile> getSongs() {
		Map<String, SongFile> retVal = new HashMap<>();
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			for (Entry<String, String> songEntry : map.entrySet()) {
				Either<JsonProcessingException, SongFile> songObj = objectMapper
						.readValue(songEntry.getValue(), SongFile.class);
				if (songObj.isRight()) {
					retVal.put(songEntry.getKey(), songObj.right().value());
				} else {
					LOG.warn(String.format("Could not deserialize song %s. Json was %s.",
							songEntry.getKey(), songEntry.getValue()), songObj.left().value());
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

	public Optional<SongFile> getSong(String songPath) {
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db.getHashMap(COLLECTION_NAME);
			String songJson = map.get(songPath);
			if (songJson == null) {
				return Optional.empty();
			}
			Either<JsonProcessingException, SongFile> songObj = objectMapper
					.readValue(songJson, SongFile.class);
			if (songObj.isRight()) {
				return Optional.of(songObj.right().value());
			} else {
				LOG.warn(String.format("Could not deserialize song %s. Json was %s.",
						songPath, songJson), songObj.left().value());
				map.remove(songPath);
				return Optional.empty();
			}
		} finally {
			db.close();
		}
	}
}
