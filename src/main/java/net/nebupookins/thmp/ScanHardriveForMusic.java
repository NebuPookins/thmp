package net.nebupookins.thmp;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.nebupookins.thmp.model.SongFileImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanHardriveForMusic implements Runnable {
	private final static Logger LOG = LoggerFactory.getLogger(ScanHardriveForMusic.class);
	private final static String[] WEIRD_DIRECTORY_ROOTS = new String[] { "/dev", "/proc", "/run" };

	private final Random rng = new Random();
	private final LocalSongFileDB db;
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
		scanNextFile: while (!directoriesToScan.isEmpty()) {
			final int nextIndex = rng.nextInt(directoriesToScan.size());
			final Path nextPath = directoriesToScan.remove(nextIndex);
			for (String weirdRootDirectory : WEIRD_DIRECTORY_ROOTS) {
				if (nextPath.startsWith(weirdRootDirectory)) {
					continue scanNextFile;
				}
			}
			final File nextFile = nextPath.toFile();
			if (nextFile.isDirectory()) {
				String[] childPaths = nextFile.list();
				if (childPaths != null) {
					/*
					 * childPaths can be null if we don't have permission to read the
					 * directory.
					 */
					for (String childPath : childPaths) {
						directoriesToScan.add(nextPath.resolve(childPath));
					}
				}
			} else if (nextFile.isFile()) {
				Optional<SongFileImpl> maybeSong = SongFileImpl.fromPath(nextPath);
				if (maybeSong.isPresent()) {
					db.addSongFile(maybeSong.get());
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
