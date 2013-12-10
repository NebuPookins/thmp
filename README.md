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

# TODO

* Implement fingerprinting/PUID lookup. http://musicbrainz.org/doc/How_PUIDs_Work
