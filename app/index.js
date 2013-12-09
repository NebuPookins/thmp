//configurable constants
const PORT = 3000;
const DB_FILENAME = ':memory:';

const MEDIA_FILE_MIME_TYPES = [
	'application/ogg',
	'application/vnd.rn-realmedia', //TODO: check this one
	'application/x-shockwave-flash', //TODO: check this one
	'audio/mpeg',
	'audio/x-wav',
	'video/3gpp',
	'video/mp4',
	'video/mpeg',
	'video/quicktime',
	'video/webm',
	'video/x-flv',
	'video/x-matroska',
	'video/x-ms-asf',
	'video/x-msvideo',
];

const NOT_MEDIA_FILE_MIME_TYPES = [
	'application/CDFV2-corrupt',
	'application/jar',
	'application/octet-stream',
	'application/pdf',
	'application/vnd.ms-cab-compressed',
	'application/vnd.ms-office',
	'application/vnd.ms-opentype',
	'application/x-dosexec',
	'application/x-font-ttf',
	'application/x-gnupg-keyring',
	'application/x-gzip',
	'application/x-java-applet',
	'application/x-pgp-keyring',
	'application/x-rar',
	'application/xml',
	'application/zip',
	'image/gif',
	'image/jpeg',
	'image/png',
	'image/svg+xml',
	'image/vnd.adobe.photoshop',
	'image/x-icon',
	'image/x-ms-bmp',
	'image/x-paintnet',
	'inode/x-empty',
	'message/news',
	'message/rfc822',
	'text/html',
	'text/plain',
	'text/rtf',
	'text/x-asm',
	'text/x-c',
	'text/x-c++',
	'text/x-diff',
	'text/x-msdos-batch',
	'text/x-pascal',
	'text/x-perl',
	'text/x-php',
	'text/x-po',
	'text/x-python',
	'text/x-shellscript',
];

//requires
const _           = require('underscore');
const assert      = require('assert');
const consolidate = require('consolidate');
const express     = require('express');
const fs          = require('fs');
const log4js      = require('log4js');
const mime        = require('mime-magic');
const dblite      = require('dblite');
const path        = require('path');
const q           = require('q');
const util        = require('util');

//Configuring requires
const logger = log4js.getLogger(__filename);
q.longStackSupport = true;
const db = dblite(DB_FILENAME);

//Create initial schema
db.query('CREATE TABLE IF NOT EXISTS MediaFile (id INTEGER PRIMARY KEY, path VARCHAR(256))');

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
					db.query('INSERT INTO MediaFile VALUES(null, ?)', [nextEntry]);
				} else {
					//For some reason '_.contains' did not work here.
					var isKnownBadMimeType = _.some(NOT_MEDIA_FILE_MIME_TYPES, function (nmf) {
						return nmf == mimeType;
					});
					if (!isKnownBadMimeType) {
						logger.warn('Unknown mime type "%s" for file %s.', mimeType, nextEntry);
					}
				}
			});
		} else if (stat.isDirectory()) {
			//logger.trace('%s is a directory. Gettings its contents...', nextEntry);
			return q.nfcall(fs.readdir, nextEntry).then(function(dirContents) {
				const absolutePaths = _.map(dirContents, function (relPath) {
					return path.join(nextEntry, relPath);
				})
				entriesToProcess = entriesToProcess.concat(absolutePaths);
			});
		}
	}).then(function () {
		return addMediaFileToDB(entriesToProcess);
	});
}

function getUserHome() {
	return process.env[(process.platform == 'win32') ? 'USERPROFILE' : 'HOME'];
}

logger.info('thmp starting up...');

addMediaFileToDB([getUserHome()]).then(function() {
	logger.info('Finished scanning for media files.');
}).done();

const app = express();

const ROOT = path.join(__dirname, '..');

app.engine('html', consolidate.jade); //Assign the JADE engine to .html files
app.set('view engine', 'html'); //Set .html as the default extension
app.set('views', path.join(ROOT, 'views'));

//Log all requests
app.use(function(req, res, next) {
	logger.trace('%s %s', req.method, req.url);
	next();
});

app.get('/api/1/ping', function(req, res){
	res.send('pong');
});

app.get('/api/1/mp3/', function(req, res) {
	db.query('SELECT path FROM MediaFile', function (results) {
		res.json(200, results);
	});
});

app.get('/api/1/mp3/*', function(req, res) {
	var requestedPath = '/' + req.params[0];
	db.query('SELECT path FROM MediaFile WHERE path = ?', [requestedPath], function (results) {
		assert.ok(results.length === 0 || results.length === 1);
		if (results.length === 1) {
			res.sendfile(results[0]);
		} else {
			res.send(404);
		}
	});
});

app.get('/', function (req, res) {
	res.render('index.jade', {});
});

//STATIC FILES
app.use('/bootstrap', express.static(path.join(ROOT, 'vendor/bootstrap-3.0.3-dist')));
app.get('/jquery.js', function (req, res) {
	res.sendfile(path.join(ROOT, 'vendor/jquery-2.0.3.js'))
});
app.get('/underscore.js', function (req, res) {
	res.sendfile(path.join(ROOT, 'node_modules/underscore/underscore.js'))
});

app.listen(PORT);
logger.info('thmp server listening on port %d.', PORT);
