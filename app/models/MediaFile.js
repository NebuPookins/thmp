/*globals __filename, module, require*/
(function () {
	'use strict';

	var _      = require('underscore');
	var assert = require('assert');
	var id3js  = require('id3js');
	var log4js = require('log4js');
	var mime   = require('mime-magic');
	var q      = require('q');

	var logger = log4js.getLogger(__filename);

	function assertType(asserter, key, value) {
		if (!asserter(value)) {
			logger.error(
				'Expected %s to be string, but was %s. Value is %j.',
				key, typeof value, value
			);
			assert.ok(false);
		}
	}

	function isStringOrNull(value) {
		return value === null || _.isString(value);
	}

	function MediaFile(path, mimeType, artists, title, album, year, tag) {
		assertType(_.isString, 'path', path);
		assertType(_.isString, 'mimeType', mimeType);
		assertType(_.isArray, 'artists', artists);
		assertType(isStringOrNull, 'title', title);
		assertType(isStringOrNull, 'album', album);
		assertType(isStringOrNull, 'year', year);
		this.path = path;
		this.mimeType = mimeType;
		this.artists = artists;
		this.title = title;
		this.album = album;
		this.year = year;
		this.tag = tag;
	}

	/**
	 * Asynchronously creates an instance of MediaFile, based on a file.
	 * 
	 * @param  {String} path path to the file to use for the MediaFile.
	 * @return {Object} Q promise that resolves to the MediaFile instance.
	 */
	MediaFile.fromPath = function (path) {
		assert.ok(_.isString(path));
		var promisedTags = q.nfcall(id3js, {file: path, type: id3js.OPEN_LOCAL});
		var promisedMime = q.nfcall(mime, path);
		return q.all([promisedMime, promisedTags]).spread(function (mimeType, tags) {
			var artists;
			if (tags.artist === null) {
				artists = [];
			} else {
				artists = [tags.artist];
			}
			return new MediaFile(
				path, mimeType, artists, tags.title, tags.album, tags.year, tags
			);
		});
	};

	MediaFile.prototype.toJSON = function() {
		return {
			path: this.path,
			mimeType: this.mimeType,
			artists: this.artists,
			title: this.title,
			album: this.album,
			year: this.year,
			tag: this.tag
		};
	};

	module.exports = MediaFile;

})();