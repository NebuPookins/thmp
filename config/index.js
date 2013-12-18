/*globals __dirname, module, process, require*/
(function() {
	'use strict';

	var IS_PROD = process.env.NODE_ENV === 'production';
	var IS_DEV = !IS_PROD;

	var _         = require('underscore');
	var _s        = require('underscore.string');
	var assert    = require('assert');
	var log4js    = require('log4js');
	var ModelDAO  = require('../app/models/ModelDAO.interface');
	var path      = require('path');
	var q         = require('q');

	var config = {};

	/**
	 * In DEV, enable q long stack support globally.
	 */
	if (IS_DEV) {
		q.longStackSupport = true;
	}

	/**
	 * `paths` is a dictionary from strings to absolute paths.
	 */
	config.paths = {
		root: path.join(__dirname, '..')
	};
	/**
	 * `port` is the port number that the main HTTP server is going to listen
	 * upon.
	 */
	config.port = 3000;

	/**
	 * `modelDAO` is the DAO (Data Access Object) used to modify the model objects
	 * and persist or retrieve them.
	 */
	if (IS_DEV) { 
		config.getModelDAO = _.once(function() {
			/*
			 * For development, we use an SQLite3 store that writes to a file in the
			 * root folder.
			 */
			var SQLiteDAO = require('../app/models/SQLiteDAO');
			var DB_FILENAME = 'songDb.1.sqlite';
			var retVal = new SQLiteDAO(path.join(config.paths.root, DB_FILENAME));
			ModelDAO.assertIsImplementedBy(retVal);
			return retVal;
		})
	} else {
		assert.ok(IS_PROD);
		throw new Error("TODO: Not implemented yet.");
	}
	

	config.getLogger = function(absolutePath) {
		var relativePath = path.relative(config.paths.root, absolutePath);
		var logger = log4js.getLogger(relativePath);
		if (IS_DEV) {
			if (_s.startsWith(relativePath, "app/proc/mediaFileScanner")) {
				logger.setLevel(log4js.levels.INFO);
			} else {
				logger.setLevel(log4js.levels.ALL);
			}
		} else {
			assert.ok(IS_PROD);
			logger.setLevel(log4js.levels.WARN);
		}
		return logger;
	};

	/*
	 * Now that we've finished building the config object, freeze it and export
	 * it.
	 */
	module.exports = Object.freeze(config);
}());