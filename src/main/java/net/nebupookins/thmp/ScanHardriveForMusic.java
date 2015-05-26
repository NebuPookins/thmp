package net.nebupookins.thmp;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.nebupookins.thmp.datastores.LocalSongFileDB;
import net.nebupookins.thmp.model.SongFile;
import net.nebupookins.thmp.model.SongFileImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanHardriveForMusic implements Runnable {
	private final static Logger LOG = LoggerFactory.getLogger(ScanHardriveForMusic.class);
	private final static String[] WEIRD_DIRECTORY_ROOTS = new String[] { "/dev", "/proc", "/run" };

	private final Random rng = new Random();
	private final LocalSongFileDB db;
	private final List<Path> filesToScan = new ArrayList<>();
	private final List<Path> directoriesToScan = new ArrayList<>();

	public ScanHardriveForMusic(LocalSongFileDB db) {
		this.db = db;
	}

	@Override
	public void run() {
		LOG.info(String.format("Scanning filesystems for music files..."));
		FileSystem defaultFS = FileSystems.getDefault();
		for (Path root : defaultFS.getRootDirectories()) {
			directoriesToScan.add(root);
		}
		directoriesToScan.add(defaultFS.getPath(System.getProperty("user.home")));
		scanNextFile: while (!filesToScan.isEmpty() || !directoriesToScan.isEmpty()) {
			// Go through all files.
			while (!filesToScan.isEmpty()) {
				Path nextPath = filesToScan.remove(0);
				Optional<? extends SongFile> maybeSong = SongFileImpl.fromPath(nextPath);
				if (maybeSong.isPresent()) {
					db.addSongFile(maybeSong.get());
				}
			}
			// Pick a directory at random
			final int nextIndex = rng.nextInt(directoriesToScan.size());
			final Path nextPath = directoriesToScan.remove(nextIndex);
			for (String weirdRootDirectory : WEIRD_DIRECTORY_ROOTS) {
				if (nextPath.startsWith(weirdRootDirectory)) {
					continue scanNextFile;
				}
			}
			final File nextDirectory = nextPath.toFile();
			assert !nextDirectory.isFile();
			if (nextDirectory.isDirectory()) {
				File[] children = nextDirectory.listFiles();
				if (children != null) {
					/*
					 * childPaths can be null if we don't have permission to read the
					 * directory.
					 */
					for (File child : children) {
						if (child.isFile()) {
							filesToScan.add(child.toPath());
						} else if (child.isDirectory()) {
							directoriesToScan.add(child.toPath());
						} else {
							/*
							 * Do nothing. This is probably a socket or a pipe or something like
							 * that.
							 */
						}
					}
				}
			} else {
				/*
				 * Do nothing. This is probably a socket or a pipe or something like
				 * that.
				 */
			}
		}
	}
}
