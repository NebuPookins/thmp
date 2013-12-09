/*globals $, setTimeout, _, console*/

$(function() {
	'use strict';
	var $songListTable = $('#songList tbody');
	var $audioTag = $('#audioTag');
	$songListTable.on('click', 'tr', function () {
		var url = $(this).data('url');
		$audioTag.empty();
		$audioTag.append('<source src="'+url+'" />'); //TODO add "type"
	});
	function getSongsFromBackend() {
		console.log('Refreshing song list...');
		$.ajax({
			type: 'GET',
			url: '/api/1/mp3/',
			accepts: 'application/json',
			dataType: 'json',
		}).done(function (songData) {
			$songListTable.empty();
			_.each(songData, function (song) {
				var url = '/api/1/mp3' + song[0];
				var $newRow = $('<tr data-url="'+url+'"><td>1</td><td>'+song[0]+'</td></tr>');
				$songListTable.append($newRow);
			});
		}).fail(function () {
			console.log(arguments); //TODO
		});
		setTimeout(getSongsFromBackend, 1000 * 60);
	}
	getSongsFromBackend();
});