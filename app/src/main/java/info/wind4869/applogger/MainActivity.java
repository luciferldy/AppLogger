package info.wind4869.applogger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.app.AlertDialog.Builder;

import java.security.Permission;

import info.wind4869.applogger.Service.LogService;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        builder = new Builder(this);
        builder.setTitle("服务已经开始在后台运行");
        builder.setMessage("请点击确认退出当前窗口，谢谢");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                MainActivity.this.finish();
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("AppLogger", "startbutton clicked");

                // start service
                Intent intent = new Intent(MainActivity.this, LogService.class);
                startService(intent);

                builder.create().show();
            }
        });



    }

    @Override
    protected void onStart() {
        super.onStart();

        AlertDialog.Builder b = new Builder(this);
        b.setTitle("需要开启查看应用使用情况的权限");
        b.setMessage("去开权限");
        b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
            }
        });
        int usageStatsPermission = getPackageManager().checkPermission("android.permission.PACKAGE_USAGE_STATS", getPackageName());
        if (usageStatsPermission != PERMISSION_GRANTED) {
            Log.d("AppLogger", "PACKAGE_USAGE_STATS permission loss, " + usageStatsPermission);
            b.create().show();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Builder builder;
    private Button startButton;
}
