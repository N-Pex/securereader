package info.guardianproject.securereaderinterface.musicplayer;

import com.tinymission.rss.MediaContent;

import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableMediaContent implements Parcelable
{
	private MediaContent mMC;
	
	public ParcelableMediaContent(MediaContent mc)
	{
		mMC = mc;
	}
	
	public MediaContent getMediaContent()
	{
		return mMC;
	}
	
	@Override
	public int describeContents()
	{
		return 0;
	}	

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeSerializable(mMC);
	}

	public static final Parcelable.Creator<ParcelableMediaContent> CREATOR = new Parcelable.Creator<ParcelableMediaContent>()
	{
		@Override
		public ParcelableMediaContent createFromParcel(Parcel indata)
		{
			try
			{
				MediaContent mc = (MediaContent) indata.readSerializable();
				return new ParcelableMediaContent(mc);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public ParcelableMediaContent[] newArray(int size) {
			return null;
		}
	};
}
