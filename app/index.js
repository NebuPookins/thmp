/*globals require, __filename, process, __dirname*/

(function() {
	'use strict';
	//configurable constants
	var PORT = 3000;
	var DB_FILENAME = 'songDb.1.sqlite';

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
		'application/msword',
		'application/octet-stream',
		'application/pdf',
		'application/postscript',
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
		'application/x-archive',
		'application/x-bittorrent',
		'application/x-cpio',
		'application/x-dosexec',
		'application/x-dvi',
		'application/x-font-sfn',
		'application/x-font-ttf',
		'application/x-gnupg-keyring',
		'application/x-gzip',
		'application/x-iso9660-image',
		'application/x-java-applet',
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
	];

	//requires
	var _           = require('underscore');
	var assert      = require('assert');
	var consolidate = require('consolidate');
	var express     = require('express');
	var fs          = require('fs');
	var log4js      = require('log4js');
	var mime        = require('mime-magic');
	var path        = require('path');
	var q           = require('q');
	var SQLiteDAO   = require('./models/SQLiteDAO');
	var MediaFile   = require('./models/MediaFile');

	//Configuring requires
	var ROOT = path.join(__dirname, '..');
	var logger = log4js.getLogger(__filename);
	q.longStackSupport = true;
	var sqliteDao = new SQLiteDAO(path.join(ROOT, DB_FILENAME));

	/**
	 * Returns a promise that, when resolved, means all the files in the specified
	 * directory have been processed.
	 */
	function addMediaFileToDB(entriesToProcess) {
		//logger.trace('Entering addMediaFileToDB2.');
		assert.ok(_.isArray(entriesToProcess));
		if (entriesToProcess.length === 0) {
			//logger.trace('All entries processed. addMediaFileToDB2 is done.');
			return q.promise(function (resolve) { resolve(); });
		}
		var nextEntry = entriesToProcess.pop();
		//logger.trace('Processing %s. Is it a file or a dir?', nextEntry);
		return q.nfcall(fs.stat, nextEntry).then(function (stat) {
			if (stat.isFile()) {
				//logger.trace('%s is a file, determining type...', nextEntry);
				return q.nfcall(mime, nextEntry).then(function (mimeType) {
					if (_.contains(MEDIA_FILE_MIME_TYPES, mimeType)) {
						//logger.info('Added %s.', nextEntry);
						return MediaFile.fromPath(nextEntry).then(function (mediaFile) {
							return sqliteDao.putMediaFile(mediaFile).fail(function (err) {
								logger.warn('Error saving MediaFile %j: %j.', mediaFile.toJSON(), err);
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
		}).then(function () {
			return addMediaFileToDB(entriesToProcess);
		});
	}

	function getUserHome() {
		return process.env[(process.platform === 'win32') ? 'USERPROFILE' : 'HOME'];
	}

	logger.info('thmp starting up...');

	addMediaFileToDB([getUserHome()]).then(function() {
		logger.info('Finished scanning for media files.');
	}).done();

	var app = express();

	app.engine('html', consolidate.jade); //Assign the JADE engine to .html files
	app.set('view engine', 'html'); //Set .html as the default extension
	app.set('views', path.join(ROOT, 'views'));

	//Log all requests
	app.use(function(req, res, next) {
		//logger.trace('%s %s', req.method, req.url);
		next();
	});

	app.get('/api/1/ping', function(req, res){
		res.send('pong');
	});

	app.get('/api/1/mp3/', function(req, res) {
		sqliteDao.listMediaFile().then(function (results) {
			var jsonResults = _.map(results, function (mediaFile) {
				return mediaFile.toJSON();
			});
			res.json(200, jsonResults);
		}).done();
	});

	app.get('/api/1/mp3/*', function(req, res) {
		var requestedPath = '/' + req.params[0];
		sqliteDao.findMediaFileByPath(requestedPath).then(function (results) {
			if (results) {
				return res.sendfile(results.path);
			} else {
				return res.send(404);
			}
		}).done();
	});

	app.get('/', function (req, res) {
		res.render('index.jade', {});
	});

	//PUBLIC FOLDER
	app.use('/public', express.static(path.join(ROOT, 'public')));

	//STATIC FILES
	app.use('/bootstrap', express.static(path.join(ROOT, 'vendor/bootstrap-3.0.3-dist')));
	app.get('/jquery.js', function (req, res) {
		res.sendfile(path.join(ROOT, 'vendor/jquery-2.0.3.js'));
	});
	app.get('/underscore.js', function (req, res) {
		res.sendfile(path.join(ROOT, 'node_modules/underscore/underscore.js'));
	});

	app.listen(PORT);
	logger.info('thmp server listening on port %d.', PORT);
}());