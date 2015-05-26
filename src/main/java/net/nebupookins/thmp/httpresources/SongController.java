package net.nebupookins.thmp.httpresources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import net.nebupookins.thmp.SafeObjectMapper;
import net.nebupookins.thmp.datastores.LocalSongFileDB;
import net.nebupookins.thmp.model.SongFile;
import net.nebupookins.thmp.model.SongFileImpl;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;

import fj.data.Either;

@Path("/api/1/songs")
public class SongController {
	private final static Logger LOG = LoggerFactory.getLogger(SongController.class);
	private final LocalSongFileDB db;
	private final JsonFactory factory;
	private final SafeObjectMapper objectMapper;


	public SongController(final LocalSongFileDB db, final SafeObjectMapper objectMapper, final MetricRegistry metricRegistry) {
		this.factory = new JsonFactory();
		this.db = db;
		this.objectMapper = objectMapper;
		this.getSongListJsonGeneratorTimer = metricRegistry.timer(MetricRegistry.name(SongController.class, "getSongListJsonGenerator"));
	}

	private final Timer getSongListJsonGeneratorTimer;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Timed
	public StreamingOutput getSongList() {
		return output -> {
			try (Context context = getSongListJsonGeneratorTimer.time()) {
				try (JsonGenerator generator = factory.createGenerator(output)) {
					generator.writeStartArray();
					List<SongFile> dbSongs = db.getSongs();
					LOG.info(String.format("Writing out list of %s songs.", dbSongs.size()));
					for (final SongFile song : dbSongs) {
						final Either<JsonProcessingException, String> songJson = objectMapper.writeValueAsString(song);
						if (songJson.isRight()) {
							generator.writeRawValue(songJson.right().value());
						} else {
							LOG.warn(String.format("Could not serialize json for song %s", song), songJson.left().value());
						}
					}
					generator.writeEndArray();
					generator.flush();
				}
			}
		};
	}

	@GET
	@Path("/metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public SongFile getMetadata(@QueryParam("id") final String nullableId) {
		return getAndRefreshSongfile(nullableId)
				.orElseThrow(() -> new WebApplicationException(Status.NOT_FOUND));
	}

	private Optional<? extends SongFile> getAndRefreshSongfile(@Nullable final String nullableId) {
		return Optional.<String>ofNullable(nullableId)
				// Check that song was already in the DB (don't allow them to ask for
				// metadata for arbitrary files).
				.flatMap(id -> db.getSong(id).<Pair<String,SongFile>>map(song -> Pair.of(id, song)))
				// If it was already in the DB, refresh the metadata.
				.flatMap(idSongPair -> refreshMetadata(idSongPair.getLeft(), Paths.get(idSongPair.getRight().getPath())));
	}

	@GET
	@Path("/binary")
	public Response getFile(@QueryParam("id") final String nullableId) {
		return getAndRefreshSongfile(nullableId)
				//Attempt to open a FileInputStream on the song file.
				.flatMap(song -> {
					FileInputStream fis;
					try {
						fis = new FileInputStream(new File(song.getPath()));
						return Optional.of(Pair.<SongFile, FileInputStream>of(song, fis));
					} catch (final FileNotFoundException e) {
						return Optional.empty();
					}
				})
				.map(pairSongFis -> {
					String mimeType = pairSongFis.getLeft().getMimeType();
					if (mimeType.isEmpty()) {
						mimeType = MediaType.APPLICATION_OCTET_STREAM;
					}
					return Response.ok(pairSongFis.getRight(), mimeType).build();
				}).orElseThrow(() -> new WebApplicationException(Status.NOT_FOUND));
	}

	private Optional<? extends SongFile> refreshMetadata(final String oldId, final java.nio.file.Path path) {
		final Optional<? extends SongFile> maybeReloadedSong = SongFileImpl.fromPath(path);
		if (maybeReloadedSong.isPresent()) {
			final SongFile reloadedSong = maybeReloadedSong.get();
			db.addSongFile(reloadedSong);
			if (!oldId.equals(reloadedSong.getSha512())) {
				db.removeSongFile(oldId);
			}
		} else {
			db.removeSongFile(oldId);
		}
		return maybeReloadedSong;
	}
}
