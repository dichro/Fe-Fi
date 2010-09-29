package to.rcpt.fefi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class EyefiCardActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_control);
        Button b = (Button)findViewById(R.id.scan_button);
        b.setOnClickListener(new OnClickListener() {		
			public void onClick(View v) {
				Intent i = new Intent().setClass(EyefiCardActivity.this, EyefiCardScanActivity.class);
				startActivityForResult(i, 0);
			}
		});
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	Log.d("foo", "got req " + requestCode + " res " + resultCode + " inte " + data);
    }
}
