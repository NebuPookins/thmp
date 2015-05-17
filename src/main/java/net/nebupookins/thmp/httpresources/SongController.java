package net.nebupookins.thmp.httpresources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Map.Entry;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;

import fj.data.Either;
import net.nebupookins.thmp.LocalSongFileDB;
import net.nebupookins.thmp.SafeObjectMapper;
import net.nebupookins.thmp.model.SongFile;

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
			generator.writeStartObject();
			for (Entry<String, SongFile> songEntry : db.getSongs().entrySet()) {
				Either<JsonProcessingException, String> songJson = objectMapper.writeValueAsString(songEntry.getValue());
				if (songJson.isRight()) {
					generator.writeFieldName(songEntry.getKey());
					generator.writeRawValue(songJson.right().value());
				} else {
					LOG.warn(
							String.format("Could not deserialize json for song %s. JSON was: %s", songEntry.getKey(),
									songEntry.getValue()), songJson.left().value());
				}
			}
			generator.writeEndObject();
			generator.flush();
			generator.close();
		};
	}

	@GET
	@Path("/binary")
	public Response getFile(@QueryParam("songPath") String songPath) {
		if (songPath == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
		Optional<SongFile> song = db.getSong(songPath);
		if (!song.isPresent()) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
		FileInputStream fis;
		try {
			fis = new FileInputStream(new File(songPath));
		} catch (FileNotFoundException e) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
		return Response.ok(fis, MediaType.APPLICATION_OCTET_STREAM).build();
	}
}
