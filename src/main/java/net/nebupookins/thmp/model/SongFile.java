package net.nebupookins.thmp.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SongFile {

	public String getSha512();

	public String getPath();

	public String getMimeType();

	public Optional<String> getArtist();
	
	public Optional<String> getTitle();

	public Map<String, List<String>> getExtendedMetadata();
}
