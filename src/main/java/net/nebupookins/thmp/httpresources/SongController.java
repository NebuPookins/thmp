package net.nebupookins.thmp.httpresources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import net.nebupookins.thmp.LocalSongFileDB;
import net.nebupookins.thmp.SafeObjectMapper;
import net.nebupookins.thmp.model.SongFile;
import net.nebupookins.thmp.model.SongFileImpl;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public SongController(LocalSongFileDB db, SafeObjectMapper objectMapper) {
		this.factory = new JsonFactory();
		this.db = db;
		this.objectMapper = objectMapper;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public StreamingOutput getSongList() {
		return output -> {
			JsonGenerator generator = factory.createGenerator(output);
			generator.writeStartArray();
			for (SongFile song : db.getSongs()) {
				Either<JsonProcessingException, String> songJson = objectMapper.writeValueAsString(song);
				if (songJson.isRight()) {
					generator.writeRawValue(songJson.right().value());
				} else {
					LOG.warn(String.format("Could not serialize json for song %s",song), songJson.left().value());
				}
			}
			generator.writeEndArray();
			generator.flush();
			generator.close();
		};
	}

	@GET
	@Path("/metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public SongFile getMetadata(@QueryParam("id") String nullableId) {
		return Optional.ofNullable(nullableId)
				// Check that song was already in the DB (don't allow them to ask for
				// metadata for arbitrary files).
				.flatMap(id -> db.getSong(id))
				// If it was already in the DB, refresh the metadata.
				.flatMap(song -> refreshMetadata(Paths.get(song.getPath())))
				.orElseThrow(() -> new WebApplicationException(Status.NOT_FOUND));
	}

	@GET
	@Path("/binary")
	public Response getFile(@QueryParam("id") String nullableId) {
		return Optional.ofNullable(nullableId)
				// Check that song was already in the DB (don't allow them to ask for arbitrary files).
				.flatMap(id -> db.getSong(id))
				// If it was already in the DB, refresh the metadata.
				.flatMap(song -> refreshMetadata(Paths.get(song.getPath())))
				//Attempt to open a FileInputStream on the song file.
				.flatMap(song -> {
					FileInputStream fis;
					try {
						fis = new FileInputStream(new File(song.getPath()));
						return Optional.of(Pair.of(song, fis));
					} catch (FileNotFoundException e) {
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

	private Optional<SongFileImpl> refreshMetadata(java.nio.file.Path path) {
		final Optional<SongFileImpl> reloadedSong = SongFileImpl.fromPath(path);
		if (reloadedSong.isPresent()) {
			db.addSongFile(reloadedSong.get());
		}
		return reloadedSong;
	}
}
