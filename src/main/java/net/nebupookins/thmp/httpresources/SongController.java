package net.nebupookins.thmp.httpresources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.nebupookins.thmp.LocalSongFileDB;
import net.nebupookins.thmp.model.SongFile;

@Path("/api/1/songs")
public class SongController {
	private final LocalSongFileDB db;
	
	public SongController(LocalSongFileDB db) {
		this.db = db;
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, SongFile> getSongList() {
		//TODO use StreamingOutput
		return db.getSongs();
	}
	
	@GET
	@Path("/binary")
	public Response getFile(@QueryParam("songPath") String songPath) {
		if (songPath == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		Optional<SongFile> song = db.getSong(songPath);
		if (!song.isPresent()) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		FileInputStream fis;
		try {
			fis = new FileInputStream(new File(songPath));
		} catch (FileNotFoundException e) {
			
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		//TODO use StreamingOutput
		return Response.ok(fis, MediaType.APPLICATION_OCTET_STREAM).build();
	}
}
