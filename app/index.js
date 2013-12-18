/*globals require, __filename, process, __dirname*/

(function() {
	'use strict';
	//configurable constants
	var PORT = 3000;
	var DB_FILENAME = 'songDb.1.sqlite';

	//requires
	var _           = require('underscore');
	var consolidate = require('consolidate');
	var express     = require('express');
	var log4js      = require('log4js');
	var path        = require('path');
	var q           = require('q');
	var SQLiteDAO   = require('./models/SQLiteDAO');
	var mediaFileScanner = require('./proc/mediaFileScanner');

	//Configuring requires
	var ROOT = path.join(__dirname, '..');
	var logger = log4js.getLogger(path.relative(ROOT, __filename));
	logger.setLevel(log4js.levels.INFO);
	q.longStackSupport = true;
	var sqliteDao = new SQLiteDAO(path.join(ROOT, DB_FILENAME));

	function getUserHome() {
		return process.env[(process.platform === 'win32') ? 'USERPROFILE' : 'HOME'];
	}

	logger.info('thmp starting up...');

	mediaFileScanner.addMediaFileToDB([getUserHome()]).then(function() {
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