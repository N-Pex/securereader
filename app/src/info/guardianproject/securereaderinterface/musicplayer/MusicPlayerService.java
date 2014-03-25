package info.guardianproject.securereaderinterface.musicplayer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Map;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.securereader.MediaDownloader.MediaDownloaderCallback;
import info.guardianproject.securereader.SocialReader;
import info.guardianproject.securereaderinterface.App;
import info.guardianproject.idaho.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;



public class MusicPlayerService extends Service implements OnCompletionListener, OnPreparedListener, OnErrorListener
{
	public static final String BROADCAST_ACTION_PROGRESS = "info.guardianproject.securereaderinterface.musicplayer.action.broadcast.progress";
	public static final String BROADCAST_ACTION_DURATION = "info.guardianproject.securereaderinterface.musicplayer.action.broadcast.duration";
	public static final String BROADCAST_ACTION_TRACK_INFO = "info.guardianproject.securereaderinterface.musicplayer.action.broadcast.trackinfo";
	public static final String BROADCAST_ACTION_STATUS = "info.guardianproject.securereaderinterface.musicplayer.action.broadcast.status";

	public static final String EXTRA_PROGRESS = "info.guardianproject.securereaderinterface.musicplayer.extra.progress";
	public static final String EXTRA_TRACK = "info.guardianproject.securereaderinterface.musicplayer.extra.track";
	public static final String EXTRA_ARTIST = "info.guardianproject.securereaderinterface.musicplayer.extra.artist";
	public static final String EXTRA_DURATION = "info.guardianproject.securereaderinterface.musicplayer.extra.duration";
	public static final String EXTRA_STATUS = "info.guardianproject.securereaderinterface.musicplayer.extra.status";

	public static final int STATUS_IDLE = 0;
	public static final int STATUS_PAUSED = 1;
	public static final int STATUS_PLAYING = 2;
	public static final int STATUS_LOADING = 3;
	
	private static final String ACTION_PAUSE = "info.guardianproject.securereaderinterface.musicplayer.action.pause";
	private static final String ACTION_PLAY = "info.guardianproject.securereaderinterface.musicplayer.action.play";
	private static final String ACTION_NEXT = "info.guardianproject.securereaderinterface.musicplayer.action.next";
	private static final String ACTION_PREVIOUS = "info.guardianproject.securereaderinterface.musicplayer.action.previous";
	private static final String ACTION_STOP = "info.guardianproject.securereaderinterface.musicplayer.action.stop";
	
	private static final int NOTIFICATION_ID = 1;
	private static final String LOG = "MusicPlayerService";

	private static final int DEFAULT_PORT = 8080;
	
	private MediaPlayer mMediaPlayer;
	private ArrayList<MediaContent> mPlaylist;
	private int mCurrentPlaylistIndex;

	private NotificationManager mNM;
	
	private MediaHTTPD mMediaServer;
	private int mMediaServerPort;
	
	private PendingIntent mIntentPause;
	private PendingIntent mIntentPlay;
	private PendingIntent mIntentNext;
	private PendingIntent mIntentPrevious;
	private PendingIntent mIntentStop;
	
	private boolean mPaused;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnErrorListener(this);
		
		mNM = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
		mPlaylist = new ArrayList<MediaContent>();
		mCurrentPlaylistIndex = 0;
		registerReceiver(mBroadcastReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
		
		mIntentPause = PendingIntent.getService(
				getApplicationContext(), 0,
                new Intent(getApplicationContext(), MusicPlayerService.class).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT);
		mIntentPlay = PendingIntent.getService(
				getApplicationContext(), 0,
                new Intent(getApplicationContext(), MusicPlayerService.class).setAction(ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT);
		mIntentNext = PendingIntent.getService(
				getApplicationContext(), 0,
                new Intent(getApplicationContext(), MusicPlayerService.class).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT);
		mIntentPrevious = PendingIntent.getService(
				getApplicationContext(), 0,
                new Intent(getApplicationContext(), MusicPlayerService.class).setAction(ACTION_PREVIOUS),
                PendingIntent.FLAG_UPDATE_CURRENT);
		mIntentStop = PendingIntent.getService(
				getApplicationContext(), 0,
                new Intent(getApplicationContext(), MusicPlayerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
		
		mNotification = new Notification();
		mNotification.icon = R.drawable.ic_launcher;
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		//mNotification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), "Playing: " + item.getTitle(), null);

		RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.music_player_notification);
		contentView.setOnClickPendingIntent(R.id.ivPlayPause, mIntentPause);
		contentView.setOnClickPendingIntent(R.id.ivMediaNext, mIntentNext);
		contentView.setOnClickPendingIntent(R.id.ivClose, mIntentStop);
		contentView.setTextViewText(R.id.tvTitle, "");
		contentView.setTextViewText(R.id.tvSubTitle, "");
		mNotification.contentView = contentView;
	}

	@Override
	public void onDestroy()
	{
		mMediaPlayer.stop();
		mMediaPlayer.release();
		mNM.cancel(NOTIFICATION_ID);
		unregisterReceiver(mBroadcastReceiver);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (ACTION_PAUSE.equals(intent.getAction()))
		{
			pause();
		}
		else if (ACTION_PLAY.equals(intent.getAction()))
		{
			play();
		}
		else if (ACTION_NEXT.equals(intent.getAction()))
		{
			playNext();
		}
		else if (ACTION_STOP.equals(intent.getAction()))
		{
			stop();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) 
	{
		return mBinder;
	}
	
    private final MusicPlayerInterface.Stub mBinder = new MusicPlayerInterface.Stub()
    {
    	public void play(int position) throws DeadObjectException 
    	{
    		playMedia(position);
    	}

		@Override
		public void addToPlaylist(ParcelableMediaContent media) throws RemoteException
		{
			mPlaylist.add(media.getMediaContent());
		}
		
    	public void clearPlaylist() throws DeadObjectException
    	{
    		mPlaylist.clear();
    	}

    	public void pause() throws DeadObjectException 
    	{
    		MusicPlayerService.this.pause();
    	}

    	public void stop() throws DeadObjectException
    	{
    		MusicPlayerService.this.stop();
    	}

		@Override
		public void resume() throws RemoteException
		{
			MusicPlayerService.this.play();
		}

		@Override
		public boolean isPlaying() throws RemoteException
		{
			return mMediaPlayer.isPlaying();
		}

		@Override
		public boolean isPaused() throws RemoteException
		{
			return mPaused;
		}

		@Override
		public void previous() throws RemoteException
		{
			MusicPlayerService.this.playPrevious();
		}
		
		@Override
		public void next() throws RemoteException
		{
			MusicPlayerService.this.playNext();
		}

		@Override
		public void playWithId(long id) throws RemoteException
		{
			int position = getPlaylistIndexOfMediaId(id);
			if (position != -1)
				playMedia(position);
		}
    };
	private Notification mNotification;
    
	private void playMedia(int position) 
	{
		try
		{
			if (mMediaPlayer.isPlaying())
				mMediaPlayer.stop();
			
			mCurrentPlaylistIndex = position;
			if (mCurrentPlaylistIndex >= mPlaylist.size())
				return;
			
			MediaContent media = mPlaylist.get(mCurrentPlaylistIndex);
			
			SocialReader socialReader = App.getInstance().socialReader;
			
			if (!socialReader.isMediaContentLoaded(media))
			{
				if (socialReader.loadMediaContent(media, new MediaContentLoadedCallback(media), true))
					updateStatus(STATUS_LOADING);
			}
			else
			{
				doPlayLoadedMedia(media);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void playNext() 
	{
		playMedia(mCurrentPlaylistIndex + 1);
	}

	private void playPrevious() 
	{
		playMedia(mCurrentPlaylistIndex - 1);
	}
	
	private void play()
	{
		mMediaPlayer.start();
		startProgressThread();
		mPaused = false;
		updateStatus(STATUS_PLAYING);
	}
	
	private void pause()
	{
		mMediaPlayer.pause();
		mPaused = true;
		updateStatus(STATUS_PAUSED);
	}

	private void stop()
	{
		mPaused = false;
		mMediaPlayer.stop();
		updateStatus(STATUS_IDLE);
	}
	
	private int getPlaylistIndexOfMediaId(long id)
	{
		for (int i = 0; i < mPlaylist.size(); i++)
		{
			MediaContent mc = mPlaylist.get(i);
			if (id == mc.getDatabaseId())
			{
				return i;
			}
		}
		return -1;
	}
	
	private void doPlayLoadedMedia(MediaContent media)
	{
		try
		{
			SocialReader socialReader = App.getInstance().socialReader;
			Item item = socialReader.getItemFromId(media.getItemDatabaseId());
			
			// Let our listeners know (and update notification)
			//
			updateStatus(STATUS_PLAYING);
			updateNowPlaying(item);
			
			mMediaPlayer.reset();
			
			if (mMediaServer == null)
			{
				initMediaServer();
			}
			Uri mediaUri = Uri.parse("http://localhost:" + mMediaServerPort + "/media/" + media.getDatabaseId());
			mMediaPlayer.setDataSource(mediaUri.toString());	
			mMediaPlayer.prepareAsync();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void initMediaServer()
	{
		if (mMediaServer == null)
		{
			for (int i = 0; i < 100; i++)
			{
				try
				{
					mMediaServerPort = DEFAULT_PORT + i;
					mMediaServer = new MediaHTTPD(mMediaServerPort);
					mMediaServer.start();
					break;
				}
				catch (IOException e)
				{
					Log.d(LOG, "Failed to start service on port " + mMediaServerPort);
				}
			}
		}
	}
	
	private class MediaHTTPD extends NanoHTTPD
	{
		public MediaHTTPD(int port) throws IOException
		{
			super(port);
		}

		@Override
		public Response serve(String uri, Method method, Map<String, String> header, Map<String, String> parms, Map<String, String> files)
		{
			Log.v(LOGTAG, "Request for: " + uri);

			if (uri.startsWith("/media/"))
			{
				try
				{
					String mediaId = uri.substring(7);
				
					File mediaFile = new File(App.getInstance().socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaId);
					FileInputStream fin = new FileInputStream(mediaFile);

					Response response = new Response(Status.OK, "audio/mpeg", fin);
					response.addHeader("Content-Length", "" + mediaFile.length());

					return response;
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			return null;
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) 
	{
		playNext();
	}

	@Override
	public void onPrepared(MediaPlayer mp) 
	{
		mp.start();
		mPaused = false;
		updateDuration(mMediaPlayer.getDuration() / 1000);
		startProgressThread();
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra)
	{
		//TODO - Error handling here!
		return true;
	}

	private void onMediaDownloaded(MediaContent media)
	{
		// Is this the one we are playing?
		//
		int position = getPlaylistIndexOfMediaId(media.getDatabaseId());
		if (position != -1 && position == mCurrentPlaylistIndex)
		{
			doPlayLoadedMedia(media);
		}
	}
	
	private void startProgressThread()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					while (mMediaPlayer != null && mMediaPlayer.isPlaying() && mMediaPlayer.getCurrentPosition() < mMediaPlayer.getDuration())
					{
						int progress = mMediaPlayer.getCurrentPosition() / 1000;
						updateProgress(progress);
						try
						{
							Thread.sleep(1000);
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
					}
				}
				catch (Exception e)
				{
				}
			}
		}).start();
	}
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
			{
				pause();
			}
		}
	};
	
	private class MediaContentLoadedCallback implements MediaDownloaderCallback
	{
		private MediaContent mMedia;

		public MediaContentLoadedCallback(MediaContent media)
		{
			mMedia = media;
		}

		@Override
		public void mediaDownloaded(File mediaFile)
		{
			onMediaDownloaded(mMedia);
		}

		@Override
		public void mediaDownloadedNonVFS(java.io.File mediaFile)
		{
		}
	}
	
	private void updateNowPlaying(Item item)
	{
		mNotification.contentView.setTextViewText(R.id.tvTitle, item.getTitle());
		mNotification.contentView.setTextViewText(R.id.tvSubTitle, item.getAuthor());
		mNM.notify(NOTIFICATION_ID, mNotification);

		Intent intent = new Intent(BROADCAST_ACTION_TRACK_INFO);
		intent.putExtra(EXTRA_TRACK, item.getTitle());
		intent.putExtra(EXTRA_ARTIST, item.getAuthor());
		LocalBroadcastManager.getInstance(MusicPlayerService.this).sendBroadcast(intent);
	}
	
	private void updateStatus(int status)
	{
		switch (status)
		{
		case STATUS_IDLE:
			stopForeground(true);
			break;
		case STATUS_PAUSED:
			mNotification.contentView.setImageViewResource(R.id.ivPlayPause, R.drawable.ic_notif_play);
			mNotification.contentView.setOnClickPendingIntent(R.id.ivPlayPause, mIntentPlay);
			mNM.notify(NOTIFICATION_ID, mNotification);
			break;
		case STATUS_PLAYING:
			mNotification.contentView.setImageViewResource(R.id.ivPlayPause, R.drawable.ic_notif_pause);
			mNotification.contentView.setOnClickPendingIntent(R.id.ivPlayPause, mIntentPause);
			startForeground(NOTIFICATION_ID, mNotification);
			break;
		case STATUS_LOADING:
			//TODO
			mNotification.contentView.setTextViewText(R.id.tvTitle, getString(R.string.music_player_loading));
			mNotification.contentView.setTextViewText(R.id.tvSubTitle, "");
			mNM.notify(NOTIFICATION_ID, mNotification);
			break;
		}

		Intent intent = new Intent(BROADCAST_ACTION_STATUS);
		intent.putExtra(EXTRA_STATUS, status);
		LocalBroadcastManager.getInstance(MusicPlayerService.this).sendBroadcast(intent);
	}
	
	private void updateProgress(int seconds)
	{
		Intent intent = new Intent(BROADCAST_ACTION_PROGRESS);
		intent.putExtra(EXTRA_PROGRESS, seconds);
		LocalBroadcastManager.getInstance(MusicPlayerService.this).sendBroadcast(intent);
	}
	
	private void updateDuration(int seconds)
	{
		Intent intent = new Intent(BROADCAST_ACTION_DURATION);
		intent.putExtra(EXTRA_DURATION, seconds);
		LocalBroadcastManager.getInstance(MusicPlayerService.this).sendBroadcast(intent);
	}
}
