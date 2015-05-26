package net.nebupookins.thmp.healthchecks;

import net.nebupookins.thmp.datastores.LocalSongFileDB;

import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.TxMaker;

import com.codahale.metrics.health.HealthCheck;

public class MapDbHealthCheck extends HealthCheck {

	public static final String NAME = MapDbHealthCheck.class.getSimpleName();

	private final TxMaker txMaker;

	public MapDbHealthCheck(TxMaker db) {
		this.txMaker = db;
	}

	@Override
	protected Result check() throws Exception {
		DB db = txMaker.makeTx();
		try {
			HTreeMap<String, String> map = db
					.getHashMap(LocalSongFileDB.COLLECTION_NAME);
			map.size();
			return Result.healthy();
		} finally {
			db.close();
		}
	}

}
