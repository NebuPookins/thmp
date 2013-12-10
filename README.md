thmp: **TH**eoretical **M**usic **P**layer, pronounced like a bass drum kick.

# What is it?

A desktop application that figures out what kind of music you like, and plays
that music for you, without forcing you to worry about details such as whether
or not the music exists as a file on your harddrive.

You have mp3s on your harddrive? Great, I'll play those. Oh, it looks like a lot
of these mp3 files are recordings of Daft Punk. It looks like Daft Punk
released a new single last week, and the song's available on YouTube. I'll play
that for you next. And it looks like there's a bootleg Daft Punk mashup on
Soundcloud. I'll queue that one up for you right after this Grooveshark mix I
found.

# Is it ready for the public?

NO. This is an early alpha, and most of the functionality is not written yet.
Contributions welcome.

# What's the licensing like?

Undecided, but will be open source. E.g. if we use Sencha ExtJS for the UI,
it'll probably end up being GPL3.

# How do I build it locally?

    git clone https://github.com/NebuPookins/thmp.git
    cd thmp
    npm install
    npm start
    # Load up http://localhost:3000/ in your browser
    # Optional: Use set up grunt for jshint checking
    npm install grunt-cli --global
    grunt

# What's the official homepage?

https://github.com/NebuPookins/thmp

# What's the vision/philosophy?

* There's the "abstract, platonic ideal of a song" and there's the "physical
  manifestation as a stream of bytes". Most other music players make the latter
  the axiomatic element upon which the rest of the software is based. Thmp
  focuses on the former. If I want to hear Eminem's latest hit, I don't care if
  the data comes from an mp3 file, from GrooveShark, from Youtube, or whatever,
  I just want to listen to the damn song.
* MusicBrainz has put [a lot of thought](http://musicbrainz.org/doc/MusicBrainz_Database/Schema)
  into explicitly modeling this distinction between the platonic form of the
  song (a "[work](http://musicbrainz.org/doc/Work)"), and its manifestations
  (a "[recording/Released Track](http://musicbrainz.org/doc/Recording)"). We
  want to reuse as much of this thought as possible in our design.
* When reading tagging data from a media file (e.g. ID3v2 tags from an mp3 file),
  this information should only be used to help identify/associate the concrete
  media file with the platonic song/work. One implication of this is that a
  single manifestation (e.g. a single mp3 file) can serve as the recording for
  multiple albums (e.g. if the same song appears on multiple albums), even though
  the ID3v2 tag only lists a single album.
* For many tagging formats, there is no convenient way to encoding the metadata
  that thmp derives back into the file (for example, it's not obviously
  desirable to encode every album a given track appears on in ID3v2). As such,
  thmp will perform little-to-no writing of tags back onto the media files. At
  most, it might write the PUID and MBID into extended attributes in the tag.
* MusicBrainz uses some terms which are more pedantically correct, but which
  [essentially map](http://musicbrainz.org/doc/MusicBrainz_Picard/Tags/Mapping)
  onto commonly used existing terms. For example, MusicBrainz uses the term
  "Release" which maps onto what ID3v2, Vorbis, etc. all call "Album". In this
  case, we prefer user familiarity over pendantry, and thus will refer to the
  concept as "Album", not "Release".
