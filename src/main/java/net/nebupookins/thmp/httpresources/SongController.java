package net.nebupookins.thmp.httpresources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import net.nebupookins.thmp.SafeObjectMapper;
import net.nebupookins.thmp.datastores.LocalSongFileDB;
import net.nebupookins.thmp.model.SongFile;
import net.nebupookins.thmp.model.SongFileImpl;

import org.apache.commons.lang3.StringUtils;
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

	public SongController(final LocalSongFileDB db, final SafeObjectMapper objectMapper,
			final MetricRegistry metricRegistry) {
		this.factory = new JsonFactory();
		this.db = db;
		this.objectMapper = objectMapper;
		this.getSongListJsonGeneratorTimer =
				metricRegistry.timer(MetricRegistry.name(SongController.class, "getSongListJsonGenerator"));
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
					final List<SongFile> dbSongs = db.getSongs(1000);
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

	private Optional<? extends SongFile> getSongFile(@Nullable final String nullableId) {
		return Optional.<String> ofNullable(nullableId)
				.flatMap(id -> db.getSong(id));
	}

	private Optional<? extends SongFile> getAndRefreshSongfile(@Nullable final String nullableId) {
		return getSongFile(nullableId)
				.flatMap(songFile -> refreshMetadata(songFile.getSha512(), Paths.get(songFile.getPath())));
	}

	@GET
	@Path("/binary")
	public Response getFile(@HeaderParam("Range") final String headerRange, @QueryParam("id") final String nullableId) {
		return getSongFile(nullableId)
				.map(songFile -> {
					String mimeType = songFile.getMimeType();
					if (mimeType.isEmpty()) {
						mimeType = MediaType.APPLICATION_OCTET_STREAM;
					}
					final File file = new File(songFile.getPath());
					try {
						final FileInputStream fileInputStream = new FileInputStream(file);
						return Response.ok((StreamingOutput) outputStream -> {
							try (
									final FileChannel inputChannel = fileInputStream.getChannel();
									final WritableByteChannel outputChannel = Channels.newChannel(outputStream)
								) {
									inputChannel.transferTo(0, inputChannel.size(), outputChannel);
								}
							})
								.status(200)
								.header(HttpHeaders.CONTENT_LENGTH, file.length())
								.build();
					} catch (final FileNotFoundException e) {
						throw new WebApplicationException(Status.NOT_FOUND);
					}
					// return Response.ok(new File(songFile.getPath()), mimeType).build();
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
