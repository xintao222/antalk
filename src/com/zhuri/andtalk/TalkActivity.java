package com.zhuri.andtalk;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuItem;

public class TalkActivity extends Activity implements OnClickListener {
	static final String LOG_TAG ="TalkActivity";
	private static final Intent talkService = new Intent("com.zhuri.service.TALK");
	private static final Intent pstcpService = new Intent("com.zhuri.service.PSTCP");

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button start = (Button)findViewById(R.id.start);
		start.setOnClickListener(this);

		Button stop = (Button)findViewById(R.id.stop);
		stop.setOnClickListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.start:
				startService(pstcpService);
				startService(talkService);
				break;

			case R.id.stop:
				stopService(talkService);
				stopService(pstcpService);
				break;

			default:
				break;
		}
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.FIRST + 1, 5, R.string.settings).setIcon(android.R.drawable.ic_menu_edit);
        return true;
    }   

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case Menu.FIRST + 1:
                Intent settings = new Intent(this, TalkRobotSettings.class);
                startActivity(settings);
                break;
        }   

        return false;
    }   
}
