package net.nebupookins.thmp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface SongFile {
	@JsonProperty
	public String getPath();
	
	static class SongFileImpl implements SongFile {
		
		private String path;

		@Override
		public String getPath() {
			return this.path;
		}
		
		public SongFileImpl setPath(String path) {
			this.path = path;
			return this;
		}
		
	}
}
