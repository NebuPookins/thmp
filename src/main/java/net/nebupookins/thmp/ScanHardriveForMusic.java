package net.nebupookins.thmp;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanHardriveForMusic implements Runnable {
	private final static Logger LOG = LoggerFactory
			.getLogger(ScanHardriveForMusic.class);
	private final static String[] WEIRD_DIRECTORY_ROOTS = new String[] { "/dev",
			"/proc", "/run" };
	private final static String[] NON_MUSIC_MIME_PREFIXES = new String[] {
			"application/pgp-", "application/vnd.oasis.",
			"application/vnd.openxmlformats", "application/x-font-",
			"application/x-virtualbox-", "application/x-wine-", "application/xml-", "image/", "text/" };
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
				try {
					if (isMusicFile(nextPath)) {
						db.addSongFile(nextPath);
					}
				} catch (IOException e) {
					LOG.warn(
							String.format("Could not determine type of file %s.", nextFile),
							e);
				}
			} else {
				/*
				 * Do nothing. This is probably a socket or a pipe or something like
				 * that.
				 */
			}
		}
	}

	private static boolean isMusicFile(Path nextPath) throws IOException {
		String fileType = Files.probeContentType(nextPath);
		if (fileType.startsWith("audio/")) {
			return true;
		}
		if (fileType.startsWith("video/")) {
			return true;
		}
		for (String nonMusicPrefix : NON_MUSIC_MIME_PREFIXES) {
			if (fileType.startsWith(nonMusicPrefix)) {
				return false;
			}
		}

		switch (fileType) {
		case "application/vnd.adobe.flash.movie":
		case "application/vnd.rn-realmedia":
			return true;
			// TODO: One day, we might scan inside archives?
		case "application/gzip":
		case "application/vnd.ms-access":
		case "application/vnd.android.package-archive":
		case "application/vnd.ms-asf":
		case "application/vnd.ms-cab-compressed":
		case "application/vnd.ms-excel":
		case "application/vnd.ms-htmlhelp":
		case "application/vnd.ms-wpl":
		case "application/vnd.ms-powerpoint":
		case "application/vnd.ms-publisher":
		case "application/vnd.ms-visio":
		case "application/vnd.ms-works":
		case "application/x-7z-compressed":
		case "application/x-archive":
		case "application/x-bzip":
		case "application/x-cd-image":
		case "application/x-compressed-tar":
		case "application/x-genesis-rom":
		case "application/x-lha":
		case "application/x-nintendo-ds-rom":
		case "application/x-par2":
		case "application/x-rar":
		case "application/x-raw-disk-image":
		case "application/x-tar":
		case "application/x-xz-compressed-tar":
		case "application/zip":
			return false;
		case "application/javascript":
		case "application/json":
		case "application/epub+zip":
		case "application/font-woff":
		case "application/nemo-action":
		case "application/mbox":
		case "application/msword":
		case "application/msword-template":
		case "application/octet-stream":
		case "application/pdf":
		case "application/pkix-cert":
		case "application/pkix-crl":
		case "application/postscript":
		case "application/rdf+xml":
		case "application/rtf":
		case "application/rss+xml":
		case "application/smil+xml":
		case "application/sql":
		case "application/vnd.iccprofile":
		case "application/vnd.sun.xml.writer.template":
		case "application/vnd.tcpdump.pcap":
		case "application/vnd.visio":
		case "application/winhlp":
		case "application/x-aportisdoc":
		case "application/x-asp":
		case "application/x-awk":
		case "application/x-bittorrent":
		case "application/x-cbr": // Can comic book archives ever contain music?
		case "application/x-cbz": // Can comic book archives ever contain music?
		case "application/x-cdrdao-toc":
		case "application/x-csh":
		case "application/x-cue":
		case "application/x-desktop":
		case "application/x-docbook+xml":
		case "application/x-dvi":
		case "application/x-executable":
		case "application/x-gettext-translation":
		case "application/x-glade":
		case "application/x-gtk-builder":
		case "application/x-gz-font-linux-psf":
		case "application/x-gzpdf":
		case "application/x-iwork-keynote-sffkey":
		case "application/x-java":
		case "application/x-java-archive":
		case "application/x-keepass2":
		case "application/x-m4":
		case "application/x-magicpoint":
		case "application/x-mobipocket-ebook":
		case "application/x-ms-dos-executable":
		case "application/x-ms-shortcut":
		case "application/x-msdownload":
		case "application/x-mswinurl":
		case "application/x-mswrite":
		case "application/x-msi":
		case "application/x-object":
		case "application/x-ole-storage":
		case "application/x-pak":
		case "application/x-perl":
		case "application/x-php":
		case "application/x-planperfect":
		case "application/x-python-bytecode":
		case "application/x-pkcs12":
		case "application/x-riff":
		case "application/x-ruby":
		case "application/x-shared-library-la":
		case "application/x-sharedlib":
		case "application/x-shellscript":
		case "application/x-spss-sav":
		case "application/x-sqlite3":
		case "application/x-subrip":
		case "application/x-tex-pk":
		case "application/x-tgif":
		case "application/x-theme":
		case "application/x-trash":
		case "application/x-troff-man":
		case "application/x-wais-source":
		case "application/x-x509-ca-cert":
		case "application/x-yaml":
		case "application/xhtml+xml":
		case "application/xml":
		case "application/xslt+xml":
		case "message/rfc822":
			return false;
		default:
			LOG.warn(String.format("unknown file type %s for file %s ", fileType,
					nextPath));
			return false;
		}
	}

}
