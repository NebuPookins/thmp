/*globals __dirname, __filename, console, module, require*/
(function () {
	'use strict';

	var MEDIA_FILE_MIME_TYPES = [
		'application/ogg',
		'application/vnd.rn-realmedia', //TODO: check this one
		'application/x-shockwave-flash', //TODO: check this one
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
		'application/x-executable',
		'application/x-font-sfn',
		'application/x-font-ttf',
		'application/x-gnupg-keyring',
		'application/x-gzip',
		'application/x-iso9660-image',
		'application/x-java-applet',
		'application/x-java-keystore',
		'application/x-ms-reader',
		'application/x-object',
		'application/x-pgp-keyring',
		'application/x-rar',
		'application/x-sharedlib',
		'application/x-tar',
		'application/x-tex-tfm',
		'application/xml',
		'application/zip',
		'image/gif',
		'image/jpeg',
		'image/png',
		'image/svg+xml',
		'image/tiff',
		'image/vnd.adobe.photoshop',
		'image/x-icon',
		'image/x-ms-bmp',
		'image/x-paintnet',
		'image/x-portable-bitmap',
		'image/x-portable-pixmap',
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
		'text/x-shellscript',
		'text/x-tex',
		'image/x-xpmi',
	];

	var _           = require('underscore');
	var assert      = require('assert');
	var config      = require('../../../config');
	var fs          = require('fs');
	var mime        = require('mime-magic');
	var MediaFile   = require('../../models/MediaFile');
	var path        = require('path');
	var q           = require('q');

	var childProcess = require('child_process').fork(
		path.join(__dirname, './mediaFileScanner.proc'),
		[]
	);

	var logger = config.getLogger(__filename);

	/**
	 * Returns a promise that, when resolved, means all the files in the specified
	 * directory have been processed.
	 */
	module.exports.addMediaFileToDB = function (entriesToProcess) {
		//logger.trace('Entering addMediaFileToDB2.');
		assert.ok(_.isArray(entriesToProcess));
		if (entriesToProcess.length === 0) {
			logger.trace('All entries processed. addMediaFileToDB2 is done.');
			return q.promise(function (resolve) { resolve(); });
		}
		var nextEntry = entriesToProcess.pop();
		//logger.trace('Processing %s. Is it a file or a dir?', nextEntry);
		return q.nfcall(fs.stat, nextEntry).then(function (stat) {
			if (stat.isFile()) {
				logger.trace('%s is a file, determining type...', nextEntry);
				return q.nfcall(mime, nextEntry).then(function (mimeType) {
					if (_.contains(MEDIA_FILE_MIME_TYPES, mimeType)) {
						logger.info('Added %s.', nextEntry);
						return MediaFile.fromPath(nextEntry).then(function (mediaFile) {
							return config.modelDAO.putMediaFile(mediaFile).fail(function (err) {
								if (err instanceof Error) {
									console.log(err);
								} else {
									logger.warn('Error saving MediaFile %j: %s.', mediaFile.toJSON(), err);
								}
							});
						});
					} else {
						//For some reason '_.contains' did not work here.
						var isKnownBadMimeType = _.some(NOT_MEDIA_FILE_MIME_TYPES, function (nmf) {
							return nmf === mimeType;
						});
						if (!isKnownBadMimeType) {
							logger.warn('Unknown mime type "%s" for file %s.', mimeType, nextEntry);
						}
						return;
					}
				});
			} else if (stat.isDirectory()) {
				//logger.trace('%s is a directory. Gettings its contents...', nextEntry);
				return q.nfcall(fs.readdir, nextEntry).then(function(dirContents) {

					var absolutePaths = _.map(dirContents, function (relPath) {
						return path.join(nextEntry, relPath);
					});
					/*
					 * Shuffled so that we don't scan the same directories in the same
					 * order every time.
					 */
					absolutePaths = _.shuffle(absolutePaths);
					entriesToProcess = entriesToProcess.concat(absolutePaths);
				});
			}
		}).fail(function (err) {
			if (err instanceof Error) {
				if (err.errno === 34 && err.code === 'ENOENT') {
					/*
					 * This can happen if we found a symbolic link, but the file that the
					 * symbolic points to does not exist. In this case, we just ignore
					 * this file, and continue on.
					 */
					return;
				}
			}
			//This is some failure we don't know how to handle, so rethrow it.
			throw err;
		}).then(function () {
			return module.exports.addMediaFileToDB(entriesToProcess);
		});
	};
}());