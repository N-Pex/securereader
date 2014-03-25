package info.guardianproject.securereaderinterface;

import android.os.Bundle;

import info.guardianproject.securereaderinterface.ui.UICallbacks;
import info.guardianproject.securereaderinterface.views.CreateAccountView;
import info.guardianproject.securereaderinterface.views.CreateAccountView.OnActionListener;
import info.guardianproject.idaho.R;

public class CreateAccountActivity extends FragmentActivityWithMenu implements OnActionListener
{
	public static String LOGTAG = "Big Buffalo";
	private CreateAccountView mViewCreateAccount;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Display home as up
		setDisplayHomeAsUp(true);

		setContentView(R.layout.activity_create_account);
		setMenuIdentifier(R.menu.activity_create_account);

		mViewCreateAccount = (CreateAccountView) findViewById(R.id.createAccount);
		mViewCreateAccount.setActionListener(this);
	}

	@Override
	public void onCreateIdentity(String authorName)
	{
		App.getInstance().socialReporter.createAuthorName(authorName);
		UICallbacks.handleCommand(this, R.integer.command_chat, null);
		finish();
	}
}
