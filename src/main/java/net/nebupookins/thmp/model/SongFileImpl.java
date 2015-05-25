package net.nebupookins.thmp.model;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;

public class SongFileImpl implements SongFile {
	private final static Logger LOG = LoggerFactory.getLogger(SongFileImpl.class);
	private final static String[] NON_MUSIC_MIME_PREFIXES = new String[] { "application/pgp-", "application/vnd.oasis.",
			"application/vnd.openxmlformats", "application/x-font-", "application/x-virtualbox-", "application/x-wine-",
			"application/xml-", "image/", "text/" };

	private String path = "";
	
	private String sha512 = "";

	private String mimeType = "";

	private Map<String, List<String>> extendedMetadata = new HashMap<>();

	private Optional<String> artist = Optional.empty();
	
	private Optional<String> title  = Optional.empty();
	
	private Optional<Long> playbackLengthMillis  = Optional.empty();
	
	public Optional<SongFileImpl> validate() {
		try {
			Validate.notBlank(path);
			Validate.notBlank(sha512);
			Validate.notBlank(mimeType);
			playbackLengthMillis.ifPresent(millis -> Validate.validState(millis >= 0));
			return Optional.of(this);
		} catch (RuntimeException e) {
			LOG.warn("Invalid SongFile.", e);;
			return Optional.empty();
		}
	}

	@Override
	public String getPath() {
		return this.path;
	}
	
	@Override
	public String getSha512() {
		return this.sha512;
	}

	@Override
	public String getMimeType() {
		return this.mimeType;
	}

	@Override
	public Optional<String> getArtist() {
		return this.artist;
	}
	
	@Override
	public Optional<String> getTitle() {
		return this.title;
	}
	
	@Override
	public Optional<Long> getPlaybackLengthMillis() {
		return this.playbackLengthMillis;
	}

	@Override
	public Map<String, List<String>> getExtendedMetadata() {
		return extendedMetadata;
	}

	private static Optional<SongFileImpl> genericMediaFrom(Path path, String mimeType) {
		final SongFileImpl retVal = new SongFileImpl();
		retVal.path = path.toString();
		retVal.mimeType = mimeType;
		try (FileInputStream fis = new FileInputStream(path.toFile())) {
			retVal.sha512 = DigestUtils.sha512Hex(fis);
		} catch (IOException e) {
			LOG.info(String.format("Could not read file %s.", path), e);
			return Optional.empty();
		}
		return Optional.of(retVal);
	}
	
	private static Optional<String> max(Optional<String> a, Optional<String> b) {
		if (a.isPresent()) {
			if (b.isPresent()) {
				return Optional.of(a.get().length() > b.get().length() ? a.get() : b.get());
			} else {
				return a;
			}
		} else {
			return b;
		}
	}
	
	private static Optional<String> max(Optional<ID3v1> a, Optional<ID3v2> b, Function<? super ID3v1, String> f) {
		return max(a.map(t -> f.apply(t)), b.map(t -> f.apply(t)));
	}

	private final static String MIMETYPE_MP3 = "audio/mpeg";

	private static Optional<SongFileImpl> mp3From(Path path) {
		return genericMediaFrom(path, MIMETYPE_MP3).flatMap(retVal -> {
			try {
				Mp3File mp3File = new Mp3File(path.toFile());
				Optional<ID3v1> maybeId3v1 = Optional.ofNullable(mp3File.getId3v1Tag());
				Optional<ID3v2> maybeId3v2 = Optional.ofNullable(mp3File.getId3v2Tag());
				retVal.artist = max(maybeId3v1, maybeId3v2, t -> t.getArtist());
				retVal.title = max(maybeId3v1, maybeId3v2, t -> t.getTitle());
				retVal.playbackLengthMillis = Optional.of(mp3File.getLengthInMilliseconds()).map(
						//Apparently Mp3agic sometimes returns negative results?
						ms -> ms < 0 ? 0 : ms); 
				// TODO copy other fields.
				return Optional.of(retVal);
			} catch (UnsupportedTagException | InvalidDataException e) {
				LOG.debug(String.format("Could not read mp3 metadata for %s.", path), e);
				return Optional.empty();
			} catch (IOException e) {
				LOG.warn(String.format("Error reading ID3 file %s.", path), e);
				return Optional.empty();
			}
		});
	}

	public static Optional<? extends SongFile> fromPath(Path path) {
		try {
			String localMimeType = Files.probeContentType(path);
			if (isKnownNonMusicMimeType(localMimeType)) {
				return Optional.empty();
			}
			switch (localMimeType) {
			case MIMETYPE_MP3:
				return mp3From(path).flatMap(song -> song.validate());
				// TODO: Add metadata support for these formats.
			case "application/vnd.adobe.flash.movie":
			case "application/vnd.rn-realmedia":
			case "audio/basic": // .au files.
			case "audio/flac":
			case "audio/midi":
			case "audio/mp2":
			case "audio/mp4":
			case "audio/ogg":
			case "audio/x-aiff":
			case "audio/x-it":
			case "audio/x-mod":
			case "audio/x-ms-asx":
			case "audio/x-ms-wma":
			case "audio/x-musepack":
			case "audio/x-vorbis+ogg":
			case "audio/x-wav":
			case "video/mp4":
			case "video/mpeg":
			case "video/quicktime":
			case "video/webm":
			case "video/x-flv":
			case "video/x-matroska":
			case "video/x-ms-wmv":
			case "video/x-msvideo":
			case "video/x-ogm+ogg":
			case "video/x-theora+ogg":
			case "video/x-vorbis+ogg":
			case "video/x-wav":
				return genericMediaFrom(path, localMimeType).flatMap(sf -> sf.validate());
			default:
				LOG.warn(String.format("Unknown file type %s for %s.", localMimeType, path));
				return Optional.empty();
			}
		} catch (IOException e) {
			LOG.warn(String.format("Could not determine mimetype of file %s.", path), e);
			return Optional.empty();
		}
	}

	private static boolean isKnownNonMusicMimeType(String mimeType) throws IOException {
		for (String nonMusicPrefix : NON_MUSIC_MIME_PREFIXES) {
			if (mimeType.startsWith(nonMusicPrefix)) {
				return true;
			}
		}
		switch (mimeType) {
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
		case "application/x-lzma-compressed-tar":
		case "application/x-nintendo-ds-rom":
		case "application/x-par2":
		case "application/x-rar":
		case "application/x-raw-disk-image":
		case "application/x-tar":
		case "application/x-wii-rom":
		case "application/x-xz-compressed-tar":
		case "application/zip":
			return true;
		case "application/javascript":
		case "application/json":
		case "application/epub+zip":
		case "application/font-woff":
		case "application/nemo-action":
		case "application/mbox":
		case "application/msword":
		case "application/msword-template":
		case "application/octet-stream":
		case "application/oxps":
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
		case "application/vnd.sun.xml.draw.template":
		case "application/vnd.tcpdump.pcap":
		case "application/vnd.visio":
		case "application/winhlp":
		case "application/x-aportisdoc":
		case "application/x-applix-spreadsheet":
		case "application/x-asp":
		case "application/x-awk":
		case "application/x-bittorrent":
		case "application/x-cbr": // Can comic book archives ever contain music?
		case "application/x-cbz": // Can comic book archives ever contain music?
		case "application/x-cdrdao-toc":
		case "application/x-csh":
		case "application/x-cue":
		case "application/x-core":
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
		case "application/x-mif":
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
		case "application/x-spsf+xml":
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
		case "audio/x-mpegurl": //This is a playlist file.
		case "message/news":
		case "message/rfc822":
			return true;
		default:
			return false;
		}
	}
}