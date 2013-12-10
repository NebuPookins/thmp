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
