/*globals __filename, process, require*/

(function() {
	'use strict';

	//requires
	var _           = require('underscore');
	var config      = require('../config');
	var consolidate = require('consolidate');
	var express     = require('express');
	var path        = require('path');
	var mediaFileScanner = require('./proc/mediaFileScanner');

	var logger = config.getLogger(__filename);

	function getUserHome() {
		return process.env[(process.platform === 'win32') ? 'USERPROFILE' : 'HOME'];
	}

	logger.info('thmp starting up...');

	mediaFileScanner.addMediaFileToDB(getUserHome()).then(function() {
		logger.info('Finished scanning for media files.');
	}).done();

	var app = express();

	app.engine('html', consolidate.jade); //Assign the JADE engine to .html files
	app.set('view engine', 'html'); //Set .html as the default extension
	app.set('views', path.join(config.paths.root, 'views'));

	//Log all requests
	app.use(function(req, res, next) {
		//logger.trace('%s %s', req.method, req.url);
		next();
	});

	app.get('/api/1/ping', function(req, res){
		res.send('pong');
	});

	app.get('/api/1/mp3/', function(req, res) {
		config.modelDAO.listMediaFile().then(function (results) {
			var jsonResults = _.map(results, function (mediaFile) {
				return mediaFile.toJSON();
			});
			res.json(200, jsonResults);
		}).done();
	});

	app.get('/api/1/mp3/*', function(req, res) {
		var requestedPath = '/' + req.params[0];
		config.modelDAO.findMediaFileByPath(requestedPath).then(function (results) {
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
	app.use('/public', express.static(path.join(config.paths.root, 'public')));

	//STATIC FILES
	app.use('/bootstrap', express.static(path.join(config.paths.root, 'vendor/bootstrap-3.0.3-dist')));
	app.get('/jquery.js', function (req, res) {
		res.sendfile(path.join(config.paths.root, 'vendor/jquery-2.0.3.js'));
	});
	app.get('/underscore.js', function (req, res) {
		res.sendfile(path.join(config.paths.root, 'node_modules/underscore/underscore.js'));
	});

	app.listen(config.port);
	logger.info('thmp server listening on port %d.', config.port);
}());