/*globals __filename, process, require*/
(function () {
	'use strict';

	var MEDIA_FILE_MIME_TYPES = [
		'application/ogg',
		'application/vnd.rn-realmedia', //TODO: check this one
		'application/x-shockwave-flash', //TODO: check this one
		'audio/basic',
		'audio/midi',
		'audio/mp4',
		'audio/mpeg',
		'audio/x-aiff',
		'audio/x-mod',
		'audio/x-mp4a-latm',
		'audio/x-wav',
		'video/3gpp',
		'video/mp4',
		'video/mpeg',
		'video/mpeg4-generic',
		'video/quicktime',
		'video/webm',
		'video/x-flv',
		'video/x-matroska',
		'video/x-ms-asf',
		'video/x-msvideo',
	];

	var NOT_MEDIA_FILE_MIME_TYPES = [
		'application/CDFV2-corrupt',
		'application/epub+zip',
		'application/jar',
		'application/javascript',
		'application/msword',
		'application/octet-stream',
		'application/pdf',
		'application/postscript',
		'application/vnd.font-fontforge-sfd',
		'application/vnd.iccprofile',
		'application/vnd.ms-cab-compressed',
		'application/vnd.ms-excel',
		'application/vnd.ms-fontobject',
		'application/vnd.ms-office',
		'application/vnd.ms-opentype',
		'application/vnd.ms-powerpoint',
		'application/vnd.oasis.opendocument.spreadsheet',
		'application/vnd.oasis.opendocument.text',
		'application/vnd.openxmlformats-officedocument.presentationml.presentation',
		'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
		'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
		'application/x-123',
		'application/x-7z-compressed',
		'application/x-archive',
		'application/x-bittorrent',
		'application/x-cpio',
		'application/x-dbf',
		'application/x-debian-package',
		'application/x-dosexec',
		'application/x-dvi',
		'application/x-elc',
		'application/x-executable',
		'application/x-font-sfn',
		'application/x-font-ttf',
		'application/x-gnupg-keyring',
		'application/x-gzip',
		'application/x-iso9660-image',
		'application/x-java-applet',
		'application/x-java-keystore',
		'application/x-lha',
		'application/x-lharc',
		'application/x-ms-reader',
		'application/x-object',
		'application/x-pgp-keyring',
		'application/x-rar',
		'application/x-rpm',
		'application/x-setupscript.', //yes, it ends with a period.
		'application/x-sharedlib',
		'application/x-tar',
		'application/x-tex-tfm',
		'application/x-xz',
		'application/xml',
		'application/zip',
		'image/gif',
		'image/jpeg',
		'image/png',
		'image/svg+xml',
		'image/tiff',
		'image/vnd.adobe.photoshop',
		'image/vnd.djvu',
		'image/x-icon',
		'image/x-ms-bmp',
		'image/x-paintnet',
		'image/x-portable-bitmap',
		'image/x-portable-pixmap',
		'image/x-xpmi',
		'inode/x-empty',
		'message/news',
		'message/rfc822',
		'model/vrml',
		'text/html',
		'text/plain',
		'text/rtf',
		'text/troff',
		'text/x-asm',
		'text/x-c',
		'text/x-c++',
		'text/x-diff',
		'text/x-fortran',
		'text/x-makefile',
		'text/x-msdos-batch',
		'text/x-pascal',
		'text/x-perl',
		'text/x-php',
		'text/x-po',
		'text/x-python',
		'text/x-ruby',
		'text/x-shellscript',
		'text/x-tex',
	];

	var _        = require('underscore');
	var assert   = require('assert');
	var commands = require('./commands');
	var config   = require('../../../config');
	var fs       = require('fs');
	var mime     = require('mime-magic');
	var path     = require('path');
	var q        = require('q');

	assert.equal('function', typeof process.send, "This module is intended to be run as a child process.");

	var logger = config.getLogger(__filename);
	logger.info('mediaFileScanner process has started.');

	var jobs = [];
	var currentJob = null;
	var entriesToProcess = [];

	process.on('message', function (msg) {
		if (msg.code === commands.SCAN_DIR.code) {
			jobs.push(msg.path);
			if (currentJob === null) {
				startWork();
			}
		} else {
			logger.warn('Received unrecognized command: %s.', msg.cmd);
		}
	});

	function startWork() {
		assert.ok(currentJob === null);
		assert.ok(jobs.length > 0);
		assert.ok(entriesToProcess.length === 0);
		currentJob = jobs.pop();
		logger.info('Starting scan of %s.', currentJob);
		entriesToProcess = [currentJob];
		processNextEntry();
	}

	function finishWork() {
		assert.ok(currentJob);
		assert.ok(entriesToProcess.length === 0);
		logger.info('Finished scan of %s.', currentJob);
		process.send(new (commands.SCAN_DONE)(currentJob));
		currentJob = null;
		if (jobs.length > 0) {
			startWork();
		}
	}

	function processNextEntry() {
		logger.trace('Entering processNextEntry.');
		if (entriesToProcess.length === 0) {
			logger.trace('All entries processed. mediaFileScanner.proc is done.');
			finishWork();
			return;
		}
		var nextEntry = entriesToProcess.pop();
		logger.trace('Processing %s. Is it a file or a dir?', nextEntry);
		q.nfcall(fs.stat, nextEntry).then(function (stat) {
			var promise;
			if (stat.isFile()) {
				promise = processFile(nextEntry);
			} else {
				promise = addContentsOfDirectoryToQueue(nextEntry);
			}
			promise.then(function () {
				processNextEntry();
			}).done();
		}).fail(function (err) {
			if (err instanceof Error) {
				if (err.errno === 34 && err.code === 'ENOENT') {
					/*
					 * This can happen if we found a symbolic link, but the file that the
					 * symbolic points to does not exist. In this case, we just ignore
					 * this file, and continue on.
					 */
					return processNextEntry();
				}
			}
			//This is some failure we don't know how to handle, so rethrow it.
			throw err;
		}).done();
	}

	function addContentsOfDirectoryToQueue(pathToDir) {
		logger.trace('%s is a directory. Gettings its contents...', pathToDir);
		return q.nfcall(fs.readdir, pathToDir).then(function(dirContents) {
			var absolutePaths = _.map(dirContents, function (relPath) {
				return path.join(pathToDir, relPath);
			});
			/*
			 * Shuffled so that we don't scan the same directories in the same
			 * order every time.
			 */
			absolutePaths = _.shuffle(absolutePaths);
			entriesToProcess = entriesToProcess.concat(absolutePaths);
		});
		/*
		 * TODO: Apparently this could fail if the directory is actually a symlink
		 * to a device that's not mounted, e.g. a cdrom drive.
		 *
		 * You get ENOTDIR, but not sure of the error code.
		 */
	}

	function processFile(pathToFile) {
		logger.trace('%s is a file, determining type...', pathToFile);
		return q.nfcall(mime, pathToFile).then(function (mimeType) {
			if (_.contains(MEDIA_FILE_MIME_TYPES, mimeType)) {
				logger.info('Found media file %s.', pathToFile);
				process.send(new (commands.FOUND_FILE)(pathToFile));
				return;
			}
			//For some reason '_.contains' did not work here.
			var isKnownBadMimeType = _.some(NOT_MEDIA_FILE_MIME_TYPES, function (nmf) {
				return nmf === mimeType;
			});
			if (!isKnownBadMimeType) {
				logger.warn('Unknown mime type "%s" for file %s.', mimeType, pathToFile);
			}
		});
	}
}());