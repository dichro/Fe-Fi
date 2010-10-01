package to.rcpt.fefi;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class TabbedHomeActivity extends TabActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabbed_home);
		
		TabHost tabHost = getTabHost();
		TabHost.TabSpec tabSpec;
		Intent intent;
		
		intent = new Intent().setClass(this, EyefiCardListActivity.class);
		tabSpec = tabHost.newTabSpec("cards").setIndicator("Cards").setContent(intent);
		tabHost.addTab(tabSpec);
		
		intent = new Intent().setClass(this, IncomingImagesActivity.class);
		tabSpec = tabHost.newTabSpec("pictures").setIndicator("Pictures").setContent(intent);
		tabHost.addTab(tabSpec);
		
		tabHost.setCurrentTab(0);
	}
}
