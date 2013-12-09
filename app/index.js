//configurable constants
const PORT = 3000;
const DB_FILENAME = ':memory:';

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
				if (mimeType === "audio/mpeg") {
					db.query('INSERT INTO MediaFile VALUES(null, ?)', [nextEntry]);
				} else {
					//TODO
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

logger.info('thmp starting up...');

addMediaFileToDB(['/home/nebu/mnt/documents/My Music/']).done();

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
	console.log(req.params[0]);
	var requestedPath = '/' + req.params[0];
	db.query('SELECT path FROM MediaFile WHERE path = ?', [requestedPath], function (results) {
		console.log(results);
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

app.listen(PORT);
logger.info('thmp server listening on port %d.', PORT);
