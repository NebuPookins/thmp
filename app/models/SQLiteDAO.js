/*globals require, module*/

(function () {
	'use strict';

	var _         = require('underscore');
	var assert    = require('assert');
	var MediaFile = require('./MediaFile');
	var dblite    = require('dblite');
	var q         = require('q');

	function SQLiteDAO(pathToDb) {
		this.db = dblite(pathToDb);
		//Create initial schema
		this.db.query([
			'CREATE TABLE IF NOT EXISTS MediaFile (',
			'	id INTEGER PRIMARY KEY,',
			'	path VARCHAR(256),',
			'	artists VARCHAR(256),',
			'	title VARCHAR(256),',
			'	album VARCHAR(256),',
			'	year VARCHAR(10),',
			'	tag TEXT',
			')'
		].join(' '));
		this.db.query('CREATE UNIQUE INDEX IF NOT EXISTS idxMediaFile_path ON MediaFile (path)');
	}

	SQLiteDAO.prototype.putMediaFile = function (mediaFile) {
		assert.ok(mediaFile instanceof MediaFile);
		var deferred = q.defer();
		this.db.query([
				'INSERT OR REPLACE INTO MediaFile',
				'(path, artists, title, album, year, tag) VALUES(',
				':path, :artists, :title, :album, :year, :tag)'
			].join(' '),
			{
				path: mediaFile.path,
				artists: JSON.stringify(mediaFile.artists),
				title: mediaFile.title,
				album: mediaFile.album,
				year: mediaFile.year,
				tag: JSON.stringify(mediaFile.tag)
			},
			function (err) {
				if (err) {
					deferred.reject(err);
				} else {
					deferred.resolve();
				}
			}
		);
		return deferred.promise;
	};

	function selectMediaFile(db, whereClause, queryParams) {
		return q.promise(function (resolve, reject) {
			db.query(
				'SELECT path, artists, title, album, year, tag FROM MediaFile ' + whereClause,
				queryParams,
				{
					path: String,
					artists: JSON.parse,
					title: String,
					album: String,
					year: String,
					tag: JSON.parse
				},
				function (err, results) {
					if (err) {
						return reject(err);
					}
					assert.ok(_.isArray(results));
					var objs = _.map(results, function (row) {
						return new MediaFile(row.path, row.artists, row.title, row.album, row.year, row.tag);
					});
					return resolve(objs);
				});
		});
	}

	SQLiteDAO.prototype.listMediaFile = function() {
		return selectMediaFile(this.db, '', {});
	};

	SQLiteDAO.prototype.findMediaFileByPath = function(path) {
		return selectMediaFile(
			this.db, 'WHERE path = :path', {path: path}
		).then(function(results) {
			assert.ok(_.isArray(results));
			assert.ok(results.length === 0 || results.length === 1);
			if (results.length === 0) {
				return null;
			} else {
				return results[0];
			}
		});
	};
	module.exports = SQLiteDAO;

})();