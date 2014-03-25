
package info.guardianproject.securereaderinterface.musicplayer;

import info.guardianproject.securereaderinterface.musicplayer.ParcelableMediaContent;

interface MusicPlayerInterface 
{
	void clearPlaylist();
	void addToPlaylist( in ParcelableMediaContent media ); 
	void play( in int position );
	void playWithId( in long id );
	void pause();
	void resume();
	void stop();
	void previous();
	void next();
	boolean isPlaying();
	boolean isPaused();
} 
